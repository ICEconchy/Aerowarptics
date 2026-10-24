package uk.co.iceconchy.aerowarptics.collapse;

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
 * The Collapse Cradle as an item: the whole machine, drawn a third of its size, and set down whole.
 *
 * <p>A placement the block refuses is said out loud rather than swallowed. Three blocks by three by three is a
 * lot of room to find, and a click that does nothing reads as a broken item; so a refusal names its reason -
 * no room, or a ship's deck - and this player's client outlines the space the machine needed.
 */
public class CollapseCradleItem extends GeoBlockItem {

    public CollapseCradleItem(Block block, Properties properties) {
        super(block, properties, "collapse_cradle", "collapse_cradle", "collapse_cradle",
                "animation.collapse_cradle.idle");
    }

    /** Three blocks each way, drawn in one slot. */
    @Override
    public int blocks() {
        return CradleStructure.SIZE;
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
            String reason = CollapseCradleBlock.aboard(level, pos) ? "collapse_cradle.aboard" : "collapse_cradle.no_room";
            player.displayClientMessage(AWLang.translate(reason).component(), true);
        }
        return result;
    }
}
