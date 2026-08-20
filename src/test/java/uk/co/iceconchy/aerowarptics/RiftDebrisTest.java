package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.client.fx.RiftDebris;
import uk.co.iceconchy.aerowarptics.client.fx.RiftShatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What is loose inside a rift's throat.
 *
 * <p>The failures worth guarding here are the ones nobody would see and everybody would feel. A piece
 * that drifted out of range ends up inside the hull, inside the wall, or inside the cone where the
 * bore has already closed - and all three read as the renderer being broken rather than as debris
 * being in the wrong place.
 */
class RiftDebrisTest {

    private static int[] seeds() {
        int[] seeds = new int[32];
        for (int i = 0; i < seeds.length; i++) {
            seeds[i] = RiftShatter.seedFor(i * 7.25D, 64.0D - i, i * 19.5D);
        }
        return seeds;
    }

    // ------------------------------------------------------------ the field

    @Test
    void everyPieceIsInsideTheBoreAndClearOfTheAxis() {
        for (int seed : seeds()) {
            for (RiftDebris.Mote mote : RiftDebris.field(seed)) {
                // The hull flies down the middle: anything inside INNER is inside the ship.
                assertTrue(mote.radius() >= RiftDebris.INNER,
                        "a piece of debris was in the hull's way: " + mote.radius());
                assertTrue(mote.radius() <= RiftDebris.OUTER,
                        "a piece of debris was embedded in the wall: " + mote.radius());
                assertTrue(mote.along() >= 0.0F && mote.along() < 1.0F,
                        "a piece of debris started outside the bore: " + mote.along());
            }
        }
    }

    @Test
    void everyPieceHasASizeItCouldPlausiblyBe() {
        for (int seed : seeds()) {
            for (RiftDebris.Mote mote : RiftDebris.field(seed)) {
                assertTrue(mote.size() > 0.0F, "a piece of debris with no size");
                // A tenth of the bore across is already a large object. Anything near the full width
                // would black out the view rather than pass it.
                assertTrue(mote.size() < 0.10F, "a piece of debris that would fill the bore: " + mote.size());
            }
        }
    }

    @Test
    void theFieldIsAMixture() {
        // If a change to the hash made every piece the same kind, nothing else here would notice.
        int streaks = 0;
        int glass = 0;
        int wreckage = 0;
        for (RiftDebris.Mote mote : RiftDebris.field(RiftShatter.seedFor(12.0D, 80.0D, -40.0D))) {
            if (mote.streak()) {
                streaks++;
            } else if (mote.glass()) {
                glass++;
            } else {
                wreckage++;
            }
        }
        assertTrue(streaks > 0 && streaks < RiftDebris.MOTES / 3,
                "streaks should be a garnish, not the field: " + streaks);
        assertTrue(glass > 0, "no glass in the bore");
        assertTrue(wreckage > 0, "no wreckage in the bore");
    }

    /**
     * The two speeds have to stay two speeds.
     *
     * <p>A drifting piece given a streak's speed crosses the view inside a frame and is never seen; a
     * streak given a drifter's speed is a stationary line hanging in the tunnel. The whole reason the
     * field reads as depth rather than as noise is that almost all of it is nearly still.
     */
    @Test
    void driftersAreSlowAndStreaksAreNot() {
        for (int seed : seeds()) {
            for (RiftDebris.Mote mote : RiftDebris.field(seed)) {
                if (mote.streak()) {
                    assertTrue(mote.drift() < -0.01F,
                            "a streak was not moving against the ship: " + mote.drift());
                } else {
                    assertTrue(Math.abs(mote.drift()) < 0.002F,
                            "a drifting piece was moving like a streak: " + mote.drift());
                }
            }
        }
    }

    @Test
    void aStreakIsAsLongAsItIsFast() {
        RiftDebris.Mote[] motes = RiftDebris.field(RiftShatter.seedFor(3.0D, 70.0D, 3.0D));
        for (RiftDebris.Mote mote : motes) {
            float length = RiftDebris.streakLength(mote);
            assertTrue(length >= 0.0F && length <= 0.22F, "a streak overran the bore: " + length);
        }
        RiftDebris.Mote slower = null;
        RiftDebris.Mote faster = null;
        for (RiftDebris.Mote mote : motes) {
            if (!mote.streak()) {
                continue;
            }
            if (slower == null || mote.drift() > slower.drift()) {
                slower = mote;
            }
            if (faster == null || mote.drift() < faster.drift()) {
                faster = mote;
            }
        }
        assertTrue(slower != null && faster != null, "no streaks to compare");
        assertTrue(RiftDebris.streakLength(faster) > RiftDebris.streakLength(slower),
                "a faster piece did not leave a longer line");
    }

    // ----------------------------------------------------------------- drift

    /**
     * The property that would fail silently.
     *
     * <p>A bore is only open for a few hundred ticks, but a piece that left the range at any point in
     * that window is a piece drawn somewhere it cannot be - past the closing cone, or behind the
     * mouth and out in the open world.
     */
    @Test
    void aPieceNeverLeavesTheBoreHoweverLongItDrifts() {
        for (int seed : seeds()) {
            for (RiftDebris.Mote mote : RiftDebris.field(seed)) {
                for (int tick = 0; tick <= 4_000; tick += 7) {
                    float along = RiftDebris.along(mote, tick);
                    assertTrue(along >= 0.0F && along < 1.0F,
                            "debris left the bore at tick " + tick + ": " + along);
                }
            }
        }
    }

    @Test
    void aPieceWrapsRatherThanStopping() {
        RiftDebris.Mote[] motes = RiftDebris.field(RiftShatter.seedFor(88.0D, 64.0D, 12.0D));
        RiftDebris.Mote streak = null;
        for (RiftDebris.Mote mote : motes) {
            if (mote.streak()) {
                streak = mote;
                break;
            }
        }
        assertTrue(streak != null, "no streak in the field");

        // Far enough for a streak to have run the length of the bore several times over. If it had
        // been clamped it would be sitting at an end, and the tunnel would empty out behind the ship.
        float early = RiftDebris.along(streak, 5.0F);
        float late = RiftDebris.along(streak, 600.0F);
        assertTrue(late >= 0.0F && late < 1.0F);
        assertTrue(Math.abs(late - early) > 1.0e-4F, "a piece stopped moving");
    }

    @Test
    void aPieceMovesTheWayItsDriftSaysItDoes() {
        RiftDebris.Mote[] motes = RiftDebris.field(RiftShatter.seedFor(5.0D, 100.0D, -5.0D));
        for (RiftDebris.Mote mote : motes) {
            if (mote.drift() == 0.0F) {
                continue;
            }
            // Over one tick nothing can wrap, so the sign of the step is the sign of the drift.
            float step = RiftDebris.along(mote, 1.0F) - RiftDebris.along(mote, 0.0F);
            if (Math.abs(step) > 0.5F) {
                continue; // this one happened to wrap; the next tick will not
            }
            assertTrue(Math.signum(step) == Math.signum(mote.drift()),
                    "a piece moved against its own drift");
        }
    }

    // ------------------------------------------------------------ repeatable

    @Test
    void oneBoreAlwaysHasTheSameThingsInIt() {
        // Nothing about the field is sent, so two players in one corridor only see the same debris
        // because it is a pure function of the rift they are inside.
        int seed = RiftShatter.seedFor(410.0D, 88.0D, -96.0D);
        RiftDebris.Mote[] first = RiftDebris.field(seed);
        RiftDebris.Mote[] second = RiftDebris.field(seed);
        assertEquals(RiftDebris.MOTES, first.length);
        for (int i = 0; i < first.length; i++) {
            assertEquals(first[i], second[i], "the same bore was furnished differently at " + i);
        }
    }
}
