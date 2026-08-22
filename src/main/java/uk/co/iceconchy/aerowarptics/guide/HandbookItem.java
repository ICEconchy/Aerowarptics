package uk.co.iceconchy.aerowarptics.guide;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.List;

/**
 * The Navigator's Handbook: an item whose entire behaviour is to open a screen.
 *
 * <p>Nothing about it is server-side, and deliberately so. What the book says is the same for every
 * player on every world, so there is nothing for a server to be authoritative about and no packet to
 * send - unlike every other screen in this mod, which is a view of a machine and has to be told what
 * the machine is doing before it can draw anything. Opening this one is a local matter.
 */
public class HandbookItem extends Item {

    public HandbookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide) {
            AWClientHooks.openHandbook();
        }
        // Success on the client only: the swing and the sound belong to the person who opened it, and
        // the server has nothing to do here.
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(AWLang.translate("guide.tip").component()
                .copy().withStyle(ChatFormatting.DARK_GRAY));
    }
}
