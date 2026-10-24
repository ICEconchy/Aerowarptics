package uk.co.iceconchy.aerowarptics.airship;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.FlightGeometry;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The box a warp is sized from, once a vessel is more than one sub-level.
 *
 * <p>The failure this guards does not throw: a ship built in sections is given a rift the size of the
 * section its drive is in, and the rest fly past the aperture and are put down in ground nobody looked at.
 */
class VesselBoxTest {

    private static final double EPSILON = 1.0e-9D;

    /** A hull eighty long, forty wide and twenty high, as the reported ship's drive section was. */
    private static double[] hull() {
        return new double[] {0.0D, 0.0D, 0.0D, 40.0D, 20.0D, 80.0D};
    }

    @Test
    void aSectionAsternMakesTheVesselLongerAndNoWider() {
        double[] box = hull();
        // The same size of section, sitting sixty blocks astern with a gap between: its own frame is the
        // hull's moved along Z.
        VesselBox.include(box, 0.0D, 0.0D, 0.0D, 40.0D, 20.0D, 80.0D, corner -> corner.add(0.0D, 0.0D, 140.0D));
        assertArrayEquals(new double[] {0.0D, 0.0D, 0.0D, 40.0D, 20.0D, 220.0D}, box, EPSILON);
    }

    @Test
    void aSectionInsideTheHullChangesNothing() {
        double[] box = hull();
        VesselBox.include(box, 0.0D, 0.0D, 0.0D, 4.0D, 4.0D, 4.0D, corner -> corner.add(10.0D, 5.0D, 10.0D));
        assertArrayEquals(hull(), box, EPSILON);
    }

    @Test
    void aSectionTurnedAgainstTheHullIsHeldWhole() {
        double[] box = hull();
        // A prong thirty long and four wide, turned forty-five degrees and set off the hull's side. Every
        // corner of it has to end up inside the box, wherever the turn puts it.
        Quaterniond turn = new Quaterniond().rotateY(Math.PI / 4.0D);
        Vector3d offset = new Vector3d(60.0D, 0.0D, 0.0D);
        VesselBox.include(box, 0.0D, 0.0D, 0.0D, 4.0D, 4.0D, 30.0D, corner -> turn.transform(corner).add(offset));
        for (int corner = 0; corner < 8; corner++) {
            Vector3d placed = turn.transform(new Vector3d(
                    (corner & 1) == 0 ? 0.0D : 4.0D, (corner & 2) == 0 ? 0.0D : 4.0D,
                    (corner & 4) == 0 ? 0.0D : 30.0D)).add(offset);
            assertTrue(placed.x >= box[0] - EPSILON && placed.x <= box[3] + EPSILON, "x of corner " + corner);
            assertTrue(placed.y >= box[1] - EPSILON && placed.y <= box[4] + EPSILON, "y of corner " + corner);
            assertTrue(placed.z >= box[2] - EPSILON && placed.z <= box[5] + EPSILON, "z of corner " + corner);
        }
        // And it never shrinks what was there.
        assertEquals(0.0D, box[0], EPSILON);
        assertEquals(80.0D, box[5], EPSILON);
    }

    @Test
    void aCornerThatCannotBePlacedDoesNotMakeTheBoxTheSizeOfTheWorld() {
        double[] box = hull();
        VesselBox.include(box, 0.0D, 0.0D, 0.0D, 4.0D, 4.0D, 4.0D,
                corner -> new Vector3d(Double.NaN, Double.POSITIVE_INFINITY, 0.0D));
        assertArrayEquals(hull(), box, EPSILON);
        VesselBox.include(box, 0.0D, 0.0D, 0.0D, 4.0D, 4.0D, 4.0D, corner -> null);
        assertArrayEquals(hull(), box, EPSILON);
    }

    @Test
    void theRiftIsSizedForEverySectionAndNotOnlyTheDrives() {
        // Flying along the hull's own Z. Alone, the drive's section is eighty along the bow.
        Quaterniond level = new Quaterniond();
        Vector3d bow = new Vector3d(0.0D, 0.0D, 1.0D);
        FlightGeometry alone = FlightGeometry.of(40.0D, 20.0D, 80.0D, level, bow, 1.35D, 60, 30, 1.25D);

        double[] box = hull();
        // A second section astern and a prong off each beam.
        VesselBox.include(box, 0.0D, 0.0D, 0.0D, 40.0D, 20.0D, 80.0D, corner -> corner.add(0.0D, 0.0D, -100.0D));
        VesselBox.include(box, 0.0D, 0.0D, 0.0D, 8.0D, 8.0D, 60.0D, corner -> corner.add(-20.0D, 6.0D, 40.0D));
        VesselBox.include(box, 0.0D, 0.0D, 0.0D, 8.0D, 8.0D, 60.0D, corner -> corner.add(52.0D, 6.0D, 40.0D));
        FlightGeometry whole = FlightGeometry.of(box[3] - box[0], box[4] - box[1], box[5] - box[2], level, bow,
                1.35D, 60, 30, 1.25D);

        assertEquals(80.0D, alone.hullSpan(), EPSILON);
        assertEquals(200.0D, whole.hullSpan(), EPSILON);
        assertEquals(80.0D, whole.crossSpan(), EPSILON);
        // Wider, so a larger aperture; longer, so a longer passage and a longer run out to prove clear.
        assertTrue(whole.riftRadius() > alone.riftRadius());
        assertTrue(whole.transitRun() > alone.transitRun());
        assertTrue(whole.emergeDistance() > alone.emergeDistance());
    }

    @Test
    void theVesselsSizeDoesNotChangeWithTheCompass() {
        // The box is in the hull's own frame, so turning the whole ship turns the bow with it and leaves
        // every span alone - the reason a warp is never sized from a world box.
        double[] box = hull();
        VesselBox.include(box, 0.0D, 0.0D, 0.0D, 40.0D, 20.0D, 80.0D, corner -> corner.add(0.0D, 0.0D, 140.0D));
        FlightGeometry north = null;
        for (int step = 0; step < 16; step++) {
            Quaterniond heading = new Quaterniond().rotateY(step * Math.PI / 8.0D);
            Vector3d bow = heading.transform(new Vector3d(0.0D, 0.0D, 1.0D));
            FlightGeometry turned = FlightGeometry.of(box[3] - box[0], box[4] - box[1], box[5] - box[2], heading,
                    bow, 1.35D, 60, 30, 1.25D);
            if (north == null) {
                north = turned;
            }
            assertEquals(north.hullSpan(), turned.hullSpan(), 1.0e-6D, "heading " + step);
            assertEquals(north.crossSpan(), turned.crossSpan(), 1.0e-6D, "heading " + step);
            assertEquals(north.riftRadius(), turned.riftRadius(), 1.0e-6D, "heading " + step);
        }
    }
}
