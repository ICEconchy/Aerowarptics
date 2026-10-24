package uk.co.iceconchy.aerowarptics.ward;

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
 * The Rift Ward as an item: the whole machine, drawn a third of its size, and set down whole. A refusal says
 * why and outlines the room it needed, as the Collapse Cradle's does - the two stand on the same footprint.
 */
public class RiftWardItem extends GeoBlockItem {

    public RiftWardItem(Block block, Properties properties) {
        super(block, properties, "rift_ward", "rift_ward", "rift_ward", "animation.rift_ward.idle");
    }

    /** Three blocks each way, drawn in one slot. */
    @Override
    public int blocks() {
        return WardStructure.SIZE;
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
            String reason = RiftWardBlock.aboard(level, pos) ? "rift_ward.aboard" : "rift_ward.no_room";
            player.displayClientMessage(AWLang.translate(reason).component(), true);
        }
        return result;
    }
}
