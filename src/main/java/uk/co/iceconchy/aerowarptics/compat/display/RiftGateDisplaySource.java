package uk.co.iceconchy.aerowarptics.compat.display;

import net.minecraft.network.chat.MutableComponent;
import uk.co.iceconchy.aerowarptics.gate.GateFailure;
import uk.co.iceconchy.aerowarptics.gate.RiftGateBlockEntity;
import uk.co.iceconchy.aerowarptics.gate.RiftGateShape;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.util.AWLang;

/**
 * Reports a Rift Gate to a Display Link.
 *
 * <p>A gate is the one machine here whose readings a player watches while standing somewhere else,
 * which is rather the point of putting them on a board by the loading dock: is the ring closed, is
 * there essence in it, and how big a thing will fit through.
 *
 * <p>The essence readings are the tank in millibuckets and as a percentage of capacity, both bare
 * numbers. The goggle tooltip says {@code 4200 / 8192 mB} on one line because a tooltip has room
 * for it; a nixie tube does not, so the two halves are separate modes here.
 *
 * <p>{@code OPENING} reads the aperture the ring encloses, and clears the target when the ring is
 * not formed &mdash; there is no opening to describe, and {@code STATE} is the mode that says so.
 * {@code DIAL_COST} is what a connection would cost at that size, which is the number worth having
 * on the wall next to the essence level.
 */
public class RiftGateDisplaySource extends AWDisplaySource<RiftGateBlockEntity> {

    /** Registry path, {@code aerowarptics:rift_gate} in full. */
    public static final String SOURCE_ID = "rift_gate";

    static final int MODE_STATE = 0;
    static final int MODE_ESSENCE = 1;
    static final int MODE_FILL = 2;
    static final int MODE_OPENING = 3;
    static final int MODE_DIAL_COST = 4;
    static final int MODE_FAULT = 5;

    RiftGateDisplaySource() {
        super(SOURCE_ID, RiftGateBlockEntity.class, () -> AWBlockEntities.RIFT_GATE.get(),
                "state", "essence", "fill", "opening", "dial_cost", "fault");
    }

    @Override
    protected boolean numeric(int mode) {
        return mode == MODE_ESSENCE || mode == MODE_FILL || mode == MODE_DIAL_COST;
    }

    @Override
    protected MutableComponent line(RiftGateBlockEntity gate, int mode) {
        return switch (mode) {
            case MODE_STATE -> AWLang.translate(gate.state().translationKey()).component();
            case MODE_ESSENCE -> number(gate.essence());
            case MODE_FILL -> percent(gate.essence() / (float) RiftGateBlockEntity.CAPACITY);
            case MODE_OPENING -> opening(gate);
            case MODE_DIAL_COST -> number(gate.dialCost());
            case MODE_FAULT -> fault(gate);
            // Unreachable: the mode is clamped to the list this source declares.
            default -> EMPTY_LINE;
        };
    }

    /** The aperture as width by height, or nothing at all while the ring is broken. */
    private static MutableComponent opening(RiftGateBlockEntity gate) {
        RiftGateShape shape = gate.shape();
        if (shape == null) {
            return EMPTY_LINE;
        }
        return AWLang.translate("display_source.rift_gate.opening", shape.width(), shape.height())
                .component();
    }

    /** The last refusal, or nothing. */
    private static MutableComponent fault(RiftGateBlockEntity gate) {
        GateFailure failure = gate.lastFailure();
        return failure.isFailure()
                ? AWLang.translate(failure.translationKey()).component()
                : EMPTY_LINE;
    }
}
