package uk.co.iceconchy.aerowarptics;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.FlightGeometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That a hull is the same size whichever way it is facing.
 *
 * <p>The aperture radius used to come from the hull's world bounding box - its X extent and its height
 * - so a four-hundred-block ship got a forty-block rift flying north and a two-hundred-and-seventy-block
 * one flying east. Every clearance margin and chunk claim is sized off that radius, which made whether
 * a large ship could warp at all a question of its compass heading. Nothing threw; the ship was simply
 * refused a landing in two directions out of four.
 */
class FlightGeometryTest {

    private static final double EPSILON = 1.0e-6D;

    private static FlightGeometry measure(double width, double height, double length, double yaw,
                                          Vector3d shipBow) {
        Quaterniond orientation = new Quaterniond().rotateY(yaw);
        Vector3d bow = orientation.transform(new Vector3d(shipBow));
        return FlightGeometry.of(width, height, length, orientation, bow, 1.35D, 60, 35, 1.25D);
    }

    /** Turn the ship to any heading and fly it along its own length: nothing about it changes. */
    @Test
    void aHullIsTheSameSizeOnEveryHeading() {
        Vector3d ownBow = new Vector3d(0.0D, 0.0D, -1.0D);
        for (double[] size : new double[][]{{60, 60, 400}, {12, 9, 30}, {200, 200, 1000}}) {
            FlightGeometry reference = measure(size[0], size[1], size[2], 0.0D, ownBow);
            for (double degrees : new double[]{30, 45, 90, 135, 180, 211, 270, 315}) {
                FlightGeometry turned = measure(size[0], size[1], size[2], Math.toRadians(degrees), ownBow);
                String at = java.util.Arrays.toString(size) + " at " + degrees + " degrees";
                assertEquals(reference.hullSpan(), turned.hullSpan(), EPSILON, at);
                assertEquals(reference.crossSpan(), turned.crossSpan(), EPSILON, at);
                assertEquals(reference.hullRadius(), turned.hullRadius(), EPSILON, at);
                assertEquals(reference.riftRadius(), turned.riftRadius(), EPSILON, at);
                assertEquals(reference.emergeDistance(), turned.emergeDistance(), EPSILON, at);
            }
        }
    }

    /** Flown along its length it is long; flown sideways it is only as long as it is wide. */
    @Test
    void theSpanIsAlongTheBearingAndTheWidthAcrossIt() {
        FlightGeometry lengthways = measure(60, 60, 400, 0.0D, new Vector3d(0, 0, -1));
        assertEquals(400.0D, lengthways.hullSpan(), EPSILON);
        assertEquals(60.0D, lengthways.crossSpan(), EPSILON);
        assertEquals(60.0D, lengthways.height(), EPSILON);

        FlightGeometry sideways = measure(60, 60, 400, 0.0D, new Vector3d(1, 0, 0));
        assertEquals(60.0D, sideways.hullSpan(), EPSILON);
        assertEquals(400.0D, sideways.crossSpan(), EPSILON);
    }

    /**
     * For the one case the old arithmetic got right - an unturned hull flying along Z - the answers
     * are unchanged, so no small ship's warp is any different for this.
     */
    @Test
    void anUnturnedHullFlyingNorthMeasuresAsItAlwaysDid() {
        double width = 60;
        double height = 60;
        double length = 400;
        FlightGeometry now = measure(width, height, length, 0.0D, new Vector3d(0, 0, -1));
        double legacyLength = Math.max(width, length);
        double legacyRadius = Math.max(4.0D, Math.sqrt(width * width + height * height) * 0.5D);
        assertEquals(legacyLength, now.hullSpan(), EPSILON);
        assertEquals(legacyRadius, now.hullRadius(), EPSILON);
    }

    /** And the heading the old arithmetic got badly wrong. */
    @Test
    void aHullFlyingEastNoLongerGetsARiftSizedByItsLength() {
        double width = 400;  // the hull lies along X
        double height = 60;
        double length = 60;
        FlightGeometry east = measure(width, height, length, 0.0D, new Vector3d(1, 0, 0));
        double legacyRadius = Math.max(4.0D, Math.sqrt(width * width + height * height) * 0.5D);
        assertTrue(legacyRadius > 200.0D, "the legacy radius was " + legacyRadius);
        assertEquals(Math.hypot(60, 60) * 0.5D, east.hullRadius(), EPSILON);
        assertTrue(east.apertureMargin() < 16.0D, "margin " + east.apertureMargin());
    }

    /** The emerge run is the old formula, with the span measured along the bearing. */
    @Test
    void theEmergeDistanceFollowsThePassage() {
        FlightGeometry geometry = measure(60, 60, 400, 0.0D, new Vector3d(0, 0, -1));
        double transitRun = 400.0D + FlightGeometry.TRANSIT_MARGIN;
        double settle = transitRun / 60.0D * 35.0D * 0.5D;
        assertEquals(transitRun, geometry.transitRun(), EPSILON);
        assertEquals(Math.max(400.0D * 1.25D, transitRun + settle), geometry.emergeDistance(), EPSILON);
        assertEquals(transitRun / 60.0D, geometry.passageSpeed(), EPSILON);
    }

    /** The shadow of a turned box on a line is the sum of its sides' shadows. */
    @Test
    void theExtentOfATurnedBoxIsItsShadow() {
        Quaterniond quarter = new Quaterniond().rotateY(Math.toRadians(45));
        double along = FlightGeometry.extentAlong(10, 1, 10, quarter, new Vector3d(1, 0, 0));
        assertEquals(20.0D / Math.sqrt(2.0D), along, EPSILON);

        Quaterniond pitched = new Quaterniond().rotateX(Math.toRadians(90));
        assertEquals(400.0D, FlightGeometry.extentAlong(60, 60, 400, pitched, new Vector3d(0, 1, 0)), EPSILON,
                "a hull stood on end is as tall as it was long");
    }

    /** A single block still gets an aperture it can be seen going through. */
    @Test
    void aTinyHullStillGetsAWorkableRift() {
        FlightGeometry block = measure(1, 1, 1, 0.0D, new Vector3d(0, 0, -1));
        assertEquals(FlightGeometry.MINIMUM_HULL_RADIUS, block.hullRadius(), EPSILON);
        assertTrue(block.emergeDistance() >= 8.0D, "run-out " + block.emergeDistance());
    }
}
