package uk.co.iceconchy.aerowarptics.warp;

import uk.co.iceconchy.aerowarptics.AWConfig;

/**
 * How far above its destination a ship comes in.
 *
 * <p>This used to be one server-wide figure, {@code arrivalGroundBuffer}, and no single figure is
 * right everywhere: six blocks suits a meadow, but a harbour wants a ship low enough to step off and
 * an anchor on a peak wants a hull well clear of the ridges round it. So the height now belongs to
 * the destination. A Warp Anchor carries its own, a Rift Probe hands one to the drive along with its
 * fix, and the server setting is only where a new one starts.
 *
 * <p>It is where the arrival search <em>starts</em>, never a promise. {@link ArrivalSearch} only ever
 * climbs from here, so a column that is blocked at the chosen height still means "come in higher"
 * rather than "come in inside the hill", and asking for a height of zero puts the keel on top of the
 * block rather than through it.
 *
 * <p>The height is measured the way the buffer always was: from the top of the target block - the
 * anchor itself, or the surface a sounding found - to the underside of the hull.
 */
public final class ArrivalHeight {

    private ArrivalHeight() {
    }

    /**
     * What the server default reads as when the config cannot be asked. Matches the shipped
     * {@code arrivalGroundBuffer}, so a unit test sees the same number a fresh server would.
     */
    public static final int FALLBACK = 6;

    /** The ceiling when the config cannot be asked, matching the shipped {@code maxArrivalHeight}. */
    public static final int FALLBACK_MAXIMUM = 128;

    /**
     * A height nobody has chosen yet.
     *
     * <p>Kept distinct from any real height so a block entity can tell "never set" from "set to the
     * default", and resolve the first to whatever the server says at the moment it matters rather
     * than whatever the constructor happened to read - which on a client is nothing at all.
     */
    public static final int UNSET = -1;

    /** The height a new anchor or probe starts at: {@code arrivalGroundBuffer}, to the nearest block. */
    public static int serverDefault() {
        try {
            return clamp((int) Math.round(AWConfig.ARRIVAL_GROUND_BUFFER.get()), maximum());
        } catch (IllegalStateException e) {
            return FALLBACK; // config not loaded - unit tests
        }
    }

    /** The highest height a player may ask for, from {@code maxArrivalHeight}. */
    public static int maximum() {
        try {
            return Math.max(0, AWConfig.MAX_ARRIVAL_HEIGHT.get());
        } catch (IllegalStateException e) {
            return FALLBACK_MAXIMUM;
        }
    }

    /**
     * A requested height brought inside what is allowed.
     *
     * <p>Negative is refused rather than honoured: the search starts from this point and never goes
     * below it, so a negative height would start it inside the block it is meant to clear.
     */
    public static int clamp(int requested, int maximum) {
        return Math.max(0, Math.min(requested, Math.max(0, maximum)));
    }

    /**
     * The height a warp will actually use.
     *
     * <p>Clamped again when the flight is planned, not only when the height is set, because a server
     * can lower {@code maxArrivalHeight} after an anchor was set higher - and an {@link #UNSET} height
     * is resolved to the default here, so nothing downstream has to know the sentinel exists.
     */
    public static int resolve(int requested) {
        return requested < 0 ? serverDefault() : clamp(requested, maximum());
    }

    /**
     * The lowest the hull's underside can arrive at over a target block of this height.
     *
     * <p>What a panel can honestly promise: the ship comes in at this height or, if something is in
     * the way there, somewhere above it.
     */
    public static int lowestUnderside(int targetY, int height) {
        return targetY + 1 + Math.max(0, height);
    }
}
