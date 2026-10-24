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

    /**
     * A Rift Core part-way down its assembly line: a Precision Mechanism with some of its tubes, crystals and
     * brass on. Create's own transitional item, so it carries the line's progress bar and is what a Deployer
     * or a Press on the line recognises.
     */
    public static final DeferredItem<Item> INCOMPLETE_RIFT_CORE =
            ITEMS.register("incomplete_rift_core",
                    () -> new com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem(new Item.Properties()));

    /** Rift Essence in a shell - see {@link uk.co.iceconchy.aerowarptics.pearl.RiftPearlItem}. */
    public static final DeferredItem<Item> RIFT_PEARL =
            ITEMS.register("rift_pearl", () -> new uk.co.iceconchy.aerowarptics.pearl.RiftPearlItem(
                    new Item.Properties().stacksTo(16)));

    /** Focusing lens used to aim a rift; scales with drive tier. */
    public static final DeferredItem<Item> RIFT_LENS =
            ITEMS.registerSimpleItem("rift_lens", new Item.Properties());

    /**
     * A Rift Lens that has seen a rift: blown through a standing one by a fan. Everything that tears a rift of
     * its own is built with one - the Mk II and up, gate controllers, chutes, probes and couplers - and the
     * Mk I, which is built with a plain lens, is the one that has to tear the first rift blind.
     */
    public static final DeferredItem<Item> OPENED_RIFT_LENS =
            ITEMS.registerSimpleItem("opened_rift_lens", new Item.Properties());

    /**
     * What a sealed fissure leaves behind: the one thing a Singularity Core can collapse into. Found, never
     * made - see {@code RiftFissureBlockEntity.close}; a fissure a Rift Sunderer tore seals with none.
     */
    public static final DeferredItem<Item> SCAR_HEART =
            ITEMS.registerSimpleItem("scar_heart", new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE));

    /**
     * The rings at the heart of a Collapse Cradle, the one part of it nobody knows how to make any more: found
     * in the caches whoever built the rift scars left behind, never made. Everything else on a cradle is new.
     */
    public static final DeferredItem<Item> RELIC_ARMILLARY =
            ITEMS.registerSimpleItem("relic_armillary", new Item.Properties().stacksTo(16)
                    .rarity(net.minecraft.world.item.Rarity.RARE));

    /** Opened, becomes a map to the nearest rift scar nobody has charted - see {@link uk.co.iceconchy.aerowarptics.fissure.ScarChartItem}. */
    public static final DeferredItem<Item> SCAR_CHART =
            ITEMS.register("scar_chart", () -> new uk.co.iceconchy.aerowarptics.fissure.ScarChartItem(new Item.Properties()));

    /** Stabiliser ring, the part that keeps the corridor from collapsing. */
    public static final DeferredItem<Item> STABILISER_RING =
            ITEMS.registerSimpleItem("stabiliser_ring", new Item.Properties());

    /** Collapsed core, only obtainable at the top of the progression. */
    public static final DeferredItem<Item> SINGULARITY_CORE =
            ITEMS.registerSimpleItem("singularity_core", new Item.Properties());

    /**
     * Warp matter: the common feedstock, mixed from amethyst and an ender pearl.
     *
     * <p>The mixer is the only source, deliberately. Fissures once grew an ore that crushed into this, and
     * that was retired so a tear never touches the ground around it - see {@code docs/crafting-plan.md}.
     */
    public static final DeferredItem<Item> WARP_DUST =
            ITEMS.registerSimpleItem("warp_dust", new Item.Properties());

    /** Warp dust fused over soul fire, a step from being pressed into a finished crystal. */
    public static final DeferredItem<Item> WARP_SHARD =
            ITEMS.registerSimpleItem("warp_shard", new Item.Properties());

    /** Mid-tier crafting stock, pressed from a shard. The drives are built on it. */
    public static final DeferredItem<Item> WARP_CRYSTAL =
            ITEMS.registerSimpleItem("warp_crystal", new Item.Properties());

    /**
     * A Warp Crystal folded at a Collapse Cradle, for essence. The Mk III is built from it, so the third drive
     * asks for the cradle, and the cradle for a Relic Armillary found in the ruins.
     */
    public static final DeferredItem<Item> FOLDED_WARP_CRYSTAL =
            ITEMS.registerSimpleItem("folded_warp_crystal", new Item.Properties());

    /** A compass folded aboard a ship, which points at it - see {@link uk.co.iceconchy.aerowarptics.fold.ShipCompassItem}. */
    public static final DeferredItem<Item> SHIP_COMPASS =
            ITEMS.register("ship_compass", () -> new uk.co.iceconchy.aerowarptics.fold.ShipCompassItem(
                    new Item.Properties().stacksTo(1)));

    /** Half a bucket of essence in a glass bottle, waiting for a storm to set it - see {@link uk.co.iceconchy.aerowarptics.weather.UnsetStormglassItem}. */
    public static final DeferredItem<Item> UNSET_STORMGLASS =
            ITEMS.register("unset_stormglass", () -> new uk.co.iceconchy.aerowarptics.weather.UnsetStormglassItem(
                    new Item.Properties().stacksTo(16)));

    /** A storm glass that knows Rift Storms - see {@link uk.co.iceconchy.aerowarptics.weather.StormglassItem}. */
    public static final DeferredItem<Item> STORMGLASS =
            ITEMS.register("stormglass", () -> new uk.co.iceconchy.aerowarptics.weather.StormglassItem(
                    new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));

    /** The refined, radiant crystal the top tier is built from. */
    public static final DeferredItem<Item> PEARLESCENT_WARP_CRYSTAL =
            ITEMS.registerSimpleItem("pearlescent_warp_crystal", new Item.Properties());

    /** The anchor's lantern stands three-quarters of a block above it; this brings the whole of it into a slot. */
    public static final float ANCHOR_ITEM_SCALE = 0.7F;

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
                    "animation.warp_anchor.idle").drawnAt(ANCHOR_ITEM_SCALE));

    public static final DeferredItem<BlockItem> RIFT_CASING =
            ITEMS.registerSimpleBlockItem("rift_casing", AWBlocks.RIFT_CASING);

    public static final DeferredItem<BlockItem> WARP_GLASS =
            ITEMS.registerSimpleBlockItem("warp_glass", AWBlocks.WARP_GLASS);

    /** The whole three-block machine in one item, set down whole - see {@link uk.co.iceconchy.aerowarptics.collapse.CollapseCradleItem}. */
    public static final DeferredItem<GeoBlockItem> COLLAPSE_CRADLE =
            ITEMS.register("collapse_cradle", () -> new uk.co.iceconchy.aerowarptics.collapse.CollapseCradleItem(
                    AWBlocks.COLLAPSE_CRADLE.get(), new Item.Properties().rarity(net.minecraft.world.item.Rarity.UNCOMMON)));

    /** The Rift Ward, whole, in one item - see {@link uk.co.iceconchy.aerowarptics.ward.RiftWardItem}. */
    public static final DeferredItem<GeoBlockItem> RIFT_WARD =
            ITEMS.register("rift_ward", () -> new uk.co.iceconchy.aerowarptics.ward.RiftWardItem(
                    AWBlocks.RIFT_WARD.get(), new Item.Properties().rarity(net.minecraft.world.item.Rarity.UNCOMMON)));

    /** The Rift Plotter, whole, in one item - see {@link uk.co.iceconchy.aerowarptics.plotter.RiftPlotterItem}. */
    public static final DeferredItem<GeoBlockItem> RIFT_PLOTTER =
            ITEMS.register("rift_plotter", () -> new uk.co.iceconchy.aerowarptics.plotter.RiftPlotterItem(
                    AWBlocks.RIFT_PLOTTER.get(), new Item.Properties().rarity(net.minecraft.world.item.Rarity.UNCOMMON)));

    public static final DeferredItem<GeoBlockItem> RIFT_BRAZIER =
            ITEMS.register("rift_brazier", () -> new GeoBlockItem(AWBlocks.RIFT_BRAZIER.get(),
                    new Item.Properties(), "rift_brazier", "rift_brazier", "rift_brazier",
                    "animation.rift_brazier.idle"));

    /** Held as the loose casing block it is until a frame forms, so a plain block item. */
    public static final DeferredItem<BlockItem> DRIVE_EXTENDER =
            ITEMS.registerSimpleBlockItem("drive_extender", AWBlocks.DRIVE_EXTENDER);

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

    public static final DeferredItem<GeoBlockItem> RIFT_COUPLER =
            ITEMS.register("rift_coupler", () -> new GeoBlockItem(AWBlocks.RIFT_COUPLER.get(),
                    new Item.Properties(), "rift_coupler", "rift_coupler", "rift_coupler",
                    "animation.rift_coupler.idle"));

    public static final DeferredItem<BlockItem> SPATIAL_STABILISER =
            ITEMS.registerSimpleBlockItem("spatial_stabiliser", AWBlocks.SPATIAL_STABILISER);

    public static final DeferredItem<BlockItem> COOLING_VENT =
            ITEMS.registerSimpleBlockItem("cooling_vent", AWBlocks.COOLING_VENT);

    /**
     * A Rift Fissure in the hand: the tear as a block somebody can set down.
     *
     * <p>Uncraftable, like the creative drive. A fissure is found at a scar, or torn by a Rift Sunderer (as a
     * conjured tear, by {@code SundererBreach} alone), and drops nothing, so this cannot be come by in survival. It is in the creative
     * tab for the people that rule is not for: somebody setting up a build, a test or a map, who would otherwise
     * need {@code /setblock} and a block id. A tear set down from it is a tear like any other: it holds what any
     * fissure at that spot holds, and it cannot be seen without Rift Infused Goggles.
     */
    public static final DeferredItem<BlockItem> RIFT_FISSURE = ITEMS.register("rift_fissure",
            () -> new BlockItem(AWBlocks.RIFT_FISSURE.get(), new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC)));

    public static final DeferredItem<BlockItem> SCARRED_STONE =
            ITEMS.registerSimpleBlockItem("scarred_stone", AWBlocks.SCARRED_STONE);

    /** The Rift Collar: the master block of a Rift Wellhead, set in the side of a frame built round a fissure. */
    public static final DeferredItem<BlockItem> RIFT_COLLAR =
            ITEMS.registerSimpleBlockItem("rift_collar", AWBlocks.RIFT_COLLAR);

    /** The Rift Sunderer: set in a ring of gate frame, it tears a fissure and is used up doing it. */
    public static final DeferredItem<BlockItem> RIFT_SUNDERER =
            ITEMS.registerSimpleBlockItem("rift_sunderer", AWBlocks.RIFT_SUNDERER);

    /** Not a plain block item: the stack remembers the bond it was tuned to - see RiftBondItem. */
    public static final DeferredItem<uk.co.iceconchy.aerowarptics.bond.RiftBondItem> RIFT_BOND =
            ITEMS.register("rift_bond", () -> new uk.co.iceconchy.aerowarptics.bond.RiftBondItem(
                    AWBlocks.RIFT_BOND.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> BERTH =
            ITEMS.registerSimpleBlockItem("berth", AWBlocks.BERTH);

    public static final DeferredItem<BlockItem> SPATIAL_WAVE_DETECTOR =
            ITEMS.registerSimpleBlockItem("spatial_wave_detector", AWBlocks.SPATIAL_WAVE_DETECTOR);

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
        items.add(RIFT_COUPLER);
        items.add(RIFT_BRAZIER);
        items.add(COLLAPSE_CRADLE);
        items.add(RIFT_PLOTTER);
        items.add(RIFT_WARD);
        return List.copyOf(items);
    }

    private AWItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
