package uk.co.iceconchy.aerowarptics.client.screen;

import uk.co.iceconchy.aerowarptics.client.screen.AWLayout.Rect;

/**
 * Where everything on the Berth's panel goes, as arithmetic - free of Minecraft, for {@code BerthLayoutTest}.
 *
 * <p>A slip is a box of air tens of blocks long, set from a bollard at its edge: nobody standing at the block
 * can see where it ends. So the panel is the Rift Ward's - a strip down the right of the screen with the world
 * in view beside it, the camera flown out over the slip while it is open - with three tabs where the ward has
 * two. The strip itself is {@link WardLayout}'s, so the two cannot drift apart.
 */
public final class BerthLayout {

    /** The tabs: the slip's shape, where it lies, and whom it is kept for. */
    public static final int TABS = 3;

    public static final int ROWS = WardLayout.ROWS;

    /**
     * How much lane is drawn behind a slip while it is being set. The lane a ship really flies down is as long
     * as that ship's run out, which no berth knows until one comes; this is a fair skiff's, and is there to say
     * which end ships come in at and that the way in has to be kept clear.
     */
    public static final double LANE_SHOWN = 24.0D;

    private BerthLayout() {
    }

    public static WardLayout.Sidebar sidebar(int screenWidth, int screenHeight) {
        return WardLayout.sidebar(screenWidth, screenHeight, TABS);
    }

    /** The box a name is typed into, on a given row. */
    public static Rect textBox(Rect row) {
        return new Rect(row.x(), row.y() + 2, row.width(), 16);
    }

    /**
     * How far off the camera stands to take in a slip and the lane behind it, looking down on them.
     *
     * @param length the slip's length
     * @param width  and its width
     */
    public static float cameraDistance(double length, double width) {
        double span = Math.max(length + LANE_SHOWN, width * 2.0D);
        return (float) Math.max(18.0D, Math.min(OrbitCamera.MAX_DISTANCE, span * 1.3D + 8.0D));
    }

    /** A slider's value from where the mouse is along its bar, held to its range and its step. */
    public static int sliderValue(Rect row, double mouseX, int min, int max, int step) {
        Rect track = WardLayout.sliderBar(row);
        double fraction = Math.max(0.0D, Math.min(1.0D, (mouseX - track.x()) / Math.max(1, track.width())));
        int raw = (int) Math.round(min + fraction * (max - min));
        int stepped = min + Math.round((raw - min) / (float) Math.max(1, step)) * Math.max(1, step);
        return Math.max(min, Math.min(max, stepped));
    }

    /** How far along its bar a slider's knob sits, 0..1. */
    public static float sliderFraction(int value, int min, int max) {
        return max <= min ? 0.0F : Math.max(0.0F, Math.min(1.0F, (value - min) / (float) (max - min)));
    }
}
