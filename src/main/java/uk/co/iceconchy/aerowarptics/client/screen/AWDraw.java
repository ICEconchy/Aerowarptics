package uk.co.iceconchy.aerowarptics.client.screen;

/**
 * Somewhere to put a coloured rectangle, and the shapes that can be built out of one.
 *
 * <p>Every drawing in this mod that is not a texture comes down to {@code GuiGraphics.fill}, so this
 * is that one method and nothing else - which is what lets the book's chrome and its diagrams be
 * written without Minecraft in them, exactly as {@code AWLayout} and {@code AWAnim} already are. The
 * screen hands over {@code graphics::fill} and never notices; a test hands over something that writes
 * the rectangles down instead, and can then prove a diagram stays inside the box the page gave it.
 *
 * <p>That is not a hypothetical guard. Four of these drawings overhung their box on the first
 * attempt, by two to nine pixels, and the symptom would have been a marker ring or a ring gate drawn
 * faintly over the first line of the text underneath - which nobody would report as a bug and
 * everybody would notice as the page looking wrong.
 *
 * <p>The default methods are here rather than in a helper class so that anything holding one of these
 * can draw a line without also having to be handed the toolbox.
 */
@FunctionalInterface
public interface AWDraw {

    /**
     * Fills a rectangle. Right and bottom are exclusive, as everywhere in Minecraft's GUI.
     *
     * @param argb packed colour, alpha first
     */
    void fill(int left, int top, int right, int bottom, int argb);

    default void hLine(int x, int y, int length, int colour) {
        fill(x, y, x + length, y + 1, colour);
    }

    default void vLine(int x, int y, int length, int colour) {
        fill(x, y, x + 1, y + length, colour);
    }

    default void outline(int x, int y, int width, int height, int colour) {
        hLine(x, y, width, colour);
        hLine(x, y + height - 1, width, colour);
        vLine(x, y, height, colour);
        vLine(x + width - 1, y, height, colour);
    }

    /** A straight line between two points, one pixel at a time. */
    default void line(int x0, int y0, int x1, int y1, int colour) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        int x = x0;
        int y = y0;
        // Bounded rather than trusting the loop to terminate: a diagram is drawn every frame, and a
        // line that never arrives would take the game with it.
        for (int guard = 0; guard < 512; guard++) {
            fill(x, y, x + 1, y + 1, colour);
            if (x == x1 && y == y1) {
                return;
            }
            int doubled = error * 2;
            if (doubled >= dy) {
                error += dy;
                x += sx;
            }
            if (doubled <= dx) {
                error += dx;
                y += sy;
            }
        }
    }

    /** A circle's edge. Sampled by angle rather than stepped, so a small radius still closes up. */
    default void circle(int centreX, int centreY, int radius, int colour) {
        arc(centreX, centreY, radius, 0.0F, 1.0F, colour);
    }

    /**
     * Part of a circle's edge, measured in turns clockwise from twelve o'clock.
     *
     * <p>Turns rather than radians because every caller here is thinking in fractions of a dial.
     */
    default void arc(int centreX, int centreY, int radius, float from, float to, int colour) {
        int steps = Math.max(12, radius * 8);
        for (int step = 0; step <= steps; step++) {
            float turn = from + (to - from) * step / steps;
            double angle = turn * Math.PI * 2.0D;
            int x = centreX + (int) Math.round(Math.sin(angle) * radius);
            int y = centreY - (int) Math.round(Math.cos(angle) * radius);
            fill(x, y, x + 1, y + 1, colour);
        }
    }

    /** A filled circle. */
    default void disc(int centreX, int centreY, int radius, int colour) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.round(Math.sqrt(Math.max(0, radius * radius - dy * dy)));
            if (half <= 0) {
                continue;
            }
            fill(centreX - half, centreY + dy, centreX + half + 1, centreY + dy + 1, colour);
        }
    }

    /** A solid triangle pointing left or right, for a page-turn arrow or a play mark. */
    default void triangle(int tipX, int centreY, int size, boolean pointsRight, int colour) {
        for (int step = 0; step < size; step++) {
            int x = pointsRight ? tipX - step : tipX + step;
            fill(x, centreY - step, x + 1, centreY + step + 1, colour);
        }
    }

    /**
     * A horizontal shade, blended across in strips.
     *
     * <p>Vanilla's own gradient fill runs top to bottom only, and every shadow a book casts - the
     * spine into the page, a turning leaf onto the sheet under it - runs the other way.
     */
    default void shadeAcross(int x, int y, int width, int height, int from, int to) {
        if (width <= 0 || height <= 0) {
            return;
        }
        for (int column = 0; column < width; column++) {
            int colour = AWAnim.blend(from, to, width == 1 ? 0.0F : column / (float) (width - 1));
            fill(x + column, y, x + column + 1, y + height, colour);
        }
    }
}
