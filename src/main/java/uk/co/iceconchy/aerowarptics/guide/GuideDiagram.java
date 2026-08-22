package uk.co.iceconchy.aerowarptics.guide;

/**
 * The picture at the top of a page, named rather than drawn.
 *
 * <p>{@link GuideBook} says which diagram a page carries; {@code GuideDiagrams} on the client says
 * what that looks like and how it moves. Splitting them is what keeps the book's contents free of
 * Minecraft and therefore testable - and it means a page can be written before anybody has decided
 * how to illustrate it.
 *
 * <p>Every one of these is animated. A still picture of a machine is something the item's own model
 * already shows better; what a diagram here is for is the part that moves - a shaft turning, a needle
 * settling, an item crossing between two apertures - which is precisely the part a screenshot in a
 * wiki cannot carry.
 */
public enum GuideDiagram {

    /** No picture. The page is all prose, and gets the room back. */
    NONE(0),

    /** A rift hanging open, turning slowly in its own wreckage. The book's cover mark. */
    RIFT(56),

    /** A hull with a drive aboard it, and the shaft that spins it. */
    DRIVE(52),

    /** The bow needle swinging round and settling on the nose. */
    BOW(52),

    /** An anchor standing on the ground with its marker beating above it. */
    ANCHOR(48),

    /** Nine panels clicking together into one table, and the chart that appears on it. */
    CHART(52),

    /** A lever, a wire, and the pulse travelling down it into the drive. */
    REDSTONE(48),

    /** The ship in the throat: ring gates going by, and the light at the far end. */
    CORRIDOR(52),

    /** The probe's dial sweeping a bearing, and the arc of its range. */
    PROBE(52),

    /** A survey square with a cross on it: a place known by evidence rather than by a block. */
    FIX(48),

    /** A ring of frame with an aperture standing in it. */
    GATE(52),

    /** Two gates of different sizes, and something crossing from one to the other. */
    GATE_PAIR(48),

    /** The siphon filling as a ship arrives. */
    SIPHON(48),

    /** Two chutes and an item hopping the gap between them. */
    CHUTE(44),

    /** A checklist ticking itself off, one line at a time, and stopping at the one that fails. */
    CHECKLIST(52);

    private final int height;

    GuideDiagram(int height) {
        this.height = height;
    }

    /**
     * Vertical room the drawing needs, in pixels.
     *
     * <p>Stated here rather than measured on the client, because the page's text has to be laid out
     * around it and the layout is worked out where there is no {@code GuiGraphics} to ask.
     */
    public int height() {
        return height;
    }
}
