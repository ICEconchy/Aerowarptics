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
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
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
     * Commands the airship's velocity outright for this tick.
     *
     * <p>Used while the server is flying the ship through a warp. Setting velocity rather than
     * teleporting per tick means Sable moves the hull exactly the way it moves any airship, so
     * everything standing on the deck is carried along by the same code that carries it in normal
     * flight - and because the velocity is re-commanded every tick, gravity, lift and thrust from the
     * ship's own machinery are all overridden for the duration.
     */
    public void driveVelocity(Vector3dc velocity) {
        PhysicsPipeline pipeline = pipeline();
        if (pipeline == null || subLevel.isRemoved()) {
            return;
        }
        pipeline.resetVelocity(subLevel);
        pipeline.addLinearAndAngularVelocity(subLevel, velocity, ZERO);
        pipeline.wakeUp(subLevel);
    }

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

    /** Persistent, Sable-managed data bag attached to this airship. */
    public AirshipWarpData warpData() {
        return new AirshipWarpData(subLevel);
    }

    @Override
    public String toString() {
        return "Airship[" + uuid() + (name() == null ? "" : " \"" + name() + "\"") + "]";
    }
}
