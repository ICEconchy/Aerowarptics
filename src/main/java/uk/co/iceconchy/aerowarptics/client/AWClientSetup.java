package uk.co.iceconchy.aerowarptics.client;

import net.createmod.ponder.foundation.PonderIndex;
import com.simibubi.create.content.equipment.goggles.GogglesModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.compat.ponder.AWPonderPlugin;
import uk.co.iceconchy.aerowarptics.client.fx.RiftSparkParticle;
import uk.co.iceconchy.aerowarptics.client.fx.WarpStreakParticle;
import uk.co.iceconchy.aerowarptics.client.render.AstrolabeRenderer;
import uk.co.iceconchy.aerowarptics.client.render.RiftDriveRenderer;
import uk.co.iceconchy.aerowarptics.client.render.SpatialSiphonRenderer;
import uk.co.iceconchy.aerowarptics.client.render.WarpAnchorRenderer;
import net.neoforged.neoforge.registries.DeferredItem;
import uk.co.iceconchy.aerowarptics.client.render.GeoBlockItemRenderer;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.registry.AWItems;
import uk.co.iceconchy.aerowarptics.registry.AWFluids;
import uk.co.iceconchy.aerowarptics.registry.AWParticles;

/** Client-side registration: GeckoLib block entity renderers, particles, and how Rift Essence looks. */
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class AWClientSetup {

    private static final ResourceLocation ESSENCE_STILL = AeroWarptics.id("block/rift_essence_still");
    private static final ResourceLocation ESSENCE_FLOW = AeroWarptics.id("block/rift_essence_flow");

    private AWClientSetup() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AWBlockEntities.RIFT_DRIVE.get(), context -> new RiftDriveRenderer());
        event.registerBlockEntityRenderer(AWBlockEntities.WARP_ANCHOR.get(), context -> new WarpAnchorRenderer());
        event.registerBlockEntityRenderer(AWBlockEntities.ASTROLABE.get(), context -> new AstrolabeRenderer());
        event.registerBlockEntityRenderer(AWBlockEntities.RIFT_CHUTE.get(),
                context -> new uk.co.iceconchy.aerowarptics.client.render.RiftChuteRenderer());
        event.registerBlockEntityRenderer(AWBlockEntities.SPATIAL_SIPHON.get(),
                context -> new SpatialSiphonRenderer());
        event.registerBlockEntityRenderer(AWBlockEntities.RIFT_MODULATOR.get(),
                context -> new uk.co.iceconchy.aerowarptics.client.render.RiftModulatorRenderer());
    }

    /**
     * Adds this mod's scenes to the Ponder index.
     *
     * <p>Client setup is where Create adds its own, and everything registered by then is gathered
     * together later at load complete - so no ordering against Create is needed, only that this
     * happens first. Queued rather than called straight out because client setup runs in parallel
     * across mods and the index is a plain list.
     */
    @SubscribeEvent
    public static void registerPonder(FMLClientSetupEvent event) {
        event.enqueueWork(() -> PonderIndex.addPlugin(new AWPonderPlugin()));
    }

    /**
     * Puts the Rift Infused Goggles on the player's face rather than on top of their head.
     *
     * <p>Vanilla renders any non-helmet item worn in the head slot with the item's own model and the
     * {@code HEAD} display transform, which for a flat sprite means a card standing up out of your
     * scalp. Create solved that for its own goggles with a model wrapper that supplies a sensible
     * transform, and the class is public - so ours borrows it rather than copying the numbers out of
     * it and drifting the first time Create adjusts them.
     */
    @SubscribeEvent
    public static void wrapGogglesModel(ModelEvent.ModifyBakingResult event) {
        ModelResourceLocation key = ModelResourceLocation.inventory(AeroWarptics.id("rift_goggles"));
        BakedModel model = event.getModels().get(key);
        if (model != null) {
            event.getModels().put(key, new GogglesModel(model));
        }
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(AWParticles.RIFT_SPARK.get(), RiftSparkParticle.Provider::new);
        event.registerSpriteSet(AWParticles.WARP_STREAK.get(), WarpStreakParticle.Provider::new);
    }

    /**
     * How Rift Essence is drawn.
     *
     * <p>Untinted: the textures already carry the colour, and a tint on top of them would fight the
     * light level the fluid type emits.
     */
    /**
     * Hands the block items their renderer.
     *
     * <p>Built on first use rather than here: a {@code GeoItemRenderer} asks Minecraft for the block
     * entity dispatcher and the entity model set as it is constructed, and this event fires early
     * enough that reaching for either is a gamble. Nothing asks for the renderer until something is
     * drawn, by which time both certainly exist.
     */
    @SubscribeEvent
    public static void registerItemRenderers(RegisterClientExtensionsEvent event) {
        IClientItemExtensions renderer = new IClientItemExtensions() {

            private GeoBlockItemRenderer geoRenderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (geoRenderer == null) {
                    geoRenderer = new GeoBlockItemRenderer();
                }
                return geoRenderer;
            }
        };
        event.registerItem(renderer, AWItems.geoBlockItems().stream()
                .map(DeferredItem::get)
                .toArray(Item[]::new));
    }

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return ESSENCE_STILL;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return ESSENCE_FLOW;
            }
        }, AWFluids.RIFT_ESSENCE_TYPE.get());
    }
}
