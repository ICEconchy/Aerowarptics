package uk.co.iceconchy.aerowarptics.client.fx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bend a Mostly Dangerous rift puts in the view.
 *
 * <p>A lens that is wrong still draws, which is the trouble with it. A seam at its edge is a circle
 * drawn round the rift; a fold is a crease with the same sky either side of it; a pull the wrong way is
 * space bending away from a mass; and a lens that never lets go is a smudge on the screen that follows
 * the rift about.
 */
class LensProfileTest {

    private static final float[] STRENGTHS = {0.0F, 0.1F, 0.35F, 0.5F, 0.8F, 1.0F};

    /** At its rim the lens looks exactly where it is, so it meets the frame around it without a seam. */
    @Test
    void theLensHasNoSeamAtItsEdge() {
        for (float strength : STRENGTHS) {
            assertEquals(1.0F, LensProfile.sample(1.0F, strength), 1.0e-6F, "a seam at strength " + strength);
            // And it arrives there gently rather than snapping straight at the last moment.
            assertEquals(0.99F, LensProfile.sample(0.99F, strength), 1.0e-3F,
                    "the lens is still bending hard at its very edge at strength " + strength);
        }
    }

    @Test
    void theMiddleStaysInTheMiddle() {
        for (float strength : STRENGTHS) {
            assertEquals(0.0F, LensProfile.sample(0.0F, strength), 1.0e-6F);
        }
    }

    /** Further out always looks further out: nothing is drawn twice with a crease between. */
    @Test
    void theLensNeverFolds() {
        for (float strength : STRENGTHS) {
            float last = -1.0F;
            for (float rho = 0.0F; rho <= 1.0F; rho += 0.0005F) {
                float looks = LensProfile.sample(rho, strength);
                assertTrue(looks > last, "the lens folds at " + rho + " at strength " + strength);
                last = looks;
            }
        }
    }

    /** Light bends towards the mass: a point only ever shows something nearer the middle, never past it. */
    @Test
    void theLensOnlyEverPullsInwards() {
        for (float strength : STRENGTHS) {
            for (float rho = 0.0F; rho <= 1.0F; rho += 0.001F) {
                float looks = LensProfile.sample(rho, strength);
                assertTrue(looks <= rho + 1.0e-6F, "the lens pushed outwards at " + rho);
                assertTrue(looks >= 0.0F, "the lens looked through the middle at " + rho);
            }
        }
    }

    @Test
    void aLensAtRestBendsNothing() {
        for (float rho = 0.0F; rho <= 1.0F; rho += 0.01F) {
            assertEquals(rho, LensProfile.sample(rho, 0.0F), 1.0e-6F);
        }
    }

    /**
     * It bends hardest just past the cloud, and it bends there enough to see.
     *
     * <p>A lens whose strongest pull was behind the cloud would be hidden by it; one too weak would be a
     * shimmer nobody noticed.
     */
    @Test
    void theLensBendsHardestJustPastTheCloud() {
        float hardest = 0.0F;
        float where = 0.0F;
        for (float rho = 0.0F; rho <= 1.0F; rho += 0.001F) {
            float pull = rho - LensProfile.sample(rho, 1.0F);
            if (pull > hardest) {
                hardest = pull;
                where = rho;
            }
        }
        float cloud = Nebula.REACH / LensProfile.REACH;
        assertTrue(Math.abs(where - cloud) < 0.08F,
                "the lens bends hardest at " + where + ", not at the cloud's edge at " + cloud);
        assertTrue(hardest * LensProfile.REACH > 0.8F,
                "at its hardest it moves the view only " + hardest * LensProfile.REACH + " of the rim");
    }

    /**
     * Briefly: once as the cloud arrives, once as a hull comes out, once as it collapses, and nothing in
     * between - and never more than full strength.
     */
    @Test
    void theBendingIsBrief() {
        float open = 20.0F;
        float transit = 90.0F;
        float close = 300.0F;
        float bending = 0.0F;
        for (float time = 0.0F; time < 400.0F; time += 0.05F) {
            float strength = LensProfile.strength(time, open, transit, close);
            assertTrue(strength >= 0.0F && strength <= 1.0F, "strength " + strength + " at " + time);
            if (strength > 0.0F) {
                bending += 0.05F;
            }
        }
        assertTrue(bending < 110.0F, "the view was bent for " + bending + " ticks of a 400-tick rift");
        assertEquals(0.0F, LensProfile.strength(60.0F, open, transit, close), 1.0e-6F, "still bent after arriving");
        assertEquals(0.0F, LensProfile.strength(200.0F, open, transit, close), 1.0e-6F, "still bent after the hull");
        assertEquals(0.0F, LensProfile.strength(380.0F, open, transit, close), 1.0e-6F, "still bent after the collapse");
        assertEquals(0.0F, LensProfile.strength(200.0F, open, -1.0F, close), 1.0e-6F, "bent by a hull that never came");
        assertTrue(LensProfile.strength(transit + 3.0F, open, transit, close) > 0.9F, "a hull came out and nothing bent");
        assertTrue(LensProfile.strength(close + 6.0F, open, transit, close) > 0.8F, "it collapsed and nothing bent");
    }

    /** It comes and goes smoothly: at no moment does the view jump from bent to straight. */
    @Test
    void theBendingNeverJumps() {
        float last = 0.0F;
        for (float time = 0.0F; time < 400.0F; time += 0.01F) {
            float strength = LensProfile.strength(time, 20.0F, 41.5F, 250.0F);
            assertTrue(Math.abs(strength - last) < 0.01F, "the lens jumped at " + time);
            last = strength;
        }
    }
}
