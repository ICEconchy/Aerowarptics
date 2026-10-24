package uk.co.iceconchy.aerowarptics.flotilla;

import net.minecraft.util.StringRepresentable;

/**
 * What a Rift Coupler is set to be: the ship that leads a flotilla, or one that follows.
 *
 * <p>Chosen at the coupler's own screen rather than worked out from anything, because the same hull can
 * be a flagship one evening and an escort the next, and nothing about how it is built says which. A
 * fresh coupler is an escort: most couplers on a server will be, and an escort that has joined nothing
 * does nothing at all.
 */
public enum CouplerRole implements StringRepresentable {

    ESCORT("escort"),
    FLAGSHIP("flagship");

    private final String name;

    CouplerRole(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** Namespace-relative lang key, resolved through {@code AWLang}. */
    public String translationKey() {
        return "flotilla.role." + name;
    }

    public static CouplerRole byIndex(int index) {
        CouplerRole[] values = values();
        return index >= 0 && index < values.length ? values[index] : ESCORT;
    }
}
