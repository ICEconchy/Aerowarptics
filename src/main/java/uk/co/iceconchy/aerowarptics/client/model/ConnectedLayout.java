package uk.co.iceconchy.aerowarptics.client.model;

/**
 * Which tile of a connected texture a face draws, from which of the eight blocks round it on that face it joins.
 *
 * <p>The tiles are laid out as OptiFine lays them, which is what the artist drew to, so the files themselves - one
 * per tile, numbered - are the ones a CTM resource pack would ship:
 * <ul>
 *   <li><b>Full</b> ({@code method=ctm}): 47 tiles, one for every way the eight neighbours can join that looks
 *       different. A corner only matters when both sides beside it join; then its tile shows whether the diagonal
 *       does too, as a seam or a little inside corner. {@link #TILES} is each tile's arrangement, read off the
 *       artist's own tiles - an unjoined edge is a brass border, an unjoined corner between two joined edges a brass
 *       nub - and {@code ConnectedLayoutTest} reads them off again so a set drawn in another order is caught.</li>
 *   <li><b>Compact</b> ({@code method=ctm_compact}): 5 tiles, and a face drawn as four quarters, each quarter taken
 *       from whichever tile says what that corner of the face is doing: alone (0), joined all round (1), joined
 *       only up and down (2), joined only left and right (3), or joined on both sides but not across the corner
 *       (4).</li>
 * </ul>
 *
 * <p>Up, down, left and right are as the face is seen from outside the block, with its texture the right way up -
 * see {@code ConnectedBakedModel} for which way that is on each face. Free of Minecraft, for the test.
 */
public final class ConnectedLayout {

    /** A neighbour on the face: above, below, to the left, to the right, and the four corners between. */
    public static final int UP = 1;
    public static final int DOWN = 1 << 1;
    public static final int LEFT = 1 << 2;
    public static final int RIGHT = 1 << 3;
    public static final int UP_LEFT = 1 << 4;
    public static final int UP_RIGHT = 1 << 5;
    public static final int DOWN_LEFT = 1 << 6;
    public static final int DOWN_RIGHT = 1 << 7;

    /** How many tiles each method draws from. */
    public static final int FULL_TILES = 47;
    public static final int COMPACT_TILES = 5;

    /** The compact tiles. */
    public static final int ALONE = 0;
    public static final int JOINED = 1;
    public static final int UP_AND_DOWN = 2;
    public static final int ACROSS = 3;
    public static final int INSIDE_CORNER = 4;

    /**
     * Each full tile's arrangement, in order: which sides it joins (U, D, L, R, or a dash), then each corner - up-left,
     * up-right, down-left, down-right - as joined (c), not joined (n), or a dash where a side beside it is not joined
     * and the corner cannot show.
     */
    static final String[] TILES = {
            "---- ----", "---R ----", "--LR ----", "--L- ----", "-D-R ---n", "-DL- --n-",
            "UD-R -n-n", "-DLR --nn", "UDLR ncnn", "UDLR nnnc", "UDLR cncn", "UDLR ccnn",
            "-D-- ----", "-D-R ---c", "-DLR --cc", "-DL- --c-", "U--R -n--", "U-L- n---",
            "U-LR nn--", "UDL- n-n-", "UDLR cnnn", "UDLR nncn", "UDLR nncc", "UDLR ncnc",
            "UD-- ----", "UD-R -c-c", "UDLR cccc", "UDL- c-c-", "UD-R -n-c", "-DLR --cn",
            "UD-R -c-n", "-DLR --nc", "UDLR cccn", "UDLR ccnc", "UDLR nccn", "UDLR cnnc",
            "U--- ----", "U--R -c--", "U-LR cc--", "U-L- c---", "U-LR nc--", "UDL- c-n-",
            "U-LR cn--", "UDL- n-c-", "UDLR cncc", "UDLR nccc", "UDLR nnnn",
    };

    /** The full tile for every combination of the eight neighbours. */
    private static final int[] FULL = new int[256];

    static {
        java.util.Arrays.fill(FULL, -1);
        for (int tile = 0; tile < TILES.length; tile++) {
            int mask = mask(TILES[tile]);
            // Every combination that looks like this tile: the corners it cannot show, either way.
            for (int extra = 0; extra < 16; extra++) {
                int candidate = mask;
                for (int corner = 0; corner < 4; corner++) {
                    if ((extra & (1 << corner)) != 0) {
                        candidate |= UP_LEFT << corner;
                    }
                }
                if (canonical(candidate) == mask) {
                    FULL[candidate] = tile;
                }
            }
        }
    }

    private ConnectedLayout() {
    }

    /** The full tile a face draws, for the neighbours it joins. */
    public static int full(int neighbours) {
        return FULL[canonical(neighbours)];
    }

    /**
     * The compact tile one quarter of a face draws, from the two neighbours beside that corner and the one across it.
     *
     * @param across   whether the face joins the neighbour to the side of this quarter - left or right
     * @param upright  whether it joins the one above or below it
     * @param corner   whether it joins the one diagonally beyond the corner
     */
    public static int compact(boolean across, boolean upright, boolean corner) {
        if (across && upright) {
            return corner ? JOINED : INSIDE_CORNER;
        }
        if (across) {
            return ACROSS;
        }
        return upright ? UP_AND_DOWN : ALONE;
    }

    /** A set of neighbours with every corner that cannot show - a side beside it not joined - left out. */
    public static int canonical(int neighbours) {
        int mask = neighbours & (UP | DOWN | LEFT | RIGHT);
        mask |= corner(neighbours, UP, LEFT, UP_LEFT);
        mask |= corner(neighbours, UP, RIGHT, UP_RIGHT);
        mask |= corner(neighbours, DOWN, LEFT, DOWN_LEFT);
        mask |= corner(neighbours, DOWN, RIGHT, DOWN_RIGHT);
        return mask;
    }

    private static int corner(int neighbours, int upright, int side, int corner) {
        return (neighbours & upright) != 0 && (neighbours & side) != 0 ? neighbours & corner : 0;
    }

    /** The neighbours a tile's arrangement stands for, with the corners it cannot show left out. */
    static int mask(String arrangement) {
        int mask = 0;
        int[] sides = {UP, DOWN, LEFT, RIGHT};
        for (int i = 0; i < 4; i++) {
            if (arrangement.charAt(i) != '-') {
                mask |= sides[i];
            }
        }
        int[] corners = {UP_LEFT, UP_RIGHT, DOWN_LEFT, DOWN_RIGHT};
        for (int i = 0; i < 4; i++) {
            if (arrangement.charAt(5 + i) == 'c') {
                mask |= corners[i];
            }
        }
        return mask;
    }
}
