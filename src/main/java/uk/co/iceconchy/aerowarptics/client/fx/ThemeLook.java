package uk.co.iceconchy.aerowarptics.client.fx;

import uk.co.iceconchy.aerowarptics.modulator.RiftModulatorTheme;

/**
 * What the themes look like before a single stroke is drawn: their colours, whether they crackle, what
 * their face is made of, when their wind-up makes a sound, and the one woven sett Ludicrous Speed is
 * cut from.
 *
 * <h2>Why these themes ignore the swatch</h2>
 * The five original themes separate themselves by shape and motion and wear whatever colours the
 * pilot picked - a hell portal in pale blue is a perfectly reasonable thing to want. The borrowed seven
 * are different in kind: each is trying to look like something the player has already seen, and most
 * of what makes a jump to hyperspace recognisable is that it is blue and white. In pink it is a pink
 * spiral. So each of them brings its own palette, applied when the rift is created so the face, the
 * bore, the fire, the glass and the lightning all agree. The Modulator's intensity still applies; its
 * swatch does not.
 *
 * <p>Ender is the same case - the End is teal and violet, and a pink one is not the End - and so is
 * Mostly Dangerous, whose cloud is black because that is the whole of it. Sketch is the exception among
 * the later themes: what it draws with is its own - graphite, white, cartridge paper -
 * but what it draws <em>with them</em> is the pilot's two swatches, which are the crayons it colours the
 * hole in and lines it with. So it wears the swatch like the five originals do.
 *
 * <p>Free of Minecraft, like {@link RiftShatter}'s patterns and for the same reason: the ways this goes
 * wrong - a rim drawn additively in a colour too dark to add anything, a tartan thread that is
 * invisible, a palette two themes share - do not throw, and {@code ThemeLookTest} is what notices.
 */
public final class ThemeLook {

    /**
     * A rift's two colours.
     *
     * @param core the body of the rift - the face's middle, the bore, the fire at the heart of it
     * @param rim  the torn edge, and anything that has to glow against the dark: the rim is drawn
     *             additively, so it must be bright enough to add something
     */
    public record Palette(int core, int rim) {
    }

    /**
     * What an aperture's face and bore are made of.
     *
     * <p>Every theme but two draws an opaque membrane that is dark in the middle and burns at the rim,
     * and lays additive light over it. The other two are different in kind rather than in colour, which
     * is why this is a property of its own rather than something a palette could say.
     */
    public enum Surface {
        /** The membrane every rift has always had: dark at the heart, hot at the torn edge, lit by fire. */
        LIT,
        /**
         * Paper: the membrane drawn flat and pale, and nothing drawn on it as light. Everything a paper
         * theme adds is <em>ink</em>, which is dark, and additive light cannot draw dark - so it goes on
         * the translucent glass pass instead, where a stroke genuinely darkens what is under it.
         */
        PAPER,
        /**
         * The End portal's own starfield. Still opaque and still writing depth, so it hides a hull
         * exactly as the membrane does - it only paints the occluder with vanilla's shader rather than a
         * colour.
         */
        END_SKY,
        /**
         * An ink-black cloud. The face and bore are still the opaque membrane, and still hide a hull the
         * way it always has, but black to the edge with no fire burning round them - the light a cloud
         * has is lightning, inside it and across it. And it reaches far past its own rim, as soft dark
         * puffs on the translucent pass: dark, like ink, so they cannot go on the additive one either.
         */
        NEBULA
    }

    // ------------------------------------------------------------------ colours

    /** Hyperspace's blue: the tunnel between the starlines. */
    public static final int HYPERSPACE_BLUE = 0x1E4BFF;
    /** The paler blue the tunnel mottles through. */
    public static final int HYPERSPACE_PALE = 0x8EC8FF;
    /** A star drawn out into a line. Not quite white, which is what stops it reading as paper. */
    public static final int STARLINE_WHITE = 0xE8F4FF;

    /** A singularity: as near black as the opaque face can be drawn. */
    public static final int VOID = 0x050308;
    /** Light bent right round a black hole - warm, and the brightest thing near it. */
    public static final int HORIZON_GOLD = 0xFFD68A;
    /** The thin hot inner edge of that halo. */
    public static final int HORIZON_WHITE = 0xFFF4DC;

    /** A warp drive's blue. */
    public static final int WARP_BLUE = 0x1560FF;
    /** The flash as the drive engages. */
    public static final int WARP_FLASH = 0x9FE8FF;

    /** A gravity drive's wormhole: red going to black. */
    public static final int GRAVITY_RED = 0x3A0006;
    /** The same red, lit - for the parts of that wormhole that have to glow. */
    public static final int GRAVITY_GLOW = 0xB0102A;
    /** The cold arc-light that crawls over the drive's core. */
    public static final int GRAVITY_ARC = 0x8FC8FF;

    /** The time vortex's blue. */
    public static final int VORTEX_BLUE = 0x1B3DFF;
    /** That blue brightened enough to be drawn additively and still read as blue. */
    public static final int VORTEX_BLUE_LIT = 0x3A6BFF;
    /** The time vortex's orange. */
    public static final int VORTEX_ORANGE = 0xFF8A1E;
    /** A police box's blue, lifted so it survives being drawn as light rather than as paint. */
    public static final int POLICE_BOX = 0x4F7FE0;

    public static final int TARTAN_RED = 0xC8102E;
    public static final int TARTAN_NAVY = 0x0B1E5B;
    public static final int TARTAN_GREEN = 0x00563F;
    public static final int TARTAN_YELLOW = 0xFFD100;
    public static final int TARTAN_WHITE = 0xF4F4F4;

    /**
     * The dark between the End's stars. Teal rather than black, because it is what the haze, the fire's
     * heart and the far light are tinted with, and the End portal's own speckle is teal.
     */
    public static final int END_VOID = 0x1F5E5A;
    /** An enderman's eye, as light: the pink-violet it glows at its brightest. */
    public static final int ENDER_EYE_LIT = 0xE079FA;
    /** The same eye's deeper magenta, for the glow round it. */
    public static final int ENDER_EYE = 0xCC00FA;
    /** The near-white heart of the eye - the part that seems to follow you. */
    public static final int ENDER_EYE_HOT = 0xFBDDFF;

    /**
     * The cloud a capital ship arrives in. Not quite black: a hair of violet, so that its thin edge reads
     * as a cloud against a night sky rather than as a hole cut in it.
     */
    public static final int NEBULA_BLACK = 0x07050C;
    /** The three colours its lightning comes in: electric blue, violet, and white where it is hottest. */
    public static final int ARC_BLUE = 0x55AEFF;
    public static final int ARC_VIOLET = 0xB27DFF;
    public static final int ARC_WHITE = 0xF2F0FF;

    /** Sketchbook paper. Warm rather than white, because white paper reads as a missing texture. */
    public static final int PAPER = 0xF2EADA;
    /** The same paper where the light catches it. Never drawn as light - paper does not glow. */
    public static final int PAPER_LIT = 0xFFF9EE;
    /** A soft pencil's grey, a touch cool, so it reads as graphite rather than as black ink. */
    public static final int GRAPHITE = 0x33323A;

    // -------------------------------------------------------------------- beats

    /**
     * How far into the wind-up the enderman's eyes begin to open.
     *
     * <p>Late enough that the motes have gathered and the End has begun to bleed through, and early
     * enough that the stare has a moment to be a stare before the pane goes.
     */
    public static final float STARE = 0.35F;
    /** How much of the wind-up the eyes take to open fully. */
    public static final float STARE_OPENING = 0.25F;

    /**
     * How far into the wind-up the cloud is heard gathering: straight away, because out of nowhere is the
     * point of it.
     */
    public static final float GATHER = 0.05F;

    private static final float[] NO_BEATS = {};

    /** Ticks a rift takes to let go once it is told to. */
    private static final int CLOSE = 25;
    /**
     * How long Sketch takes instead.
     *
     * <p>Longer, because its close is an animation rather than a fade: the pencil has to come back,
     * scribble the whole drawing out, crumple it up and lob it away, and at twenty-five ticks each of
     * those is over before it reads. Nothing is waiting on it - the hull left the aperture before it was
     * told to close - so the only cost is that the drawing hangs about for another three quarters of a
     * second.
     */
    private static final int SKETCH_CLOSE = 40;
    /**
     * How long Mostly Dangerous takes.
     *
     * <p>Not because the collapse is slow - it is the opposite, over in the first third of this - but
     * because what it leaves behind has to have somewhere to be: the trails hanging where the ship came
     * out, fading. The rift is dropped at the end of its close, and anything still drawn then goes with
     * it.
     */
    private static final int NEBULA_CLOSE = 45;

    // -------------------------------------------------------------------- sett

    /**
     * The threads of the tartan, in order across one repeat.
     *
     * <p>Red-dominant with navy and green between and single thin lines of yellow and white, which is
     * the shape of a Royal Stewart without claiming to be one. The widths matter more than the colours:
     * a sett of even bands is a check, and only uneven ones read as cloth.
     */
    private static final int[] SETT_COLOURS = {TARTAN_RED, TARTAN_NAVY, TARTAN_RED, TARTAN_NAVY,
            TARTAN_GREEN, TARTAN_YELLOW, TARTAN_GREEN, TARTAN_NAVY, TARTAN_RED, TARTAN_NAVY, TARTAN_WHITE};

    /** How wide each of those threads is, in the sett's own units. */
    private static final int[] SETT_WIDTHS = {8, 3, 1, 1, 4, 1, 4, 1, 1, 3, 1};

    private static final int SETT_UNITS = sum(SETT_WIDTHS);

    private ThemeLook() {
    }

    // ------------------------------------------------------------------ palette

    /**
     * The colours a theme wears, or {@code null} for one that wears the pilot's.
     *
     * <p>Exhaustive with no {@code default}, so an added theme has to decide which kind it is rather
     * than quietly inheriting the swatch.
     *
     * @param seed the rift's own seed - read only by {@link RiftModulatorTheme#IMPROBABILITY}, which
     *             rolls its colours from it and so is never the same colour at both ends of one jump
     */
    public static Palette palette(RiftModulatorTheme theme, int seed) {
        return switch (theme) {
            // Sketch belongs here rather than below: it is coloured in with the pilot's crayons.
            case STANDARD, EMBER, STARLIGHT, ARCANE, CLOCKWORK, SKETCH -> null;
            case STARBLOCKS -> new Palette(HYPERSPACE_BLUE, STARLINE_WHITE);
            case BEDROCK -> new Palette(VOID, HORIZON_GOLD);
            case BOLDLY_GONE -> new Palette(WARP_BLUE, WARP_FLASH);
            case LUDICROUS -> new Palette(TARTAN_RED, TARTAN_YELLOW);
            case EVENTFUL_HORIZON -> new Palette(GRAVITY_RED, GRAVITY_ARC);
            case VWORP -> new Palette(VORTEX_BLUE, VORTEX_ORANGE);
            case IMPROBABILITY -> improbable(seed);
            case ENDER -> new Palette(END_VOID, ENDER_EYE_LIT);
            case MOSTLY_DANGEROUS -> new Palette(NEBULA_BLACK, ARC_BLUE);
        };
    }

    /**
     * What a theme's face and bore are made of.
     *
     * <p>Exhaustive, like {@link #palette}, so a new theme has to say.
     */
    public static Surface surface(RiftModulatorTheme theme) {
        return switch (theme) {
            case ENDER -> Surface.END_SKY;
            case SKETCH -> Surface.PAPER;
            case MOSTLY_DANGEROUS -> Surface.NEBULA;
            case STANDARD, EMBER, STARLIGHT, ARCANE, CLOCKWORK, STARBLOCKS, BEDROCK, BOLDLY_GONE, LUDICROUS,
                    EVENTFUL_HORIZON, VWORP, IMPROBABILITY -> Surface.LIT;
        };
    }

    /** How long a theme's aperture takes to let go once it is told to, in ticks. */
    public static int closeTicks(RiftModulatorTheme theme) {
        return switch (theme) {
            case SKETCH -> SKETCH_CLOSE;
            case MOSTLY_DANGEROUS -> NEBULA_CLOSE;
            default -> CLOSE;
        };
    }

    /**
     * Whether a theme reads the Modulator's two swatches at all.
     *
     * <p>Worked out from the thing that decides it rather than listed, so it cannot drift from what the
     * rift actually draws: a theme with no palette of its own is wearing the pilot's. Everything else -
     * the borrowed jumps and the End - brings its own colours, and the Modulator's screen greys its
     * swatches out rather than letting a pilot pick a colour that will never be seen.
     */
    public static boolean takesSwatches(RiftModulatorTheme theme) {
        return palette(theme, 0) == null;
    }

    /**
     * Whether a theme is drawn in ink on paper rather than in light.
     *
     * <p>The one question every pass has to ask before it draws: an inked rift has no fire, no haze, no
     * far light and no flash, and everything it does have goes on the translucent pass - bar the pencil
     * drawing it, which is solid.
     */
    public static boolean inked(RiftModulatorTheme theme) {
        return surface(theme) == Surface.PAPER;
    }

    /** The colour one of a cloud's arcs is drawn in, from {@link Nebula#arcColour}. */
    public static int arcColour(int which) {
        return switch (which) {
            case 0 -> ARC_WHITE;
            case 1 -> ARC_VIOLET;
            default -> ARC_BLUE;
        };
    }

    /**
     * The moments in a theme's wind-up that make a sound, as fractions of it, in order.
     *
     * <p>Kept here rather than beside the sounds because the drawing reads the same numbers: the stare
     * is heard at the instant the eyes start to open. Two copies of that moment would drift apart the
     * first time either was tuned.
     *
     * <p>A sketch has none. Its pencil goes on drawing long after the wind-up is over, so it is heard
     * stroke by stroke from its own {@link SketchPlan} instead.
     */
    public static float[] beats(RiftModulatorTheme theme) {
        return switch (theme) {
            case ENDER -> new float[]{STARE};
            case MOSTLY_DANGEROUS -> new float[]{GATHER};
            case SKETCH, STANDARD, EMBER, STARLIGHT, ARCANE, CLOCKWORK, STARBLOCKS, BEDROCK, BOLDLY_GONE,
                    LUDICROUS, EVENTFUL_HORIZON, VWORP, IMPROBABILITY -> NO_BEATS;
        };
    }

    /**
     * Whether a theme's rift throws lightning.
     *
     * <p>Unchanged - yes - for the five originals. Of the ten after them only four do, because only
     * four of their sources crackle: a gravity drive tearing a hole is violent, the time vortex is full
     * of lightning, an improbable rift is allowed anything, and a capital ship's cloud never stops.
     * Hyperspace, a warp bubble, a fold and plaid are each quiet in their own way, and bolts off the rim
     * would make them all the same stock rift wearing a different colour. The End is silent, and a
     * drawing has nothing to crackle.
     */
    public static boolean crackles(RiftModulatorTheme theme) {
        return switch (theme) {
            case STANDARD, EMBER, STARLIGHT, ARCANE, CLOCKWORK -> true;
            case EVENTFUL_HORIZON, VWORP, IMPROBABILITY, MOSTLY_DANGEROUS -> true;
            case STARBLOCKS, BEDROCK, BOLDLY_GONE, LUDICROUS, ENDER, SKETCH -> false;
        };
    }

    /** Improbability's colours: a hue rolled from the rift's seed, and its opposite at the rim. */
    private static Palette improbable(int seed) {
        float hue = ((seed >>> 8) & 0xFFFF) / 65_536.0F;
        return new Palette(hsv(hue, 0.70F, 1.0F), hsv(hue + 0.5F, 0.50F, 1.0F));
    }

    // --------------------------------------------------------------------- sett

    /** How many threads there are in one repeat of the sett. */
    public static int settBands() {
        return SETT_COLOURS.length;
    }

    /** How wide one repeat of the sett is, in its own units. */
    public static int settUnits() {
        return SETT_UNITS;
    }

    /** The colour of a thread, counting on round the repeat however far the index runs. */
    public static int settColour(int index) {
        return SETT_COLOURS[Math.floorMod(index, SETT_COLOURS.length)];
    }

    /** The width of a thread, in the sett's own units, likewise wrapping. */
    public static int settWidth(int index) {
        return SETT_WIDTHS[Math.floorMod(index, SETT_WIDTHS.length)];
    }

    // -------------------------------------------------------------------- colour

    /**
     * A colour from hue, saturation and value, packed as {@code 0xRRGGBB}.
     *
     * <p>A real conversion rather than the three-sines approximation the glass uses, because here it
     * sets whole palettes - a rim that came out muddy for one hue in six would be a rim that sometimes
     * cannot be seen.
     *
     * @param hue any value; it wraps round the wheel once per unit
     */
    public static int hsv(float hue, float saturation, float value) {
        float h = hue - (float) Math.floor(hue);
        float s = Math.max(0.0F, Math.min(1.0F, saturation));
        float v = Math.max(0.0F, Math.min(1.0F, value));
        float scaled = h * 6.0F;
        int sector = ((int) scaled) % 6;
        float f = scaled - (int) scaled;
        float p = v * (1.0F - s);
        float q = v * (1.0F - s * f);
        float t = v * (1.0F - s * (1.0F - f));
        float r;
        float g;
        float b;
        switch (sector) {
            case 0 -> {
                r = v;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = v;
                b = p;
            }
            case 2 -> {
                r = p;
                g = v;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = v;
            }
            case 4 -> {
                r = t;
                g = p;
                b = v;
            }
            default -> {
                r = v;
                g = p;
                b = q;
            }
        }
        return (Math.round(r * 255.0F) << 16) | (Math.round(g * 255.0F) << 8) | Math.round(b * 255.0F);
    }

    /** Perceived brightness of a packed colour, 0 to 1. For tests, and for the reasoning above. */
    public static float luminance(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        return 0.2126F * r + 0.7152F * g + 0.0722F * b;
    }

    private static int sum(int[] values) {
        int total = 0;
        for (int value : values) {
            total += value;
        }
        return total;
    }
}
