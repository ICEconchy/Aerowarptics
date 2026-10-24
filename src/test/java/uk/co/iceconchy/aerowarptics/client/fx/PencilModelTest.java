package uk.co.iceconchy.aerowarptics.client.fx;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Sketch rift's pencil, as a mesh.
 *
 * <p>A face lit from its inside is dark where it should be bright, a point that is not at the origin
 * draws its line somewhere the pencil is not, and a barrel that pokes through its ferrule is visible
 * metal-and-paint confetti. None of them throw.
 */
class PencilModelTest {

    @Test
    void theTipIsTheOriginAndTheEraserIsTheFarEnd() {
        float lowest = Float.MAX_VALUE;
        float highest = -Float.MAX_VALUE;
        for (PencilModel.Face face : PencilModel.faces()) {
            float[] c = face.corners();
            for (int corner = 0; corner < 4; corner++) {
                lowest = Math.min(lowest, c[corner * 3 + 2]);
                highest = Math.max(highest, c[corner * 3 + 2]);
                if (c[corner * 3 + 2] < 1.0e-6F) {
                    assertEquals(0.0D, Math.hypot(c[corner * 3], c[corner * 3 + 1]), 1.0e-6D,
                            "something reaches the page other than the point");
                }
            }
        }
        assertEquals(0.0F, lowest, 1.0e-6F, "the point is not at the origin");
        assertEquals(1.0F, highest, 1.0e-6F, "the pencil is not one long");
    }

    @Test
    void nothingIsWiderThanTheRidges() {
        for (PencilModel.Face face : PencilModel.faces()) {
            float[] c = face.corners();
            for (int corner = 0; corner < 4; corner++) {
                assertTrue(Math.hypot(c[corner * 3], c[corner * 3 + 1]) <= PencilModel.RIDGE_RADIUS + 1.0e-6F);
            }
        }
    }

    /** Every face is lit from outside: a unit normal pointing away from the axis, or off either end. */
    @Test
    void everyFaceFacesOutward() {
        for (PencilModel.Face face : PencilModel.faces()) {
            double length = Math.sqrt(face.nx() * face.nx() + face.ny() * face.ny() + face.nz() * face.nz());
            assertEquals(1.0D, length, 1.0e-4D, face.part() + " has a normal that is not a direction");
            float[] c = face.corners();
            double midX = (c[0] + c[3] + c[6] + c[9]) * 0.25D;
            double midY = (c[1] + c[4] + c[7] + c[10]) * 0.25D;
            boolean end = Math.abs(face.nx()) < 1.0e-6F && Math.abs(face.ny()) < 1.0e-6F;
            if (!end) {
                assertTrue(face.nx() * midX + face.ny() * midY > 0.0D,
                        "a " + face.part() + " face is lit from inside the pencil");
            }
        }
    }

    /** The point leans back towards the pencil, which is what a cone's faces do. */
    @Test
    void theConesFaceTheTipEnd() {
        for (PencilModel.Face face : PencilModel.faces()) {
            if (face.part() == PencilModel.Part.LEAD || face.part() == PencilModel.Part.WOOD) {
                assertTrue(face.nz() < 0.0F, face.part() + " faces the wrong way along the pencil");
            }
        }
    }

    @Test
    void everyPartIsThere() {
        Set<PencilModel.Part> seen = EnumSet.noneOf(PencilModel.Part.class);
        for (PencilModel.Face face : PencilModel.faces()) {
            seen.add(face.part());
        }
        assertEquals(EnumSet.allOf(PencilModel.Part.class), seen);
    }

    /**
     * The barrel is inside its ferrule all the way round, so no corner of the hexagon shows through the
     * metal. The ferrule's corners sit on the barrel's, which is why it has twice as many.
     */
    @Test
    void theFerruleHidesTheEndOfTheBarrel() {
        assertEquals(0, PencilModel.ROUND_SIDES % PencilModel.BARREL_SIDES, "the ferrule's corners miss the barrel's");
        for (int step = 0; step < 720; step++) {
            double angle = step * Math.PI / 360.0D;
            assertTrue(boundary(PencilModel.BARREL_SIDES, PencilModel.RADIUS, angle)
                            < boundary(PencilModel.ROUND_SIDES, PencilModel.FERRULE_RADIUS, angle),
                    "the barrel pokes through the ferrule at " + angle);
        }
    }

    @Test
    void thePartsGoInOrderFromTheTip() {
        assertTrue(0.0F < PencilModel.LEAD_END && PencilModel.LEAD_END < PencilModel.WOOD_END
                && PencilModel.WOOD_END < PencilModel.STRIPE_FROM && PencilModel.STRIPE_FROM < PencilModel.STRIPE_TO
                && PencilModel.STRIPE_TO < PencilModel.BODY_END && PencilModel.BODY_END < PencilModel.FERRULE_END
                && PencilModel.FERRULE_END < 1.0F);
    }

    /** How far a regular polygon with a corner on angle zero reaches at an angle. */
    private static double boundary(int sides, double radius, double angle) {
        double sector = Math.PI * 2.0D / sides;
        double within = angle - Math.floor(angle / sector) * sector - sector * 0.5D;
        return radius * Math.cos(sector * 0.5D) / Math.cos(within);
    }
}
