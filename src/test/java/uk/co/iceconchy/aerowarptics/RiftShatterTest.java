package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.client.fx.RiftShatter;
import uk.co.iceconchy.aerowarptics.client.fx.ThemeLook;
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

    /**
     * However lumpy the rings came out, no piece lets go before a piece inside it.
     *
     * <p>Glass orders its break by where each piece really sits rather than by its nominal ring, so
     * this is only true because a ring's wander is bounded well inside the gap to the next. A piece
     * of an outer ring falling first would open a hole in the pane with glass still standing in it.
     */
    @Test
    void noPieceOfGlassLetsGoBeforeOneInsideIt() {
        for (int seed : seeds()) {
            RiftShatter.Shard[] shards = RiftShatter.fracture(seed);
            for (int ring = 1; ring < RiftShatter.RINGS; ring++) {
                float latestInside = 0.0F;
                float earliestHere = Float.MAX_VALUE;
                for (int crack = 0; crack < RiftShatter.CRACKS; crack++) {
                    latestInside = Math.max(latestInside, shards[(ring - 1) * RiftShatter.CRACKS + crack].delay());
                    earliestHere = Math.min(earliestHere, shards[ring * RiftShatter.CRACKS + crack].delay());
                }
                assertTrue(earliestHere > latestInside,
                        "ring " + ring + " began to fall before ring " + (ring - 1) + " had, seed " + seed);
            }
        }
    }

    // ------------------------------------------------------------------- web

    /**
     * Where the cracks cross stays in order however far they wander.
     *
     * <p>Two radials that swapped places at a ring would cut a piece with a twist in it, which draws
     * as a bow tie; two rings that crossed at a crack would cut one with no inside at all.
     */
    @Test
    void theWebStaysInOrderHoweverItWanders() {
        for (RiftShatter.Pattern pattern : allPatterns()) {
            for (int seed : seeds()) {
                RiftShatter.Web web = RiftShatter.web(seed, pattern);
                for (int ring = 1; ring <= web.rings(); ring++) {
                    for (int crack = 0; crack < web.cracks(); crack++) {
                        assertTrue(web.angle(crack + 1, ring) > web.angle(crack, ring),
                                pattern + " crossed cracks " + crack + " and " + (crack + 1)
                                        + " at ring " + ring + ", seed " + seed);
                    }
                }
                for (int crack = 0; crack < web.cracks(); crack++) {
                    assertEquals(0.0F, web.radius(crack, 0), pattern + " had a crack miss the impact");
                    assertEquals(1.0F, web.radius(crack, web.rings()), pattern + " had a crack miss the rim");
                }
                // Not merely each crack's own crossings in order: every crossing of one ring inside
                // every crossing of the next, so no two rings touch anywhere round the pane.
                for (int ring = 1; ring <= web.rings(); ring++) {
                    float outermost = 0.0F;
                    float innermost = Float.MAX_VALUE;
                    for (int crack = 0; crack < web.cracks(); crack++) {
                        outermost = Math.max(outermost, web.radius(crack, ring - 1));
                        innermost = Math.min(innermost, web.radius(crack, ring));
                    }
                    assertTrue(innermost > outermost,
                            pattern + " let ring " + ring + " touch the ring inside it, seed " + seed);
                }
            }
        }
    }

    /**
     * The pieces are cut exactly where the web's cracks cross, and meet each other corner to corner.
     *
     * <p>The nominal cells are proven to tile above, but a piece is drawn from its outline. An outline
     * that disagreed with its neighbour's would open a sliver of empty pane between two pieces the
     * moment they were drawn, or lay two over one another.
     */
    @Test
    void thePiecesMeetCornerToCorner() {
        for (RiftShatter.Pattern pattern : allPatterns()) {
            for (int seed : seeds()) {
                RiftShatter.Shard[] shards = RiftShatter.fracture(seed, pattern);
                int cracks = pattern.cracks();
                int rings = pattern.rings();
                for (int ring = 0; ring < rings; ring++) {
                    for (int crack = 0; crack < cracks; crack++) {
                        RiftShatter.Outline here = shards[ring * cracks + crack].outline();
                        RiftShatter.Outline next = shards[ring * cracks + (crack + 1) % cracks].outline();
                        // The last piece round shares its second crack with the first piece a turn on.
                        float turn = crack + 1 == cracks ? TWO_PI : 0.0F;
                        String where = pattern + " ring " + ring + " crack " + crack + " seed " + seed;
                        assertEquals(here.angle1() - turn, next.angle0(), 1.0e-4F, "inner edge apart, " + where);
                        assertEquals(here.radius1(), next.radius0(), 1.0e-6F, "inner edge apart, " + where);
                        assertEquals(here.angle2() - turn, next.angle3(), 1.0e-4F, "outer edge apart, " + where);
                        assertEquals(here.radius2(), next.radius3(), 1.0e-6F, "outer edge apart, " + where);
                        if (ring + 1 < rings) {
                            RiftShatter.Outline above = shards[(ring + 1) * cracks + crack].outline();
                            assertEquals(here.angle3(), above.angle0(), 1.0e-6F, "a gap outside, " + where);
                            assertEquals(here.radius3(), above.radius0(), 1.0e-6F, "a gap outside, " + where);
                            assertEquals(here.angle2(), above.angle1(), 1.0e-6F, "a gap outside, " + where);
                            assertEquals(here.radius2(), above.radius1(), 1.0e-6F, "a gap outside, " + where);
                        }
                        if (ring == 0) {
                            assertEquals(0.0F, here.radius0(), "an innermost piece clear of the impact, " + where);
                            assertEquals(0.0F, here.radius1(), "an innermost piece clear of the impact, " + where);
                        }
                        if (ring + 1 == rings) {
                            assertEquals(1.0F, here.radius2(), "an outermost piece short of the rim, " + where);
                            assertEquals(1.0F, here.radius3(), "an outermost piece short of the rim, " + where);
                        }
                    }
                }
            }
        }
    }

    /**
     * Every piece of glass is a real shape, and is drawn as one.
     *
     * <p>An outline runs out along its inner edge and back along its outer one, which is clockwise, so
     * every piece has a negative area taken over its corners in that order. A quad whose corners
     * wandered far enough to go through itself would be a bow tie, with no way to draw it at all.
     *
     * <p>A piece can also be a real shape and still be drawn wrong. A quad goes down as two triangles
     * either side of the diagonal from its first corner, and where a crack bends hard, one corner of a
     * piece can be pushed in far enough that only one diagonal lies inside it. Split along the other,
     * one triangle comes out inside out and folds over the outside of the piece - which the web does
     * produce, so {@link RiftShatter.Outline#firstCorner} picks the diagonal, and this holds it to it.
     */
    @Test
    void everyPieceOfGlassIsARealShapeAndIsDrawnAsOne() {
        int bent = 0;
        for (int seed : seeds()) {
            for (RiftShatter.Shard shard : RiftShatter.fracture(seed)) {
                RiftShatter.Outline outline = shard.outline();
                double area = 0.0D;
                for (int corner = 0; corner < 4; corner++) {
                    int next = (corner + 1) % 4;
                    area += outline.u(corner) * outline.v(next) - outline.u(next) * outline.v(corner);
                }
                assertTrue(area < 0.0D, "a piece of glass with no inside, seed " + seed + ": " + outline);
                int first = outline.firstCorner();
                bent += first;
                // A triangle may have no area only for a piece from the middle, whose two inner
                // corners are both on the impact - the piece's other half is the whole of it.
                double one = outline.triangle(first, first + 1, first + 2);
                double two = outline.triangle(first, first + 2, first + 3);
                assertTrue(one < 0.0D || (one == 0.0D && outline.radius0() == 0.0F),
                        "a piece of glass that folds over itself when drawn, seed " + seed + ": " + outline);
                assertTrue(two < 0.0D || (two == 0.0D && outline.radius0() == 0.0F),
                        "a piece of glass that folds over itself when drawn, seed " + seed + ": " + outline);
            }
        }
        // Every piece of a plain grid is convex, so without a bent one the check above would say
        // nothing about the choice of diagonal.
        assertTrue(bent > 0, "no piece of glass needed the other diagonal, so the choice went untested");
    }

    /**
     * Glass really is cut off the grid, and nothing else is.
     *
     * <p>Two halves of one claim. Without the first, {@link #thePiecesMeetCornerToCorner} would pass
     * just as happily for a pane that had quietly gone back to being a wheel of wedges. Without the
     * second, a planar motion - which sweeps a piece as the polar wedge it nominally is - would draw a
     * different shape from the one it was cut as.
     */
    @Test
    void onlyGlassIsCutOffTheGrid() {
        for (RiftShatter.Pattern pattern : allPatterns()) {
            if (pattern.motion() != RiftShatter.Motion.TUMBLE) {
                assertEquals(0.0F, pattern.wander(), pattern + " sweeps polar wedges but was given wander");
            }
            for (int seed : seeds()) {
                int moved = 0;
                RiftShatter.Shard[] shards = RiftShatter.fracture(seed, pattern);
                for (RiftShatter.Shard shard : shards) {
                    RiftShatter.Outline outline = shard.outline();
                    boolean onGrid = outline.angle0() == shard.angle0() && outline.angle3() == shard.angle0()
                            && outline.angle1() == shard.angle1() && outline.angle2() == shard.angle1()
                            && outline.radius0() == shard.innerT() && outline.radius1() == shard.innerT()
                            && outline.radius2() == shard.outerT() && outline.radius3() == shard.outerT();
                    if (pattern.wander() <= 0.0F) {
                        assertTrue(onGrid, pattern + " was cut off its own grid, seed " + seed);
                    } else if (!onGrid) {
                        moved++;
                    }
                }
                if (pattern.wander() > 0.0F) {
                    assertTrue(moved > shards.length / 2,
                            pattern + " barely wandered: " + moved + " of " + shards.length + ", seed " + seed);
                }
            }
        }
    }

    /**
     * The whole web is drawn before the pane goes.
     *
     * <p>A crack still running when the pieces start to move would be a line being drawn across a
     * hole, and a cell not yet cut free would be a piece falling out of glass that never broke round it.
     */
    @Test
    void theWholeWebIsDrawnBeforeThePaneBreaks() {
        for (int seed : seeds()) {
            RiftShatter.Web web = RiftShatter.web(seed, RiftShatter.Pattern.GLASS);
            for (int crack = 0; crack < web.cracks(); crack++) {
                assertEquals(0.0F, web.radialReach(crack, 0.0F), "a crack already running at the blow");
                assertEquals(1.0F, web.radialReach(crack, 1.0F), 1.0e-6F, "a crack short of the rim at the break");
                float previous = 0.0F;
                for (int step = 0; step <= 100; step++) {
                    float reach = web.radialReach(crack, step / 100.0F);
                    assertTrue(reach >= previous, "a crack ran backwards, seed " + seed);
                    previous = reach;
                }
                for (int ring = 1; ring < web.rings(); ring++) {
                    assertEquals(0.0F, web.chordReach(crack, ring, 0.0F), "a ring crack there at the blow");
                    assertEquals(1.0F, web.chordReach(crack, ring, 1.0F), 1.0e-6F,
                            "a ring crack unfinished at the break, seed " + seed);
                }
                for (int ring = 0; ring < web.rings(); ring++) {
                    assertEquals(1.0F, web.cellFormed(crack, ring, 1.0F), 1.0e-6F,
                            "a cell not cut free by the break, seed " + seed);
                }
            }
            for (RiftShatter.Branch branch : web.branches()) {
                assertEquals(0.0F, branch.reach(0.0F), "a branch there at the blow");
                assertEquals(1.0F, branch.reach(1.0F), 1.0e-6F, "a branch unfinished at the break, seed " + seed);
            }
        }
    }

    /**
     * A crack only ever runs from a crack that is already there.
     *
     * <p>A ring crack that started before both of the radials it bridges had reached it would be a
     * line appearing in unbroken glass, and a branch that grew before its parent passed would be a
     * twig with no tree.
     */
    @Test
    void aCrackOnlyRunsFromACrackAlreadyThere() {
        for (int seed : seeds()) {
            RiftShatter.Web web = RiftShatter.web(seed, RiftShatter.Pattern.GLASS);
            for (int step = 0; step <= 200; step++) {
                float progress = step / 200.0F;
                for (int crack = 0; crack < web.cracks(); crack++) {
                    for (int ring = 1; ring < web.rings(); ring++) {
                        if (web.chordReach(crack, ring, progress) <= 0.0F) {
                            continue;
                        }
                        assertTrue(web.radialReach(crack, progress) >= web.nodeAlong(crack, ring)
                                        && web.radialReach(crack + 1, progress) >= web.nodeAlong(crack + 1, ring),
                                "ring crack " + ring + " from crack " + crack + " started in unbroken glass, seed "
                                        + seed);
                    }
                }
                for (RiftShatter.Branch branch : web.branches()) {
                    if (branch.reach(progress) > 0.0F) {
                        assertTrue(web.radialReach(branch.crack(), progress) >= branch.along(),
                                "a branch grew before its crack got there, seed " + seed);
                    }
                }
            }
        }
    }

    /** Ring cracks form from the impact outwards, each ring wholly after the one inside it. */
    @Test
    void theRingCracksFormFromTheImpactOutwards() {
        for (int seed : seeds()) {
            RiftShatter.Web web = RiftShatter.web(seed, RiftShatter.Pattern.GLASS);
            for (int ring = 2; ring < web.rings(); ring++) {
                float latestInside = 0.0F;
                float earliestHere = Float.MAX_VALUE;
                for (int crack = 0; crack < web.cracks(); crack++) {
                    latestInside = Math.max(latestInside, web.chordStart(crack, ring - 1));
                    earliestHere = Math.min(earliestHere, web.chordStart(crack, ring));
                }
                assertTrue(earliestHere > latestInside,
                        "ring " + ring + " began cracking before ring " + (ring - 1) + ", seed " + seed);
            }
        }
    }

    /**
     * A branch leaves from its own crack and runs out inside the pane.
     *
     * <p>One that started beside its crack would be a stray scratch; one that ran past the rim would be
     * a crack drawn across open air.
     */
    @Test
    void aBranchLeavesItsCrackAndStaysInThePane() {
        int total = 0;
        for (int seed : seeds()) {
            RiftShatter.Web web = RiftShatter.web(seed, RiftShatter.Pattern.GLASS);
            for (RiftShatter.Branch branch : web.branches()) {
                total++;
                boolean onPath = false;
                for (int point = 0; point < web.pathPoints(); point++) {
                    onPath |= web.pathU(branch.crack(), point) == branch.u0()
                            && web.pathV(branch.crack(), point) == branch.v0()
                            && web.pathAlong(branch.crack(), point) == branch.along();
                }
                assertTrue(onPath, "a branch that does not leave from its crack, seed " + seed);
                assertTrue(Math.hypot(branch.u1(), branch.v1()) <= 1.0D, "a branch past the rim, seed " + seed);
                assertTrue(Math.hypot(branch.u1() - branch.u0(), branch.v1() - branch.v0()) > 0.0D,
                        "a branch of no length, seed " + seed);
                assertTrue(branch.end() > branch.start(), "a branch that never grows, seed " + seed);
            }
            assertTrue(RiftShatter.web(seed, RiftShatter.Pattern.GEARS).branches().isEmpty(),
                    "a precise pattern threw off a branch");
        }
        // A web that never branched would pass every check above, so make sure the checks had
        // something to look at.
        assertTrue(total > seeds().length * 4, "glass barely branches: " + total + " over all seeds");
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
                RiftShatter.Pattern.EMBERS, RiftShatter.Pattern.MOTES,
                RiftShatter.Pattern.STREAKS, RiftShatter.Pattern.FOLD, RiftShatter.Pattern.LENS,
                RiftShatter.Pattern.TARTAN, RiftShatter.Pattern.GRAVITY, RiftShatter.Pattern.STAGGER,
                RiftShatter.Pattern.CHAOS, RiftShatter.Pattern.BLINKS, RiftShatter.Pattern.PAGE,
                RiftShatter.Pattern.VEIL);
    }

    /**
     * The list above is every pattern except glass.
     *
     * <p>Written down rather than derived, because {@link #namedPatterns()} is what most of the
     * invariants below actually run over - so a pattern added to {@code ALL} and forgotten here would
     * quietly not be tested at all, which is the one failure this file could have that nothing else
     * would catch.
     */
    @Test
    void everyPatternExceptGlassIsUnderTest() {
        assertEquals(allPatterns().size() - 1, namedPatterns().size(),
                "a pattern was added to Pattern.ALL without being added to namedPatterns()");
        for (RiftShatter.Pattern pattern : allPatterns()) {
            if (pattern == RiftShatter.Pattern.GLASS) {
                continue;
            }
            assertTrue(namedPatterns().contains(pattern), pattern + " is not under test");
        }
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
     * Every theme actually moves differently.
     *
     * <p>This is the regression this test file most wants to hold. The fracture shape alone is nearly
     * invisible at these sizes - cells this small, seen for a second, all look much alike - so a theme
     * that shares another's {@link RiftShatter.Motion} reads as the same animation in a different
     * colour however differently it was cut up. Two patterns landing on the same motion is therefore a
     * bug even though nothing about it would crash, fail to compile, or look wrong in a still.
     *
     * <p>Bar one motion, which is not a motion: a pane that never breaks has no pieces, so there is no
     * animation for two of them to share. The themes on it are told apart by everything else they are -
     * see {@link #onlyAThemeMadeOfSomethingElseOpensUnbroken}.
     */
    @Test
    void everyPatternMovesItsOwnWay() {
        Set<RiftShatter.Motion> motions = new HashSet<>();
        int unbroken = 0;
        for (RiftShatter.Pattern pattern : allPatterns()) {
            if (pattern.motion() == RiftShatter.Motion.UNBROKEN) {
                unbroken++;
                continue;
            }
            assertTrue(motions.add(pattern.motion()),
                    pattern + " moves the same way as another pattern, so it will read as that one");
        }
        assertEquals(allPatterns().size() - unbroken, motions.size());
    }

    /**
     * A pane that never breaks is only for a theme whose face is something else entirely - a drawing, a
     * cloud - and that arrives some other way.
     *
     * <p>An ordinary lit rift landing on one would simply appear: no fracture, no pieces, nothing to show
     * it arriving, and nothing about that would throw.
     */
    @Test
    void onlyAThemeMadeOfSomethingElseOpensUnbroken() {
        for (RiftModulatorTheme theme : RiftModulatorTheme.values()) {
            if (RiftShatter.patternFor(theme).motion() == RiftShatter.Motion.UNBROKEN) {
                assertNotEquals(ThemeLook.Surface.LIT, ThemeLook.surface(theme),
                        theme + " opens unbroken with nothing else to show it arriving");
            }
        }
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

    /**
     * A simultaneous pattern really does go all at once.
     *
     * <p>Worth pinning because it is the one sequencing whose correct behaviour is indistinguishable
     * from a bug at a glance: every other one spreads its pieces out, so "all the delays are zero"
     * looks exactly like a delay calculation that was never wired up. It is deliberate here, and a
     * jump to lightspeed staggering by even a tick or two would stop reading as one event.
     */
    @Test
    void aSimultaneousPatternHasNoStaggerAtAll() {
        for (RiftShatter.Pattern pattern : allPatterns()) {
            if (pattern.sequencing() != RiftShatter.Sequencing.SIMULTANEOUS) {
                continue;
            }
            for (int seed : seeds()) {
                for (RiftShatter.Shard shard : RiftShatter.fracture(seed, pattern)) {
                    assertEquals(0.0F, shard.delay(),
                            pattern + " staggered a piece that should have gone with the rest");
                }
            }
        }
    }

    /**
     * Every theme in the mod has a shape under test, reached from the theme rather than the pattern.
     *
     * <p>{@link #everyThemeBreaksItsOwnWay} already proves the map is injective. This proves it is
     * <em>total</em> in the sense that matters: the pattern a theme lands on is one this file actually
     * exercises, so adding a theme cannot smuggle in an untested fracture.
     */
    @Test
    void everyThemesPatternIsUnderTest() {
        for (RiftModulatorTheme theme : RiftModulatorTheme.values()) {
            RiftShatter.Pattern pattern = RiftShatter.patternFor(theme);
            assertTrue(pattern == RiftShatter.Pattern.GLASS || namedPatterns().contains(pattern),
                    theme + " breaks along a pattern no test covers");
        }
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
