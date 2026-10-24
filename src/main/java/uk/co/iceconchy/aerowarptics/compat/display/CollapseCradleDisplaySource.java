package uk.co.iceconchy.aerowarptics.compat.display;

import net.minecraft.network.chat.MutableComponent;
import uk.co.iceconchy.aerowarptics.collapse.CollapseCradleBlockEntity;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.util.AWLang;

/**
 * Reports a Collapse Cradle to a Display Link: what it is doing, how far through, how unstable it is getting,
 * and how much essence it holds. Instability on a nixie tube beside the cradle is the reading a player wants
 * while deciding whether to switch another stabiliser on.
 */
public class CollapseCradleDisplaySource extends AWDisplaySource<CollapseCradleBlockEntity> {

    /** Registry path, {@code aerowarptics:collapse_cradle} in full. */
    public static final String SOURCE_ID = "collapse_cradle";

    static final int MODE_STATE = 0;
    static final int MODE_PROGRESS = 1;
    static final int MODE_INSTABILITY = 2;
    static final int MODE_ESSENCE = 3;

    CollapseCradleDisplaySource() {
        super(SOURCE_ID, CollapseCradleBlockEntity.class, () -> AWBlockEntities.COLLAPSE_CRADLE.get(),
                "state", "progress", "instability", "essence");
    }

    @Override
    protected boolean numeric(int mode) {
        return mode != MODE_STATE;
    }

    /**
     * A link on any block of the machine reads the machine. The master is walled in by the rest of it on every
     * side but the shaft's, so a link that could only read the master could never be put anywhere.
     */
    @Override
    public void registerExtras() {
        com.simibubi.create.api.behaviour.display.DisplaySource.BY_BLOCK.add(
                uk.co.iceconchy.aerowarptics.registry.AWBlocks.COLLAPSE_CRADLE_PART.get(), this);
    }

    @Override
    protected net.minecraft.world.level.block.entity.BlockEntity source(
            com.simibubi.create.content.redstone.displayLink.DisplayLinkContext context) {
        net.minecraft.core.BlockPos at = context.getSourcePos();
        net.minecraft.world.level.block.state.BlockState state = context.level().getBlockState(at);
        if (uk.co.iceconchy.aerowarptics.collapse.CollapseCradlePartBlock.stillValid(context.level(), at, state)) {
            return context.level().getBlockEntity(
                    uk.co.iceconchy.aerowarptics.collapse.CollapseCradlePartBlock.master(at, state));
        }
        return super.source(context);
    }

    @Override
    protected MutableComponent line(CollapseCradleBlockEntity cradle, int mode) {
        return switch (mode) {
            case MODE_STATE -> {
                if (cradle.running()) {
                    yield AWLang.translate("display_source.collapse_cradle.collapsing").component();
                }
                if (cradle.lastFailure().isFailure()) {
                    yield AWLang.translate("display_source.collapse_cradle.refused").component();
                }
                yield AWLang.translate("display_source.collapse_cradle.idle").component();
            }
            case MODE_PROGRESS -> cradle.running() ? percent(cradle.progress()) : EMPTY_LINE;
            case MODE_INSTABILITY -> cradle.running() ? percent(cradle.instability()) : EMPTY_LINE;
            case MODE_ESSENCE -> number(cradle.contents().getAmount());
            default -> EMPTY_LINE;
        };
    }
}
