package uk.co.iceconchy.aerowarptics.wellhead;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * How large a Rift Wellhead is: a frame three, five or seven blocks along an edge.
 *
 * <p>The size is the tier. A larger frame digests more, holds more and needs more to keep it steady, and the
 * crystal seated in its collar is what permits it - each is the crystal of the drive ladder's matching step,
 * so a larger well costs the act that step already asks for rather than more of the same blocks.
 */
public enum WellheadTier implements StringRepresentable {

    I(1),
    II(2),
    III(3);

    /** Blocks from the fissure to the middle of a face. */
    private final int radius;

    WellheadTier(int radius) {
        this.radius = radius;
    }

    public int radius() {
        return radius;
    }

    /** Blocks along an edge of the frame. */
    public int size() {
        return radius * 2 + 1;
    }

    /** The tier a frame of this radius is, or {@code null} for no frame at all. */
    public static WellheadTier ofRadius(int radius) {
        for (WellheadTier tier : values()) {
            if (tier.radius == radius) {
                return tier;
            }
        }
        return null;
    }

    /** Whether a crystal good for this tier is good for a frame of another. */
    public boolean permits(WellheadTier frame) {
        return frame != null && frame.ordinal() <= ordinal();
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "wellhead.tier." + getSerializedName();
    }
}
