package uk.co.iceconchy.aerowarptics.wellhead;

/**
 * What a comparator reads off a Rift Wellhead: its strain, and one reading kept for trouble.
 *
 * <p>Fifteen means exactly "critical or worse". A circuit built to scram a well waits for it, so a reading that
 * showed fifteen a little early would scram a well that was only running hot, and one that showed it late would
 * be no use at all - the same bargain {@code DriveSignal} keeps with fourteen. Everything under critical is
 * spread over one to fourteen, and nought is a well with no strain on it at all, which is also an unformed one.
 */
public final class WellheadSignal {

    /** The reading kept for a well at or past {@link WellheadReactor#CRITICAL}. */
    public static final int ALARM = 15;

    private WellheadSignal() {
    }

    public static int of(boolean formed, double strain) {
        if (!formed || strain <= 0.0D) {
            return 0;
        }
        if (strain >= WellheadReactor.CRITICAL) {
            return ALARM;
        }
        return Math.min(ALARM - 1, 1 + (int) Math.floor(strain / WellheadReactor.CRITICAL * (ALARM - 1)));
    }
}
