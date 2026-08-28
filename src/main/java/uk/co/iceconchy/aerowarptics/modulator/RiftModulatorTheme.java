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
 * enum could change anything about the fracture itself. {@link #CLOCKWORK} and {@link #ARCANE} cut a
 * genuinely different pattern - the mapping from a theme to a {@code RiftShatter.Pattern} lives in
 * {@code RiftEffectManager} rather than here, since this enum is shared with the server and the pattern
 * is purely a client rendering shape. {@code RiftEffectManager} reuses the very same shard-fling and
 * shard-return rendering for all five themes; only the shapes {@code RiftShatter} hands it differ.
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
    CLOCKWORK("clockwork");

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

    public static RiftModulatorTheme byIndex(int index) {
        RiftModulatorTheme[] values = values();
        return index >= 0 && index < values.length ? values[index] : STANDARD;
    }
}
