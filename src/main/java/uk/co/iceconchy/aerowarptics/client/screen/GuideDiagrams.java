package uk.co.iceconchy.aerowarptics.client.screen;

import uk.co.iceconchy.aerowarptics.guide.GuideDiagram;

/**
 * The pictures in the handbook, and the fact that they move.
 *
 * <p>A guide book in a mod is usually a wall of text with an item icon at the top, and the reason is
 * that the interesting half of most of these machines is a thing that happens over time - a shaft
 * turning, a needle settling on the bow, a traveller going in one gate and out of another the right
 * way round. A still picture of a Rift Gate is a rectangle. A moving one is a doorway.
 *
 * <p>So every diagram here animates off a single clock: the ticks the book has been open, plus the
 * partial tick, which makes them smooth rather than twenty-a-second. They are drawn from rectangles
 * and lines like the rest of the book, which keeps them sharp at every GUI scale and means adding one
 * is a method rather than a texture, a model, an atlas entry and a resource test.
 *
 * <p>Nothing here reads game state. These are illustrations, not readouts - the screens in this mod
 * already show a real drive's real charge, and a diagram that pretended to would be a fifth of a
 * console with no machine behind it.
 */
public final class GuideDiagrams {

    private GuideDiagrams() {
    }

    /**
     * Draws a diagram into the space the page has set aside for it.
     *
     * <p>The area is the full page width; each drawing centres itself in it and is free to use less.
     */
    public static void draw(AWDraw draw, GuideDiagram diagram,
                            int left, int top, int width, int height, float ticks) {
        int centreX = left + width / 2;
        int centreY = top + height / 2;
        switch (diagram) {
            case NONE -> {
            }
            case RIFT -> rift(draw, centreX, centreY, ticks);
            case DRIVE -> drive(draw, centreX, centreY, ticks);
            case BOW -> bow(draw, centreX, centreY, ticks);
            case ANCHOR -> anchor(draw, centreX, centreY, ticks);
            case CHART -> chart(draw, centreX, centreY, ticks);
            case REDSTONE -> redstone(draw, centreX, centreY, ticks);
            case CORRIDOR -> corridor(draw, centreX, centreY, ticks);
            case PROBE -> probe(draw, centreX, centreY, ticks);
            case FIX -> fix(draw, centreX, centreY, ticks);
            case GATE -> gate(draw, centreX, centreY, ticks);
            case GATE_PAIR -> gatePair(draw, centreX, centreY, ticks);
            case SIPHON -> siphon(draw, centreX, centreY, ticks);
            case CHUTE -> chute(draw, centreX, centreY, ticks);
            case CHECKLIST -> checklist(draw, centreX, centreY, ticks);
        }
    }

    // ------------------------------------------------------------------ parts

    /** A machine block: brass edge, dark face. The shape every diagram here builds from. */
    private static void machine(AWDraw draw, int x, int y, int width, int height) {
        draw.fill(x, y, x + width, y + height, 0xFF_2E_25_1B);
        draw.outline(x, y, width, height, AWBookStyle.BRASS_DARK);
        draw.hLine(x + 1, y + 1, width - 2, AWBookStyle.BRASS);
    }

    /** A stretch of ground, hatched the way a section drawing is. */
    private static void ground(AWDraw draw, int left, int y, int width) {
        draw.hLine(left, y, width, AWBookStyle.INK);
        for (int x = 0; x < width; x += 4) {
            draw.line(left + x, y + 4, left + x + 3, y + 1, AWBookStyle.RULE);
        }
    }

    /** A hull, seen from the side: the shape of an Aeronautics deck rather than a rectangle. */
    private static void hull(AWDraw draw, int left, int y, int width, int colour) {
        int depth = 6;
        draw.fill(left, y, left + width, y + 2, colour);
        for (int step = 0; step < depth; step++) {
            int inset = step * step / 3;
            draw.fill(left + inset, y + 2 + step, left + width - inset, y + 3 + step, colour);
        }
    }

    /**
     * The rift's own edge: a tear rather than a hole.
     *
     * <p>Deliberately not an ellipse. The rift in this mod arrives like a stone through a window, and
     * a smooth oval is the one shape that would say the opposite - so the profile carries a fixed
     * wobble, which stays put frame to frame because it is a function of the row, not of a random.
     */
    private static void tear(AWDraw draw, int centreX, int centreY, int halfHeight,
                             float scale, int colour, int core) {
        for (int dy = -halfHeight; dy <= halfHeight; dy++) {
            float profile = (float) Math.cos(dy / (double) halfHeight * Math.PI / 2.0D);
            int wobble = ((dy * 37) % 5) - 2;
            int half = Math.round(profile * profile * 11.0F * scale) + (Math.abs(dy) > 2 ? wobble : 0);
            if (half <= 0) {
                continue;
            }
            draw.fill(centreX - half, centreY + dy, centreX + half + 1, centreY + dy + 1, colour);
            if (half > 2) {
                draw.fill(centreX - half / 3, centreY + dy, centreX + half / 3 + 1,
                        centreY + dy + 1, core);
            }
        }
    }

    // -------------------------------------------------------------- diagrams

    /** The cover mark: a rift hanging open, turning in the wreckage it made. */
    private static void rift(AWDraw draw, int centreX, int centreY, float ticks) {
        float breath = AWAnim.pulse(ticks, 90.0F);

        // Cracks first, so the tear is drawn over the ends of them.
        for (int spoke = 0; spoke < 8; spoke++) {
            double angle = spoke * Math.PI / 4.0D + ticks / 400.0D;
            int reach = 14 + (spoke % 3) * 4 + Math.round(breath * 4.0F);
            draw.line(centreX, centreY,
                    centreX + (int) Math.round(Math.sin(angle) * reach),
                    centreY - (int) Math.round(Math.cos(angle) * reach),
                    AWAnim.fade(AWBookStyle.RIFT_INK, 0.35F + 0.25F * breath));
        }

        tear(draw, centreX, centreY, 20, 0.85F + 0.15F * breath,
                AWBookStyle.RIFT_INK, AWAnim.blend(AWBookStyle.RIFT_GLOW, 0xFF_FF_FF_FF, 0.5F));

        // The panes that came loose, still tumbling outward.
        for (int shard = 0; shard < 7; shard++) {
            double angle = shard * Math.PI * 2.0D / 7.0D + ticks / 220.0D;
            float drift = AWAnim.sweep(ticks + shard * 90.0F, 260.0F);
            int radius = 18 + Math.round(drift * 16.0F);
            int x = centreX + (int) Math.round(Math.sin(angle) * radius * 1.4D);
            int y = centreY - (int) Math.round(Math.cos(angle) * radius * 0.7D);
            int size = 3 - Math.round(drift * 2.0F);
            if (size <= 0) {
                continue;
            }
            draw.fill(x, y, x + size, y + size,
                    AWAnim.fade(AWBookStyle.RIFT_GLOW, 1.0F - drift));
        }
    }

    /** A drive aboard a hull, with the shaft that spins it. */
    private static void drive(AWDraw draw, int centreX, int centreY, float ticks) {
        int deck = centreY + 12;
        hull(draw, centreX - 44, deck, 88, AWBookStyle.INK_SOFT);

        machine(draw, centreX + 2, deck - 18, 18, 18);
        // The drive's face, lit as it charges.
        float charge = AWAnim.sweep(ticks, 140.0F);
        draw.fill(centreX + 5, deck - 15, centreX + 17, deck - 3,
                AWAnim.fade(AWBookStyle.RIFT_GLOW, 0.25F + 0.6F * charge));
        tear(draw, centreX + 11, deck - 9, 5, 0.35F + 0.3F * charge,
                AWBookStyle.RIFT_INK, AWBookStyle.RIFT_GLOW);

        // Shaft and cog. The cog is what says "rotational force" without a word of text.
        draw.fill(centreX - 14, deck - 10, centreX + 2, deck - 8, AWBookStyle.BRASS_DARK);
        int cogX = centreX - 20;
        int cogY = deck - 9;
        draw.circle(cogX, cogY, 7, AWBookStyle.BRASS_DARK);
        draw.disc(cogX, cogY, 2, AWBookStyle.BRASS_DARK);
        for (int tooth = 0; tooth < 8; tooth++) {
            double angle = tooth * Math.PI / 4.0D + ticks / 12.0D;
            int x = cogX + (int) Math.round(Math.sin(angle) * 8);
            int y = cogY - (int) Math.round(Math.cos(angle) * 8);
            draw.fill(x - 1, y - 1, x + 2, y + 2, AWBookStyle.BRASS);
        }
    }

    /** The bow needle, swinging round the four settings and settling on each. */
    private static void bow(AWDraw draw, int centreX, int centreY, float ticks) {
        int radius = 18;
        draw.circle(centreX, centreY, radius, AWBookStyle.INK_SOFT);
        draw.circle(centreX, centreY, radius - 4, AWBookStyle.RULE);
        for (int mark = 0; mark < 4; mark++) {
            double angle = mark * Math.PI / 2.0D;
            int x = centreX + (int) Math.round(Math.sin(angle) * (radius - 2));
            int y = centreY - (int) Math.round(Math.cos(angle) * (radius - 2));
            draw.fill(x - 1, y - 1, x + 2, y + 2, AWBookStyle.BRASS_DARK);
        }

        // A quarter turn every four seconds, eased so it swings and settles rather than snapping -
        // which is exactly what the needle on the machine does.
        float phase = AWAnim.sweep(ticks, 320.0F) * 4.0F;
        int from = (int) phase;
        float within = AWAnim.easeOut(Math.min(1.0F, (phase - from) * 2.5F));
        double angle = (from + within) * Math.PI / 2.0D;
        int tipX = centreX + (int) Math.round(Math.sin(angle) * (radius - 5));
        int tipY = centreY - (int) Math.round(Math.cos(angle) * (radius - 5));
        draw.line(centreX, centreY, tipX, tipY, AWBookStyle.RIFT_INK);
        draw.fill(tipX - 1, tipY - 1, tipX + 2, tipY + 2, AWBookStyle.RIFT_GLOW);
        draw.disc(centreX, centreY, 2, AWBookStyle.INK);

        // The nose the needle is being lined up with.
        hull(draw, centreX + radius + 6, centreY - 4, 22, AWBookStyle.INK_SOFT);
    }

    /** An anchor standing on the ground, marking itself. */
    private static void anchor(AWDraw draw, int centreX, int centreY, float ticks) {
        int base = centreY + 14;
        ground(draw, centreX - 40, base, 80);
        machine(draw, centreX - 7, base - 14, 14, 14);
        draw.fill(centreX - 3, base - 11, centreX + 3, base - 5,
                AWAnim.fade(AWBookStyle.RIFT_GLOW, 0.5F + 0.5F * AWAnim.pulse(ticks, 50.0F)));

        // A marker beating above it: three rings, staggered, each fading as it grows. The largest
        // has to finish inside the box the page gave this drawing, or it is drawn over the first
        // line of the text underneath.
        for (int ring = 0; ring < 3; ring++) {
            float phase = AWAnim.sweep(ticks + ring * 30.0F, 90.0F);
            int radius = 3 + Math.round(phase * 11.0F);
            draw.circle(centreX, base - 22, radius,
                    AWAnim.fade(AWBookStyle.RIFT_INK, 1.0F - phase));
        }
    }

    /** Nine panels finding each other, and the chart that appears once they have. */
    private static void chart(AWDraw draw, int centreX, int centreY, float ticks) {
        float cycle = AWAnim.sweep(ticks, 200.0F);
        float assembled = AWAnim.easeOut(Math.min(1.0F, cycle * 2.0F));
        int cell = 7;
        int originX = centreX - cell * 3 / 2 - 12;
        int originY = centreY - 2;

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                // Each panel starts scattered and slides home; the offset is fixed per panel so the
                // same panel arrives from the same direction every time round.
                int scatterX = ((row * 3 + column) * 13 % 17) - 8;
                int scatterY = ((row * 3 + column) * 7 % 11) - 5;
                int x = originX + column * cell + Math.round(scatterX * (1.0F - assembled));
                int y = originY + row * cell + Math.round(scatterY * (1.0F - assembled));
                draw.fill(x, y, x + cell - 1, y + cell - 1, AWBookStyle.INK_SOFT);
                draw.hLine(x, y, cell - 1, AWBookStyle.BRASS_DARK);
            }
        }

        if (assembled < 1.0F) {
            return;
        }

        // The chart the finished table draws, with the scan line travelling over it.
        int mapX = centreX + 8;
        int mapY = centreY - 14;
        int size = 30;
        draw.fill(mapX, mapY, mapX + size, mapY + size, 0xFF_8F_A8_7A);
        for (int y = 0; y < size; y += 2) {
            for (int x = 0; x < size; x += 2) {
                int hash = (x * 41 ^ y * 97) & 0xFF;
                int colour = hash < 90 ? 0xFF_6E_8C_5E : hash < 150 ? 0xFF_A8_BC_8C : 0xFF_5E_7C_9E;
                draw.fill(mapX + x, mapY + y, mapX + x + 2, mapY + y + 2, colour);
            }
        }
        int scan = mapY + Math.round(AWAnim.sweep(ticks, 70.0F) * size);
        draw.hLine(mapX, scan, size, AWAnim.fade(0xFF_FF_FF_FF, 0.55F));
        draw.outline(mapX - 1, mapY - 1, size + 2, size + 2, AWBookStyle.BRASS_DARK);
    }

    /** A lever, a wire, and the edge that travels down it. */
    private static void redstone(AWDraw draw, int centreX, int centreY, float ticks) {
        int y = centreY + 6;
        int leverX = centreX - 42;
        int driveX = centreX + 18;

        ground(draw, centreX - 48, y + 8, 96);

        // The lever, thrown at the moment the pulse leaves it.
        float cycle = AWAnim.sweep(ticks, 110.0F);
        boolean thrown = cycle > 0.05F;
        machine(draw, leverX - 5, y, 10, 8);
        draw.line(leverX, y + 2, leverX + (thrown ? 4 : -4), y - 6,
                thrown ? 0xFF_C0_3A_2A : AWBookStyle.INK_SOFT);

        // The wire, and the one pulse on it. An edge, not a level - which is the whole point of the
        // page this sits on.
        for (int x = leverX + 8; x < driveX; x += 4) {
            draw.hLine(x, y + 2, 3, 0x88_8A_3A_2A);
        }
        int head = leverX + 8 + Math.round(AWAnim.clamp(cycle * 2.0F) * (driveX - leverX - 8));
        draw.fill(head - 2, y + 1, head + 2, y + 4, 0xFF_E0_4A_3A);

        machine(draw, driveX, y - 8, 18, 18);
        boolean arrived = cycle > 0.5F;
        draw.fill(driveX + 3, y - 5, driveX + 15, y + 7,
                AWAnim.fade(AWBookStyle.RIFT_GLOW, arrived ? 0.4F + 0.5F * AWAnim.pulse(ticks, 20.0F) : 0.15F));
    }

    /** The throat: rings going by, and the light at the far end. */
    private static void corridor(AWDraw draw, int centreX, int centreY, float ticks) {
        int vanishX = centreX + 44;

        // The far aperture, which is the only light in here.
        draw.disc(vanishX, centreY, 5,
                AWAnim.fade(AWBookStyle.RIFT_GLOW, 0.5F + 0.3F * AWAnim.pulse(ticks, 40.0F)));

        // Four ring gates in perspective, sliding towards the viewer and wrapping round. The
        // nearest one sets how big this drawing gets, so the scale it reaches is chosen to land
        // inside the box rather than to look impressive on its own.
        for (int ring = 0; ring < 4; ring++) {
            float phase = AWAnim.sweep(ticks + ring * 45.0F, 180.0F);
            float scale = 0.12F + phase * phase * 1.1F;
            int halfWidth = Math.max(1, Math.round(34 * scale));
            int halfHeight = Math.max(1, Math.round(13 * scale));
            int x = Math.round(AWAnim.lerp(vanishX, centreX - 46, phase));
            draw.outline(x - halfWidth / 3, centreY - halfHeight,
                    Math.max(2, halfWidth / 2), halfHeight * 2,
                    AWAnim.fade(AWBookStyle.RIFT_INK, Math.min(1.0F, phase * 3.0F) * (1.0F - phase * 0.6F)));
        }

        // The ship, holding station in the middle of the picture while the corridor goes past it.
        int bob = Math.round((float) Math.sin(ticks / 30.0D) * 2.0F);
        hull(draw, centreX - 22, centreY - 2 + bob, 34, AWBookStyle.INK);
    }

    /** The probe's dial, sweeping a bearing, and the range it is reaching to. */
    private static void probe(AWDraw draw, int centreX, int centreY, float ticks) {
        int dialX = centreX - 26;
        int radius = 17;
        draw.circle(dialX, centreY, radius, AWBookStyle.INK_SOFT);
        for (int bearing = 0; bearing < 8; bearing++) {
            double angle = bearing * Math.PI / 4.0D;
            int x = dialX + (int) Math.round(Math.sin(angle) * (radius - 2));
            int y = centreY - (int) Math.round(Math.cos(angle) * (radius - 2));
            draw.fill(x - 1, y - 1, x + 2, y + 2, AWBookStyle.BRASS_DARK);
        }

        float cycle = AWAnim.sweep(ticks, 160.0F);
        double sweep = cycle * Math.PI * 2.0D;
        draw.arc(dialX, centreY, radius - 5, 0.0F, cycle,
                AWAnim.fade(AWBookStyle.RIFT_INK, 0.6F));
        draw.line(dialX, centreY,
                dialX + (int) Math.round(Math.sin(sweep) * (radius - 4)),
                centreY - (int) Math.round(Math.cos(sweep) * (radius - 4)),
                AWBookStyle.RIFT_INK);
        draw.disc(dialX, centreY, 2, AWBookStyle.INK);

        // What comes back: the survey, filling in from the top as the scan completes.
        int mapX = centreX + 6;
        int mapY = centreY - 16;
        int size = 32;
        draw.outline(mapX - 1, mapY - 1, size + 2, size + 2, AWBookStyle.BRASS_DARK);
        int arrived = Math.round(cycle * size);
        for (int y = 0; y < arrived; y += 2) {
            for (int x = 0; x < size; x += 2) {
                int hash = (x * 61 ^ y * 29) & 0xFF;
                int colour = hash < 70 ? 0xFF_5E_7C_9E : hash < 160 ? 0xFF_6E_8C_5E : 0xFF_A8_BC_8C;
                draw.fill(mapX + x, mapY + y, mapX + x + 2, mapY + y + 2, colour);
            }
        }
        // The line the ground is arriving along, which is the server generating it.
        if (arrived < size) {
            draw.hLine(mapX, mapY + arrived, size,
                    AWAnim.fade(AWBookStyle.RIFT_GLOW, 0.8F));
        }
    }

    /** A fix: a place known by the picture of it rather than by a block somebody carried there. */
    private static void fix(AWDraw draw, int centreX, int centreY, float ticks) {
        int size = 40;
        int mapX = centreX - size / 2;
        int mapY = centreY - size / 2;
        for (int y = 0; y < size; y += 2) {
            for (int x = 0; x < size; x += 2) {
                int hash = (x * 53 ^ y * 31) & 0xFF;
                int colour = hash < 80 ? 0xFF_6E_8C_5E : hash < 170 ? 0xFF_A8_BC_8C : 0xFF_5E_7C_9E;
                draw.fill(mapX + x, mapY + y, mapX + x + 2, mapY + y + 2, colour);
            }
        }
        draw.outline(mapX - 1, mapY - 1, size + 2, size + 2, AWBookStyle.BRASS_DARK);

        // The cross-hair, drawn over the picture and beating, because a fix is a decision as much as
        // a place.
        int markX = mapX + size * 2 / 3;
        int markY = mapY + size / 3;
        float beat = AWAnim.pulse(ticks, 45.0F);
        int reach = 5 + Math.round(beat * 2.0F);
        draw.hLine(markX - reach, markY, reach * 2, AWBookStyle.RIFT_GLOW);
        draw.vLine(markX, markY - reach, reach * 2, AWBookStyle.RIFT_GLOW);
        draw.circle(markX, markY, 3, AWBookStyle.RIFT_INK);

        // No anchor at the far end: the dashed run out to it is the only thing joining them.
        for (int x = mapX - 14; x < markX - reach; x += 5) {
            draw.hLine(x, markY + 6, 3, AWAnim.fade(AWBookStyle.RIFT_INK, 0.7F));
        }
    }

    /** A ring of frame with an aperture standing in it. */
    private static void gate(AWDraw draw, int centreX, int centreY, float ticks) {
        int halfWidth = 20;
        int halfHeight = 20;
        // One pixel closer than looks natural on paper, because the hatching under the ground line
        // is four pixels deep and the box ends where it ends.
        ground(draw, centreX - 34, centreY + halfHeight + 1, 68);
        ring(draw, centreX, centreY, halfWidth, halfHeight);
        aperture(draw, centreX, centreY, halfWidth - 3, halfHeight - 3, ticks, 1.0F);
    }

    /** Two gates of different sizes, and a traveller crossing between them. */
    private static void gatePair(AWDraw draw, int centreX, int centreY, float ticks) {
        int leftX = centreX - 34;
        int rightX = centreX + 34;
        ground(draw, centreX - 56, centreY + 19, 112);

        ring(draw, leftX, centreY, 16, 18);
        aperture(draw, leftX, centreY, 13, 15, ticks, 1.0F);
        ring(draw, rightX, centreY + 6, 10, 12);
        aperture(draw, rightX, centreY + 6, 7, 9, ticks, 1.0F);

        // The traveller enters near the top left of the large opening and leaves by the top left of
        // the small one - the fraction of the opening carrying over, which is the rule worth drawing.
        float cycle = AWAnim.sweep(ticks, 120.0F);
        if (cycle < 0.45F) {
            float run = cycle / 0.45F;
            int x = Math.round(AWAnim.lerp(leftX - 30, leftX, run));
            draw.fill(x, centreY - 9, x + 4, centreY - 4, AWBookStyle.INK);
        } else if (cycle > 0.55F) {
            float run = (cycle - 0.55F) / 0.45F;
            int x = Math.round(AWAnim.lerp(rightX, rightX + 24, run));
            draw.fill(x, centreY - 1, x + 3, centreY + 3, AWBookStyle.INK);
        }
    }

    /** The frame around a gate's opening, laid as blocks rather than drawn as a line. */
    private static void ring(AWDraw draw, int centreX, int centreY,
                             int halfWidth, int halfHeight) {
        int block = 3;
        for (int x = centreX - halfWidth; x <= centreX + halfWidth - block; x += block) {
            frameBlock(draw, x, centreY - halfHeight, block);
            frameBlock(draw, x, centreY + halfHeight - block, block);
        }
        for (int y = centreY - halfHeight; y <= centreY + halfHeight - block; y += block) {
            frameBlock(draw, centreX - halfWidth, y, block);
            frameBlock(draw, centreX + halfWidth - block, y, block);
        }
    }

    private static void frameBlock(AWDraw draw, int x, int y, int size) {
        draw.fill(x, y, x + size, y + size, 0xFF_3A_2E_4A);
        draw.hLine(x, y, size, 0xFF_58_44_70);
    }

    /** What stands in the opening: bands of light, drifting, never still. */
    private static void aperture(AWDraw draw, int centreX, int centreY,
                                 int halfWidth, int halfHeight, float ticks, float strength) {
        draw.fill(centreX - halfWidth, centreY - halfHeight, centreX + halfWidth,
                centreY + halfHeight, AWAnim.fade(AWBookStyle.RIFT_INK, 0.55F * strength));
        for (int y = -halfHeight; y < halfHeight; y += 2) {
            float band = AWAnim.pulse(ticks + y * 6.0F, 60.0F);
            draw.hLine(centreX - halfWidth, centreY + y, halfWidth * 2,
                    AWAnim.fade(AWBookStyle.RIFT_GLOW, 0.10F + 0.28F * band * strength));
        }
    }

    /** The siphon, catching a draught each time a ship comes back out of a rift. */
    private static void siphon(AWDraw draw, int centreX, int centreY, float ticks) {
        int width = 22;
        int height = 26;
        int left = centreX - width / 2;
        int top = centreY - height / 2;

        // Filling in steps rather than smoothly: what a siphon catches arrives per journey.
        float cycle = AWAnim.sweep(ticks, 240.0F);
        int catches = (int) (cycle * 5.0F);
        float level = Math.min(1.0F, catches / 5.0F + 0.05F);
        int fill = Math.round((height - 6) * level);

        draw.fill(left, top + height - 4 - fill, left + width, top + height - 4,
                AWAnim.fade(AWBookStyle.RIFT_GLOW, 0.75F));
        // Whatever is in it moves; a still fluid looks like a coloured rectangle.
        for (int bubble = 0; bubble < 3; bubble++) {
            float rise = AWAnim.sweep(ticks + bubble * 40.0F, 100.0F);
            int y = top + height - 5 - Math.round(rise * fill);
            if (fill > 4) {
                int x = left + 5 + bubble * 6;
                draw.fill(x, y, x + 2, y + 2, AWAnim.fade(0xFF_FF_FF_FF, 0.5F * (1.0F - rise)));
            }
        }

        draw.outline(left, top, width, height, AWBookStyle.INK_SOFT);
        draw.hLine(left - 3, top + height - 4, width + 6, AWBookStyle.BRASS_DARK);
        draw.fill(left - 3, top + height - 4, left + width + 3, top + height, AWBookStyle.BRASS_DARK);

        // The arrival that just paid it: a small rift closing over on the left.
        float arrival = (cycle * 5.0F) % 1.0F;
        if (arrival < 0.4F) {
            tear(draw, centreX - 34, centreY, 12, 1.0F - arrival * 2.0F,
                    AWBookStyle.RIFT_INK, AWBookStyle.RIFT_GLOW);
        }
    }

    /** Two chutes, and an item making the jump between them. */
    private static void chute(AWDraw draw, int centreX, int centreY, float ticks) {
        int leftX = centreX - 34;
        int rightX = centreX + 22;

        machine(draw, leftX, centreY - 8, 14, 16);
        machine(draw, rightX, centreY - 2, 14, 16);

        float cycle = AWAnim.sweep(ticks, 90.0F);
        // The item is never in between: it goes into one aperture and out of the other, which is the
        // point of a chute and would be lost if it simply slid across the gap.
        boolean sending = cycle < 0.5F;
        float run = (sending ? cycle : cycle - 0.5F) * 2.0F;
        int mouthLeft = leftX + 14;
        int mouthRight = rightX;

        aperture(draw, mouthLeft + 2, centreY, 2, 6, ticks, sending ? 1.0F : 0.35F);
        aperture(draw, mouthRight - 2, centreY + 6, 2, 6, ticks, sending ? 0.35F : 1.0F);

        int x = sending
                ? Math.round(AWAnim.lerp(leftX + 3, mouthLeft, run))
                : Math.round(AWAnim.lerp(mouthRight, rightX + 10, run));
        int y = sending ? centreY - 2 : centreY + 4;
        draw.fill(x, y, x + 4, y + 4, AWBookStyle.BRASS);
        draw.hLine(x, y, 4, AWBookStyle.BRASS_DARK);
    }

    /** The console's checklist, ticking itself off and stopping at the line that fails. */
    private static void checklist(AWDraw draw, int centreX, int centreY, float ticks) {
        int left = centreX - 40;
        int top = centreY - 22;
        int rows = 5;

        draw.fill(left - 4, top - 3, left + 84, top + rows * 9 + 2, 0x22_35_2C_20);
        draw.outline(left - 4, top - 3, 88, rows * 9 + 5, AWBookStyle.RULE);

        float cycle = AWAnim.sweep(ticks, 200.0F);
        int ticked = Math.min(rows - 1, (int) (cycle * (rows + 1)));

        for (int row = 0; row < rows; row++) {
            int y = top + row * 9;
            boolean met = row < ticked;
            boolean failing = row == rows - 1;
            int colour = met ? 0xFF_2E_7D_5B : failing && ticked >= rows - 1
                    ? AWAnim.blend(AWBookStyle.INK_SOFT, 0xFF_B0_3A_2A, AWAnim.pulse(ticks, 30.0F))
                    : AWBookStyle.INK_SOFT;

            draw.outline(left, y, 6, 6, colour);
            if (met) {
                draw.fill(left + 2, y + 2, left + 4, y + 4, colour);
            }
            // The lines of the list, as lines rather than as words: a diagram that spelled out five
            // requirements would be five more strings to translate and would say what the console
            // already says better.
            draw.fill(left + 10, y + 2, left + 10 + 46 + (row * 13) % 20, y + 4,
                    AWAnim.fade(colour, met ? 0.8F : 0.4F));
        }
    }
}
