package uk.co.iceconchy.aerowarptics.plotter;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Rift Plotter's two rifts and the crossing between them, held to what a player aiming a weapon is relying
 * on: the exit lies where the bearing says and faces the target, whatever crosses the entry comes out of the exit's
 * front the right way up at the speed it went in, a shell too fast for a slab is still caught, and nothing that
 * passes beside the rift is sent anywhere.
 */
class PlotGeometryTest {

    /** The exit opens for what is coming: through either face, inside the rim, towards it - and for nothing else. */
    @Test
    void somethingOnItsWayThroughIsSeenComing() {
        PlotGeometry.Frame disc = PlotGeometry.frame(new Vec3(0, 80, 0), 0, 0); // facing south, +z
        Vec3 behind = new Vec3(1, 80, -10);
        assertEquals(10.0D, PlotGeometry.ticksToCross(disc, 4.0D, behind, new Vec3(0, 0, 1)), 1.0e-9D);
        assertEquals(5.0D, PlotGeometry.ticksToCross(disc, 4.0D, new Vec3(0, 81, 10), new Vec3(0, 0, -2)), 1.0e-9D,
                "from in front, the way back through");
        assertEquals(Double.POSITIVE_INFINITY, PlotGeometry.ticksToCross(disc, 4.0D, behind, new Vec3(0, 0, -1)),
                "moving away");
        assertEquals(Double.POSITIVE_INFINITY, PlotGeometry.ticksToCross(disc, 4.0D, behind, new Vec3(1, 0, 0)),
                "along the plane");
        assertEquals(Double.POSITIVE_INFINITY, PlotGeometry.ticksToCross(disc, 4.0D, new Vec3(6, 80, -10),
                new Vec3(0, 0, 1)), "passing outside the rim");
        assertEquals(Double.POSITIVE_INFINITY, PlotGeometry.ticksToCross(disc, 4.0D, behind, Vec3.ZERO), "standing still");
        assertEquals(0.0D, PlotGeometry.ticksToCross(disc, 4.0D, new Vec3(0, 80, 0), new Vec3(0, 0, 1)), 1.0e-9D,
                "in the plane now");
    }

    private static final double EPSILON = 1.0e-9D;

    private static final double[] YAWS = {0.0D, 37.0D, 90.0D, 145.0D, 180.0D, -120.0D, -90.0D, 271.0D};
    private static final double[] PITCHES = {-90.0D, -60.0D, -12.0D, 0.0D, 30.0D, 75.0D, 90.0D};

    private static Plot plot(double distance, double bearing, double height) {
        return Plot.NONE.withTarget(1000, 70, -2000).withExit(distance, bearing, height);
    }

    // ------------------------------------------------------------------ directions and frames

    @Test
    void directionsFollowMinecraftsYawAndPitch() {
        assertClose(new Vec3(0, 0, 1), PlotGeometry.direction(0, 0), "yaw 0 faces south");
        assertClose(new Vec3(-1, 0, 0), PlotGeometry.direction(90, 0), "yaw 90 faces west");
        assertClose(new Vec3(0, 0, -1), PlotGeometry.direction(180, 0), "yaw 180 faces north");
        assertClose(new Vec3(1, 0, 0), PlotGeometry.direction(-90, 0), "yaw -90 faces east");
        assertClose(new Vec3(0, 1, 0), PlotGeometry.direction(0, -90), "pitch -90 looks up");
        assertClose(new Vec3(0, -1, 0), PlotGeometry.direction(0, 90), "pitch 90 looks down");
        for (double yaw : YAWS) {
            for (double pitch : new double[]{-60.0D, -12.0D, 0.0D, 30.0D, 75.0D}) {
                Vec3 look = PlotGeometry.direction(yaw, pitch);
                assertEquals(pitch, PlotGeometry.pitchOf(look), 1.0e-7D);
                assertEquals(0.0D, wrap(yaw - PlotGeometry.yawOf(look)), 1.0e-7D, "yaw " + yaw + " round trip");
            }
        }
    }

    /** Every frame, even one facing straight up or down, is a right-handed set of three unit axes at right angles. */
    @Test
    void everyFrameIsRightHandedAndSquare() {
        for (double yaw : YAWS) {
            for (double pitch : PITCHES) {
                PlotGeometry.Frame frame = PlotGeometry.frame(Vec3.ZERO, yaw, pitch);
                String at = "yaw " + yaw + " pitch " + pitch;
                assertEquals(1.0D, frame.normal().length(), EPSILON, at);
                assertEquals(1.0D, frame.up().length(), EPSILON, at);
                assertEquals(1.0D, frame.right().length(), EPSILON, at);
                assertEquals(0.0D, frame.normal().dot(frame.up()), EPSILON, at);
                assertEquals(0.0D, frame.normal().dot(frame.right()), EPSILON, at);
                assertEquals(0.0D, frame.up().dot(frame.right()), EPSILON, at);
                assertClose(frame.right(), frame.normal().cross(frame.up()), at + ": right is normal x up");
            }
        }
    }

    // ------------------------------------------------------------------ where the rifts are

    @Test
    void theExitLiesOnItsBearingFromTheTarget() {
        Vec3 target = new Vec3(0.5D, 65.0D, 0.5D);
        assertClose(target.add(0, 20, -50), PlotGeometry.exitCentre(target, plot(50, 0, 20)), "bearing 0 is north");
        assertClose(target.add(50, 20, 0), PlotGeometry.exitCentre(target, plot(50, 90, 20)), "bearing 90 is east");
        assertClose(target.add(0, 20, 50), PlotGeometry.exitCentre(target, plot(50, 180, 20)), "bearing 180 is south");
        assertClose(target.add(-50, 20, 0), PlotGeometry.exitCentre(target, plot(50, 270, 20)), "bearing 270 is west");
    }

    /** Aimed, the exit faces the target from wherever round it it is put - which is what a gunner is choosing. */
    @Test
    void anAimedExitFacesTheTargetFromAnywhereRoundIt() {
        Vec3 target = new Vec3(10.5D, 70.0D, -3.5D);
        for (double bearing = 0.0D; bearing < 360.0D; bearing += 22.5D) {
            for (double height : new double[]{0.0D, 12.0D, 80.0D}) {
                Plot plot = plot(64, bearing, height);
                PlotGeometry.Frame exit = PlotGeometry.exit(target, plot);
                Vec3 toTarget = target.subtract(exit.centre()).normalize();
                assertClose(toTarget, exit.normal(), "bearing " + bearing + " height " + height);
            }
        }
    }

    @Test
    void anExitRightOverItsTargetLooksStraightDownAndStillHasAnUp() {
        Vec3 target = new Vec3(0.5D, 64.0D, 0.5D);
        PlotGeometry.Frame exit = PlotGeometry.exit(target, plot(0, 45, 40));
        assertClose(new Vec3(0, -1, 0), exit.normal(), "straight down");
        assertEquals(1.0D, exit.up().length(), EPSILON);
        assertEquals(0.0D, exit.up().dot(exit.normal()), EPSILON);
    }

    @Test
    void aManualAimTurnsOffTheLineToTheTarget() {
        Vec3 target = new Vec3(0.5D, 64.0D, 0.5D);
        // North of the target: facing it is facing south, yaw 0. Turned 30 clockwise from above is yaw 30.
        Plot plot = plot(40, 0, 0).withAim(false, 30.0D, 20.0D);
        PlotGeometry.Frame exit = PlotGeometry.exit(target, plot);
        assertEquals(30.0D, wrap(PlotGeometry.yawOf(exit.normal())), 1.0e-7D);
        assertEquals(20.0D, PlotGeometry.pitchOf(exit.normal()), 1.0e-7D, "diving 20");
    }

    @Test
    void theEntryIsThrownOutFromTheLensAlongTheWayItFaces() {
        BlockPos master = new BlockPos(-300, 70, 42);
        for (double yaw : YAWS) {
            for (double pitch : PITCHES) {
                Plot plot = Plot.NONE.withInput(yaw, pitch, 18.0D, 5.0D);
                PlotGeometry.Frame entry = PlotGeometry.input(master, plot);
                Vec3 lens = PlotGeometry.lens(master);
                assertEquals(18.0D, entry.centre().distanceTo(lens), 1.0e-7D);
                assertClose(entry.normal(), entry.centre().subtract(lens).normalize(), "yaw " + yaw + " pitch " + pitch);
            }
        }
    }

    /**
     * Turned, the entry stays exactly where it was thrown and faces the way it was told - every way round, at every
     * tip - and is still a frame the exit's can be reached from by a rotation.
     */
    @Test
    void aTurnedEntryStaysWhereItWasThrownAndFacesWhereItIsTold() {
        BlockPos master = new BlockPos(-300, 70, 42);
        Plot thrown = Plot.NONE.withInput(37.0D, -12.0D, 18.0D, 5.0D);
        PlotGeometry.Frame outward = PlotGeometry.input(master, thrown);
        PlotGeometry.Frame exit = PlotGeometry.frame(new Vec3(5000, 120, -3000), 135, 25);
        for (double yaw : YAWS) {
            for (double pitch : PITCHES) {
                String at = "turned to yaw " + yaw + " pitch " + pitch;
                PlotGeometry.Frame entry = PlotGeometry.input(master, thrown.withFacing(true, yaw, pitch));
                assertClose(outward.centre(), entry.centre(), at + ": turning it moved it");
                assertClose(PlotGeometry.direction(yaw, pitch), entry.normal(), at);
                assertClose(entry.right(), entry.normal().cross(entry.up()), at + ": right is normal x up");
                Quaterniond turn = PlotGeometry.turn(entry, exit);
                assertEquals(1.0D, new Matrix3d().rotation(turn).determinant(), 1.0e-9D, at + ": no mirror");
                assertClose(exit.up(), PlotGeometry.rotate(turn, entry.up()), at + ": up onto up");
            }
        }
        // The dish still points at where the entry is, not the way it faces.
        Plot turned = thrown.withFacing(true, -120.0D, 30.0D);
        assertClose(PlotGeometry.focus(master, thrown), PlotGeometry.focus(master, turned), "the ball moved");
    }

    /**
     * A level hull flown level through a turned entry comes out of a level exit level: whichever way the entry is
     * turned about the upright, its up is the world's, so nothing is rolled on the way through.
     */
    @Test
    void aLevelHullGoesThroughATurnedEntryTheRightWayUp() {
        BlockPos master = new BlockPos(0, 64, 0);
        Plot thrown = Plot.NONE.withInput(0.0D, -30.0D, 24.0D, 6.0D); // up and out to the south
        PlotGeometry.Frame exit = PlotGeometry.frame(new Vec3(900, 140, 900), 45, 0);
        for (double yaw : YAWS) {
            PlotGeometry.Frame entry = PlotGeometry.input(master, thrown.withFacing(true, yaw, 0.0D));
            assertClose(new Vec3(0, 1, 0), entry.up(), "yaw " + yaw + ": a level entry's up is not the world's");
            Vec3 worldUp = PlotGeometry.rotate(PlotGeometry.turn(entry, exit), new Vec3(0, 1, 0));
            assertClose(new Vec3(0, 1, 0), worldUp, "yaw " + yaw + ": a hull's up came out leaning");
        }
    }

    // ------------------------------------------------------------------ crossing

    @Test
    void aLineThroughTheDiscCrossesItAndOneBesideItDoesNot() {
        PlotGeometry.Frame entry = PlotGeometry.frame(new Vec3(0, 80, 0), 0, 0);
        PlotGeometry.Crossing through = PlotGeometry.crossing(entry, 4.0D, new Vec3(1, 81, -2), new Vec3(1, 81, 3));
        assertNotNull(through);
        assertTrue(through.forward(), "south through a south-facing disc is forwards");
        assertEquals(3.0D, through.past(), EPSILON);
        assertClose(new Vec3(1, 81, 0), through.point(), "where it met the plane");

        assertNull(PlotGeometry.crossing(entry, 4.0D, new Vec3(5, 80, -2), new Vec3(5, 80, 3)), "beside the rim");
        assertNull(PlotGeometry.crossing(entry, 4.0D, new Vec3(0, 80, 1), new Vec3(0, 80, 3)), "stayed in front");
        assertNull(PlotGeometry.crossing(entry, 4.0D, new Vec3(0, 80, -3), new Vec3(0, 80, -1)), "stayed behind");

        PlotGeometry.Crossing back = PlotGeometry.crossing(entry, 4.0D, new Vec3(0, 80, 2), new Vec3(0, 80, -1));
        assertNotNull(back);
        assertTrue(!back.forward(), "north through it is backwards");
    }

    /** What the trace is told about a line the crossing turned away: how far out it met the plane, either way through. */
    @Test
    void aLineThatMeetsThePlaneSaysHowFarFromTheMiddle() {
        PlotGeometry.Frame entry = PlotGeometry.frame(new Vec3(0, 80, 0), 0, 0);
        assertEquals(5.0D, PlotGeometry.metPlaneAt(entry, new Vec3(3, 84, -2), new Vec3(3, 84, 3)), EPSILON);
        assertEquals(5.0D, PlotGeometry.metPlaneAt(entry, new Vec3(3, 84, 3), new Vec3(3, 84, -2)), EPSILON);
        assertTrue(Double.isNaN(PlotGeometry.metPlaneAt(entry, new Vec3(0, 80, 1), new Vec3(0, 80, 3))), "stayed in front");
        // The same line the crossing answers for: inside the rim one says so, and outside it the other.
        assertNotNull(PlotGeometry.crossing(entry, 5.5D, new Vec3(3, 84, -2), new Vec3(3, 84, 3)));
        assertNull(PlotGeometry.crossing(entry, 4.5D, new Vec3(3, 84, -2), new Vec3(3, 84, 3)));
    }

    /** A shell covering thirty blocks a tick is seen once on each side and never in the plane - and still caught. */
    @Test
    void aShellTooFastForASlabIsStillCaught() {
        PlotGeometry.Frame entry = PlotGeometry.frame(new Vec3(0, 80, 0), -90, 0);
        PlotGeometry.Crossing crossing = PlotGeometry.crossing(entry, 3.0D, new Vec3(-12, 80.5, 0.5), new Vec3(18, 80.5, 0.5));
        assertNotNull(crossing);
        assertEquals(18.0D, crossing.past(), EPSILON);
    }

    // ------------------------------------------------------------------ carrying

    /** The rotation between the rifts takes the entry's axes onto the exit's, and is a proper rotation. */
    @Test
    void theTurnBetweenTheRiftsIsARotationTakingOneFrameOntoTheOther() {
        for (double yaw : YAWS) {
            for (double pitch : PITCHES) {
                PlotGeometry.Frame from = PlotGeometry.frame(Vec3.ZERO, yaw, pitch);
                PlotGeometry.Frame to = PlotGeometry.frame(Vec3.ZERO, 180.0D - yaw * 0.5D, -pitch * 0.3D + 10.0D);
                Quaterniond turn = PlotGeometry.turn(from, to);
                assertEquals(1.0D, turn.lengthSquared(), 1.0e-9D);
                assertClose(to.normal(), PlotGeometry.rotate(turn, from.normal()), "normal onto normal");
                assertClose(to.up(), PlotGeometry.rotate(turn, from.up()), "up onto up");
                assertClose(to.right(), PlotGeometry.rotate(turn, from.right()), "right onto right");
                assertEquals(1.0D, new Matrix3d().rotation(turn).determinant(), 1.0e-9D, "no mirror");
            }
        }
    }

    /**
     * Something going through the entry comes out of the exit at the same place on the disc, just past it, heading
     * out of its front at the speed it went in.
     */
    @Test
    void whatGoesInComesOutOfTheExitsFrontAtTheSameSpeed() {
        PlotGeometry.Frame entry = PlotGeometry.frame(new Vec3(100, 70, 100), 0, 0);
        PlotGeometry.Frame exit = PlotGeometry.frame(new Vec3(5000, 120, -3000), 135, 25);
        Vec3 before = entry.world(1.5D, -0.75D, -2.0D);
        Vec3 after = entry.world(1.5D, -0.75D, 1.0D);
        PlotGeometry.Crossing crossing = PlotGeometry.crossing(entry, 4.0D, before, after);
        assertNotNull(crossing);
        Vec3 motion = after.subtract(before);
        PlotGeometry.Arrival arrival = PlotGeometry.map(entry, exit, crossing, motion);
        Vec3 local = exit.local(arrival.position());
        assertEquals(1.5D, local.x, 1.0e-9D, "same place across");
        assertEquals(-0.75D, local.y, 1.0e-9D, "same place up");
        assertEquals(PlotGeometry.CLEARANCE + 1.0D, local.z, 1.0e-9D, "just past, by what it had covered");
        assertEquals(motion.length(), arrival.motion().length(), 1.0e-9D, "same speed");
        assertClose(exit.normal(), arrival.motion().normalize(), "out of the front");
    }

    /** Either face of the entry leads out of the exit's front - a floor rift takes what falls in from above. */
    @Test
    void theEntryWorksFromEitherFace() {
        PlotGeometry.Frame floor = PlotGeometry.frame(new Vec3(0, 60, 0), 0, -90);
        PlotGeometry.Frame exit = PlotGeometry.frame(new Vec3(900, 140, 900), 45, 60);
        Vec3 falling = new Vec3(0, -1.2D, 0);
        PlotGeometry.Crossing down = PlotGeometry.crossing(floor, 3.0D, new Vec3(0.5, 61, 0.2), new Vec3(0.5, 59.8, 0.2));
        assertNotNull(down);
        assertTrue(!down.forward(), "falling through a skyward rift is going through it backwards");
        PlotGeometry.Arrival arrival = PlotGeometry.map(floor, exit, down, falling);
        assertClose(exit.normal(), arrival.motion().normalize(), "and still comes out of the front");
        assertTrue(exit.side(arrival.position()) > 0.0D, "in front of the exit");
    }

    // ------------------------------------------------------------------ fit

    @Test
    void aHullNeedsTheCircleRoundItsShadow() {
        PlotGeometry.Frame facingSouth = PlotGeometry.frame(Vec3.ZERO, 0, 0);
        Quaterniond level = new Quaterniond();
        // Ten wide, six tall, thirty long, flying south along its length: its shadow is ten by six.
        assertEquals(0.5D * Math.hypot(10, 6), PlotGeometry.needs(10, 6, 30, level, facingSouth), 1.0e-9D);
        // Broadside on, its length is across the rift.
        Quaterniond broadside = new Quaterniond().rotateY(Math.PI / 2.0D);
        assertEquals(0.5D * Math.hypot(30, 6), PlotGeometry.needs(10, 6, 30, broadside, facingSouth), 1.0e-9D);
    }

    /** The same hull meeting the same rift the same way needs the same rift, whichever way the compass points. */
    @Test
    void whatAHullNeedsDoesNotDependOnTheCompass() {
        double reference = PlotGeometry.needs(12, 7, 40, new Quaterniond(), PlotGeometry.frame(Vec3.ZERO, 0, 0));
        for (double yaw : YAWS) {
            Quaterniond hull = new Quaterniond().rotateY(Math.toRadians(-yaw));
            PlotGeometry.Frame rift = PlotGeometry.frame(Vec3.ZERO, yaw, 0);
            assertEquals(reference, PlotGeometry.needs(12, 7, 40, hull, rift), 1.0e-7D, "yaw " + yaw);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static double wrap(double degrees) {
        double wrapped = degrees % 360.0D;
        if (wrapped > 180.0D) {
            wrapped -= 360.0D;
        } else if (wrapped < -180.0D) {
            wrapped += 360.0D;
        }
        return wrapped;
    }

    private static void assertClose(Vec3 expected, Vec3 actual, String message) {
        assertEquals(expected.x, actual.x, 1.0e-7D, message + " (x) " + actual);
        assertEquals(expected.y, actual.y, 1.0e-7D, message + " (y) " + actual);
        assertEquals(expected.z, actual.z, 1.0e-7D, message + " (z) " + actual);
    }
}
