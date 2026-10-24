package uk.co.iceconchy.aerowarptics.siphon;

/**
 * How much essence one jump sheds, and how it is shared between the vessels aboard.
 *
 * <p>Free of Minecraft, like {@link uk.co.iceconchy.aerowarptics.fissure.FissureDrain} beside it, so the
 * rule that matters can be proved without a server: <strong>a ship catches one draught per jump, however
 * many siphons it carries.</strong> The draught is rolled once and poured into the vessels in turn, each
 * taking what it has room for, exactly as a fissure's drain is shared - more siphons add room, not yield.
 *
 * <p>It used to be the other way round. Each siphon rolled for itself, which the old comment allowed only
 * because "there is no rate to balance yet". Once essence is a price - rifting a lens, folding at a cradle,
 * paying for a collapse - a deck of ten siphons catching ten draughts made the price meaningless.
 *
 * <p>The same pour serves the storm's rain, whose budget is per chunk (or per ship) rather than per jump.
 */
public final class SiphonPool {

    /** Millibuckets every completed jump is worth before distance is considered. */
    public static final int BASE_YIELD = 40;
    public static final int BASE_SPREAD = 160;

    /** Extra millibuckets a jump at {@link #REACH_REFERENCE} blocks can add, on top of the base. */
    public static final int REACH_BONUS = 300;
    public static final double REACH_REFERENCE = 4_000.0D;

    private SiphonPool() {
    }

    /**
     * The draught one jump sheds, from three uniform fractions in {@code [0, 1)}.
     *
     * <p>Taking fractions rather than a random source keeps the roll testable: the lottery is the same
     * lottery the per-siphon roll was - a base, a spread, and a bonus whose ceiling widens with distance -
     * it is simply rolled once.
     *
     * @param distance blocks the hull crossed, used only to widen the odds
     */
    public static int draught(double distance, double spread, double bonus) {
        double reach = Math.max(0.0D, Math.min(1.0D, distance / REACH_REFERENCE));
        int ceiling = 1 + (int) (reach * REACH_BONUS);
        return BASE_YIELD + roll(BASE_SPREAD, spread) + roll(ceiling, bonus);
    }

    /**
     * Pours a draught into vessels in turn, each taking what it has room for.
     *
     * @param draught millibuckets on offer
     * @param rooms   what each vessel can still take, in the order they are filled
     * @return what each vessel took; their sum is never more than the draught, and never more than the
     * room there was
     */
    public static int[] pour(int draught, int[] rooms) {
        int[] took = new int[rooms.length];
        int left = Math.max(0, draught);
        for (int i = 0; i < rooms.length && left > 0; i++) {
            int amount = Math.max(0, Math.min(left, rooms[i]));
            took[i] = amount;
            left -= amount;
        }
        return took;
    }

    private static int roll(int bound, double fraction) {
        if (bound <= 1) {
            return 0;
        }
        double f = Math.max(0.0D, Math.min(1.0D, fraction));
        return Math.min(bound - 1, (int) Math.floor(f * bound));
    }
}
