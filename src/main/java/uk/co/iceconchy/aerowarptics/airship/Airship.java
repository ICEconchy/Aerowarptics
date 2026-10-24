package uk.co.iceconchy.aerowarptics.airship;

import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
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
import uk.co.iceconchy.aerowarptics.bond.RiftBondBlockEntity;
import uk.co.iceconchy.aerowarptics.bond.RiftBonds;
import uk.co.iceconchy.aerowarptics.compat.simulated.CarriedRopes;
import uk.co.iceconchy.aerowarptics.compat.simulated.SimulatedTies;
import uk.co.iceconchy.aerowarptics.network.AWNetwork;
import uk.co.iceconchy.aerowarptics.network.ClientboundFoldCrossedPacket;
import uk.co.iceconchy.aerowarptics.trace.Trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
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

    /**
     * Whether a block entity's block has gone out from under it.
     *
     * <p>Sable does not tear a plot down one block at a time. When a plot is released its chunks are
     * wiped wholesale, which leaves {@code void_air} where the blocks were - but the block entities
     * are still in the chunk's ticking list, and each of them gets at least one more tick with a
     * {@code worldPosition} pointing at nothing. Anything that touches the level from that tick then
     * asks Sable to change a block in a plot that no longer has a holder, and Sable throws
     * {@code UnsupportedOperationException: Cannot change blocks in nonexistent plot holder} - which
     * takes the server thread with it.
     *
     * <p>Most machines in this mod never reach that, because they resolve their {@link Airship} at
     * the top of their tick and {@link #isActive()} already answers no. This is for the ones with no
     * airship to ask about: a block entity that is a feature of the <em>world</em> and only
     * incidentally ended up inside a plot, which has nothing to resolve and so nothing to bail on.
     *
     * <p>Comparing the live block against the block entity's own cached state is the same test
     * {@code RiftDriveBlockEntity.remove} uses to tell a break from an unload, and it is the cheapest
     * honest one: it needs no Sable lookup, and it is equally true of a block mined out from under a
     * block entity on solid ground.
     */
    public static boolean orphaned(BlockEntity blockEntity) {
        Level level = blockEntity.getLevel();
        if (level == null) {
            return true;
        }
        if (blockEntity.isRemoved()) {
            return true;
        }
        return level.getBlockState(blockEntity.getBlockPos()).getBlock()
                != blockEntity.getBlockState().getBlock();
    }

    /**
     * The airship an entity is currently standing on or riding, or {@code null} for one on solid
     * ground.
     *
     * <p>Follows a rider up to whatever it is sitting in, the same rule {@link #crew()} uses, so a
     * player in a seat on the deck still resolves to the ship. Used by the dry-run command to find the
     * vessel a pilot is aboard without them having to name it.
     */
    @Nullable
    public static Airship aboard(Entity entity) {
        SubLevel subLevel = Sable.HELPER.getTrackingOrVehicleSubLevel(entity);
        return subLevel instanceof ServerSubLevel server && !server.isRemoved() ? new Airship(server) : null;
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

    /**
     * The lowest and highest chunk of the plot's <em>allocation</em>, or {@code null} when the plot
     * is not loaded.
     *
     * <p><strong>This is not the hull.</strong> Sable reserves a fixed square of the plot grid for a
     * sub-level - {@code LevelPlot.logSize} chunks a side - and hands back that whole square here
     * however few blocks the ship actually has in it. Sizing anything per-chunk off this is
     * therefore work proportional to the reservation rather than to the vessel, which is identical
     * for a raft and for a battleship. Residency learned that the hard way: claiming every chunk in
     * here force-loaded the entire reservation on a drive's first tick and hung the server for over a
     * minute. Use {@link #hullChunkMin()}/{@link #hullChunkMax()} for "what does this ship occupy".
     */
    @Nullable
    public ChunkPos plotChunkMin() {
        LevelPlot plot = subLevel.getPlot();
        return plot == null ? null : plot.getChunkMin();
    }

    /**
     * The lowest and highest chunk the airship's <em>blocks</em> occupy, in the level's own chunk
     * space, or {@code null} when the plot is not loaded.
     *
     * <p>Taken from the plot's own bounding box, which Sable keeps to the blocks that are really
     * there, so this grows with the vessel instead of with its reservation. Plot-space block
     * coordinates are coordinates in the parent level's chunk space already - that is what makes a
     * block aboard a ship read as a number in the millions - so shifting them down by four gives
     * chunk positions directly, with no transform.
     */
    @Nullable
    public ChunkPos hullChunkMin() {
        LevelPlot plot = subLevel.getPlot();
        if (plot == null) {
            return null;
        }
        BoundingBox3ic bounds = plot.getBoundingBox();
        return bounds == null ? null : new ChunkPos(bounds.minX() >> 4, bounds.minZ() >> 4);
    }

    /** The far corner of {@link #hullChunkMin()}. */
    @Nullable
    public ChunkPos hullChunkMax() {
        LevelPlot plot = subLevel.getPlot();
        if (plot == null) {
            return null;
        }
        BoundingBox3ic bounds = plot.getBoundingBox();
        return bounds == null ? null : new ChunkPos(bounds.maxX() >> 4, bounds.maxZ() >> 4);
    }

    @Nullable
    public ChunkPos plotChunkMax() {
        LevelPlot plot = subLevel.getPlot();
        return plot == null ? null : plot.getChunkMax();
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

    // -------------------------------------------------------------- assembly

    /**
     * World-space bounds of the whole physically-connected assembly, not just the hull's own plot.
     *
     * <p>{@link #worldBounds()} is one sub-level's box, and a vessel is routinely more than that.
     * Sable joins child sub-levels to a hull with physics constraints, and every bearing on the deck
     * spins a Create contraption that lives as its own entity <em>outside</em> the plot - a propeller
     * on an Aeronautics propeller bearing being the case that bites. Neither reaches
     * {@link #worldBounds()}, so a warp corridor or a launch-clearance volume sized from it alone
     * leaves the propellers to clip whatever the bare hull just cleared.
     *
     * <p>This unions all of it: the connected chain of sub-levels
     * ({@link SubLevelHelper#getConnectedChain}) and every bearing-mounted contraption's own world box.
     * Create keeps that box wide enough to enclose the spinning sweep, which is exactly the volume
     * that has to stay clear. A contraption box that lands implausibly far from the hull - a stale or
     * wrong-framed entity - is dropped rather than trusted, so it can never balloon the corridor.
     *
     * <p><strong>The clearance checks deliberately do not use this.</strong> Sizing a corridor from
     * the assembly meant a propeller's swept box - which Create keeps wide enough to enclose a full
     * rotation - widened every departure volume by the reach of the blades, and the connected plots
     * folded into the box were then read as obstructions by the very check the box was built for.
     * Departure and arrival both measure the hull's own plot instead; see
     * {@link uk.co.iceconchy.aerowarptics.warp.LaunchClearance} for why that is the right footprint
     * rather than merely the cheaper one.
     */
    public BoundingBox3d assemblyBounds() {
        BoundingBox3dc hull = subLevel.boundingBox();
        double[] box = {hull.minX(), hull.minY(), hull.minZ(), hull.maxX(), hull.maxY(), hull.maxZ()};
        for (SubLevel connected : chain()) {
            BoundingBox3dc b = connected.boundingBox();
            include(box, b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
        }
        for (AABB contraption : contraptionBounds()) {
            include(box, contraption.minX, contraption.minY, contraption.minZ,
                    contraption.maxX, contraption.maxY, contraption.maxZ);
        }
        return new BoundingBox3d(box[0], box[1], box[2], box[3], box[4], box[5]);
    }

    /**
     * Every sub-level that is part of <em>this one vessel</em>: the hull's own plot, plus whatever
     * Sable has constrained to it.
     *
     * <p>Asked by the clearance checks before anything is called an obstruction. A vessel is
     * routinely several sub-levels - a tender, a gondola, a turret on its own plot - and they all fly
     * together, so none of them is "another airship" to the hull they are joined to. The obstruction
     * test used to excuse only {@link #subLevel()}, which meant any such vessel read its own attached
     * plot as a ship standing in its path and could never be cleared to launch at all: the sub-levels
     * stopping warps. Identity-keyed, because Sable's sub-levels are compared by reference
     * everywhere else here and nothing promises a useful {@code equals}.
     */
    public Set<SubLevel> assemblySubLevels() {
        Set<SubLevel> own = Collections.newSetFromMap(new IdentityHashMap<>());
        own.addAll(chain());
        return own;
    }

    /**
     * The hull, then every sub-level joined to it, found both ways there are of finding them.
     *
     * <p>Sable's own walk ({@link SubLevelHelper#getConnectedChain}) follows each plot's list of actors,
     * and that list has the lifecycle {@link #machines} was written to avoid: a machine is on it only if
     * its block has changed since the plot was made. A Swivel Bearing missing from it is a bearing whose
     * plate - the turret, the gondola, whatever it carries - is not in the chain, and the clearance checks
     * then read the ship's own turret as another ship standing in its path and refuse every launch. So the
     * same question is also asked of every machine actually standing in each plot's chunks, and the two
     * answers are put together.
     */
    private List<SubLevel> chain() {
        return VesselChain.from(subLevel, Airship::joinedTo);
    }

    /** Every sub-level the machines aboard one say they are joined to. */
    private static List<SubLevel> joinedTo(SubLevel member) {
        LevelPlot plot = member.isRemoved() ? null : member.getPlot();
        if (plot == null) {
            // Nothing to read, and Sable's walk would be reading the same plot.
            return List.of();
        }
        List<SubLevel> joined = new ArrayList<>(SubLevelHelper.getConnectedChain(member));
        // A rope's joint is on no machine's list either: whatever hangs on one is part of the vessel.
        Fastenings.probe("rope parts", () -> SimulatedTies.ropedTo(member, joined));
        for (PlotChunkHolder holder : plot.getLoadedChunks()) {
            LevelChunk chunk = holder.getChunk();
            if (chunk == null) {
                continue;
            }
            for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                if (blockEntity instanceof RiftBondBlockEntity bond && !blockEntity.isRemoved()) {
                    // No joint, so nothing of Sable's knows of it: a section that only flies alongside is
                    // part of the vessel because its builder has said so. See RiftBonds.
                    joined.addAll(RiftBonds.bondedTo(bond));
                    continue;
                }
                if (blockEntity.isRemoved() || !(blockEntity instanceof BlockEntitySubLevelActor actor)
                        || UNASKABLE.contains(blockEntity.getClass())) {
                    continue;
                }
                try {
                    Iterable<SubLevel> dependencies = actor.sable$getConnectionDependencies();
                    if (dependencies != null) {
                        for (SubLevel dependency : dependencies) {
                            joined.add(dependency);
                        }
                    }
                } catch (RuntimeException | LinkageError e) {
                    // Asked of machines Sable itself may never have asked, every tick of a flight. One that
                    // cannot answer is left out from then on rather than taking the server down with it.
                    UNASKABLE.add(blockEntity.getClass());
                    Trace.warn(Trace.LAUNCH, "{} cannot say what it joins its ship to, and will not be asked"
                            + " again this session: {}", blockEntity.getClass().getName(), e.toString());
                }
            }
        }
        return joined;
    }

    /** Machine classes whose connections could not be read, so that each is only tried, and reported, once. */
    private static final Set<Class<?>> UNASKABLE = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Every sub-level joined to this hull that goes where it goes: {@link #assemblySubLevels()} without the
     * hull itself, and only those that are loaded and have a plot to move.
     *
     * <p>The chain followed through each machine's connection dependencies: a Docking Connector's partner,
     * a Swivel Bearing's plate, a Spring's pair - and through every Rift Bond, which joins nothing and is
     * there to say that a section flying alongside belongs to the ship. Sable's own walk is what Dimensional Sable moves on a
     * crossing; this one never finds less than that, and finds the machines Sable's list of actors has
     * missed - see {@link #chain()}. {@link #relocate} carries all of it, and {@link Fastenings} refuses a
     * warp for anything joined to something that is not in it.
     */
    public List<ServerSubLevel> parts() {
        List<ServerSubLevel> parts = new ArrayList<>();
        for (SubLevel connected : chain()) {
            if (connected != subLevel && connected instanceof ServerSubLevel server && !parts.contains(server)
                    && !server.isRemoved() && server.getPlot() != null) {
                parts.add(server);
            }
        }
        return parts;
    }

    /**
     * How many sub-levels Sable's own walk joins to this hull, for the trace to set beside
     * {@link #parts()}. When this is the smaller, a machine joining the vessel together is missing from
     * Sable's list of actors - which is the case {@link #chain()} exists for, and worth being able to see.
     */
    public int sableParts() {
        return Math.max(0, SubLevelHelper.getConnectedChain(subLevel).size() - 1);
    }

    /** The hull and then each of its {@link #parts()}: everything a warp moves. */
    public List<Airship> vessel() {
        List<Airship> vessel = new ArrayList<>();
        vessel.add(this);
        for (ServerSubLevel part : parts()) {
            vessel.add(new Airship(part));
        }
        return vessel;
    }

    /**
     * The whole vessel's block box in <em>this hull's own plot space</em>: the hull's plot, grown to take in
     * every one of its {@link #parts()} where it sits against the hull now. {@code null} when the hull has
     * no plot to measure.
     *
     * <p>What a warp is sized from. Measuring the hull's plot alone was right while a ship was one
     * sub-level, and gave a ship built in sections a rift the size of whichever section the drive was in:
     * the others flew past the aperture, and were put down at the far end in ground nobody had looked at.
     * Kept in the hull's frame, not the world's, so that everything already written against a plot box -
     * the arrival search's placement, {@link uk.co.iceconchy.aerowarptics.warp.FlightGeometry} - takes the
     * larger box without knowing there are parts at all, and so that the answer does not change with the
     * compass. Bearing-mounted contraptions are still left out, for the reason {@link #assemblyBounds()}
     * gives: a propeller's box is its whole sweep.
     *
     * <p>With no parts this is exactly the hull's plot box, block bounds made exclusive.
     */
    @Nullable
    public BoundingBox3d vesselShipBounds() {
        LevelPlot plot = subLevel.getPlot();
        BoundingBox3ic own = plot == null ? null : plot.getBoundingBox();
        if (own == null) {
            return null;
        }
        // Block bounds are inclusive; a block at maxX occupies up to maxX + 1.
        double[] box = {own.minX(), own.minY(), own.minZ(), own.maxX() + 1.0D, own.maxY() + 1.0D, own.maxZ() + 1.0D};
        Pose3dc hull = subLevel.logicalPose();
        for (ServerSubLevel part : parts()) {
            BoundingBox3ic theirs = part.getPlot().getBoundingBox();
            if (theirs == null) {
                continue;
            }
            Pose3dc pose = part.logicalPose();
            VesselBox.include(box, theirs.minX(), theirs.minY(), theirs.minZ(),
                    theirs.maxX() + 1.0D, theirs.maxY() + 1.0D, theirs.maxZ() + 1.0D,
                    corner -> hull.transformPositionInverse(pose.transformPosition(corner)));
        }
        return new BoundingBox3d(box[0], box[1], box[2], box[3], box[4], box[5]);
    }

    /**
     * World-space bounds of the hull and every one of its {@link #parts()}, and nothing hung off them:
     * {@link #assemblyBounds()} without the contraptions. With no parts, exactly {@link #worldBounds()}.
     */
    public BoundingBox3dc vesselWorldBounds() {
        List<ServerSubLevel> parts = parts();
        BoundingBox3dc hull = subLevel.boundingBox();
        if (parts.isEmpty()) {
            return hull;
        }
        double[] box = {hull.minX(), hull.minY(), hull.minZ(), hull.maxX(), hull.maxY(), hull.maxZ()};
        for (ServerSubLevel part : parts) {
            BoundingBox3dc b = part.boundingBox();
            include(box, b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
        }
        return new BoundingBox3d(box[0], box[1], box[2], box[3], box[4], box[5]);
    }

    /**
     * World-space centre of the whole vessel: the middle of {@link #vesselShipBounds()}, put into the world.
     * With no parts, exactly {@link #centre}, so a ship of one sub-level flies as it always has.
     */
    public Vector3d vesselCentre(Vector3d dest) {
        BoundingBox3d box = parts().isEmpty() ? null : vesselShipBounds();
        if (box == null) {
            return centre(dest);
        }
        return subLevel.logicalPose().transformPosition(box.center(dest));
    }

    /** Volume of {@link #vesselShipBounds()}: what a check over the whole vessel is budgeted from. */
    public double vesselVolume() {
        BoundingBox3d box = vesselShipBounds();
        return box == null ? 0.0D : box.width() * box.height() * box.length();
    }

    /**
     * The world boxes of every bearing-mounted contraption on the hull - a propeller spun by a
     * propeller bearing, a windmill on a mechanical bearing - that is close enough to the hull to be
     * believed. Empty when nothing is mounted or nothing is currently assembled.
     */
    private List<AABB> contraptionBounds() {
        BoundingBox3dc hull = subLevel.boundingBox();
        Vector3d hullCentre = hull.center(new Vector3d());
        // A contraption box is believed only within reach of the hull: its centre no further out
        // than the hull's own diagonal plus a fixed slack. This rejects an entity read in a
        // different coordinate frame, which would otherwise union in as a corridor the size of the world.
        double reach = new Vector3d(hull.width(), hull.height(), hull.length()).length() + 64.0D;
        double reachSq = reach * reach;

        List<AABB> found = new ArrayList<>();
        for (MechanicalBearingBlockEntity bearing : machines(MechanicalBearingBlockEntity.class)) {
            ControlledContraptionEntity moved = bearing.getMovedContraption();
            if (moved == null || moved.isRemoved()) {
                continue;
            }
            AABB box = moved.getBoundingBox();
            if (!isFinite(box)
                    || box.getCenter().distanceToSqr(hullCentre.x, hullCentre.y, hullCentre.z) > reachSq) {
                continue;
            }
            found.add(box);
        }
        return found;
    }

    private static void include(double[] box, double minX, double minY, double minZ,
                                double maxX, double maxY, double maxZ) {
        box[0] = Math.min(box[0], minX);
        box[1] = Math.min(box[1], minY);
        box[2] = Math.min(box[2], minZ);
        box[3] = Math.max(box[3], maxX);
        box[4] = Math.max(box[4], maxY);
        box[5] = Math.max(box[5], maxZ);
    }

    private static boolean isFinite(AABB box) {
        return Double.isFinite(box.minX) && Double.isFinite(box.minY) && Double.isFinite(box.minZ)
                && Double.isFinite(box.maxX) && Double.isFinite(box.maxY) && Double.isFinite(box.maxZ);
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
     * How far outside the hull's own blocks, in ship space, an unsupported entity still counts as
     * aboard it for a relocation - see {@link #withinHull}. A player's feet overhang the last plank by a
     * fraction of a block, and a margin of one takes in someone at the very rail.
     */
    private static final double HULL_MARGIN = 1.0D;

    /**
     * How far above the highest block an unsupported entity still counts as aboard. A jump from the
     * deck peaks at about a block and a quarter; this leaves room for a jump from the top of a cabin.
     */
    private static final double HULL_HEADROOM = 3.0D;

    /**
     * Moves the entire airship - structure, block entities, passengers and cargo - to a new place in
     * the same {@link ServerLevel}.
     *
     * <p>The move is performed by Sable's physics pipeline, the same call the {@code /sable sub_level
     * teleport} command uses, so the sub-level's plot, its block entities and everything attached to
     * it come along untouched. Everything carried by the hull (see {@link #carried}) is read in ship
     * space before the move and put back in the new frame afterwards, which reproduces its position
     * relative to the deck exactly.
     *
     * <p>Then every client holding a copy of the hull is told exactly where it went, and a client whose
     * own player went with it is told that too - see {@link ClientboundFoldCrossedPacket} and
     * {@code FoldCrossings}. That notice is not a courtesy. A client left to read the jump out of
     * Sable's pose stream interpolates it as movement: it draws the hull part of the way across, in
     * chunks it does not have, and throws whoever is standing on it the same distance. Those were the
     * two things players reported - the ship vanishing at a warp, and being launched off a deck they
     * were standing on - and they were one bug.
     *
     * <p>Every one of the hull's {@link #parts()} goes with it, by the same rigid jump, with its own
     * passengers and its own notice to the clients that have it. This used to move the hull alone, and a
     * ship docked alongside, or a turret on a Swivel Bearing, was left behind still joined to it: the
     * joint, stretched across the whole warp, then dragged the two back together. Moving every part by one
     * transform keeps every joint anchored in a plot exactly where it was, because each end moves with
     * the plot it is anchored in. A rope is the one joint that is not anchored like that: its middle is in
     * world space, so every rope with both ends aboard is lifted out of the physics world first and put
     * back through the same jump - see {@link CarriedRopes}.
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

        // All read before anything moves: who is aboard and where on each part, and which clients have a
        // copy of it. Afterwards the vessel is somewhere else and so, as far as a search is concerned, is
        // everyone on it. Somebody standing where two parts meet is counted once, on the hull if either.
        Set<Entity> counted = Collections.newSetFromMap(new IdentityHashMap<>());
        Moving hull = Moving.read(this, pipeline, counted);
        List<Moving> parts = new ArrayList<>();
        Set<UUID> vessel = new HashSet<>();
        vessel.add(uuid());
        for (ServerSubLevel part : parts()) {
            parts.add(Moving.read(of(part), pipeline, counted));
            vessel.add(part.getUniqueId());
        }
        // Out of the physics world before anything moves, or each rope is dragged by its ends across the jump.
        CarriedRopes ropes = CarriedRopes.lift(level(), vessel);

        pipeline.resetVelocity(subLevel);
        pipeline.teleport(subLevel, destination, orientation);

        // teleport() writes straight into logicalPose, so this is already the new frame.
        HullJump jump = HullJump.between(hull.before(), subLevel.logicalPose());
        hull.arrive(pipeline, retainedVelocity);
        for (Moving part : parts) {
            Pose3d there = jump.moved(part.before());
            ServerSubLevel moving = part.airship().subLevel();
            pipeline.resetVelocity(moving);
            pipeline.teleport(moving, there.position(), there.orientation());
            part.arrive(pipeline, retainedVelocity);
        }
        ropes.land(jump);

        announce(jump, hull.carried(), hull.watching());
        for (Moving part : parts) {
            part.airship().announce(jump, part.carried(), part.watching());
        }
        return true;
    }

    /**
     * One sub-level of the vessel on its way across: where it was, how it was moving, and what it had
     * aboard, read before anything moved.
     */
    private record Moving(Airship airship, Pose3d before, Vector3d linear, Vector3d angular,
                          List<Carried> carried, List<UUID> watching) {

        static Moving read(Airship airship, PhysicsPipeline pipeline, Set<Entity> counted) {
            ServerSubLevel sub = airship.subLevel();
            Vector3d linear = new Vector3d();
            Vector3d angular = new Vector3d();
            pipeline.getLinearVelocity(sub, linear);
            pipeline.getAngularVelocity(sub, angular);
            Pose3d before = new Pose3d(sub.logicalPose());
            List<Carried> carried = new ArrayList<>();
            for (Carried passenger : airship.carried(before)) {
                if (counted.add(passenger.entity())) {
                    carried.add(passenger);
                }
            }
            return new Moving(airship, before, linear, angular, carried, new ArrayList<>(sub.getTrackingPlayers()));
        }

        /** Puts its passengers back and its motion back, once it has been teleported. */
        void arrive(PhysicsPipeline pipeline, double retainedVelocity) {
            ServerSubLevel sub = airship.subLevel();
            Pose3dc after = sub.logicalPose();
            for (Carried passenger : carried) {
                passenger.land(after);
            }
            if (retainedVelocity > 0.0D) {
                pipeline.addLinearAndAngularVelocity(sub,
                        linear.mul(retainedVelocity, new Vector3d()),
                        angular.mul(retainedVelocity, new Vector3d()));
            }
            pipeline.wakeUp(sub);

            // Collapse the pose delta so nothing on board reads the jump as a one-tick velocity spike.
            sub.updateLastPose();
            sub.updateBoundingBox();
            sub.forceUpdateGlobalBounds();
        }
    }

    /**
     * Something the hull takes with it when it is relocated, and where on the hull it was.
     *
     * @param stowaway whether it was taken although Sable did not have it aboard - see {@link #carried}
     */
    private record Carried(Entity entity, Vec3 local, Vec3 localMotion, boolean stowaway) {

        /**
         * Puts it back on the hull in the hull's new frame.
         *
         * <p>Through the entity's own setter, not the raw write Sable's push and pop use. The raw write
         * moves the coordinates and nothing else, so the level went on filing the entity under the
         * section it had left - at the far end of a warp - and every search of the arrival missed it
         * until it next moved of its own accord. For a player that was until their client caught up;
         * in the meantime the drive's own crew count, a search by area, found nobody aboard and
         * "recovered" them with a second teleport.
         *
         * <p>Facing is left alone, as Sable's own pop leaves it; motion is turned with the hull.
         */
        void land(Pose3dc pose) {
            entity.setPos(pose.transformPosition(local));
            entity.setDeltaMovement(pose.transformNormal(localMotion));
        }
    }

    /**
     * Everything the hull should take with it: whatever Sable has standing on or riding it, and anything
     * else unsupported inside it.
     *
     * <p>Sable's answer alone is what {@link #passengers()} gives, and it misses the moment that
     * matters: a player whose feet left the deck - a jump, a step off a ledge inside the hold - is not
     * on the hull as far as Sable is concerned until they land. At a crossing that is a player left
     * four thousand blocks behind their ship, which leaves the range their client tracks it at, so
     * their client deletes it: the ship vanishes for them, and they fall until the manifest fetches
     * them. So anything in the air inside the hull's own footprint goes too. Standing on real ground is
     * what rules a bystander out - someone beside a car at a gate is not in it - and a spectator
     * passing through a hull is not aboard it either.
     */
    private List<Carried> carried(Pose3dc pose) {
        LevelPlot plot = subLevel.getPlot();
        BoundingBox3ic hull = plot == null ? null : plot.getBoundingBox();
        AABB bounds = worldBounds().toMojang().inflate(2.0D + HULL_HEADROOM);
        List<Carried> found = new ArrayList<>();
        for (Entity entity : level().getEntities((Entity) null, bounds, e -> !e.isRemoved())) {
            if (entity.isPassenger()) {
                continue; // moved along with its vehicle
            }
            SubLevel tracking = Sable.HELPER.getTrackingOrVehicleSubLevel(entity);
            Vec3 local = pose.transformPositionInverse(entity.position());
            boolean aboard = tracking == subLevel;
            boolean stowaway = !aboard && tracking == null && !entity.onGround() && !entity.isSpectator() && hull != null
                    && withinHull(hull.minX(), hull.minY(), hull.minZ(), hull.maxX(), hull.maxY(), hull.maxZ(),
                    local.x, local.y, local.z);
            if (aboard || stowaway) {
                found.add(new Carried(entity, local, pose.transformNormalInverse(entity.getDeltaMovement()), stowaway));
            }
        }
        return found;
    }

    /**
     * Whether a point in ship space lies within a hull's block bounds, give or take the rail and a
     * jump's height above it. Pure, so the rule can be tested without a hull.
     */
    public static boolean withinHull(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                     double x, double y, double z) {
        return x >= minX - HULL_MARGIN && x <= maxX + 1 + HULL_MARGIN
                && z >= minZ - HULL_MARGIN && z <= maxZ + 1 + HULL_MARGIN
                && y >= minY && y <= maxY + 1 + HULL_HEADROOM;
    }

    /**
     * Tells every client that has this hull where it went.
     *
     * <p>A player who went with it is told they were carried, and their client moves them by the same
     * jump as its copy of the hull, so they keep their plank of the deck in their own view of it. That
     * replaces the world-space teleport this used to send, which put them where the deck is on the
     * server - a tick and a half ahead of where their client draws it, which on a moving hull is off the
     * edge - and, worse, a tick before their client had the hull there at all.
     *
     * <p>A stowaway is sent the teleport as well, after the notice. The server had them off the hull, so
     * whatever their client sends before the notice reaches it is a position at the near end; a real
     * teleport is what makes the server wait for the client to catch up rather than believe it.
     *
     * <p>Everyone else holding a copy only has it moved. That is exactly Sable's own list of who is
     * tracking the hull, read before the move: a client that only starts tracking it afterwards is sent
     * it where it already is.
     */
    private void announce(HullJump jump, List<Carried> carried, List<UUID> watching) {
        Set<UUID> told = new HashSet<>();
        for (Carried passenger : carried) {
            if (passenger.entity() instanceof ServerPlayer player && told.add(player.getUUID())) {
                AWNetwork.sendTo(player, ClientboundFoldCrossedPacket.of(uuid(), jump, true));
                if (passenger.stowaway()) {
                    player.connection.teleport(player.getX(), player.getY(), player.getZ(),
                            player.getYRot(), player.getXRot());
                }
            }
        }
        ClientboundFoldCrossedPacket notice = ClientboundFoldCrossedPacket.of(uuid(), jump, false);
        for (UUID id : watching) {
            if (told.add(id)) {
                ServerPlayer watcher = level().getServer().getPlayerList().getPlayer(id);
                if (watcher != null) {
                    AWNetwork.sendTo(watcher, notice);
                }
            }
        }
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
     * How fast the airship is turning, in radians per tick - per tick for the same reason
     * {@link #velocity()} is, so the two can be compared and cushioned in the same units.
     */
    public Vector3d angularVelocity() {
        PhysicsPipeline pipeline = pipeline();
        Vector3d angular = new Vector3d();
        if (pipeline != null && !subLevel.isRemoved()) {
            pipeline.getAngularVelocity(subLevel, angular);
            angular.mul(1.0D / PHYSICS_TICKS_PER_SECOND);
        }
        return angular;
    }

    /**
     * Adds to the airship's velocity without replacing it.
     *
     * <p>The opposite of {@link #driveVelocity}, which resets the hull and commands a speed outright.
     * A Spatial Stabiliser has to leave everything the ship's own machinery is doing in place and only
     * take the edge off it, so it hands over a correction and nothing else. Both halves are converted
     * to per-second here, where every other velocity crosses into the pipeline.
     *
     * <p>Not clamped: a correction is always a fraction of a change the hull has already made, so it
     * is never larger than something the physics produced by itself.
     */
    public void nudgeVelocity(Vector3dc linearBlocksPerTick, Vector3dc angularRadiansPerTick) {
        PhysicsPipeline pipeline = pipeline();
        if (pipeline == null || subLevel.isRemoved()) {
            return;
        }
        pipeline.addLinearAndAngularVelocity(subLevel, toPhysicsVelocity(linearBlocksPerTick),
                new Vector3d(angularRadiansPerTick).mul(PHYSICS_TICKS_PER_SECOND));
    }

    /**
     * Commands the airship's velocity outright for this tick.
     *
     * <p>Used while the server is flying the ship through a warp. Setting velocity rather than
     * teleporting per tick means Sable moves the hull exactly the way it moves any airship, so
     * everything standing on the deck is carried along by the same code that carries it in normal
     * flight - and because the velocity is re-commanded every tick, gravity, lift and thrust from the
     * ship's own machinery are all overridden for the duration.
     *
     * <p>Every one of the hull's {@link #parts()} is commanded the same, so the vessel flies as one body. A
     * docked ship left to the joint would be dead weight dragged at warp speed, pulling the hull off its
     * line and loading the joint with the whole of its mass every tick.
     */
    public void driveVelocity(Vector3dc blocksPerTick) {
        PhysicsPipeline pipeline = pipeline();
        if (pipeline == null || subLevel.isRemoved()) {
            return;
        }
        // Clamp at source. A frame slip in the emerge command, a solver glitch or momentum that
        // stacked would otherwise be handed to the pipeline verbatim and integrated into a hull
        // launched across the world - the "yeet". The ceiling sits far above any legitimate passage
        // speed, so a real warp never touches it; anything that does was never going to be flight.
        Vector3d commanded = toPhysicsVelocity(clampSpeed(blocksPerTick, maxCommandedSpeed()));
        pipeline.resetVelocity(subLevel);
        pipeline.addLinearAndAngularVelocity(subLevel, commanded, ZERO);
        pipeline.wakeUp(subLevel);
        for (ServerSubLevel part : parts()) {
            pipeline.resetVelocity(part);
            pipeline.addLinearAndAngularVelocity(part, commanded, ZERO);
            pipeline.wakeUp(part);
        }
    }

    /**
     * Commands the airship's velocity <em>and</em> its turn outright for this tick.
     *
     * <p>{@link #driveVelocity}, with a turn: what a Rift Plotter's dock helper steers a caught hull with, gliding it onto
     * the rift's axis and turning it end-on, a little each tick. Its {@link #parts()} are given the same turn and the
     * speed a rigid body turning about the hull's middle would give them there, so a docked ship or a turret on a bearing
     * swings round with the hull rather than being dragged across it by the joint.
     *
     * @param linearBlocksPerTick   the hull's velocity
     * @param angularRadiansPerTick its turn, about a world axis
     */
    public void steerVelocity(Vector3dc linearBlocksPerTick, Vector3dc angularRadiansPerTick) {
        PhysicsPipeline pipeline = pipeline();
        if (pipeline == null || subLevel.isRemoved()) {
            return;
        }
        Vector3d linear = clampSpeed(linearBlocksPerTick, maxCommandedSpeed());
        Vector3d angular = new Vector3d(angularRadiansPerTick);
        pipeline.resetVelocity(subLevel);
        pipeline.addLinearAndAngularVelocity(subLevel, toPhysicsVelocity(linear),
                new Vector3d(angular).mul(PHYSICS_TICKS_PER_SECOND));
        pipeline.wakeUp(subLevel);
        Vector3d middle = centre(new Vector3d());
        for (ServerSubLevel part : parts()) {
            Vector3d arm = of(part).centre(new Vector3d()).sub(middle);
            Vector3d swing = new Vector3d(angular).cross(arm).add(linear);
            pipeline.resetVelocity(part);
            pipeline.addLinearAndAngularVelocity(part, toPhysicsVelocity(swing),
                    new Vector3d(angular).mul(PHYSICS_TICKS_PER_SECOND));
            pipeline.wakeUp(part);
        }
    }

    /**
     * Commands the airship's horizontal velocity for this tick, and leaves the rest of its motion alone.
     *
     * <p>What a free cruise flies with, and the whole reason a cruise can be steered. {@link #driveVelocity}
     * resets the hull outright - rotation and vertical speed included - because a warp is the server
     * flying the ship. A cruise is the server supplying thrust while the pilot flies it: whatever turn the
     * ship's own propellers put on it, and whatever its balloons and gravity are doing to its height, are
     * read back and handed straight back to the pipeline, and only the horizontal is replaced.
     *
     * <p>The hull's {@link #parts()} are given the same horizontal speed and keep the rest of their own
     * motion, for the reason {@link #driveVelocity} gives them its speed.
     *
     * @param horizontalBlocksPerTick the velocity wanted across the ground; its Y is ignored
     */
    public void cruiseVelocity(Vector3dc horizontalBlocksPerTick) {
        PhysicsPipeline pipeline = pipeline();
        if (pipeline == null || subLevel.isRemoved()) {
            return;
        }
        Vector3d across = toPhysicsVelocity(clampSpeed(
                new Vector3d(horizontalBlocksPerTick.x(), 0.0D, horizontalBlocksPerTick.z()), maxCommandedSpeed()));
        cruise(pipeline, subLevel, across);
        for (ServerSubLevel part : parts()) {
            cruise(pipeline, part, across);
        }
    }

    /** Replaces one sub-level's horizontal velocity, in the pipeline's units, and hands back the rest. */
    private static void cruise(PhysicsPipeline pipeline, ServerSubLevel sub, Vector3dc across) {
        Vector3d linear = new Vector3d();
        Vector3d angular = new Vector3d();
        pipeline.getLinearVelocity(sub, linear);
        pipeline.getAngularVelocity(sub, angular);
        // Already in the pipeline's units: read back from it, handed back to it unchanged.
        Vector3d wanted = new Vector3d(across.x(), linear.y, across.z());
        pipeline.resetVelocity(sub);
        pipeline.addLinearAndAngularVelocity(sub, wanted, angular);
        pipeline.wakeUp(sub);
    }

    /**
     * A commanded velocity with its speed capped, keeping its direction.
     *
     * <p>Pure, so the cap can be tested without a physics pipeline: a speed at or below the ceiling is
     * returned unchanged, one above it is scaled back to exactly the ceiling along the same bearing,
     * and a zero or non-finite command is left alone rather than turned into a division by zero.
     */
    public static Vector3d clampSpeed(Vector3dc blocksPerTick, double ceiling) {
        double speed = blocksPerTick.length();
        if (!Double.isFinite(speed) || speed <= ceiling || speed <= 0.0D) {
            return new Vector3d(blocksPerTick);
        }
        return new Vector3d(blocksPerTick).mul(ceiling / speed);
    }

    /** The configured velocity ceiling, falling back to a safe default when the config is not loaded. */
    public static double maxCommandedSpeed() {
        try {
            return uk.co.iceconchy.aerowarptics.AWConfig.MAX_COMMANDED_SPEED.get();
        } catch (IllegalStateException e) {
            // Config not loaded yet - happens in unit tests.
            return 48.0D;
        }
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
