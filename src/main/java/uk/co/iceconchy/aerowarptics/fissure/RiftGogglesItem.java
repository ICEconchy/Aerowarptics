package uk.co.iceconchy.aerowarptics.fissure;

import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import uk.co.iceconchy.aerowarptics.registry.AWItems;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.List;

/**
 * Rift Infused Goggles: Create's goggles, with a lens ground from the same stuff a rift is made of.
 *
 * <p>They do everything the engineer's pair does - Create's overlay reads from a list of predicates
 * rather than from one item, so registering ours means every machine in every mod that answers to
 * goggles answers to these too - and they do one thing more. A {@link RiftFissureBlock} is drawn
 * only for a player wearing them.
 *
 * <p>Extending Create's item rather than reimplementing it is the whole point. Right-clicking to
 * equip, the head slot, the way they sit alongside a helmet: all of that is behaviour a player
 * already knows, and a second implementation of it would eventually disagree with the first.
 */
public class RiftGogglesItem extends GogglesItem {

    public RiftGogglesItem(Properties properties) {
        super(properties);
    }

    /**
     * Whether this player is looking through a rift-infused lens.
     *
     * <p>Deliberately not {@code GogglesItem.isWearingGoggles}: that answers "can they read a
     * machine", which an ordinary engineer's pair also can. This answers "can they see torn space",
     * which only these can - so the two questions stay separate and a plain pair of goggles never
     * quietly starts revealing fissures.
     */
    public static boolean isWorn(Player player) {
        return player != null
                && player.getItemBySlot(EquipmentSlot.HEAD).is(AWItems.RIFT_GOGGLES.get());
    }

    /**
     * Tells Create that these count.
     *
     * <p>Create keeps a list of predicates rather than one item, precisely so an addon can add a pair
     * of its own without either mod knowing about the other's. Registering here means every goggle
     * tooltip in the game - Create's, this mod's, anybody else's - works while these are worn.
     */
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> GogglesItem.addIsWearingPredicate(RiftGogglesItem::isWorn));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(AWLang.translate("goggles.tip").component()
                .copy().withStyle(ChatFormatting.DARK_GRAY));
    }
}
