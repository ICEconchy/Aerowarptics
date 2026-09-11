package uk.co.iceconchy.aerowarptics.client.screen;

import uk.co.iceconchy.aerowarptics.modulator.RiftModulatorTheme;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * The little flurry of shapes a Rift Modulator's panel throws up when its theme is changed.
 *
 * <p>Purely decoration, and purely to answer one question the moment a player clicks the theme button:
 * what did I just pick? A word on the button says it, but a handful of cogs tumbling up the panel for
 * a clockwork rift, or runes drifting for an arcane one, says it without being read. The motif is the
 * message - each theme fans out its own shape, so the flourish and the button agree at a glance.
 *
 * <p>Free of Minecraft, like {@link AWLayout} and {@link AWAnim} beside it, and for the same reason:
 * the failure mode of a particle system is not a crash but a mote that never dies, or a burst that
 * marches off the panel, or a rune drawn as an empty box - none of which throw, and all of which
 * {@code ThemeMotesTest} can catch by handing this an {@link AWDraw} that records rather than paints.
 * Everything it draws goes through that one interface, exactly as the handbook's diagrams do.
 *
 * <p>Coordinates are window-relative, the same frame {@code AWLayout} works in, so a screen adds its
 * {@code guiLeft}/{@code guiTop} at draw time and this class never has to know where the window sits.
 */
public final class ThemeMotes {

    /** The shape a burst fans out. One per theme, mapped by {@link #motifFor}. */
    public enum Motif {
        /** A flickering plus - the standard rift's electric spark. */
        SPARK,
        /** A soft rising cinder - the ember rift smouldering. */
        EMBER,
        /** A four-point twinkle - the starlight rift glittering. */
        STAR,
        /** A small angular glyph, slowly turning - the arcane rift's rune-ring. */
        RUNE,
        /** A toothed wheel, tumbling - the clockwork rift's gears letting go. */
        COG,
        /** A hard line with a bright head, thrown fast - a star drawn out by a jump. */
        STREAK,
        /** A dark disc inside a bright ring, falling inwards - something with a hole in the middle. */
        WELL,
        /** A flattened lozenge, drifting level - the stretched lens. */
        LENS,
        /** A small woven square of crossing bars. The joke, in eight pixels. */
        CHECK,
        /** A small core inside rings turning on crossed axes - a gravity drive's gimbal. */
        GYRO,
        /** A police box, blinking out and back as it tries to land. */
        BOX,
        /** Whichever of the others it felt like being, decided when it was thrown. */
        ODDMENT,
    }

    /**
     * Which shape a theme throws.
     *
     * <p>A total mapping with no default branch on purpose: a sixth theme added to
     * {@link RiftModulatorTheme} without a motif here should fail to compile, not silently fall back to
     * sparks, which is the sort of thing nobody would notice until the new theme's flourish looked
     * like the old one's.
     */
    public static Motif motifFor(RiftModulatorTheme theme) {
        return switch (theme) {
            case STANDARD -> Motif.SPARK;
            case EMBER -> Motif.EMBER;
            case STARLIGHT -> Motif.STAR;
            case ARCANE -> Motif.RUNE;
            case CLOCKWORK -> Motif.COG;
            case STARBLOCKS -> Motif.STREAK;
            case BEDROCK -> Motif.WELL;
            case BOLDLY_GONE -> Motif.LENS;
            case LUDICROUS -> Motif.CHECK;
            case EVENTFUL_HORIZON -> Motif.GYRO;
            case VWORP -> Motif.BOX;
            case IMPROBABILITY -> Motif.ODDMENT;
        };
    }

    /** How many motes one burst throws. Enough to read as a flourish, few enough to stay tidy. */
    private static final int BURST = 16;

    /**
     * A hard cap on live motes, so leaning on the theme button cannot grow the list without bound.
     * The oldest are dropped first - a fresh burst matters more than the tail of the one before it.
     */
    private static final int MAX = 160;

    private final Random random;
    private final List<Mote> motes = new ArrayList<>();

    public ThemeMotes() {
        this(new Random());
    }

    /** Seeded, for tests that want the same burst twice. */
    ThemeMotes(Random random) {
        this.random = random;
    }

    /** How many motes are currently alive. */
    public int count() {
        return motes.size();
    }

    /**
     * Throws a burst of this theme's motif from a point, fanned upward.
     *
     * <p>The fan is biased into the upper half because the theme button sits at the foot of the panel,
     * so its motes rise across the window rather than immediately falling off the bottom edge under it.
     */
    public void burst(RiftModulatorTheme theme, float originX, float originY) {
        Motif motif = motifFor(theme);
        for (int i = 0; i < BURST; i++) {
            motes.add(spawn(motif, originX, originY));
        }
        // Drop the oldest rather than refusing the new burst: the click that asked for it should always
        // be answered, and the stalest motes are the ones already fading.
        while (motes.size() > MAX) {
            motes.remove(0);
        }
    }

    public void tick() {
        for (Iterator<Mote> it = motes.iterator(); it.hasNext(); ) {
            Mote mote = it.next();
            mote.tick();
            if (mote.dead()) {
                it.remove();
            }
        }
    }

    /**
     * Draws every live mote, interpolated between ticks.
     *
     * @param partialTicks fraction of a tick since the last {@link #tick}, so motion is smooth rather
     *                     than the twenty-steps-a-second the tick rate would give on its own
     */
    public void render(AWDraw draw, float partialTicks) {
        for (Mote mote : motes) {
            mote.render(draw, partialTicks);
        }
    }

    // ------------------------------------------------------------------ spawn

    private Mote spawn(Motif motif, float originX, float originY) {
        // A wide upward fan: straight up is -PI/2 (y grows downward), and the spread reaches almost to
        // the horizontal either side, so a burst opens like a fountain rather than a narrow jet.
        double angle = -Math.PI / 2.0D + (random.nextDouble() - 0.5D) * Math.PI * 1.3D;
        Mote mote = new Mote();
        mote.motif = motif;
        mote.x = originX;
        mote.y = originY;

        switch (motif) {
            case SPARK -> {
                float speed = rand(1.6F, 3.0F);
                mote.vx = (float) Math.cos(angle) * speed;
                mote.vy = (float) Math.sin(angle) * speed;
                mote.gravity = 0.07F;
                mote.drag = 0.90F;
                mote.life = randInt(8, 15);
                mote.size = randInt(3, 5);
                mote.colour = pick(0xFF_C8_6C_FF, 0xFF_E8_D0_FF);
                mote.twinkle = true;
            }
            case EMBER -> {
                float speed = rand(0.3F, 0.9F);
                mote.vx = (float) Math.cos(angle) * speed;
                mote.vy = (float) Math.sin(angle) * speed;
                mote.gravity = -0.045F;
                mote.drag = 0.94F;
                mote.life = randInt(30, 52);
                mote.size = randInt(2, 4);
                mote.colour = pick(0xFF_FF_7A_2A, 0xFF_FF_B0_4A);
            }
            case STAR -> {
                float speed = rand(0.2F, 0.7F);
                mote.vx = (float) Math.cos(angle) * speed;
                mote.vy = (float) Math.sin(angle) * speed;
                mote.gravity = 0.0F;
                mote.drag = 0.96F;
                mote.life = randInt(32, 60);
                mote.size = randInt(3, 5);
                mote.spin = rand(-0.03F, 0.03F);
                mote.colour = pick(0xFF_EA_F2_FF, 0xFF_BF_D8_FF);
                mote.twinkle = true;
            }
            case RUNE -> {
                float speed = rand(0.25F, 0.7F);
                mote.vx = (float) Math.cos(angle) * speed;
                mote.vy = (float) Math.sin(angle) * speed;
                mote.gravity = -0.02F;
                mote.drag = 0.95F;
                mote.life = randInt(32, 54);
                mote.size = randInt(4, 6);
                mote.spin = rand(-0.04F, 0.04F);
                mote.colour = pick(0xFF_B0_60_FF, 0xFF_E0_A0_FF);
                mote.glyph = randInt(0, RUNE_GLYPHS.length - 1);
            }
            case COG -> {
                float speed = rand(0.6F, 1.5F);
                mote.vx = (float) Math.cos(angle) * speed;
                mote.vy = (float) Math.sin(angle) * speed;
                mote.gravity = 0.06F;
                mote.drag = 0.93F;
                mote.life = randInt(26, 46);
                mote.size = randInt(4, 7);
                // A cog reads as a cog only if it is visibly turning, so the spin is never near zero.
                mote.spin = (random.nextBoolean() ? 1 : -1) * rand(0.06F, 0.14F);
                mote.colour = pick(0xFF_C8_A0_50, 0xFF_9A_78_38);
            }
            case STREAK -> {
                // Much the fastest thing here, and the shortest lived. A streak that lingered would be
                // a line lying on the panel rather than something going past it.
                float speed = rand(4.5F, 7.5F);
                mote.vx = (float) Math.cos(angle) * speed;
                mote.vy = (float) Math.sin(angle) * speed;
                mote.gravity = 0.0F;
                mote.drag = 0.97F;
                mote.life = randInt(6, 11);
                mote.size = randInt(5, 9);
                mote.colour = pick(0xFF_DCE8FF, 0xFF_FFFFFF);
            }
            case WELL -> {
                // Falls in rather than out: slow, and pulled back the way it came.
                float speed = rand(0.8F, 1.6F);
                mote.vx = (float) Math.cos(angle) * speed;
                mote.vy = (float) Math.sin(angle) * speed;
                mote.gravity = 0.10F;
                mote.drag = 0.88F;
                mote.life = randInt(24, 40);
                mote.size = randInt(3, 6);
                mote.spin = rand(-0.05F, 0.05F);
                // The event horizon's gold, and the white of its hot inner edge.
                mote.colour = pick(0xFF_FFD68A, 0xFF_FFF4DC);
            }
            case LENS -> {
                // Level, because a lens drifting on a curve would look thrown, and the whole of this
                // theme is that nothing about it is violent.
                float speed = rand(0.9F, 1.8F);
                mote.vx = (float) Math.cos(angle) * speed;
                mote.vy = (float) Math.sin(angle) * speed * 0.35F;
                mote.gravity = 0.0F;
                mote.drag = 0.95F;
                mote.life = randInt(26, 44);
                mote.size = randInt(4, 7);
                mote.colour = pick(0xFF_9AD8FF, 0xFF_E8F6FF);
            }
            case CHECK -> {
                float speed = rand(1.0F, 2.2F);
                mote.vx = (float) Math.cos(angle) * speed;
                mote.vy = (float) Math.sin(angle) * speed;
                mote.gravity = 0.08F;
                mote.drag = 0.92F;
                mote.life = randInt(22, 40);
                mote.size = randInt(4, 7);
                // Four of the sett's threads, so a scatter of these reads as tartan rather than as tiles.
                mote.glyph = randInt(0, 3);
                mote.colour = 0xFF_FFFFFF;
            }
            case GYRO -> {
                // Thrown out and spinning hard - the gimbal is the one part of the drive never still.
                float speed = rand(0.8F, 1.8F);
                mote.vx = (float) Math.cos(angle) * speed;
                mote.vy = (float) Math.sin(angle) * speed;
                mote.gravity = 0.03F;
                mote.drag = 0.93F;
                mote.life = randInt(26, 44);
                mote.size = randInt(4, 7);
                mote.spin = (random.nextBoolean() ? 1 : -1) * rand(0.05F, 0.12F);
                // Cold arc-light and the drive's red.
                mote.colour = pick(0xFF_8FC8FF, 0xFF_E0403A);
            }
            case BOX -> {
                float speed = rand(0.7F, 1.6F);
                mote.vx = (float) Math.cos(angle) * speed;
                mote.vy = (float) Math.sin(angle) * speed;
                mote.gravity = -0.01F;
                mote.drag = 0.94F;
                mote.life = randInt(28, 48);
                mote.size = randInt(3, 6);
                mote.twinkle = true;
                // Police-box blue, lifted so it reads on the panel.
                mote.colour = pick(0xFF_4F7FE0, 0xFF_8FB2FF);
            }
            case ODDMENT -> {
                // Every knob rolled, including which shape it will draw as. Two of these agreeing on
                // anything is a coincidence, which is the point.
                float speed = rand(0.3F, 4.0F);
                mote.vx = (float) Math.cos(angle) * speed;
                mote.vy = (float) Math.sin(angle) * speed;
                mote.gravity = rand(-0.12F, 0.20F);
                mote.drag = rand(0.86F, 0.99F);
                mote.life = randInt(10, 55);
                mote.size = randInt(2, 8);
                mote.spin = rand(-0.16F, 0.16F);
                mote.twinkle = random.nextBoolean();
                // Anything but ODDMENT itself, so a roll always lands on a shape that draws.
                mote.glyph = randInt(0, Motif.values().length - 2);
                mote.colour = 0xFF_000000
                        | (randInt(80, 255) << 16) | (randInt(80, 255) << 8) | randInt(80, 255);
            }
        }
        return mote;
    }

    private float rand(float from, float to) {
        return from + random.nextFloat() * (to - from);
    }

    private int randInt(int from, int to) {
        return from + random.nextInt(to - from + 1);
    }

    private int pick(int a, int b) {
        return random.nextBoolean() ? a : b;
    }

    // -------------------------------------------------------------- one mote

    /** A single travelling shape. Kept package-private and mutable - it is a value, spawned in bulk. */
    static final class Mote {

        Motif motif;
        float x;
        float y;
        float vx;
        float vy;
        float gravity;
        float drag = 1.0F;
        float angle;
        float spin;
        int age;
        int life = 1;
        int size = 3;
        int colour = 0xFF_FF_FF_FF;
        int glyph;
        boolean twinkle;

        void tick() {
            x += vx;
            y += vy;
            vx *= drag;
            vy = vy * drag + gravity;
            angle += spin;
            age++;
        }

        boolean dead() {
            return age >= life;
        }

        void render(AWDraw draw, float partialTicks) {
            float t = (age + partialTicks) / life;
            if (t >= 1.0F) {
                return;
            }
            // Fade in over the first couple of ticks so a burst blooms rather than blinks on, then out
            // on a curve that lingers bright before dropping - a linear fade reads as a dimmer switch,
            // this reads as a spark going cold.
            float in = AWAnim.clamp((age + partialTicks) / 2.0F);
            float alpha = in * (1.0F - t * t);
            if (twinkle) {
                alpha *= 0.55F + 0.45F * AWAnim.pulse(age + partialTicks, 6.0F);
            }
            if (alpha <= 0.02F) {
                return;
            }

            int cx = Math.round(x + vx * partialTicks);
            int cy = Math.round(y + vy * partialTicks);
            int argb = AWAnim.fade(colour, alpha);
            float turn = angle + spin * partialTicks;

            // An oddment draws as whatever shape it rolled when it was thrown, which is why this is
            // resolved here rather than in the switch below - the switch is about shapes, and ODDMENT
            // is not a shape.
            Motif drawn = motif == Motif.ODDMENT
                    ? Motif.values()[Math.floorMod(glyph, Motif.values().length - 1)]
                    : motif;

            switch (drawn) {
                case SPARK -> spark(draw, cx, cy, size, argb);
                case EMBER -> ember(draw, cx, cy, size, argb);
                case STAR -> star(draw, cx, cy, size, turn, argb);
                case RUNE -> rune(draw, cx, cy, size, turn, argb, glyph);
                case COG -> cog(draw, cx, cy, size, turn, argb);
                case STREAK -> streak(draw, cx, cy, size, vx, vy, argb);
                case WELL -> well(draw, cx, cy, size, argb);
                case LENS -> lens(draw, cx, cy, size, argb);
                case CHECK -> check(draw, cx, cy, size, argb, glyph);
                case GYRO -> gyro(draw, cx, cy, size, turn, argb);
                case BOX -> box(draw, cx, cy, size, argb);
                // Unreachable: resolved to a real shape above.
                case ODDMENT -> spark(draw, cx, cy, size, argb);
            }
        }
    }

    // ------------------------------------------------------------------ shapes

    /** A plus with a bright heart: the standard rift's electric spark. */
    private static void spark(AWDraw draw, int cx, int cy, int size, int argb) {
        draw.hLine(cx - size, cy, size * 2 + 1, argb);
        draw.vLine(cx, cy - size, size * 2 + 1, argb);
        draw.fill(cx, cy, cx + 1, cy + 1, whiten(argb));
    }

    /** A soft cinder with a hot core. */
    private static void ember(AWDraw draw, int cx, int cy, int size, int argb) {
        draw.disc(cx, cy, size, AWAnim.fade(argb, 0.7F));
        draw.fill(cx, cy, cx + 1, cy + 1, whiten(argb));
    }

    /** A four-point sparkle, its long rays turning, with a fainter diagonal cross between them. */
    private static void star(AWDraw draw, int cx, int cy, int size, float turn, int argb) {
        for (int arm = 0; arm < 4; arm++) {
            double a = turn * TWO_PI + arm * (Math.PI / 2.0D);
            ray(draw, cx, cy, a, size, argb);
        }
        int faint = AWAnim.fade(argb, 0.5F);
        int half = Math.max(1, size / 2);
        for (int arm = 0; arm < 4; arm++) {
            double a = turn * TWO_PI + Math.PI / 4.0D + arm * (Math.PI / 2.0D);
            ray(draw, cx, cy, a, half, faint);
        }
        draw.fill(cx, cy, cx + 1, cy + 1, whiten(argb));
    }

    /** A small glyph built from a few strokes, rotated as a whole. */
    private static void rune(AWDraw draw, int cx, int cy, int size, float turn, int argb, int glyph) {
        float[] strokes = RUNE_GLYPHS[Math.floorMod(glyph, RUNE_GLYPHS.length)];
        double cos = Math.cos(turn * TWO_PI);
        double sin = Math.sin(turn * TWO_PI);
        for (int i = 0; i + 3 < strokes.length; i += 4) {
            int x0 = cx + rot(strokes[i], strokes[i + 1], cos, sin, size, true);
            int y0 = cy + rot(strokes[i], strokes[i + 1], cos, sin, size, false);
            int x1 = cx + rot(strokes[i + 2], strokes[i + 3], cos, sin, size, true);
            int y1 = cy + rot(strokes[i + 2], strokes[i + 3], cos, sin, size, false);
            draw.line(x0, y0, x1, y1, argb);
        }
        draw.fill(cx, cy, cx + 1, cy + 1, whiten(argb));
    }

    /** A toothed wheel with a dark hub, turning about its centre. */
    private static void cog(AWDraw draw, int cx, int cy, int size, float turn, int argb) {
        draw.disc(cx, cy, size, argb);
        int teeth = 8;
        for (int k = 0; k < teeth; k++) {
            double a = turn * TWO_PI + k * (TWO_PI / teeth);
            int tx = cx + (int) Math.round(Math.cos(a) * (size + 1.4D));
            int ty = cy + (int) Math.round(Math.sin(a) * (size + 1.4D));
            draw.fill(tx - 1, ty - 1, tx + 1, ty + 1, argb);
        }
        // A hub darker than the rim, and a one-pixel axle hole in the middle of it, so a small wheel
        // still reads as a gear rather than a plain dot.
        int hub = Math.max(1, size / 2);
        draw.disc(cx, cy, hub, darken(argb));
        draw.fill(cx, cy, cx + 1, cy + 1, darken(darken(argb)));
    }

    /**
     * A line lying along the way the mote is going, with a bright head at the front.
     *
     * <p>Drawn from its own velocity rather than from an angle it was given, so a streak always
     * points where it is travelling. Anything else and a fast mote would slide sideways, which is the
     * one thing a streak must never do.
     */
    private static void streak(AWDraw draw, int cx, int cy, int size, float vx, float vy, int argb) {
        float speed = (float) Math.sqrt(vx * vx + vy * vy);
        if (speed < 0.01F) {
            draw.fill(cx, cy, cx + 1, cy + 1, argb);
            return;
        }
        int tailX = cx - Math.round(vx / speed * size);
        int tailY = cy - Math.round(vy / speed * size);
        // The tail is dimmer than the head, so the line reads as having a direction.
        draw.line(tailX, tailY, cx, cy, AWAnim.fade(argb, 0.45F));
        draw.line(cx, cy, cx + Math.round(vx / speed), cy + Math.round(vy / speed), argb);
        draw.fill(cx, cy, cx + 1, cy + 1, whiten(argb));
    }

    /** A bright ring with nothing inside it - the hole is the shape. */
    private static void well(AWDraw draw, int cx, int cy, int size, int argb) {
        draw.circle(cx, cy, size, argb);
        draw.circle(cx, cy, Math.max(1, size - 1), AWAnim.fade(argb, 0.5F));
        // Deliberately no centre mark: every other motif here has a hot core, and this one's whole
        // identity is that it has not.
    }

    /** A flattened lozenge: wide, shallow, and level. */
    private static void lens(AWDraw draw, int cx, int cy, int size, int argb) {
        int half = Math.max(1, size / 3);
        for (int dy = -half; dy <= half; dy++) {
            // An ellipse's half-width at this row, so the shape tapers to points at both ends.
            int wide = (int) Math.round(size * Math.sqrt(Math.max(0.0D, 1.0D - (dy / (double) half) * (dy / (double) half))));
            if (wide <= 0) {
                continue;
            }
            int shade = dy == 0 ? argb : AWAnim.fade(argb, 0.55F);
            draw.fill(cx - wide, cy + dy, cx + wide + 1, cy + dy + 1, shade);
        }
        draw.fill(cx, cy, cx + 1, cy + 1, whiten(argb));
    }

    /** A woven square: a wide bar each way, crossing, in one of the sett's threads. */
    private static void check(AWDraw draw, int cx, int cy, int size, int argb, int thread) {
        int half = Math.max(2, size);
        int band = Math.max(1, size / 3);
        // The tartan's own red, navy, green and yellow - navy and green lifted so they read on the panel.
        int[] sett = {0xFF_C8102E, 0xFF_2A4BA8, 0xFF_1E7A4F, 0xFF_FFD100};
        int colour = AWAnim.fade(sett[Math.floorMod(thread, sett.length)], alphaOf(argb));
        // The ground first, then the two bars over it, which is the order that makes the crossing
        // read as cloth rather than as a plus sign.
        draw.fill(cx - half, cy - half, cx + half + 1, cy + half + 1, AWAnim.fade(colour, 0.25F));
        draw.fill(cx - band, cy - half, cx + band + 1, cy + half + 1, colour);
        draw.fill(cx - half, cy - band, cx + half + 1, cy + band + 1, AWAnim.fade(colour, 0.75F));
    }

    /**
     * A small bright core inside two rings seen at changing angles - the gravity drive's gimbal.
     *
     * <p>The rings are ellipses whose width swings with the mote's turn, on crossed axes, so a tumbling
     * gyro reads as rings spinning round a sphere rather than as a circle with lines on it.
     */
    private static void gyro(AWDraw draw, int cx, int cy, int size, float turn, int argb) {
        double angle = turn * TWO_PI;
        ellipse(draw, cx, cy, size, (float) Math.abs(Math.cos(angle)), true, argb);
        ellipse(draw, cx, cy, size, (float) Math.abs(Math.sin(angle)), false, AWAnim.fade(argb, 0.7F));
        draw.disc(cx, cy, Math.max(1, size / 3), whiten(argb));
    }

    /** An ellipse's outline, squashed on one axis, sampled by angle as {@link AWDraw#circle} is. */
    private static void ellipse(AWDraw draw, int cx, int cy, int radius, float squash, boolean flatten,
                                int argb) {
        int steps = Math.max(12, radius * 8);
        for (int step = 0; step < steps; step++) {
            double a = step * TWO_PI / steps;
            int px = cx + (int) Math.round(Math.cos(a) * radius * (flatten ? 1.0D : squash));
            int py = cy + (int) Math.round(Math.sin(a) * radius * (flatten ? squash : 1.0D));
            draw.fill(px, py, px + 1, py + 1, argb);
        }
    }

    /** A rectangle outline, taller than it is wide. */
    private static void box(AWDraw draw, int cx, int cy, int size, int argb) {
        int wide = Math.max(2, size);
        int tall = Math.max(3, (int) (size * 1.6F));
        draw.outline(cx - wide, cy - tall, wide * 2 + 1, tall * 2 + 1, argb);
        // The lamp on top, which is what makes a rectangle a shape rather than a rectangle.
        draw.fill(cx, cy - tall - 1, cx + 1, cy - tall, whiten(argb));
    }

    /** The alpha channel of a packed colour, so a themed palette can borrow a mote's own fade. */
    private static float alphaOf(int argb) {
        return ((argb >>> 24) & 0xFF) / 255.0F;
    }

    /** One straight ray from a centre, at an angle, for the star's points. */
    private static void ray(AWDraw draw, int cx, int cy, double angle, int length, int argb) {
        int x1 = cx + (int) Math.round(Math.cos(angle) * length);
        int y1 = cy + (int) Math.round(Math.sin(angle) * length);
        draw.line(cx, cy, x1, y1, argb);
    }

    /** Rotates a unit-space glyph point and scales it to pixels, returning one axis. */
    private static int rot(float ux, float uy, double cos, double sin, int size, boolean xAxis) {
        double v = xAxis ? ux * cos - uy * sin : ux * sin + uy * cos;
        return (int) Math.round(v * size);
    }

    private static final double TWO_PI = Math.PI * 2.0D;

    /**
     * The arcane runes, each a list of strokes in unit space {@code [-1, 1]}: {@code x0, y0, x1, y1}
     * per stroke. Deliberately angular and a little asymmetric, so a slow turn reads as a mark rotating
     * rather than a symmetric shape sitting still.
     */
    private static final float[][] RUNE_GLYPHS = {
            // A staff with two branches.
            {0F, -1F, 0F, 1F, 0F, -0.3F, 0.7F, -0.8F, 0F, 0.2F, -0.7F, 0.7F},
            // A bent zig-zag through a bar.
            {-0.7F, -0.7F, 0.2F, 0F, 0.2F, 0F, -0.7F, 0.7F, 0.2F, 0F, 0.8F, 0F},
            // A cross with a hook.
            {0F, -0.9F, 0F, 0.9F, -0.7F, -0.2F, 0.7F, -0.2F, 0.7F, -0.2F, 0.4F, 0.4F},
    };

    // ------------------------------------------------------------------ colour

    /** Toward white, for a mote's hot core. */
    private static int whiten(int argb) {
        return AWAnim.blend(argb, 0xFF_FF_FF_FF, 0.6F);
    }

    /** Toward black, keeping the alpha, for a cog's hub. */
    private static int darken(int argb) {
        int black = argb & 0xFF_00_00_00;
        return AWAnim.blend(argb, black, 0.5F);
    }
}
