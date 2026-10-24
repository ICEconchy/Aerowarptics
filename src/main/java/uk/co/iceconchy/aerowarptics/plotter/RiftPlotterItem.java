package uk.co.iceconchy.aerowarptics.plotter;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.registry.GeoBlockItem;
import uk.co.iceconchy.aerowarptics.util.AWLang;

/**
 * The Rift Plotter as an item: the whole machine, drawn a third of its size, and set down whole. A refusal says
 * why and outlines the room it needed, as the Collapse Cradle's does - the two stand on the same footprint.
 */
public class RiftPlotterItem extends GeoBlockItem {

    public RiftPlotterItem(Block block, Properties properties) {
        super(block, properties, "rift_plotter", "rift_plotter", "rift_plotter", "animation.rift_plotter.idle");
    }

    /** Three blocks each way, drawn in one slot. */
    @Override
    public int blocks() {
        return PlotterStructure.SIZE;
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        InteractionResult result = super.place(context);
        Player player = context.getPlayer();
        if (result != InteractionResult.FAIL || player == null) {
            return result;
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (level.isClientSide) {
            AWClientHooks.showCradleSpace(pos);
        } else {
            String reason = RiftPlotterBlock.aboard(level, pos) ? "rift_plotter.aboard" : "rift_plotter.no_room";
            player.displayClientMessage(AWLang.translate(reason).component(), true);
        }
        return result;
    }
}
