package uk.co.iceconchy.aerowarptics.wellhead;

import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.collapse.CollapseInstability;
import uk.co.iceconchy.aerowarptics.collapse.CradleRing;
import uk.co.iceconchy.aerowarptics.fissure.RiftFissureBlockEntity;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.registry.AWFluids;
import uk.co.iceconchy.aerowarptics.registry.AWItems;
import uk.co.iceconchy.aerowarptics.registry.AWRecipeTypes;
import uk.co.iceconchy.aerowarptics.rifting.RiftHop;
import uk.co.iceconchy.aerowarptics.stabiliser.SpatialStabiliserBlock;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.weather.RiftStorm;

import java.util.ArrayList;
import java.util.List;

/**
 * A Rift Wellhead: the frame it holds, the tear inside it, and the working of the two.
 *
 * <p>Everything that decides anything is somewhere else and pure. {@link WellheadStructure} says what a frame
 * is and {@link WellheadAssembly} forms it; {@link WellheadReactor} is the whole of how strain, yield and wear
 * move, stepped once a second from here with what this block can see - how fast it is turned, how many
 * stabilisers stand round it and how evenly, whether a storm is raging, how much room its tank has. This class
 * is the part with a world in it: it reads those, pays the reactor's answers out, and tells the fissure, the
 * comparators and the clients.
 *
 * <h2>What a player can do to it</h2>
 * <ul>
 *   <li><b>Feed it</b> through the hatch on top, or the collar: only what has an
 *       {@code aerowarptics:agitating} recipe, only with room, never during a lockout. An automation handler
 *       that takes anything lets a pipe fill it with anything - the cradle's lesson.</li>
 *   <li><b>Draw it off</b> through a tap. The tank only ever gives; essence is not something a well is fed.</li>
 *   <li><b>Scram it</b> with a redstone pulse on any block of the frame: the gullet is thrown away and the hatch
 *       shut for a while. The strain already there is left to be damped.</li>
 *   <li><b>Break it open</b>, which puts the whole gullet into the tear at once.</li>
 * </ul>
 *
 * <p>An unloaded well does not tick, and for a grace after loading it neither digests nor strains: Create
 * reports no rotation for the first ticks after a load, and a well must not take that for a stopped shaft.
 */
public class RiftCollarBlockEntity extends KineticBlockEntity {

    /** How far past its frame a strained well reaches the living, in blocks. */
    private static final int REACH_PAST_FRAME = 4;

    /** Seconds ahead the heading looks. Long enough to tell a well that settles from one that creeps. */
    private static final int HEADING_SECONDS = 180;

    private ItemStack crystal = ItemStack.EMPTY;

    /** How far behind the collar its fissure is, in a formed frame; nought while it holds none. */
    private int radius;

    private WellheadFailure failure = WellheadFailure.NO_FISSURE;
    @Nullable
    private BlockPos culprit;

    private WellheadReactor reactor = new WellheadReactor();

    /** Where the tear is and how big the frame was, for a well broken open with strain still on it. */
    @Nullable
    private BlockPos breachCentre;
    private int breachRadius;

    /** Sized to the frame as it forms; the figure here is only what a collar holds before it has one. */
    private final FluidTank tank = new FluidTank(4_000,
            stack -> stack.getFluid() == AWFluids.RIFT_ESSENCE.get()) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    /** What a pipe finds: the tank, to take from and never to put into. */
    private final IFluidHandler tap = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int index) {
            return tank.getFluid();
        }

        @Override
        public int getTankCapacity(int index) {
            return tank.getCapacity();
        }

        @Override
        public boolean isFluidValid(int index, FluidStack stack) {
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return tank.drain(resource, action);
        }

        @Override
        public FluidStack drain(int most, FluidAction action) {
            return tank.drain(most, action);
        }
    };

    /** What a funnel finds: a slot that is always empty, and takes only what the well digests. */
    private final IItemHandler hatch = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            int taken = accepts(stack);
            if (taken <= 0) {
                return stack;
            }
            if (!simulate) {
                swallow(stack, taken);
            }
            return stack.copyWithCount(stack.getCount() - taken);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return recipeFor(stack) != null;
        }
    };

    private int secondTimer;
    private int graceTicks;
    private int lockoutSeconds;
    private boolean powered;

    /** What the cage came to at the last look, and where the strain is going: for the goggles, and sent. */
    private int stabilisers;
    private double imbalance;
    private double heading;
    private boolean storm;

    /** What rotation and the cage each gave of their damping at the last look, for the panel. */
    private double speedShare;
    private double cageShare;
    private int needed;

    /** Shut from the panel: the hatch takes nothing until it is opened again. Not a scram - nothing is thrown away. */
    private boolean hatchShut;

    private int lastSignal;
    private double syncedStrain = -1.0D;

    public RiftCollarBlockEntity(BlockPos pos, BlockState state) {
        super(AWBlockEntities.RIFT_COLLAR.get(), pos, state);
    }

    // ------------------------------------------------------------------ the frame

    /** The side of the frame this collar is in: the way it faces, out of the machine. */
    public Direction side() {
        return getBlockState().getValue(HorizontalKineticBlock.HORIZONTAL_FACING);
    }

    public int radius() {
        return radius;
    }

    public boolean isFormed() {
        return radius > 0;
    }

    /** Where the fissure is. Only meaningful while formed. */
    public BlockPos centre() {
        return WellheadStructure.centre(worldPosition, side(), radius);
    }

    @Nullable
    public WellheadTier tier() {
        return WellheadTier.ofRadius(radius);
    }

    /** Called by {@link WellheadAssembly} alone, as a frame forms or comes apart. */
    void setRadius(int radius) {
        if (this.radius == radius) {
            return;
        }
        this.radius = radius;
        WellheadTier tier = tier();
        if (tier != null) {
            tank.setCapacity(AWConfig.WELLHEAD_TANK.get(tier).get());
            failure = WellheadFailure.NONE;
            culprit = null;
            breachCentre = null;
        }
        if (hasNetwork()) {
            getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
        }
        setChanged();
        sendData();
        tellComparators(true);
    }

    void noteSurvey(WellheadAssembly.Survey survey) {
        if (survey.failure() == failure && java.util.Objects.equals(survey.culprit(), culprit)) {
            return;
        }
        failure = survey.failure();
        culprit = survey.culprit();
        Trace.log(Trace.WELLHEAD, "collar at {} would not form: {} (fissure {} back, at fault {})",
                Trace.pos(worldPosition), failure.getSerializedName(), survey.radius(),
                culprit == null ? "-" : Trace.pos(culprit));
        setChanged();
        sendData();
    }

    /** A formed well draws its tier's stress; a loose collar is a shaft's end and draws nothing. */
    @Override
    public float calculateStressApplied() {
        WellheadTier tier = tier();
        float impact = tier == null ? 0.0F : (float) (double) AWConfig.WELLHEAD_STRESS.get(tier).get();
        this.lastStressApplied = impact;
        return impact;
    }

    // ------------------------------------------------------------------ the crystal

    /** The tier a crystal permits, or {@code null} for anything that is not one. */
    @Nullable
    public static WellheadTier tierOf(ItemStack stack) {
        if (stack.is(AWItems.PEARLESCENT_WARP_CRYSTAL.get())) {
            return WellheadTier.III;
        }
        if (stack.is(AWItems.FOLDED_WARP_CRYSTAL.get())) {
            return WellheadTier.II;
        }
        return stack.is(AWItems.WARP_CRYSTAL.get()) ? WellheadTier.I : null;
    }

    @Nullable
    public WellheadTier crystalTier() {
        return tierOf(crystal);
    }

    public ItemStack crystal() {
        return crystal;
    }

    /** Whether nothing is in it and nothing is on it: the only state a crystal comes back out in. */
    public boolean isCold() {
        return reactor.matter() <= 0.0D && reactor.strain() <= 0.0D;
    }

    void seat(Player player, InteractionHand hand, ItemStack stack) {
        if (!isCold()) {
            AWLang.translate("gui.rift_collar.not_cold").style(ChatFormatting.RED).sendStatus(player);
            return;
        }
        ItemStack old = crystal;
        crystal = stack.copyWithCount(1);
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        if (!old.isEmpty()) {
            player.getInventory().placeItemBackInInventory(old);
        }
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 0.8F, 1.1F);
            WellheadAssembly.check(level, this);
        }
        setChanged();
        sendData();
    }

    void unseat(Player player) {
        if (crystal.isEmpty()) {
            report(player);
            return;
        }
        if (!isCold()) {
            AWLang.translate("gui.rift_collar.not_cold").style(ChatFormatting.RED).sendStatus(player);
            return;
        }
        player.getInventory().placeItemBackInInventory(crystal);
        crystal = ItemStack.EMPTY;
        if (level != null) {
            WellheadAssembly.check(level, this);
        }
        setChanged();
        sendData();
    }

    /** Opens the panel of a formed well, or says in the action bar the reason the collar holds no frame. */
    void report(Player player) {
        if (isFormed()) {
            if (player instanceof net.minecraft.server.level.ServerPlayer server) {
                showPanel(server, true);
            }
        } else {
            AWLang.translate(failure.translationKey()).style(ChatFormatting.RED).sendStatus(player);
        }
    }

    void onCollarRemoved() {
        if (level == null) {
            return;
        }
        if (isFormed()) {
            onBreached();
            WellheadAssembly.unform(level, this, null);
        }
        if (!crystal.isEmpty()) {
            Block.popResource(level, worldPosition, crystal);
            crystal = ItemStack.EMPTY;
        }
        // The collar is what was counting. With it gone, whatever strain was on the tear has nothing left to
        // run it down or carry it over, so a well taken apart at the brink goes over it here.
        if (breachCentre != null && reactor.band() == WellheadReactor.Band.MELTDOWN
                && level instanceof ServerLevel server) {
            meltdown(server);
        }
    }

    /** The frame has been broken open: everything in the gullet goes into the tear at once. */
    void onBreached() {
        WellheadTier tier = tier();
        if (tier == null) {
            return;
        }
        breachCentre = centre();
        breachRadius = radius;
        reactor.breach(AWConfig.WELLHEAD_GULLET.get(tier).get());
        Trace.log(Trace.WELLHEAD, "collar at {} broken open: strain now {}", Trace.pos(worldPosition),
                Trace.percent(reactor.strain()));
        setChanged();
    }

    // ------------------------------------------------------------------ feeding and drawing off

    public IFluidHandler tap() {
        return tap;
    }

    public IItemHandler hatch() {
        return hatch;
    }

    public FluidTank tank() {
        return tank;
    }

    @Nullable
    private AgitatingRecipe recipeFor(ItemStack stack) {
        if (level == null || stack.isEmpty()) {
            return null;
        }
        return AWRecipeTypes.AGITATING.<SingleRecipeInput, AgitatingRecipe>find(new SingleRecipeInput(stack), level)
                .map(RecipeHolder::value).orElse(null);
    }

    /** How many of a stack the hatch will take now: none unless the well is formed, open and has the room. */
    private int accepts(ItemStack stack) {
        WellheadTier tier = tier();
        if (tier == null || lockoutSeconds > 0 || hatchShut || reactor.band() == WellheadReactor.Band.MELTDOWN) {
            return 0;
        }
        AgitatingRecipe recipe = recipeFor(stack);
        if (recipe == null || recipe.essence() <= 0) {
            return 0;
        }
        double room = AWConfig.WELLHEAD_GULLET.get(tier).get() - reactor.matter();
        return (int) Math.max(0L, Math.min(stack.getCount(), (long) Math.floor(room / recipe.essence())));
    }

    private void swallow(ItemStack stack, int count) {
        AgitatingRecipe recipe = recipeFor(stack);
        if (recipe == null) {
            return;
        }
        reactor.feed((double) recipe.essence() * count, recipe.strain());
        setChanged();
    }

    // ------------------------------------------------------------------ redstone

    /** Fifteen means critical or worse, and nothing less does - see {@link WellheadSignal}. */
    public int comparatorSignal() {
        return WellheadSignal.of(isFormed(), reactor.strain());
    }

    /**
     * A signal at any block of the frame is a signal to the well, and a rising one is a scram. Read on a
     * neighbour's change rather than each tick: a frame has two hundred blocks a signal could arrive at.
     */
    void readSignal() {
        if (level == null) {
            return;
        }
        boolean now = level.hasNeighborSignal(worldPosition);
        if (!now && isFormed()) {
            BlockPos centre = centre();
            for (Vec3i offset : WellheadStructure.shell(radius)) {
                if (level.hasNeighborSignal(centre.offset(offset))) {
                    now = true;
                    break;
                }
            }
        }
        if (now && !powered) {
            scram();
        }
        powered = now;
    }

    /** Throws the gullet away and shuts the hatch. Nothing once a meltdown has begun: there is nothing to save. */
    public boolean scram() {
        if (!isFormed() || reactor.band() == WellheadReactor.Band.MELTDOWN) {
            return false;
        }
        double lost = reactor.matter();
        // Pulled back from the brink, with matter in it: the one scram worth remembering.
        if (lost > 0.0D && reactor.band() == WellheadReactor.Band.CRITICAL && level instanceof ServerLevel server) {
            uk.co.iceconchy.aerowarptics.advancement.AWCriteria.wellhead(server, worldPosition,
                    uk.co.iceconchy.aerowarptics.advancement.WellheadTrigger.SCRAMMED, 32.0D);
        }
        reactor.dump();
        lockoutSeconds = AWConfig.WELLHEAD_SCRAM_LOCKOUT.get();
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.2F, 0.6F);
        }
        Trace.log(Trace.WELLHEAD, "collar at {} scrammed at strain {}: {} mB of matter thrown away, hatch shut {} s",
                Trace.pos(worldPosition), Trace.percent(reactor.strain()), Trace.num(lost), lockoutSeconds);
        setChanged();
        sendData();
        return true;
    }

    // ------------------------------------------------------------------ the panel

    /** Whether a player is near enough the machine to give it orders: beside any block of its frame, more or less. */
    public boolean inReach(Player player) {
        double reach = 8.0D + 2.0D * radius;
        return isFormed() && !isRemoved() && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(worldPosition))
                <= reach * reach;
    }

    /** Opens or shuts the hatch from the panel. Shut, it takes nothing; what is already in the gullet stays. */
    public void toggleHatch() {
        hatchShut = !hatchShut;
        if (level != null) {
            level.playSound(null, worldPosition, hatchShut ? SoundEvents.IRON_TRAPDOOR_CLOSE : SoundEvents.IRON_TRAPDOOR_OPEN,
                    SoundSource.BLOCKS, 1.0F, 0.8F);
        }
        setChanged();
    }

    /**
     * Tells a player's panel how the well stands.
     *
     * @param open whether this answers a click on the machine, and so opens the panel
     */
    public void showPanel(net.minecraft.server.level.ServerPlayer player, boolean open) {
        WellheadTier tier = tier();
        if (tier == null) {
            return;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new uk.co.iceconchy.aerowarptics.network.ClientboundWellheadPanelPacket(worldPosition, tier.ordinal(),
                        (float) reactor.strain(), (float) heading, (float) reactor.integrity(),
                        (int) Math.round(reactor.matter()), AWConfig.WELLHEAD_GULLET.get(tier).get(), tank.getFluidAmount(),
                        tank.getCapacity(), stabilisers, needed, (float) imbalance, (float) speedShare, (float) cageShare,
                        (float) (reactor.matter() > 0.0D ? reactor.rate(settings(tier), storm) : 0.0D), lockoutSeconds,
                        hatchShut, storm, open));
    }

    public void showPanel(net.minecraft.server.level.ServerPlayer player) {
        showPanel(player, false);
    }

    /** For an operator's command, and nothing else - see {@link WellheadCommands}. */
    void forceStrain(double strain) {
        reactor.forceStrain(strain);
        setChanged();
        sendData();
    }

    private void tellComparators(boolean force) {
        int signal = comparatorSignal();
        if (level == null || level.isClientSide || (!force && signal == lastSignal)) {
            return;
        }
        lastSignal = signal;
        level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        if (isFormed()) {
            BlockPos centre = centre();
            for (Vec3i offset : WellheadStructure.shell(radius)) {
                BlockPos cell = centre.offset(offset);
                if (!cell.equals(worldPosition) && level.isLoaded(cell)) {
                    level.updateNeighbourForOutputSignal(cell, level.getBlockState(cell).getBlock());
                }
            }
        }
    }

    // ------------------------------------------------------------------- tick

    @Override
    public void onLoad() {
        super.onLoad();
        graceTicks = AWConfig.SERVER_SPEC.isLoaded() ? AWConfig.WELLHEAD_RESUME_GRACE.get() : 60;
    }

    @Override
    public void tick() {
        // Before super.tick(): a loose collar can be aboard a hull, and a block aboard one is still ticking the
        // tick after Sable has wiped its plot - see Airship.orphaned.
        if (Airship.orphaned(this)) {
            return;
        }
        super.tick();
        if (graceTicks > 0) {
            graceTicks--;
        }
        if (!(level instanceof ServerLevel server) || ++secondTimer < 20) {
            return;
        }
        secondTimer = 0;
        if (AWConfig.WELLHEAD_ENABLED.get()) {
            WellheadAssembly.check(server, this);
        } else if (isFormed()) {
            WellheadAssembly.unform(server, this, null);
        }
        if (lockoutSeconds > 0) {
            lockoutSeconds--;
            // The goggles count it down, and they are the client's.
            sendData();
        }
        if (isFormed()) {
            work(server);
        } else if (breachCentre != null) {
            workBreached(server);
        }
        // A meltdown takes the collar with the ground it stood on.
        if (isRemoved()) {
            return;
        }
        tellComparators(false);
        if (Math.abs(reactor.strain() - syncedStrain) >= 0.01D) {
            syncedStrain = reactor.strain();
            sendData();
        }
    }

    private WellheadReactor.Settings settings(WellheadTier tier) {
        return new WellheadReactor.Settings(AWConfig.WELLHEAD_DIGESTION.get(tier).get(), AWConfig.WELLHEAD_YIELD_GAIN.get(),
                AWConfig.WELLHEAD_PRESSURE.get(), AWConfig.WELLHEAD_FEEDBACK.get(), AWConfig.WELLHEAD_DAMPING.get(),
                AWConfig.WELLHEAD_CAGE_FLOOR.get(), 0.75D, AWConfig.WELLHEAD_STORM_FACTOR.get(), 0.05D, 0.002D,
                0.01D, 0.005D, 0.4D, AWConfig.WELLHEAD_BLEED.get());
    }

    /** One second of a formed well. */
    private void work(ServerLevel server) {
        WellheadTier tier = tier();
        BlockPos centre = centre();
        if (tier == null || !(server.getBlockEntity(centre) instanceof RiftFissureBlockEntity fissure)) {
            return;
        }
        int reach = AWConfig.WELLHEAD_STABILISER_REACH.get();
        List<double[]> cage = new ArrayList<>();
        for (int[] offset : CradleRing.stabilisers(reach, (dx, dy, dz) ->
                SpatialStabiliserBlock.switchedOn(server.getBlockState(centre.offset(dx, dy, dz))))) {
            cage.add(new double[]{offset[0], offset[1], offset[2]});
        }
        stabilisers = cage.size();
        imbalance = CollapseInstability.imbalance(cage, reach);
        storm = RiftStorm.ragingOver(server);
        double minimum = AWConfig.WELLHEAD_MINIMUM_SPEED.get(tier).get();
        WellheadReactor.Conditions conditions = new WellheadReactor.Conditions(Math.abs(getSpeed()), minimum,
                minimum * 2.0D, stabilisers, AWConfig.WELLHEAD_STABILISERS.get(tier).get(), imbalance, storm,
                tolerance(fissure), tank.getCapacity() - tank.getFluidAmount(), false, graceTicks > 0);
        WellheadReactor.Settings settings = settings(tier);
        needed = conditions.needed();
        speedShare = WellheadReactor.speedShare(conditions);
        cageShare = WellheadReactor.cage(conditions, settings);
        WellheadReactor.Band before = reactor.band();
        WellheadReactor.Step step = reactor.step(conditions, settings);
        heading = reactor.heading(conditions, settings, HEADING_SECONDS);
        if (step.essence() > 0) {
            tank.fill(new FluidStack(AWFluids.RIFT_ESSENCE.get(), step.essence()), IFluidHandler.FluidAction.EXECUTE);
        }
        // A tear gapes wider the harder it is strained, from the size it hangs at when left alone.
        fissure.gape((float) (0.4D + 0.6D * reactor.strain()));
        if (step.band() != before) {
            Trace.log(Trace.WELLHEAD, "collar at {} is now {}: strain {}, heading {}, speed {}, cage {}/{} (lopsided {}), "
                            + "integrity {}, storm {}, tank room {} mB, matter {} mB",
                    Trace.pos(worldPosition), step.band(), Trace.percent(reactor.strain()), Trace.percent(heading),
                    Trace.num(getSpeed()), stabilisers, conditions.needed(), Trace.num(imbalance),
                    Trace.percent(reactor.integrity()), storm, conditions.tankRoom(), Trace.num(reactor.matter()));
        }
        if (step.band().ordinal() >= WellheadReactor.Band.STRAINING.ordinal()) {
            trouble(server, centre, step.band());
        }
        if (step.band() == WellheadReactor.Band.CRITICAL) {
            // The alarm: two notes, a second each, carried well past the frame. Heard is the point of it.
            server.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 4.0F,
                    (server.getGameTime() / 20L) % 2L == 0L ? 0.6F : 0.8F);
        }
        if (step.band() == WellheadReactor.Band.MELTDOWN) {
            breachCentre = centre;
            breachRadius = radius;
            WellheadAssembly.unform(server, this, null);
            meltdown(server);
            return;
        }
        // Bled dry, the tear seals as a drained one does - heart and all - and the frame is let go whole.
        if (step.bleed() > 0 && fissure.bleed(step.bleed())) {
            Trace.log(Trace.WELLHEAD, "collar at {} bled its fissure dry: frame let go", Trace.pos(worldPosition));
            reactor.dump();
            WellheadAssembly.unform(server, this, null);
        }
        setChanged();
    }

    /** A well broken open with strain still on it: nothing damps it but the tear's own slow leak. */
    private void workBreached(ServerLevel server) {
        WellheadTier tier = WellheadTier.ofRadius(breachRadius);
        if (tier == null || reactor.strain() <= 0.0D) {
            breachCentre = null;
            return;
        }
        WellheadReactor.Conditions open = new WellheadReactor.Conditions(0.0D, 1.0D, 2.0D, 0, 0, 0.0D, false, 1.0D,
                0, true, graceTicks > 0);
        if (reactor.step(open, settings(tier)).band() == WellheadReactor.Band.MELTDOWN) {
            meltdown(server);
        }
        setChanged();
    }

    private void meltdown(ServerLevel server) {
        WellheadTier tier = WellheadTier.ofRadius(breachRadius);
        BlockPos centre = breachCentre;
        breachCentre = null;
        reactor = new WellheadReactor();
        tank.setFluid(FluidStack.EMPTY);
        if (tier != null && centre != null) {
            WellheadMeltdown.begin(server, centre, tier, breachRadius);
        }
        setChanged();
        sendData();
    }

    /** A large tear takes more before it tears: three quarters of the pressure for the largest, five for the smallest. */
    private static double tolerance(RiftFissureBlockEntity fissure) {
        double least = AWConfig.FISSURE_RESERVOIR_LEAST.get();
        double most = Math.max(least + 1.0D, AWConfig.FISSURE_RESERVOIR_MOST.get());
        double share = Math.max(0.0D, Math.min(1.0D, (fissure.original() - least) / (most - least)));
        return 0.8D + 0.45D * share;
    }

    /**
     * What a strained well does to the living near it: a straining one hops them about, as a rifting fan's
     * stream does, and a tearing one hurts them. Never anything aboard a ship, and never an armour stand.
     */
    private void trouble(ServerLevel server, BlockPos centre, WellheadReactor.Band band) {
        boolean tearing = band.ordinal() >= WellheadReactor.Band.TEARING.ordinal();
        float harm = (float) (double) AWConfig.WELLHEAD_HARM.get();
        for (LivingEntity living : server.getEntitiesOfClass(LivingEntity.class,
                new AABB(centre).inflate(radius + REACH_PAST_FRAME))) {
            if (living instanceof ArmorStand || !living.isAlive() || !RiftHop.mayHop(living)) {
                continue;
            }
            if (tearing && harm > 0.0F) {
                living.hurt(uk.co.iceconchy.aerowarptics.registry.AWDamageTypes.fallout(server,
                        net.minecraft.world.phys.Vec3.atCenterOf(centre)), harm);
            }
            if (AWConfig.RIFTING_HOPS.get() && server.random.nextInt(tearing ? 3 : 8) == 0) {
                RiftHop.hop(server, living, AWConfig.RIFTING_HOP_RANGE.get());
            }
        }
    }

    // -------------------------------------------------------------------- nbt

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!crystal.isEmpty()) {
            tag.put("Crystal", crystal.save(registries));
        }
        tag.putInt("Radius", radius);
        tag.putString("Failure", failure.getSerializedName());
        if (culprit != null) {
            tag.putLong("Culprit", culprit.asLong());
        }
        tag.putDouble("Matter", reactor.matter());
        tag.putDouble("FeedStrain", reactor.feedStrain());
        tag.putDouble("Strain", reactor.strain());
        tag.putDouble("Integrity", reactor.integrity());
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putInt("TankCapacity", tank.getCapacity());
        tag.putInt("Lockout", lockoutSeconds);
        tag.putBoolean("Powered", powered);
        tag.putBoolean("HatchShut", hatchShut);
        if (breachCentre != null) {
            tag.putLong("BreachCentre", breachCentre.asLong());
            tag.putInt("BreachRadius", breachRadius);
        }
        if (clientPacket) {
            tag.putInt("Stabilisers", stabilisers);
            tag.putDouble("Imbalance", imbalance);
            tag.putDouble("Heading", heading);
            tag.putBoolean("Storm", storm);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        crystal = tag.contains("Crystal") ? ItemStack.parseOptional(registries, tag.getCompound("Crystal")) : ItemStack.EMPTY;
        radius = Math.max(0, Math.min(WellheadStructure.MAX_RADIUS, tag.getInt("Radius")));
        failure = WellheadFailure.NONE;
        for (WellheadFailure candidate : WellheadFailure.values()) {
            if (candidate.getSerializedName().equals(tag.getString("Failure"))) {
                failure = candidate;
            }
        }
        culprit = tag.contains("Culprit") ? BlockPos.of(tag.getLong("Culprit")) : null;
        reactor = new WellheadReactor(tag.getDouble("Matter"), tag.getDouble("FeedStrain"), tag.getDouble("Strain"),
                tag.contains("Integrity") ? tag.getDouble("Integrity") : 1.0D);
        if (tag.contains("TankCapacity")) {
            tank.setCapacity(Math.max(1_000, tag.getInt("TankCapacity")));
        }
        tank.readFromNBT(registries, tag.getCompound("Tank"));
        lockoutSeconds = tag.getInt("Lockout");
        powered = tag.getBoolean("Powered");
        hatchShut = tag.getBoolean("HatchShut");
        breachCentre = tag.contains("BreachCentre") ? BlockPos.of(tag.getLong("BreachCentre")) : null;
        breachRadius = tag.getInt("BreachRadius");
        if (clientPacket) {
            stabilisers = tag.getInt("Stabilisers");
            imbalance = tag.getDouble("Imbalance");
            heading = tag.getDouble("Heading");
            storm = tag.getBoolean("Storm");
        }
    }

    // --------------------------------------------------------------- goggles

    public double strain() {
        return reactor.strain();
    }

    /** Essence still to be had from what is in the gullet, in millibuckets. */
    public double matter() {
        return reactor.matter();
    }

    public double integrity() {
        return reactor.integrity();
    }

    public double heading() {
        return heading;
    }

    public WellheadFailure failure() {
        return failure;
    }

    private static ChatFormatting bandColour(WellheadReactor.Band band) {
        return switch (band) {
            case CALM -> ChatFormatting.AQUA;
            case STRAINING -> ChatFormatting.YELLOW;
            case TEARING -> ChatFormatting.GOLD;
            default -> ChatFormatting.RED;
        };
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        AWLang.translate("gui.goggles.rift_wellhead").forGoggles(tooltip);
        WellheadTier tier = tier();
        if (tier == null) {
            AWLang.translate(failure.translationKey()).style(ChatFormatting.RED).forGoggles(tooltip, 1);
            if (culprit != null) {
                AWLang.translate("gui.rift_collar.at_fault", culprit.getX(), culprit.getY(), culprit.getZ())
                        .style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
            }
        } else {
            WellheadReactor.Band band = reactor.band();
            AWLang.translate("gui.rift_collar.strain", (int) Math.round(reactor.strain() * 100.0D),
                            AWLang.translate(band.translationKey()).component())
                    .style(bandColour(band)).forGoggles(tooltip, 1);
            AWLang.translate("gui.rift_collar.heading", (int) Math.round(heading * 100.0D))
                    .style(bandColour(WellheadReactor.Band.of(heading))).forGoggles(tooltip, 1);
            AWLang.translate("gui.rift_collar.cage", stabilisers, (int) Math.round(reactor.integrity() * 100.0D))
                    .style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
            AWLang.translate("gui.rift_collar.tank", AWLang.count(tank.getFluidAmount()), AWLang.count(tank.getCapacity()))
                    .style(ChatFormatting.LIGHT_PURPLE).forGoggles(tooltip, 1);
            AWLang.translate("gui.rift_collar.gullet", AWLang.count(reactor.matter()))
                    .style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
            if (lockoutSeconds > 0) {
                AWLang.translate("gui.rift_collar.lockout", lockoutSeconds).style(ChatFormatting.RED).forGoggles(tooltip, 1);
            }
        }
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        return true;
    }
}
