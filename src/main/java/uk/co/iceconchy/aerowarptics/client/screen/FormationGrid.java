package uk.co.iceconchy.aerowarptics.client.screen;

/**
 * The Fleet Board's formation grid: blocks of sky to pixels of panel, and back.
 *
 * <p>Top-down with the flagship's bow at the top - starboard to the right, ahead up the panel - which is
 * how a pilot thinks of a formation and how every slot is stored. Free of Minecraft, like the rest of the
 * layout code, so the round trip a dragged marker makes is tested rather than trusted.
 *
 * <h2>Scale</h2>
 * Fitted to the formation: the furthest slot, plus its ship's footprint, sits a margin in from the edge.
 * Never finer than {@link #MIN_REACH} blocks across the half-panel, so a flagship alone with one escort
 * tucked alongside is not drawn as two markers the width of the screen - and fitted to whole
 * {@link #STEP}s of reach, so it does not change on every packet as a ship wanders a block.
 */
public final class FormationGrid {

    /** The least reach the grid shows from the flagship to the edge, in blocks. A whole number of steps. */
    public static final double MIN_REACH = 32.0D;

    /** Reach is rounded up to a multiple of this, so the scale holds still while ships drift. */
    public static final double STEP = 16.0D;

    /** Pixels kept clear inside the panel's edge. */
    public static final int MARGIN = 6;

    private final int left;
    private final int top;
    private final int width;
    private final int height;
    private final double reach;

    /**
     * @param reach blocks from the flagship to the nearer edge of the panel, before fitting to {@link #STEP}
     */
    public FormationGrid(int left, int top, int width, int height, double reach) {
        this.left = left;
        this.top = top;
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.reach = fit(reach);
    }

    /** A reach rounded up to whole steps, and never under the minimum. */
    public static double fit(double reach) {
        double safe = Double.isFinite(reach) ? Math.max(MIN_REACH, reach) : MIN_REACH;
        return Math.ceil(safe / STEP) * STEP;
    }

    public double reach() {
        return reach;
    }

    /** Pixels per block, the same both ways so a circle of sky is drawn round. */
    public double scale() {
        int half = Math.min(width, height) / 2 - MARGIN;
        return Math.max(1, half) / reach;
    }

    public double centreX() {
        return left + width / 2.0D;
    }

    public double centreY() {
        return top + height / 2.0D;
    }

    /** Where a slot is drawn: starboard to the right, ahead upwards. */
    public double x(double right) {
        return centreX() + right * scale();
    }

    public double y(double ahead) {
        return centreY() - ahead * scale();
    }

    /** The slot under a pixel: the inverse of {@link #x} and {@link #y}. */
    public double right(double px) {
        return (px - centreX()) / scale();
    }

    public double ahead(double py) {
        return (centreY() - py) / scale();
    }

    /**
     * Which marker a pixel picks up: the one whose box it is in, and of two drawn over each other, the one
     * whose middle it is nearer. Picking only within a few pixels of a middle made a marker hard to take
     * hold of by its edge, and one lying over the flagship or another slot could not be told apart.
     *
     * @param xs   each marker's middle, across
     * @param ys   and down
     * @param half half the width of a marker's box, in pixels
     * @return the marker's index, or {@code -1} when the pixel is in none
     */
    public static int pick(double[] xs, double[] ys, double px, double py, double half) {
        int best = -1;
        double nearest = Double.POSITIVE_INFINITY;
        for (int i = 0; i < Math.min(xs.length, ys.length); i++) {
            double dx = Math.abs(xs[i] - px);
            double dy = Math.abs(ys[i] - py);
            if (dx <= half && dy <= half && dx * dx + dy * dy < nearest) {
                nearest = dx * dx + dy * dy;
                best = i;
            }
        }
        return best;
    }

    /** Whether a pixel is inside the grid's drawable area. */
    public boolean contains(double px, double py) {
        return px >= left + MARGIN && px <= left + width - MARGIN && py >= top + MARGIN && py <= top + height - MARGIN;
    }

    /** A pixel pulled inside the drawable area, so a marker dragged off the edge stops at it. */
    public double clampX(double px) {
        return Math.max(left + MARGIN, Math.min(left + width - MARGIN, px));
    }

    public double clampY(double py) {
        return Math.max(top + MARGIN, Math.min(top + height - MARGIN, py));
    }
}
