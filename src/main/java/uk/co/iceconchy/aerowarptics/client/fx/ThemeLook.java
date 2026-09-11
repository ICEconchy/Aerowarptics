package uk.co.iceconchy.aerowarptics.client.fx;

import uk.co.iceconchy.aerowarptics.modulator.RiftModulatorTheme;

/**
 * What the seven borrowed themes look like before a single stroke is drawn: their colours, whether
 * they crackle, and the one woven sett Ludicrous Speed is cut from.
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
            case STANDARD, EMBER, STARLIGHT, ARCANE, CLOCKWORK -> null;
            case STARBLOCKS -> new Palette(HYPERSPACE_BLUE, STARLINE_WHITE);
            case BEDROCK -> new Palette(VOID, HORIZON_GOLD);
            case BOLDLY_GONE -> new Palette(WARP_BLUE, WARP_FLASH);
            case LUDICROUS -> new Palette(TARTAN_RED, TARTAN_YELLOW);
            case EVENTFUL_HORIZON -> new Palette(GRAVITY_RED, GRAVITY_ARC);
            case VWORP -> new Palette(VORTEX_BLUE, VORTEX_ORANGE);
            case IMPROBABILITY -> improbable(seed);
        };
    }

    /**
     * Whether a theme's rift throws lightning.
     *
     * <p>Unchanged - yes - for the five originals. Of the borrowed seven only three do, because only
     * three of their sources crackle: a gravity drive tearing a hole is violent, the time vortex is
     * full of lightning, and an improbable rift is allowed anything. Hyperspace, a warp bubble, a fold
     * and plaid are each quiet in their own way, and bolts off the rim would make them all the same
     * stock rift wearing a different colour.
     */
    public static boolean crackles(RiftModulatorTheme theme) {
        return switch (theme) {
            case STANDARD, EMBER, STARLIGHT, ARCANE, CLOCKWORK -> true;
            case EVENTFUL_HORIZON, VWORP, IMPROBABILITY -> true;
            case STARBLOCKS, BEDROCK, BOLDLY_GONE, LUDICROUS -> false;
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
