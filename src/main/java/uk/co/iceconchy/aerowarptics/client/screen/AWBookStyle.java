package uk.co.iceconchy.aerowarptics.client.screen;

/**
 * What a book looks like, drawn rather than textured.
 *
 * <p>{@code AWScreenStyle} is the look of a machine's panel: dark, brass-edged, purple where the rift
 * is. A handbook is not a machine, and dressing it as one would make it read as a fifth readout
 * screen rather than as something you sat down with. So this is the other palette - leather boards,
 * brass corners, parchment - and it lives in its own file for the same reason the panel palette does:
 * the screen and the diagrams on its pages both need it, and two files agreeing on hex codes by
 * copying them at each other is how a mod ends up with three slightly different browns.
 *
 * <p>Everything here is drawn from rectangles. That is not stubbornness about textures: a page is
 * sized by the layout and a 320-wide book on one machine is a 640-wide book on another, so a painted
 * page would have to be either stretched or tiled, and the shading that makes parchment look like
 * parchment is exactly what does not survive either. Rectangles scale.
 */
public final class AWBookStyle {

    // ---------------------------------------------------------------- palette

    /** The cover boards. */
    public static final int LEATHER = 0xFF_3A_2A_1E;
    public static final int LEATHER_DARK = 0xFF_20_16_0F;
    public static final int LEATHER_LIGHT = 0xFF_4E_3A_29;

    /** Brass, the same family the machines are built from. */
    public static final int BRASS = 0xFF_B0_8D_57;
    public static final int BRASS_DARK = 0xFF_7A_60_38;

    /** Paper, lit from the outer edge and shaded into the spine. */
    public static final int PAPER = 0xFF_E9_DC_B8;
    public static final int PAPER_SHADE = 0xFF_C6_B3_88;
    public static final int PAPER_EDGE = 0xFF_A3_8F_66;

    /** Ink, in three weights: a heading, a sentence, and an aside. */
    public static final int INK_TITLE = 0xFF_40_2A_18;
    public static final int INK = 0xFF_35_2C_20;
    public static final int INK_SOFT = 0xFF_6E_5D_45;

    /**
     * The rift's colour, mixed for paper.
     *
     * <p>{@code AWScreenStyle.ACCENT} is tuned to glow on a near-black panel and is close to
     * illegible on parchment. This is the same hue taken down until it reads as ink.
     */
    public static final int RIFT_INK = 0xFF_6D_36_96;

    /** The rift itself, in a diagram, where it is meant to glow rather than be read. */
    public static final int RIFT_GLOW = 0xFF_C8_6C_FF;

    /** A hairline across a page. */
    public static final int RULE = 0x55_8A_74_50;

    private AWBookStyle() {
    }

    // ------------------------------------------------------------------ book

    /** The closed boards the pages sit on: leather, with a brass rule inside its edge. */
    public static void cover(AWDraw draw, int left, int top, int width, int height) {
        draw.fill(left, top, left + width, top + height, LEATHER);
        // Lit from above, as a physical object standing on a table would be.
        draw.hLine(left, top, width, LEATHER_LIGHT);
        draw.hLine(left, top + height - 1, width, LEATHER_DARK);
        draw.vLine(left, top, height, LEATHER_LIGHT);
        draw.vLine(left + width - 1, top, height, LEATHER_DARK);
        draw.outline(left + 3, top + 3, width - 6, height - 6, BRASS_DARK);

        // Brass at the corners only. A full brass frame reads as a machine panel, which is the one
        // thing this screen is trying not to be.
        for (int corner = 0; corner < 4; corner++) {
            boolean right = corner % 2 == 1;
            boolean bottom = corner > 1;
            int x = right ? left + width - 11 : left + 3;
            int y = bottom ? top + height - 11 : top + 3;
            draw.hLine(x, bottom ? y + 7 : y, 8, BRASS);
            draw.vLine(right ? x + 7 : x, y, 8, BRASS);
        }
    }

    /**
     * One page of paper.
     *
     * <p>Shaded into the spine rather than flat, because that shading is the only cue that the two
     * halves of this screen are one sheet of paper bent in the middle rather than two panels.
     *
     * @param towardsSpine which edge of this page the spine is on
     */
    public static void page(AWDraw draw, int left, int top, int width, int height,
                            boolean towardsSpine) {
        draw.fill(left, top, left + width, top + height, PAPER);

        int shade = Math.min(28, width / 3);
        if (towardsSpine) {
            draw.shadeAcross(left, top, shade, height, PAPER_SHADE, PAPER);
        } else {
            draw.shadeAcross(left + width - shade, top, shade, height, PAPER, PAPER_SHADE);
        }
        draw.outline(left, top, width, height, PAPER_EDGE);
        grain(draw, left, top, width, height);
    }

    /**
     * The flecks in the paper.
     *
     * <p>Fixed by position rather than random: a page redrawn sixty times a second with new specks
     * each time does not look like paper, it looks like static.
     */
    private static void grain(AWDraw draw, int left, int top, int width, int height) {
        for (int y = 0; y < height; y += 3) {
            for (int x = 0; x < width; x += 3) {
                int hash = (left + x) * 73_856_093 ^ (top + y) * 19_349_663;
                int mixed = (hash >>> 8) & 0xFF;
                if (mixed < 24) {
                    draw.fill(left + x, top + y, left + x + 1, top + y + 1, 0x14_5A_46_2A);
                }
            }
        }
    }

    /** The gutter between the two pages, and the shadow each page throws into it. */
    public static void spine(AWDraw draw, int left, int top, int width, int height) {
        draw.fill(left, top, left + width, top + height, LEATHER_DARK);
        int half = Math.max(1, width / 2);
        draw.shadeAcross(left, top, half, height, 0x88_00_00_00, 0x00_00_00_00);
        draw.shadeAcross(left + width - half, top, half, height, 0x00_00_00_00, 0x88_00_00_00);
        // Stitching, at the two thirds a real binding would take.
        for (int y = top + 6; y < top + height - 4; y += 9) {
            draw.hLine(left + 1, y, Math.max(1, width - 2), 0x66_B0_8D_57);
        }
    }

    /**
     * A chapter ribbon on the top board.
     *
     * @param out how far the ribbon is pulled down, in pixels - the open chapter sits proud, and a
     *            hovered one is on its way there
     */
    public static void ribbon(AWDraw draw, int x, int y, int width, int height,
                             int out, boolean open) {
        int colour = open ? RIFT_INK : 0xFF_5A_3F_2C;
        int edge = open ? RIFT_GLOW : BRASS_DARK;
        int bottom = y + height + out;
        draw.fill(x, y, x + width, bottom, colour);
        draw.hLine(x, y, width, AWAnim.blend(colour, 0xFF_FF_FF_FF, 0.25F));
        draw.vLine(x, y, bottom - y, AWAnim.blend(colour, 0xFF_FF_FF_FF, 0.15F));
        draw.vLine(x + width - 1, y, bottom - y, LEATHER_DARK);
        draw.hLine(x + 1, bottom - 1, width - 2, edge);
    }

    /** A page-turn arrow, in the vanilla shape: a triangle with a tail. */
    public static void turnArrow(AWDraw draw, int centreX, int centreY,
                                 boolean forward, float emphasis, boolean available) {
        int colour = available
                ? AWAnim.blend(BRASS_DARK, BRASS, emphasis)
                : 0x55_7A_60_38;
        int nudge = Math.round(emphasis * 2.0F) * (forward ? 1 : -1);
        int tip = centreX + nudge + (forward ? 4 : -4);
        draw.triangle(tip, centreY, 5, forward, colour);
        draw.fill(forward ? tip - 8 : tip + 4, centreY - 1, forward ? tip - 4 : tip + 8,
                centreY + 1, colour);
    }

    /** The rule under a page's heading, with a diamond in the middle of it. */
    public static void ornament(AWDraw draw, int left, int y, int width) {
        int centre = left + width / 2;
        draw.hLine(left, y, width / 2 - 5, RULE);
        draw.hLine(centre + 5, y, width / 2 - 5, RULE);
        for (int step = 0; step < 3; step++) {
            draw.fill(centre - step, y - 2 + step, centre + step + 1, y - 1 + step, BRASS_DARK);
            draw.fill(centre - step, y + 2 - step, centre + step + 1, y + 3 - step, BRASS_DARK);
        }
    }
}
