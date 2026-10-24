package uk.co.iceconchy.aerowarptics.client.screen;

import uk.co.iceconchy.aerowarptics.client.screen.AWLayout.Rect;

import java.util.List;

/**
 * Where everything on the Collapse Cradle's panel goes, as arithmetic.
 *
 * <p>The panel is the cradle's own, not the mod's usual window: the machine is the end of the game, so its screen
 * is dressed as the machine is - a slab of rift-stone in a verdigris frame, brass plates at its corners, carvings
 * that light as it works. Laid out like every other screen here, though: a pure class, window-relative, tested
 * by {@code CradleLayoutTest} to fit the 320 pixels Auto GUI scale promises and to keep its parts apart.
 *
 * <p>The slots are the menu's as well as the screen's, which is why this lives where the menu can reach it: a
 * slot is where the menu says it is and the screen draws its well there, and two numbers for one place drift.
 *
 * <pre>
 *  +--------------------------------------------------------------------------+
 *  | COLLAPSE CRADLE                                              [ state ]   |
 *  | +-altar-----+ +-dial------------+ +-readouts---------------+             |
 *  | | o crown o | |   ___           | | essence | speed | lean |             |
 *  | | o (arms)o | |  /   \  needle  | |   ||    |  ||   |  (o) |             |
 *  | | o [cup] o | | progress ====== | | figures, the width of  |             |
 *  | | o [out] o | | status lines    | | the panel              |             |
 *  | +-----------+ +-----------------+ +------------------------+             |
 *  | inventory 9x3          [  BEGIN  ]                                        |
 *  | hotbar 9x1             result, cost, refusal                              |
 *  +--------------------------------------------------------------------------+
 * </pre>
 */
public final class CradleLayout {

    public static final int WIDTH = 320;
    public static final int HEIGHT = 236;

    /** A slot's well is eighteen pixels; the item inside it is sixteen, one in from the well's corner. */
    public static final int SLOT = 18;

    /** The three slots' item corners: the arms, the cup and the output tray, down the middle of the altar. */
    public static final int ARMS_X = 51;
    public static final int ARMS_Y = 52;
    public static final int CUP_X = 51;
    public static final int CUP_Y = 90;
    public static final int OUT_X = 51;
    public static final int OUT_Y = 118;

    /** The player's inventory: three rows and the hotbar under them. */
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 154;
    public static final int HOTBAR_Y = INVENTORY_Y + 3 * SLOT + 4;

    private CradleLayout() {
    }

    /** The regions of the panel. */
    public record Panel(Rect header, Rect altar, Rect dial, Rect progress, Rect status, Rect readouts,
                        Rect essence, Rect speed, Rect lean, Rect figures, Rect inventory, Rect start, Rect preview,
                        List<Rect> offerings) {

        /** The regions that must never overlap one another. */
        public List<Rect> regions() {
            return List.of(header, altar, dial, progress, status, readouts, inventory, start, preview);
        }
    }

    public static Panel panel() {
        Rect header = new Rect(0, 0, WIDTH, 18);
        Rect altar = new Rect(8, 22, 104, 118);
        Rect dial = new Rect(118, 22, 102, 64);
        Rect progress = new Rect(118, 90, 102, 10);
        Rect status = new Rect(118, 104, 102, 36);
        Rect readouts = new Rect(226, 22, 86, 118);
        // The gauges stop short of the floor so the figures can have the panel's whole width under them: beside
        // the gauges there are thirty-six pixels, and "No stabilisers" was cut to "No sta" in them.
        Rect essence = new Rect(232, 36, 12, 60);
        Rect speed = new Rect(260, 36, 12, 60);
        Rect lean = new Rect(276, 44, 32, 32);
        Rect figures = new Rect(230, 103, 80, 34);
        Rect inventory = new Rect(INVENTORY_X - 1, INVENTORY_Y - 1, 9 * SLOT, 3 * SLOT + 4 + SLOT);
        Rect start = new Rect(182, 156, 130, 24);
        Rect preview = new Rect(182, 184, 130, 44);
        // Round the altar, where the depots stand round the machine: four down each side.
        List<Rect> offerings = List.of(
                new Rect(12, 30, 16, 16), new Rect(12, 54, 16, 16), new Rect(12, 78, 16, 16), new Rect(12, 102, 16, 16),
                new Rect(92, 30, 16, 16), new Rect(92, 54, 16, 16), new Rect(92, 78, 16, 16), new Rect(92, 102, 16, 16));
        return new Panel(header, altar, dial, progress, status, readouts, essence, speed, lean, figures, inventory, start,
                preview, offerings);
    }

    /** A slot's well, from the item corner the menu places it at. */
    public static Rect well(int itemX, int itemY) {
        return new Rect(itemX - 1, itemY - 1, SLOT, SLOT);
    }
}
