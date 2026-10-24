package uk.co.iceconchy.aerowarptics.client;

import net.createmod.ponder.foundation.PonderIndex;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.simibubi.create.content.equipment.goggles.GogglesModel;
import net.minecraft.client.renderer.ShaderInstance;
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
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.compat.ponder.AWPonderPlugin;
import uk.co.iceconchy.aerowarptics.client.fx.GatePassagePainter;
import uk.co.iceconchy.aerowarptics.client.fx.GravityLens;
import uk.co.iceconchy.aerowarptics.gate.GatePassageStyle;
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
        event.registerBlockEntityRenderer(AWBlockEntities.RIFT_COUPLER.get(),
                context -> new uk.co.iceconchy.aerowarptics.client.render.RiftCouplerRenderer());
        event.registerBlockEntityRenderer(AWBlockEntities.RIFT_BRAZIER.get(),
                context -> new uk.co.iceconchy.aerowarptics.client.render.RiftBrazierRenderer());
        event.registerBlockEntityRenderer(AWBlockEntities.COLLAPSE_CRADLE.get(),
                context -> new uk.co.iceconchy.aerowarptics.client.render.CollapseCradleRenderer());
        event.registerBlockEntityRenderer(AWBlockEntities.RIFT_PLOTTER.get(),
                context -> new uk.co.iceconchy.aerowarptics.client.render.RiftPlotterRenderer());
        event.registerBlockEntityRenderer(AWBlockEntities.RIFT_WARD.get(),
                context -> new uk.co.iceconchy.aerowarptics.client.render.RiftWardRenderer());
        // A thrown Rift Pearl is drawn as its own item sprite, the way vanilla draws a thrown ender pearl.
        event.registerEntityRenderer(uk.co.iceconchy.aerowarptics.registry.AWEntities.RIFT_PEARL.get(),
                net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
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

    /** Rift Goggles worn in a Curios slot are drawn on the head, as Create's are. Only with Curios. */
    @SubscribeEvent
    public static void registerCuriosRenderers(FMLClientSetupEvent event) {
        if (uk.co.iceconchy.aerowarptics.compat.curios.CuriosBridge.loaded()) {
            event.enqueueWork(uk.co.iceconchy.aerowarptics.compat.curios.CuriosClient::registerRenderers);
        }
    }

    /**
     * The Ship's Compass needle: vanilla's compass, pointed at wherever the server last said the ship was.
     * Spins, as a lodestone compass does with no lodestone, until the server has said anything at all.
     */
    @SubscribeEvent
    public static void registerItemProperties(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            net.minecraft.client.renderer.item.ItemProperties.register(AWItems.SHIP_COMPASS.get(),
                    ResourceLocation.withDefaultNamespace("angle"),
                    new net.minecraft.client.renderer.item.CompassItemPropertyFunction((level, stack, entity) ->
                            stack.get(uk.co.iceconchy.aerowarptics.registry.AWDataComponents.SHIP_COMPASS_TARGET.get())));
            // A Stormglass's crystals show what it reads: 0 clear, .25 gathering, .5 raging, .75 still. Quarters
            // rather than the ordinal, because an item property is clamped to 0..1 and the ordinal would read
            // raging and still both as 1.
            net.minecraft.client.renderer.item.ItemProperties.register(AWItems.STORMGLASS.get(),
                    AeroWarptics.id("reading"),
                    (stack, level, entity, seed) -> ClientStormReading.get().ordinal() / 4.0F);
        });
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
    /** Connected textures for the Rift Casing, Warp Glass, Rift Gate frames and a formed Wellhead's shell. */
    @SubscribeEvent
    public static void registerModelLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(uk.co.iceconchy.aerowarptics.client.model.ConnectedGeometry.LOADER,
                uk.co.iceconchy.aerowarptics.client.model.ConnectedGeometry.Loader.INSTANCE);
    }

    @SubscribeEvent
    public static void wrapGogglesModel(ModelEvent.ModifyBakingResult event) {
        ModelResourceLocation key = ModelResourceLocation.inventory(AeroWarptics.id("rift_goggles"));
        BakedModel model = event.getModels().get(key);
        if (model != null) {
            event.getModels().put(key, new GogglesModel(model));
        }
    }

    /**
     * The mod's shaders: the lens's, and one for each style of gate passage that is drawn with one.
     *
     * <p>A shader that fails to load is logged and left out rather than thrown: all of them are decoration.
     * Without its shader the lens simply stands down and the rift draws its ring of bent light instead, and
     * a gate passage in that style is drawn the plain way. A resource pack that broke one should cost a
     * flourish, not the game - and not the other shaders either, which is why each has a {@code try} of its
     * own.
     */
    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), AeroWarptics.id("gravity_lens"),
                    DefaultVertexFormat.POSITION_TEX_COLOR), GravityLens::setShader);
        } catch (java.io.IOException failure) {
            AeroWarptics.LOGGER.error("The rift lens shader did not load; rifts will not bend the view", failure);
        }
        for (GatePassageStyle style : GatePassageStyle.values()) {
            String shader = style.shader();
            if (shader == null) {
                continue;
            }
            try {
                event.registerShader(new ShaderInstance(event.getResourceProvider(), AeroWarptics.id(shader),
                        DefaultVertexFormat.POSITION_TEX), loaded -> GatePassagePainter.setShader(style, loaded));
            } catch (java.io.IOException failure) {
                // Not left holding the one from before a reload: that has been closed.
                GatePassagePainter.setShader(style, null);
                AeroWarptics.LOGGER.error("The {} gate passage's shader did not load; it will be drawn plain",
                        style, failure);
            }
        }
    }

    /**
     * A gate passage, over everything else on the screen.
     *
     * <p>Above all, not above any one vanilla layer: it has to cover the chat and a boss bar as surely as
     * the hotbar, and a mod's own layer is drawn whether or not the HUD is hidden, which vanilla's are not.
     */
    @SubscribeEvent
    public static void registerGuiLayers(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) {
        event.registerAboveAll(AeroWarptics.id("gate_passage"),
                uk.co.iceconchy.aerowarptics.client.fx.GatePassageOverlay::render);
    }

    /** The Collapse Cradle's panel, the mod's one container screen. */
    @SubscribeEvent
    public static void registerMenuScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(uk.co.iceconchy.aerowarptics.registry.AWMenus.COLLAPSE_CRADLE.get(),
                uk.co.iceconchy.aerowarptics.client.screen.CollapseCradleScreen::new);
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
