package uk.co.iceconchy.aerowarptics.client;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.fx.RiftSparkParticle;
import uk.co.iceconchy.aerowarptics.client.fx.WarpStreakParticle;
import uk.co.iceconchy.aerowarptics.client.render.AstrolabeRenderer;
import uk.co.iceconchy.aerowarptics.client.render.RiftDriveRenderer;
import uk.co.iceconchy.aerowarptics.client.render.SpatialSiphonRenderer;
import uk.co.iceconchy.aerowarptics.client.render.WarpAnchorRenderer;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
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
        event.registerBlockEntityRenderer(AWBlockEntities.SPATIAL_SIPHON.get(),
                context -> new SpatialSiphonRenderer());
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
