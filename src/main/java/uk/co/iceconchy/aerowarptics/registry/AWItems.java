package uk.co.iceconchy.aerowarptics.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import uk.co.iceconchy.aerowarptics.beacon.RiftBeaconItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;
import uk.co.iceconchy.aerowarptics.fissure.RiftGogglesItem;
import uk.co.iceconchy.aerowarptics.guide.HandbookItem;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
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

    /**
     * Create's goggles with a rift-ground lens in them.
     *
     * <p>Registered as an item of ours rather than as a variant of Create's, because the thing that
     * makes them different - seeing a Rift Fissure - is a question this mod asks about the stack in
     * a player's head slot, and that question needs an item to point at.
     */
    public static final DeferredItem<Item> RIFT_GOGGLES =
            ITEMS.register("rift_goggles", () -> new RiftGogglesItem(new Item.Properties().stacksTo(1)));

    /** The shared machinery core every Rift Drive is built around. */
    /**
     * Calls a bound airship down to wherever its holder is pointing.
     *
     * <p>Stacks to one, and not for inventory tidiness: a beacon carries which ship it answers to,
     * so two of them are only interchangeable when neither has been bound. Letting them stack would
     * mean picking one up could quietly overwrite the other's ship.
     */
    public static final DeferredItem<Item> RIFT_BEACON =
            ITEMS.register("rift_beacon", () -> new RiftBeaconItem(new Item.Properties().stacksTo(1)));

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

    /**
     * The machines whose item form is drawn from their own GeckoLib model.
     *
     * <p>The Astrolabe is deliberately not one of them: a cell has a real baked model, and that model
     * is what a player is holding. Rendering its GeckoLib geometry would put the whole assembled
     * three-by-three table in the slot, for an item that is one ninth of one.
     */
    public static final DeferredItem<GeoBlockItem> WARP_ANCHOR =
            ITEMS.register("warp_anchor", () -> new GeoBlockItem(AWBlocks.WARP_ANCHOR.get(),
                    new Item.Properties(), "warp_anchor", "warp_anchor", "warp_anchor",
                    "animation.warp_anchor.idle"));

    public static final DeferredItem<BlockItem> RIFT_GATE_FRAME =
            ITEMS.registerSimpleBlockItem("rift_gate_frame", AWBlocks.RIFT_GATE_FRAME);

    public static final DeferredItem<BlockItem> RIFT_GATE =
            ITEMS.registerSimpleBlockItem("rift_gate", AWBlocks.RIFT_GATE);

    public static final DeferredItem<BlockItem> ASTROLABE =
            ITEMS.registerSimpleBlockItem("astrolabe", AWBlocks.ASTROLABE);

    public static final DeferredItem<GeoBlockItem> RIFT_CHUTE =
            ITEMS.register("rift_chute", () -> new GeoBlockItem(AWBlocks.RIFT_CHUTE.get(),
                    new Item.Properties(), "rift_chute", "rift_chute", "rift_chute",
                    "animation.rift_chute.idle"));

    public static final DeferredItem<BlockItem> RIFT_PROBE =
            ITEMS.registerSimpleBlockItem("rift_probe", AWBlocks.RIFT_PROBE);

    public static final DeferredItem<GeoBlockItem> RIFT_MODULATOR =
            ITEMS.register("rift_modulator", () -> new GeoBlockItem(AWBlocks.RIFT_MODULATOR.get(),
                    new Item.Properties(), "rift_modulator", "rift_modulator", "rift_modulator",
                    "animation.rift_modulator.idle"));

    public static final DeferredItem<GeoBlockItem> SPATIAL_SIPHON =
            ITEMS.register("spatial_siphon", () -> new GeoBlockItem(AWBlocks.SPATIAL_SIPHON.get(),
                    new Item.Properties(), "spatial_siphon", "spatial_siphon", "spatial_siphon",
                    "animation.spatial_siphon.idle", "essence"));

    /** A bucket of what a Spatial Siphon collects, so the fluid can be moved by hand as well as by pipe. */
    public static final DeferredItem<BucketItem> RIFT_ESSENCE_BUCKET =
            ITEMS.register("rift_essence_bucket", () -> new BucketItem(AWFluids.RIFT_ESSENCE.get(),
                    new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    /** One per tier: the same geometry and animation, and the tier's own skin. */
    public static final Map<RiftDriveTier, DeferredItem<GeoBlockItem>> RIFT_DRIVES =
            new EnumMap<>(RiftDriveTier.class);

    static {
        for (RiftDriveTier tier : RiftDriveTier.values()) {
            RIFT_DRIVES.put(tier, ITEMS.register(tier.blockName(),
                    () -> new GeoBlockItem(AWBlocks.RIFT_DRIVES.get(tier).get(), new Item.Properties(),
                            "rift_drive", tier.blockName(), "rift_drive", "animation.rift_drive.idle")));
        }
    }

    /**
     * Every item drawn by its block's GeckoLib model, for the client to hand a renderer to.
     *
     * <p>Gathered here rather than listed again on the client, so adding a machine cannot leave it
     * with a model, an item and nothing to draw it - which renders as an empty slot, not an error.
     */
    public static List<DeferredItem<GeoBlockItem>> geoBlockItems() {
        List<DeferredItem<GeoBlockItem>> items = new ArrayList<>(RIFT_DRIVES.values());
        items.add(WARP_ANCHOR);
        items.add(SPATIAL_SIPHON);
        items.add(RIFT_CHUTE);
        items.add(RIFT_MODULATOR);
        return List.copyOf(items);
    }

    private AWItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
