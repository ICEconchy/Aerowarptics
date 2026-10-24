package uk.co.iceconchy.aerowarptics.weather;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.registry.AWDataComponents;
import uk.co.iceconchy.aerowarptics.registry.AWItems;
import uk.co.iceconchy.aerowarptics.registry.AWParticles;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.List;

/**
 * A glass bottle a spout has filled with half a bucket of Rift Essence, waiting for a storm to set it.
 *
 * <p>It sets only out in a Rift Storm, under open sky: held by a player standing in it, or dropped on the
 * ground and left there, for half a minute ({@code riftStorm.stormglassSetTicks}). Made by a storm, it knows
 * storms - see {@link StormglassItem}.
 */
public class UnsetStormglassItem extends Item {

    /** How often progress is written, in ticks: often enough to see, rarely enough not to spam the inventory. */
    private static final int STEP = 20;

    public UnsetStormglassItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof Player player)
                || server.getGameTime() % STEP != 0) {
            return;
        }
        // Held means in a hand. The slot index is no help for the offhand, which reports slot 0 of its own
        // one-slot compartment, so each hand is asked for by identity instead.
        net.minecraft.world.InteractionHand hand = player.getMainHandItem() == stack
                ? net.minecraft.world.InteractionHand.MAIN_HAND
                : player.getOffhandItem() == stack ? net.minecraft.world.InteractionHand.OFF_HAND : null;
        if (hand == null) {
            return;
        }
        ItemStack set = expose(server, player.blockPosition().above(), stack, STEP);
        if (set != null) {
            player.setItemInHand(hand, set);
            Trace.log(Trace.STORM, "{}'s stormglass set, held out in the storm", player.getGameProfile().getName());
        }
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (entity.level() instanceof ServerLevel server && server.getGameTime() % STEP == 0) {
            ItemStack set = expose(server, entity.blockPosition().above(), stack, STEP);
            if (set != null) {
                entity.setItem(set);
                Trace.log(Trace.STORM, "a dropped stormglass set at {}", Trace.pos(entity.blockPosition()));
            }
        }
        return false;
    }

    /**
     * Counts a spell out in the storm, and hands back the set glass once it has had long enough.
     *
     * @return the Stormglass that replaces {@code stack}, or {@code null} while it is still setting
     */
    private static ItemStack expose(ServerLevel level, BlockPos over, ItemStack stack, int ticks) {
        if (!RiftStorm.ragingOver(level) || !level.canSeeSky(over)) {
            return null;
        }
        int progress = stack.getOrDefault(AWDataComponents.STORMGLASS_PROGRESS.get(), 0) + ticks;
        if (progress < AWConfig.STORMGLASS_SET_TICKS.get()) {
            stack.set(AWDataComponents.STORMGLASS_PROGRESS.get(), progress);
            return null;
        }
        level.playSound(null, over, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.7F);
        level.sendParticles(AWParticles.RIFT_SPARK.get(), over.getX() + 0.5D, over.getY(), over.getZ() + 0.5D,
                16, 0.3D, 0.3D, 0.3D, 0.02D);
        return new ItemStack(AWItems.STORMGLASS.get(), stack.getCount());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(AWLang.translate("item.unset_stormglass.tooltip").component().copy().withStyle(ChatFormatting.GRAY));
        int progress = stack.getOrDefault(AWDataComponents.STORMGLASS_PROGRESS.get(), 0);
        if (progress > 0) {
            tooltip.add(AWLang.translate("item.unset_stormglass.setting", AWLang.count(progress / 20))
                    .component().copy().withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }
}
