package uk.co.iceconchy.aerowarptics.ward;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.advancement.AWCriteria;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.airship.DrawnAboard;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.collapse.RelicFloor;
import uk.co.iceconchy.aerowarptics.collapse.RelicMaster;
import uk.co.iceconchy.aerowarptics.plotter.PlotCost;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.registry.AWFluids;
import uk.co.iceconchy.aerowarptics.registry.AWSounds;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.warpnet.WarpNetworkRegistry;
import uk.co.iceconchy.aerowarptics.weather.RiftStorm;

import java.util.List;
import java.util.UUID;

/**
 * The Rift Ward's machine: the tank, the charge it banks from it, and the field that charge holds up.
 *
 * <h2>The charge</h2>
 * Turned at {@code wardMinimumSpeed} or faster, the ward draws Rift Essence out of its tank into its charge, up to
 * {@code wardCapacity}. The charge is what the field runs on: upkeep comes out of it every tick the ward is
 * switched on, by the area of the field as set, and so does the price of everything it turns aside. While it has
 * any, the field stands. Run dry - or made to give way by a rift it could not pay to turn - the field drops, and
 * stays down until the charge has climbed back past {@code wardRecoverFraction} ({@link WardCharge#standing}).
 *
 * <h2>Who decides</h2>
 * Not this class. What a field does about a rift is {@link Wards}', which asks a loaded ward for its charge and
 * tells it what it spent, and reads an unloaded one out of {@link WardRegistry}. So this block writes its record
 * there every second and whenever anything about it changes, and - coming back from an unload - takes its charge
 * <em>from</em> the record, which has had everything the field did in the meantime taken off it.
 *
 * <h2>What it costs to hold</h2>
 * Rotation, while it is switched on, and essence. It takes no orders from a circuit.
 */
public class RiftWardBlockEntity extends KineticBlockEntity implements GeoBlockEntity, IHaveGoggleInformation,
        RelicMaster {

    /** How much essence the ward's tank holds. A buffer for the pipes: what the field runs on is the charge. */
    public static final int TANK_CAPACITY = 16 * AWFluids.BUCKET;

    /** Ticks between the times the ward writes its record, tells its comparators and corrects its clients. */
    private static final int COUNT_INTERVAL = 20;

    /** How far the charge has to move, as a share of what the ward can bank, before clients are sent it again. */
    private static final double SYNC_STEP = 0.01D;

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    private final FluidTank tank = new FluidTank(TANK_CAPACITY, stack -> stack.getFluid() == AWFluids.RIFT_ESSENCE.get()) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    @Nullable
    private UUID wardId;
    @Nullable
    private UUID owner;
    private String ownerName = "";
    private WardMode mode = WardMode.BLOCK_ALL;
    /** The networks the mode is about, by id - see {@link RiftWard#networks}. */
    private List<UUID> networks = List.of();
    /** Whether the owner is turned too, so one player can try their own ward. */
    private boolean selfTest;
    private boolean enabled = true;
    private int radius = WardRules.DEFAULT_RADIUS;
    private double charge;
    private boolean standing;
    /** How many rifts this ward has turned aside. */
    private int turned;
    /** Ticks of redstone pulse left, having turned something aside. */
    private int pulse;

    /** Whether this block has taken up its record since it loaded - see {@link #adopt}. */
    private boolean adopted;
    private int countTimer;
    private int lastComparator = -1;
    private double lastSyncedCharge = -1.0D;
    private int lastSyncedTank = -1;
    private boolean lastStorm;
    private boolean kineticsSettled;
    /** Whether the line under the master has yet to be told what the master asks of it now. */
    private boolean retell = true;

    // What a client is told, since it never reads the server's config.
    private List<String> networkNames = List.of();
    private double capacity = 32_000.0D;
    private double upkeepPerMinute;
    private int maxRadius = 96;
    private double standingRadius = WardRules.DEFAULT_RADIUS;
    private final WardWorks.Motion motion = new WardWorks.Motion();

    public RiftWardBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public RiftWardBlockEntity(BlockPos pos, BlockState state) {
        this(AWBlockEntities.RIFT_WARD.get(), pos, state);
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
            adopted = false;
        }
    }

    // ------------------------------------------------------------------ reading

    public UUID wardId() {
        if (wardId == null) {
            wardId = UUID.randomUUID();
            setChanged();
        }
        return wardId;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public WardMode mode() {
        return mode;
    }

    public boolean selfTest() {
        return selfTest;
    }

    /** The names of the networks the mode is about, as the server last sent them. */
    public List<String> networkNames() {
        return networkNames;
    }

    public boolean enabled() {
        return enabled;
    }

    /** The field's radius as its owner set it. */
    public int radius() {
        return radius;
    }

    /** The field's radius as it stands: thinned by a storm, and nothing while the field is down. */
    public double standingRadius() {
        return standing ? standingRadius : 0.0D;
    }

    public double charge() {
        return charge;
    }

    /** What the ward can bank. */
    public double capacity() {
        return capacity;
    }

    /** How full the ward is, 0..1. */
    public double charged() {
        return WardCharge.fraction(charge, capacity);
    }

    public boolean standing() {
        return standing;
    }

    public int turned() {
        return turned;
    }

    /** Millibuckets a minute the field costs to hold, as set. */
    public double upkeepPerMinute() {
        return upkeepPerMinute;
    }

    /** The widest this server lets a field be set. */
    public int maxRadius() {
        return maxRadius;
    }

    public boolean pulsing() {
        return pulse > 0;
    }

    public int comparator() {
        return WardRules.comparator(charge, capacity);
    }

    public IFluidHandler tank() {
        return tank;
    }

    public FluidStack contents() {
        return tank.getFluid();
    }

    public boolean turning() {
        return Math.abs(getSpeed()) >= AWConfig.WARD_MINIMUM_SPEED.get();
    }

    /** The totem's opening and turning, as the client is easing them. */
    public WardWorks.Motion motion() {
        return motion;
    }

    /** Whether a player may change this ward's settings: its owner, an operator, or anyone if it has no owner. */
    public boolean mayEdit(Player player) {
        return owner == null || owner.equals(player.getUUID()) || player.hasPermissions(2);
    }

    // ------------------------------------------------------------------ orders

    void placedBy(Player player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        Trace.log(Trace.WARD, "ward at {} set down by {}", Trace.pos(worldPosition), Trace.who(player));
        changed();
    }

    /** An edit from the panel, already checked: who may make it, and that the network is one they may use. */
    public void applyEdit(int wantedRadius, WardMode wantedMode, List<UUID> named, boolean nowEnabled,
                          boolean turnOwner) {
        radius = WardRules.clampRadius(wantedRadius, AWConfig.WARD_MAX_RADIUS.get());
        mode = wantedMode;
        networks = List.copyOf(named.subList(0, Math.min(named.size(), WardRules.MOST_NETWORKS)));
        selfTest = turnOwner;
        enabled = nowEnabled;
        Trace.log(Trace.WARD, "ward at {} set: radius {}, {} {}, owner turned too {}, {}", Trace.pos(worldPosition),
                radius, mode, networks, selfTest, enabled ? "on" : "off");
        RelicFloor.changed(level, worldPosition);
        changed();
    }

    /** A network this ward named has been disbanded. */
    public void forgetNetwork(UUID network) {
        if (networks.contains(network)) {
            List<UUID> left = new java.util.ArrayList<>(networks);
            left.remove(network);
            networks = List.copyOf(left);
            changed();
        }
    }

    /**
     * Spends charge on something the field did. Asked by {@link Wards}, and only once the thing is certain.
     *
     * @param deflection whether a rift was turned aside, which a circuit is told and the panel counts
     */
    void spend(long cost, boolean deflection) {
        charge = Math.max(0.0D, charge - cost);
        if (deflection) {
            turned++;
            pulse = WardRules.PULSE_TICKS;
            if (level != null) {
                RiftWardBlock.tellCircuits(level, worldPosition);
            }
        }
        Trace.log(Trace.WARD, "ward at {} spent {} mB{}: {} mB left", Trace.pos(worldPosition), cost,
                deflection ? " turning a rift aside" : "", Trace.num(charge));
        changed();
    }

    /** A rift came that the ward could not pay to turn: it is let through, and the field drops. */
    void giveWay() {
        if (standing) {
            standing = false;
            light(false);
            playSound(AWSounds.WARD_GIVE_WAY.get(), 1.4F, 1.0F);
            Trace.log(Trace.WARD, "ward at {} gave way with {} mB: its field is down", Trace.pos(worldPosition),
                    Trace.num(charge));
            changed();
        }
    }

    /** A scene was shown round this ward: whoever is standing by it saw their ward hold, or fail to. */
    void witnessed(boolean held) {
        if (held && level instanceof ServerLevel server) {
            AWCriteria.wardHeld(server, worldPosition);
        }
    }

    /** Going: the field goes with the machine. */
    void broken() {
        if (level instanceof ServerLevel server && wardId != null) {
            WardRegistry.get(server).remove(wardId);
        }
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        if (Airship.orphaned(this)) {
            return;
        }
        super.tick();
        if (level == null) {
            return;
        }
        if (level.isClientSide) {
            motion.tick(enabled, standing);
            AWClientHooks.tickWard(this);
            return;
        }
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (!kineticsSettled) {
            kineticsSettled = true;
            RelicFloor.dropStrayLinks(this);
        }
        if (retell) {
            retell = false;
            RelicFloor.tell(this);
        }
        if (!adopted) {
            adopted = true;
            adopt(server);
        }
        double most = AWConfig.WARD_CAPACITY.get();
        if (charge < most) {
            int rate = PlotCost.drawRate(getSpeed(), AWConfig.WARD_MINIMUM_SPEED.get(), AWConfig.WARD_DRAW_RATE.get());
            if (rate > 0) {
                int want = (int) Math.min(rate, Math.ceil(most - charge));
                charge += tank.drain(want, IFluidHandler.FluidAction.EXECUTE).getAmount();
            }
        }
        boolean live = enabled && AWConfig.WARD_ENABLED.get();
        if (live) {
            charge = WardCharge.after(charge, 1L, WardCharge.upkeepPerSecond(radius, AWConfig.WARD_UPKEEP.get()));
        }
        boolean was = standing;
        standing = WardCharge.standing(was, live, charge, most, AWConfig.WARD_RECOVER_FRACTION.get());
        if (was != standing) {
            light(standing);
            playSound(standing ? AWSounds.WARD_RETURN.get() : AWSounds.WARD_GIVE_WAY.get(), 1.0F, standing ? 1.0F : 0.8F);
            Trace.log(Trace.WARD, "ward at {} field {}: {} mB of {}, {}", Trace.pos(worldPosition),
                    standing ? "stands" : "is down", Trace.num(charge), Trace.num(most), live ? "on" : "off");
            changed();
        }
        if (pulse > 0 && --pulse == 0) {
            RiftWardBlock.tellCircuits(level, worldPosition);
        }
        if (++countTimer >= COUNT_INTERVAL) {
            countTimer = 0;
            record(server);
            int reading = comparator();
            if (reading != lastComparator) {
                lastComparator = reading;
                RiftWardBlock.tellCircuits(level, worldPosition);
            }
            // A storm thins the field, and the motes at its rim are drawn where the client was last told it is.
            boolean storm = RiftStorm.ragingOver(server);
            if (Math.abs(charge - lastSyncedCharge) >= most * SYNC_STEP || tank.getFluidAmount() != lastSyncedTank
                    || storm != lastStorm) {
                lastStorm = storm;
                sendData();
            }
        }
    }

    /**
     * Takes up the record the server kept while this block was away.
     *
     * <p>The record wins over what the block saved: it is the one a rift was charged against while the chunk was
     * unloaded, and the one that has had the upkeep since then taken off. A record somewhere else under this id is
     * another ward's - this one is a copy, a structure block's or a command's - and a copy is given an id of its
     * own rather than left fighting the original for one field. A network disbanded while the block was away is
     * let go of here.
     */
    private void adopt(ServerLevel server) {
        WardRegistry registry = WardRegistry.get(server);
        RiftWard kept = registry.byId(wardId());
        if (kept != null && (!kept.pos().equals(worldPosition) || !kept.dimension().equals(server.dimension()))) {
            Trace.log(Trace.WARD, "ward at {} shares id {} with the ward at {}: taking a new one", Trace.pos(worldPosition),
                    wardId, Trace.pos(kept.pos()));
            wardId = UUID.randomUUID();
            kept = null;
        }
        if (kept != null) {
            // Switched off while it was away is switched off: only an operator standing a ward down does that.
            enabled = enabled && kept.enabled();
            long away = Wards.now(server.getServer()) - kept.countedAt();
            charge = kept.enabled()
                    ? WardCharge.after(kept.charge(), away, WardCharge.upkeepPerSecond(kept.radius(), AWConfig.WARD_UPKEEP.get()))
                    : kept.charge();
            standing = WardCharge.standing(kept.standing(), kept.enabled() && AWConfig.WARD_ENABLED.get(), charge,
                    AWConfig.WARD_CAPACITY.get(), AWConfig.WARD_RECOVER_FRACTION.get());
        }
        WarpNetworkRegistry known = WarpNetworkRegistry.get(server);
        List<UUID> still = networks.stream().filter(network -> known.byId(network) != null).toList();
        if (still.size() != networks.size()) {
            Trace.log(Trace.WARD, "ward at {} let go of {} network(s) that have gone", Trace.pos(worldPosition),
                    networks.size() - still.size());
            networks = still;
        }
        light(standing);
        changed();
    }

    /** Writes this ward into the registry as it stands now. */
    private void record(ServerLevel server) {
        WardRegistry.get(server).register(new RiftWard(wardId(), server.dimension(), worldPosition, owner, ownerName,
                mode, networks, selfTest, enabled, radius, charge, Wards.now(server.getServer()), standing));
    }

    /**
     * Lights the machine, or puts it out: its block gives off light only while its field stands, which is one of
     * the ways a player can tell from across a base that it does. A plain state change: the block and its axis are
     * the same, which Create takes as kinetically the same block, so a line run in under the master is not disturbed.
     */
    private void light(boolean lit) {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (state.hasProperty(RiftWardBlock.LIT) && state.getValue(RiftWardBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(RiftWardBlock.LIT, lit), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    /** Something about the ward has changed: saved, recorded, and sent. */
    private void changed() {
        setChanged();
        if (level instanceof ServerLevel server) {
            if (adopted) {
                record(server);
            }
            sendData();
        }
    }

    // ------------------------------------------------------------------ turned through its floor

    /** Stress while it is switched on: a ward switched off holds nothing, and is not made to be fed. */
    @Override
    public float relicImpact() {
        return enabled ? AWConfig.WARD_STRESS.get().floatValue() : 0.0F;
    }

    /** The ward's load, while the line under it is the input turning it; otherwise it is another input's. */
    @Override
    public float calculateStressApplied() {
        float impact = RelicFloor.load(this, worldPosition);
        this.lastStressApplied = impact;
        return impact;
    }

    /**
     * The speed of the input turning the machine: the line under the master or one into the middle of a side of its
     * floor, whichever is fastest. Nothing comes out again - see {@link RelicFloor}.
     */
    @Override
    public float getSpeed() {
        return RelicFloor.speed(level, worldPosition);
    }

    @Override
    public float lineSpeed() {
        return super.getSpeed();
    }

    @Override
    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        RelicFloor.changed(level, worldPosition);
    }

    @Override
    public void retell() {
        retell = true;
    }

    @Override
    public boolean lineGoggles(List<Component> tooltip, boolean isPlayerSneaking) {
        return super.addToGoggleTooltip(tooltip, isPlayerSneaking);
    }

    // ------------------------------------------------------------------ saving and syncing

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (wardId != null) {
            tag.putUUID("WardId", wardId);
        }
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
        RiftWard.saveAccess(tag, mode, networks, selfTest);
        tag.putBoolean("Enabled", enabled);
        tag.putInt("Radius", radius);
        tag.putDouble("Charge", charge);
        tag.putBoolean("Standing", standing);
        tag.putInt("Turned", turned);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        if (clientPacket && level instanceof ServerLevel server) {
            // A client never reads the server's config, nor its networks: what its panel and its motes need is sent.
            net.minecraft.nbt.ListTag names = new net.minecraft.nbt.ListTag();
            for (UUID network : networks) {
                names.add(net.minecraft.nbt.StringTag.valueOf(WarpNetworkRegistry.get(server).nameOf(network)));
            }
            tag.put("NetworkNames", names);
            tag.putDouble("Capacity", AWConfig.WARD_CAPACITY.get());
            tag.putDouble("UpkeepPerMinute", WardCharge.upkeepPerSecond(radius, AWConfig.WARD_UPKEEP.get()) * 60.0D);
            tag.putInt("MaxRadius", AWConfig.WARD_MAX_RADIUS.get());
            tag.putDouble("StandingRadius", WardField.standing(radius, RiftStorm.ragingOver(server),
                    AWConfig.WARD_STORM_THINNING.get()));
            lastSyncedCharge = charge;
            lastSyncedTank = tank.getFluidAmount();
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        wardId = tag.hasUUID("WardId") ? tag.getUUID("WardId") : null;
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        mode = RiftWard.loadMode(tag);
        networks = List.copyOf(RiftWard.loadNetworks(tag));
        selfTest = tag.getBoolean("SelfTest");
        // A ward saved before it had a switch was on.
        enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
        radius = Math.max(WardRules.MIN_RADIUS, tag.getInt("Radius"));
        charge = Math.max(0.0D, tag.getDouble("Charge"));
        standing = tag.getBoolean("Standing");
        turned = tag.getInt("Turned");
        tank.readFromNBT(registries, tag.getCompound("Tank"));
        if (clientPacket) {
            List<String> names = new java.util.ArrayList<>();
            for (net.minecraft.nbt.Tag name : tag.getList("NetworkNames", net.minecraft.nbt.Tag.TAG_STRING)) {
                names.add(name.getAsString());
            }
            networkNames = List.copyOf(names);
            if (tag.contains("Capacity")) {
                capacity = tag.getDouble("Capacity");
                upkeepPerMinute = tag.getDouble("UpkeepPerMinute");
                maxRadius = tag.getInt("MaxRadius");
                standingRadius = tag.getDouble("StandingRadius");
            }
        } else if (AWConfig.SERVER_SPEC.isLoaded()) {
            capacity = AWConfig.WARD_CAPACITY.get();
        }
    }

    // ------------------------------------------------------------------ goggles

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        AWLang.translate("gui.goggles.rift_ward").forGoggles(tooltip);
        AWLang.translate(stateKey()).style(standing ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
        AWLang.translate("gui.goggles.rift_ward.radius", radius).style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        AWLang.translate("gui.goggles.rift_ward.charge", Math.round(charge), Math.round(capacity))
                .style(ChatFormatting.GOLD).forGoggles(tooltip, 1);
        AWLang.translate("gui.goggles.rift_ward.essence", tank.getFluidAmount(), TANK_CAPACITY)
                .style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        AWLang.translate(mode.translationKey()).style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        if (selfTest) {
            AWLang.translate("gui.goggles.rift_ward.self_test").style(ChatFormatting.GOLD).forGoggles(tooltip, 1);
        }
        if (turned > 0) {
            AWLang.translate("gui.goggles.rift_ward.turned", turned).style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        }
        RelicFloor.goggles(level, worldPosition, tooltip, isPlayerSneaking);
        return true;
    }

    /** What the field is doing, as a translation key: standing, down, or switched off. */
    public String stateKey() {
        return !enabled ? "rift_ward.state.off" : standing ? "rift_ward.state.standing" : "rift_ward.state.down";
    }

    // ------------------------------------------------------------------ geckolib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Posed by RiftWardRenderer from WardWorks, never animated - see that class.
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    /** The machine. What it does to the sky is drawn in world space by the ward's effects, not here. */
    @Override
    public AABB getRenderBoundingBox() {
        double[] box = WardStructure.box();
        return new AABB(worldPosition.getX() + box[0], worldPosition.getY() + box[1], worldPosition.getZ() + box[2],
                worldPosition.getX() + box[3], worldPosition.getY() + box[4], worldPosition.getZ() + box[5]);
    }

    // ------------------------------------------------------------------ helpers

    private void playSound(SoundEvent sound, float volume, float pitch) {
        if (level != null) {
            level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume, pitch);
        }
    }
}
