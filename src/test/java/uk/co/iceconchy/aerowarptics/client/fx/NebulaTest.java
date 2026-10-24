package uk.co.iceconchy.aerowarptics.client.fx;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cloud a Mostly Dangerous rift arrives in.
 *
 * <p>Every way this goes wrong draws something. A gap between the hole and the cloud is a ring of
 * daylight round a black disc; a cloud still arriving when the hole opens is a black disc on its own; a
 * collapse that has not finished when the rift is dropped is a cloud that vanishes; and flashes that go
 * off after the hull is out light up a sky with nothing in it.
 */
class NebulaTest {

    private static final int[] SEEDS = {0, 1, 7, 42, 1_234, -99_999, 0x5EED, 0x7FFF_FFFF};

    /**
     * No gap between the hole and the cloud, however the cloud has turned.
     *
     * <p>From just inside the rim to a good way past where the torn edge can wander to, every direction
     * is dark: sampled all the way round, over a long stretch of churn, for many rifts. Further out than
     * that the cloud is allowed to thin, because that is its billowing edge.
     */
    @Test
    void thereIsNoDaylightBetweenTheHoleAndTheCloud() {
        float innermost = 0.95F;
        float outermost = 1.0F + RiftTear.RAG + 0.25F;
        for (int seed : SEEDS) {
            Nebula.Puff[] puffs = Nebula.puffs(seed);
            float least = 1.0F;
            for (float time = 0.0F; time < 600.0F; time += 13.0F) {
                for (int step = 0; step < 120; step++) {
                    double angle = step * Math.PI * 2.0D / 120.0D;
                    for (float out = innermost; out <= outermost; out += 0.05F) {
                        float dark = Nebula.opacity(puffs, (float) Math.cos(angle) * out,
                                (float) Math.sin(angle) * out, time, 1.0F, 0.0F);
                        least = Math.min(least, dark);
                    }
                }
            }
            assertTrue(least >= 0.8F, "seed " + seed + " let the sky through round the hole: " + least);
        }
    }

    /** The edge billows rather than ending: well clear of the hole the cloud thins out to nothing. */
    @Test
    void theEdgeFadesOutRatherThanStopping() {
        for (int seed : SEEDS) {
            Nebula.Puff[] puffs = Nebula.puffs(seed);
            assertEquals(0.0F, Nebula.opacity(puffs, Nebula.REACH + 0.01F, 0.0F, 0.0F, 1.0F, 0.0F), 1.0e-6F,
                    "the cloud reaches past where it says it does");
            float thinnest = 1.0F;
            for (int step = 0; step < 90; step++) {
                double angle = step * Math.PI * 2.0D / 90.0D;
                float out = Nebula.REACH * 0.9F;
                thinnest = Math.min(thinnest, Nebula.opacity(puffs, (float) Math.cos(angle) * out,
                        (float) Math.sin(angle) * out, 0.0F, 1.0F, 0.0F));
            }
            assertTrue(thinnest < 0.5F, "the cloud's edge is still solid near its reach: " + thinnest);
        }
    }

    /**
     * Nothing ever reaches past {@link Nebula#REACH}, which the lens and the lightning are both sized
     * from - checked on the puffs as they actually move, not on the arithmetic that promised it.
     */
    @Test
    void theCloudStaysInsideItsReach() {
        for (int seed : SEEDS) {
            for (Nebula.Puff puff : Nebula.puffs(seed)) {
                for (float time = 0.0F; time < 800.0F; time += 3.7F) {
                    Nebula.Placed placed = Nebula.place(puff, time, 1.0F, 0.0F);
                    float out = (float) Math.hypot(placed.u(), placed.v()) + placed.size();
                    assertTrue(out <= Nebula.REACH + 1.0e-4F, "a puff reached " + out + " past " + Nebula.REACH);
                }
            }
        }
    }

    /** The inner cloud turns faster than the outer, which is the difference between a vortex and a plate. */
    @Test
    void theMiddleTurnsFasterThanTheEdge() {
        Nebula.Puff[] puffs = Nebula.puffs(3);
        Nebula.Puff inner = puffs[0];
        Nebula.Puff outer = puffs[puffs.length - 1];
        assertTrue(inner.radius() < outer.radius(), "the puffs are no longer laid out inner ring first");
        assertTrue(inner.spin() > outer.spin() * 1.5F, "the cloud turns as one plate");
        assertTrue(outer.spin() > 0.0F, "the edge of the cloud stands still");
    }

    /** The whole cloud is laid out, once each, and every rift's is its own. */
    @Test
    void everyRiftGathersItsOwnCloud() {
        assertEquals(Nebula.PUFFS, Nebula.puffs(5).length);
        Set<Float> angles = new HashSet<>();
        for (int seed : SEEDS) {
            angles.add(Nebula.puffs(seed)[0].angle());
        }
        assertTrue(angles.size() > SEEDS.length / 2, "different rifts gathered the same cloud");
        Nebula.Puff[] once = Nebula.puffs(77);
        Nebula.Puff[] again = Nebula.puffs(77);
        for (int puff = 0; puff < once.length; puff++) {
            assertEquals(once[puff], again[puff], "the same rift gathered a different cloud");
        }
    }

    /**
     * Out of nothing, and there before the hole is.
     *
     * <p>The face starts to open at {@link RiftShatter#CRACK_PHASE} of the opening. A cloud still
     * arriving by then would leave the black face growing out past it as a hard-edged disc.
     */
    @Test
    void theCloudArrivesFromNothingBeforeTheHoleOpens() {
        assertEquals(0.0F, Nebula.gathered(0.0F), 1.0e-6F, "the cloud was already there");
        assertTrue(Nebula.gathered(RiftShatter.CRACK_PHASE) >= 0.95F,
                "the cloud is still arriving when the hole opens: " + Nebula.gathered(RiftShatter.CRACK_PHASE));
        assertEquals(1.0F, Nebula.gathered(1.0F), 1.0e-6F, "the cloud never finished arriving");
        float last = -1.0F;
        for (float progress = 0.0F; progress <= 1.0F; progress += 0.001F) {
            float now = Nebula.gathered(progress);
            assertTrue(now >= last, "the cloud shrank while it was arriving");
            assertTrue(now - last < 0.02F || last < 0.0F, "the cloud jumped while it was arriving");
            last = now;
        }
        for (Nebula.Puff puff : Nebula.puffs(9)) {
            assertEquals(0.0F, Nebula.place(puff, 0.0F, 0.0F, 0.0F).alpha(), 1.0e-6F,
                    "a puff was dark before the cloud had arrived");
        }
    }

    /**
     * Collapsed well inside the close, and completely - so the rift being dropped at the end of it takes
     * nothing with it that was still on screen.
     */
    @Test
    void theCollapseIsOverLongBeforeTheCloseIs() {
        assertTrue(Nebula.COLLAPSE_TO <= 0.5F, "the collapse takes most of the close - it is meant to be violent");
        assertEquals(0.0F, Nebula.collapse(0.0F), 1.0e-6F);
        assertEquals(1.0F, Nebula.collapse(Nebula.COLLAPSE_TO), 1.0e-6F, "the collapse is not finished on time");
        assertEquals(0.0F, Nebula.shrink(1.0F), 1.0e-6F, "a collapsed cloud still has a hole in it");
        for (Nebula.Puff puff : Nebula.puffs(11)) {
            Nebula.Placed placed = Nebula.place(puff, 50.0F, 1.0F, Nebula.collapse(Nebula.COLLAPSE_TO));
            assertEquals(0.0F, placed.size(), 1.0e-6F, "a puff outlived the collapse");
        }
        float last = 0.0F;
        for (float seal = 0.0F; seal <= 1.0F; seal += 0.001F) {
            float now = Nebula.collapse(seal);
            assertTrue(now >= last, "the collapse went back out");
            last = now;
        }
    }

    /**
     * As it falls in, the cloud stays over the hole it is falling into.
     *
     * <p>The hole shrinks on the same curve as the cloud is pulled in, and the puffs shrink slower than
     * they are pulled - so at every point of the collapse the rim of the hole is still under cloud.
     */
    @Test
    void theCloudCoversTheHoleAllTheWayDown() {
        for (int seed : SEEDS) {
            Nebula.Puff[] puffs = Nebula.puffs(seed);
            for (float seal = 0.0F; seal < Nebula.COLLAPSE_TO * 0.95F; seal += Nebula.COLLAPSE_TO / 20.0F) {
                float collapse = Nebula.collapse(seal);
                float rim = Nebula.shrink(collapse) * (1.0F + RiftTear.RAG);
                for (int step = 0; step < 72; step++) {
                    double angle = step * Math.PI * 2.0D / 72.0D;
                    float dark = Nebula.opacity(puffs, (float) Math.cos(angle) * rim, (float) Math.sin(angle) * rim,
                            120.0F, 1.0F, collapse);
                    assertTrue(dark >= 0.7F, "seed " + seed + " bared the hole's edge at " + seal + " of the close: " + dark);
                }
            }
        }
    }

    /**
     * The flashes that light the hull go off while it is coming out, each a flash of its own, and each is
     * heard and felt exactly once.
     */
    @Test
    void theHullIsLitWhileItIsComingOut() {
        float[] moments = Nebula.emergenceMoments();
        assertTrue(moments.length >= 3, "a flash or two is not lightning");
        for (int flash = 0; flash < moments.length; flash++) {
            assertTrue(moments[flash] >= 0.0F && moments[flash] + Nebula.EMERGENCE_FLASH < 60.0F,
                    "a flash goes off when no hull is coming out: " + moments[flash]);
            if (flash > 0) {
                assertTrue(moments[flash] - moments[flash - 1] > Nebula.EMERGENCE_FLASH,
                        "two flashes run into one another");
            }
        }
        float from = 213.0F;
        int heard = 0;
        for (int tick = 200; tick < 300; tick++) {
            heard += Nebula.emergenceStarts(tick, tick + 1, from);
        }
        assertEquals(moments.length, heard, "a flash was heard twice, or not at all");
        assertEquals(0, Nebula.emergenceStarts(0.0F, 1_000.0F, -1.0F), "a rift nothing came out of flashed");
        assertEquals(0.0F, Nebula.emergence(from - 0.5F, from), 1.0e-6F, "it flashed before the hull arrived");
        assertEquals(1.0F, Nebula.emergence(from, from), 1.0e-6F, "the first flash is not a snap");
        assertEquals(0.0F, Nebula.emergence(from + 200.0F, from), 1.0e-6F, "it was still flashing long after");
    }

    /** The flash the collapse ends on is at its brightest as the last of it arrives in the middle. */
    @Test
    void theCollapseEndsOnAFlash() {
        assertEquals(1.0F, Nebula.pop(Nebula.COLLAPSE_TO), 1.0e-6F);
        assertEquals(0.0F, Nebula.pop(0.0F), 1.0e-6F, "the collapse flashed before it started");
        assertEquals(0.0F, Nebula.pop(0.95F), 1.0e-6F, "the flash lingers to the end of the close");
    }

    /**
     * The trails neither appear nor vanish: nothing at either end of the close, and no step anywhere in
     * between.
     */
    @Test
    void theTrailsComeUpAndFadeSmoothly() {
        assertEquals(0.0F, Nebula.afterglow(0.0F), 1.0e-6F);
        assertEquals(0.0F, Nebula.afterglow(1.0F), 1.0e-6F);
        float last = 0.0F;
        for (float seal = 0.0F; seal <= 1.0F; seal += 0.0005F) {
            float now = Nebula.afterglow(seal);
            assertTrue(Math.abs(now - last) < 0.02F, "the trails jumped at " + seal);
            last = now;
        }
        for (Nebula.Trail trail : Nebula.trails(21)) {
            assertTrue(Math.hypot(trail.u(), trail.v()) < 0.8F, "a trail hangs outside where the ship came through");
            assertTrue(trail.length() > 1.0F, "a trail too short to see");
        }
    }

    /** Every arc is one of the three colours, and all three turn up. */
    @Test
    void theArcsComeInAllThreeColours() {
        Set<Integer> seen = new HashSet<>();
        for (int cycle = 0; cycle < 200; cycle++) {
            int colour = Nebula.arcColour(4_242, cycle % 12, cycle);
            assertTrue(colour >= 0 && colour <= 2, "an arc asked for a fourth colour");
            seen.add(colour);
        }
        assertEquals(3, seen.size(), "one of the three colours never turns up");
    }
}
