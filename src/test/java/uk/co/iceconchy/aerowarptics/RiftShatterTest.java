package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.client.fx.RiftShatter;
import uk.co.iceconchy.aerowarptics.modulator.RiftModulatorTheme;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    // --------------------------------------------------------------- patterns

    /**
     * A Rift Modulator's other two shapes, held to the same tiling and timing invariants as the
     * default {@link RiftShatter.Pattern#GLASS} above - a gear or a rune circle that left a gap or an
     * overlap would be exactly as wrong as a pane of glass that did.
     */
    private static List<RiftShatter.Pattern> namedPatterns() {
        return List.of(RiftShatter.Pattern.GEARS, RiftShatter.Pattern.RUNES,
                RiftShatter.Pattern.EMBERS, RiftShatter.Pattern.MOTES);
    }

    /** Every pattern including glass, taken from the canonical list so this cannot go stale. */
    private static List<RiftShatter.Pattern> allPatterns() {
        return RiftShatter.Pattern.ALL;
    }

    @Test
    void everyPatternTilesItsWholePane() {
        for (RiftShatter.Pattern pattern : namedPatterns()) {
            for (int seed : seeds()) {
                RiftShatter.Shard[] shards = RiftShatter.fracture(seed, pattern);
                assertEquals(pattern.cracks() * pattern.rings(), shards.length);

                for (int ring = 0; ring < pattern.rings(); ring++) {
                    float sweep = 0.0F;
                    float inner = shards[ring * pattern.cracks()].innerT();
                    float outer = shards[ring * pattern.cracks()].outerT();
                    for (int crack = 0; crack < pattern.cracks(); crack++) {
                        RiftShatter.Shard shard = shards[ring * pattern.cracks() + crack];
                        sweep += shard.angle1() - shard.angle0();
                        assertEquals(inner, shard.innerT(), 1.0e-6F,
                                "ragged inner edge on ring " + ring + " of " + pattern);
                        assertEquals(outer, shard.outerT(), 1.0e-6F,
                                "ragged outer edge on ring " + ring + " of " + pattern);
                    }
                    assertEquals(TWO_PI, sweep, 1.0e-4F,
                            "ring " + ring + " of " + pattern + " did not close for seed " + seed);
                }

                for (int ring = 1; ring < pattern.rings(); ring++) {
                    assertEquals(shards[(ring - 1) * pattern.cracks()].outerT(),
                            shards[ring * pattern.cracks()].innerT(), 1.0e-6F,
                            "a gap between rings " + (ring - 1) + " and " + ring + " of " + pattern);
                }
            }
        }
    }

    @Test
    void everyPatternsShardHasArea() {
        for (RiftShatter.Pattern pattern : namedPatterns()) {
            for (int seed : seeds()) {
                for (RiftShatter.Shard shard : RiftShatter.fracture(seed, pattern)) {
                    assertTrue(shard.angle1() > shard.angle0(), "a shard with no width, " + pattern);
                    assertTrue(shard.outerT() > shard.innerT(), "a shard with no depth, " + pattern);
                    assertTrue(shard.innerT() >= 0.0F && shard.outerT() <= 1.0F,
                            "a shard outside the pane it was cut from, " + pattern);
                }
            }
        }
    }

    @Test
    void everyPatternBreaksTheSameWayTwice() {
        for (RiftShatter.Pattern pattern : namedPatterns()) {
            int seed = RiftShatter.seedFor(184.5D, 91.0D, -2048.25D);
            RiftShatter.Shard[] first = RiftShatter.fracture(seed, pattern);
            RiftShatter.Shard[] second = RiftShatter.fracture(seed, pattern);
            assertEquals(first.length, second.length);
            for (int i = 0; i < first.length; i++) {
                assertEquals(first[i], second[i], pattern + " broke differently at shard " + i);
            }
        }
    }

    /**
     * Every piece of every pattern is loose before the opening is over, exactly the bound {@link
     * #thePaneIsFullyBrokenByTheTimeTheHoleIsOpen} holds glass to - a Modulator's choice of theme must
     * never leave a hole standing open with a tooth still attached.
     *
     * <p>Worth having per-pattern rather than only for glass because the sequencing differs: a ratchet
     * spends its budget going round the rim and an inward sweep spends it coming in from the edge, so
     * each has its own way of running over.
     */
    @Test
    void everyPatternStaysInsideTheShatterBudget() {
        float shatterTicks = 20.0F * (1.0F - RiftShatter.CRACK_PHASE);
        for (RiftShatter.Pattern pattern : allPatterns()) {
            for (int seed : seeds()) {
                for (RiftShatter.Shard shard : RiftShatter.fracture(seed, pattern)) {
                    assertTrue(shard.delay() >= 0.0F, "a piece of " + pattern + " broke before the pane did");
                    assertTrue(shard.delay() <= shatterTicks,
                            "a piece of " + pattern + " was still attached after the hole finished "
                                    + "opening: " + shard.delay());
                }
            }
        }
    }

    /**
     * A piece that does not travel must not outstay one that does.
     *
     * <p>{@code lifeScale} is what keeps a motionless pattern honest. A rune that never moves but
     * lingers the full {@link RiftShatter#SHARD_LIFE} is a coloured disc sitting over the opening it
     * was supposed to have got out of the way of - which is not a subtle bug, but is exactly the sort
     * that only shows up in a running game.
     */
    @Test
    void aPatternThatDoesNotTravelDoesNotLinger() {
        for (RiftShatter.Pattern pattern : namedPatterns()) {
            assertTrue(pattern.lifeScale() > 0.0F && pattern.lifeScale() <= 1.0F,
                    pattern + " has a nonsensical life scale: " + pattern.lifeScale());
            if (pattern.motion() == RiftShatter.Motion.DISSOLVE) {
                assertTrue(pattern.lifeScale() <= 0.5F,
                        "a pattern whose pieces never move must clear quickly, but " + pattern
                                + " lingers for " + pattern.lifeScale() + " of a full shard life");
            }
        }
        assertEquals(1.0F, RiftShatter.Pattern.GLASS.lifeScale(), "glass keeps its long drifting tail");
    }

    /**
     * The three themes actually move differently.
     *
     * <p>This is the regression this test file most wants to hold. The fracture shape alone is nearly
     * invisible at these sizes - cells this small, seen for a second, all look much alike - so a theme
     * that shares another's {@link RiftShatter.Motion} reads as the same animation in a different
     * colour however differently it was cut up. Two patterns landing on the same motion is therefore a
     * bug even though nothing about it would crash, fail to compile, or look wrong in a still.
     */
    @Test
    void everyPatternMovesItsOwnWay() {
        Set<RiftShatter.Motion> motions = new HashSet<>();
        for (RiftShatter.Pattern pattern : allPatterns()) {
            assertTrue(motions.add(pattern.motion()),
                    pattern + " moves the same way as another pattern, so it will read as that one");
        }
        assertEquals(allPatterns().size(), motions.size());
    }

    /**
     * Every theme has a shape, and no two themes share one.
     *
     * <p>Sequencing deliberately is <em>not</em> required to be unique - glass and embers both spread
     * outwards from where they started, and that is simply true of both. Motion is the field that has
     * to be distinct, so this checks the map rather than the fields: two themes landing on one pattern
     * would make them the same animation whatever their fields said.
     */
    @Test
    void everyThemeBreaksItsOwnWay() {
        Set<RiftShatter.Pattern> claimed = new HashSet<>();
        for (RiftModulatorTheme theme : RiftModulatorTheme.values()) {
            RiftShatter.Pattern pattern = RiftShatter.patternFor(theme);
            assertTrue(allPatterns().contains(pattern),
                    theme + " maps to a pattern that is missing from Pattern.ALL");
            assertTrue(claimed.add(pattern), theme + " breaks the same way as another theme");
        }
        assertEquals(RiftModulatorTheme.values().length, claimed.size());
    }

    @Test
    void everyPatternsCracksRunAllTheWayRoundInOrder() {
        for (RiftShatter.Pattern pattern : allPatterns()) {
            for (int seed : seeds()) {
                float[] angles = RiftShatter.crackAngles(seed, pattern);
                assertEquals(pattern.cracks(), angles.length);
                for (int crack = 1; crack < angles.length; crack++) {
                    assertTrue(angles[crack] > angles[crack - 1],
                            pattern + " put its cracks out of order at " + crack + ", seed " + seed);
                }
                assertTrue(angles[0] + TWO_PI > angles[angles.length - 1],
                        pattern + " overlapped itself where it closed, seed " + seed);
            }
        }
    }

    /** A motion that keeps its pieces in the plane must not also be throwing them through it. */
    @Test
    void aPlanarMotionNeverLeavesTheAperturesPlane() {
        for (RiftShatter.Pattern pattern : namedPatterns()) {
            if (pattern.motion() == RiftShatter.Motion.TUMBLE) {
                continue;
            }
            assertEquals(0.0F, pattern.pushVariance(),
                    pattern + " keeps its pieces in the plane but was given a push through it");
            for (int seed : seeds()) {
                for (RiftShatter.Shard shard : RiftShatter.fracture(seed, pattern)) {
                    assertEquals(0.0F, shard.push(),
                            "a piece of " + pattern + " was thrown out of the aperture's plane");
                }
            }
        }
    }

    /**
     * Locks in the claim {@code RiftShatter}'s class doc makes: the parameter-less overloads are
     * exactly {@link RiftShatter.Pattern#GLASS}, not merely something that looks like it.
     */
    @Test
    void thePatternlessOverloadsAreExactlyGlass() {
        for (int seed : seeds()) {
            assertEquals(java.util.Arrays.toString(RiftShatter.crackAngles(seed)),
                    java.util.Arrays.toString(RiftShatter.crackAngles(seed, RiftShatter.Pattern.GLASS)));
            assertEquals(java.util.Arrays.toString(RiftShatter.ringRadii(seed)),
                    java.util.Arrays.toString(RiftShatter.ringRadii(seed, RiftShatter.Pattern.GLASS)));
            assertArrayEquals(RiftShatter.fracture(seed), RiftShatter.fracture(seed, RiftShatter.Pattern.GLASS));
        }
    }

    private static void assertArrayEquals(RiftShatter.Shard[] expected, RiftShatter.Shard[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], "shard " + i + " differs");
        }
    }
}
