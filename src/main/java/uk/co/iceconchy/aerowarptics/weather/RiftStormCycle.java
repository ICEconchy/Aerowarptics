package uk.co.iceconchy.aerowarptics.weather;

import java.util.function.IntSupplier;

/**
 * When a Rift Storm is raging, and when the next one is due.
 *
 * <p>Deliberately free of Minecraft, like {@link uk.co.iceconchy.aerowarptics.warp.ScatterOffset}: two
 * counters and the rules for moving them, so every edge of the cycle - the first tick of a new world,
 * a storm commanded over the top of another, a frozen weather cycle - can be tested without a server.
 * {@link RiftStorm} is the part that persists it and tells the players.
 *
 * <p>The shape is vanilla's own weather clock. Calm counts down to a storm, the storm counts down to
 * calm, and each end rolls a fresh length for the other. A calm of zero means "not yet rolled" rather
 * than "due now", which is what stops a brand new world opening with a storm on its very first tick.
 */
public final class RiftStormCycle {

    /** What a tick did, so the caller knows whether there is anything to announce. */
    public enum Change {
        NONE,
        BEGAN,
        ENDED
    }

    private int stormTicks;
    private int calmTicks;

    public RiftStormCycle() {
        this(0, 0);
    }

    /**
     * @param stormTicks ticks left of a storm in progress, or zero for none
     * @param calmTicks  ticks until the next natural storm, or zero when not yet rolled
     */
    public RiftStormCycle(int stormTicks, int calmTicks) {
        this.stormTicks = Math.max(0, stormTicks);
        this.calmTicks = Math.max(0, calmTicks);
    }

    /**
     * Moves the clock on one tick.
     *
     * @param advance     whether weather moves at all - vanilla's {@code doWeatherCycle}. Off freezes
     *                    both counters, a storm in progress included, exactly as it freezes the rain
     * @param natural     whether storms may arrive on their own. Off still lets a commanded storm run
     *                    out; it only stops the calm from ever ending
     * @param calmLength  rolls the length of the calm that follows
     * @param stormLength rolls the length of a storm that arrives naturally
     */
    public Change tick(boolean advance, boolean natural, IntSupplier calmLength, IntSupplier stormLength) {
        if (!advance) {
            return Change.NONE;
        }
        if (stormTicks > 0) {
            if (--stormTicks > 0) {
                return Change.NONE;
            }
            calmTicks = Math.max(1, calmLength.getAsInt());
            return Change.ENDED;
        }
        if (!natural) {
            return Change.NONE;
        }
        if (calmTicks <= 0) {
            calmTicks = Math.max(1, calmLength.getAsInt());
            return Change.NONE;
        }
        if (--calmTicks > 0) {
            return Change.NONE;
        }
        stormTicks = Math.max(1, stormLength.getAsInt());
        return Change.BEGAN;
    }

    /**
     * Starts a storm now, for this long, replacing whatever was left of one already raging.
     *
     * @return {@code true} if this began a storm, {@code false} if it only changed how long one lasts
     */
    public boolean start(int duration) {
        boolean began = stormTicks <= 0;
        stormTicks = Math.max(1, duration);
        return began;
    }

    /**
     * Ends any storm and sets how long the calm after it lasts.
     *
     * @param calm ticks until the next natural storm, or zero or less to roll one on the next tick
     * @return {@code true} if a storm was actually raging
     */
    public boolean stop(int calm) {
        boolean ended = stormTicks > 0;
        stormTicks = 0;
        calmTicks = Math.max(0, calm);
        return ended;
    }

    public boolean raging() {
        return stormTicks > 0;
    }

    public int stormTicks() {
        return stormTicks;
    }

    public int calmTicks() {
        return calmTicks;
    }
}
