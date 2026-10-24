package uk.co.iceconchy.aerowarptics.sunderer;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The frame unravelling: every burst block stands where it stood until its turn, never jumps, never passes inside
 * the seed until it is swallowed, and is swallowed in its turn, one a beat.
 */
class UnravellingTest {

    private static final Unravelling.Axes ACROSS_X = new Unravelling.Axes(1.0D, 0.0D, 0.0D, 1.0D);
    private static final long SEED = 0x5EEDL;

    /** A 5x5 opening's frame, the burst third of it: home offsets from the middle. */
    private static final double[][] HOMES = {
            {-3.0D, 3.0D, 0.0D}, {-2.0D, 3.0D, 0.0D}, {-1.0D, 3.0D, 0.0D},
            {3.0D, -1.0D, 0.0D}, {3.0D, -2.0D, 0.0D}, {3.0D, -3.0D, 0.0D}, {2.0D, -3.0D, 0.0D}};
    private static final double SIZE = 2.5D;

    private static Unravelling.Piece piece(int index, double tick, boolean hand) {
        int[] ranks = Unravelling.ranks(HOMES.length, SEED);
        double[] home = HOMES[index];
        return Unravelling.at(index, HOMES.length, ranks[index], home[0], home[1], home[2], ACROSS_X, SIZE, SEED, tick, hand);
    }

    /** A block that jumps is a block that teleports. Every block, a hundred samples a tick, the whole scene. */
    @Test
    void noBlockJumps() {
        for (boolean hand : new boolean[]{false, true}) {
            int steps = SundererSequence.length(hand) * 100;
            for (int index = 0; index < HOMES.length; index++) {
                Unravelling.Piece last = piece(index, 0.0D, hand);
                for (int step = 1; step <= steps; step++) {
                    Unravelling.Piece now = piece(index, step / 100.0D, hand);
                    if (!now.present()) {
                        break;
                    }
                    double moved = Math.sqrt(Math.pow(now.x() - last.x(), 2) + Math.pow(now.y() - last.y(), 2)
                            + Math.pow(now.z() - last.z(), 2));
                    assertTrue(moved < 0.05D, "block " + index + " jumped " + moved + " at tick " + step / 100.0D);
                    assertTrue(Math.abs(now.angle() - last.angle()) < 0.05D, "block " + index + " snapped round");
                    assertTrue(Math.abs(now.scale() - last.scale()) < 0.01D, "block " + index + " popped in size");
                    last = now;
                }
            }
        }
    }

    /** Until its turn a block is where it stood, give or take a shudder: nothing is seen to vanish as it fires. */
    @Test
    void aBlockStandsWhereItStoodUntilItsTurn() {
        for (int index = 0; index < HOMES.length; index++) {
            Unravelling.Piece first = piece(index, 0.0D, false);
            assertEquals(HOMES[index][0], first.x(), 1.0e-9D);
            assertEquals(HOMES[index][1], first.y(), 1.0e-9D);
            assertEquals(HOMES[index][2], first.z(), 1.0e-9D);
            assertEquals(1.0D, first.scale(), 1.0e-9D);
            double lifts = SundererSequence.REACHING + Unravelling.liftStart(index, HOMES.length) * SundererSequence.STRAINING;
            for (double tick = 0.0D; tick < lifts; tick += 0.5D) {
                Unravelling.Piece now = piece(index, tick, false);
                assertTrue(Math.abs(now.x() - HOMES[index][0]) < 0.08D && Math.abs(now.y() - HOMES[index][1]) < 0.08D,
                        "block " + index + " left home before its turn, at tick " + tick);
            }
        }
    }

    /** No block is ever inside the seed before it is swallowed, and every one is gone by the breach. */
    @Test
    void theSeedOnlySwallowsInTurn() {
        int[] ranks = Unravelling.ranks(HOMES.length, SEED);
        for (int tick = 0; tick <= SundererSequence.length(false); tick++) {
            double s = SundererChoreography.straining(tick, false);
            double seed = SundererChoreography.seed(tick, false, SIZE, HOMES.length);
            for (int index = 0; index < HOMES.length; index++) {
                Unravelling.Piece now = piece(index, tick, false);
                if (s < Unravelling.swallowStart(ranks[index], HOMES.length) && now.present()) {
                    assertTrue(now.distance() > seed + 0.3D, "block " + index + " inside the seed early, tick " + tick);
                }
            }
        }
        for (int index = 0; index < HOMES.length; index++) {
            assertFalse(piece(index, SundererSequence.breachStart(false), false).present(), "block " + index + " never swallowed");
        }
    }

    /** One a beat: never two blocks gone in the same tick, and each gone at the height of a beat of the seed. */
    @Test
    void theSwallowsAreStaggered() {
        Set<Integer> gone = new HashSet<>();
        for (int index = 0; index < HOMES.length; index++) {
            for (int tick = SundererSequence.REACHING; tick <= SundererSequence.length(false); tick++) {
                if (!piece(index, tick, false).present()) {
                    assertTrue(gone.add(tick), "two blocks swallowed at tick " + tick);
                    break;
                }
            }
        }
        assertEquals(HOMES.length, gone.size());
        for (int rank = 0; rank < HOMES.length; rank++) {
            double at = SundererSequence.REACHING + Unravelling.swallowed(rank, HOMES.length) * SundererSequence.STRAINING;
            assertEquals(1.0D, SundererChoreography.pulse(at, false, HOMES.length), 1.0e-6D,
                    "swallow " + rank + " is not on a beat of the seed");
        }
    }

    /** The orbit never closes inside the seed's reach, at any size of opening a Sunderer tears. */
    @Test
    void theOrbitStaysClearOfTheSeed() {
        for (double size = 1.5D; size <= 3.5D; size += 0.5D) {
            for (double s = 0.0D; s <= 1.0D; s += 0.01D) {
                assertTrue(Unravelling.orbitRadius(size, s) >= SundererChoreography.largestSeed(size) + 0.4D,
                        "the orbit closes on the seed for size " + size);
            }
        }
    }

    @Test
    void theSameSeedUnravelsTheSameWay() {
        assertEquals(piece(3, 400.0D, false), piece(3, 400.0D, false));
        int[] a = Unravelling.ranks(9, 77L);
        int[] b = Unravelling.ranks(9, 77L);
        assertEquals(java.util.Arrays.toString(a), java.util.Arrays.toString(b));
        Set<Integer> places = new HashSet<>();
        for (int rank : a) {
            places.add(rank);
        }
        assertEquals(9, places.size(), "the swallowing order is not a permutation");
    }

    /** A rupture flings a block from wherever it was, and it is gone after a while. */
    @Test
    void aThrownBlockFliesOnFromWhereItWas() {
        Unravelling.Piece from = piece(2, 500.0D, false);
        Unravelling.Piece start = Unravelling.thrown(from, 2, SEED, 0.0D);
        assertEquals(from.x(), start.x(), 1.0e-9D);
        assertEquals(from.y(), start.y(), 1.0e-9D);
        Unravelling.Piece later = Unravelling.thrown(from, 2, SEED, 10.0D);
        assertTrue(later.distance() > from.distance(), "a thrown block came inward");
        assertFalse(Unravelling.thrown(from, 2, SEED, Unravelling.THROWN_FOR + 1.0D).present());
        Unravelling.Piece last = start;
        for (double t = 0.01D; t < Unravelling.THROWN_FOR; t += 0.01D) {
            Unravelling.Piece now = Unravelling.thrown(from, 2, SEED, t);
            assertTrue(Math.abs(now.x() - last.x()) + Math.abs(now.y() - last.y()) < 0.05D, "a thrown block jumped");
            last = now;
        }
    }
}
