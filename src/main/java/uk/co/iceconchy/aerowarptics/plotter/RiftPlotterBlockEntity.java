package uk.co.iceconchy.aerowarptics.plotter;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.advancement.AWCriteria;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.airship.DrawnAboard;
import uk.co.iceconchy.aerowarptics.airship.Fastening;
import uk.co.iceconchy.aerowarptics.airship.Fastenings;
import uk.co.iceconchy.aerowarptics.astrolabe.TerrainPalette;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.detector.SpatialWaveDetectorBlockEntity;
import uk.co.iceconchy.aerowarptics.hold.HoldPurpose;
import uk.co.iceconchy.aerowarptics.hold.Holds;
import uk.co.iceconchy.aerowarptics.modulator.RiftModulatorBlockEntity;
import uk.co.iceconchy.aerowarptics.modulator.RiftModulatorTheme;
import uk.co.iceconchy.aerowarptics.network.AWNetwork;
import uk.co.iceconchy.aerowarptics.network.ClientboundWarpEffectPacket;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.registry.AWFluids;
import uk.co.iceconchy.aerowarptics.registry.AWSounds;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Rift Plotter's machine: the plot, the tank and the charge, the two rifts, and the crossing between them.
 *
 * <h2>A strike</h2>
 * A plot is set at the plotting table and begun there, or by a redstone rising edge that replays it. Everything
 * that can be known at the machine is asked first ({@link PlotRules#preflight}); refused, nothing is drawn. Then
 * the machine <em>charges</em>: it draws essence from its tank into its charge, as fast as it is turned, until the
 * plot is paid for - a long plot drains a tank several times over, and the pipes feeding it are the range. All the
 * while it holds the far end's ground, which is usually somewhere nobody has been for a long time. Once paid, it
 * <em>reaches</em>: the far end has to be loaded before its ground can be read, and the ground there decides the
 * exit's real height, which is asked about again ({@link PlotRules#farEnd}). Refused there, the charge stays in the
 * machine - drawn, not spent - for the next plot. Then both rifts tear open and stand for
 * {@code plotterOpenSeconds}; whatever crosses the near one comes out of the far one. Then they heal.
 *
 * <h2>What crosses</h2>
 * A crossing is a line between two sightings the plotter took itself, meeting the entry's disc - see
 * {@link PlotGeometry}. Vehicles first, as at a gate, because a vehicle carries its crew and whatever is loose in
 * its hold; then loose things, if the server allows it. A vehicle is carried with every part of its vessel by
 * {@link Airship#relocate} - the one way a hull may jump - turned by the rotation between the rifts, its speed
 * turned with it, and then held loaded a while so an uncrewed one flies on at its target. A vehicle wider than the
 * rift, or tied to the ground, is turned back, not half swallowed.
 *
 * <h2>What it costs to hold</h2>
 * Rotation. The plotter draws stress only while a strike is under way, as a gate does only while it holds an
 * aperture; let the shaft slow below the minimum with the rift open and the rift collapses.
 */
public class RiftPlotterBlockEntity extends KineticBlockEntity implements GeoBlockEntity, IHaveGoggleInformation,
        uk.co.iceconchy.aerowarptics.collapse.RelicMaster {

    /** How much essence the plotter can hold at once. A buffer: a long plot drinks more than this through pipes. */
    public static final int TANK_CAPACITY = 16 * AWFluids.BUCKET;

    /** The rifts' colour when no Rift Modulator dresses them: a hot, deliberate crimson. */
    public static final int PLOT_COLOUR = 0xE0305A;
    public static final int PLOT_ACCENT = 0xFF9A5C;

    /** Ticks between renewals of the far end's hold. Its tickets last {@link #FAR_LIFESPAN}. */
    private static final int FAR_RENEW = 100;
    private static final int FAR_LIFESPAN = 300;

    /** Most chunks out from the exit held for things flying on from it: past this they are on their own. */
    private static final int MOST_FAR_CHUNKS = 10;

    /** Ticks something that has just been carried is left alone for. */
    private static final int SETTLE_TICKS = 20;

    /**
     * Ticks between keeping a standing exit up on the clients near it - which also opens it for anybody who has come
     * up since it tore.
     */
    private static final int EXIT_RENEW_INTERVAL = 40;

    /** Ticks each keep-up holds the exit for on a client: past the next one, so it never flickers between them. */
    private static final int EXIT_HOLD_TICKS = 100;

    /** Ticks ahead of going through the entry something has to be for the exit to open for it. */
    private static final int EXIT_LEAD_TICKS = 40;

    /** Ticks the exit stands after the last thing through it, or the last thing on its way, before it heals. */
    private static final int EXIT_LINGER_TICKS = 60;

    /** Ticks between syncs of the charge to watching clients. */
    private static final int SYNC_INTERVAL = 10;

    /** Ticks a strike fired for one docked vehicle stays open after it is through, so the tear is seen to heal. */
    private static final int HEAL_AFTER_SEND = 30;

    /** Ticks a docked vehicle waits before trying to fire the plotter again, after it refused. */
    private static final int DOCK_RETRY_TICKS = 100;

    /** Ticks a vehicle whose crew broke free of the dock is left alone for, so it can fly clear. */
    private static final int BREAK_FREE_TICKS = 100;

    /** Ticks between the dock helper's beam of sparks to what it has caught. */
    private static final int DOCK_BEAM_INTERVAL = 3;

    /** Ticks between the trace's lines about a vehicle the dock holds and has not lined up. */
    private static final int DOCK_REPORT_INTERVAL = 40;

    /** Ticks the rifts flare for when something goes through them. */
    private static final int TRANSIT_TICKS = 20;

    /** Ticks a Rift Ward's scene runs before a turned exit breaks open at the rim: a strike is not a ship's corridor. */
    private static final int WARD_SCENE_TICKS = 60;

    /** The fewest ticks between the times a lane held open through a field costs its ward again. */
    private static final int WARD_RENEW_FLOOR = 200;

    /** Blocks a turned-back vehicle is put back from the entry's plane. */
    private static final double TURN_BACK = 1.5D;


    private static final TicketType<ChunkPos> FAR_END =
            TicketType.create("aerowarptics:plotter_far_end", Comparator.comparingLong(ChunkPos::toLong), FAR_LIFESPAN);

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    private final FluidTank tank = new FluidTank(TANK_CAPACITY, stack -> stack.getFluid() == AWFluids.RIFT_ESSENCE.get()) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide) {
                sync(false);
            }
        }
    };

    @Nullable
    private UUID plotterId;
    /**
     * Whoever set the machine down, or {@code null} for one set down before plotters had owners. A Rift Ward asks
     * whose rift it is before it turns one aside, and a plotter nobody can be named for is a stranger's.
     */
    @Nullable
    private UUID owner;
    private String ownerName = "";
    /** What a Rift Ward made of this strike's exit, from the reach until the rifts heal. Not saved, as a strike is not. */
    @Nullable
    private uk.co.iceconchy.aerowarptics.ward.Wards.Turn wardTurn;
    /** Where this strike's exit really is, once a ward has had its say: what the far end's hold is centred on. */
    @Nullable
    private Vec3 heldExit;
    private int wardTimer;
    private Plot plot = Plot.NONE;
    private PlotterState state = PlotterState.IDLE;
    private int stateTicks;
    /** Essence drawn out of the tank and held, in millibuckets. Kept when a strike is stood down or refused. */
    private long charge;
    /** The plot under way's price, or 0 at rest. */
    private long cost;
    private int openTicks;
    /** Whether the rifts standing now are a lane: open until stood down, paying as they go. */
    private boolean laneOpen;
    private PlotFailure lastFailure = PlotFailure.NONE;
    private boolean powered;
    private Firing firing = Firing.DEFAULT;
    /** Whether the lane standing now was opened by a redstone signal, and so closes when the signal falls. */
    private boolean laneByRedstone;
    private int upkeepTimer;

    /** The vehicle the dock helper has caught, or {@code null}. Server only. */
    @Nullable
    private UUID docked;
    /** Ticks the caught vehicle has sat lined up. */
    private int linedUpTicks;
    /** Whether the caught vehicle is being pushed through the open entry. */
    private boolean launching;
    /** The vehicle a dock-fired strike is for: once it is through, the rifts heal behind it. */
    @Nullable
    private UUID sendFor;
    /** Ticks until a strike whose vehicle has gone through heals, or 0 for none pending. */
    private int closeIn;
    /** Ticks before a docked vehicle may try to fire the plotter again after a refusal. */
    private int dockRetry;
    /** Synced: whether something is caught, and whether it is lined up - for the table and the goggles. */
    private boolean dockHolding;
    private boolean dockLinedUp;
    /**
     * How deep a dock nobody has sized reaches behind the entry, as the server has it: sent so a client can draw the
     * guide of a plotter whose depth is left to the server.
     */
    private double dockReach = 32.0D;
    /** Things carried through since the rift last opened. */
    private int carried;
    private int farTimer;
    private int syncTimer;
    private int exitRenewTimer;
    /**
     * Whether the far rift stands. It opens only while something is on its way through the entry, and heals a moment
     * after the last of it: until then the exit is only held - its ground loaded, its place known - and nobody at the
     * target sees a thing. Synced, for the goggles.
     */
    private boolean exitShown;
    /** Ticks the exit is still wanted for. Server only. */
    private int exitWanted;

    /** The rifts, worked out once the far end is read. Server only; clients rebuild them from the plot. */
    @Nullable
    private PlotGeometry.Frame entry;
    @Nullable
    private PlotGeometry.Frame exit;

    /** Where the plotter last saw each thing near its entry, for crossings. */
    private final Map<UUID, Vec3> sightings = new HashMap<>();
    /** Things just carried, not to be looked at again for a moment. */
    private final Map<UUID, Integer> settling = new HashMap<>();
    /** Vehicles whose crew broke free of the dock, not to be caught again for a moment. */
    private final Map<UUID, Integer> brokeFree = new HashMap<>();

    // Client only: the machine as this client is turning it.
    private final PlotterWorks.Aim aim = new PlotterWorks.Aim();
    private final PlotterWorks.Spin spin = new PlotterWorks.Spin();
    private int clientCarried;
    private int flash;

    public RiftPlotterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public RiftPlotterBlockEntity(BlockPos pos, BlockState state) {
        this(AWBlockEntities.RIFT_PLOTTER.get(), pos, state);
    }

    /**
     * Asks a client to draw this machine, in case it arrived after the blocks round it - see {@link DrawnAboard} -
     * and asks the server to look the machine over, which puts back any part a world edit took away.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        DrawnAboard.redraw(this);
        if (level instanceof ServerLevel server) {
            server.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
            // A strike does not survive the machine being put away: its far end was held on a timer, its sightings
            // were in memory, and its rifts were cues sent to whoever was there. What was drawn is kept.
            if (state.busy()) {
                Trace.log(Trace.PLOTTER, "plotter at {} loaded mid-strike ({}), standing it down with {} mB kept",
                        Trace.pos(worldPosition), state.getSerializedName(), charge);
                state = PlotterState.IDLE;
                stateTicks = 0;
                cost = 0L;
                laneOpen = false;
            }
            dockHolding = false;
            dockLinedUp = false;
        }
    }

    // ------------------------------------------------------------------ reading

    public UUID plotterId() {
        if (plotterId == null) {
            plotterId = UUID.randomUUID();
            setChanged();
        }
        return plotterId;
    }

    public Plot plot() {
        return plot;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    /** Whoever set the machine down owns the rifts it tears. */
    void placedBy(net.minecraft.world.entity.player.Player player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        setChanged();
    }

    public PlotterState state() {
        return state;
    }

    public int stateTicks() {
        return stateTicks;
    }

    public long charge() {
        return charge;
    }

    public long cost() {
        return cost;
    }

    /** How far through its charge the strike under way is, 0..1. */
    public float charged() {
        return state == PlotterState.CHARGING ? PlotCost.progress(charge, cost) : state.busy() ? 1.0F : 0.0F;
    }

    public int openTicks() {
        return openTicks;
    }

    public boolean laneOpen() {
        return laneOpen;
    }

    public Firing firing() {
        return firing;
    }

    /** Whether the dock helper has a vehicle, and whether it is lined up. */
    public boolean dockHolding() {
        return dockHolding;
    }

    public boolean dockLinedUp() {
        return dockLinedUp;
    }

    /**
     * How deep a dock nobody has sized reaches behind the entry. On a client, as the server last said - it never
     * reads the config.
     */
    public double dockReach() {
        return level != null && !level.isClientSide ? AWConfig.PLOTTER_DOCK_REACH.get() : dockReach;
    }

    /** How deep this plotter's dock reaches behind its entry: as it was set, or as the server sizes one left alone. */
    public double dockDepth() {
        return firing.dockDepth(dockReach());
    }

    /** How far from its rift's axis this plotter's dock reaches: as it was set, or as suits the rift. */
    public double dockRadius() {
        return firing.dockRadius(plot.radius());
    }

    /** The server's limits on a plotter's firing settings, which a firing is clamped to. */
    public static Firing clampFiring(Firing firing) {
        return firing.clamped(AWConfig.PLOTTER_MAX_OPEN_SECONDS.get(), AWConfig.PLOTTER_MAX_EXIT_SPEED.get(),
                AWConfig.PLOTTER_MAX_DOCK_RADIUS.get(), AWConfig.PLOTTER_MAX_DOCK_DEPTH.get());
    }

    public PlotFailure lastFailure() {
        return lastFailure;
    }

    public int carried() {
        return carried;
    }

    public IFluidHandler tank() {
        return tank;
    }

    public FluidStack contents() {
        return tank.getFluid();
    }

    public boolean turning() {
        return Math.abs(getSpeed()) >= AWConfig.PLOTTER_MINIMUM_SPEED.get();
    }

    /** The configured rift radius limit, which a plot is clamped to. */
    public static double maxRadius() {
        return AWConfig.PLOTTER_MAX_RADIUS.get();
    }

    /** A Rift Modulator against the machine's side, whose look dresses its rifts - or {@code null}. */
    @Nullable
    public RiftModulatorBlockEntity modulator() {
        if (level == null) {
            return null;
        }
        for (BlockPos beside : PlotterStructure.around(worldPosition)) {
            if (level.getBlockEntity(beside) instanceof RiftModulatorBlockEntity modulator) {
                return modulator;
            }
        }
        return null;
    }

    public int riftColour() {
        RiftModulatorBlockEntity modulator = modulator();
        return modulator == null ? PLOT_COLOUR : modulator.colour() & 0xFFFFFF;
    }

    public int riftAccent() {
        RiftModulatorBlockEntity modulator = modulator();
        return modulator == null ? PLOT_ACCENT : modulator.accentColour() & 0xFFFFFF;
    }

    public RiftModulatorTheme riftTheme() {
        RiftModulatorBlockEntity modulator = modulator();
        return modulator == null ? RiftModulatorTheme.STANDARD : modulator.theme();
    }

    /** The entry, as the plot places it. */
    public PlotGeometry.Frame entryFrame() {
        return PlotGeometry.input(worldPosition, plot);
    }

    /** The exit, as the plot places it round the target's last known ground. */
    public PlotGeometry.Frame exitFrame() {
        return PlotGeometry.exit(PlotGeometry.target(plot), plot);
    }

    /** Blocks from the lens to where the exit would open. */
    public double reach() {
        return PlotGeometry.lens(worldPosition).distanceTo(exitFrame().centre());
    }

    /** What the plot as it stands would cost. */
    public long price() {
        return PlotCost.essence(reach(), plot.radius(), AWConfig.PLOTTER_BASE_COST.get(),
                AWConfig.PLOTTER_COST_PER_KM.get(), AWConfig.PLOTTER_COST_PER_RADIUS.get());
    }

    // ------------------------------------------------------------------ the client's machine

    public PlotterWorks.Aim aim() {
        return aim;
    }

    public PlotterWorks.Spin spin() {
        return spin;
    }

    /** Ticks since something last came out of the far end, as this client heard it; large when nothing has. */
    public int flash() {
        return flash;
    }

    // ------------------------------------------------------------------ orders

    /**
     * A new plot, from the plotting table. Only at rest: a strike under way is committed to the plot it was begun
     * on, and a target changed under a charge would change its price under it.
     */
    public PlotFailure setPlot(Plot wanted) {
        if (state.busy()) {
            return PlotFailure.BUSY;
        }
        Plot next = wanted.clamped(maxRadius());
        if (!next.equals(plot)) {
            plot = next;
            Trace.changed("plotter-plot:" + worldPosition, Trace.PLOTTER, "plotter at {} plotted {}", Trace.pos(worldPosition),
                    describe(plot));
            sync(true);
        }
        return PlotFailure.NONE;
    }

    /**
     * The ground at the target, as a survey has just read it. Kept on a plot at rest aimed at that column, so the
     * price the table is shown is for the exit where it will really be; a strike reads the ground again anyway.
     */
    void groundSurveyed(int x, int z, int groundY) {
        if (state.busy() || !plot.set() || plot.targetX() != x || plot.targetZ() != z || plot.targetY() == groundY
                || groundY == PlotterSurvey.UNKNOWN) {
            return;
        }
        plot = plot.withTargetY(groundY);
        sync(true);
    }

    /**
     * How it fires, from the table. Taken whenever - a change of exit speed or of what the dock does is wanted at once -
     * and read by a strike when it next needs it; a lane's being a lane is decided as it opens.
     */
    public void setFiring(Firing wanted) {
        Firing next = clampFiring(wanted);
        if (!next.equals(firing)) {
            firing = next;
            Trace.changed("plotter-firing:" + worldPosition, Trace.PLOTTER, "plotter at {} fires {}", Trace.pos(worldPosition),
                    firing);
            if (!firing.dock()) {
                letGo("the dock helper was switched off");
            }
            sync(true);
        }
    }

    /**
     * The block's redstone, told on every neighbour change. Only if the plotter fires on redstone: a rising edge fires it,
     * and a lane opened that way stands while the signal does, closing when it falls.
     */
    void signal(boolean now) {
        boolean rising = now && !powered;
        boolean falling = !now && powered;
        powered = now;
        setChanged();
        if (!firing.fireOnRedstone()) {
            return;
        }
        if (rising) {
            boolean began = !engage(null).isFailure();
            laneByRedstone = began && firing.lane();
        } else if (falling && laneByRedstone && state.busy() && state != PlotterState.CLOSING) {
            laneByRedstone = false;
            standDown(null);
        }
    }

    /**
     * Begins a strike on the plot the machine holds - asked by the table's Engage or by a redstone rising edge,
     * which are the same order. Refused, nothing is drawn.
     */
    public PlotFailure engage(@Nullable ServerPlayer by) {
        if (!(level instanceof ServerLevel server)) {
            return PlotFailure.NONE;
        }
        PlotGeometry.Frame far = exitFrame();
        PlotFailure failure = PlotRules.preflight(new PlotRules.Situation(AWConfig.PLOTTER_ENABLED.get(), state.busy(),
                plot.set(), turning(), server.getWorldBorder().isWithinBounds(plot.targetX(), plot.targetZ()),
                far.centre().y, server.getMinBuildHeight(), server.getMaxBuildHeight()));
        if (failure.isFailure()) {
            lastFailure = failure;
            Trace.log(Trace.PLOTTER, "plotter at {} refused to begin{}: {} - plot {}, turning {} rpm (needs {}), exit {} blocks"
                            + " out, exit at y {}", Trace.pos(worldPosition),
                    by == null ? " (redstone)" : " for " + Trace.who(by), failure.getSerializedName(), describe(plot),
                    Trace.num(getSpeed()), Trace.num(AWConfig.PLOTTER_MINIMUM_SPEED.get()), Trace.num(reach()),
                    Trace.num(far.centre().y));
            playSound(AWSounds.WARP_FAILED.get(), 0.7F, 1.2F);
            sync(true);
            return failure;
        }
        lastFailure = PlotFailure.NONE;
        wardTurn = null;
        heldExit = null;
        cost = price();
        carried = 0;
        sendFor = null;
        closeIn = 0;
        Trace.log(Trace.PLOTTER, "plotter at {} begins{}: {} - {} blocks out for {} mB, {} mB already held",
                Trace.pos(worldPosition), by == null ? " (redstone)" : " for " + Trace.who(by), describe(plot),
                Trace.num(reach()), cost, charge);
        transition(PlotterState.CHARGING);
        holdFarEnd(server, true);
        playSound(AWSounds.DESTINATION_LOCK.get(), 1.2F, 0.8F);
        return PlotFailure.NONE;
    }

    /**
     * Stands a strike down: a charge is kept, whatever has been drawn; an open rift heals now instead of at the end
     * of its window.
     */
    public void standDown(@Nullable ServerPlayer by) {
        if (!state.busy() || state == PlotterState.CLOSING) {
            return;
        }
        Trace.log(Trace.PLOTTER, "plotter at {} stood down {} by {}, {} mB kept", Trace.pos(worldPosition),
                state.getSerializedName(), by == null ? "redstone" : Trace.who(by), charge);
        laneByRedstone = false;
        if (state == PlotterState.OPEN) {
            close();
        } else {
            cost = 0L;
            transition(PlotterState.IDLE);
            playSound(AWSounds.DRIVE_COOLDOWN.get(), 0.8F, 1.0F);
        }
    }

    /** The master has been broken: an open rift closes with it. */
    void broken() {
        if (level instanceof ServerLevel && state == PlotterState.OPEN && exitShown) {
            sendExitCue(ClientboundWarpEffectPacket.Stage.RIFT_CLOSE, 0);
        }
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        if (Airship.orphaned(this)) {
            return;
        }
        super.tick();
        if (level == null) {
            return;
        }
        settleKinetics();
        stateTicks++;
        if (level.isClientSide) {
            // The dish points at where the entry is, which is no longer always the way the entry faces.
            aim.tick(plot.inputYaw(), plot.inputPitch());
            spin.tick(PlotterWorks.spinRate(state, charged()));
            if (carried != clientCarried) {
                if (carried > clientCarried) {
                    flash = 0;
                }
                clientCarried = carried;
            } else if (flash < 10_000) {
                flash++;
            }
            AWClientHooks.tickPlotter(this);
            return;
        }
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        settle();
        if (dockRetry > 0) {
            dockRetry--;
        }
        if (firing.dock()) {
            dock(server);
        }
        switch (state) {
            case IDLE -> {
            }
            case CHARGING -> charge(server);
            case REACHING -> reach(server);
            case OPEN -> hold(server);
            case CLOSING -> {
                if (stateTicks >= PlotterWorks.CLOSE_TICKS) {
                    transition(PlotterState.IDLE);
                }
            }
        }
        if (state == PlotterState.CHARGING && ++syncTimer >= SYNC_INTERVAL) {
            syncTimer = 0;
            sync(true);
        }
    }

    private void settle() {
        countDown(brokeFree);
        countDown(settling);
    }

    private static void countDown(Map<UUID, Integer> timers) {
        Iterator<Map.Entry<UUID, Integer>> entries = timers.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Integer> entry = entries.next();
            // Map.Entry.setValue answers the old value - the thing that has run two countdowns here a tick long.
            int left = entry.getValue() - 1;
            if (left <= 0) {
                entries.remove();
            } else {
                entry.setValue(left);
            }
        }
    }

    /** Draws essence into the charge, as fast as the shaft allows, until the plot is paid for. */
    private void charge(ServerLevel server) {
        holdFarEnd(server, false);
        int rate = PlotCost.drawRate(getSpeed(), AWConfig.PLOTTER_MINIMUM_SPEED.get(), AWConfig.PLOTTER_DRAW_RATE.get());
        long owed = cost - charge;
        if (owed > 0L && rate > 0) {
            int want = (int) Math.min(rate, owed);
            FluidStack drawn = tank.drain(want, IFluidHandler.FluidAction.EXECUTE);
            charge += drawn.getAmount();
        }
        if (charge >= cost) {
            Trace.log(Trace.PLOTTER, "plotter at {} is charged: {} of {} mB after {}t", Trace.pos(worldPosition), charge, cost,
                    stateTicks);
            transition(PlotterState.REACHING);
            playSound(AWSounds.DRIVE_CHARGED.get(), 1.0F, 0.9F);
        }
    }

    /**
     * Waits for the far end, then reads its ground and asks about it. The price is asked again too: the real ground
     * moves the exit, and a plot a few blocks longer than it looked from the table has a few more millibuckets owed.
     */
    private void reach(ServerLevel server) {
        holdFarEnd(server, false);
        BlockPos target = new BlockPos(plot.targetX(), 0, plot.targetZ());
        if (!farReady(server, target)) {
            if (stateTicks >= AWConfig.PLOTTER_REACH_TIMEOUT.get()) {
                refuse(PlotFailure.FAR_END_UNREACHED, "the far end at " + Trace.pos(target) + " never loaded in "
                        + stateTicks + "t");
            }
            return;
        }
        int ground = TerrainPalette.surfaceOf(server, new BlockPos.MutableBlockPos(), plot.targetX(), plot.targetZ());
        if (ground != TerrainPalette.UNKNOWN_SURFACE && ground != plot.targetY()) {
            plot = plot.withTargetY(ground);
        }
        PlotGeometry.Frame near = entryFrame();
        PlotGeometry.Frame far = wardedExit(server, exitFrame());
        if (!farReady(server, BlockPos.containing(far.centre()))) {
            // The exit lies outside the chunks the target's hold covered - a plot hung far over the target's edge.
            holdFarEnd(server, true);
            return;
        }
        double reach = PlotGeometry.lens(worldPosition).distanceTo(far.centre());
        long owed = PlotCost.essence(reach, plot.radius(), AWConfig.PLOTTER_BASE_COST.get(),
                AWConfig.PLOTTER_COST_PER_KM.get(), AWConfig.PLOTTER_COST_PER_RADIUS.get());
        if (owed > charge) {
            cost = owed;
            Trace.log(Trace.PLOTTER, "plotter at {} found the far ground at y {}: the plot now costs {} mB, {} held - charging on",
                    Trace.pos(worldPosition), ground, owed, charge);
            transition(PlotterState.CHARGING);
            return;
        }
        int exitSolid = solid(server, far, plot.radius());
        int entrySolid = solid(server, near, plot.radius());
        int sampled = PlotRules.samples(far, plot.radius()).size();
        PlotFailure failure = PlotRules.farEnd(server.getWorldBorder().isWithinBounds(plot.targetX(), plot.targetZ()),
                far.centre().y,
                server.getMinBuildHeight(), server.getMaxBuildHeight(), PlotRules.buried(exitSolid, sampled),
                PlotRules.buried(entrySolid, sampled));
        if (failure.isFailure()) {
            refuse(failure, "far ground y " + ground + ", exit " + Trace.vec(far.centre()) + " (" + exitSolid + "/" + sampled
                    + " solid), entry " + Trace.vec(near.centre()) + " (" + entrySolid + "/" + sampled + " solid), "
                    + Trace.num(reach) + " blocks out");
            return;
        }
        open(server, near, far, owed);
    }

    /**
     * The exit as a Rift Ward leaves it: where it was plotted, unless that is inside a field that turns this
     * plotter's owner aside - then at the field's rim, on the line from the lens, still facing the way it was aimed.
     *
     * <p>Asked at the reach and at no other time, of the exit the real ground gives; nothing is paid until the
     * rifts tear ({@link #open}). A plotter is never refused by a ward, and its table goes on showing the exit
     * where it was plotted - a ward says nothing until it acts.
     */
    private PlotGeometry.Frame wardedExit(ServerLevel server, PlotGeometry.Frame far) {
        wardTurn = null;
        heldExit = far.centre();
        if (!AWConfig.WARD_DEFLECTS_PLOTTERS.get()) {
            return far;
        }
        BlockPos aimed = BlockPos.containing(far.centre());
        uk.co.iceconchy.aerowarptics.ward.Wards.Turn turn = uk.co.iceconchy.aerowarptics.ward.Wards.turnAside(server, aimed, PlotGeometry.lens(worldPosition), owner,
                0.0D, plot.radius() + uk.co.iceconchy.aerowarptics.ward.WardRules.RIM_MARGIN, wardPrice());
        if (!turn.turned() && turn.gaveWay() == null) {
            return far;
        }
        wardTurn = turn;
        if (!turn.turned()) {
            return far;
        }
        Vec3 shift = new Vec3(turn.target().getX() - aimed.getX(), 0.0D, turn.target().getZ() - aimed.getZ());
        heldExit = far.centre().add(shift);
        return new PlotGeometry.Frame(heldExit, far.normal(), far.up(), far.right());
    }

    /** What it costs a ward to turn this plot's exit aside. */
    private long wardPrice() {
        return uk.co.iceconchy.aerowarptics.ward.WardCharge.plotted(AWConfig.WARD_PLOTTER_BASE.get(), AWConfig.WARD_PLOTTER_PER_RADIUS.get(),
                plot.radius());
    }

    /** Refused at the far end: the charge stays, nothing moved. */
    private void refuse(PlotFailure failure, String why) {
        lastFailure = failure;
        Trace.log(Trace.PLOTTER, "plotter at {} refused at the far end: {} - {}; {} mB kept", Trace.pos(worldPosition),
                failure.getSerializedName(), why, charge);
        cost = 0L;
        transition(PlotterState.IDLE);
        playSound(AWSounds.WARP_FAILED.get(), 1.0F, 0.8F);
    }

    /** How many points across a disc are in something solid. */
    private static int solid(ServerLevel server, PlotGeometry.Frame frame, double radius) {
        int solid = 0;
        for (Vec3 point : PlotRules.samples(frame, radius)) {
            BlockPos at = BlockPos.containing(point);
            if (!server.isInWorldBounds(at) || server.getBlockState(at).blocksMotion()) {
                solid++;
            }
        }
        return solid;
    }

    /** Both rifts tear open. The charge is spent here, and only here. */
    private void open(ServerLevel server, PlotGeometry.Frame near, PlotGeometry.Frame far, long spend) {
        charge -= spend;
        cost = 0L;
        entry = near;
        exit = far;
        laneOpen = firing.lane();
        openTicks = laneOpen ? 0 : firing.window() * 20;
        upkeepTimer = 0;
        carried = 0;
        sightings.clear();
        exitShown = false;
        exitWanted = 0;
        exitRenewTimer = 0;
        Trace.log(Trace.PLOTTER, "plotter at {} tears open: entry {} facing {}, exit {} facing {} (waiting for traffic),"
                        + " radius {}, {} mB spent, {} mB left in the charge, {}", Trace.pos(worldPosition),
                Trace.vec(near.centre()), Trace.vec(near.normal()), Trace.vec(far.centre()), Trace.vec(far.normal()),
                Trace.num(plot.radius()), spend, charge, laneOpen ? "held open as a lane" : "open " + openTicks + "t");
        transition(PlotterState.OPEN);
        playSound(AWSounds.COLLAPSE_TEAR.get(), 2.0F, 1.1F);
        // The rifts are torn: a ward that turned the exit aside is paid, one that could not pay drops its field,
        // and whoever is standing under it is shown which.
        wardTimer = 0;
        if (wardTurn != null) {
            uk.co.iceconchy.aerowarptics.ward.Wards.commit(server, wardTurn, wardPrice());
            uk.co.iceconchy.aerowarptics.ward.Wards.announce(server, wardTurn, far.centre(), WARD_SCENE_TICKS, riftColour(), List.of(), true);
        }
    }

    /** An open rift, held: the far end kept loaded, the shaft checked, and everything crossing carried. */
    private void hold(ServerLevel server) {
        if (entry == null || exit == null) {
            close();
            return;
        }
        if (!turning()) {
            lastFailure = PlotFailure.LOST_ROTATION;
            Trace.log(Trace.PLOTTER, "plotter at {} lost its rotation with the rift open ({} rpm, needs {}) - collapsing",
                    Trace.pos(worldPosition), Trace.num(getSpeed()), Trace.num(AWConfig.PLOTTER_MINIMUM_SPEED.get()));
            close();
            return;
        }
        holdFarEnd(server, false);
        // A docked vehicle lined up with the rift open is pushed through at once, so it is as good as on its way.
        boolean coming = traverse(server, entry, exit) | launching | (dockHolding && dockLinedUp);
        exitWanted = coming ? EXIT_LINGER_TICKS : Math.max(0, exitWanted - 1);
        if (exitWanted > 0) {
            if (!exitShown) {
                showExit(server, "something is on its way through the entry");
            } else if (++exitRenewTimer >= EXIT_RENEW_INTERVAL) {
                exitRenewTimer = 0;
                cueNearExit(server, ClientboundWarpEffectPacket.Stage.RIFT_HOLD);
            }
        } else if (exitShown) {
            hideExit("nothing more is coming");
        }
        // A lane held open through a field goes on costing the ward that turned it, a strike's worth at a time. A
        // ward that runs dry drops its field for the next tear; an exit already standing is never moved.
        if (laneOpen && wardTurn != null && wardTurn.turned()
                && ++wardTimer >= Math.max(WARD_RENEW_FLOOR, firing.window() * 20)) {
            wardTimer = 0;
            uk.co.iceconchy.aerowarptics.ward.Wards.commit(server, wardTurn, wardPrice());
        }
        if (laneOpen) {
            if (++upkeepTimer >= 20) {
                upkeepTimer = 0;
                if (!payUpkeep()) {
                    lastFailure = PlotFailure.LANE_STARVED;
                    Trace.log(Trace.PLOTTER, "plotter at {} could not pay its lane's upkeep of {} mB ({} mB held, {} in the"
                            + " tank) - closing", Trace.pos(worldPosition), upkeep(), charge, tank.getFluidAmount());
                    close();
                    return;
                }
            }
        } else if (stateTicks >= openTicks) {
            close();
            return;
        }
        if (closeIn > 0 && --closeIn == 0) {
            Trace.log(Trace.PLOTTER, "plotter at {} heals behind the vehicle it opened for", Trace.pos(worldPosition));
            close();
        }
    }

    /** What a lane costs a second: a share of what opening this plot costs, never less than a millibucket. */
    public long upkeep() {
        return PlotCost.upkeep(price(), AWConfig.PLOTTER_LANE_UPKEEP.get());
    }

    /** Pays a second of a lane, from the charge first and then the tank. */
    private boolean payUpkeep() {
        long owed = upkeep();
        long fromCharge = Math.min(owed, charge);
        long rest = owed - fromCharge;
        if (rest > tank.getFluidAmount()) {
            return false;
        }
        charge -= fromCharge;
        if (rest > 0L) {
            tank.drain((int) rest, IFluidHandler.FluidAction.EXECUTE);
        }
        return true;
    }

    /** The rifts heal. */
    private void close() {
        wardTurn = null;
        heldExit = null;
        laneOpen = false;
        laneByRedstone = false;
        closeIn = 0;
        sendFor = null;
        Trace.log(Trace.PLOTTER, "plotter at {} closes after {}t, having carried {}", Trace.pos(worldPosition), stateTicks,
                carried);
        if (exitShown) {
            sendExitCue(ClientboundWarpEffectPacket.Stage.RIFT_CLOSE, 0);
        }
        exitShown = false;
        exitWanted = 0;
        entry = null;
        exit = null;
        sightings.clear();
        transition(PlotterState.CLOSING);
        playSound(AWSounds.DRIVE_COOLDOWN.get(), 1.0F, 0.8F);
    }

    private void transition(PlotterState next) {
        boolean kinetic = state.busy() != next.busy();
        state = next;
        stateTicks = 0;
        syncTimer = 0;
        if (kinetic) {
            refreshKinetics();
        }
        sync(true);
    }

    // ------------------------------------------------------------------ the far end

    /**
     * Holds the ground round the exit and the target, renewed well inside the tickets' life. Nothing here loads a
     * chunk itself: {@link Holds#ground} does, and refuses plot space.
     */
    private void holdFarEnd(ServerLevel server, boolean now) {
        if (!now && ++farTimer < FAR_RENEW) {
            return;
        }
        farTimer = 0;
        // Round where the exit really is, once a ward has had its say - turned aside, that can be chunks from
        // where it was plotted, and a hold on the plotted place would never see the real one load.
        BlockPos exitAt = BlockPos.containing(heldExit != null ? heldExit : exitFrame().centre());
        int reach = (int) Math.ceil((plot.radius() + plot.distance() + 16.0D) / 16.0D);
        // Two past the chunks wanted, so every one of them is entity-ticking and not just loaded: a shell or a hull
        // that comes out has to go on moving.
        int radius = Math.min(MOST_FAR_CHUNKS, reach) + 2;
        Holds.ground(server, FAR_END, new ChunkPos(exitAt), radius);
        Holds.ground(server, FAR_END, new ChunkPos(new BlockPos(plot.targetX(), 0, plot.targetZ())), 3);
    }

    /** Whether a column is loaded and ticking entities, so something put there goes on moving. */
    private static boolean farReady(ServerLevel server, BlockPos pos) {
        return server.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) && server.isPositionEntityTicking(pos)
                && server.shouldTickBlocksAt(ChunkPos.asLong(pos));
    }

    /**
     * The far rift tears open, for everyone near it: something is on its way through the entry. Until now the exit
     * was only held - its ground loaded, its place known - and nobody at the target saw a thing.
     */
    private void showExit(ServerLevel server, String why) {
        if (exit == null) {
            return;
        }
        exitShown = true;
        exitRenewTimer = 0;
        Trace.log(Trace.PLOTTER, "plotter at {} opens its exit at {}: {}", Trace.pos(worldPosition), Trace.vec(exit.centre()),
                why);
        cueNearExit(server, ClientboundWarpEffectPacket.Stage.RIFT_OPEN);
        server.playSound(null, BlockPos.containing(exit.centre()), AWSounds.RIFT_OPEN.get(), SoundSource.BLOCKS, 4.0F, 0.9F);
        sync(true);
    }

    /** Nothing more is coming: the exit heals, and the entry stands on for whatever its window has left. */
    private void hideExit(String why) {
        Trace.log(Trace.PLOTTER, "plotter at {} heals its exit: {}", Trace.pos(worldPosition), why);
        sendExitCue(ClientboundWarpEffectPacket.Stage.RIFT_CLOSE, 0);
        exitShown = false;
        sync(true);
    }

    /**
     * A cue about the exit to every player near it. Each holds it up for {@link #EXIT_HOLD_TICKS} on their client, so
     * one who leaves before it heals is not left with it standing - and {@code RIFT_HOLD} opens it for one who has
     * come up since it tore.
     */
    private void cueNearExit(ServerLevel server, ClientboundWarpEffectPacket.Stage stage) {
        if (exit == null) {
            return;
        }
        ClientboundWarpEffectPacket cue = cue(stage, exit, EXIT_HOLD_TICKS, -plot.radius() * 2.0D);
        double range = 256.0D;
        for (ServerPlayer player : server.players()) {
            if (player.distanceToSqr(exit.centre()) <= range * range) {
                AWNetwork.sendTo(player, cue);
            }
        }
    }

    /** Whether the far rift stands now - it waits, while the entry is open, for something to come through. */
    public boolean exitShown() {
        return exitShown;
    }

    private void sendExitCue(ClientboundWarpEffectPacket.Stage stage, int duration) {
        if (!(level instanceof ServerLevel server) || exit == null) {
            return;
        }
        AWNetwork.sendToTracking(server, exit.centre(), cue(stage, exit, duration, -plot.radius() * 2.0D));
    }

    private ClientboundWarpEffectPacket cue(ClientboundWarpEffectPacket.Stage stage, PlotGeometry.Frame frame, int duration,
                                            double throat) {
        return new ClientboundWarpEffectPacket(worldPosition, stage, 3, plotterId(), frame.centre(), frame.normal(),
                plot.radius(), duration, (float) throat, riftColour(), riftAccent(), riftTheme().ordinal(), 1.0F, 1.0F);
    }

    // ------------------------------------------------------------------ crossing

    /**
     * Carries whatever crossed the entry since the plotter last looked. Vehicles first, then loose things.
     *
     * @return whether anything is on its way through: headed into the disc and due within {@link #EXIT_LEAD_TICKS}
     */
    private boolean traverse(ServerLevel server, PlotGeometry.Frame near, PlotGeometry.Frame far) {
        double radius = plot.radius();
        boolean coming = false;
        AABB catchment = new AABB(near.centre(), near.centre()).inflate(radius + AWConfig.PLOTTER_CATCHMENT.get());
        Set<UUID> seen = new HashSet<>();

        Set<SubLevel> moved = Collections.newSetFromMap(new IdentityHashMap<>());
        List<ServerSubLevel> vehicles = new ArrayList<>();
        for (SubLevel sub : Sable.HELPER.getAllIntersecting(server, new BoundingBox3d(catchment))) {
            if (sub instanceof ServerSubLevel ship && !ship.isRemoved()) {
                vehicles.add(ship);
            }
        }
        for (ServerSubLevel ship : vehicles) {
            if (moved.contains(ship)) {
                continue;
            }
            Airship vehicle = Airship.of(ship);
            Vec3 centre = vec(vehicle.centre(new Vector3d()));
            UUID id = vehicle.uuid();
            seen.add(id);
            Vec3 before = sightings.put(id, centre);
            if (settling.containsKey(id)) {
                continue;
            }
            // A hull's middle a little off the disc may still bring its bow through, so the lead looks a little wide.
            coming |= PlotGeometry.ticksToCross(near, radius + 2.0D, centre, vec(vehicle.velocity())) <= EXIT_LEAD_TICKS;
            if (before == null) {
                continue;
            }
            PlotGeometry.Crossing crossing = PlotGeometry.crossing(near, radius, before, centre);
            if (crossing != null) {
                carryVehicle(server, vehicle, centre, crossing, near, far, moved);
            } else if (Trace.enabled()) {
                // The one way a hull goes past the entry and nothing is said: its middle met the plane outside the rim.
                double met = PlotGeometry.metPlaneAt(near, before, centre);
                if (!Double.isNaN(met)) {
                    Trace.log(Trace.PLOTTER, "plotter at {} let vehicle {} past its entry: its middle met the plane {} blocks"
                                    + " from the rift's middle and the rift is {} wide - entry {}, hull's middle {}",
                            Trace.pos(worldPosition), id, Trace.num(met), Trace.num(radius), Trace.vec(near.centre()),
                            Trace.vec(centre));
                }
            }
        }

        if (AWConfig.PLOTTER_CARRIES_ENTITIES.get()) {
            for (Entity entity : server.getEntities((Entity) null, catchment,
                    candidate -> !candidate.isRemoved() && !candidate.isSpectator() && !candidate.isPassenger())) {
                // Aboard a hull: it goes with the hull or not at all.
                if (Sable.HELPER.getTrackingOrVehicleSubLevel(entity) != null) {
                    continue;
                }
                Vec3 middle = entity.getBoundingBox().getCenter();
                UUID id = entity.getUUID();
                seen.add(id);
                Vec3 before = sightings.put(id, middle);
                if (settling.containsKey(id)) {
                    continue;
                }
                // A player's motion on the server is whatever its client last said, so how far it has come since the last
                // sighting is the better guide to where it is going.
                Vec3 moving = before != null ? middle.subtract(before) : entity.getDeltaMovement();
                coming |= PlotGeometry.ticksToCross(near, radius, middle, moving) <= EXIT_LEAD_TICKS;
                if (before == null) {
                    continue;
                }
                PlotGeometry.Crossing crossing = PlotGeometry.crossing(near, radius, before, middle);
                if (crossing != null) {
                    carryEntity(server, entity, middle, crossing, near, far);
                }
            }
        }
        // Anything that has left is forgotten, so coming back is a fresh approach rather than half of an old one.
        sightings.keySet().retainAll(seen);
        return coming;
    }

    private void carryVehicle(ServerLevel server, Airship vehicle, Vec3 centre, PlotGeometry.Crossing crossing,
                              PlotGeometry.Frame near, PlotGeometry.Frame far, Set<SubLevel> moved) {
        var box = vehicle.shipBounds();
        if (box != null) {
            double needs = PlotGeometry.needs(box.maxX() + 1.0D - box.minX(), box.maxY() + 1.0D - box.minY(),
                    box.maxZ() + 1.0D - box.minZ(), vehicle.orientation(), near);
            if (needs > plot.radius()) {
                Trace.log(Trace.PLOTTER, "plotter at {} turned back vehicle {}: it needs a rift {} wide and this one is {}",
                        Trace.pos(worldPosition), vehicle.uuid(), Trace.num(needs), Trace.num(plot.radius()));
                turnBack(vehicle, centre, crossing, near, PlotFailure.TOO_LARGE);
                return;
            }
        }
        Fastening tie = Fastenings.holding(vehicle).orElse(null);
        if (tie != null) {
            Trace.log(Trace.PLOTTER, "plotter at {} turned back vehicle {}: tied down by {}", Trace.pos(worldPosition),
                    vehicle.uuid(), tie);
            turnBack(vehicle, centre, crossing, near, PlotFailure.FASTENED);
            return;
        }

        // Read before the hull moves: afterwards whoever was aboard is found by looking where it is now.
        List<ServerPlayer> aboard = vehicle.crew();
        List<Airship> vessel = vehicle.vessel();
        vessel.forEach(member -> moved.add(member.subLevel()));

        PlotGeometry.Arrival arrival = PlotGeometry.map(near, far, crossing, vec(vehicle.velocity()));
        // The arrival is where the hull's middle goes, and relocate places its origin: the one is turned into the
        // other through the same rotation as everything else.
        Vec3 fromCentre = vec(vehicle.position()).subtract(centre);
        Vec3 origin = arrival.position().add(PlotGeometry.rotate(arrival.turn(), fromCentre));
        Quaterniond orientation = new Quaterniond(arrival.turn()).mul(vehicle.orientation());
        boolean relocated = vehicle.relocate(new Vector3d(origin.x, origin.y, origin.z), orientation, 0.0D);
        Trace.log(Trace.PLOTTER, "plotter at {} carried vehicle {} ({} crew, {} in its vessel) from {} to {}{}",
                Trace.pos(worldPosition), vehicle.uuid(), aboard.size(), vessel.size(), Trace.vec(centre),
                Trace.vec(arrival.position()), relocated ? "" : " - Sable REFUSED the move");
        if (!relocated) {
            return;
        }
        Vec3 out = PlotGeometry.exitMotion(arrival, far, firing.exitSpeed());
        vehicle.driveVelocity(new Vector3d(out.x, out.y, out.z));
        int lease = AWConfig.PLOTTER_ARRIVAL_HOLD_SECONDS.get() * 20;
        for (Airship member : vessel) {
            settling.put(member.uuid(), SETTLE_TICKS);
            sightings.remove(member.uuid());
            if (lease > 0) {
                Holds.holdShip(member, HoldPurpose.PLOTTED, plotterId(), lease, true);
            }
        }
        if (vehicle.uuid().equals(docked)) {
            letGo("it went through");
        }
        if (vehicle.uuid().equals(sendFor) && !laneOpen) {
            // A strike fired for this vehicle: it is through, so the tear heals behind it.
            sendFor = null;
            closeIn = HEAL_AFTER_SEND;
        }
        arrived(server, vehicle.assemblyBounds().toMojang(), arrival.position(), aboard);
    }

    /** A vehicle that may not go through: put back on the side it came from and stopped, rather than half swallowed. */
    private void turnBack(Airship vehicle, Vec3 centre, PlotGeometry.Crossing crossing, PlotGeometry.Frame near,
                          PlotFailure reason) {
        Vec3 back = near.normal().scale(crossing.forward() ? -(crossing.past() + TURN_BACK) : crossing.past() + TURN_BACK);
        Vec3 origin = vec(vehicle.position()).add(back);
        vehicle.relocate(new Vector3d(origin.x, origin.y, origin.z), new Quaterniond(vehicle.orientation()), 0.0D);
        vehicle.driveVelocity(new Vector3d());
        settling.put(vehicle.uuid(), SETTLE_TICKS);
        sightings.remove(vehicle.uuid());
        lastFailure = reason;
        if (level != null) {
            level.playSound(null, BlockPos.containing(centre), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 0.6F);
        }
        sync(true);
    }

    private void carryEntity(ServerLevel server, Entity entity, Vec3 middle, PlotGeometry.Crossing crossing,
                             PlotGeometry.Frame near, PlotGeometry.Frame far) {
        PlotGeometry.Arrival arrival = PlotGeometry.map(near, far, crossing, entity.getDeltaMovement());
        // The crossing was measured on the middle of the thing; it is put down by its feet.
        Vec3 feet = arrival.position().subtract(0.0D, middle.y - entity.getY(), 0.0D);
        Vec3 look = PlotGeometry.rotate(arrival.turn(), entity.getLookAngle());
        float yaw = (float) PlotGeometry.yawOf(look);
        float pitch = (float) PlotGeometry.pitchOf(look);
        if (entity instanceof ServerPlayer player) {
            player.connection.teleport(feet.x, feet.y, feet.z, yaw, pitch);
        } else {
            entity.teleportTo(feet.x, feet.y, feet.z);
            entity.setYRot(yaw);
            entity.setXRot(pitch);
            entity.setYHeadRot(yaw);
        }
        entity.setDeltaMovement(PlotGeometry.exitMotion(arrival, far, firing.exitSpeed()));
        entity.hurtMarked = true;
        entity.fallDistance = 0.0F;
        settling.put(entity.getUUID(), SETTLE_TICKS);
        sightings.remove(entity.getUUID());
        Trace.log(Trace.PLOTTER, "plotter at {} carried {} from {} to {}", Trace.pos(worldPosition),
                entity.getName().getString(), Trace.vec(middle), Trace.vec(feet));
        arrived(server, entity.getBoundingBox(), arrival.position(),
                entity instanceof ServerPlayer walker ? List.of(walker) : List.of());
    }

    /** Something has come out of the far end: the detectors hear it, both ends react, and the witnesses mark it. */
    private void arrived(ServerLevel server, AABB bounds, Vec3 at, List<ServerPlayer> through) {
        carried++;
        exitWanted = EXIT_LINGER_TICKS;
        if (!exitShown) {
            showExit(server, "something came through faster than it was seen coming");
        }
        SpatialWaveDetectorBlockEntity.announce(server, bounds);
        if (exit != null && entry != null) {
            AWNetwork.sendToTracking(server, exit.centre(), through,
                    cue(ClientboundWarpEffectPacket.Stage.RIFT_TRANSIT, exit, TRANSIT_TICKS, -plot.radius() * 2.0D));
            AWNetwork.sendToTracking(server, entry.centre(),
                    cue(ClientboundWarpEffectPacket.Stage.RIFT_TRANSIT, entry, TRANSIT_TICKS, plot.radius() * 2.0D));
        }
        server.playSound(null, BlockPos.containing(at), AWSounds.WARP_EXIT.get(), SoundSource.BLOCKS, 3.0F, 1.0F);
        AWCriteria.plotStruck(server, worldPosition, through);
        sync(true);
    }

    // ------------------------------------------------------------------ the dock

    /**
     * The dock helper, every tick it is switched on: keep hold of the vehicle it has, or catch one waiting behind the
     * entry; glide it onto the rift's axis and turn it level and end-on; and once it is lined up and the rift is open,
     * push it straight through. Lined up with the rift shut, it holds it there - and if the plotter fires when a vehicle
     * docks, fires.
     */
    private void dock(ServerLevel server) {
        PlotGeometry.Frame near = state == PlotterState.OPEN && entry != null ? entry : entryFrame();
        double radius = plot.radius();
        double zone = dockRadius();
        double reach = dockDepth();
        Airship vehicle = docked == null ? null : caught(server, docked);
        if (docked != null && vehicle == null) {
            letGo("it is gone");
        }
        if (vehicle != null && (settling.containsKey(vehicle.uuid()) || !held(vehicle, near, radius, zone, reach))) {
            letGo("it left the dock");
            vehicle = null;
        }
        // A pilot is never trapped: crouch aboard and the dock lets go, and leaves the ship alone for a moment.
        if (vehicle != null && vehicle.crew().stream().anyMatch(ServerPlayer::isShiftKeyDown)) {
            brokeFree.put(vehicle.uuid(), BREAK_FREE_TICKS);
            letGo("its crew broke free");
            vehicle = null;
        }
        if (vehicle == null) {
            vehicle = catchOne(server, near, radius, zone, reach);
            if (vehicle == null) {
                return;
            }
        }
        double[] size = size(vehicle);
        if (size == null) {
            letGo("it has no blocks left");
            return;
        }
        Quaterniond lined = DockAlign.lineUp(vehicle.orientation(), size, near, radius);
        Vec3 centre = vec(vehicle.centre(new Vector3d()));
        DockAlign.Steer steer = DockAlign.steer(centre, DockAlign.mark(near, size, lined), vehicle.orientation(), lined);
        boolean open = state == PlotterState.OPEN;
        if (open && (launching || steer.linedUp())) {
            if (!launching) {
                Trace.log(Trace.PLOTTER, "plotter at {} launches vehicle {} through its entry", Trace.pos(worldPosition),
                        vehicle.uuid());
                server.playSound(null, BlockPos.containing(centre), AWSounds.STABILIZING.get(), SoundSource.BLOCKS, 1.5F,
                        1.3F);
            }
            launching = true;
            Vec3 push = DockAlign.launch(near, AWConfig.PLOTTER_LAUNCH_SPEED.get() / 20.0D);
            vehicle.steerVelocity(new Vector3d(push.x, push.y, push.z),
                    new Vector3d(steer.angular().x, steer.angular().y, steer.angular().z));
        } else {
            launching = false;
            vehicle.steerVelocity(new Vector3d(steer.linear().x, steer.linear().y, steer.linear().z),
                    new Vector3d(steer.angular().x, steer.angular().y, steer.angular().z));
        }
        if (!steer.linedUp() && server.getGameTime() % DOCK_REPORT_INTERVAL == 0L) {
            // A hull the dock cannot bring to its mark - one in the ground, or in the machine - is held and never sent.
            Trace.log(Trace.PLOTTER, "plotter at {} is still lining up vehicle {}: {} blocks and {} degrees off its mark at {},"
                            + " rift {}", Trace.pos(worldPosition), vehicle.uuid(), Trace.num(steer.distance()),
                    Trace.num(Math.toDegrees(steer.angle())), Trace.vec(DockAlign.mark(near, size, lined)),
                    open ? "open" : "shut");
        }
        boolean wasLinedUp = dockLinedUp;
        linedUpTicks = steer.linedUp() ? linedUpTicks + 1 : 0;
        dockLinedUp = steer.linedUp();
        if (dockLinedUp != wasLinedUp) {
            sync(true);
        }
        if (!open && dockLinedUp && firing.fireOnDock() && state == PlotterState.IDLE && dockRetry <= 0
                && linedUpTicks >= firing.dockDelay() * 20) {
            PlotFailure failure = engage(null);
            if (failure.isFailure()) {
                dockRetry = DOCK_RETRY_TICKS;
            } else {
                sendFor = vehicle.uuid();
                Trace.log(Trace.PLOTTER, "plotter at {} fires for docked vehicle {}", Trace.pos(worldPosition),
                        vehicle.uuid());
            }
        }
        if (server.getGameTime() % DOCK_BEAM_INTERVAL == 0L) {
            beam(server, PlotGeometry.focus(worldPosition, plot), centre, dockLinedUp);
        }
    }

    /**
     * Whether a vehicle the dock has caught is still the dock's: inside it, or no further back than the mark the dock
     * is taking it to - see {@link DockAlign#keepDepth}.
     */
    private static boolean held(Airship vehicle, PlotGeometry.Frame near, double radius, double zone, double reach) {
        double[] size = size(vehicle);
        if (size == null) {
            return false;
        }
        Quaterniond lined = DockAlign.lineUp(vehicle.orientation(), size, near, radius);
        return DockAlign.inDock(near, zone, DockAlign.keepDepth(near, reach, size, lined),
                vec(vehicle.centre(new Vector3d())));
    }

    /** A hull's own block box, x y z - or {@code null} for one with no blocks to measure. */
    @Nullable
    private static double[] size(Airship vehicle) {
        var box = vehicle.shipBounds();
        return box == null ? null : new double[]{box.maxX() + 1.0D - box.minX(), box.maxY() + 1.0D - box.minY(),
                box.maxZ() + 1.0D - box.minZ()};
    }

    /** The vehicle the dock has caught, if it is still in this level. */
    @Nullable
    private static Airship caught(ServerLevel server, UUID id) {
        ServerSubLevel ship = Holds.present(server, id);
        if (ship == null) {
            return null;
        }
        Airship airship = Airship.of(ship);
        return airship.isActive() ? airship : null;
    }

    /**
     * The nearest vehicle waiting in the dock that may be caught: slow enough, not just carried, able to fit the rift
     * some way round, and not tied to anything it would leave behind.
     */
    @Nullable
    private Airship catchOne(ServerLevel server, PlotGeometry.Frame near, double radius, double zone, double reach) {
        AABB box = new AABB(near.centre(), near.centre().subtract(near.normal().scale(reach))).inflate(zone);
        double slowest = AWConfig.PLOTTER_DOCK_CAPTURE_SPEED.get() / 20.0D;
        Airship best = null;
        double bestDistance = Double.MAX_VALUE;
        for (SubLevel sub : Sable.HELPER.getAllIntersecting(server, new BoundingBox3d(box))) {
            if (!(sub instanceof ServerSubLevel ship) || ship.isRemoved()) {
                continue;
            }
            Airship candidate = Airship.of(ship);
            Vec3 centre = vec(candidate.centre(new Vector3d()));
            if (settling.containsKey(candidate.uuid()) || brokeFree.containsKey(candidate.uuid())
                    || !DockAlign.inDock(near, zone, reach, centre)
                    || candidate.velocity().length() > slowest) {
                continue;
            }
            double[] size = size(candidate);
            if (size == null) {
                continue;
            }
            if (!DockAlign.fitsSomeWay(size, near, radius)) {
                Trace.changed("plotter-dock-fit:" + worldPosition + ":" + candidate.uuid(), Trace.PLOTTER,
                        "plotter at {} will not dock vehicle {}: it fits the rift no way round ({} x {} x {}, radius {})",
                        Trace.pos(worldPosition), candidate.uuid(), size[0], size[1], size[2], Trace.num(radius));
                continue;
            }
            if (Fastenings.holding(candidate).isPresent()) {
                continue;
            }
            double distance = centre.distanceTo(near.centre());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        if (best != null) {
            docked = best.uuid();
            linedUpTicks = 0;
            launching = false;
            dockHolding = true;
            Trace.log(Trace.PLOTTER, "plotter at {} docks vehicle {} ({} blocks from the entry, dock {} out from the axis"
                            + " and {} deep)", Trace.pos(worldPosition), best.uuid(), Trace.num(bestDistance), Trace.num(zone),
                    Trace.num(reach));
            server.playSound(null, BlockPos.containing(vec(best.centre(new Vector3d()))), SoundEvents.BEACON_POWER_SELECT,
                    SoundSource.BLOCKS, 1.2F, 0.8F);
            sync(true);
        }
        return best;
    }

    /** Lets go of whatever the dock was holding. */
    private void letGo(String why) {
        if (docked == null) {
            return;
        }
        Trace.log(Trace.PLOTTER, "plotter at {} lets go of vehicle {}: {}", Trace.pos(worldPosition), docked, why);
        docked = null;
        linedUpTicks = 0;
        launching = false;
        dockHolding = false;
        dockLinedUp = false;
        sync(true);
    }

    /** A thread of sparks from the ball to what the dock holds, brighter once it is lined up. */
    private static void beam(ServerLevel server, Vec3 from, Vec3 to, boolean linedUp) {
        Vec3 along = to.subtract(from);
        int sparks = (int) Math.min(24, 4 + along.length() / 2.0D);
        for (int i = 0; i < sparks; i++) {
            Vec3 at = from.add(along.scale(server.random.nextDouble()));
            server.sendParticles(linedUp ? uk.co.iceconchy.aerowarptics.registry.AWParticles.RIFT_SPARK.get()
                    : net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 1, 0.05D, 0.05D, 0.05D, 0.0D);
        }
    }

    // ------------------------------------------------------------------ kinetics

    /**
     * Stress only while a strike is under way. A plotter standing idle holds nothing open, and a player should not be
     * made to feed a machine they only fire now and then.
     */
    @Override
    public float relicImpact() {
        return state.busy() ? AWConfig.PLOTTER_STRESS.get().floatValue() : 0.0F;
    }

    /** The plotter's load, while the line under it is the input turning it; otherwise it is another input's. */
    @Override
    public float calculateStressApplied() {
        float impact = uk.co.iceconchy.aerowarptics.collapse.RelicFloor.load(this, worldPosition);
        this.lastStressApplied = impact;
        return impact;
    }

    /** Create caches a block's stress impact, so one that changes with the machine has to say when it does. */
    private void refreshKinetics() {
        uk.co.iceconchy.aerowarptics.collapse.RelicFloor.changed(level, worldPosition);
    }

    // ------------------------------------------------------------------ turned through its floor

    /** Whether links saved while the master had shafts of its own have been let go of. */
    private boolean kineticsSettled;
    /** Whether the line under the master has yet to be told what the master asks of it now. */
    private boolean retell = true;

    /**
     * The speed of the input turning the machine: the line under the master or one into the middle of a side of its
     * floor, whichever is fastest. Nothing comes out again - see {@link uk.co.iceconchy.aerowarptics.collapse.RelicFloor}.
     */
    @Override
    public float getSpeed() {
        return uk.co.iceconchy.aerowarptics.collapse.RelicFloor.speed(level, worldPosition);
    }

    @Override
    public float lineSpeed() {
        return super.getSpeed();
    }

    @Override
    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        refreshKinetics();
    }

    @Override
    public void retell() {
        retell = true;
    }

    private void settleKinetics() {
        if (level == null || level.isClientSide) {
            return;
        }
        if (!kineticsSettled) {
            kineticsSettled = true;
            uk.co.iceconchy.aerowarptics.collapse.RelicFloor.dropStrayLinks(this);
        }
        if (retell) {
            retell = false;
            uk.co.iceconchy.aerowarptics.collapse.RelicFloor.tell(this);
        }
    }

    /** Create's own lines - the load and the network's stress - are the feeder's to give. */
    private void kineticGoggles(List<Component> tooltip, boolean isPlayerSneaking) {
        uk.co.iceconchy.aerowarptics.collapse.RelicFloor.goggles(level, worldPosition, tooltip, isPlayerSneaking);
    }

    @Override
    public boolean lineGoggles(List<Component> tooltip, boolean isPlayerSneaking) {
        return super.addToGoggleTooltip(tooltip, isPlayerSneaking);
    }

    // ------------------------------------------------------------------ saving and syncing

    private void sync(boolean now) {
        setChanged();
        if (now && level != null && !level.isClientSide) {
            sendData();
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (plotterId != null) {
            tag.putUUID("PlotterId", plotterId);
        }
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
        tag.put("Plot", plot.save());
        tag.putString("State", state.getSerializedName());
        tag.putInt("StateTicks", stateTicks);
        tag.putLong("Charge", charge);
        tag.putLong("Cost", cost);
        tag.putInt("OpenTicks", openTicks);
        tag.putBoolean("LaneOpen", laneOpen);
        tag.put("Firing", firing.save());
        tag.putBoolean("DockHolding", dockHolding);
        tag.putBoolean("DockLinedUp", dockLinedUp);
        if (clientPacket) {
            tag.putDouble("DockReach", AWConfig.PLOTTER_DOCK_REACH.get());
            tag.putBoolean("ExitShown", exitShown);
        }
        tag.putString("Failure", lastFailure.getSerializedName());
        tag.putBoolean("Powered", powered);
        tag.putInt("Carried", carried);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        plotterId = tag.hasUUID("PlotterId") ? tag.getUUID("PlotterId") : null;
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        // A client never reads the server's config, and has no need to clamp what the server already clamped.
        plot = Plot.load(tag.getCompound("Plot")).clamped(clientPacket ? 64.0D : maxRadiusSafe());
        PlotterState was = state;
        state = PlotterState.byName(tag.getString("State"));
        int ticks = tag.getInt("StateTicks");
        // A client counts its own ticks between syncs; a sync only corrects it when the state changes or it has
        // drifted, so the glow does not jump back a few ticks at every charge update.
        if (!clientPacket || was != state || Math.abs(ticks - stateTicks) > 10) {
            stateTicks = ticks;
        }
        charge = tag.getLong("Charge");
        cost = tag.getLong("Cost");
        openTicks = tag.getInt("OpenTicks");
        laneOpen = tag.getBoolean("LaneOpen");
        Firing loaded = Firing.load(tag.getCompound("Firing"));
        firing = clientPacket || !AWConfig.SERVER_SPEC.isLoaded() ? loaded : clampFiring(loaded);
        dockHolding = tag.getBoolean("DockHolding");
        dockLinedUp = tag.getBoolean("DockLinedUp");
        if (clientPacket && tag.contains("DockReach")) {
            dockReach = tag.getDouble("DockReach");
            exitShown = tag.getBoolean("ExitShown");
        }
        lastFailure = PlotFailure.byName(tag.getString("Failure"));
        powered = tag.getBoolean("Powered");
        carried = tag.getInt("Carried");
        tank.readFromNBT(registries, tag.getCompound("Tank"));
    }

    /** The configured radius limit, or a generous stand-in while the server config is not loaded yet. */
    private static double maxRadiusSafe() {
        return AWConfig.SERVER_SPEC.isLoaded() ? maxRadius() : 64.0D;
    }

    // ------------------------------------------------------------------ goggles

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        AWLang.translate("gui.goggles.rift_plotter").forGoggles(tooltip);
        AWLang.translate(state.translationKey())
                .style(state == PlotterState.OPEN ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
        if (plot.set()) {
            AWLang.translate("gui.goggles.rift_plotter.target", plot.targetX(), plot.targetZ())
                    .style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        }
        AWLang.translate("gui.goggles.rift_plotter.essence", tank.getFluidAmount(), TANK_CAPACITY)
                .style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        if (state == PlotterState.OPEN && laneOpen) {
            AWLang.translate("gui.goggles.rift_plotter.lane", upkeep()).style(ChatFormatting.LIGHT_PURPLE)
                    .forGoggles(tooltip, 1);
        }
        if (state == PlotterState.OPEN) {
            AWLang.translate(exitShown ? "gui.goggles.rift_plotter.exit_open" : "gui.goggles.rift_plotter.exit_waiting")
                    .style(exitShown ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY).forGoggles(tooltip, 1);
        }
        if (firing.dock() && dockHolding) {
            AWLang.translate(dockLinedUp ? "gui.goggles.rift_plotter.lined_up" : "gui.goggles.rift_plotter.docking")
                    .style(dockLinedUp ? ChatFormatting.AQUA : ChatFormatting.GRAY).forGoggles(tooltip, 1);
        }
        if (state == PlotterState.CHARGING) {
            AWLang.translate("gui.goggles.rift_plotter.charge", charge, cost).style(ChatFormatting.GOLD)
                    .forGoggles(tooltip, 1);
        } else if (charge > 0L) {
            AWLang.translate("gui.goggles.rift_plotter.held", charge).style(ChatFormatting.GOLD).forGoggles(tooltip, 1);
        }
        if (lastFailure.isFailure()) {
            AWLang.translate(lastFailure.translationKey()).style(ChatFormatting.RED).forGoggles(tooltip, 1);
        }
        kineticGoggles(tooltip, isPlayerSneaking);
        return true;
    }

    // ------------------------------------------------------------------ geckolib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Posed by RiftPlotterRenderer from PlotterWorks, never animated - see that class.
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    /** The machine. The rifts and the dock's guide are drawn in world space by the plotter's effects, not here. */
    @Override
    public AABB getRenderBoundingBox() {
        double[] box = PlotterStructure.box();
        return new AABB(worldPosition.getX() + box[0], worldPosition.getY() + box[1], worldPosition.getZ() + box[2],
                worldPosition.getX() + box[3], worldPosition.getY() + box[4], worldPosition.getZ() + box[5]);
    }

    // ------------------------------------------------------------------ helpers

    private void playSound(SoundEvent sound, float volume, float pitch) {
        if (level != null) {
            level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume, pitch);
        }
    }

    private static Vec3 vec(Vector3dc vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }

    private static String describe(Plot plot) {
        return "target (" + plot.targetX() + ", " + plot.targetY() + ", " + plot.targetZ() + "), exit "
                + Trace.num(plot.distance()) + " out on " + Trace.num(plot.bearing()) + " deg, " + Trace.num(plot.height())
                + " up, " + (plot.aimed() ? "aimed at the target" : "aimed " + Trace.num(plot.aimYaw()) + "/"
                + Trace.num(plot.aimPitch())) + ", entry " + Trace.num(plot.inputReach()) + " out at "
                + Trace.num(plot.inputYaw()) + "/" + Trace.num(plot.inputPitch()) + (plot.inputTurned() ? " turned to face "
                + Trace.num(plot.faceYaw()) + "/" + Trace.num(plot.facePitch()) : " facing straight out") + ", radius "
                + Trace.num(plot.radius());
    }
}
