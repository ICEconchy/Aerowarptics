package uk.co.iceconchy.aerowarptics.bond;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import uk.co.iceconchy.aerowarptics.registry.AWDataComponents;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.List;

/**
 * Rift Bonds in hand, which remember the bond they were last tuned to.
 *
 * <p>The bond rides on the stack as a data component, so a tuned stack and an untuned one do not merge
 * into one and lose which was which. Sneaking and using the stack on nothing clears it.
 */
public class RiftBondItem extends BlockItem {

    public RiftBondItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown() || !stack.has(AWDataComponents.BOND.get())) {
            return super.use(level, player, hand);
        }
        if (!level.isClientSide) {
            stack.remove(AWDataComponents.BOND.get());
            AWLang.translate("gui.rift_bond.cleared").sendStatus(player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        boolean tuned = stack.has(AWDataComponents.BOND.get());
        tooltip.add(AWLang.translate(tuned ? "gui.rift_bond.tooltip.tuned" : "gui.rift_bond.tooltip.untuned")
                .component().withStyle(tuned ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY));
        tooltip.add(AWLang.translate("gui.rift_bond.hint").component().withStyle(ChatFormatting.DARK_GRAY));
    }
}
