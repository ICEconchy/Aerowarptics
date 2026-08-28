package uk.co.iceconchy.aerowarptics.fissure;

/**
 * How much a Rift Fissure holds, and how fast it gives it up.
 *
 * <p>Free of Minecraft so it can be tested directly, like {@code WarpCost} and {@code AWLayout}
 * before it. The arithmetic here is small but the failure modes are all silent ones: a fissure that
 * hands out more than it holds mints essence out of nothing, one that hands out less than it takes
 * away destroys it, and a reservoir rolled from the wrong seed gives two players standing at the same
 * tear two different answers about how big it is.
 */
public final class FissureDrain {

    private FissureDrain() {
    }

    /**
     * How much essence the fissure at a position holds, in millibuckets.
     *
     * <p>Rolled from the position rather than from the level's random, so it is a property of the
     * place. That matters more than it sounds: the block entity is written by worldgen with nothing in
     * it, and this is what both the server and every client can work out for themselves before a
     * single tick has run, without a packet and without the answer changing if the chunk is unloaded
     * and read back before anything has drawn from it.
     *
     * @param seed  the fissure's position, packed
     * @param least fewest millibuckets a fissure may hold
     * @param most  most it may hold; a range of one is allowed and gives a fixed size
     */
    public static int reservoir(long seed, int least, int most) {
        if (most <= least) {
            return Math.max(0, least);
        }
        // SplitMix64's finaliser. Chosen because block positions are wildly non-random - three small
        // numbers packed into one long - and a weaker mix leaves fissures in a line all much of a
        // size, which is exactly the sort of thing nobody notices until a screenshot of four of them.
        long mixed = seed + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        mixed ^= mixed >>> 31;
        int span = most - least;
        return least + (int) Math.floorMod(mixed, span);
    }

    /**
     * What moves this tick.
     *
     * <p>Bounded three ways, and every one of them matters: never more than is left in the tear,
     * never more than the vessel has room for, and never more than the rate allows.
     *
     * @param reservoir what the fissure has left
     * @param room      what the siphon can still accept
     * @param rate      millibuckets a tick this fissure gives up
     * @return millibuckets to move, never negative
     */
    public static int transfer(int reservoir, int room, int rate) {
        return Math.max(0, Math.min(Math.min(reservoir, room), rate));
    }

    /**
     * How open the tear should look, 0 to 1.
     *
     * <p>A draining fissure closes visibly rather than vanishing at the end, so a player watching one
     * can tell how much longer it has. Never quite reaches zero while anything is left, because a rift
     * drawn at no size at all reads as one that has already gone.
     */
    public static float openness(int reservoir, int original) {
        if (original <= 0) {
            return 0.0F;
        }
        float left = Math.max(0, Math.min(reservoir, original)) / (float) original;
        return reservoir <= 0 ? 0.0F : 0.25F + 0.75F * left;
    }
}
