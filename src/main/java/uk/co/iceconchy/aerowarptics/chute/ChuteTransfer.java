package uk.co.iceconchy.aerowarptics.chute;

/**
 * How many items a chute may send this instant, and what it costs.
 *
 * <p>Deliberately free of Minecraft, in the same way {@code AWLayout} and {@code AWAnim} are: no
 * {@code ItemStack}, no {@code IItemHandler}, nothing that needs a game running. The block entity
 * turns the answer into an actual item move; everything that decides <em>whether</em> and
 * <em>how many</em> lives here so it can be tested directly.
 *
 * <p>That split is not tidiness. The failure this guards against is an item being duplicated or lost
 * while crossing between two sub-levels, and the only way to test for conservation is to be able to
 * run the arithmetic thousands of times without a server. A bug here is silent: nobody notices two
 * extra ingots, and nobody can prove where the missing one went.
 */
public final class ChuteTransfer {

    private ChuteTransfer() {
    }

    /**
     * What a chute should do with one attempted transfer.
     *
     * @param count   how many items to move; zero means the transfer does not happen at all
     * @param essence millibuckets to spend, which is zero exactly when {@code count} is zero
     * @param reason  why nothing is moving, or {@link Reason#READY} when something is
     */
    public record Plan(int count, int essence, Reason reason) {

        public boolean moves() {
            return count > 0;
        }
    }

    /** Why a transfer is not happening. Each maps to a line the panel can show. */
    public enum Reason {
        /** Something is moving. */
        READY,
        /** Nothing in the buffer to send. */
        EMPTY,
        /** No partner chute bound, or the partner no longer exists. */
        UNBOUND,
        /** The partner is in chunks nobody has loaded, so there is nothing to hand items to. */
        PARTNER_ABSENT,
        /** The far side has no room. Items back up here rather than being destroyed. */
        PARTNER_FULL,
        /** Not enough Rift Essence to hold the rift open for even one item. */
        NO_ESSENCE,
        /** The ship this chute is bolted to is in the middle of a warp. */
        WARPING
    }

    /**
     * Works out the transfer.
     *
     * <p>The essence cost is per item rather than per operation, so a chute moving a stack of sixty-
     * four pays sixty-four times. That is what stops a single expensive rift being amortised into
     * free bulk logistics, and it is why the count is clamped by what the buffer can actually pay for
     * rather than by what it holds.
     *
     * <p>Order matters in one place: {@link Reason#EMPTY} is checked before anything else, because a
     * chute with nothing in it is idle rather than broken and should not report a fault. A player
     * watching an empty chute say "not enough Rift Essence" would go and fix the wrong thing.
     *
     * @param available     items waiting in the sending buffer
     * @param room          how many the receiving side will accept right now
     * @param essence       millibuckets held by the sending chute
     * @param costPerItem   millibuckets each item costs to send; zero makes transfers free
     * @param batchLimit    most items one transfer may move, however much of everything there is
     * @param bound         whether a partner is bound at all
     * @param partnerLoaded whether the partner's block entity is actually reachable
     * @param warping       whether either end is aboard a hull mid-warp
     */
    public static Plan plan(int available, int room, int essence, int costPerItem, int batchLimit,
                            boolean bound, boolean partnerLoaded, boolean warping) {
        if (available <= 0) {
            return nothing(Reason.EMPTY);
        }
        if (!bound) {
            return nothing(Reason.UNBOUND);
        }
        if (warping) {
            // Not a failure. A hull inside the fold is not somewhere an item can be handed to, and
            // the buffer holding onto its contents until the ship arrives is the whole point.
            return nothing(Reason.WARPING);
        }
        if (!partnerLoaded) {
            return nothing(Reason.PARTNER_ABSENT);
        }
        if (room <= 0) {
            return nothing(Reason.PARTNER_FULL);
        }

        int affordable = costPerItem <= 0 ? Integer.MAX_VALUE : essence / costPerItem;
        if (affordable <= 0) {
            return nothing(Reason.NO_ESSENCE);
        }

        int count = Math.min(Math.min(available, room), Math.min(affordable, Math.max(1, batchLimit)));
        if (count <= 0) {
            return nothing(Reason.EMPTY);
        }
        // Multiplied in int space on purpose: count is bounded by batchLimit and the cost by config,
        // so this cannot overflow at any setting the config permits.
        return new Plan(count, costPerItem <= 0 ? 0 : count * costPerItem, Reason.READY);
    }

    private static Plan nothing(Reason reason) {
        return new Plan(0, 0, reason);
    }

    /**
     * Whether a chute holding this much essence can hold its rift open.
     *
     * <p>An idle chute spends nothing, so this is the only thing standing between "the rift is
     * visibly there" and "the rift has collapsed". It asks whether one more item <em>could</em> be
     * sent, not whether one is waiting - a rift that winked out whenever the belt went quiet would
     * read as a fault rather than as an idle machine.
     */
    public static boolean riftOpen(int essence, int costPerItem) {
        return costPerItem <= 0 || essence >= costPerItem;
    }
}
