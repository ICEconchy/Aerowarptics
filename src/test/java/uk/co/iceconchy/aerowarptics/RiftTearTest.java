package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.client.fx.RiftTear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shape of a torn aperture.
 *
 * <p>These are not tests of how it looks. The face of a rift is the thing that hides an airship while
 * it flies through, so the one property that matters is that the tear never cuts inside the circle
 * doing the hiding. Get that wrong and the ship is visible through a gap in its own portal.
 */
class RiftTearTest {

    @Test
    void theTearNeverCutsInsideTheCoveringCircle() {
        for (int step = 0; step < 2_000; step++) {
            double angle = step * 0.0031D;
            float time = step * 0.017F;
            float rim = RiftTear.rim(angle, time);
            assertTrue(rim >= 1.0F,
                    "the tear reached inside the covering circle at angle " + angle + ": " + rim);
        }
    }

    @Test
    void theTearStaysWithinItsStatedReach() {
        // The renderer sizes nothing else off this, but a rim that wandered without bound would put
        // an aperture around a hull it was never meant to swallow.
        for (int step = 0; step < 2_000; step++) {
            float rim = RiftTear.rim(step * 0.0047D, step * 0.023F);
            assertTrue(rim <= 1.0F + RiftTear.RAG, "the tear overran its reach: " + rim);
        }
    }

    @Test
    void theRimIsContinuousAllTheWayRound() {
        // A rift is drawn as a closed loop of quads. A jump between neighbouring angles would show
        // as a notch in the edge, and a large enough one as a hole.
        float time = 12.5F;
        float previous = RiftTear.rim(0.0D, time);
        double step = Math.PI * 2.0D / 48.0D;
        for (int segment = 1; segment <= 48; segment++) {
            float rim = RiftTear.rim(segment * step, time);
            assertTrue(Math.abs(rim - previous) < RiftTear.RAG * 0.75F,
                    "the rim jumped between neighbouring segments at " + segment);
            previous = rim;
        }
        assertEquals(RiftTear.rim(0.0D, time), RiftTear.rim(Math.PI * 2.0D, time), 1.0e-5F,
                "the rim must meet itself where the loop closes");
    }

    @Test
    void theRimMovesOverTime() {
        // A static tear is a shape; a moving one is a tear. If these ever came out equal the noise
        // would have stopped depending on time.
        assertTrue(Math.abs(RiftTear.rim(1.0D, 0.0F) - RiftTear.rim(1.0D, 3.0F)) > 1.0e-4F);
    }

    @Test
    void theFlamesStayWithinZeroAndOne() {
        for (int step = 0; step < 2_000; step++) {
            float lick = RiftTear.lick(step * 0.0039D, step * 0.031F);
            assertTrue(lick >= 0.0F && lick <= 1.0F, "flame length out of range: " + lick);
        }
    }
}
