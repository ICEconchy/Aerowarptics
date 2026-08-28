package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.gate.RiftGateState;
import uk.co.iceconchy.aerowarptics.gate.RiftPortalStage;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a Rift Portal pane shows, for every state its gate can be in.
 *
 * <p>Small enough to look at and exactly the kind of mapping that rots quietly. A gate gaining a
 * state - and this one has gained states before - would either fail to compile here, which is the
 * good outcome, or silently fall through to a texture nobody chose, which is a pane that stops
 * animating through a beat somebody spent effort drawing. Neither shows up in a build log.
 */
class RiftPortalStageTest {

    /**
     * A gate with no aperture has no pane to draw, so asking what one should look like is a bug at
     * the call site, not a question with an answer. Pinned here rather than left to fall through to a
     * default, on the theory that a thrown exception in a test is easier to find than a portal frozen
     * on the wrong texture in a screenshot somebody sends months later.
     */
    @Test
    void aGateWithNoApertureHasNoStageToShow() {
        assertThrows(IllegalArgumentException.class, () -> RiftPortalStage.of(RiftGateState.UNFORMED));
        assertThrows(IllegalArgumentException.class, () -> RiftPortalStage.of(RiftGateState.IDLE));
    }

    /** Every state that shows a pane maps to a stage, and only those states are asked for one. */
    @Test
    void everyStateWithAnApertureMapsToAStage() {
        for (RiftGateState state : RiftGateState.values()) {
            if (!state.hasAperture()) {
                continue;
            }
            RiftPortalStage stage = RiftPortalStage.of(state); // must not throw
            assertTrue(EnumSet.allOf(RiftPortalStage.class).contains(stage));
        }
    }

    @Test
    void diallingOpensAndOpenStaysOpen() {
        assertSame(RiftPortalStage.OPENING, RiftPortalStage.of(RiftGateState.DIALLING));
        assertSame(RiftPortalStage.OPEN, RiftPortalStage.of(RiftGateState.OPEN));
    }

    /**
     * Closing gets its own look, rather than borrowing the opening one.
     *
     * <p>This is the difference between this scheme and the ring animation it replaced: a gate's
     * frame reused its wind-up for closing because an animated strip cannot play backwards, but the
     * pane is not built the same way twice, and forming and unravelling are drawn as two distinct
     * motifs rather than one played both directions.
     */
    @Test
    void closingLooksDifferentFromOpening() {
        assertSame(RiftPortalStage.CLOSING, RiftPortalStage.of(RiftGateState.CLOSING));
        assertTrue(RiftPortalStage.of(RiftGateState.CLOSING) != RiftPortalStage.of(RiftGateState.DIALLING),
                "closing borrows the opening animation, so the pane never shows falling apart");
    }

    /**
     * Every stage is reachable, and every one is named as its blockstate file uses it.
     *
     * <p>The names are not decoration: they are the {@code stage=} values in
     * {@code blockstates/rift_portal.json}, and renaming one here without renaming it there is a
     * missing-texture chequer rather than a compile error.
     */
    @Test
    void everyStageIsReachableAndNamedAsItsVariantKey() {
        Set<RiftPortalStage> reached = EnumSet.noneOf(RiftPortalStage.class);
        for (RiftGateState state : RiftGateState.values()) {
            if (state.hasAperture()) {
                reached.add(RiftPortalStage.of(state));
            }
        }
        assertEquals(EnumSet.allOf(RiftPortalStage.class), reached,
                "a stage value nothing can ever put a pane into");

        assertEquals("opening", RiftPortalStage.OPENING.getSerializedName());
        assertEquals("open", RiftPortalStage.OPEN.getSerializedName());
        assertEquals("closing", RiftPortalStage.CLOSING.getSerializedName());
    }
}
