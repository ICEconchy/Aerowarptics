package uk.co.iceconchy.aerowarptics.weather;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * What a Stormglass reads, and whether breaking one may call a storm. Free of Minecraft, like
 * {@link RiftStormCycle} beside it, so every reading and every refusal can be tested without a server.
 *
 * <p>The reading is honest because the server knows the calm: a Stormglass is FitzRoy's storm glass with the
 * weather it forecasts actually decided in advance.
 */
public final class StormForecast {

    /** How far ahead "gathering" looks: a storm due within a day. */
    public static final int GATHERING_WINDOW = 24_000;

    /** A Stormglass's four readings, in the order the item's model counts them. */
    public enum Reading implements StringRepresentable {
        /** No storm within a day - or the calm has not been rolled yet, which says nothing is due. */
        CLEAR,
        /** The next storm is due within a day. */
        GATHERING,
        RAGING,
        /** Natural storms are off, or the weather is frozen: none will come on its own. */
        STILL;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Reading byOrdinal(int ordinal) {
            Reading[] all = values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : CLEAR;
        }
    }

    /** Why breaking a Stormglass did or did not call a storm. */
    public enum Call implements StringRepresentable {
        CALLED,
        /** The server has said no: storms may not be called, or come at all. */
        FORBIDDEN,
        /** One is raging already. Calling another would only reset how long this one has left. */
        RAGING,
        /** The weather is frozen, and a called storm would never end. */
        FROZEN,
        /** Too soon after the last one ended. */
        TOO_SOON;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public String translationKey() {
            return "stormglass.call." + getSerializedName();
        }
    }

    private StormForecast() {
    }

    /**
     * @param raging     whether a storm is raging now
     * @param natural    whether storms arrive on their own ({@code riftStorm.naturalStorms})
     * @param cycling    whether the weather moves at all ({@code doWeatherCycle})
     * @param calmTicks  ticks until the next natural storm; zero when not yet rolled
     */
    public static Reading read(boolean raging, boolean natural, boolean cycling, int calmTicks) {
        if (raging) {
            return Reading.RAGING;
        }
        if (!natural || !cycling) {
            return Reading.STILL;
        }
        if (calmTicks > 0 && calmTicks <= GATHERING_WINDOW) {
            return Reading.GATHERING;
        }
        return Reading.CLEAR;
    }

    /**
     * Whether breaking a set Stormglass calls a storm now.
     *
     * @param callable     {@code riftStorm.callable}
     * @param natural      {@code riftStorm.naturalStorms} - off, the operator chose no storms, and a glass does not
     *                     overrule that
     * @param sinceEnded   ticks since the last storm ended; negative when none has ever ended
     * @param minimumCalm  {@code riftStorm.callMinimumCalmTicks}
     */
    public static Call call(boolean callable, boolean natural, boolean raging, boolean cycling, long sinceEnded,
                            int minimumCalm) {
        if (!callable || !natural) {
            return Call.FORBIDDEN;
        }
        if (raging) {
            return Call.RAGING;
        }
        if (!cycling) {
            return Call.FROZEN;
        }
        if (sinceEnded >= 0 && sinceEnded < minimumCalm) {
            return Call.TOO_SOON;
        }
        return Call.CALLED;
    }
}
