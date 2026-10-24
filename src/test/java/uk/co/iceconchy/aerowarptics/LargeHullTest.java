package uk.co.iceconchy.aerowarptics;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.ArrivalTicket;
import uk.co.iceconchy.aerowarptics.warp.FlightGeometry;
import uk.co.iceconchy.aerowarptics.warp.SafeArrival;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That nothing in a warp's geometry quietly becomes a size limit on ships.
 *
 * <p>Each of these was one: an arrival volume padded beneath the keel by a margin that grew with the
 * hull until no landing over flat ground could be proved; a chunk cap that every candidate of a long
 * hull exceeded; an arrival ticket that did not reach the place a long hull reappears; and a preload
 * that must cover everything the search is about to read, or it moves the stall rather than removing
 * it. None of them threw. The ship was refused, froze the server, or was dropped mid-warp.
 *
 * <p>Swept over hull lengths from a skiff to a thousand blocks and over headings, using the shipped
 * defaults: Mk II arrive ticks, 60 transit ticks, a rift radius factor of 1.35, two blocks of
 * clearance and a 48-block search radius.
 */
class LargeHullTest {

    private static final double CLEARANCE = 2.0D;
    private static final int SEARCH_RADIUS = 48;
    private static final int CHUNK_CAP = 4096;
    private static final double[] LENGTHS = {50, 100, 200, 400, 700, 1000};
    private static final double[] AXIS_HEADINGS = {0, 90, 180, 270};
    private static final double[] EVERY_HEADING = {0, 30, 45, 90, 135, 180, 225, 270, 333};

    /** A hull 60 wide and 60 high, as long as asked, turned to a heading and flown along its length. */
    private record Hull(FlightGeometry geometry, Quaterniond orientation, Vector3d bow, Vector3d across,
                        double width, double height, double length) {

        static Hull of(double length, double degrees) {
            Quaterniond orientation = new Quaterniond().rotateY(Math.toRadians(degrees));
            Vector3d bow = orientation.transform(new Vector3d(0, 0, -1));
            FlightGeometry geometry = FlightGeometry.of(60, 60, length, orientation, bow, 1.35D, 60, 35, 1.25D);
            return new Hull(geometry, orientation, bow, new Vector3d(-bow.z, 0, bow.x), 60, 60, length);
        }

        /** The world-aligned box of the turned hull, centred on {@code centre}. */
        BoundingBox3d box(Vector3d centre) {
            double x = FlightGeometry.extentAlong(width, height, length, orientation, new Vector3d(1, 0, 0));
            double y = FlightGeometry.extentAlong(width, height, length, orientation, new Vector3d(0, 1, 0));
            double z = FlightGeometry.extentAlong(width, height, length, orientation, new Vector3d(0, 0, 1));
            return new BoundingBox3d(centre.x - x / 2, centre.y - y / 2, centre.z - z / 2,
                    centre.x + x / 2, centre.y + y / 2, centre.z + z / 2);
        }
    }

    /**
     * The arrival volume stops at the keel, whatever the ship.
     *
     * <p>The volume used to be widened by the aperture margin underneath as well, and the margin grew
     * with the hull. A four-hundred-block hull flying east had it pushed more than sixty blocks into the
     * ground, where a search that climbs only thirty-two could never lift it out.
     */
    @Test
    void theArrivalVolumeNeverReachesBelowTheKeel() {
        for (double length : LENGTHS) {
            for (double heading : EVERY_HEADING) {
                Hull hull = Hull.of(length, heading);
                BoundingBox3d box = hull.box(new Vector3d(0.5, 120, 0.5));
                for (BoundingBox3d segment : SafeArrival.arrivalSegments(box, hull.bow(),
                        hull.geometry().emergeDistance(), CLEARANCE)) {
                    assertTrue(segment.minY() >= box.minY() - CLEARANCE - 1.0e-9D,
                            "hull " + length + " at " + heading + ": tested down to " + segment.minY()
                                    + " under a keel at " + box.minY());
                }
            }
        }
    }

    /** A thousand-block hull on an axis, and a four-hundred-block one on any heading, fits the chunk cap. */
    @Test
    void largeArrivalsFitTheChunkCap() {
        for (double length : LENGTHS) {
            for (double heading : EVERY_HEADING) {
                boolean axis = java.util.Arrays.stream(AXIS_HEADINGS).anyMatch(h -> h == heading);
                if (!axis && length > 400) {
                    continue; // a diagonal mega-hull's world box is the known remaining limit
                }
                Hull hull = Hull.of(length, heading);
                List<BoundingBox3d> volume = SafeArrival.arrivalSegments(hull.box(new Vector3d(0.5, 120, 0.5)),
                        hull.bow(), hull.geometry().emergeDistance(), CLEARANCE);
                int columns = SafeArrival.chunkColumns(volume);
                assertTrue(columns <= CHUNK_CAP,
                        "hull " + length + " at " + heading + " needs " + columns + " chunk columns");
            }
        }
    }

    /**
     * The ground under the whole hull is held where it reappears, where it rests, and along the coast
     * between - and the budget never has to give any of it up.
     *
     * <p>The reappearance point is the one that mattered. It sits the whole emerge distance behind the
     * resting place, and the old square ticket about the resting place did not reach it on any hull
     * much over a hundred blocks.
     */
    @Test
    void theGroundUnderTheHullIsHeldForTheWholeArrival() {
        for (double length : LENGTHS) {
            for (double heading : EVERY_HEADING) {
                Hull hull = Hull.of(length, heading);
                FlightGeometry g = hull.geometry();
                Vector3d rests = new Vector3d(7_301.5, 110, -2_054.25);
                Vector3d reappears = new Vector3d(hull.bow()).mul(-g.emergeDistance()).add(rests);
                List<ArrivalTicket.Square> squares = ArrivalTicket.centresAlong(reappears.x, reappears.z,
                        rests.x, rests.z, ArrivalTicket.arrivalHalfWidth(g.crossSpan(), g.apertureMargin()),
                        g.hullSpan() * 0.5D + CLEARANCE);
                List<ArrivalTicket.Square> held = ArrivalTicket.withinBudget(squares, CHUNK_CAP);
                String at = "hull " + length + " at " + heading;
                if (length <= 400 || java.util.Arrays.stream(AXIS_HEADINGS).anyMatch(h -> h == heading)) {
                    assertTrue(held.size() == squares.size(),
                            at + ": the budget trimmed the arrival to " + held.size() + " of " + squares.size());
                }
                // Wherever the hull's centre is on the coast, every column under it is held.
                for (double t : new double[]{0.0, 0.25, 0.5, 0.75, 1.0}) {
                    if (held.size() < squares.size() && t > 0.0 && t < 1.0) {
                        continue; // a trimmed claim keeps the ends, and only the ends are promised
                    }
                    Vector3d centre = new Vector3d(reappears).lerp(rests, t);
                    assertFootprintHeld(held, hull, centre, at + " at t=" + t);
                }
            }
        }
    }

    /**
     * Everything the arrival search will read was claimed while the drive spun up.
     *
     * <p>Checked at the anchor's own column and at the corners of the search - the furthest candidates
     * from the target - because a preload that misses any of it leaves the search to generate that
     * ground synchronously, which is the freeze it exists to prevent.
     */
    @Test
    void thePreloadCoversEverythingTheSearchReads() {
        for (double length : new double[]{50, 200, 400}) {
            for (double heading : EVERY_HEADING) {
                Hull hull = Hull.of(length, heading);
                FlightGeometry g = hull.geometry();
                Vector3d target = new Vector3d(-12_345.5, 80, 6_789.5);
                BoundingBox3d atTarget = hull.box(target);
                List<ArrivalTicket.Square> preload = ArrivalTicket.preloadSquares(target.x, target.z,
                        hull.bow(), g.emergeDistance(), atTarget.width(), atTarget.length(),
                        SEARCH_RADIUS, CLEARANCE, CHUNK_CAP);
                LongOpenHashSet claimed = ArrivalTicket.chunksOf(preload);
                for (int dx : new int[]{-SEARCH_RADIUS, 0, SEARCH_RADIUS}) {
                    for (int dz : new int[]{-SEARCH_RADIUS, 0, SEARCH_RADIUS}) {
                        BoundingBox3d box = hull.box(new Vector3d(target).add(dx, 0, dz));
                        for (BoundingBox3d segment : SafeArrival.arrivalSegments(box, hull.bow(),
                                g.emergeDistance(), CLEARANCE)) {
                            assertColumnsClaimed(claimed, segment,
                                    "hull " + length + " at " + heading + " candidate " + dx + "," + dz
                                            + " (" + claimed.size() + " chunks claimed)");
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private static void assertFootprintHeld(List<ArrivalTicket.Square> squares, Hull hull, Vector3d centre,
                                            String at) {
        double halfLength = hull.geometry().hullSpan() * 0.5D;
        double halfWidth = hull.geometry().crossSpan() * 0.5D;
        for (double u = -halfLength; u <= halfLength + 1.0e-9D; u += 5.3D) {
            for (double v = -halfWidth; v <= halfWidth + 1.0e-9D; v += 5.3D) {
                double x = centre.x + hull.bow().x * u + hull.across().x * v;
                double z = centre.z + hull.bow().z * u + hull.across().z * v;
                boolean inside = squares.stream().anyMatch(square -> square.containsBlock(x, z));
                assertTrue(inside, at + ": column (" + x + ", " + z + ") under the hull is unheld");
            }
        }
    }

    private static void assertColumnsClaimed(LongOpenHashSet claimed, BoundingBox3d box, String at) {
        for (int x = (int) Math.floor(box.minX()) >> 4; x <= (int) Math.floor(box.maxX()) >> 4; x++) {
            for (int z = (int) Math.floor(box.minZ()) >> 4; z <= (int) Math.floor(box.maxZ()) >> 4; z++) {
                assertTrue(claimed.contains(ArrivalTicket.pack(x, z)),
                        at + ": chunk " + x + ", " + z + " is read but was never preloaded");
            }
        }
    }
}
