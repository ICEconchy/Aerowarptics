package uk.co.iceconchy.aerowarptics.plotter;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Rift Plotter's dock helper: it catches only what waits behind the entry, lines a hull up level and end-on the
 * least way round (or the way it fits), holds it just short of the rift, and glides it there gently - a simulated
 * hull, steered tick by tick, arrives lined up without ever being pulled faster than the cap.
 */
class DockAlignTest {

    private static final PlotGeometry.Frame SOUTH = PlotGeometry.frame(new Vec3(0, 80, 0), 0, 0);

    @Test
    void onlyWhatWaitsBehindTheEntryIsInTheDock() {
        double zone = DockAlign.zoneRadius(4.0D);
        assertTrue(DockAlign.inDock(SOUTH, zone, 32.0D, new Vec3(0, 80, -10)), "behind, on the axis");
        assertFalse(DockAlign.inDock(SOUTH, zone, 32.0D, new Vec3(0, 80, 10)), "in front: already through");
        assertFalse(DockAlign.inDock(SOUTH, zone, 32.0D, new Vec3(0, 80, -40)), "too far back");
        assertFalse(DockAlign.inDock(SOUTH, zone, 32.0D, new Vec3(zone + 1, 80, -10)), "too far off the axis");
    }

    /** The dock is as wide and as deep as it is told, whatever the rift: a point is in one size of dock and out of another. */
    @Test
    void theDockIsTheSizeItIsGiven() {
        Vec3 wide = new Vec3(20, 80, -10);
        Vec3 deep = new Vec3(0, 80, -60);
        assertFalse(DockAlign.inDock(SOUTH, 12.0D, 32.0D, wide));
        assertTrue(DockAlign.inDock(SOUTH, 24.0D, 32.0D, wide), "a wider dock catches further out");
        assertFalse(DockAlign.inDock(SOUTH, 12.0D, 32.0D, deep));
        assertTrue(DockAlign.inDock(SOUTH, 12.0D, 64.0D, deep), "a deeper dock catches further back");
        assertFalse(DockAlign.inDock(SOUTH, 2.0D, 4.0D, new Vec3(3, 80, -2)), "a narrow dock leaves alone what is beside it");
        assertTrue(DockAlign.inDock(SOUTH, 2.0D, 4.0D, new Vec3(1, 80, -2)));
    }

    /**
     * The dock is behind the way the entry faces, wherever the entry has been turned to face: turn the entry and the
     * dock swings round with it, about the entry's middle.
     */
    @Test
    void theDockTurnsWithTheEntry() {
        BlockPos master = new BlockPos(0, 64, 0);
        Plot thrown = Plot.NONE.withInput(0.0D, 0.0D, 20.0D, 6.0D); // thrown out south of the machine
        PlotGeometry.Frame outward = PlotGeometry.input(master, thrown);
        PlotGeometry.Frame east = PlotGeometry.input(master, thrown.withFacing(true, -90.0D, 0.0D));
        Vec3 northOfIt = outward.centre().add(0, 0, -10);
        Vec3 westOfIt = outward.centre().add(-10, 0, 0);
        assertTrue(DockAlign.inDock(outward, 8.0D, 32.0D, northOfIt), "facing out, the dock is between it and the machine");
        assertFalse(DockAlign.inDock(outward, 8.0D, 32.0D, westOfIt));
        assertTrue(DockAlign.inDock(east, 8.0D, 32.0D, westOfIt), "facing east, the dock is to its west");
        assertFalse(DockAlign.inDock(east, 8.0D, 32.0D, northOfIt));
        // And a ship there is lined up end-on to the way the entry now faces, and sent that way.
        Quaterniond lined = DockAlign.lineUp(new Quaterniond(), new double[]{4, 4, 12}, east, 6.0D);
        assertEquals(1.0D, Math.abs(lined.transform(new Vector3d(0, 0, 1)).x), 1.0e-9D, "its length is not along east");
        assertEquals(1.0D, DockAlign.launch(east, 1.0D).x, 1.0e-9D, "launched east");
        Vec3 mark = DockAlign.mark(east, new double[]{4, 4, 12}, lined);
        assertEquals(outward.centre().x - (6.0D + DockAlign.STANDOFF), mark.x, 1.0e-9D, "held west of the rift");
        assertEquals(outward.centre().z, mark.z, 1.0e-9D, "on the turned rift's axis");
    }

    /**
     * A hull longer than the dock is deep is taken by the dock to a mark behind the dock's own back wall. It is still
     * the dock's there - held to the dock's depth alone, it was let go on the way to its mark, every time.
     */
    @Test
    void aHullLongerThanTheDockIsDeepIsHeldAllTheWayToItsMark() {
        double[] longHull = {6, 5, 40};
        Quaterniond lined = DockAlign.lineUp(new Quaterniond(), longHull, SOUTH, 8.0D);
        Vec3 mark = DockAlign.mark(SOUTH, longHull, lined);
        double shallow = Firing.MIN_DOCK_DEPTH;
        assertFalse(DockAlign.inDock(SOUTH, 10.0D, shallow, mark), "the test is of a mark outside the dock");
        double kept = DockAlign.keepDepth(SOUTH, shallow, longHull, lined);
        assertTrue(DockAlign.inDock(SOUTH, 10.0D, kept, mark), "the dock lets go of a hull on its own mark");
        // Flown in from where it is caught, it is held at every step of the way.
        Vec3 position = new Vec3(1, 80, -3);
        for (int tick = 0; tick < 1200; tick++) {
            assertTrue(DockAlign.inDock(SOUTH, 10.0D, kept, position), "let go at " + position + " on tick " + tick);
            DockAlign.Steer steer = DockAlign.steer(position, mark, lined, lined);
            if (steer.linedUp()) {
                break;
            }
            position = position.add(steer.linear());
        }
        assertTrue(position.distanceTo(mark) <= DockAlign.LINED_UP_DISTANCE, "never reached its mark");
        // A short hull's mark is inside the dock, and it is held to the dock and a little slack - no further.
        double[] boat = {3, 2, 4};
        assertEquals(32.0D + DockAlign.KEEP_SLACK, DockAlign.keepDepth(SOUTH, 32.0D, boat, new Quaterniond()), 1.0e-9D);
    }

    @Test
    void aLevelTurnPointsAnEndWhereItIsTold() {
        Vector3d towards = new Vector3d(1, 0, 1).normalize();
        for (Vector3d end : new Vector3d[]{new Vector3d(0, 0, 1), new Vector3d(1, 0, 0), new Vector3d(-1, 0, 0)}) {
            Vector3d turned = DockAlign.yawTurning(end, towards).transform(new Vector3d(end));
            assertEquals(towards.x, turned.x, 1.0e-9D);
            assertEquals(0.0D, turned.y, 1.0e-9D);
            assertEquals(towards.z, turned.z, 1.0e-9D);
        }
    }

    /** Lined up is level: the hull's own up is the world's up, however it arrived. */
    @Test
    void aLinedUpHullIsLevelAndEndOn() {
        Quaterniond tilted = new Quaterniond().rotateY(0.3D).rotateX(0.2D).rotateZ(-0.15D);
        Quaterniond lined = DockAlign.lineUp(tilted, new double[]{8, 5, 20}, SOUTH, 8.0D);
        Vector3d up = lined.transform(new Vector3d(0, 1, 0));
        assertEquals(1.0D, up.y, 1.0e-9D, "not level");
        // One of its horizontal ends points through the rift, south.
        double best = 0.0D;
        for (Vector3d end : new Vector3d[]{new Vector3d(0, 0, 1), new Vector3d(0, 0, -1), new Vector3d(1, 0, 0),
                new Vector3d(-1, 0, 0)}) {
            best = Math.max(best, lined.transform(new Vector3d(end)).dot(new Vector3d(0, 0, 1)));
        }
        assertEquals(1.0D, best, 1.0e-9D, "no end points through");
    }

    /** A hull already nearly lined up turns the few degrees it is off, not round the other way. */
    @Test
    void itTurnsTheLeastWayRound() {
        Quaterniond nearly = new Quaterniond().rotateY(Math.toRadians(170.0D));
        Quaterniond lined = DockAlign.lineUp(nearly, new double[]{6, 4, 6}, SOUTH, 8.0D);
        double turned = Math.toDegrees(new Quaterniond(lined).mul(new Quaterniond(nearly).conjugate()).angle());
        turned = Math.min(turned, 360.0D - turned);
        assertTrue(turned <= 45.0D + 1.0e-6D, "turned " + turned + " degrees");
    }

    /** Broadside, a long hull does not fit a small rift; end-on it does - so end-on it goes, however far that turns it. */
    @Test
    void aHullThatOnlyFitsEndOnIsTurnedEndOn() {
        double[] longHull = {4, 4, 30};
        Quaterniond broadside = new Quaterniond().rotateY(Math.PI / 2.0D);
        Quaterniond lined = DockAlign.lineUp(broadside, longHull, SOUTH, 4.0D);
        assertTrue(PlotGeometry.needs(4, 4, 30, lined, SOUTH) <= 4.0D, "lined up a way it does not fit");
        assertTrue(DockAlign.fitsSomeWay(longHull, SOUTH, 4.0D));
        assertFalse(DockAlign.fitsSomeWay(new double[]{30, 30, 30}, SOUTH, 4.0D), "a cube this big fits nowhere");
    }

    @Test
    void aFloorRiftOnlyLevelsAHullAndKeepsItsHeading() {
        PlotGeometry.Frame floor = PlotGeometry.frame(new Vec3(0, 60, 0), 0, -90);
        Quaterniond heading = new Quaterniond().rotateY(0.8D).rotateX(0.3D);
        Quaterniond lined = DockAlign.lineUp(heading, new double[]{6, 4, 10}, floor, 8.0D);
        Vector3d nose = lined.transform(new Vector3d(0, 0, 1));
        Vector3d was = heading.transform(new Vector3d(0, 0, 1));
        assertEquals(0.0D, nose.y, 1.0e-9D, "not level");
        assertEquals(Math.atan2(was.x, was.z), Math.atan2(nose.x, nose.z), 1.0e-9D, "heading changed");
    }

    @Test
    void theMarkIsOnTheAxisJustShortOfTheRift() {
        Quaterniond lined = new Quaterniond();
        Vec3 mark = DockAlign.mark(SOUTH, new double[]{6, 4, 20}, lined);
        assertEquals(0.0D, mark.x, 1.0e-9D);
        assertEquals(80.0D, mark.y, 1.0e-9D);
        assertEquals(-(10.0D + DockAlign.STANDOFF), mark.z, 1.0e-9D, "its nearest end waits the standoff away");
    }

    /**
     * A hull caught off to one side, turned wrong and tipped, steered tick by tick as the plotter would, glides in and
     * is lined up - and is never drawn or turned faster than the caps allow.
     */
    @Test
    void aCaughtHullGlidesInToItsMark() {
        double[] size = {6, 5, 16};
        Vec3 position = new Vec3(9, 84, -25);
        Quaterniond orientation = new Quaterniond().rotateY(1.9D).rotateX(0.1D);
        boolean linedUp = false;
        for (int tick = 0; tick < 1200 && !linedUp; tick++) {
            Quaterniond lined = DockAlign.lineUp(orientation, size, SOUTH, 8.0D);
            DockAlign.Steer steer = DockAlign.steer(position, DockAlign.mark(SOUTH, size, lined), orientation, lined);
            assertTrue(steer.linear().length() <= DockAlign.MAX_DRAW + 1.0e-9D, "yanked");
            assertTrue(steer.angular().length() <= DockAlign.MAX_TURN + 1.0e-9D, "spun");
            linedUp = steer.linedUp();
            position = position.add(steer.linear());
            double rate = steer.angular().length();
            if (rate > 0.0D) {
                Vec3 axis = steer.angular().scale(1.0D / rate);
                orientation = new Quaterniond().rotateAxis(rate, axis.x, axis.y, axis.z).mul(orientation);
            }
        }
        assertTrue(linedUp, "never lined up, stopped at " + position);
    }

    @Test
    void theLaunchIsStraightThroughTheRift() {
        Vec3 push = DockAlign.launch(SOUTH, 1.0D);
        assertEquals(1.0D, push.z, 1.0e-9D);
        assertEquals(0.0D, push.x, 1.0e-9D);
    }
}
