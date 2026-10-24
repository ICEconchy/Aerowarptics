package uk.co.iceconchy.aerowarptics.warp;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.joml.Vector3dc;
import uk.co.iceconchy.aerowarptics.airship.Airship;

import java.util.Set;

/**
 * How far a cruising ship can see ahead of its bow: to the first solid block, the first ground the
 * server has not loaded, or the end of what it was asked to read.
 *
 * <p>The check a waypoint jump makes at its mooring, {@link LaunchClearance}, is the wrong tool for a
 * ship already doing ninety blocks a second. It <em>loads</em> what it reads - synchronously, generating
 * terrain if it has to - which at a mooring costs a hitch and at cruise speed would stall the server on
 * every look. This reads only what is already there, and treats ground that is not as a wall it cannot
 * see past. That is not a limitation worked around; it is the rule that keeps a cruise honest. A ship
 * may never go faster than it could stop in the world that exists, so a cruise into new country flies
 * exactly as fast as the server can build it.
 *
 * <p>It reads the hull's leading faces rather than the whole hull at every step. Moving a box forward by
 * a few blocks only ever uncovers a slab ahead of its front face and one beside its side face, so that is
 * all that is read - a cost of the hull's cross-section per block of reach, not its volume, which is the
 * difference between a flying city being able to cruise at all or not.
 *
 * <p>The core is free of Minecraft, behind {@link World}, so a wall, a gap and a stretch of unloaded
 * ground can each be put in front of a hull in a test.
 */
public final class CruiseSensor {

    private CruiseSensor() {
    }

    /** Blocks between one look and the next along the bow. */
    public static final double STEP = 4.0D;

    /**
     * How much of a hull's underside is forgiven.
     *
     * <p>Sable's world box floats a hair below a whole block on a ship resting at an exact height. Read
     * literally, the ground a cruising ship skims over would be inside its own leading face and every look
     * ahead would report a wall at zero.
     */
    private static final double KEEL_TOLERANCE = 0.05D;

    /** What stopped the look ahead. */
    public enum Limit {
        /** Nothing: the whole reach is open and loaded. */
        CLEAR,
        /** A solid block, or another ship. */
        TERRAIN,
        /** Ground the server has not loaded yet - or so much to read that the look gave up. */
        UNLOADED
    }

    /**
     * What the look ahead found.
     *
     * @param distance how far the hull could move along the bow before reaching the limit
     * @param limit    what is there
     */
    public record Reading(double distance, Limit limit) {
    }

    /** What the sensor needs to know about the world, and nothing else. */
    public interface World {
        int minY();

        /** The highest block Y, inclusive. */
        int maxY();

        /** Whether this chunk column is loaded. The sensor never asks for one to be. */
        boolean loaded(int chunkX, int chunkZ);

        /** Whether this chunk section is entirely air, so none of its blocks need reading. */
        boolean sectionEmpty(int chunkX, int sectionY, int chunkZ);

        boolean solid(int x, int y, int z);
    }

    /**
     * Looks ahead of a hull.
     *
     * @param bowX   the bow's X, unit length together with {@code bowZ}; a cruise flies level
     * @param reach  how far to look, in blocks
     * @param budget the most blocks this look may read; running out stops it where it got to, as
     *               {@link Limit#UNLOADED}, so a ship cannot outrun what it had time to check
     */
    public static Reading ahead(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                                double bowX, double bowZ, double reach, long budget, World world) {
        int fromY = Math.max(world.minY(), (int) Math.floor(minY + KEEL_TOLERANCE));
        int toY = Math.min(world.maxY(), (int) Math.ceil(maxY) - 1);
        long[] reads = {0L};
        double travelled = 0.0D;
        while (travelled < reach) {
            double next = Math.min(reach, travelled + STEP);
            Limit found = uncovered(minX, minZ, maxX, maxZ, bowX, bowZ, travelled, next, fromY, toY,
                    budget, reads, world);
            if (found != Limit.CLEAR) {
                return new Reading(travelled, found);
            }
            travelled = next;
        }
        return new Reading(reach, Limit.CLEAR);
    }

    /**
     * Reads what the hull uncovers moving from {@code from} to {@code to} along the bow: the slab ahead of
     * whichever X face leads, and the slab ahead of whichever Z face leads.
     */
    private static Limit uncovered(double minX, double minZ, double maxX, double maxZ, double bowX, double bowZ,
                                   double from, double to, int fromY, int toY, long budget, long[] reads,
                                   World world) {
        if (fromY > toY) {
            return Limit.CLEAR; // entirely above or below the world: nothing to hit
        }
        double x0 = bowX * from;
        double x1 = bowX * to;
        double z0 = bowZ * from;
        double z1 = bowZ * to;
        // The hull's extent across the other axis over this step, so a diagonal bearing reads the corner.
        double spanMinX = minX + Math.min(x0, x1);
        double spanMaxX = maxX + Math.max(x0, x1);
        double spanMinZ = minZ + Math.min(z0, z1);
        double spanMaxZ = maxZ + Math.max(z0, z1);
        if (Math.abs(bowX) > 1.0e-9D) {
            double lo = bowX > 0.0D ? maxX + x0 : minX + x1;
            double hi = bowX > 0.0D ? maxX + x1 : minX + x0;
            Limit limit = scan(lo, hi, spanMinZ, spanMaxZ, fromY, toY, budget, reads, world);
            if (limit != Limit.CLEAR) {
                return limit;
            }
        }
        if (Math.abs(bowZ) > 1.0e-9D) {
            double lo = bowZ > 0.0D ? maxZ + z0 : minZ + z1;
            double hi = bowZ > 0.0D ? maxZ + z1 : minZ + z0;
            return scan(spanMinX, spanMaxX, lo, hi, fromY, toY, budget, reads, world);
        }
        return Limit.CLEAR;
    }

    /** Every block whose cube reaches into the given box, skipping empty sections, stopping at the first. */
    private static Limit scan(double loX, double hiX, double loZ, double hiZ, int fromY, int toY,
                              long budget, long[] reads, World world) {
        int fromX = (int) Math.floor(loX);
        int toX = (int) Math.ceil(hiX) - 1;
        int fromZ = (int) Math.floor(loZ);
        int toZ = (int) Math.ceil(hiZ) - 1;
        if (fromX > toX || fromZ > toZ) {
            return Limit.CLEAR;
        }
        for (int chunkX = fromX >> 4; chunkX <= (toX >> 4); chunkX++) {
            for (int chunkZ = fromZ >> 4; chunkZ <= (toZ >> 4); chunkZ++) {
                if (!world.loaded(chunkX, chunkZ)) {
                    return Limit.UNLOADED;
                }
                for (int sectionY = fromY >> 4; sectionY <= (toY >> 4); sectionY++) {
                    if (world.sectionEmpty(chunkX, sectionY, chunkZ)) {
                        continue;
                    }
                    int ax = Math.max(fromX, chunkX << 4);
                    int bx = Math.min(toX, (chunkX << 4) + 15);
                    int ay = Math.max(fromY, sectionY << 4);
                    int by = Math.min(toY, (sectionY << 4) + 15);
                    int az = Math.max(fromZ, chunkZ << 4);
                    int bz = Math.min(toZ, (chunkZ << 4) + 15);
                    for (int x = ax; x <= bx; x++) {
                        for (int z = az; z <= bz; z++) {
                            for (int y = ay; y <= by; y++) {
                                if (++reads[0] > budget) {
                                    return Limit.UNLOADED;
                                }
                                if (world.solid(x, y, z)) {
                                    return Limit.TERRAIN;
                                }
                            }
                        }
                    }
                }
            }
        }
        return Limit.CLEAR;
    }

    /**
     * How far a hull can move along the bow before its box meets another box, or {@code reach} if it
     * never does within it.
     *
     * <p>Another ship is not in the world's blocks, so the block scan cannot see one. Each box stands still
     * for the look - a moving ship is re-read four ticks later - and the answer is where the two boxes'
     * intervals first overlap on both horizontal axes while they already overlap vertically.
     */
    public static double boxHitDistance(double minX, double minY, double minZ, double maxX, double maxY,
                                        double maxZ, double bowX, double bowZ,
                                        double oMinX, double oMinY, double oMinZ, double oMaxX, double oMaxY,
                                        double oMaxZ, double reach) {
        if (maxY <= oMinY || minY >= oMaxY) {
            return reach;
        }
        double[] x = interval(minX, maxX, oMinX, oMaxX, bowX);
        double[] z = interval(minZ, maxZ, oMinZ, oMaxZ, bowZ);
        double enter = Math.max(0.0D, Math.max(x[0], z[0]));
        double leave = Math.min(x[1], z[1]);
        return enter < leave && enter <= reach ? enter : reach;
    }

    /** The distances along the bow over which two intervals overlap on one axis. */
    private static double[] interval(double min, double max, double oMin, double oMax, double step) {
        if (Math.abs(step) < 1.0e-9D) {
            return max > oMin && min < oMax
                    ? new double[]{Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}
                    : new double[]{Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        }
        double a = (oMin - max) / step;
        double b = (oMax - min) / step;
        return new double[]{Math.min(a, b), Math.max(a, b)};
    }

    // ---------------------------------------------------------------- runtime

    /**
     * Looks ahead of a live airship in its own level.
     *
     * @param bow   the world bow, horizontal and unit length
     * @param reach how far to look, from {@link FreeCruise#sensorReach}
     */
    public static Reading ahead(Airship airship, ServerLevel level, Vector3dc bow, double reach, long budget) {
        BoundingBox3dc hull = airship.worldBounds();
        Reading blocks = ahead(hull.minX(), hull.minY(), hull.minZ(), hull.maxX(), hull.maxY(), hull.maxZ(),
                bow.x(), bow.z(), reach, budget, new LevelWorld(level));

        // Any other ship inside the stretch the blocks said was open.
        BoundingBox3d swept = SafeArrival.sweep(hull, new org.joml.Vector3d(bow).negate(), blocks.distance());
        Set<SubLevel> own = airship.assemblySubLevels();
        double nearest = blocks.distance();
        for (SubLevel other : Sable.HELPER.getAllIntersecting(level, swept)) {
            if (own.contains(other)) {
                continue;
            }
            BoundingBox3dc box = other.boundingBox();
            nearest = Math.min(nearest, boxHitDistance(hull.minX(), hull.minY(), hull.minZ(),
                    hull.maxX(), hull.maxY(), hull.maxZ(), bow.x(), bow.z(),
                    box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ(), blocks.distance()));
        }
        return nearest < blocks.distance() ? new Reading(nearest, Limit.TERRAIN) : blocks;
    }

    /** The sensor's view of a real level: loaded chunks only, and never a load. */
    private record LevelWorld(ServerLevel level) implements World {

        @Override
        public int minY() {
            return level.getMinBuildHeight();
        }

        @Override
        public int maxY() {
            return level.getMaxBuildHeight() - 1;
        }

        @Override
        public boolean loaded(int chunkX, int chunkZ) {
            return level.getChunkSource().getChunkNow(chunkX, chunkZ) != null;
        }

        @Override
        public boolean sectionEmpty(int chunkX, int sectionY, int chunkZ) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
            if (chunk == null) {
                return false;
            }
            int index = chunk.getSectionIndexFromSectionY(sectionY);
            LevelChunkSection[] sections = chunk.getSections();
            return index < 0 || index >= sections.length || sections[index].hasOnlyAir();
        }

        @Override
        public boolean solid(int x, int y, int z) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            return !state.isAir() && !state.getCollisionShape(level, pos).isEmpty();
        }
    }
}
