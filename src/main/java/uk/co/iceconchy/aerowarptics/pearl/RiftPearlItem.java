package uk.co.iceconchy.aerowarptics.pearl;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.List;

/**
 * Rift Essence in a shell: an ender pearl filled with 250 mB of essence by a spout.
 *
 * <p>Essence in item form. A fluid cannot go into a crafting grid or a mechanical crafter, so anything that
 * wants essence as an ingredient - the Rift Beacon, the Collapse Cradle - takes a pearl instead. It is also
 * a denomination: a thing a player can hand over, trade, or keep in a chest. Emptied at an Item Drain it
 * gives back 200 mB, and the fifty that do not come back are what makes it a store of value rather than a
 * free bucket.
 *
 * <p>Thrown, it hops whatever living thing it hits somewhere near, as a chorus fruit would - see
 * {@link RiftPearlEntity}.
 */
public class RiftPearlItem extends Item {

    public RiftPearlItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_PEARL_THROW,
                SoundSource.NEUTRAL, 0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        player.getCooldowns().addCooldown(this, 20);
        if (!level.isClientSide) {
            RiftPearlEntity pearl = new RiftPearlEntity(level, player);
            pearl.setItem(stack);
            pearl.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
            level.addFreshEntity(pearl);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(AWLang.translate("item.rift_pearl.tooltip").component().copy().withStyle(ChatFormatting.GRAY));
    }
}
