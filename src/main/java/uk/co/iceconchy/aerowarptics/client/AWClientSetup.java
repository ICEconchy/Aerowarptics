package uk.co.iceconchy.aerowarptics.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.fx.RiftSparkParticle;
import uk.co.iceconchy.aerowarptics.client.fx.WarpStreakParticle;
import uk.co.iceconchy.aerowarptics.client.render.RiftDriveRenderer;
import uk.co.iceconchy.aerowarptics.client.render.WarpAnchorRenderer;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.registry.AWParticles;

/** Client-side registration: GeckoLib block entity renderers and particle providers. */
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class AWClientSetup {

    private AWClientSetup() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AWBlockEntities.RIFT_DRIVE.get(), context -> new RiftDriveRenderer());
        event.registerBlockEntityRenderer(AWBlockEntities.WARP_ANCHOR.get(), context -> new WarpAnchorRenderer());
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(AWParticles.RIFT_SPARK.get(), RiftSparkParticle.Provider::new);
        event.registerSpriteSet(AWParticles.WARP_STREAK.get(), WarpStreakParticle.Provider::new);
    }
}
