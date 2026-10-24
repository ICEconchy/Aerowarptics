package uk.co.iceconchy.aerowarptics.gate;

/**
 * How far through a gate passage a traveller is, counted on the server's tick.
 *
 * <p>Three stages and no way back through them. {@link Stage#HELD} is the passage itself: the traveller
 * is at the far gate and kept there. {@link Stage#GRACE} is the moment after, while the light is still
 * thinning on their screen: they are free and in the world, and still not to be hit by something they
 * cannot yet see. {@link Stage#OVER} is forgotten.
 *
 * <p>Separate from {@link GatePassages} so that it can be counted without a server. A countdown that runs
 * a tick long or short is not a thing anybody notices by looking, and this codebase has shipped two.
 */
public final class PassageClock {

    public enum Stage {
        HELD,
        GRACE,
        OVER
    }

    private int held;
    private int grace;
    /** The tick a passage begins in is not one of its ticks - see {@link #tick}. */
    private boolean begun;

    /**
     * @param ticks how long the traveller is held for; at least one
     * @param grace how long they are protected for after that
     */
    public PassageClock(int ticks, int grace) {
        this.held = Math.max(1, ticks);
        this.grace = Math.max(0, grace);
    }

    public Stage stage() {
        if (held > 0) {
            return Stage.HELD;
        }
        return grace > 0 ? Stage.GRACE : Stage.OVER;
    }

    /**
     * Counts the end of one server tick.
     *
     * <p>A passage begins part of the way through a tick - a gate notices the crossing on its own tick -
     * and the server's end-of-tick is then called in that same tick. That first call counts nothing, so a
     * passage of sixty ticks ends sixty ticks after the one it began in, which is what its traveller's
     * client was told to expect.
     *
     * @return {@code true} on the one tick the traveller arrives
     */
    public boolean tick() {
        if (!begun) {
            begun = true;
            return false;
        }
        if (held > 0) {
            held--;
            return held == 0;
        }
        if (grace > 0) {
            grace--;
        }
        return false;
    }

    /**
     * Ends the hold now, as though its last tick had just been counted. The grace still runs; whoever
     * calls this does the arriving, since {@link #tick} will not report one that has already happened.
     */
    public void cutShort() {
        begun = true;
        held = 0;
    }
}
