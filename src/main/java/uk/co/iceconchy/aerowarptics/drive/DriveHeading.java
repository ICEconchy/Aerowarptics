package uk.co.iceconchy.aerowarptics.drive;

import net.minecraft.core.Direction;

/**
 * Which way a Rift Drive throws its airship, as a quarter turn from the drive's own face.
 *
 * <p>The setting is deliberately <em>relative</em>. A compass point would be a lie the moment the
 * vessel came about: a pilot who set "north" before a long turn would find the rift tearing itself
 * open across the beam. These four values live in the airship's own frame, so once the needle is
 * aimed at the nose it stays aimed at the nose however the hull is lying.
 *
 * <p>{@link #FORWARD} is the direction the drive's front face points; the rest are quarter turns
 * clockwise from it, seen from above. The drive carries a needle on its top plate showing the
 * current setting, so the bearing can be read off the machine itself rather than out of a menu.
 */
public enum DriveHeading {

    /** Straight out of the drive's front face. */
    FORWARD("forward", 0),
    /** A quarter turn clockwise from the drive's face, seen from above. */
    RIGHT("right", 1),
    /** Out of the back of the drive. */
    BACK("back", 2),
    /** A quarter turn anticlockwise from the drive's face. */
    LEFT("left", 3);

    private final String name;
    private final int quarterTurns;

    DriveHeading(String name, int quarterTurns) {
        this.name = name;
        this.quarterTurns = quarterTurns;
    }

    /** Quarter turns clockwise from the drive's face, 0 to 3. */
    public int quarterTurns() {
        return quarterTurns;
    }

    /**
     * Turns a reference direction into the bow.
     *
     * @param reference a horizontal direction - the drive's own facing
     * @throws IllegalArgumentException if the reference is vertical, which has no clockwise neighbour
     */
    public Direction apply(Direction reference) {
        if (!reference.getAxis().isHorizontal()) {
            throw new IllegalArgumentException("bow reference must be horizontal, got " + reference);
        }
        Direction bow = reference;
        for (int turn = 0; turn < quarterTurns; turn++) {
            bow = bow.getClockWise();
        }
        return bow;
    }

    /**
     * The needle's rotation about the model's Y axis, in radians.
     *
     * <p>Negative because GeckoLib turns a bone with {@code Axis.YP}, which runs anticlockwise seen
     * from above, and the settings run clockwise. The needle is authored pointing at the drive's
     * front face, so this is the whole of its rotation - it does not need to know which way the
     * block was placed, since the renderer has already turned the model to match.
     */
    public float needleRadians() {
        return (float) (-quarterTurns * Math.PI / 2.0D);
    }

    /** Namespace-relative lang key, resolved through {@code AWLang}. */
    public String translationKey() {
        return "drive.heading." + name;
    }

    public DriveHeading next() {
        DriveHeading[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static DriveHeading byIndex(int index) {
        DriveHeading[] values = values();
        return index >= 0 && index < values.length ? values[index] : FORWARD;
    }
}
