package uk.co.iceconchy.aerowarptics.client.fx;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ball a Sketch rift is screwed up into.
 *
 * <p>A crumpled ball reads entirely from its facets catching the light at different angles, so a facet
 * lit from inside is a dark patch for no reason - and a corner pushed in too far is what causes one.
 * Neither throws, and both depend on the seed.
 */
class PaperBallTest {

    @Test
    void everyFacetFacesOutward() {
        for (int seed = 0; seed < 64; seed++) {
            for (PaperBall.Face face : PaperBall.faces(seed * 0x9E3779B1)) {
                double length = Math.sqrt(face.nx() * face.nx() + face.ny() * face.ny() + face.nz() * face.nz());
                assertEquals(1.0D, length, 1.0e-4D, "a facet with no direction to it");
                float[] c = face.corners();
                double midX = (c[0] + c[3] + c[6] + c[9]) * 0.25D;
                double midY = (c[1] + c[4] + c[7] + c[10]) * 0.25D;
                double midZ = (c[2] + c[5] + c[8] + c[11]) * 0.25D;
                assertTrue(face.nx() * midX + face.ny() * midY + face.nz() * midZ > 0.0D,
                        "a facet of the ball is lit from inside it");
            }
        }
    }

    @Test
    void theBallIsNeverBiggerThanItself() {
        for (int seed = 0; seed < 64; seed++) {
            boolean crumpled = false;
            for (PaperBall.Face face : PaperBall.faces(seed)) {
                float[] c = face.corners();
                for (int corner = 0; corner < 4; corner++) {
                    double radius = Math.sqrt(c[corner * 3] * c[corner * 3] + c[corner * 3 + 1] * c[corner * 3 + 1]
                            + c[corner * 3 + 2] * c[corner * 3 + 2]);
                    assertTrue(radius <= 1.0D + 1.0e-5D, "the ball swelled past its own size: " + radius);
                    crumpled |= radius < 0.92D;
                }
            }
            assertTrue(crumpled, "the ball came out perfectly round, which is not what crumpled paper is");
        }
    }

    /** Every viewer screws the same drawing up the same way. */
    @Test
    void oneRiftAlwaysCrumplesTheSameWay() {
        List<PaperBall.Face> first = PaperBall.faces(77);
        List<PaperBall.Face> second = PaperBall.faces(77);
        assertEquals(first.size(), second.size());
        for (int index = 0; index < first.size(); index++) {
            assertTrue(java.util.Arrays.equals(first.get(index).corners(), second.get(index).corners()));
            assertEquals(first.get(index).tone(), second.get(index).tone());
        }
        assertTrue(!java.util.Arrays.equals(PaperBall.faces(77).get(3).corners(),
                PaperBall.faces(78).get(3).corners()), "two rifts crumpled identically");
    }

    /** Creases are shading, not blackout: a facet is darkened, never put out. */
    @Test
    void everyCreaseCanStillBeSeen() {
        for (PaperBall.Face face : PaperBall.faces(5)) {
            assertTrue(face.tone() > 0.5F && face.tone() <= 1.0F, "a crease at " + face.tone());
        }
    }
}
