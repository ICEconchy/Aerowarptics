package uk.co.iceconchy.aerowarptics;

import net.minecraft.nbt.CompoundTag;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.FleetPassage;
import uk.co.iceconchy.aerowarptics.warp.FlightGeometry;
import uk.co.iceconchy.aerowarptics.warp.WarpFlight;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A flotilla going through one rift: that the rift is big enough to hide every ship, and that the run every
 * ship flies takes the whole formation in and brings the whole of it out.
 *
 * <p>Each of these fails quietly. An aperture a few blocks short leaves one escort's hull flying visibly
 * through the side of a rift; a passage a few blocks short leaves the back of the formation hanging in the
 * air outside the throat for the length of the corridor, or half-way through the far rift when it closes.
 * Nothing throws, and a test flight with one escort abreast of the flagship would never show either.
 */
class FleetPassageTest {

    private static final double EPSILON = 1.0e-6D;
    private static final double LEAD = 24.0D;
    private static final double TOLERANCE = 6.0D;
    private static final int TRANSIT_TICKS = 60;
    private static final int CORRIDOR_TICKS = 80;
    private static final int EMERGE_TICKS = 35;
    private static final double EMERGE_FACTOR = 1.25D;
    private static final double[] HEADINGS = {0, 30, 90, 135, 211, 300};

    /** One ship of a formation, in the flagship's frame: to starboard, up, ahead; its length, its aperture. */
    private record Slot(double right, double up, double ahead, double span, double riftRadius) {
    }

    private static final List<List<Slot>> FORMATIONS = List.of(
            // Alone: nothing to share, and the passage should be the ship's own.
            List.of(new Slot(0, 0, 0, 40, 14)),
            // Line abreast, the shape a fleet is most often flown in, and the one a circle suits worst.
            List.of(new Slot(0, 0, 0, 40, 14), new Slot(-60, 0, 0, 40, 14), new Slot(60, 0, 0, 40, 14)),
            // A column: every ship behind the one before, which is what the rigid run exists for.
            List.of(new Slot(0, 0, 0, 50, 16), new Slot(0, 0, -70, 40, 12), new Slot(0, 0, -140, 60, 18)),
            // Echelon to starboard.
            List.of(new Slot(0, 0, 0, 40, 14), new Slot(40, 0, -40, 30, 10), new Slot(80, 0, -80, 30, 10),
                    new Slot(120, 0, -120, 30, 10)),
            // Stacked: above and below.
            List.of(new Slot(0, 0, 0, 40, 14), new Slot(0, 40, 10, 30, 11), new Slot(0, -40, -10, 30, 11)),
            // A big flagship with small escorts round it, ahead of it and astern.
            List.of(new Slot(0, 0, 0, 200, 60), new Slot(-90, 10, 60, 30, 9), new Slot(90, -10, 60, 30, 9),
                    new Slot(0, 30, -150, 40, 12)));

    private static Vector3d heading(double degrees) {
        double a = Math.toRadians(degrees);
        return new Vector3d(Math.cos(a), 0.0D, Math.sin(a));
    }

    private static Vector3d across(Vector3d heading) {
        return new Vector3d(-heading.z, 0.0D, heading.x);
    }

    /** Where a slot is in the world, with the flagship's centre at {@code flagship}. */
    private static Vector3d place(Vector3d flagship, Vector3d heading, Slot slot) {
        return new Vector3d(flagship)
                .add(across(heading).mul(slot.right()))
                .add(0.0D, slot.up(), 0.0D)
                .add(new Vector3d(heading).mul(slot.ahead()));
    }

    private static List<FleetPassage.Hull> hulls(Vector3d flagship, Vector3d heading, List<Slot> formation,
                                                 double jitter) {
        List<FleetPassage.Hull> out = new ArrayList<>();
        for (int i = 0; i < formation.size(); i++) {
            Slot slot = formation.get(i);
            Vector3d centre = place(flagship, heading, slot);
            if (i > 0 && jitter > 0.0D) {
                // Somewhere in its station, never the flagship: a different corner for each escort, and no
                // further than the tolerance from its slot.
                double reach = jitter / Math.sqrt(3.0D);
                centre.add((i % 2 == 0 ? reach : -reach), ((i / 2) % 2 == 0 ? -reach : reach),
                        (i % 3 == 0 ? reach : -reach));
            }
            out.add(new FleetPassage.Hull(centre, slot.span(), slot.riftRadius()));
        }
        return out;
    }

    private static FleetPassage passage(Vector3d heading, List<FleetPassage.Hull> departing) {
        return FleetPassage.of(heading, departing, LEAD, TOLERANCE, TRANSIT_TICKS, CORRIDOR_TICKS, EMERGE_TICKS,
                EMERGE_FACTOR);
    }

    /** Every point round a ship's own aperture, stood in the plane of the fleet's. */
    private static void assertHidden(WarpFlight.Rift fleet, Vector3d heading, FleetPassage.Hull hull, String at) {
        double plane = fleet.centre().dot(heading);
        Vector3d mid = new Vector3d(hull.centre()).add(new Vector3d(heading).mul(plane - hull.centre().dot(heading)));
        Vector3d across = across(heading);
        for (int k = 0; k < 360; k++) {
            double angle = Math.toRadians(k);
            Vector3d point = new Vector3d(mid)
                    .add(new Vector3d(across).mul(Math.cos(angle) * hull.riftRadius()))
                    .add(0.0D, Math.sin(angle) * hull.riftRadius(), 0.0D);
            assertTrue(FleetPassage.inside(fleet, point), at + ": a ship's own aperture pokes out at " + k + " degrees");
        }
    }

    @Test
    void everyShipsOwnApertureFitsInsideTheFleetsAtBothEnds() {
        Vector3d flagship = new Vector3d(1_000.0D, 120.0D, -300.0D);
        Vector3d arrival = new Vector3d(-4_000.0D, 90.0D, 7_000.0D);
        for (int f = 0; f < FORMATIONS.size(); f++) {
            List<Slot> formation = FORMATIONS.get(f);
            for (double degrees : HEADINGS) {
                String at = "formation " + f + " at " + degrees + " degrees";
                Vector3d heading = heading(degrees);
                List<FleetPassage.Hull> departing = hulls(flagship, heading, formation, TOLERANCE);
                FleetPassage passage = passage(heading, departing);
                for (FleetPassage.Hull hull : departing) {
                    assertHidden(passage.entry(), heading, hull, "entry, " + at);
                }
                List<FleetPassage.Hull> resting = hulls(arrival, heading, formation, 0.0D);
                FleetPassage finished = passage.arriving(resting, 1_000L);
                for (FleetPassage.Hull hull : resting) {
                    assertHidden(finished.exit(), heading, hull, "exit, " + at);
                }
            }
        }
    }

    @Test
    void theFleetsRiftIsAsBigAsItHasToBeAndNoBigger() {
        Vector3d heading = heading(0);
        Vector3d flagship = new Vector3d(0.0D, 100.0D, 0.0D);

        FleetPassage alone = passage(heading, hulls(flagship, heading, FORMATIONS.get(0), 0.0D));
        assertEquals(1.0D, alone.entry().aspect(), 0.05D, "one ship's rift is round");
        assertTrue(alone.entry().radius() <= 14.0D * 1.01D + 1.5D, "and no bigger than its own: "
                + alone.entry().radius());

        // Three ships abreast, sixty apart. A circle round them reaches 74 blocks up and 74 down - into the
        // ground under any fleet flying below that height.
        FleetPassage abreast = passage(heading, hulls(flagship, heading, FORMATIONS.get(1), 0.0D));
        double halfHeight = abreast.entry().radius() * abreast.entry().aspect();
        assertTrue(abreast.entry().aspect() < 0.5D, "wider than tall: " + abreast.entry().aspect());
        assertTrue(halfHeight < 30.0D, "not much taller than the ships it hides: " + halfHeight);
        assertTrue(abreast.entry().radius() < 100.0D, "nor much wider: " + abreast.entry().radius());
    }

    @Test
    void nobodyGoesInBeforeThePassageAndEverybodyIsInsideAfterIt() {
        Vector3d flagship = new Vector3d(1_000.0D, 120.0D, -300.0D);
        for (int f = 0; f < FORMATIONS.size(); f++) {
            for (double degrees : HEADINGS) {
                String at = "formation " + f + " at " + degrees + " degrees";
                Vector3d heading = heading(degrees);
                List<FleetPassage.Hull> departing = hulls(flagship, heading, FORMATIONS.get(f), TOLERANCE);
                FleetPassage passage = passage(heading, departing);
                double plane = passage.entry().centre().dot(heading);
                assertEquals(FleetPassage.entryPlane(heading, departing, LEAD), plane, EPSILON, at);
                double foremost = Double.NEGATIVE_INFINITY;
                for (FleetPassage.Hull hull : departing) {
                    // Every ship runs the same distance, then the same passage, together.
                    double centre = hull.centre().dot(heading) + passage.approach();
                    foremost = Math.max(foremost, centre + hull.span() * 0.5D);
                    double stern = centre + passage.transitRun() - hull.span() * 0.5D;
                    assertTrue(stern >= plane + FlightGeometry.TRANSIT_MARGIN - EPSILON,
                            at + ": a hull is left sticking out of the entry by " + (plane - stern));
                }
                assertEquals(plane, foremost, EPSILON, at + ": the foremost bow meets the plane as the passage starts");
            }
        }
    }

    @Test
    void theFormationReappearsBehindTheExitAndIsClearOfItAfterwards() {
        Vector3d flagship = new Vector3d(1_000.0D, 120.0D, -300.0D);
        Vector3d arrival = new Vector3d(-4_000.0D, 90.0D, 7_000.0D);
        for (int f = 0; f < FORMATIONS.size(); f++) {
            for (double degrees : HEADINGS) {
                String at = "formation " + f + " at " + degrees + " degrees";
                Vector3d heading = heading(degrees);
                // Went in wherever each escort was in its station; comes out exactly in its slot.
                FleetPassage passage = passage(heading, hulls(flagship, heading, FORMATIONS.get(f), TOLERANCE));
                List<FleetPassage.Hull> resting = hulls(arrival, heading, FORMATIONS.get(f), 0.0D);
                FleetPassage finished = passage.arriving(resting, 1_000L);
                double plane = finished.exit().centre().dot(heading);
                assertEquals(finished.exitPlane(resting), plane, EPSILON, at);
                for (FleetPassage.Hull hull : resting) {
                    double rest = hull.centre().dot(heading);
                    double reappears = rest - finished.emergeDistance();
                    assertTrue(reappears + hull.span() * 0.5D <= plane - FlightGeometry.TRANSIT_MARGIN * 0.5D + EPSILON,
                            at + ": a hull reappears already through the exit");
                    double breached = reappears + finished.transitRun() - hull.span() * 0.5D;
                    assertTrue(breached >= plane + FlightGeometry.TRANSIT_MARGIN * 0.5D - EPSILON,
                            at + ": a hull is still in the exit when the passage ends");
                    assertTrue(rest - hull.span() * 0.5D >= plane,
                            at + ": a hull comes to rest with its stern in the exit");
                }
            }
        }
    }

    @Test
    void aPassageForOneShipIsThatShipsOwnRun() {
        Quaterniond orientation = new Quaterniond().rotateY(Math.toRadians(35.0D));
        Vector3d heading = orientation.transform(new Vector3d(0.0D, 0.0D, -1.0D));
        FlightGeometry own = FlightGeometry.of(20, 14, 90, orientation, heading, 1.35D, TRANSIT_TICKS,
                EMERGE_TICKS, EMERGE_FACTOR);
        FleetPassage passage = FleetPassage.of(heading, List.of(new FleetPassage.Hull(new Vector3d(5, 80, 5),
                own.hullSpan(), own.riftRadius())), LEAD, 0.0D, TRANSIT_TICKS, CORRIDOR_TICKS, EMERGE_TICKS,
                EMERGE_FACTOR);
        assertEquals(own.transitRun(), passage.transitRun(), EPSILON);
        assertEquals(own.transitTicks(), passage.transitTicks());
        assertEquals(own.emergeDistance(), passage.emergeDistance(), EPSILON);
        assertEquals(own.passageSpeed(), passage.passageSpeed(), EPSILON);
    }

    @Test
    void everyShipFliesTheFleetsRunWithItsOwnAperture() {
        Vector3d heading = heading(90);
        FleetPassage passage = passage(heading, hulls(new Vector3d(0, 100, 0), heading, FORMATIONS.get(2), 0.0D));
        Quaterniond level = new Quaterniond();
        for (double[] size : new double[][]{{12, 9, 30}, {60, 40, 200}}) {
            FlightGeometry own = FlightGeometry.of(size[0], size[1], size[2], level, heading, 1.35D, TRANSIT_TICKS,
                    EMERGE_TICKS, EMERGE_FACTOR);
            FlightGeometry flown = passage.flownBy(own);
            assertEquals(passage.transitRun(), flown.transitRun(), EPSILON, "the same passage for everybody");
            assertEquals(passage.transitTicks(), flown.transitTicks());
            assertEquals(passage.emergeDistance(), flown.emergeDistance(), EPSILON, "and the same run out");
            assertEquals(own.hullSpan(), flown.hullSpan(), EPSILON, "but its own length");
            assertEquals(own.riftRadius(), flown.riftRadius(), EPSILON, "and its own aperture, for its margins");
            assertEquals(own.apertureMargin(), flown.apertureMargin(), EPSILON);
        }
    }

    @Test
    void aPassageSurvivesASave() {
        Vector3d heading = heading(211);
        FleetPassage passage = passage(heading, hulls(new Vector3d(0, 100, 0), heading, FORMATIONS.get(3), TOLERANCE))
                .arriving(hulls(new Vector3d(900, 70, -900), heading, FORMATIONS.get(3), 0.0D), 123_456L);
        FleetPassage loaded = FleetPassage.load(passage.save());
        assertNotNull(loaded);
        assertEquals(passage.heading().x, loaded.heading().x, EPSILON);
        assertEquals(passage.heading().z, loaded.heading().z, EPSILON);
        assertEquals(passage.span(), loaded.span(), EPSILON);
        assertEquals(passage.transitRun(), loaded.transitRun(), EPSILON);
        assertEquals(passage.transitTicks(), loaded.transitTicks());
        assertEquals(passage.corridorTicks(), loaded.corridorTicks());
        assertEquals(passage.emergeTicks(), loaded.emergeTicks());
        assertEquals(passage.emergeDistance(), loaded.emergeDistance(), EPSILON);
        assertEquals(passage.approach(), loaded.approach(), EPSILON);
        assertEquals(passage.startTick(), loaded.startTick());
        for (WarpFlight.Rift[] pair : new WarpFlight.Rift[][]{{passage.entry(), loaded.entry()},
                {passage.exit(), loaded.exit()}}) {
            assertEquals(pair[0].centre().distance(pair[1].centre()), 0.0D, EPSILON);
            assertEquals(pair[0].radius(), pair[1].radius(), EPSILON);
            assertEquals(pair[0].aspect(), pair[1].aspect(), EPSILON, "an ellipse stays an ellipse");
        }
        assertNull(FleetPassage.load(new CompoundTag()), "nothing saved is no passage");
        assertNull(FleetPassage.load(null));
    }

    @Test
    void aRoundRiftSavesAsItAlwaysDid() {
        WarpFlight.Rift round = new WarpFlight.Rift(new Vector3d(1, 2, 3), new Vector3d(0, 0, 1), 9.0D);
        CompoundTag tag = round.save();
        assertFalse(tag.contains("a"), "a drive's own rift writes nothing new");
        assertEquals(1.0D, WarpFlight.Rift.load(tag).aspect(), EPSILON);
    }
}
