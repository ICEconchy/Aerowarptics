package uk.co.iceconchy.aerowarptics.collapse;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Everything the Collapse Cradle's panel shows, as the server sees it - sent to whoever has the panel open, a few
 * times a second, by {@code ClientboundCradlePanelPacket}.
 *
 * <p>Built by {@link CollapseCradleBlockEntity#panel} from the same plans the cradle begins from, so what the
 * panel says Start would do is what Start does, refusal and all. The server works it out because most of it is
 * the server's to know: the recipe the depots match, the stabilisers switched on round it, where the instability
 * is heading, and the config's bands for how a collapse ends.
 *
 * @param mode          what is under way: {@link #IDLE}, {@link #COLLAPSE} or {@link #FOLD}
 * @param next          what Start would begin: {@link #COLLAPSE} or {@link #FOLD}, or {@link #IDLE} for nothing
 * @param failure       why Start would refuse, a {@link CollapseFailure} ordinal - {@code NONE} if it would not
 * @param progress      0..1 through what is under way
 * @param instability   0..1, a collapse's instability so far
 * @param heading       0..1, where it is heading - what the machine and the panel show of how unsteady it is
 * @param held          whether a collapse is standing still, turned too slowly
 * @param essenceNeeded millibuckets what Start would begin burns, or what is under way is burning
 * @param minimumSpeed  the slowest it may be turned for what Start would begin, RPM
 * @param optimalSpeed  the speed that begins it at its steadiest, RPM - the same as the minimum for a fold
 * @param leanX         which way the stabilisers stand lopsided, -1..1 along x
 * @param leanZ         and along z
 * @param settleBelow   the config's band a collapse settles below
 * @param spitAbove     and the one it spits at or above
 * @param offerings     what stands on the depots round it, or what it drew off them, with where each depot is
 * @param result        what Start would make, or what is being made, as it will come out
 * @param lastOutcome   how the last collapse or fold ended, or empty
 */
public record CradlePanel(BlockPos pos, int mode, int next, int failure, float progress, float instability,
                          float heading, boolean held, int essence, int capacity, int essenceNeeded, float speed,
                          float minimumSpeed, float optimalSpeed, int stabilisers, float leanX, float leanZ,
                          float settleBelow, float spitAbove, List<Offering> offerings, ItemStack result,
                          int lastFailure, String lastOutcome) {

    public static final int IDLE = 0;
    public static final int COLLAPSE = 1;
    public static final int FOLD = 2;

    /** The most offerings a panel carries: the most depots a collapse draws from. */
    public static final int MOST_OFFERINGS = 8;

    /** Longest outcome name a panel carries. */
    public static final int OUTCOME_LENGTH = 16;

    /** An offering, and where its depot stands from the cradle's master, in blocks. */
    public record Offering(ItemStack stack, int x, int z) {
    }

    public CradlePanel {
        offerings = List.copyOf(offerings.subList(0, Math.min(MOST_OFFERINGS, offerings.size())));
        lastOutcome = lastOutcome == null ? "" : lastOutcome.substring(0, Math.min(OUTCOME_LENGTH, lastOutcome.length()));
        result = result == null ? ItemStack.EMPTY : result;
    }

    public CollapseFailure failureValue() {
        return byOrdinal(failure);
    }

    public CollapseFailure lastFailureValue() {
        return byOrdinal(lastFailure);
    }

    private static CollapseFailure byOrdinal(int ordinal) {
        CollapseFailure[] all = CollapseFailure.values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : CollapseFailure.NONE;
    }

    /** Whether Start would begin something right now. */
    public boolean ready() {
        return mode == IDLE && next != IDLE && !failureValue().isFailure();
    }
}
