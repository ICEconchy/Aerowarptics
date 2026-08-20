package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.client.fx.RiftShatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a rift breaks space open.
 *
 * <p>Not tests of how it looks. What is worth pinning down is that the fracture actually tiles the
 * pane it is cutting up - a gap between cells is a piece of glass that never existed, and an overlap
 * is two pieces occupying the same space - and that the break stays inside the window it is allowed,
 * because everything about an aperture hiding a hull depends on the hole being fully open and steady
 * before a bow reaches it.
 */
class RiftShatterTest {

    private static final float TWO_PI = (float) (Math.PI * 2.0D);

    /** A spread of seeds, so nothing here passes by luck of one fracture. */
    private static int[] seeds() {
        int[] seeds = new int[64];
        for (int i = 0; i < seeds.length; i++) {
            seeds[i] = RiftShatter.seedFor(i * 13.5D, 70.0D + i, -i * 41.25D);
        }
        return seeds;
    }

    // -------------------------------------------------------------- the cuts

    @Test
    void theCracksRunAllTheWayRoundInOrder() {
        for (int seed : seeds()) {
            float[] angles = RiftShatter.crackAngles(seed);
            assertEquals(RiftShatter.CRACKS, angles.length);
            for (int crack = 1; crack < angles.length; crack++) {
                // Two cracks that swapped places would cut a shard of negative width, which draws as
                // a fold rather than as a piece of glass.
                assertTrue(angles[crack] > angles[crack - 1],
                        "cracks out of order at " + crack + " for seed " + seed);
            }
            // And the loop has to close: the last gap wraps onto the first crack a turn on.
            assertTrue(angles[0] + TWO_PI > angles[angles.length - 1],
                    "the fracture overlapped itself where it closed, seed " + seed);
        }
    }

    @Test
    void theRingsSpanThePaneFromTheImpactToTheRim() {
        for (int seed : seeds()) {
            float[] radii = RiftShatter.ringRadii(seed);
            assertEquals(RiftShatter.RINGS + 1, radii.length);
            assertEquals(0.0F, radii[0], "the fracture must start at the impact");
            assertEquals(1.0F, radii[RiftShatter.RINGS], "the fracture must reach the rim");
            for (int ring = 1; ring < radii.length; ring++) {
                assertTrue(radii[ring] > radii[ring - 1],
                        "fracture rings out of order at " + ring + " for seed " + seed);
            }
        }
    }

    /**
     * The property the whole class exists to keep: the pieces tile the pane.
     *
     * <p>Every ring's wedges must add up to exactly one turn and every ring must start where the last
     * one ended. A shortfall is a missing shard, an overshoot is two shards in the same place, and
     * neither is something you would spot by looking at one aperture.
     */
    @Test
    void theShardsTileTheWholePane() {
        for (int seed : seeds()) {
            RiftShatter.Shard[] shards = RiftShatter.fracture(seed);
            assertEquals(RiftShatter.SHARDS, shards.length);

            for (int ring = 0; ring < RiftShatter.RINGS; ring++) {
                float sweep = 0.0F;
                float inner = shards[ring * RiftShatter.CRACKS].innerT();
                float outer = shards[ring * RiftShatter.CRACKS].outerT();
                for (int crack = 0; crack < RiftShatter.CRACKS; crack++) {
                    RiftShatter.Shard shard = shards[ring * RiftShatter.CRACKS + crack];
                    sweep += shard.angle1() - shard.angle0();
                    assertEquals(inner, shard.innerT(), 1.0e-6F, "ragged inner edge on ring " + ring);
                    assertEquals(outer, shard.outerT(), 1.0e-6F, "ragged outer edge on ring " + ring);
                }
                assertEquals(TWO_PI, sweep, 1.0e-4F,
                        "ring " + ring + " did not close for seed " + seed);
            }

            // Consecutive rings meet exactly, so there is no annulus of pane left unbroken.
            for (int ring = 1; ring < RiftShatter.RINGS; ring++) {
                assertEquals(shards[(ring - 1) * RiftShatter.CRACKS].outerT(),
                        shards[ring * RiftShatter.CRACKS].innerT(), 1.0e-6F,
                        "a gap between rings " + (ring - 1) + " and " + ring);
            }
        }
    }

    @Test
    void everyShardHasArea() {
        for (int seed : seeds()) {
            for (RiftShatter.Shard shard : RiftShatter.fracture(seed)) {
                assertTrue(shard.angle1() > shard.angle0(), "a shard with no width, seed " + seed);
                assertTrue(shard.outerT() > shard.innerT(), "a shard with no depth, seed " + seed);
                assertTrue(shard.innerT() >= 0.0F && shard.outerT() <= 1.0F,
                        "a shard outside the pane it was cut from, seed " + seed);
            }
        }
    }

    // ------------------------------------------------------------ repeatable

    @Test
    void oneRiftAlwaysBreaksTheSameWay() {
        // Nothing about the fracture goes over the wire, so two players watching one aperture only
        // see the same glass break because this is a pure function of where the rift is.
        int seed = RiftShatter.seedFor(184.5D, 91.0D, -2048.25D);
        RiftShatter.Shard[] first = RiftShatter.fracture(seed);
        RiftShatter.Shard[] second = RiftShatter.fracture(seed);
        for (int i = 0; i < first.length; i++) {
            assertEquals(first[i], second[i], "the same rift broke differently at shard " + i);
        }
    }

    @Test
    void twoRiftsBreakDifferently() {
        // The entry and exit apertures of one jump are metres apart and must not be twins.
        assertNotEquals(RiftShatter.seedFor(0.0D, 80.0D, 0.0D), RiftShatter.seedFor(0.0D, 80.0D, 6.0D));
        RiftShatter.Shard[] here = RiftShatter.fracture(RiftShatter.seedFor(0.0D, 80.0D, 0.0D));
        RiftShatter.Shard[] there = RiftShatter.fracture(RiftShatter.seedFor(0.0D, 80.0D, 6.0D));
        boolean differs = false;
        for (int i = 0; i < here.length && !differs; i++) {
            differs = !here[i].equals(there[i]);
        }
        assertTrue(differs, "two different rifts produced an identical fracture");
    }

    // ---------------------------------------------------------------- timing

    /**
     * Nothing opens while the pane is only cracking.
     *
     * <p>The cracks are drawn over an intact view, so if the hole grew during that phase there would
     * be a hole with a fracture pattern painted over it - and, worse, an aperture partway open at a
     * moment nothing has told it to be.
     */
    @Test
    void thePaneIsWholeUntilItBreaks() {
        for (int step = 0; step <= 100; step++) {
            float progress = step / 100.0F * RiftShatter.CRACK_PHASE;
            assertEquals(0.0F, RiftShatter.hole(progress), "the hole opened while still cracking");
        }
        assertEquals(1.0F, RiftShatter.hole(1.0F), 1.0e-5F, "the hole never finished opening");
    }

    @Test
    void theHoleOnlyEverOpens() {
        float previous = 0.0F;
        for (int step = 0; step <= 200; step++) {
            float hole = RiftShatter.hole(step / 200.0F);
            assertTrue(hole >= previous - 1.0e-6F, "the hole shrank partway through opening");
            assertTrue(hole >= 0.0F && hole <= 1.0F, "the hole left its range: " + hole);
            previous = hole;
        }
    }

    /**
     * Every piece is loose before the opening is over.
     *
     * <p>The drift afterwards is meant to outlast the opening - that is the long quiet tail. What
     * would look wrong is the reverse: a hole standing fully open with parts of it still sitting in
     * place, waiting their turn to fall.
     */
    @Test
    void thePaneIsFullyBrokenByTheTimeTheHoleIsOpen() {
        float shatterTicks = 20.0F * (1.0F - RiftShatter.CRACK_PHASE);
        for (int seed : seeds()) {
            for (RiftShatter.Shard shard : RiftShatter.fracture(seed)) {
                assertTrue(shard.delay() >= 0.0F, "a shard broke before the pane did");
                assertTrue(shard.delay() <= shatterTicks,
                        "a shard was still attached after the hole finished opening: " + shard.delay());
            }
        }
    }

    @Test
    void theBreakRunsOutwardsFromTheImpact() {
        RiftShatter.Shard[] shards = RiftShatter.fracture(RiftShatter.seedFor(7.0D, 64.0D, 9.0D));
        // Ring order is inner to outer, so the first cell of each ring breaks later than the last.
        for (int ring = 1; ring < RiftShatter.RINGS; ring++) {
            assertTrue(shards[ring * RiftShatter.CRACKS].delay()
                            > shards[(ring - 1) * RiftShatter.CRACKS].delay(),
                    "ring " + ring + " did not break after the one inside it");
        }
    }

    // ----------------------------------------------------------------- drift

    @Test
    void aShardIsInvisibleBeforeItBreaksAndAfterItHasGone() {
        assertEquals(0.0F, RiftShatter.fade(-0.5F));
        assertEquals(0.0F, RiftShatter.fade(0.0F));
        assertEquals(0.0F, RiftShatter.fade(1.0F));
        assertEquals(0.0F, RiftShatter.fade(2.0F));
        for (int step = 1; step < 100; step++) {
            float fade = RiftShatter.fade(step / 100.0F);
            assertTrue(fade > 0.0F && fade <= 1.0F, "a shard left its visible range: " + fade);
        }
    }

    @Test
    void aShardCoastsRatherThanAccelerating() {
        assertEquals(0.0F, RiftShatter.travel(0.0F));
        assertEquals(1.0F, RiftShatter.travel(1.0F), 1.0e-6F);
        float previousStep = Float.MAX_VALUE;
        float previous = 0.0F;
        for (int step = 1; step <= 50; step++) {
            float travel = RiftShatter.travel(step / 50.0F);
            float delta = travel - previous;
            assertTrue(delta > 0.0F, "a shard stopped moving at step " + step);
            assertTrue(delta <= previousStep + 1.0e-6F, "a shard sped up at step " + step);
            previousStep = delta;
            previous = travel;
        }
    }

    // ------------------------------------------------------------------ seal

    @Test
    void aReturningShardIsInvisibleBeforeAndAfterItsReturn() {
        assertEquals(0.0F, RiftShatter.sealFade(-0.2F));
        assertEquals(0.0F, RiftShatter.sealFade(0.0F));
        assertEquals(0.0F, RiftShatter.sealFade(1.0F), "a shard was still lit once it was home");
        assertEquals(0.0F, RiftShatter.sealFade(1.5F));
        for (int step = 1; step < 100; step++) {
            float fade = RiftShatter.sealFade(step / 100.0F);
            assertTrue(fade > 0.0F && fade <= 1.0F, "a returning shard left its visible range: " + fade);
        }
    }

    /**
     * The hole shuts down to a point rather than fading out evenly.
     *
     * <p>Space closes from the rim inwards, which is the opposite order to the way it broke. At any
     * moment during a seal the outermost pieces have to be further along their return than the
     * innermost, or the whole thing collapses uniformly and there is nothing to watch.
     */
    @Test
    void theRimComesHomeBeforeTheMiddle() {
        for (int step = 1; step < 100; step++) {
            float progress = step / 100.0F;
            float rim = RiftShatter.sealLife(progress, 1.0F);
            float middle = RiftShatter.sealLife(progress, 0.0F);
            assertTrue(rim > middle, "the middle sealed before the rim at " + progress);
        }
    }

    @Test
    void theWholePaneIsBackByTheEndOfTheSeal() {
        // Including the piece that waits longest. A shard still on its way in when the aperture has
        // gone is a shard left hanging in front of solid space.
        for (float mid = 0.0F; mid <= 1.0F; mid += 0.05F) {
            assertTrue(RiftShatter.sealLife(1.0F, mid) >= 1.0F,
                    "a shard at " + mid + " had not come home when the seal finished");
        }
        // And nothing has started before the seal does.
        for (float mid = 0.0F; mid <= 1.0F; mid += 0.05F) {
            assertTrue(RiftShatter.sealLife(0.0F, mid) <= 0.0F,
                    "a shard at " + mid + " started returning before the aperture began to close");
        }
    }

    @Test
    void theCracksRunOutAndStayOut() {
        assertEquals(0.0F, RiftShatter.crackReach(0.0F));
        assertEquals(1.0F, RiftShatter.crackReach(1.0F), 1.0e-6F);
        assertEquals(1.0F, RiftShatter.crackReach(3.0F), 1.0e-6F, "the cracks retreated after running");
    }
}
