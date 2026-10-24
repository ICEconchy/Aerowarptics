package uk.co.iceconchy.aerowarptics.gate;

/**
 * What a traveller sees while a Rift Gate carries them across.
 *
 * <p>Purely a client-side selector, and each player's own: it is read from that player's client config
 * as the passage begins, set from the button on a gate's dial panel, and nothing about it is stored on a
 * gate or sent anywhere. The passage underneath is the same passage whichever of these is drawn over it -
 * how long it lasts, and everything it protects the traveller from, are the server's and are
 * {@link GatePassages}'.
 *
 * <p>Every style runs on the same clock, {@link GatePassage}'s, and is handed the same few numbers from
 * it. What it makes of them is its own business, with one rule that is not: <em>while the veil is whole,
 * nothing of the world shows</em>. A style decides what the veil is made of, and where it thins first as
 * the traveller arrives - an iris, a dissolve, a fade - never whether there is one. A style that showed
 * the far gate early would be showing a player the place they are being held, unable to move.
 * {@code PassageShaderTest} draws every one of them and reads the pixels back to hold them to it.
 *
 * <h2>The names are a save format</h2>
 * A style is written into the client config by name, so the names are what must not change. The order
 * is only the order the dial's button steps through them in, with the plain one last.
 */
public enum GatePassageStyle {

    // ------------------------------------------------------------ the borrowed ones
    // As with a Rift Modulator's themes: a familiar journey from somewhere else, worn on this mod's own
    // machinery, under a name that is near enough to raise a smile and far enough to be its own.

    /**
     * The plunge through a ring of standing water. The pane's light bursts over the traveller, and then
     * they are flung down a twisting conduit of blue and violet plasma, light streaking past the walls,
     * until the far end swells into a flare and lets them out.
     */
    KAWOOSH("kawoosh", true),

    /**
     * A blue box's way through time. A vortex of storm cloud wound into a spiral, ember red sliding to
     * cold blue and back, turning hard about its middle with lightning cracking down its length.
     */
    TIMEY_WIMEY("timey_wimey", true),

    /**
     * The jump to lightspeed. The stars hold for a breath, pull out into lines, and then there is only
     * the mottled blue-white of the lane rushing past until it lets go.
     */
    PUNCH_IT("punch_it", true),

    /**
     * No journey at all, only a taking apart and a putting together: a column of light and a shimmer of
     * gold and blue motes rising through it, thickening to white - and thinning, mote by mote, into the
     * far side.
     */
    ENERGISE("energise", true),

    /**
     * The long corridor of light at the end of a much older voyage. Two planes of colour, one above and
     * one below a burning slit, streaming out of it and past in every hue there is, turning as they come.
     */
    FULL_OF_STARS("full_of_stars", true),

    /**
     * Down a green pipe and out of another. An iris shuts, a pipe goes by in three flat greens and big
     * square pixels, and an iris opens on wherever it came out.
     */
    DOWN_THE_PIPE("down_the_pipe", true),

    // ---------------------------------------------------------------- and the plain one

    /**
     * No tunnel at all: the view dims, holds, and brightens into the far side. For anybody who would
     * rather not be flown down a tunnel every time they go to the mine - and what is drawn in place of
     * any other style whose shader did not load.
     */
    QUIET("quiet", false);

    private final String name;
    private final boolean shaded;

    GatePassageStyle(String name, boolean shaded) {
        this.name = name;
        this.shaded = shaded;
    }

    /** Namespace-relative lang key of the style's name, resolved through {@code AWLang}. */
    public String translationKey() {
        return "gate.passage." + name;
    }

    /** Namespace-relative lang key of a line saying what the style looks like. */
    public String aboutKey() {
        return "gate.passage." + name + ".about";
    }

    /**
     * The core shader the style is drawn with, by its name under {@code shaders/core}, or {@code null}
     * for one that needs none.
     */
    public String shader() {
        return shaded ? "passage_" + name : null;
    }

    /** The style after this one, wrapping round. */
    public GatePassageStyle next() {
        GatePassageStyle[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /** The style before this one, wrapping round - a shifted click, as the Modulator's theme button has it. */
    public GatePassageStyle previous() {
        GatePassageStyle[] values = values();
        return values[(ordinal() + values.length - 1) % values.length];
    }
}
