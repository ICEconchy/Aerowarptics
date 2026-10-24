package uk.co.iceconchy.aerowarptics.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.fx.WardEffects;
import uk.co.iceconchy.aerowarptics.ward.RiftWardBlockEntity;
import uk.co.iceconchy.aerowarptics.ward.WardWorks;

/**
 * The light in the Rift Ward: the figures cut into its totem, the bands round each piece, the broken faces between
 * them and the thread of void they hang on, and the glyphs round its plinth - the cradle's {@link CradleGlowLayer},
 * lit by {@link WardWorks#glow}. Out altogether while the machine is switched off, low and slow while the field
 * stands, guttering while it is down, and flaring while the totem takes the strain of a rift. It is the first of the
 * ways a ward says whether it is on.
 */
@OnlyIn(Dist.CLIENT)
public final class WardGlowLayer extends GeoRenderLayer<RiftWardBlockEntity> {

    private static final ResourceLocation GLOW = AeroWarptics.id("textures/block/rift_ward_glow.png");
    private static final RenderType TYPE = RenderType.eyes(GLOW);
    private static final int FULL_BRIGHT = 0xF0_00F0;

    public WardGlowLayer(GeoRenderer<RiftWardBlockEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, RiftWardBlockEntity ward, BakedGeoModel model, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                       int packedLight, int packedOverlay) {
        double time = (ward.getLevel() == null ? 0L : ward.getLevel().getGameTime()) + partialTick;
        double strain = WardEffects.pull(ward.getBlockPos(), partialTick).strain();
        int level = (int) Math.round(255.0D * WardWorks.glow(ward.enabled(), ward.standing(), ward.charged(), strain,
                time));
        if (level <= 2) {
            return;
        }
        // The sheet is painted in the carvings' own violet, so it is only ever dimmed, never tinted.
        int colour = 0xFF_000000 | level << 16 | level << 8 | level;
        getRenderer().reRender(model, poseStack, bufferSource, ward, TYPE, bufferSource.getBuffer(TYPE),
                partialTick, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, colour);
    }
}
