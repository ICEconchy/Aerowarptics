package uk.co.iceconchy.aerowarptics.collapse;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
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
import uk.co.iceconchy.aerowarptics.detector.SpatialWaveDetectorBlockEntity;
import uk.co.iceconchy.aerowarptics.fold.FoldingRecipe;
import uk.co.iceconchy.aerowarptics.hold.Holds;
import uk.co.iceconchy.aerowarptics.modulator.RiftModulatorBlockEntity;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.registry.AWDamageTypes;
import uk.co.iceconchy.aerowarptics.registry.AWFluids;
import uk.co.iceconchy.aerowarptics.registry.AWParticles;
import uk.co.iceconchy.aerowarptics.registry.AWRecipeTypes;
import uk.co.iceconchy.aerowarptics.registry.AWSounds;
import uk.co.iceconchy.aerowarptics.stabiliser.SpatialStabiliserBlock;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.weather.RiftStorm;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Collapse Cradle's ritual: a Rift Core and a Scar Heart, collapsed into a Singularity Core.
 *
 * <p>The sequence, about a minute long and started on a redstone rising edge:
 * <ol>
 *   <li><b>Pre-flight.</b> Named refusals ({@link CollapseFailure}), with nothing spent.</li>
 *   <li><b>Spin-up.</b> The offerings are taken off their depots all at once - drawn lifting off them - and
 *       the core rises.</li>
 *   <li><b>Opening.</b> A rift opens over the cradle, dressed by a Rift Modulator beside it if there is one,
 *       the offerings fall into orbit round it, and the essence begins to burn.</li>
 *   <li><b>Winding.</b> The rift folds into a hole. Instability builds from live conditions - see
 *       {@link CollapseInstability} - and loose things round the cradle are drawn in.</li>
 *   <li><b>Collapse.</b> Everything falls to a point, and the outcome is decided by how unstable it got: the
 *       core on the cradle, the core flung clear, or - spat - nothing made and the Rift Core and every
 *       offering thrown back out whole. Only the heart and the essence are ever the stake. The blast knocks
 *       back whatever stands close, and an unsteady one hurts it; detectors round about pulse.</li>
 * </ol>
 *
 * <p>What it looks like is {@link CollapseChoreography}'s, drawn on each client from what this sends: how
 * far through it is and whether it is held, where its instability is heading, and the offerings with the
 * depots they came off. The client runs the clock on between syncs, so the scene is smooth.
 *
 * <p>A ritual holds its own ground while it runs, on the expiring reach ticket through {@link Holds}, and
 * pauses whenever it cannot tick - see {@link CollapseRitual}. It never runs aboard a ship, and cannot be put on
 * one: the machine is three blocks each way and will not be moved (see {@link CollapseCradleBlock}).
 */
public class CollapseCradleBlockEntity extends KineticBlockEntity implements GeoBlockEntity,
        net.minecraft.world.MenuProvider, RelicMaster {

    /** The arms: a Rift Core to collapse, or a stack to fold. */
    public static final int CORE = 0;
    /** The cup: the Scar Heart, or what a fold is folded with. */
    public static final int HEART = 1;
    /** What the cradle has made - a fold's stack, a settled collapse's core - for a hand or a funnel to take. */
    public static final int OUTPUT = 2;
    public static final int SLOTS = 3;

    /** Eight buckets: twice what the built-in recipe burns, so one ritual never needs topping up mid-way. */
    public static final int TANK_CAPACITY = 8 * AWFluids.BUCKET;

    private static final int SYNC_INTERVAL = 10;
    private static final int SURVEY_INTERVAL = 20;
    /** How far a client's clock may drift from the server's before it is set straight rather than left be. */
    private static final int RESYNC_TICKS = 4;
    /** How finely the blast samples something's box when asking how much of it the point can see. */
    private static final int COVER_SAMPLES = 3;
    /** How long a flung core trails sparks, at most. */
    private static final int COMET_TICKS = 60;
    /** Beyond the arms' own return, how long a client's rings may take to lie flat after a short ritual. */
    private static final int SETTLE_SLACK = 20;
    /** How far the collapse's boom carries: a sound's reach is sixteen blocks a unit of volume. */
    private static final float BOOM_VOLUME = 8.0F;

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    /**
     * The cradle's three slots and what may go in each - one Rift Core and one Scar Heart at a time, up to a stack
     * of sixteen to fold, nothing into the output but what the cradle makes. Shared by the block entity and the
     * copy its panel keeps on a client, so the panel never lets a hand put in what the cradle would refuse.
     */
    public static class Slots extends ItemStackHandler {

        public Slots() {
            super(SLOTS);
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == OUTPUT ? 64 : CradleFold.MOST;
        }

        @Override
        protected int getStackLimit(int slot, ItemStack stack) {
            if (slot == CORE && stack.is(uk.co.iceconchy.aerowarptics.registry.AWItems.RIFT_CORE.get())
                    || slot == HEART && stack.is(uk.co.iceconchy.aerowarptics.registry.AWItems.SCAR_HEART.get())) {
                return 1;
            }
            return Math.min(getSlotLimit(slot), stack.getMaxStackSize());
        }

        /** Nothing is put in the output but what the cradle makes, which it writes straight in. */
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot != OUTPUT;
        }
    }

    private final Slots slots = new Slots() {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) {
                sendData();
            }
        }
    };

    /**
     * What a funnel sees: the arms and the cup to fill while the cradle is idle, and the output to empty
     * whenever. A funnel pulling from the cradle takes only what it made, never the core just put in.
     */
    private final IItemHandler automation = new IItemHandler() {
        @Override
        public int getSlots() {
            return SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slots.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return busy() || slot == OUTPUT ? stack : slots.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == OUTPUT ? slots.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slots.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot != OUTPUT;
        }
    };

    private final FluidTank tank = new FluidTank(TANK_CAPACITY,
            stack -> stack.getFluid() == AWFluids.RIFT_ESSENCE.get()) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide) {
                sendData();
            }
        }
    };

    /** The ritual under way, or {@code null}. */
    @Nullable
    private CollapseRitual ritual;

    /**
     * The fold under way, if {@link #foldLength} is above zero: which recipe, how many crafts, what it makes (for
     * the client to draw before it is made), and how far through it is. Never at the same time as a ritual.
     */
    @Nullable
    private ResourceLocation foldRecipe;
    private int foldLength;
    private int foldElapsed;
    private int foldCount;
    private ItemStack foldResult = ItemStack.EMPTY;
    /** Counts up by one at every finished fold, so a client can see one finish. */
    private int folds;
    /**
     * Ticks until the arms are home after a collapse, on the server. Nothing new begins before: a fold or a
     * collapse starting while the arms were still coming down would snap them from wherever they were into its
     * first pose. The clients' arms wait for the rings to lie flat first, so this allows for that too. Not saved -
     * after a reload the arms are home.
     */
    private int settling;
    @Nullable
    private ResourceLocation recipeId;
    /** The offerings taken off the depots for the ritual under way, and where each came from. */
    private final List<ItemStack> drawn = new ArrayList<>();
    private final List<BlockPos> drawnFrom = new ArrayList<>();
    private int essenceTotal;
    private int essencePaid;
    private int essenceShort;

    private boolean powered;
    private CollapseFailure lastFailure = CollapseFailure.NONE;
    private String lastOutcome = "";
    private int holdTimer;
    private int syncTimer;

    /** The survey of the ring, refreshed every second while a ritual runs. */
    private int stabilisers;
    private double imbalance = 1.0D;
    private int surveyTimer;

    /**
     * How far the cage reaches, as the server has it: sent so a client can find the stabilisers the cradle counts.
     */
    private int stabiliserReach = 6;

    /**
     * The depots the cradle is linked to, each as its offset from the master, in the order it took them: eight
     * at most, and one keeps its place until it goes - see {@link CradleRing#linked}. Saved, because which eight
     * of more is the order they were set down in and the world does not remember that; and sent, because a client
     * cannot work it out either.
     */
    private List<int[]> linkedDepots = new ArrayList<>();
    private int ringTimer;

    /** Whether the last tick held rather than moved, and where the instability is heading - both sent. */
    private boolean held;
    private double heading;

    /** A flung core, trailing sparks until it lands. Not saved: a comet is a moment. */
    @Nullable
    private ItemEntity comet;
    private int cometTicks;

    /** What the client is told, for the goggles and the rift. */
    private boolean syncedRunning;
    private boolean syncedFolding;
    private float syncedProgress;
    private float syncedInstability;
    /** Counts up by one at every collapse, so a client can see one happen without missing a packet. */
    private int collapses;

    /**
     * The client's own clock: the server's elapsed ticks as last sent, run on a tick at a time between syncs
     * unless the ritual is held, and set straight when it has drifted.
     */
    private float clientElapsed;
    private float clientLastElapsed;
    private int clientLength;
    private boolean clientHeld;
    private float clientHeading;
    /** The machine's rings, turned on this client from tick to tick - see {@link CradleWorks.Rings}. */
    private final CradleWorks.Rings rings = new CradleWorks.Rings();
    /** Ticks since a ritual last ended here, as this client saw it; held at the end once the arms are home. */
    private int clientAfter = CradleWorks.RETURN_TICKS;
    /** Whether this client has read the cradle once, so the collapse count it read first is not taken for one. */
    private boolean clientKnown;

    public CollapseCradleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public CollapseCradleBlockEntity(BlockPos pos, BlockState state) {
        this(AWBlockEntities.COLLAPSE_CRADLE.get(), pos, state);
    }

    /**
     * Asks a client to draw this machine, in case it arrived after the blocks round it - see {@link DrawnAboard} -
     * and asks the server to look the machine over, which puts back any part a world edit took away.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        DrawnAboard.redraw(this);
        if (ritual != null) {
            ritual.resume();
        }
        if (level instanceof ServerLevel server) {
            server.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }

    // ------------------------------------------------------------------ reading

    public IFluidHandler tank() {
        return tank;
    }

    public IItemHandler items() {
        return automation;
    }

    public FluidStack contents() {
        return tank.getFluid();
    }

    public ItemStack core() {
        return slots.getStackInSlot(CORE);
    }

    public ItemStack heart() {
        return slots.getStackInSlot(HEART);
    }

    /** What the cradle has made and is holding for someone to take. */
    public ItemStack output() {
        return slots.getStackInSlot(OUTPUT);
    }

    /**
     * What the arms are holding up: whatever is in them, or, when they are empty, what the cradle last made - a
     * settled core, a folded stack - which is where the arms caught it.
     */
    public ItemStack presented() {
        return core().isEmpty() ? output() : core();
    }

    /** Whether a collapse is under way. */
    public boolean running() {
        return level != null && level.isClientSide ? syncedRunning : ritual != null;
    }

    /** Whether a fold is under way. */
    public boolean folding() {
        return level != null && level.isClientSide ? syncedFolding : foldLength > 0;
    }

    /** Whether the cradle is at work at all: its slots are shut while it is. */
    public boolean busy() {
        return running() || folding();
    }

    /** 0..1 through the fold under way, between ticks on a client; 0 when there is none. */
    public float foldProgress(float partialTick) {
        if (level != null && level.isClientSide) {
            return !syncedFolding || clientLength <= 0 ? 0.0F
                    : Mth.clamp(Mth.lerp(partialTick, clientLastElapsed, clientElapsed) / clientLength, 0.0F, 1.0F);
        }
        return foldLength <= 0 ? 0.0F : Mth.clamp(foldElapsed / (float) foldLength, 0.0F, 1.0F);
    }

    /** What the fold under way will make, as it will come out - for drawing it before it is made. */
    public ItemStack foldResult() {
        return foldResult;
    }

    public int folds() {
        return folds;
    }

    /** 0..1 through the ritual; 0 when none is running. */
    public float progress() {
        return progress(1.0F);
    }

    /** 0..1 through the ritual, between ticks on a client; 0 when none is running. */
    public float progress(float partialTick) {
        if (level != null && level.isClientSide) {
            if (!syncedRunning) {
                return 0.0F;
            }
            return clientLength <= 0 ? syncedProgress
                    : Mth.clamp(Mth.lerp(partialTick, clientLastElapsed, clientElapsed) / clientLength, 0.0F, 1.0F);
        }
        return ritual == null ? 0.0F : (float) ritual.progress();
    }

    /** Whether the ritual is standing still: turned too slowly, or just resumed. */
    public boolean held() {
        return level != null && level.isClientSide ? clientHeld : held;
    }

    /**
     * Where the instability is heading, 0..1 - what it is now plus what the rest of the winding would add
     * as things stand. What a running collapse shows of how unsteady it is.
     */
    public float unrest() {
        return level != null && level.isClientSide ? clientHeading : (float) heading;
    }

    /** The offerings drawn for the ritual under way, and the depots they came off - one list each, in step. */
    public List<ItemStack> offerings() {
        return drawn;
    }

    public List<BlockPos> offeringDepots() {
        return drawnFrom;
    }

    public float instability() {
        return level != null && level.isClientSide ? syncedInstability
                : ritual == null ? 0.0F : (float) ritual.instability();
    }

    public int collapses() {
        return collapses;
    }

    public CollapseFailure lastFailure() {
        return lastFailure;
    }

    public String lastOutcome() {
        return lastOutcome;
    }

    /**
     * How far from the master a switched-on Spatial Stabiliser may stand and still steady a collapse. On a client,
     * as the server last said - it never reads the config.
     */
    public int stabiliserReach() {
        return level != null && !level.isClientSide ? AWConfig.COLLAPSE_STABILISER_REACH.get() : stabiliserReach;
    }

    /** The depots the cradle is linked to, as offsets from its master - on a client, as the server last said. */
    public List<int[]> linkedDepots() {
        return java.util.Collections.unmodifiableList(linkedDepots);
    }

    /** A Rift Modulator against the machine's side, whose look dresses the ritual's rift - or {@code null}. */
    @Nullable
    public RiftModulatorBlockEntity modulator() {
        if (level == null) {
            return null;
        }
        for (BlockPos beside : CradleStructure.around(worldPosition)) {
            if (level.getBlockEntity(beside) instanceof RiftModulatorBlockEntity modulator) {
                return modulator;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ the machine, on a client

    /** The rings, as this client is turning them. */
    public CradleWorks.Rings rings() {
        return rings;
    }

    /** How far the arms are through coming home after a ritual, 0..1 - 1 once they are, and at rest. */
    public float after(float partialTick) {
        return Math.min(1.0F, (clientAfter + (clientAfter < CradleWorks.RETURN_TICKS ? partialTick : 0.0F))
                / CradleWorks.RETURN_TICKS);
    }

    /** Whether the ritual that just ended left a core coming down out of the point for the arms to catch. */
    public boolean catching() {
        return clientAfter < CradleWorks.RETURN_TICKS && CollapseRules.Outcome.SETTLED.id().equals(lastOutcome);
    }

    // ------------------------------------------------------------------ hands

    /**
     * A click with something in hand puts it in, without opening the panel: a Scar Heart and anything that goes
     * with what the arms hold into the cup, everything else into the arms, as many as fit.
     */
    boolean putIn(Player player, InteractionHand hand) {
        if (busy()) {
            return false;
        }
        ItemStack held = player.getItemInHand(hand);
        int slot = cupFirst(held) ? HEART : CORE;
        ItemStack left = slots.insertItem(slot, held.copy(), false);
        if (left.getCount() == held.getCount()) {
            return false;
        }
        player.setItemInHand(hand, left);
        return true;
    }

    /** Whether something goes in the cup rather than the arms: the heart, or anything while the arms are full. */
    boolean cupFirst(ItemStack stack) {
        return stack.is(uk.co.iceconchy.aerowarptics.registry.AWItems.SCAR_HEART.get())
                || !core().isEmpty() && !ItemStack.isSameItemSameComponents(core(), stack);
    }

    /** The raw slots, for the cradle's panel, which applies its own rules about what may go where. */
    public Slots slots() {
        return slots;
    }

    void dropContents() {
        if (level == null) {
            return;
        }
        List<ItemStack> all = new ArrayList<>(drawn);
        for (int slot = 0; slot < SLOTS; slot++) {
            all.add(slots.getStackInSlot(slot));
        }
        for (ItemStack stack : all) {
            if (!stack.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + 0.5D,
                        worldPosition.getY() + 1.0D, worldPosition.getZ() + 0.5D, stack.copy());
            }
        }
        drawn.clear();
        drawnFrom.clear();
        for (int slot = 0; slot < SLOTS; slot++) {
            slots.setStackInSlot(slot, ItemStack.EMPTY);
        }
        ritual = null;
        clearFold();
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
        settleKinetics();
        if (level.isClientSide) {
            clientLastElapsed = clientElapsed;
            if ((syncedRunning && !clientHeld || syncedFolding) && clientElapsed < clientLength) {
                clientElapsed++;
            }
            rings.tick(getSpeed(), progress(1.0F), syncedRunning, syncedFolding ? foldProgress(1.0F) : -1.0D);
            // The arms come home only once the rings lie flat - a short ritual's hush can end before they have
            // braked, and nothing reaches in among rings still turning.
            if (!syncedRunning && clientAfter < CradleWorks.RETURN_TICKS && (clientAfter > 0 || rings.parked())) {
                clientAfter++;
            }
            AWClientHooks.tickCradle(this);
            return;
        }
        if (level instanceof ServerLevel server) {
            if (settling > 0) {
                settling--;
            }
            if (--ringTimer <= 0) {
                ringTimer = SURVEY_INTERVAL;
                lookAtRing(server);
            }
            if (ritual != null) {
                runRitual(server);
            } else if (foldLength > 0) {
                runFold(server);
            }
            if (comet != null) {
                trail(server);
            }
        }
    }

    /** The block's redstone, told on every neighbour change: a rising edge asks for a ritual. */
    void signal(boolean now) {
        boolean rising = now && !powered;
        powered = now;
        setChanged();
        if (rising && level instanceof ServerLevel server) {
            begin(server);
        }
    }

    /**
     * The panel's Start: the same as a rising edge at the machine's side, asked by a player at the panel - see
     * {@code ServerboundCradleStartPacket}, which checks they have it open.
     */
    public void start(ServerPlayer by) {
        if (level instanceof ServerLevel server) {
            Trace.log(Trace.COLLAPSE, "cradle at {} started from its panel by {}", Trace.pos(worldPosition),
                    Trace.who(by));
            begin(server);
        }
    }

    /**
     * Begins whatever the arms hold asks for: a collapse if it is a collapse's core, a fold if it is anything
     * else. A redstone rising edge and the panel's Start both come here, so they can never disagree.
     */
    private void begin(ServerLevel server) {
        if (!core().isEmpty() && !collapseCore(server, core())) {
            beginFold(server);
            return;
        }
        beginCollapse(server);
    }

    /** Whether something is a collapse's core: some collapse recipe asks for it in the arms. */
    private static boolean collapseCore(ServerLevel server, ItemStack stack) {
        for (RecipeHolder<CollapseRecipe> holder : server.getRecipeManager()
                .getAllRecipesFor(AWRecipeTypes.COLLAPSE.<CollapseRecipe.Altar, CollapseRecipe>getType())) {
            if (holder.value().core().test(stack)) {
                return true;
            }
        }
        return false;
    }

    private boolean aboard(ServerLevel server) {
        return Airship.containing(this) != null || Holds.inPlot(server, worldPosition);
    }

    /** What a collapse would do if it began now: the recipe the altar matches, or why it would not begin. */
    private record CollapsePlan(CollapseFailure failure, Optional<RecipeHolder<CollapseRecipe>> recipe) {
    }

    private CollapsePlan collapsePlan(ServerLevel server) {
        Optional<RecipeHolder<CollapseRecipe>> recipe = findRecipe(server);
        CollapseRecipe found = recipe.map(RecipeHolder::value).orElse(null);
        double minimum = found == null ? 0.0D : found.driveTier().minimumRpm();
        CollapseFailure failure = CollapseRules.preflight(new CollapseRules.Preflight(busy() || settling > 0,
                aboard(server), !core().isEmpty(), !heart().isEmpty(), found != null,
                tank.getFluidAmount(), found == null ? 0 : found.essence(), getSpeed(), minimum));
        return new CollapsePlan(failure, recipe);
    }

    private void beginCollapse(ServerLevel server) {
        CollapsePlan plan = collapsePlan(server);
        Optional<RecipeHolder<CollapseRecipe>> recipe = plan.recipe();
        CollapseRecipe found = recipe.map(RecipeHolder::value).orElse(null);
        double minimum = found == null ? 0.0D : found.driveTier().minimumRpm();
        CollapseFailure failure = plan.failure();
        if (failure.isFailure()) {
            lastFailure = failure;
            Trace.log(Trace.COLLAPSE, "cradle at {} refused: {} (core {}, heart {}, recipe {}, essence {}/{} mB,"
                            + " speed {} of {} rpm)", Trace.pos(worldPosition), failure.getSerializedName(),
                    core().isEmpty() ? "none" : core().getItem(), heart().isEmpty() ? "none" : heart().getItem(),
                    recipe.map(holder -> holder.id().toString()).orElse("none"), tank.getFluidAmount(),
                    found == null ? 0 : found.essence(), Math.abs(getSpeed()), minimum);
            server.playSound(null, worldPosition, AWSounds.WARP_FAILED.get(), SoundSource.BLOCKS, 0.6F, 1.2F);
            sendData();
            return;
        }
        // Nothing has been spent until now. The offerings come off their depots first, all at once, so a
        // ritual never finds half its offerings gone.
        drawn.clear();
        drawnFrom.clear();
        List<ItemStack> ring = new ArrayList<>();
        List<IItemHandler> depots = new ArrayList<>();
        List<BlockPos> depotAt = new ArrayList<>();
        depots(server, ring, depots, depotAt);
        List<ItemStack> copies = new ArrayList<>();
        for (ItemStack stack : ring) {
            copies.add(stack.copy());
        }
        found.takeOfferings(copies, false);
        for (int i = 0; i < depots.size(); i++) {
            int used = ring.get(i).getCount() - copies.get(i).getCount();
            if (used > 0) {
                ItemStack taken = depots.get(i).extractItem(0, used, false);
                drawn.add(taken);
                drawnFrom.add(depotAt.get(i));
            }
        }
        recipeId = recipe.get().id();
        essenceTotal = found.essence();
        essencePaid = 0;
        essenceShort = 0;
        boolean storm = RiftStorm.ragingOver(server);
        ritual = new CollapseRitual(CollapseRules.ritualTicks(found.getProcessingDuration(), storm));
        lastFailure = CollapseFailure.NONE;
        lastOutcome = "";
        holdTimer = 0;
        surveyTimer = 0;
        held = false;
        heading = 0.0D;
        Trace.log(Trace.COLLAPSE, "cradle at {} began {}: {} offering(s) drawn, {} mB to burn over {}t{}",
                Trace.pos(worldPosition), recipeId, drawn.size(), essenceTotal, ritual.length(),
                storm ? " (shortened by a storm)" : "");
        // Heard a little way off even by a client too far to be drawing the ritual: the rest of the scene -
        // the tear, the drone, the heart - is each client's own, from its clock.
        server.playSound(null, worldPosition, AWSounds.COLLAPSE_SPIN_UP.get(), SoundSource.BLOCKS, 2.0F, 1.0F);
        sync(true);
    }

    private void runRitual(ServerLevel server) {
        CollapseRitual current = ritual;
        CollapseRecipe recipe = recipe(server);
        if (current == null || recipe == null) {
            // The recipe went away with a data pack reload mid-ritual. Nothing to judge it against, so it
            // is spat - everything handed back but the heart and what essence was already burnt.
            Trace.log(Trace.COLLAPSE, "cradle at {}: its recipe {} is gone; spitting", Trace.pos(worldPosition), recipeId);
            finish(server, CollapseRules.Outcome.SPAT, null);
            return;
        }
        if (--holdTimer <= 0) {
            holdTimer = Holds.REACH_RENEW_INTERVAL;
            Holds.holdGround(server, worldPosition);
        }
        if (--surveyTimer <= 0) {
            surveyTimer = SURVEY_INTERVAL;
            survey(server);
        }

        double due = CollapseRules.essenceDue(current.progress()) * essenceTotal;
        int owed = (int) Math.floor(due) - essencePaid - essenceShort;
        if (owed > 0) {
            int paid = tank.drain(owed, IFluidHandler.FluidAction.EXECUTE).getAmount();
            essencePaid += paid;
            essenceShort += owed - paid;
        }
        double shortfall = essenceTotal <= 0 ? 0.0D : essenceShort / (double) essenceTotal;

        double speed = Math.abs(getSpeed());
        CollapseRitual.Conditions conditions = new CollapseRitual.Conditions(speed,
                recipe.driveTier().minimumRpm(), recipe.driveTier().optimalRpm(), RiftStorm.ragingOver(server),
                shortfall, stabilisers, imbalance);
        CollapseRules.Phase before = current.phase();
        boolean wasHeld = held;
        CollapseRitual.Step step = current.tick(conditions, AWConfig.COLLAPSE_RESUME_GRACE.get(),
                CollapseInstability.Settings.DEFAULT);
        heading = current.heading(conditions, CollapseInstability.Settings.DEFAULT);
        if (step == CollapseRitual.Step.COMPLETE) {
            CollapseRules.Outcome outcome = CollapseRules.outcome(current.instability(),
                    AWConfig.COLLAPSE_SETTLE_BELOW.get(), AWConfig.COLLAPSE_SPIT_ABOVE.get());
            Trace.log(Trace.COLLAPSE, "cradle at {} collapsed {}: instability {} ({} stabiliser(s), imbalance {},"
                            + " speed {} of {} rpm, storm {}, short {} mB)", Trace.pos(worldPosition), outcome.id(),
                    Trace.num(current.instability()), stabilisers, Trace.num(imbalance), speed,
                    recipe.driveTier().optimalRpm(), conditions.storm(), essenceShort);
            finish(server, outcome, recipe);
            return;
        }
        held = step == CollapseRitual.Step.HELD;
        if (held) {
            Trace.changed("cradle:" + worldPosition.asLong(), Trace.COLLAPSE, "cradle at {} held at {}%: speed {}"
                    + " of a {} rpm minimum", Trace.pos(worldPosition), Math.round(current.progress() * 100.0D),
                    speed, recipe.driveTier().minimumRpm());
        } else {
            draw(server, current.progress());
        }
        // A beat or a stall is sent at once, so every client's clock turns the same corner the server's did.
        if (--syncTimer <= 0 || held != wasHeld || before != current.phase()) {
            sync(false);
        }
    }

    // ------------------------------------------------------------------ folding

    /**
     * What a fold would do if it began now: the recipe the arms and cup match, how many crafts, the essence,
     * how long, and what it would make - or why it would not. {@link #beginFold} does exactly this, and the
     * cradle's panel shows it, so the panel never promises what Start would refuse.
     */
    public record FoldPlan(CollapseFailure failure, @Nullable RecipeHolder<FoldingRecipe> recipe, int count,
                           int essence, int ticks, ItemStack result) {
    }

    public FoldPlan foldPlan(ServerLevel server) {
        List<ItemStack> held = List.of(core(), heart());
        RecipeHolder<FoldingRecipe> chosen = null;
        for (RecipeHolder<FoldingRecipe> holder : server.getRecipeManager()
                .getAllRecipesFor(AWRecipeTypes.FOLDING.<FoldingRecipe.Contents, FoldingRecipe>getType())) {
            if (holder.value().take(held, true)) {
                chosen = holder;
                break;
            }
        }
        FoldingRecipe recipe = chosen == null ? null : chosen.value();
        int crafts = recipe == null ? 0 : recipe.crafts(held, CradleFold.MOST);
        ItemStack perCraft = ItemStack.EMPTY;
        if (recipe != null && !recipe.getRollableResultsAsItemStacks().isEmpty()) {
            perCraft = recipe.getRollableResultsAsItemStacks().get(0);
        }
        ItemStack out = output();
        int room = perCraft.isEmpty() ? CradleFold.MOST
                : CradleFold.room(out.getCount(), out.isEmpty() || ItemStack.isSameItemSameComponents(out, perCraft),
                perCraft.getCount(), Math.min(slots.getSlotLimit(OUTPUT), perCraft.getMaxStackSize()));
        int per = recipe == null ? 0 : recipe.essence();
        CradleFold.Preflight check = new CradleFold.Preflight(busy() || settling > 0, aboard(server),
                !core().isEmpty(), crafts, room, tank.getFluidAmount(), per, getSpeed(),
                AWConfig.COLLAPSE_FOLD_MINIMUM_SPEED.get());
        CollapseFailure failure = CradleFold.preflight(check);
        int count = failure.isFailure() ? 0 : CradleFold.count(check);
        ItemStack result = perCraft.isEmpty() ? ItemStack.EMPTY
                : perCraft.copyWithCount(perCraft.getCount() * Math.max(1, count));
        return new FoldPlan(failure, chosen, count, count * per,
                recipe == null ? 0 : CradleFold.ticks(recipe.getProcessingDuration()), result);
    }

    /** Begins a fold, or says why not. The whole run's essence is paid now; the stacks are taken at the end. */
    private void beginFold(ServerLevel server) {
        FoldPlan plan = foldPlan(server);
        if (plan.failure().isFailure() || plan.recipe() == null) {
            lastFailure = plan.failure().isFailure() ? plan.failure() : CollapseFailure.NO_FOLD;
            Trace.log(Trace.FOLD, "cradle at {} refused a fold: {} (arms {}, cup {}, recipe {}, essence {} mB, "
                            + "speed {} of {} rpm, output {})", Trace.pos(worldPosition), lastFailure.getSerializedName(),
                    core(), heart(), plan.recipe() == null ? "none" : plan.recipe().id(), tank.getFluidAmount(),
                    Math.abs(getSpeed()), AWConfig.COLLAPSE_FOLD_MINIMUM_SPEED.get(), output());
            server.playSound(null, worldPosition, AWSounds.WARP_FAILED.get(), SoundSource.BLOCKS, 0.6F, 1.4F);
            sendData();
            return;
        }
        if (plan.essence() > 0) {
            tank.drain(plan.essence(), IFluidHandler.FluidAction.EXECUTE);
        }
        foldRecipe = plan.recipe().id();
        foldCount = plan.count();
        foldLength = plan.ticks();
        foldElapsed = 0;
        foldResult = plan.result();
        lastFailure = CollapseFailure.NONE;
        lastOutcome = "";
        Trace.log(Trace.FOLD, "cradle at {} began folding {}: {} craft(s) of {} for {} mB over {}t",
                Trace.pos(worldPosition), foldRecipe, foldCount, core().getItem(), plan.essence(), foldLength);
        server.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.7F, 0.6F);
        sync(true);
    }

    private void runFold(ServerLevel server) {
        foldElapsed++;
        if (foldElapsed == Math.round(foldLength * CradleWorks.FOLD_FLASH)) {
            Vec3 at = point();
            server.playSound(null, at.x, at.y, at.z, AWSounds.COLLAPSE_FOLD.get(), SoundSource.BLOCKS, 0.9F, 1.5F);
            server.sendParticles(AWParticles.RIFT_SPARK.get(), at.x, at.y, at.z, 24, 0.15D, 0.15D, 0.15D, 0.2D);
        }
        if (foldElapsed >= foldLength) {
            finishFold(server);
            return;
        }
        if (--syncTimer <= 0) {
            sync(false);
        }
    }

    /**
     * Ends a fold: takes what it folded out of the arms and cup, craft by craft, and puts what it made in the
     * output. A data pack reloaded mid-run can take the recipe away, and then nothing is taken and nothing made -
     * only the essence, already paid, is lost.
     */
    private void finishFold(ServerLevel server) {
        RecipeHolder<FoldingRecipe> holder = foldRecipe == null ? null : server.getRecipeManager().byKey(foldRecipe)
                .filter(found -> found.value() instanceof FoldingRecipe)
                .map(found -> {
                    @SuppressWarnings("unchecked")
                    RecipeHolder<FoldingRecipe> typed = (RecipeHolder<FoldingRecipe>) found;
                    return typed;
                })
                .orElse(null);
        int made = 0;
        if (holder != null) {
            List<ItemStack> inputs = new ArrayList<>(List.of(core().copy(), heart().copy()));
            ItemStack out = output().copy();
            while (made < foldCount && holder.value().take(inputs, false)) {
                for (ItemStack result : holder.value().rollResults(server.random)) {
                    out = merge(server, out, result);
                }
                made++;
            }
            slots.setStackInSlot(CORE, inputs.get(0));
            slots.setStackInSlot(HEART, inputs.get(1));
            slots.setStackInSlot(OUTPUT, out);
        }
        Trace.log(Trace.FOLD, "cradle at {} folded {} of {} craft(s) of {}", Trace.pos(worldPosition), made, foldCount,
                foldRecipe);
        clearFold();
        folds++;
        lastOutcome = made > 0 ? "folded" : "";
        if (made > 0) {
            AWCriteria.foldCompleted(server, worldPosition, made);
            server.playSound(null, worldPosition, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.7F, 0.7F);
            server.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.2F, 0.8F);
        }
        sync(true);
    }

    private void clearFold() {
        foldRecipe = null;
        foldLength = 0;
        foldElapsed = 0;
        foldCount = 0;
        foldResult = ItemStack.EMPTY;
    }

    /** What the cradle made, put with what it made before; anything that will not fit comes out over the crown. */
    private ItemStack merge(ServerLevel server, ItemStack into, ItemStack result) {
        if (result.isEmpty()) {
            return into;
        }
        if (into.isEmpty()) {
            return result.copy();
        }
        if (ItemStack.isSameItemSameComponents(into, result)) {
            int moved = Math.min(result.getCount(), into.getMaxStackSize() - into.getCount());
            into.grow(moved);
            if (moved < result.getCount()) {
                throwOut(server, spout(), result.copyWithCount(result.getCount() - moved), 0.2D, false);
            }
            return into;
        }
        throwOut(server, spout(), result, 0.2D, false);
        return into;
    }

    /** Where whatever the cradle throws out comes from: up out of the iris, over the crown. */
    private Vec3 spout() {
        return Vec3.atBottomCenterOf(worldPosition).add(0.0D, CollapseChoreography.SPOUT, 0.0D);
    }

    /**
     * The pull: loose items, experience and creatures round the cradle drawn in towards it as it winds, and
     * hard in the hush. Players are drawn by their own clients, from the same {@link CollapseChoreography},
     * since a player's movement is theirs; a creature riding something, a contraption and an armour stand are
     * left where they are.
     */
    private void draw(ServerLevel server, double progress) {
        int range = AWConfig.COLLAPSE_PULL_RANGE.get();
        double strength = CollapseChoreography.pull(progress);
        if (range <= 0 || strength <= 0.0D) {
            return;
        }
        double cap = CollapseChoreography.pullCap(progress);
        double axisX = worldPosition.getX() + 0.5D;
        double axisZ = worldPosition.getZ() + 0.5D;
        AABB around = new AABB(worldPosition).inflate(range, 3.0D, range).move(0.0D, 2.0D, 0.0D);
        for (Entity entity : server.getEntities((Entity) null, around, CollapseCradleBlockEntity::pullable)) {
            double toX = axisX - entity.getX();
            double toZ = axisZ - entity.getZ();
            double reach = CollapseChoreography.pullReach(Math.sqrt(toX * toX + toZ * toZ), range);
            if (reach <= 0.0D) {
                continue;
            }
            Vec3 velocity = entity.getDeltaMovement();
            double[] now = CollapseChoreography.pulled(velocity.x, velocity.z, toX, toZ, strength * reach, cap);
            entity.setDeltaMovement(now[0], velocity.y, now[1]);
            entity.hasImpulse = true;
        }
    }

    /** What the pull draws in: what lies loose, and creatures - never a player (see {@link #draw}). */
    private static boolean pullable(Entity entity) {
        return movable(entity) && !(entity instanceof Player);
    }

    /** What the pull and the blast may move at all. */
    private static boolean movable(Entity entity) {
        return entity.isAlive() && !entity.isSpectator() && !entity.isPassenger() && !entity.noPhysics
                && (entity instanceof ItemEntity || entity instanceof ExperienceOrb
                || (entity instanceof LivingEntity && !(entity instanceof ArmorStand)));
    }

    /**
     * Ends the ritual, one of three ways. The heart and the essence are spent however it ends.
     *
     * <p>The blast goes first, so what the collapse throws out is not caught by its own shockwave.
     */
    private void finish(ServerLevel server, CollapseRules.Outcome outcome, @Nullable CollapseRecipe recipe) {
        slots.extractItem(HEART, 1, false);
        Vec3 over = point();
        // What is thrown out comes up out of the iris, over the crown: the point itself is inside the machine,
        // and an item let go in there would be pushed out of the side of it wherever the game liked.
        Vec3 spout = spout();
        blast(server, outcome);
        List<ItemStack> made = recipe == null ? List.of() : recipe.rollResults(server.random);
        switch (outcome) {
            case SETTLED -> {
                // The made core comes back down into the arms, and is kept in the output for whoever takes it.
                slots.extractItem(CORE, 1, false);
                ItemStack out = output().copy();
                for (ItemStack result : made) {
                    out = merge(server, out, result);
                }
                slots.setStackInSlot(OUTPUT, out);
            }
            case FLUNG -> {
                slots.extractItem(CORE, 1, false);
                for (ItemStack result : made) {
                    fling(server, spout, result);
                }
            }
            case SPAT -> {
                throwOut(server, spout, slots.extractItem(CORE, 1, false), 0.55D, false);
                for (ItemStack offering : drawn) {
                    throwOut(server, spout, offering, 0.55D, false);
                }
            }
        }
        drawn.clear();
        drawnFrom.clear();
        ritual = null;
        recipeId = null;
        held = false;
        heading = 0.0D;
        settling = CradleWorks.RETURN_TICKS + SETTLE_SLACK;
        lastOutcome = outcome.id();
        collapses++;
        // The boom is the server's, so it carries past every client drawing the scene: a landmark is heard
        // before it is seen. Everything else about the moment - the flash, the ring of air, the jolt - each
        // client draws for itself when it sees the collapse count move.
        server.playSound(null, over.x, over.y, over.z, AWSounds.COLLAPSE_BOOM.get(), SoundSource.BLOCKS, BOOM_VOLUME, 1.0F);
        server.playSound(null, over.x, over.y, over.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS,
                outcome == CollapseRules.Outcome.SPAT ? 6.0F : 4.0F, 0.5F);
        server.playSound(null, over.x, over.y, over.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 6.0F, 0.6F);
        server.playSound(null, over.x, over.y, over.z, switch (outcome) {
            case SETTLED -> AWSounds.COLLAPSE_SETTLE.get();
            case FLUNG -> AWSounds.COLLAPSE_FLING.get();
            case SPAT -> AWSounds.COLLAPSE_SPIT.get();
        }, SoundSource.BLOCKS, 2.5F, 1.0F);
        server.sendParticles(AWParticles.RIFT_SPARK.get(), over.x, over.y, over.z, 60, 0.4D, 0.4D, 0.4D, 0.35D);
        SpatialWaveDetectorBlockEntity.announce(server, body());
        AWCriteria.collapseFinished(server, worldPosition, outcome.id());
        sync(true);
    }

    /** The made core, thrown clear: it declines to be precise. It glows, so it can be found. */
    private void fling(ServerLevel server, Vec3 from, ItemStack stack) {
        int range = AWConfig.COLLAPSE_FLING_RANGE.get();
        double angle = server.random.nextDouble() * Math.PI * 2.0D;
        double reach = 2.0D + server.random.nextDouble() * Math.max(0, range - 2);
        // A throw that lands about `reach` blocks away: a lob, so it clears the ring round the cradle.
        double speed = Math.sqrt(reach * 0.08D) * 0.9D;
        ItemEntity entity = new ItemEntity(server, from.x, from.y + 0.5D, from.z, stack.copy());
        entity.setDeltaMovement(Math.cos(angle) * speed, 0.45D + reach * 0.02D, Math.sin(angle) * speed);
        entity.setGlowingTag(true);
        entity.setUnlimitedLifetime();
        entity.setDefaultPickUpDelay();
        server.addFreshEntity(entity);
        comet = entity;
        cometTicks = COMET_TICKS;
        Trace.log(Trace.COLLAPSE, "cradle at {} flung {} about {} blocks", Trace.pos(worldPosition), stack.getItem(),
                Trace.num(reach));
    }

    /** A flung core's tail of sparks, from the moment it leaves the point until it lands. */
    private void trail(ServerLevel server) {
        ItemEntity flying = comet;
        if (flying == null || !flying.isAlive() || --cometTicks <= 0
                || (flying.onGround() && cometTicks < COMET_TICKS - 5)) {
            if (flying != null && flying.isAlive()) {
                server.sendParticles(ParticleTypes.END_ROD, flying.getX(), flying.getY() + 0.2D, flying.getZ(),
                        12, 0.15D, 0.1D, 0.15D, 0.06D);
            }
            comet = null;
            return;
        }
        server.sendParticles(AWParticles.RIFT_SPARK.get(), flying.getX(), flying.getY() + 0.2D, flying.getZ(),
                4, 0.08D, 0.08D, 0.08D, 0.02D);
        server.sendParticles(ParticleTypes.END_ROD, flying.getX(), flying.getY() + 0.2D, flying.getZ(),
                1, 0.02D, 0.02D, 0.02D, 0.0D);
    }

    private void throwOut(ServerLevel server, Vec3 from, ItemStack stack, double spread, boolean glowing) {
        if (stack.isEmpty()) {
            return;
        }
        ItemEntity entity = new ItemEntity(server, from.x, from.y, from.z, stack.copy());
        double angle = server.random.nextDouble() * Math.PI * 2.0D;
        entity.setDeltaMovement(Math.cos(angle) * spread, 0.35D, Math.sin(angle) * spread);
        entity.setGlowingTag(glowing);
        entity.setDefaultPickUpDelay();
        server.addFreshEntity(entity);
    }

    /**
     * The ring of air thrown out from the point: whatever stands within {@code collapseBlastRange} is knocked
     * back, less behind cover; and from a flung or spat collapse, whatever lives there is hurt. It breaks no
     * block and harms no item, and an armour stand is left standing - a collapse is not an explosion.
     */
    private void blast(ServerLevel server, CollapseRules.Outcome outcome) {
        int range = AWConfig.COLLAPSE_BLAST_RANGE.get();
        if (range <= 0) {
            return;
        }
        Vec3 point = point();
        double most = AWConfig.COLLAPSE_BLAST_DAMAGE.get();
        DamageSource harm = CollapseChoreography.harmShare(outcome) > 0.0D && most > 0.0D
                ? AWDamageTypes.collapse(server, point) : null;
        int knocked = 0;
        int hurt = 0;
        for (Entity entity : server.getEntities((Entity) null, new AABB(point, point).inflate(range),
                CollapseCradleBlockEntity::movable)) {
            if (entity instanceof Player player && player.isCreative() && player.getAbilities().flying) {
                continue;
            }
            double distance = entity.getBoundingBox().getCenter().distanceTo(point);
            double push = CollapseChoreography.knockback(distance, range, outcome);
            if (push <= 0.0D) {
                continue;
            }
            double cover = seen(point, entity);
            double awayX = entity.getX() - point.x;
            double awayZ = entity.getZ() - point.z;
            double across = Math.sqrt(awayX * awayX + awayZ * awayZ);
            if (across < 1.0e-3D) {
                double angle = server.random.nextDouble() * Math.PI * 2.0D;
                awayX = Math.cos(angle);
                awayZ = Math.sin(angle);
                across = 1.0D;
            }
            // Cover softens the shove rather than stopping it: air goes round a wall.
            double softened = 0.35D + 0.65D * cover;
            entity.push(awayX / across * push * softened, CollapseChoreography.knockUp(distance, range) * softened,
                    awayZ / across * push * softened);
            entity.hurtMarked = true;
            knocked++;
            if (harm != null && entity instanceof LivingEntity living) {
                float amount = (float) (CollapseChoreography.damage(distance, range, most, outcome) * cover);
                if (amount > 0.0F && living.hurt(harm, amount)) {
                    hurt++;
                }
            }
        }
        Trace.log(Trace.COLLAPSE, "cradle at {} blast ({}): {} knocked back, {} hurt, within {} blocks",
                Trace.pos(worldPosition), outcome.id(), knocked, hurt, range);
    }

    /** The point the collapse falls to: the middle of the hole, in the middle of the machine's rings. */
    private Vec3 point() {
        return Vec3.atBottomCenterOf(worldPosition).add(0.0D, CollapseChoreography.CENTRE, 0.0D);
    }

    /** The whole machine's box, in the world. */
    private AABB body() {
        double[] box = CradleStructure.box();
        return new AABB(worldPosition.getX() + box[0], worldPosition.getY() + box[1], worldPosition.getZ() + box[2],
                worldPosition.getX() + box[3], worldPosition.getY() + box[4], worldPosition.getZ() + box[5]);
    }

    /**
     * How much of something the collapse can see, 0..1: vanilla's explosion sampling over its box, but each line
     * measured only as far as where it meets the machine. The point is inside the cradle and every block of it
     * is solid to a ray, so measured from the point itself the machine is its own cover, and nothing round it
     * would ever be caught in the open.
     */
    private double seen(Vec3 point, Entity entity) {
        AABB body = body();
        AABB box = entity.getBoundingBox();
        int clear = 0;
        int total = 0;
        for (int i = 0; i <= COVER_SAMPLES; i++) {
            for (int j = 0; j <= COVER_SAMPLES; j++) {
                for (int k = 0; k <= COVER_SAMPLES; k++) {
                    Vec3 sample = new Vec3(Mth.lerp(i / (double) COVER_SAMPLES, box.minX, box.maxX),
                            Mth.lerp(j / (double) COVER_SAMPLES, box.minY, box.maxY),
                            Mth.lerp(k / (double) COVER_SAMPLES, box.minZ, box.maxZ));
                    total++;
                    Optional<Vec3> meets = body.clip(sample, point);
                    if (meets.isEmpty()) {
                        clear++;
                        continue;
                    }
                    Vec3 edge = meets.get().add(sample.subtract(meets.get()).normalize().scale(0.01D));
                    if (entity.level().clip(new net.minecraft.world.level.ClipContext(sample, edge,
                            net.minecraft.world.level.ClipContext.Block.COLLIDER,
                            net.minecraft.world.level.ClipContext.Fluid.NONE, entity)).getType()
                            == net.minecraft.world.phys.HitResult.Type.MISS) {
                        clear++;
                    }
                }
            }
        }
        return total == 0 ? 0.0D : clear / (double) total;
    }

    // ------------------------------------------------------------------ the ring

    private Optional<RecipeHolder<CollapseRecipe>> findRecipe(ServerLevel server) {
        if (core().isEmpty() || heart().isEmpty()) {
            return Optional.empty();
        }
        List<ItemStack> ring = new ArrayList<>();
        depots(server, ring, new ArrayList<>(), new ArrayList<>());
        CollapseRecipe.Altar altar = new CollapseRecipe.Altar(core(), heart(), ring);
        return AWRecipeTypes.COLLAPSE.find(altar, server);
    }

    @Nullable
    private CollapseRecipe recipe(ServerLevel server) {
        if (recipeId == null) {
            return null;
        }
        return server.getRecipeManager().byKey(recipeId)
                .map(RecipeHolder::value)
                .filter(CollapseRecipe.class::isInstance)
                .map(CollapseRecipe.class::cast)
                .orElse(null);
    }

    /**
     * Looks at the depots round the cradle. Every one it is linked to that still stands keeps its place, and a
     * place that is free goes to the nearest depot without one - {@link CradleRing#linked}, which is why a ninth
     * depot gets nothing however near it is set down.
     *
     * <p>Once a second, and before anything that asks what is on the depots. Not at all while any of the square
     * is unloaded: a depot must not lose its place for standing in a chunk that was not there to be read.
     */
    private void lookAtRing(ServerLevel server) {
        int reach = AWConfig.COLLAPSE_DEPOT_REACH.get();
        // The square is never wider than a chunk and a block, so its corners stand in every chunk it touches.
        int[] corners = {-reach, reach};
        for (int dx : corners) {
            for (int dz : corners) {
                if (!server.isLoaded(worldPosition.offset(dx, 0, dz))) {
                    return;
                }
            }
        }
        List<int[]> now = CradleRing.linked(linkedDepots, reach, (dx, dy, dz) ->
                server.getBlockState(worldPosition.offset(dx, dy, dz)).is(AllBlocks.DEPOT.get()));
        if (!CradleRing.same(now, linkedDepots)) {
            Trace.log(Trace.COLLAPSE, "cradle at {} is linked to {} depot(s), was {}", Trace.pos(worldPosition),
                    now.size(), linkedDepots.size());
            linkedDepots = now;
            setChanged();
            sendData();
        }
    }

    /** The depots the cradle is linked to, in the order it took them, and what each holds. */
    private void depots(ServerLevel server, List<ItemStack> held, List<IItemHandler> handlers, List<BlockPos> at) {
        lookAtRing(server);
        for (int[] offset : linkedDepots) {
            BlockPos pos = worldPosition.offset(offset[0], offset[1], offset[2]);
            IItemHandler handler = server.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
            if (handler == null) {
                continue;
            }
            held.add(handler.getStackInSlot(0));
            handlers.add(handler);
            at.add(pos);
        }
    }

    /** Counts the switched-on stabilisers within reach, and how lopsided they stand. */
    private void survey(ServerLevel server) {
        List<double[]> offsets = stabiliserOffsets(server);
        stabilisers = offsets.size();
        imbalance = CollapseInstability.imbalance(offsets, stabiliserReach());
    }

    /** Where each switched-on stabiliser within reach stands, from the master - {@link CradleRing}'s walk again. */
    private List<double[]> stabiliserOffsets(ServerLevel server) {
        List<double[]> offsets = new ArrayList<>();
        for (int[] offset : CradleRing.stabilisers(stabiliserReach(), (dx, dy, dz) ->
                SpatialStabiliserBlock.switchedOn(server.getBlockState(worldPosition.offset(dx, dy, dz))))) {
            offsets.add(new double[]{offset[0], offset[1], offset[2]});
        }
        return offsets;
    }

    /**
     * The cradle in whose ring a Spatial Stabiliser at {@code stabiliser} stands - one that is collapsing something
     * now, if there is a choice - or {@code null}. Asked by the stabiliser, for its goggles, on the server; the
     * reach is the one {@link #stabiliserOffsets} counts by, so it never says it is linked from further than the
     * cradle looks.
     *
     * <p>Found among the block entities of the loaded chunks the reach covers, not by reading every block of the
     * cube for a master: a cradle is one block entity among a few hundred, against a couple of thousand blocks,
     * and every stabiliser in a ring asks. It never loads a chunk.
     */
    @Nullable
    public static CollapseCradleBlockEntity steadiedFrom(@Nullable Level level, BlockPos stabiliser) {
        if (level == null) {
            return null;
        }
        int reach = AWConfig.COLLAPSE_STABILISER_REACH.get();
        CollapseCradleBlockEntity found = null;
        for (int cx = (stabiliser.getX() - reach) >> 4; cx <= (stabiliser.getX() + reach) >> 4; cx++) {
            for (int cz = (stabiliser.getZ() - reach) >> 4; cz <= (stabiliser.getZ() + reach) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof CollapseCradleBlockEntity cradle) || cradle.isRemoved()) {
                        continue;
                    }
                    BlockPos master = cradle.getBlockPos();
                    if (CradleRing.steadies(stabiliser.getX() - master.getX(), stabiliser.getY() - master.getY(),
                            stabiliser.getZ() - master.getZ(), reach)) {
                        if (cradle.running()) {
                            return cradle;
                        }
                        found = cradle;
                    }
                }
            }
        }
        return found;
    }

    // ------------------------------------------------------------------ the panel

    /**
     * What the panel shows, now. Worked out from the same plans {@link #begin} begins from - a fold's, or a
     * collapse's - so its promise and Start's answer are one answer.
     */
    public CradlePanel panel(ServerLevel server) {
        int mode = running() ? CradlePanel.COLLAPSE : folding() ? CradlePanel.FOLD : CradlePanel.IDLE;
        int next;
        CollapseFailure failure;
        int needed;
        double minimum;
        double optimal;
        ItemStack result;
        double foldSpeed = AWConfig.COLLAPSE_FOLD_MINIMUM_SPEED.get();
        if (folding()) {
            next = CradlePanel.FOLD;
            failure = CollapseFailure.BUSY;
            needed = 0;
            minimum = foldSpeed;
            optimal = foldSpeed;
            result = foldResult;
        } else if (!core().isEmpty() && !collapseCore(server, core())) {
            FoldPlan plan = foldPlan(server);
            next = CradlePanel.FOLD;
            failure = plan.failure();
            needed = plan.count() > 0 ? plan.essence()
                    : plan.recipe() == null ? 0 : plan.recipe().value().essence();
            minimum = foldSpeed;
            optimal = foldSpeed;
            result = plan.result();
        } else {
            CollapsePlan plan = collapsePlan(server);
            CollapseRecipe recipe = running() ? recipe(server) : plan.recipe().map(RecipeHolder::value).orElse(null);
            next = core().isEmpty() && !running() ? CradlePanel.IDLE : CradlePanel.COLLAPSE;
            failure = running() ? CollapseFailure.BUSY : plan.failure();
            needed = running() ? essenceTotal : recipe == null ? 0 : recipe.essence();
            minimum = recipe == null ? 0.0D : recipe.driveTier().minimumRpm();
            optimal = recipe == null ? 0.0D : recipe.driveTier().optimalRpm();
            List<ItemStack> makes = recipe == null ? List.of() : recipe.getRollableResultsAsItemStacks();
            result = makes.isEmpty() ? ItemStack.EMPTY : makes.get(0).copy();
        }

        List<double[]> offsets = stabiliserOffsets(server);
        int reach = stabiliserReach();
        double leanX = 0.0D;
        double leanZ = 0.0D;
        for (double[] offset : offsets) {
            leanX += offset[0];
            leanZ += offset[2];
        }
        if (!offsets.isEmpty()) {
            leanX /= offsets.size() * (double) reach;
            leanZ /= offsets.size() * (double) reach;
        }

        List<CradlePanel.Offering> offerings = new ArrayList<>();
        if (running()) {
            for (int i = 0; i < Math.min(drawn.size(), drawnFrom.size()); i++) {
                offerings.add(new CradlePanel.Offering(drawn.get(i).copy(), drawnFrom.get(i).getX() - worldPosition.getX(),
                        drawnFrom.get(i).getZ() - worldPosition.getZ()));
            }
        } else {
            List<ItemStack> held = new ArrayList<>();
            List<BlockPos> at = new ArrayList<>();
            depots(server, held, new ArrayList<>(), at);
            for (int i = 0; i < held.size(); i++) {
                offerings.add(new CradlePanel.Offering(held.get(i).copy(), at.get(i).getX() - worldPosition.getX(),
                        at.get(i).getZ() - worldPosition.getZ()));
            }
        }

        return new CradlePanel(worldPosition, mode, next, failure.ordinal(),
                mode == CradlePanel.FOLD ? foldProgress(1.0F) : progress(), instability(), unrest(), held(),
                tank.getFluidAmount(), TANK_CAPACITY, needed, getSpeed(), (float) minimum, (float) optimal,
                offsets.size(), (float) leanX, (float) leanZ, AWConfig.COLLAPSE_SETTLE_BELOW.get().floatValue(),
                AWConfig.COLLAPSE_SPIT_ABOVE.get().floatValue(), offerings, result, lastFailure.ordinal(), lastOutcome);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.aerowarptics.collapse_cradle");
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory,
                                                                          Player player) {
        return new CollapseCradleMenu(id, inventory, this);
    }

    private void sync(boolean now) {
        syncTimer = SYNC_INTERVAL;
        setChanged();
        sendData();
    }

    // -------------------------------------------------------------------- nbt

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Slots", slots.serializeNBT(registries));
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putBoolean("Powered", powered);
        tag.putString("LastFailure", lastFailure.getSerializedName());
        tag.putString("LastOutcome", lastOutcome);
        tag.putInt("Collapses", collapses);
        tag.putInt("Folds", folds);
        if (clientPacket) {
            tag.putInt("StabiliserReach", AWConfig.COLLAPSE_STABILISER_REACH.get());
        }
        int[] links = new int[linkedDepots.size() * 3];
        for (int i = 0; i < linkedDepots.size(); i++) {
            System.arraycopy(linkedDepots.get(i), 0, links, i * 3, 3);
        }
        tag.putIntArray("LinkedDepots", links);
        if (foldLength > 0) {
            // Sent as well as saved: a client runs the fold's scene on its own clock, and draws what it makes.
            tag.putBoolean("Folding", true);
            tag.putInt("Length", foldLength);
            tag.putInt("Elapsed", foldElapsed);
            tag.putInt("FoldCount", foldCount);
            tag.put("FoldResult", foldResult.saveOptional(registries));
            if (!clientPacket && foldRecipe != null) {
                tag.putString("FoldRecipe", foldRecipe.toString());
            }
        }
        if (ritual != null) {
            tag.putBoolean("Running", true);
            tag.putFloat("Progress", (float) ritual.progress());
            tag.putFloat("Instability", (float) ritual.instability());
            tag.putInt("Length", ritual.length());
            tag.putInt("Elapsed", ritual.elapsed());
            // Sent as well as saved: a client draws each offering flying from its own depot into the orbit.
            ListTag offerings = new ListTag();
            for (int i = 0; i < drawn.size(); i++) {
                CompoundTag offering = new CompoundTag();
                offering.put("Item", drawn.get(i).saveOptional(registries));
                offering.put("From", NbtUtils.writeBlockPos(drawnFrom.get(i)));
                offerings.add(offering);
            }
            tag.put("Drawn", offerings);
            if (clientPacket) {
                tag.putBoolean("Held", held);
                tag.putFloat("Heading", (float) heading);
            } else {
                tag.putDouble("RitualInstability", ritual.instability());
                if (recipeId != null) {
                    tag.putString("Recipe", recipeId.toString());
                }
                tag.putInt("EssenceTotal", essenceTotal);
                tag.putInt("EssencePaid", essencePaid);
                tag.putInt("EssenceShort", essenceShort);
            }
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        int collapsesBefore = collapses;
        boolean wasRunning = syncedRunning;
        boolean wasFolding = syncedFolding;
        // A cradle saved before it had an output had two slots, and the handler takes its size from the save.
        CompoundTag saved = tag.getCompound("Slots").copy();
        saved.putInt("Size", SLOTS);
        slots.deserializeNBT(registries, saved);
        tank.readFromNBT(registries, tag.getCompound("Tank"));
        powered = tag.getBoolean("Powered");
        lastFailure = failureNamed(tag.getString("LastFailure"));
        lastOutcome = tag.getString("LastOutcome");
        collapses = tag.getInt("Collapses");
        folds = tag.getInt("Folds");
        // A cradle saved before it remembered its depots has none here, and links to the nearest at its first look.
        int[] links = tag.getIntArray("LinkedDepots");
        List<int[]> remembered = new ArrayList<>();
        for (int i = 0; i + 2 < links.length && remembered.size() < CradleRing.MOST_DEPOTS; i += 3) {
            remembered.add(new int[]{links[i], links[i + 1], links[i + 2]});
        }
        linkedDepots = remembered;
        syncedRunning = tag.getBoolean("Running");
        syncedFolding = tag.getBoolean("Folding");
        foldResult = ItemStack.parseOptional(registries, tag.getCompound("FoldResult"));
        foldCount = tag.getInt("FoldCount");
        syncedProgress = tag.getFloat("Progress");
        syncedInstability = tag.getFloat("Instability");
        drawn.clear();
        drawnFrom.clear();
        for (Tag element : tag.getList("Drawn", Tag.TAG_COMPOUND)) {
            CompoundTag offering = (CompoundTag) element;
            drawn.add(ItemStack.parseOptional(registries, offering.getCompound("Item")));
            drawnFrom.add(NbtUtils.readBlockPos(offering, "From").orElse(worldPosition));
        }
        if (clientPacket) {
            if (tag.contains("StabiliserReach")) {
                stabiliserReach = tag.getInt("StabiliserReach");
            }
            readClock(tag, wasRunning || wasFolding);
            // A collapse this client watched happen, not one it read about on arriving afterwards.
            if (clientKnown && collapses != collapsesBefore) {
                AWClientHooks.cradleCollapsed(this);
            }
            // The arms come home from wherever this client last saw the ritual end; on arriving, they are home.
            if (clientKnown && wasRunning && !syncedRunning) {
                clientAfter = 0;
            }
            clientKnown = true;
            return;
        }
        if (tag.getBoolean("Folding") && tag.contains("Length")) {
            foldLength = Math.max(1, tag.getInt("Length"));
            foldElapsed = tag.getInt("Elapsed");
            foldRecipe = tag.contains("FoldRecipe") ? ResourceLocation.tryParse(tag.getString("FoldRecipe")) : null;
        } else {
            clearFold();
        }
        if (tag.getBoolean("Running") && tag.contains("Length")) {
            ritual = new CollapseRitual(tag.getInt("Length"), tag.getInt("Elapsed"), tag.getDouble("RitualInstability"));
            recipeId = tag.contains("Recipe") ? ResourceLocation.tryParse(tag.getString("Recipe")) : null;
            essenceTotal = tag.getInt("EssenceTotal");
            essencePaid = tag.getInt("EssencePaid");
            essenceShort = tag.getInt("EssenceShort");
        } else {
            ritual = null;
            recipeId = null;
            drawn.clear();
            drawnFrom.clear();
        }
    }

    /** The client's clock, set from what the server sent: left running if it is near enough, set straight if not. */
    private void readClock(CompoundTag tag, boolean wasRunning) {
        clientHeld = tag.getBoolean("Held");
        clientHeading = tag.getFloat("Heading");
        if (!syncedRunning && !syncedFolding) {
            clientElapsed = 0.0F;
            clientLastElapsed = 0.0F;
            clientLength = 0;
            return;
        }
        clientLength = Math.max(1, tag.getInt("Length"));
        int elapsed = tag.getInt("Elapsed");
        if (!wasRunning || Math.abs(clientElapsed - elapsed) > RESYNC_TICKS) {
            clientElapsed = elapsed;
            clientLastElapsed = elapsed;
        }
    }

    private static CollapseFailure failureNamed(String name) {
        for (CollapseFailure failure : CollapseFailure.values()) {
            if (failure.getSerializedName().equals(name)) {
                return failure;
            }
        }
        return CollapseFailure.NONE;
    }

    // --------------------------------------------------------------- goggles

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        AWLang.translate("gui.goggles.collapse_cradle").forGoggles(tooltip);
        AWLang.translate("gui.collapse_cradle.core", core().isEmpty() ? AWLang.translate("gui.collapse_cradle.nothing").component() : core().getHoverName())
                .style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        AWLang.translate("gui.collapse_cradle.heart", heart().isEmpty() ? AWLang.translate("gui.collapse_cradle.nothing").component() : heart().getHoverName())
                .style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        AWLang.translate("gui.collapse_cradle.essence", AWLang.count(tank.getFluidAmount()), AWLang.count(TANK_CAPACITY))
                .style(tank.getFluidAmount() > 0 ? ChatFormatting.AQUA : ChatFormatting.GRAY).forGoggles(tooltip, 1);
        if (!output().isEmpty()) {
            AWLang.translate("gui.collapse_cradle.made", AWLang.count(output().getCount()), output().getHoverName())
                    .style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        }
        if (running()) {
            AWLang.translate("gui.collapse_cradle.running", AWLang.percent(progress()), AWLang.percent(instability()))
                    .style(ChatFormatting.LIGHT_PURPLE).forGoggles(tooltip, 1);
        } else if (folding()) {
            AWLang.translate("gui.collapse_cradle.folding", AWLang.percent(foldProgress(1.0F)),
                            AWLang.count(foldResult.getCount()), foldResult.getHoverName())
                    .style(ChatFormatting.LIGHT_PURPLE).forGoggles(tooltip, 1);
        } else if (lastFailure.isFailure()) {
            AWLang.translate(lastFailure.translationKey()).style(ChatFormatting.RED).forGoggles(tooltip, 1);
        } else if (!lastOutcome.isEmpty()) {
            AWLang.translate("gui.collapse_cradle.outcome." + lastOutcome).style(ChatFormatting.GOLD).forGoggles(tooltip, 1);
        }
        kineticGoggles(tooltip, isPlayerSneaking);
        return true;
    }

    // ------------------------------------------------------------------ turned through its floor

    /** Whether links saved while the master had shafts of its own have been let go of. */
    private boolean kineticsSettled;
    /** Whether the line under the master has yet to be told what the master asks of it now. */
    private boolean retell = true;

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

    private void settleKinetics() {
        if (level == null || level.isClientSide) {
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
    }

    /** Create's own lines - the load and the network's stress - are the feeder's to give. */
    private void kineticGoggles(List<Component> tooltip, boolean isPlayerSneaking) {
        RelicFloor.goggles(level, worldPosition, tooltip, isPlayerSneaking);
    }

    @Override
    public boolean lineGoggles(List<Component> tooltip, boolean isPlayerSneaking) {
        return super.addToGoggleTooltip(tooltip, isPlayerSneaking);
    }

    /** What the cradle asks of whatever turns it: a Singularity drive's own load, as the stress table has it. */
    @Override
    public float relicImpact() {
        return (float) com.simibubi.create.api.stress.BlockStressValues.getImpact(getStressConfigKey());
    }

    /** The cradle's load, while the line under it is the input turning it; otherwise it is another input's. */
    @Override
    public float calculateStressApplied() {
        float impact = RelicFloor.load(this, worldPosition);
        this.lastStressApplied = impact;
        return impact;
    }

    // -------------------------------------------------------------- geckolib

    /**
     * None. Everything on the machine that moves is posed by {@code CollapseCradleRenderer} from the ritual's
     * own clock ({@link CradleWorks}), because a looped animation drifts off the moment it is meant to show;
     * the only animation file the cradle has is the item's, turning its rings on a shelf.
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    /**
     * The whole machine at rest - all three blocks each way, drawn from this one. While a ritual runs, as wide
     * as the furthest depot an offering can be drawn off and as high as the orbit: the renderer draws every
     * offering, and a box that stopped at the machine would drop them from view with it.
     */
    @Override
    public AABB getRenderBoundingBox() {
        if (running()) {
            return new AABB(worldPosition).inflate(9.0D, 0.0D, 9.0D).expandTowards(0.0D, 4.0D, 0.0D);
        }
        return body().inflate(0.25D);
    }
}
