package uk.co.iceconchy.aerowarptics.warp;

import net.minecraft.core.BlockPos;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.trace.Trace;

import java.util.Locale;

/**
 * A running account of what a warp actually did, for when one looks wrong.
 *
 * <p>Screenshots answer "what did it look like"; they are a poor way to answer "did the stern clear
 * the plane before the teleport fired". This writes the numbers behind each stage so that question has
 * an answer rather than an impression.
 *
 * <p>Off unless {@code debug.trace} is set, and every entry point returns immediately when it is
 * off, before formatting anything. When it is on it logs at INFO, so it lands in {@code latest.log}
 * without anyone having to reconfigure logging to collect it. This is the warp's share of the mod-wide
 * trace - see {@link Trace} - kept apart because of how much of it is arithmetic worth testing.
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
        return Trace.enabled();
    }

    /**
     * What a jump costs and why, term by term, against what the drive can reach at all.
     *
     * <p>The line that answers "it is in range and it still will not go". A refusal for range names
     * {@link WarpFailure#DESTINATION_TOO_FAR} both when the anchor is past the tier's range and when the
     * jump costs more than a full charge, and only the second is fixed by a lighter ship; this says which,
     * with the hull's mass shown doing it.
     */
    public static String pricing(WarpCost.Formula formula, double distance, double mass) {
        double unclamped = formula.unclampedCost(distance, mass);
        double reach = formula.reach(mass);
        return String.format(Locale.ROOT,
                "distance=%s mass=%s cost=(base %.3f + distance %.3f + mass %.3f) / efficiency %.2f = %.3f"
                        + " of a full charge%s; range %s-%s; reach at this mass %s",
                Trace.blocks(distance), Trace.blocks(mass),
                formula.baseCost(), formula.distanceTerm(distance), formula.massTerm(mass),
                formula.efficiency(), unclamped, unclamped > 1.0D ? " (OVER A FULL CHARGE)" : "",
                Trace.blocks(formula.minimumDistance()), Trace.blocks(formula.maximumDistance()),
                reach < 0.0D ? "NONE" : Trace.blocks(reach));
    }

    /**
     * A drive refusing to start: a warp, a cruise, or its part in a fold.
     *
     * <p>Every one of these used to be silent in the log, and a redstone start's are silent to the pilot
     * too - the only record was the drive's own last-failure field. The state is the drive's whole
     * situation at that moment, see {@code RiftDriveBlockEntity.traceState}.
     */
    public static void refused(BlockPos drivePos, WarpFailure reason, String state) {
        if (!enabled()) {
            return;
        }
        AeroWarptics.LOGGER.info("[drive] {} REFUSED {} - {}", pos(drivePos), reason.getSerializedName(), state);
    }

    /** A drive changing state, with what it is running on. */
    public static void state(BlockPos drivePos, String from, String to, float charge, double rpm, int sequenceTicks) {
        if (!enabled()) {
            return;
        }
        AeroWarptics.LOGGER.info("[drive] {} {} -> {} charge={} rpm={} after {}t", pos(drivePos), from, to,
                Trace.percent(charge), num(rpm), sequenceTicks);
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
     * A flight going through its fleet's one rift: the run every ship in it shares, and the two apertures.
     * Logged by every ship, so a trace from any of them shows whether they were all given the same passage.
     */
    public static void passage(BlockPos drivePos, FleetPassage passage, int wait) {
        if (!enabled()) {
            return;
        }
        AeroWarptics.LOGGER.info(
                "[warp]   fleet passage drive={} span={} run={} approach={} wait={} start={} corridor={} emerge={}/{}",
                pos(drivePos), num(passage.span()), num(passage.transitRun()), num(passage.approach()), wait,
                passage.startTick(), passage.corridorTicks(), num(passage.emergeDistance()), passage.emergeTicks());
        WarpFlight.Rift entry = passage.entry();
        WarpFlight.Rift exit = passage.exit();
        AeroWarptics.LOGGER.info("[warp]   fleet entry={} {}x{} exit={} {}",
                vec(entry.centre()), num(entry.radius() * 2.0D), num(entry.radius() * entry.aspect() * 2.0D),
                exit == null ? "-" : vec(exit.centre()),
                exit == null ? "" : num(exit.radius() * 2.0D) + "x" + num(exit.radius() * exit.aspect() * 2.0D));
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

    /**
     * Somebody switched a drive's clearance checks on or off at its console.
     *
     * <p>Not gated behind the trace setting, unlike everything else here. It is a rare and deliberate
     * act, and when a ship is later found set down inside a hillside the first question an admin has is
     * whether the drive was told not to look - which the log should be able to answer without anybody
     * having thought to turn tracing on beforehand.
     */
    public static void clearanceChecks(BlockPos drivePos, String player, boolean on) {
        AeroWarptics.LOGGER.info("[warp] clearance checks {} drive={} by {}", on ? "ON" : "OFF",
                pos(drivePos), player);
    }

    /** A warp opening its rift with this drive's clearance checks switched off. */
    public static void uncheckedLaunch(BlockPos drivePos, BlockPos target) {
        if (!enabled()) {
            return;
        }
        AeroWarptics.LOGGER.info("[warp] launching with clearance checks OFF drive={} target={}",
                pos(drivePos), pos(target));
    }

    /** A free cruise setting off: which way, how fast it may go, and how far its charge will carry it. */
    public static void cruiseEngaged(BlockPos drivePos, Airship airship, Vector3dc bow, double topSpeed,
                                     double throttle, double limit) {
        if (!enabled()) {
            return;
        }
        AeroWarptics.LOGGER.info("[warp] cruise engaged drive={} ship={} heading={} top={}b/s throttle={} limit={}",
                pos(drivePos), airship.uuid(), vec(bow), num(topSpeed), num(throttle), num(limit));
    }

    /** A cruise starting to let go, and why - asked, throttle cut, stalled, out of charge or out of range. */
    public static void cruiseDropOut(BlockPos drivePos, String why, double flown, double speed, float charge) {
        if (!enabled()) {
            return;
        }
        AeroWarptics.LOGGER.info("[warp] cruise dropping out drive={} ({}) flown={} speed={}b/t charge={}",
                pos(drivePos), why, num(flown), num(speed), num(charge));
    }

    /** A cruise brought to a stop and handed back. */
    public static void cruiseComplete(BlockPos drivePos, double flown, float charge) {
        if (!enabled()) {
            return;
        }
        AeroWarptics.LOGGER.info("[warp] cruise complete drive={} flown={} charge left={}",
                pos(drivePos), num(flown), num(charge));
    }

    /** A hull actually being moved, which happens exactly twice and should never be visible. */
    public static void teleport(String what, Vector3dc from, Vector3dc to, int crew) {
        if (!enabled()) {
            return;
        }
        AeroWarptics.LOGGER.info("[warp] teleport {} {} -> {} ({} blocks) crew={}",
                what, vec(from), vec(to), num(new Vector3d(to).distance(from)), crew);
    }

    /**
     * A vessel moved into another dimension by Dimensional Sable.
     *
     * <p>Logged whether tracing is on or not, like {@link #interrupted}: a crossing rebuilds the whole
     * vessel in one tick, and is new, so its cost is always on record.
     *
     * @param parts    sub-levels moved, the hull and everything joined to it
     * @param bearings running bearings to start again on arrival
     */
    public static void crossDimension(java.util.UUID ship, String from, String to, int parts, int bearings,
                                      long micros) {
        AeroWarptics.LOGGER.info("[warp] crossed {} {} -> {} parts={} bearings={} in {}ms",
                ship, from, to, parts, bearings, num(micros / 1_000.0D));
    }

    /**
     * A new drive picking up a flight its old self handed across a dimension.
     *
     * @param moved how far the drive's plot position moved, which every plot-space memory moves by too
     */
    public static void handoff(BlockPos drivePos, String dimension, String stage, BlockPos moved,
                               java.util.UUID from, java.util.UUID to) {
        AeroWarptics.LOGGER.info("[warp] handoff drive={} in {} resumes stage={} plot moved {} ship {} -> {}",
                pos(drivePos), dimension, stage, pos(moved), from, to);
    }

    /** A warp that ended early, where it was when it did, and the drive's whole situation at that moment. */
    public static void abort(BlockPos drivePos, WarpFailure reason, WarpFlight flight, int ticks, String state) {
        if (!enabled()) {
            return;
        }
        AeroWarptics.LOGGER.info("[warp] ABORT {} at {} stage={} after {}t - {}",
                reason, pos(drivePos), flight == null ? "none" : flight.stage(), ticks, state);
    }

    /**
     * How far a hull came out of a warp turned from the attitude it was planned to arrive at.
     *
     * <p>The measure of the tilt {@code AttitudeHold} exists to stop: anything over a degree or two is the
     * hull having been twisted in flight and not held.
     */
    public static void attitude(BlockPos drivePos, double degrees) {
        if (!enabled()) {
            return;
        }
        AeroWarptics.LOGGER.info("[warp] attitude drive={} off the planned arrival by {} degrees{}",
                pos(drivePos), num(degrees), degrees > 2.0D ? " TILTED" : "");
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

    /** The landing claimed at the start of spin-up, so the arrival search finds it loaded. */
    public static void preloadClaimed(BlockPos drivePos, int chunks) {
        if (!enabled()) {
            return;
        }
        AeroWarptics.LOGGER.info("[warp] preload drive={} claimed {} chunks of landing", pos(drivePos), chunks);
    }

    /**
     * A fully spun drive that had to hold for its landing, and whether the landing arrived in time.
     *
     * <p>Only written when the drive actually waited. A timeout means the search went on to load the
     * rest synchronously, which is the stall the preload exists to prevent - so if a large ship still
     * hitches at launch, this is the line that says whether that is why.
     */
    public static void preloadReady(BlockPos drivePos, int waited, boolean loaded) {
        if (!enabled()) {
            return;
        }
        AeroWarptics.LOGGER.info("[warp] preload drive={} {} after {}t", pos(drivePos),
                loaded ? "ready" : "TIMED OUT - loading the rest synchronously", waited);
    }

    /**
     * The arrival coast held, at launch and again at the crossing.
     *
     * <p>The line that answers remediation item 2: if a large hull still ends {@code INTERRUPTED} at
     * the breach with this line showing its reappearance point held, the ground was not the reason.
     */
    public static void arrivalHeld(BlockPos drivePos, Vector3dc reappears, Vector3dc rests, int chunks) {
        if (!enabled()) {
            return;
        }
        AeroWarptics.LOGGER.info("[warp] holding arrival drive={} from={} to={} ({} chunks)",
                pos(drivePos), vec(reappears), vec(rests), chunks);
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

    /**
     * The single clearest signature of a yeet: what the drive asked for against what the hull is
     * actually doing.
     *
     * <p>A healthy tick has the reported speed tracking the commanded one within a small margin. A
     * penetration ejection or a frame slip shows up as the reported figure diverging - the hull moving
     * far faster than anything was told to move it - and until this was traced it was invisible.
     */
    public static String velocitySummary(Vector3dc commanded, Vector3dc reported, double ceiling) {
        double asked = commanded.length();
        double got = reported.length();
        String flag = got > ceiling ? " OVER-CEILING"
                : got > asked * 2.0D + 1.0D ? " DIVERGED"
                : asked >= ceiling ? " CLAMPED" : "";
        return String.format(Locale.ROOT, "commanded=%s |c|=%s reported=%s |r|=%s ceiling=%s%s",
                vec(commanded), num(asked), vec(reported), num(got), num(ceiling), flag);
    }

    /** Every flight tick's commanded-versus-reported velocity, the yeet detector. */
    public static void flightTick(WarpFlight.Stage stage, Vector3dc commanded, Vector3dc reported,
                                  double ceiling) {
        if (!enabled()) {
            return;
        }
        AeroWarptics.LOGGER.info("[warp]   tick {} {}", stage, velocitySummary(commanded, reported, ceiling));
    }

    /**
     * How big the client-visible move at a teleport was against the jump threshold, and how many
     * players were told to expect it.
     *
     * <p>A move under the threshold is one the fold-crossed collapse would not fire on, and a notice
     * that reached nobody is a collapse that will not happen - both silent, both traced here.
     */
    public static String crossingSummary(double moved, double jumpThreshold, int recipients) {
        boolean jump = moved >= jumpThreshold;
        return String.format(Locale.ROOT, "move=%sb threshold=%sb %s notice->%d players",
                num(moved), num(jumpThreshold), jump ? "JUMP" : "under-threshold", recipients);
    }

    /** The fold-crossed notice, sized against the jump threshold and counted by recipients. */
    public static void crossingNotice(double moved, double jumpThreshold, int recipients) {
        if (!enabled()) {
            return;
        }
        AeroWarptics.LOGGER.info("[warp]   crossing {}", crossingSummary(moved, jumpThreshold, recipients));
    }

    /**
     * The loaded state of a landing at the moment a hull arrives on it.
     *
     * <p>The report that decides whether phase 3 needs a forceload, a follow ticket, or a commit-time
     * refusal: whether the chunks are loaded, whether the warp's own ticket is still holding them, and
     * whether anything else will keep them loaded once that ticket lapses.
     */
    public static String arrivalSummary(boolean loaded, boolean held, boolean durable) {
        return String.format(Locale.ROOT, "loaded=%s held=%s durable=%s%s",
                loaded, held, durable, durable ? "" : " WILL-UNLOAD");
    }

    /** The landing's residency, traced at arrival. */
    public static void arrival(BlockPos drivePos, boolean loaded, boolean held, boolean durable) {
        if (!enabled()) {
            return;
        }
        AeroWarptics.LOGGER.info("[warp] arrival at {} {}",
                pos(drivePos), arrivalSummary(loaded, held, durable));
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
