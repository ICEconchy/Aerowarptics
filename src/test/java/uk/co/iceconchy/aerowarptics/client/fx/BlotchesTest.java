package uk.co.iceconchy.aerowarptics.client.fx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The blobs an Ender rift's face bleeds in with and a Sketch rift's paint spreads in.
 *
 * <p>The failure worth guarding does not throw and does not show in a still of an ordinary rift: a blob
 * that reaches past the rim. For the End that is an opaque patch of starfield hanging in the sky beside
 * the aperture; for a wash, paint on the world. It depends on a seed, so it is checked across many.
 */
class BlotchesTest {

    private static final int SEEDS = 400;

    private static int seed(int index) {
        return RiftShatter.seedFor(index * 3.7D, 64.0D + index, -index * 11.3D);
    }

    @Test
    void noBlobEverReachesPastItsLimit() {
        for (float reach : new float[]{0.9F, 0.95F}) {
            for (int index = 0; index < SEEDS; index++) {
                for (Blotches.Blotch blotch : Blotches.scatter(seed(index), 6, reach, 40_000)) {
                    for (int step = 0; step < 72; step++) {
                        double angle = step * (Math.PI * 2.0D / 72);
                        for (float time = 0.0F; time < 60.0F; time += 7.3F) {
                            double out = blotch.radius() * Blotches.edge(blotch, angle, time);
                            double edgeU = blotch.u() + Math.cos(angle) * out;
                            double edgeV = blotch.v() + Math.sin(angle) * out;
                            assertTrue(Math.hypot(edgeU, edgeV) <= reach + 1.0e-4D,
                                    "a blob reached " + Math.hypot(edgeU, edgeV) + " past " + reach
                                            + " at seed " + index);
                        }
                    }
                }
            }
        }
    }

    @Test
    void anEdgeNeverStraysFurtherThanTheWobble() {
        Blotches.Blotch blotch = Blotches.scatter(seed(1), 1, 0.9F, 0)[0];
        for (int step = 0; step < 1_000; step++) {
            float edge = Blotches.edge(blotch, step * 0.037D, step * 0.11F);
            assertTrue(edge >= 1.0F - Blotches.WOBBLE - 1.0e-5F && edge <= 1.0F + Blotches.WOBBLE + 1.0e-5F,
                    "the edge strayed to " + edge);
        }
    }

    /** The middle of the face is covered first, and from the start. */
    @Test
    void theFirstBlobIsTheMiddleAndStartsAtOnce() {
        for (int index = 0; index < SEEDS; index++) {
            Blotches.Blotch first = Blotches.scatter(seed(index), 5, 0.9F, 41_000)[0];
            assertEquals(0.0F, first.distance(), 1.0e-6F);
            assertEquals(0.0F, first.start(), 1.0e-6F);
        }
    }

    @Test
    void aBlobGrowsFromNothingToAllOfItselfAndNeverShrinks() {
        for (int index = 0; index < 50; index++) {
            for (Blotches.Blotch blotch : Blotches.scatter(seed(index), 6, 0.95F, 40_000)) {
                assertEquals(0.0F, Blotches.grown(blotch, 0.0F), 1.0e-6F, "a blob was there before anything spread");
                if (blotch.start() > 0.01F) {
                    assertEquals(0.0F, Blotches.grown(blotch, blotch.start() * 0.99F), 1.0e-6F,
                            "a blob grew before its own start");
                }
                assertEquals(1.0F, Blotches.grown(blotch, 1.0F), 1.0e-6F, "a blob was caught small at the end");
                assertEquals(1.0F, Blotches.grown(blotch, 3.0F), 1.0e-6F, "a blob kept growing past its size");
                float last = 0.0F;
                for (float progress = 0.0F; progress <= 1.0F; progress += 0.01F) {
                    float grown = Blotches.grown(blotch, progress);
                    assertTrue(grown >= last - 1.0e-6F, "a blob shrank while spreading");
                    last = grown;
                }
            }
        }
    }

    /** Every viewer works the blobs out for themselves, so one rift must always lay out the same. */
    @Test
    void oneRiftAlwaysLaysOutTheSameBlobs() {
        int seed = seed(7);
        Blotches.Blotch[] first = Blotches.scatter(seed, 6, 0.95F, 40_000);
        Blotches.Blotch[] second = Blotches.scatter(seed, 6, 0.95F, 40_000);
        for (int index = 0; index < first.length; index++) {
            assertEquals(first[index], second[index]);
        }
    }

    /** The End's patches and a wash taken from one rift's seed must not be the same blobs. */
    @Test
    void theSaltSeparatesTwoUsesOfOneSeed() {
        int seed = seed(9);
        Blotches.Blotch[] bleed = Blotches.scatter(seed, 5, 0.9F, 40_000);
        Blotches.Blotch[] wash = Blotches.scatter(seed, 5, 0.9F, 41_000);
        int same = 0;
        for (int index = 1; index < bleed.length; index++) {
            if (bleed[index].equals(wash[index])) {
                same++;
            }
        }
        assertEquals(0, same, "two uses of one seed laid out the same blobs");
    }
}
