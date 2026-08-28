package uk.co.iceconchy.aerowarptics.airship;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The addon's single point of contact with Create: Aeronautics airships.
 *
 * <p>An "airship" in this ecosystem is a Sable {@code SubLevel}: an independent plot of blocks that
 * Sable's physics pipeline drives around the parent {@link ServerLevel}. Aeronautics contributes the
 * lift, propulsion and balloon behaviour on top; Simulated contributes the assembler and controls.
 * None of them own a separate "ship object", so every airship query and every airship movement in
 * this mod is a Sable sub-level operation.
 *
 * <p>Instances are cheap, short-lived views. Never cache one across ticks - cache the
 * {@link #uuid()} instead and re-resolve.
 */
public final class Airship {

    private final ServerSubLevel subLevel;

    private Airship(ServerSubLevel subLevel) {
        this.subLevel = subLevel;
    }

    // -------------------------------------------------------------- lookup

    /**
     * Resolves the airship a block belongs to.
     *
     * <p>This is the correct way to ask "which airship is this machine bolted to". A block entity's
     * {@link BlockPos} is a position inside the sub-level's plot, not a world position, so it cannot
     * be compared against world coordinates directly. Sable maintains a plot-grid index that answers
     * the question in constant time, which is why nothing here scans the world.
     */
    @Nullable
    public static Airship containing(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel)) {
            return null;
        }
        SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        return subLevel instanceof ServerSubLevel server && !server.isRemoved() ? new Airship(server) : null;
    }

    @Nullable
    public static Airship containing(BlockEntity blockEntity) {
        Level level = blockEntity.getLevel();
        return level == null ? null : containing(level, blockEntity.getBlockPos());
    }

    /** Wraps an already-resolved sub-level, for code that is handed one by Sable. */
    public static Airship of(ServerSubLevel subLevel) {
        return new Airship(subLevel);
    }

    // -------------------------------------------------------------- identity

    public ServerSubLevel subLevel() {
        return subLevel;
    }

    public UUID uuid() {
        return subLevel.getUniqueId();
    }

    public ServerLevel level() {
        return subLevel.getLevel();
    }

    @Nullable
    public String name() {
        return subLevel.getName();
    }

    /** An airship is usable while its sub-level is loaded and has not been marked for removal. */
    public boolean isActive() {
        return !subLevel.isRemoved() && subLevel.getPlot() != null;
    }

    // -------------------------------------------------------------- geometry

    /** Ship-to-world transform for the current tick. */
    public Pose3dc pose() {
        return subLevel.logicalPose();
    }

    public Vector3dc position() {
        return subLevel.logicalPose().position();
    }

    public Quaterniondc orientation() {
        return subLevel.logicalPose().orientation();
    }

    /** World-space axis-aligned bounds of the whole airship. */
    public BoundingBox3dc worldBounds() {
        return subLevel.boundingBox();
    }

    /** Plot-space (ship-space) block bounds of the airship's structure. */
    public BoundingBox3ic shipBounds() {
        return subLevel.getPlot().getBoundingBox();
    }

    /** Converts a position inside the airship's plot into world space. */
    public Vec3 toWorld(Vec3 shipSpace) {
        return subLevel.logicalPose().transformPosition(shipSpace);
    }

    /**
     * Rotates a direction out of the airship's plot space into world space.
     *
     * <p>Directions are rotated but not translated, which is why this is separate from
     * {@link #toWorld(Vec3)}: a block's facing on a banking airship points somewhere quite different
     * from the same facing on the ground.
     */
    public Vector3d toWorldDirection(Vector3dc shipSpace) {
        return subLevel.logicalPose().transformNormal(shipSpace, new Vector3d());
    }

    /** Converts a world position into the airship's plot space. */
    public Vec3 toShip(Vec3 worldSpace) {
        return subLevel.logicalPose().transformPositionInverse(worldSpace);
    }

    /** Rotates a direction out of world space into the airship's plot space - the inverse of {@link #toWorldDirection}. */
    public Vector3d toShipDirection(Vector3dc worldSpace) {
        return subLevel.logicalPose().transformNormalInverse(worldSpace, new Vector3d());
    }

    /**
     * Physical mass Sable computes for the airship, or {@code 0} when the mass tracker has not been
     * built yet. Used directly by the warp cost formula.
     */
    public double mass() {
        MassData massData = subLevel.getMassTracker();
        if (massData == null || massData.isInvalid()) {
            return 0.0D;
        }
        double mass = massData.getMass();
        return Double.isFinite(mass) && mass > 0.0D ? mass : 0.0D;
    }

    /** Volume of the airship's block bounding box, used when mass data is unavailable. */
    public double structureVolume() {
        BoundingBox3ic bounds = shipBounds();
        if (bounds == null) {
            return 0.0D;
        }
        return (double) bounds.width() * bounds.height() * bounds.length();
    }

    /** World-space centre of the airship's bounding box. */
    public Vector3d centre(Vector3d dest) {
        return worldBounds().center(dest);
    }

    // -------------------------------------------------------------- physics

    @Nullable
    public SubLevelPhysicsSystem physics() {
        return SubLevelPhysicsSystem.get(subLevel.getLevel());
    }

    @Nullable
    public PhysicsPipeline pipeline() {
        SubLevelPhysicsSystem system = physics();
        return system == null ? null : system.getPipeline();
    }

    /**
     * Moves the entire airship - structure, block entities, passengers and cargo - to a new place in
     * the same {@link ServerLevel}.
     *
     * <p>The move is performed by Sable's physics pipeline, the same call the {@code /sable sub_level
     * teleport} command uses, so the sub-level's plot, its block entities and everything attached to
     * it come along untouched. Entities standing on or riding the airship are converted into
     * ship-space before the move and back into world space afterwards, which reproduces their
     * position and facing relative to the deck exactly.
     *
     * @param destination  world position for the airship's origin
     * @param orientation  world orientation for the airship
     * @param retainedVelocity fraction of the pre-warp velocity to restore, {@code 0} to arrive at rest
     * @return {@code true} when the pipeline accepted the move
     */
    /**
     * Tells a player's own client where the hull just put them.
     *
     * <p>{@code popEntityLocal} writes the new position straight onto the entity - {@code
     * sable$setPosSuperRaw}, no packet. For anything the server owns outright that is the whole job.
     * A player's client owns its own position: told nothing, it carries on at the old coordinates and
     * the raw write is undone by the very next movement packet it sends. Across a warp that means the
     * ship arrives and the player does not.
     *
     * <p>This was survivable for a long time by accident. {@code WarpPassengers.hold} runs on every
     * later tick of the flight, notices the player is nowhere near their seat and issues a real
     * correction - so on a hull small enough for the warp to finish cleanly, the player looked like
     * they had been carried across. On a large one the flight was being dropped at the crossing
     * before any of those ticks ran, and there was nothing else in the system that ever told the
     * client. Doing it here makes the teleport work on its own, rather than on the recovery.
     */
    private static void tell(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            // The one call that also resets what the client thinks it is doing. teleportTo would be
            // another raw write with the same problem.
            player.connection.teleport(player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot());
        }
    }

    public boolean relocate(Vector3dc destination, Quaterniondc orientation, double retainedVelocity) {
        PhysicsPipeline pipeline = pipeline();
        if (pipeline == null || subLevel.isRemoved()) {
            return false;
        }

        Vector3d linear = new Vector3d();
        Vector3d angular = new Vector3d();
        pipeline.getLinearVelocity(subLevel, linear);
        pipeline.getAngularVelocity(subLevel, angular);

        List<Entity> aboard = passengers();
        for (Entity entity : aboard) {
            SubLevelHelper.pushEntityLocal(subLevel, entity);
        }

        pipeline.resetVelocity(subLevel);
        pipeline.teleport(subLevel, destination, orientation);

        // teleport() writes straight into logicalPose, so the pop below already uses the new frame.
        for (Entity entity : aboard) {
            SubLevelHelper.popEntityLocal(subLevel, entity);
            tell(entity);
        }

        if (retainedVelocity > 0.0D) {
            pipeline.addLinearAndAngularVelocity(subLevel,
                    linear.mul(retainedVelocity, new Vector3d()),
                    angular.mul(retainedVelocity, new Vector3d()));
        }
        pipeline.wakeUp(subLevel);

        // Collapse the pose delta so nothing on board reads the jump as a one-tick velocity spike.
        subLevel.updateLastPose();
        subLevel.updateBoundingBox();
        subLevel.forceUpdateGlobalBounds();
        return true;
    }

    /**
     * How fast the airship is going, in blocks per tick.
     *
     * <p>Converted on the way out for the same reason {@link #driveVelocity} converts on the way in:
     * the pipeline integrates in blocks per second, and everything above this class thinks in blocks
     * per tick. A gate carrying momentum across has to hand back what it was given.
     */
    public Vector3d velocity() {
        PhysicsPipeline pipeline = pipeline();
        Vector3d linear = new Vector3d();
        if (pipeline != null && !subLevel.isRemoved()) {
            pipeline.getLinearVelocity(subLevel, linear);
            linear.mul(1.0D / PHYSICS_TICKS_PER_SECOND);
        }
        return linear;
    }

    /**
     * Commands the airship's velocity outright for this tick.
     *
     * <p>Used while the server is flying the ship through a warp. Setting velocity rather than
     * teleporting per tick means Sable moves the hull exactly the way it moves any airship, so
     * everything standing on the deck is carried along by the same code that carries it in normal
     * flight - and because the velocity is re-commanded every tick, gravity, lift and thrust from the
     * ship's own machinery are all overridden for the duration.
     */
    public void driveVelocity(Vector3dc blocksPerTick) {
        PhysicsPipeline pipeline = pipeline();
        if (pipeline == null || subLevel.isRemoved()) {
            return;
        }
        pipeline.resetVelocity(subLevel);
        pipeline.addLinearAndAngularVelocity(subLevel, toPhysicsVelocity(blocksPerTick), ZERO);
        pipeline.wakeUp(subLevel);
    }

    /**
     * Converts a speed in blocks per tick into the units Sable's physics wants.
     *
     * <p>Sable drives its pipeline with {@code physicsTick(0.05)} - Rapier's timestep is one tick
     * expressed in <em>seconds</em> - so a velocity handed to the pipeline is integrated as blocks
     * per second, and one given in blocks per tick moves the hull twenty times too slowly.
     *
     * <p>This was not obvious from the API, which says only "velocity". It came out of a traced warp:
     * the run at the aperture, the passage through it and the corridor were all commanded at
     * different speeds, and all three landed at almost exactly the commanded figure per <em>second</em>
     * - 1.6 asked, 1.42 achieved; 0.38 asked, 0.43 achieved; 9.0 asked, 8.8 achieved. Three
     * independent stages agreeing on the same factor is not a coincidence.
     *
     * <p>Everything above this method thinks in blocks per tick, which is how Minecraft speeds are
     * normally written and how the config is documented. The conversion belongs here, at the one
     * place the two worlds meet.
     */
    private static Vector3d toPhysicsVelocity(Vector3dc blocksPerTick) {
        return new Vector3d(blocksPerTick).mul(PHYSICS_TICKS_PER_SECOND);
    }

    /** Sable steps physics once per tick with a timestep of 0.05 seconds. */
    private static final double PHYSICS_TICKS_PER_SECOND = 20.0D;

    private static final Vector3dc ZERO = new Vector3d();

    /** Nudges the airship, used only by the optional dangerous-failure setting. */
    public void applyImpulse(Vector3dc linear, Vector3dc angular) {
        PhysicsPipeline pipeline = pipeline();
        if (pipeline != null) {
            pipeline.applyLinearAndAngularImpulse(subLevel, linear, angular, true);
        }
    }

    /**
     * Every entity currently carried by the airship, including riders of those entities.
     *
     * <p>Sable tracks which sub-level an entity is standing on, so this is a bounded query over the
     * airship's own bounding box rather than a world-wide scan.
     */
    public List<Entity> passengers() {
        AABB bounds = worldBounds().toMojang().inflate(2.0D);
        List<Entity> found = new ArrayList<>();
        for (Entity entity : level().getEntities((Entity) null, bounds, e -> !e.isRemoved())) {
            if (entity.isPassenger()) {
                continue; // moved along with its vehicle
            }
            SubLevel tracking = Sable.HELPER.getTrackingOrVehicleSubLevel(entity);
            if (tracking == subLevel) {
                found.add(entity);
            }
        }
        return found;
    }

    /**
     * The players aboard.
     *
     * <p>Unlike {@link #passengers()} this follows riders up to their root vehicle, so someone sitting
     * in a minecart or on a horse on the deck still counts as crew. Used to address the warp corridor
     * experience to the right people.
     */
    public List<ServerPlayer> crew() {
        AABB bounds = worldBounds().toMojang().inflate(4.0D);
        List<ServerPlayer> found = new ArrayList<>();
        for (ServerPlayer player : level().getEntitiesOfClass(ServerPlayer.class, bounds, p -> !p.isRemoved())) {
            if (isAboard(player) || isAboard(player.getRootVehicle())) {
                found.add(player);
            }
        }
        return found;
    }

    /** True when the given entity is currently carried by this airship. */
    public boolean isAboard(Entity entity) {
        return Sable.HELPER.getTrackingOrVehicleSubLevel(entity) == subLevel;
    }

    /**
     * Every block entity of a given kind aboard this airship.
     *
     * <p>Read straight out of the plot's own chunks. Sable also keeps a list of "actors" - block
     * entities it ticks - but membership of that list depends on a block having <em>changed</em> in
     * the plot since it was created, so a machine that arrived on an already-assembled hull, or came
     * back off disk, may never appear in it. That distinction has bitten this mod once already, and a
     * chunk's block-entity map has no such lifecycle to get wrong.
     *
     * <p>Bounded by the hull: an airship's plot is a handful of chunks, not a world.
     */
    public <T extends BlockEntity> List<T> machines(Class<T> type) {
        LevelPlot plot = subLevel.getPlot();
        if (plot == null) {
            return List.of();
        }
        List<T> found = new ArrayList<>();
        for (PlotChunkHolder holder : plot.getLoadedChunks()) {
            LevelChunk chunk = holder.getChunk();
            if (chunk == null) {
                continue;
            }
            for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                if (type.isInstance(blockEntity) && !blockEntity.isRemoved()) {
                    found.add(type.cast(blockEntity));
                }
            }
        }
        return found;
    }

    /** The first machine of a kind aboard, or {@code null}. A hull is expected to carry at most one. */
    @Nullable
    public <T extends BlockEntity> T machine(Class<T> type) {
        List<T> found = machines(type);
        return found.isEmpty() ? null : found.getFirst();
    }

    /** Persistent, Sable-managed data bag attached to this airship. */
    public AirshipWarpData warpData() {
        return new AirshipWarpData(subLevel);
    }

    @Override
    public String toString() {
        return "Airship[" + uuid() + (name() == null ? "" : " \"" + name() + "\"") + "]";
    }
}
