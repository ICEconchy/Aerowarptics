package uk.co.iceconchy.aerowarptics.modulator;

/**
 * How a Rift Modulator dresses the rift it is bolted beside.
 *
 * <p>Purely a client-side selector. None of these change anything about how a warp runs - only which
 * particles {@code WarpEffects} reaches for, and which fracture pattern {@code RiftShatter} cuts the
 * aperture along when it opens and seals. {@link #STANDARD} is exactly what an undecorated Rift Drive
 * already looks like, so a Modulator with no essence in it (or no drive beside it at all) changes
 * nothing about the rift it is sitting next to.
 *
 * <h2>Opening animation</h2>
 * {@link #STANDARD}, {@link #EMBER} and {@link #STARLIGHT} all break like glass - {@code
 * RiftShatter.Pattern.GLASS} - and differ only in colour and particle mote, the same as before this
 * enum could change anything about the fracture itself. Every other theme cuts a genuinely different
 * pattern - the mapping from a theme to a {@code RiftShatter.Pattern} lives in {@code
 * RiftEffectManager} rather than here, since this enum is shared with the server and the pattern is
 * purely a client rendering shape. {@code RiftEffectManager} reuses the very same shard-fling and
 * shard-return rendering for every theme; only the shapes {@code RiftShatter} hands it differ.
 *
 * <h2>Order is a save format</h2>
 * A theme travels as its {@link #ordinal()} - on the wire in {@code ServerboundModulatorPacket} and
 * in the Modulator's own NBT - so new themes are <em>appended</em> and never inserted. Reordering
 * this enum would silently repaint every Modulator already placed in a world.
 */
public enum RiftModulatorTheme {

    /** The Rift Drive's own look: sparks and electric arcs, tinted by tier. Breaks like glass. */
    STANDARD("standard"),
    /** Slow, warm embers rather than sparks - a rift that smoulders instead of crackling. Breaks like glass. */
    EMBER("ember"),
    /** Sparse, twinkling motes - a rift that glitters rather than tears. Breaks like glass. */
    STARLIGHT("starlight"),
    /** Everything the standard look has, heavier and faster - and a rune-ring tears open rather than a pane. */
    ARCANE("arcane"),
    /** Ten even teeth click open one after another around the rim, like a gear letting go. */
    CLOCKWORK("clockwork"),

    // ------------------------------------------------------------ the borrowed ones
    // Seven themes that wear a familiar science-fiction jump on the mod's own machinery. Each is
    // still nothing but a fracture shape, a mote, a sound and some furniture - the warp underneath
    // is the same warp - but they are the reason the theme button is worth pressing more than once.

    /** The pane does not break: it smears into radial streaks and snaps back. A jump to lightspeed. */
    STARBLOCKS("starblocks"),
    /** Space folds. The pane is dragged round a dark lens, fastest at the middle, and the world bends with it. */
    BEDROCK("bedrock"),
    /** A calm lens stretched long along the heading until it lets go with a rainbow-fringed flash. */
    BOLDLY_GONE("boldly_gone"),
    /** The streaks go plaid. Exactly as silly as it sounds, and entirely on purpose. */
    LUDICROUS("ludicrous"),
    /** The pane liquefies rather than breaking - black metal sagging, running and dripping inwards. */
    EVENTFUL_HORIZON("eventful_horizon"),
    /** The rift refuses to arrive all at once, stuttering in and out in hard steps with a grinding wheeze. */
    VWORP("vworp"),
    /** Every piece picks its own way out, and picks again next time. Never twice the same rift. */
    IMPROBABILITY("improbability");

    private final String name;

    RiftModulatorTheme(String name) {
        this.name = name;
    }

    /** Namespace-relative lang key, resolved through {@code AWLang}. */
    public String translationKey() {
        return "gui.rift_modulator.theme." + name;
    }

    public RiftModulatorTheme next() {
        RiftModulatorTheme[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /**
     * The theme before this one, wrapping round.
     *
     * <p>One button cycling twelve themes is a lot of clicking to get back to the one you have just
     * gone past, so the Modulator's screen reverses the cycle on a shifted click. Worth having as a
     * method rather than as arithmetic at the call site so that it wraps the same way {@link #next()}
     * does - {@code (ordinal() - 1) % length} is negative for {@link #STANDARD}, which is exactly the
     * sort of thing that would only be found by a player pressing shift on the first theme.
     */
    public RiftModulatorTheme previous() {
        RiftModulatorTheme[] values = values();
        return values[(ordinal() + values.length - 1) % values.length];
    }

    public static RiftModulatorTheme byIndex(int index) {
        RiftModulatorTheme[] values = values();
        return index >= 0 && index < values.length ? values[index] : STANDARD;
    }
}
