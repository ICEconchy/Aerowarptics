package uk.co.iceconchy.aerowarptics.astrolabe;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchor;
import uk.co.iceconchy.aerowarptics.warp.WarpCourse;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorRegistry;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One cell of an Astrolabe Cartography Table.
 *
 * <p>Every cell carries one of these; only the origin does any work. The rest remember which origin
 * they belong to and forward everything to it, which is what lets a player click any part of the table
 * and get the same chart.
 *
 * <p>The table is where a course is <em>chosen</em>. It never starts a warp: choosing is the moment
 * permission is checked, and something else - a redstone signal into the drive - is what fires it
 * afterwards. Keeping those two apart is what stops a circuit from reaching a destination the person
 * who wired it never had access to.
 */
public class AstrolabeBlockEntity extends SmartBlockEntity
        implements BlockEntitySubLevelActor, GeoBlockEntity, IHaveGoggleInformation {

    /** Ticks without a Sable actor tick before the table considers itself grounded. */
    private static final int AIRSHIP_GRACE_TICKS = 3;

    /** How often the table re-checks what it can see. Cheap, but not worth doing every tick. */
    private static final int SURVEY_INTERVAL = 20;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.astrolabe.idle");
    private static final RawAnimation ACTIVE = RawAnimation.begin().thenLoop("animation.astrolabe.active");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    /**
     * The centre of the table this cell belongs to, or {@code null} while it is a loose block.
     *
     * <p>Stored rather than searched for on demand: a cell has to know its centre to forward a click,
     * and a table on a moving hull is not somewhere a per-frame search belongs.
     */
    @Nullable
    private BlockPos master;

    /**
     * Blocks along a side of the table this cell belongs to, or 0 for a loose one.
     *
     * <p>Carried on every cell rather than only on the origin because the client needs it to
     * draw: the renderer is handed a block entity and has to know how big a table it is part of
     * before it can size anything.
     */
    private int size;

    /** The course the table is pointed at. Only meaningful on the centre. */
    @Nullable
    private UUID destination;

    /** Name of that course, resolved server-side and synced so the chart and goggles can show it. */
    private String destinationName = "";

    /** Whether this hull has a Rift Drive to send a course to. Server-derived, synced. */
    private boolean hasDrive;

    // ---- transient -----------------------------------------------------------
    @Nullable
    private ServerSubLevel airshipSubLevel;
    private int ticksSinceAirshipTick = Integer.MAX_VALUE;
    private boolean dirty;

    public AstrolabeBlockEntity(BlockPos pos, BlockState state) {
        super(AWBlockEntities.ASTROLABE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    // ------------------------------------------------------------------ sable

    @Override
    public void sable$tick(ServerSubLevel subLevel) {
        airshipSubLevel = subLevel;
        ticksSinceAirshipTick = 0;
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double deltaTime) {
        // A chart table is furniture. It applies no forces of its own.
    }

    /**
     * The airship this table is bolted to, or {@code null} when it is standing on the ground.
     *
     * <p>The sub-level is looked up from the table's own position rather than waited for. Sable only
     * registers a block entity as an actor when its block changes in the plot, so a table that was
     * already in place - loaded from disk, or carried in on an assembled hull - may never receive an
     * actor tick at all. Asking the plot grid which sub-level contains this position has no such
     * lifecycle to get wrong, and is still a constant-time index lookup rather than a search.
     */
    @Nullable
    public Airship airship() {
        if (level == null || level.isClientSide) {
            return null;
        }
        ServerSubLevel subLevel = airshipSubLevel != null && ticksSinceAirshipTick <= AIRSHIP_GRACE_TICKS
                ? airshipSubLevel
                : resolveSubLevel();
        if (subLevel == null || subLevel.isRemoved()) {
            return null;
        }
        Airship airship = Airship.of(subLevel);
        return airship.isActive() ? airship : null;
    }

    /**
     * The Rift Drive on this table's airship, or {@code null} when there is not one.
     *
     * <p>A vessel may only have one drive claim it, so the first found is the one this chart commands.
     */
    @Nullable
    public RiftDriveBlockEntity drive() {
        Airship airship = airship();
        return airship == null ? null : airship.machine(RiftDriveBlockEntity.class);
    }

    @Nullable
    private ServerSubLevel resolveSubLevel() {
        if (level == null) {
            return null;
        }
        return Sable.HELPER.getContaining(level, worldPosition) instanceof ServerSubLevel found ? found : null;
    }

    // ---------------------------------------------------------------- assembly

    /**
     * Tells this cell which table it is part of.
     *
     * <p>Called for every cell when a table forms, and with {@code null} for every cell when one
     * comes apart, so no cell is ever left pointing at an origin that is not there any more.
     */
    public void setMaster(@Nullable BlockPos centre, int tableSize) {
        BlockPos next = centre == null ? null : centre.immutable();
        int nextSize = next == null ? 0 : tableSize;
        if (Objects.equals(master, next) && size == nextSize) {
            return;
        }
        master = next;
        size = nextSize;
        if (!isMaster()) {
            // Only the centre holds a course. A cell demoted out of the middle of a table must not
            // keep one, or rebuilding the table one block over would resurrect an old heading.
            destination = null;
            destinationName = "";
        }
        setChanged();
        dirty = true;
        if (level != null && !level.isClientSide) {
            sendData();
        }
    }

    @Nullable
    public BlockPos master() {
        return master;
    }

    public boolean isFormed() {
        return master != null;
    }

    /**
     * Blocks along a side of this cell's table, or 0 when it is loose.
     *
     * <p>Everything visible about a table scales off this: how big its model is drawn, how much
     * ground its projection charts, and how wide that projection is thrown.
     */
    public int size() {
        return size;
    }

    /**
     * The table this cell belongs to, or {@code null} when it is loose.
     *
     * <p>Rebuilt from the two fields rather than stored, so there is one place a cell's membership
     * lives and no way for a saved record to disagree with it.
     */
    @Nullable
    public AstrolabeStructure.Table table() {
        return master == null || size < AstrolabeStructure.MIN_SIZE
                ? null
                : new AstrolabeStructure.Table(master, size);
    }

    /** Whether this cell is its table's origin - the one that holds the course and draws the map. */
    public boolean isMaster() {
        return master != null && master.equals(worldPosition);
    }

    /**
     * The cell that actually holds this table's state.
     *
     * <p>Returns {@code null} for a loose block, and for a cell whose centre has been broken or
     * unloaded - both of which read to a player as "this is not a working table", which is correct.
     */
    @Nullable
    public AstrolabeBlockEntity controller() {
        if (level == null || master == null) {
            return null;
        }
        if (isMaster()) {
            return this;
        }
        return level.getBlockEntity(master) instanceof AstrolabeBlockEntity centre && centre.isMaster()
                ? centre
                : null;
    }

    // ----------------------------------------------------------------- ticking

    @Override
    public void tick() {
        // Before super.tick(), not after: Create runs initialize(), lazyTick() and every
        // behaviour from there, and those touch the level too. Nothing runs on a block that is
        // no longer there - see Airship.orphaned.
        if (Airship.orphaned(this)) {
            return;
        }
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }
        if (ticksSinceAirshipTick != Integer.MAX_VALUE) {
            ticksSinceAirshipTick++;
        }
        if (!isMaster()) {
            return;
        }

        // What the goggles and the chart show has to be worked out here and sent. Sable's sub-levels
        // are a server structure, so a client asking "is there a drive on this hull" gets no for every
        // hull there is.
        if (level.getGameTime() % SURVEY_INTERVAL == 0L) {
            survey();
        }

        if (dirty) {
            dirty = false;
            sendData();
        }
    }

    private void survey() {
        boolean present = drive() != null;
        String name = resolveDestinationName();
        if (present != hasDrive || !name.equals(destinationName)) {
            hasDrive = present;
            destinationName = name;
            dirty = true;
        }
    }

    /**
     * What this table's ship is actually aimed at.
     *
     * <p>Asked of the <em>drive</em>, not of this table's own stored anchor. Since a Rift Probe can
     * aim the same drive at a bare position, the drive's standing course is the only thing that knows
     * where a ship is going - a chart table that reported its own last click would say "Mooring Spur"
     * long after a probe had pointed the ship at open country.
     *
     * <p>Falls back to the table's own anchor only when there is no drive to ask, so a table laid out
     * on the ground still shows what was last chosen at it.
     */
    private String resolveDestinationName() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return "";
        }
        RiftDriveBlockEntity drive = drive();
        if (drive != null) {
            WarpCourse course = drive.standingCourse();
            return course == null ? "" : course.label();
        }
        if (destination == null) {
            return "";
        }
        WarpAnchor anchor = WarpAnchorRegistry.get(serverLevel).byId(destination);
        return anchor == null ? "" : anchor.displayName();
    }

    // ------------------------------------------------------------------ course

    @Nullable
    public UUID destination() {
        return destination;
    }

    public String destinationName() {
        return destinationName;
    }

    public boolean hasDrive() {
        return hasDrive;
    }

    /**
     * Points this table, and the drive on its hull, at an anchor.
     *
     * <p>The table's own record is only a convenience for reopening the chart on what was last
     * chosen. The decision that matters is handed straight to the drive, because that is what a
     * redstone signal fires and what would otherwise be left pointing somewhere stale.
     */
    public WarpFailure select(ServerPlayer player, UUID anchorId) {
        AstrolabeBlockEntity centre = controller();
        if (centre == null) {
            return WarpFailure.NO_AIRSHIP;
        }
        if (centre != this) {
            return centre.select(player, anchorId);
        }
        RiftDriveBlockEntity drive = drive();
        if (drive == null) {
            return WarpFailure.NO_AIRSHIP;
        }
        WarpFailure result = drive.setCourse(player, anchorId);
        if (result.isFailure()) {
            return result;
        }
        destination = anchorId;
        destinationName = resolveDestinationName();
        hasDrive = true;
        dirty = true;
        setChanged();
        return WarpFailure.NONE;
    }

    // -------------------------------------------------------------------- nbt

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (master != null) {
            // Stored as the step to the origin rather than its absolute position, so the pointer
            // survives the hull assembling and relocating every cell into the plot grid at once - an
            // absolute Master would name the block's old world spot and leave the whole table dark.
            tag.put("MasterStep", NbtUtils.writeBlockPos(AstrolabeStructure.originStep(worldPosition, master)));
            tag.putInt("Size", size);
        }
        if (destination != null) {
            tag.putUUID("Destination", destination);
        }
        tag.putString("DestinationName", destinationName);
        tag.putBoolean("HasDrive", hasDrive);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("MasterStep")) {
            master = NbtUtils.readBlockPos(tag, "MasterStep")
                    .map(step -> AstrolabeStructure.originFromStep(worldPosition, step))
                    .orElse(null);
        } else if (tag.contains("Master")) {
            // A pointer written absolutely before the step was stored relatively. Correct on the
            // ground, where the cell has not moved since it was written, and rewritten as a step the
            // next time this cell saves.
            master = NbtUtils.readBlockPos(tag, "Master").orElse(null);
        } else {
            master = null;
        }
        // Tables saved before there was more than one size are all three by three, and a zero
        // here would render one as nothing at all.
        size = master == null ? 0 : Math.max(1, tag.getInt("Size"));
        destination = tag.hasUUID("Destination") ? tag.getUUID("Destination") : null;
        destinationName = tag.getString("DestinationName");
        hasDrive = tag.getBoolean("HasDrive");
    }

    /** The centre draws a hologram that overhangs the whole table, so its box has to cover it. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(3.0D);
    }

    // --------------------------------------------------------------- goggles

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        AWLang.translate("gui.goggles.astrolabe").forGoggles(tooltip);
        AstrolabeBlockEntity centre = controller();
        if (centre == null) {
            AWLang.translate("gui.astrolabe.incomplete").style(ChatFormatting.RED).forGoggles(tooltip, 1);
            return true;
        }
        if (!centre.hasDrive) {
            AWLang.translate("gui.astrolabe.no_drive").style(ChatFormatting.RED).forGoggles(tooltip, 1);
        }
        if (centre.destinationName.isBlank()) {
            AWLang.translate("gui.astrolabe.no_course").style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        } else {
            AWLang.translate("gui.astrolabe.course_named", centre.destinationName)
                    .style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        }
        return true;
    }

    // -------------------------------------------------------------- geckolib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "astrolabe", 8, state -> {
            state.getController().setAnimation(destinationName.isBlank() ? IDLE : ACTIVE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
