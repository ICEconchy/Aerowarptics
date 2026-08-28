package uk.co.iceconchy.aerowarptics.warp;

import net.minecraft.core.BlockPos;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.airship.Airship;

import java.util.Locale;

/**
 * A running account of what a warp actually did, for when one looks wrong.
 *
 * <p>Screenshots answer "what did it look like"; they are a poor way to answer "did the stern clear
 * the plane before the teleport fired". This writes the numbers behind each stage so that question has
 * an answer rather than an impression.
 *
 * <p>Off unless {@code debug.traceWarps} is set, and every entry point returns immediately when it is
 * off, before formatting anything. When it is on it logs at INFO, so it lands in {@code latest.log}
 * without anyone having to reconfigure logging to collect it.
 */
public final class WarpTrace {

    private WarpTrace() {
    }

    /**
     * Where a hull sits relative to an aperture, along the aperture's normal.
     *
     * <p>All three are distances <em>still to go</em>: positive means that part of the ship has not
     * reached the plane yet, negative means it is through. So a passage begins when {@link #bow}
     * reaches zero and may only end when {@link #stern} has passed it.
     *
     * @param centre how far the hull's centre is from the plane
     * @param bow    how far the leading end is from the plane
     * @param stern  how far the trailing end is from the plane
     */
    public record Crossing(double centre, double bow, double stern) {

        /** True once the whole hull is past the plane, which is the only safe moment to teleport it. */
        public boolean fullyThrough() {
            return stern <= 0.0D;
        }

        /** True once any part of the hull is past the plane. */
        public boolean touching() {
            return bow <= 0.0D;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "bow=%+.2f centre=%+.2f stern=%+.2f%s",
                    bow, centre, stern, fullyThrough() ? " THROUGH" : touching() ? " CROSSING" : "");
        }
    }

    /**
     * Works out where a hull is relative to an aperture plane.
     *
     * @param alongNormal distance from the hull's centre to the plane, measured along the aperture's
     *                    normal and positive when the aperture is still ahead
     * @param hullSpan    the hull's extent along that same normal
     */
    public static Crossing crossing(double alongNormal, double hullSpan) {
        double half = Math.max(0.0D, hullSpan) * 0.5D;
        return new Crossing(alongNormal, alongNormal - half, alongNormal + half);
    }

    /** The same thing measured off a live airship and a planned aperture. */
    public static Crossing crossing(Airship airship, WarpFlight.Rift rift, double hullSpan) {
        Vector3d centre = airship.worldBounds().center(new Vector3d());
        double alongNormal = rift.centre().sub(centre, new Vector3d()).dot(rift.normal());
        return crossing(alongNormal, hullSpan);
    }

    // ----------------------------------------------------------------- output

    public static boolean enabled() {
        return AWConfig.TRACE_WARPS.get();
    }

    /** The whole journey, as planned, before any of it happens. */
    public static void plan(BlockPos drivePos, Airship airship, WarpFlight flight, Vector3dc bow) {
        if (!enabled()) {
            return;
        }
        Vector3d hull = airship.worldBounds().size(new Vector3d());
        AeroWarptics.LOGGER.info(
                "[warp] plan drive={} ship={} bow={} hull={} span={} mass={}",
                pos(drivePos), airship.uuid(), vec(bow), vec(hull),
                num(flight.hullSpan()), num(airship.mass()));
        AeroWarptics.LOGGER.info(
                "[warp]   entry rift={} r={} standoff={}",
                vec(flight.entryRift().centre()), num(flight.entryRift().radius()),
                num(crossing(airship, flight.entryRift(), flight.hullSpan()).bow()));
        AeroWarptics.LOGGER.info(
                "[warp]   transit run={} ticks={} speed={}b/t",
                num(flight.transitRun()), flight.transitTicks(),
                num(flight.transitRun() / Math.max(1, flight.transitTicks())));
        AeroWarptics.LOGGER.info(
                "[warp]   throat entry={} exit={} corridor ticks={} (flown inside the entry throat)",
                num(flight.entryThroatDepth()), num(flight.exitThroatDepth()), flight.corridorTicks());
        AeroWarptics.LOGGER.info(
                "[warp]   exit rift={} r={} emerge from={} to={} ticks={}",
                vec(flight.exitRift().centre()), num(flight.exitRift().radius()),
                vec(flight.emergenceOrigin()), vec(flight.arrivalOrigin()), flight.emergeTicks());
    }

    /**
     * One stage handing over to the next.
     *
     * <p>The crossing is the interesting part. At the start of a passage the bow should read about
     * zero; at the end of one the stern should already be negative, or the hull was teleported while
     * some of it was still in plain sight.
     */
    public static void stage(WarpFlight.Stage from, WarpFlight.Stage to, int ticks,
                             Airship airship, WarpFlight flight) {
        if (!enabled()) {
            return;
        }
        Vector3d centre = airship.worldBounds().center(new Vector3d());

        // Which aperture the hull is actually inside at this moment: the entry one right up to the
        // teleport, the far one after it. Measuring against the wrong one prints a large number that
        // means nothing.
        WarpFlight.Rift rift = from == WarpFlight.Stage.BREACH ? flight.exitRift() : flight.entryRift();
        AeroWarptics.LOGGER.info("[warp] {} -> {} after {}t  centre={} {}",
                from, to, ticks, vec(centre), crossing(airship, rift, flight.hullSpan()));
    }

    /** A hull actually being moved, which happens exactly twice and should never be visible. */
    public static void teleport(String what, Vector3dc from, Vector3dc to, int crew) {
        if (!enabled()) {
            return;
        }
        AeroWarptics.LOGGER.info("[warp] teleport {} {} -> {} ({} blocks) crew={}",
                what, vec(from), vec(to), num(new Vector3d(to).distance(from)), crew);
    }

    /** A warp that ended early, and where it was when it did. */
    public static void abort(BlockPos drivePos, WarpFailure reason, WarpFlight flight, int ticks) {
        if (!enabled()) {
            return;
        }
        AeroWarptics.LOGGER.info("[warp] ABORT {} at {} stage={} after {}t",
                reason, pos(drivePos), flight == null ? "none" : flight.stage(), ticks);
    }

    /**
     * A warp that ended because its drive was read back off disk in the middle of it.
     *
     * <p>A warning, and deliberately not gated behind the trace setting like everything else here.
     * This path used to be completely silent: it set the drive to its error state, dropped the flight
     * and returned, so a warp that died this way was indistinguishable in the log from one that simply
     * stopped happening. That cost three rounds of chasing the wrong bug, because the passenger
     * recovery it skipped was exactly what was being investigated.
     */
    public static void interrupted(BlockPos drivePos, String stage, int ticks) {
        AeroWarptics.LOGGER.warn("[warp] INTERRUPTED at {} stage={} after {}t - the drive was reloaded "
                + "mid-flight, so the sequence was dropped", pos(drivePos), stage, ticks);
    }

    /** A warp that finished. */
    public static void complete(BlockPos drivePos, Airship airship, Vector3dc intended) {
        if (!enabled()) {
            return;
        }
        Vector3dc actual = airship.position();
        AeroWarptics.LOGGER.info("[warp] done drive={} settled={} intended={} off by {}",
                pos(drivePos), vec(actual), vec(intended),
                num(new Vector3d(actual).distance(intended)));
    }

    /**
     * The drive's unstable exit scattered the hull from its intended target.
     *
     * <p>Gated behind the trace setting like every other entry here: scatter is an expected
     * behaviour of a Singularity, not a fault, and a server admin who has not turned on tracing
     * does not want every unstable warp filling the log.
     */
    public static void scatter(BlockPos drivePos, BlockPos intended, BlockPos actual, double distance) {
        if (!enabled()) {
            return;
        }
        double offset = new Vector3d(actual.getX() - intended.getX(), 0,
                actual.getZ() - intended.getZ()).length();
        AeroWarptics.LOGGER.info(
                "[warp] SCATTER drive={} offset={} blocks of {} total (intended={} scattered={})",
                pos(drivePos), num(offset), num(distance), pos(intended), pos(actual));
    }

    // ---------------------------------------------------------------- format

    private static String vec(Vector3dc v) {
        return String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", v.x(), v.y(), v.z());
    }

    private static String pos(BlockPos pos) {
        return String.format(Locale.ROOT, "(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ());
    }

    private static String num(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
