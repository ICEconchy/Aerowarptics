package uk.co.iceconchy.aerowarptics.client.screen;

/**
 * Where everything on a Rift Wellhead's panel goes: arithmetic only, so it can be held to the smallest screen
 * the game promises (320 by 240) without a game - see {@code WellheadLayoutTest}.
 *
 * <p>One panel, top to bottom: a title, the strain gauge with its four bands, seven rows of readings, and two
 * buttons side by side. Every rectangle is {x, y, width, height} in screen pixels.
 */
public record WellheadLayout(int x, int y) {

    public static final int WIDTH = 236;
    public static final int HEIGHT = 150;
    public static final int MARGIN = 10;
    public static final int ROWS = 7;
    public static final int ROW_STEP = 11;

    private static final int GAUGE_TOP = 24;
    private static final int GAUGE_HEIGHT = 12;
    private static final int ROWS_TOP = 44;
    private static final int BUTTON_TOP = 124;
    private static final int BUTTON_HEIGHT = 16;
    private static final int BUTTON_GAP = 6;

    /** Centred, and pinned to the top-left rather than clipped on both edges where a screen is smaller than the panel. */
    public static WellheadLayout in(int screenWidth, int screenHeight) {
        return new WellheadLayout(Math.max(0, (screenWidth - WIDTH) / 2), Math.max(0, (screenHeight - HEIGHT) / 2));
    }

    public int[] panel() {
        return new int[]{x, y, WIDTH, HEIGHT};
    }

    public int titleY() {
        return y + 8;
    }

    public int[] gauge() {
        return new int[]{x + MARGIN, y + GAUGE_TOP, WIDTH - 2 * MARGIN, GAUGE_HEIGHT};
    }

    /** The pixel along the gauge a strain or a heading is drawn at, never past either end. */
    public int gaugeAt(double share) {
        int[] gauge = gauge();
        return gauge[0] + (int) Math.round(Math.max(0.0D, Math.min(1.0D, share)) * (gauge[2] - 1));
    }

    /** The baseline of a row of readings, 0 to {@link #ROWS} - 1. */
    public int rowY(int row) {
        return y + ROWS_TOP + row * ROW_STEP;
    }

    public int textX() {
        return x + MARGIN;
    }

    /** The room a row has for its text, in pixels. */
    public int textWidth() {
        return WIDTH - 2 * MARGIN;
    }

    public int[] scramButton() {
        int width = (WIDTH - 2 * MARGIN - BUTTON_GAP) / 2;
        return new int[]{x + MARGIN, y + BUTTON_TOP, width, BUTTON_HEIGHT};
    }

    public int[] hatchButton() {
        int[] scram = scramButton();
        return new int[]{scram[0] + scram[2] + BUTTON_GAP, scram[1], scram[2], BUTTON_HEIGHT};
    }
}
