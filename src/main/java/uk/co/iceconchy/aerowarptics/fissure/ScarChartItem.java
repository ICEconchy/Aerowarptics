package uk.co.iceconchy.aerowarptics.fissure;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.List;

/**
 * A Scar Chart: opened, it becomes a map to the nearest rift scar nobody has charted yet.
 *
 * <p>Scars are only ever found, never made, and a Scar Heart comes only from a sealed one. (A Rift Sunderer tears
 * a fissure of its own, but a conjured tear seals with no heart - see {@code SundererBreach}.) So finding one has
 * to be a trip rather than a search, and this is the tool that makes it one.
 * It is cheap, and needs no essence, so a player with no drive can use it.
 *
 * <p>Vanilla's treasure maps, exactly: {@code findNearestMapStructure} over
 * {@code #aerowarptics:on_scar_maps}, located from the structure's placement rather than by loading the
 * ground, and skipping any scar a chart has already been drawn to - so a second chart leads somewhere
 * else, which is what "unvisited" means here.
 */
public class ScarChartItem extends Item {

    public static final TagKey<Structure> ON_SCAR_MAPS =
            TagKey.create(Registries.STRUCTURE, AeroWarptics.id("on_scar_maps"));

    /** Chunks searched outward, the same reach as a cartographer's map. */
    private static final int SEARCH_RADIUS = 100;

    public ScarChartItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack chart = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server)) {
            return InteractionResultHolder.success(chart);
        }
        BlockPos scar = server.findNearestMapStructure(ON_SCAR_MAPS, player.blockPosition(), SEARCH_RADIUS, true);
        if (scar == null) {
            Trace.log(Trace.RIFTING, "{} opened a scar chart at {}: no uncharted scar within {} chunks",
                    player.getGameProfile().getName(), Trace.pos(player.blockPosition()), SEARCH_RADIUS);
            player.displayClientMessage(AWLang.translate("item.scar_chart.none").component()
                    .copy().withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(chart);
        }
        ItemStack map = MapItem.create(server, scar.getX(), scar.getZ(), (byte) 2, true, true);
        MapItem.renderBiomePreviewMap(server, map);
        MapItemSavedData.addTargetDecoration(map, scar, "+", MapDecorationTypes.RED_X);
        map.set(DataComponents.ITEM_NAME, Component.translatable("filled_map.aerowarptics.rift_scar"));
        Trace.log(Trace.RIFTING, "{} opened a scar chart at {}: charted the scar at {}",
                player.getGameProfile().getName(), Trace.pos(player.blockPosition()), Trace.pos(scar));

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN,
                SoundSource.PLAYERS, 1.0F, 0.8F);
        player.awardStat(Stats.ITEM_USED.get(this));
        chart.consume(1, player);
        if (chart.isEmpty()) {
            return InteractionResultHolder.consume(map);
        }
        if (!player.getInventory().add(map)) {
            player.drop(map, false);
        }
        return InteractionResultHolder.consume(chart);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(AWLang.translate("item.scar_chart.tooltip").component().copy().withStyle(ChatFormatting.GRAY));
    }
}
