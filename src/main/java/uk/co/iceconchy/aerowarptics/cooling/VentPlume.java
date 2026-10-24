package uk.co.iceconchy.aerowarptics.cooling;

/**
 * The steam a Cooling Vent forces out after a warp, as a curve over the ticks since the drive began to cool.
 *
 * <p>Three parts, and they join. The warp ends and the vent blows: a hard jet through the louvres for
 * {@link #BLAST_TICKS}. Then the pressure falls away over {@link #EASE_TICKS}, the jet thinning to a
 * drift. After that the vent only breathes - a wisp now and then for as long as the drive is still cooling,
 * so a glance across the deck says whether it has finished.
 *
 * <p>Pure, so the joins can be tested: a curve that jumps between two of its parts is a plume that
 * visibly cuts out. The client asks this each tick and draws what it says; nothing here knows what a
 * particle is.
 */
public final class VentPlume {

    /** Ticks of full blast as the drive starts to cool. */
    public static final int BLAST_TICKS = 30;

    /** Ticks the blast takes to fall away to a breath. */
    public static final int EASE_TICKS = 50;

    /** How hard a vent breathes once the blast is spent, against a blast of one. */
    public static final double BREATH = 0.12D;

    /** Jets a tick at full blast, before the player's particle density. */
    public static final double BLAST_JETS = 3.0D;

    /** How fast the steam leaves the louvres at full blast, in blocks a tick. */
    public static final double BLAST_SPEED = 0.24D;

    /** How fast a breath drifts out, in blocks a tick. */
    public static final double BREATH_SPEED = 0.05D;

    private VentPlume() {
    }

    /**
     * How hard the vent is blowing: one at full blast, {@link #BREATH} once it has settled, nought before
     * the drive begins to cool.
     *
     * @param ticks ticks since the drive began to cool, from nought
     */
    public static double strength(int ticks) {
        if (ticks < 0) {
            return 0.0D;
        }
        if (ticks < BLAST_TICKS) {
            return 1.0D;
        }
        int easing = ticks - BLAST_TICKS;
        if (easing >= EASE_TICKS) {
            return BREATH;
        }
        // Eased out rather than straight: pressure lost through an opening drops fastest at first.
        double left = 1.0D - easing / (double) EASE_TICKS;
        return BREATH + (1.0D - BREATH) * left * left;
    }

    /** Jets to throw this tick, on average - a fraction is a chance. */
    public static double jets(int ticks, double density) {
        return BLAST_JETS * strength(ticks) * Math.max(0.0D, density);
    }

    /** How fast this tick's steam leaves the vent, in blocks a tick. */
    public static double speed(int ticks) {
        double strength = strength(ticks);
        return BREATH_SPEED + (BLAST_SPEED - BREATH_SPEED) * (strength - BREATH) / (1.0D - BREATH);
    }

    /** Whether the steam is still under pressure - a jet - or has slackened to drifting cloud. */
    public static boolean isJet(int ticks) {
        return ticks >= 0 && ticks < BLAST_TICKS + EASE_TICKS / 2;
    }

    /**
     * Where a vent that was not watching when its drive began to cool picks the plume up: past the blast.
     *
     * <p>A vent a player walks up to half-way through a cooldown has not just had a warp end beside it,
     * and blowing as though it had would be a blast two minutes late.
     */
    public static int settled() {
        return BLAST_TICKS + EASE_TICKS;
    }
}
