package uk.co.iceconchy.aerowarptics.gate;

import net.minecraft.util.StringRepresentable;

/**
 * What a Rift Portal pane is doing, as a fact the block itself can be asked rather than something a
 * renderer has to be told.
 *
 * <p>Three values, not two, because a doorway forming and a doorway falling apart are not the same
 * picture played backwards. {@code OPENING} covers a gate's {@code DIALLING} state, {@code CLOSING}
 * covers {@code CLOSING}, and {@code OPEN} is everything in between - which for a pane only ever means
 * {@code RiftGateState.OPEN}, since the block does not exist at all while the gate has no aperture.
 *
 * <h2>Why this is a texture and not a scale</h2>
 * The honest version of "opening" is a pane growing from nothing to full size over the exact ticks a
 * gate spends dialling, and that needs a renderer that knows when each individual pane started - which
 * a block with no block entity has nowhere to keep. What a shared, tiling, looping texture can do
 * instead is a *motif*: a look that says "forming" or "unravelling" for as long as the state holds,
 * played as an indefinite pulse rather than a one-shot tied to a clock nothing here can read. That is
 * the same trade this mod already made for a gate's ring before this pane took the animation over -
 * see the git history - and it is made again here for the same reason: Minecraft's own animated
 * textures run off the world's global tick counter, not off when a particular block was placed, so a
 * strip that is meant to play once from the moment a pane appears would show whatever frame the clock
 * happened to be on instead, and every gate in the world would be out of step with its own doorway.
 */
public enum RiftPortalStage implements StringRepresentable {

    /** Forming. Covers a gate's {@code DIALLING} - the pane exists before the far end can be crossed. */
    OPENING("opening"),

    /** Standing. The calm, continuous shimmer - what every pane looked like before this existed. */
    OPEN("open"),

    /** Falling apart. Covers {@code CLOSING}, right up until the gate removes the blocks. */
    CLOSING("closing");

    private final String name;

    RiftPortalStage(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /**
     * The stage a pane should show while its gate is in the given state.
     *
     * <p>Only ever called while that gate {@code hasAperture()} - the pane does not exist otherwise -
     * so {@code UNFORMED} and {@code IDLE} have nothing sensible to return and are refused rather than
     * mapped to a guess. A caller that reaches this with either has a bug worth finding, not a texture
     * worth picking for it.
     */
    public static RiftPortalStage of(RiftGateState state) {
        return switch (state) {
            case DIALLING -> OPENING;
            case OPEN -> OPEN;
            case CLOSING -> CLOSING;
            case UNFORMED, IDLE -> throw new IllegalArgumentException(
                    "a gate with no aperture asked for a portal stage: " + state);
        };
    }
}
