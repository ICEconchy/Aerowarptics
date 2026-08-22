package uk.co.iceconchy.aerowarptics.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;
import uk.co.iceconchy.aerowarptics.guide.HandbookItem;

import java.util.EnumMap;
import java.util.Map;

/** Item registration: block items plus the crafting components the drives are built from. */
public final class AWItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AeroWarptics.MODID);

    /**
     * The guide, as an item.
     *
     * <p>First in the file and first in the creative tab, because it is the one thing here that is
     * about the other twelve.
     */
    public static final DeferredItem<Item> HANDBOOK =
            ITEMS.register("handbook", () -> new HandbookItem(new Item.Properties().stacksTo(1)));

    /** The shared machinery core every Rift Drive is built around. */
    public static final DeferredItem<Item> RIFT_CORE =
            ITEMS.registerSimpleItem("rift_core", new Item.Properties());

    /** Focusing lens used to aim a rift; scales with drive tier. */
    public static final DeferredItem<Item> RIFT_LENS =
            ITEMS.registerSimpleItem("rift_lens", new Item.Properties());

    /** Stabiliser ring, the part that keeps the corridor from collapsing. */
    public static final DeferredItem<Item> STABILISER_RING =
            ITEMS.registerSimpleItem("stabiliser_ring", new Item.Properties());

    /** Collapsed core, only obtainable at the top of the progression. */
    public static final DeferredItem<Item> SINGULARITY_CORE =
            ITEMS.registerSimpleItem("singularity_core", new Item.Properties());

    public static final DeferredItem<BlockItem> WARP_ANCHOR =
            ITEMS.registerSimpleBlockItem("warp_anchor", AWBlocks.WARP_ANCHOR);

    public static final DeferredItem<BlockItem> RIFT_GATE_FRAME =
            ITEMS.registerSimpleBlockItem("rift_gate_frame", AWBlocks.RIFT_GATE_FRAME);

    public static final DeferredItem<BlockItem> RIFT_GATE =
            ITEMS.registerSimpleBlockItem("rift_gate", AWBlocks.RIFT_GATE);

    public static final DeferredItem<BlockItem> ASTROLABE =
            ITEMS.registerSimpleBlockItem("astrolabe", AWBlocks.ASTROLABE);

    public static final DeferredItem<BlockItem> RIFT_CHUTE =
            ITEMS.registerSimpleBlockItem("rift_chute", AWBlocks.RIFT_CHUTE);

    public static final DeferredItem<BlockItem> RIFT_PROBE =
            ITEMS.registerSimpleBlockItem("rift_probe", AWBlocks.RIFT_PROBE);

    public static final DeferredItem<BlockItem> SPATIAL_SIPHON =
            ITEMS.registerSimpleBlockItem("spatial_siphon", AWBlocks.SPATIAL_SIPHON);

    /** A bucket of what a Spatial Siphon collects, so the fluid can be moved by hand as well as by pipe. */
    public static final DeferredItem<BucketItem> RIFT_ESSENCE_BUCKET =
            ITEMS.register("rift_essence_bucket", () -> new BucketItem(AWFluids.RIFT_ESSENCE.get(),
                    new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    public static final Map<RiftDriveTier, DeferredItem<BlockItem>> RIFT_DRIVES =
            new EnumMap<>(RiftDriveTier.class);

    static {
        for (RiftDriveTier tier : RiftDriveTier.values()) {
            RIFT_DRIVES.put(tier, ITEMS.registerSimpleBlockItem(tier.blockName(), AWBlocks.RIFT_DRIVES.get(tier)));
        }
    }

    private AWItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
