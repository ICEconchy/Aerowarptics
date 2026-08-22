package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.client.FoldCrossings;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Why a client has to be told that a warp was a teleport.
 *
 * <p>Sable sweeps an entity against a moving sub-level by mapping the entity's box into the
 * sub-level's frame at the previous pose and at the current one and unioning the two. Past a volume
 * of {@link #SABLE_COLLISION_LIMIT} it logs {@code "Enormous local sub-level collision bounds,
 * quitting."} and abandons the collision - which drops the crew through the deck and leaves the hull
 * undrawn, reported as "warping kicks the player off and deletes the airship".
 *
 * <p>A jump of a few thousand blocks puts that union straight over the limit, which is what this
 * pins down. The numbers are from a real warp that failed: a hull moved 4,626 blocks and its union
 * came out at the limit, alongside a 4,221-block one that squeaked under - the pair either side of
 * the line is the whole reason the symptom looked intermittent.
 */
class FoldCrossingTest {

    /** Sable's own sanity limit on a swept collision volume, from {@code SubLevelEntityCollision}. */
    private static final double SABLE_COLLISION_LIMIT = 1.25e8D;

    /** A player's box, which is what actually gets swept. */
    private static final double PLAYER_WIDTH = 0.6D;
    private static final double PLAYER_HEIGHT = 1.8D;

    /**
     * The union of one box mapped through two poses that far apart.
     *
     * <p>An approximation of what Sable computes - it also expands by a little and by the movement
     * vector - but close enough that both of the real warps land within a few percent of the limit,
     * which is the point being made.
     */
    private static double sweptVolume(double dx, double dy, double dz) {
        return (PLAYER_WIDTH + Math.abs(dx)) * (PLAYER_HEIGHT + Math.abs(dy)) * (PLAYER_WIDTH + Math.abs(dz));
    }

    @Test
    void aLongWarpReachesSablesCollisionLimit() {
        // The warp that was reported: (60.84, 213.04, -338.49) -> (329.54, 117.39, -4955.81).
        double volume = sweptVolume(329.54D - 60.84D, 117.39D - 213.04D, -4955.81D + 338.49D);
        assertTrue(volume > SABLE_COLLISION_LIMIT * 0.9D,
                "a 4,626-block warp should be at or past Sable's limit, was " + volume);
    }

    /**
     * And a short one is nowhere near it, which is why this was only ever reported for long jumps.
     *
     * <p>Worth pinning: an implementation that only collapsed the pose on jumps it judged "long
     * enough to matter" would be guessing at where this line falls, and the line moves with the shape
     * of the hull and the direction of travel. Collapsing every jump is what avoids that guess.
     */
    @Test
    void aShortWarpStaysUnderIt() {
        double volume = sweptVolume(120.0D, 20.0D, 90.0D);
        assertTrue(volume < SABLE_COLLISION_LIMIT,
                "a 150-block hop should be nowhere near the limit, was " + volume);
    }

    /**
     * The threshold separates flight from teleportation by a wide margin.
     *
     * <p>An airship under way moves a couple of blocks a tick and the corridor passage is under two,
     * so there is a very large gap between the fastest thing that is really movement and the slowest
     * thing that can only be a jump. The threshold has to sit inside that gap without being anywhere
     * near either edge, or it would either miss crossings or fight ordinary flight.
     */
    @Test
    void onlyATeleportCountsAsAJump() {
        for (double perTick : new double[]{0.0D, 1.0D, 1.58D, 4.0D, 16.0D, 64.0D}) {
            assertFalse(FoldCrossings.isJump(perTick),
                    perTick + " blocks in a tick is flight, not a jump");
        }
        for (double jump : new double[]{256.0D, 1_000.0D, 4_626.0D, 24_000.0D}) {
            assertTrue(FoldCrossings.isJump(jump), jump + " blocks in a tick can only be a teleport");
        }
    }

    /**
     * The watch outlives the race it exists for.
     *
     * <p>The notice and Sable's pose snapshot travel independently, so the notice usually lands
     * before the client believes the ship has moved. A window shorter than that gap would expire
     * before there was anything to collapse, and the bug would come back intermittently - which is
     * the worst way for it to come back.
     */
    @Test
    void theWatchIsLongEnoughToOutlastTheRace() {
        assertTrue(FoldCrossings.watchTicks() >= 40,
                "a watch of " + FoldCrossings.watchTicks() + " ticks may expire before the pose lands");
    }
}
