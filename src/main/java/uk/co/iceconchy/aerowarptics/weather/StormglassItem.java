package uk.co.iceconchy.aerowarptics.weather;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import uk.co.iceconchy.aerowarptics.advancement.AWCriteria;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.registry.AWParticles;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.List;

/**
 * A Stormglass: FitzRoy's storm glass, rift edition. Made by a storm, it knows storms.
 *
 * <p><b>Read it.</b> Its crystals show one of four things - clear, gathering, raging, or still - and its
 * tooltip says which. The reading is honest because the server knows the calm: it is decided before it
 * happens, and the storm packet carries it whenever it changes. See {@link StormForecast}.
 *
 * <p><b>Break it</b> to call a storm down on the whole server: the first way anyone but an operator can start
 * one. "The only thing that can call a storm is something a storm has made." Refused - and the glass kept -
 * while one rages, while the weather is frozen, too soon after the last, or on a server that allows none.
 */
public class StormglassItem extends Item {

    public StormglassItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack glass = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer caller)) {
            return InteractionResultHolder.success(glass);
        }
        StormForecast.Call result = RiftStorm.call(caller);
        if (result != StormForecast.Call.CALLED) {
            caller.displayClientMessage(AWLang.translate(result.translationKey()).component()
                    .copy().withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(glass);
        }
        server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GLASS_BREAK,
                SoundSource.PLAYERS, 1.0F, 0.8F);
        server.sendParticles(AWParticles.RIFT_SPARK.get(), player.getX(), player.getEyeY(), player.getZ(),
                40, 0.4D, 0.4D, 0.4D, 0.15D);
        AWCriteria.stormCalled(caller);
        player.awardStat(Stats.ITEM_USED.get(this));
        glass.consume(1, player);
        return InteractionResultHolder.consume(glass);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        StormForecast.Reading reading = StormForecast.Reading.byOrdinal(AWClientHooks.stormReading());
        tooltip.add(AWLang.translate("item.stormglass.reading." + reading.getSerializedName()).component()
                .copy().withStyle(reading == StormForecast.Reading.RAGING ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA));
        tooltip.add(AWLang.translate("item.stormglass.tooltip").component().copy().withStyle(ChatFormatting.GRAY));
    }
}
