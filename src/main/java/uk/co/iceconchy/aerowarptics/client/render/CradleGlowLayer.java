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
import uk.co.iceconchy.aerowarptics.client.fx.CollapseSpectacle;
import uk.co.iceconchy.aerowarptics.collapse.CollapseCradleBlockEntity;
import uk.co.iceconchy.aerowarptics.collapse.CradleWorks;

/**
 * The light in the Collapse Cradle's carvings: the glyphs round its plinth and crown, the channels cut in its
 * deck, the seams down its pillars and the veins inside its rings.
 *
 * <p>A second sheet on the model's own packing, black wherever nothing glows, drawn over the machine
 * additively - so black adds nothing, and the rest adds its colour at the brightness {@link CradleWorks#glow}
 * gives: faint at rest, waking with the spin-up, pulsing with the Scar Heart, dimming in the hush and flaring
 * at the collapse; warming through a fold, with a flash as it is made. A collapse heading for trouble makes it
 * stutter and run hot, violet towards red. Additive light is never dark, which is why the carvings' darkness is
 * painted into the machine's own sheet and only their light is here.
 */
@OnlyIn(Dist.CLIENT)
public final class CradleGlowLayer extends GeoRenderLayer<CollapseCradleBlockEntity> {

    private static final ResourceLocation GLOW = AeroWarptics.id("textures/block/collapse_cradle_glow.png");
    private static final RenderType TYPE = RenderType.eyes(GLOW);
    private static final int FULL_BRIGHT = 0xF0_00F0;

    public CradleGlowLayer(GeoRenderer<CollapseCradleBlockEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, CollapseCradleBlockEntity cradle, BakedGeoModel model, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                       int packedLight, int packedOverlay) {
        float beat = cradle.running() ? CollapseSpectacle.beat(cradle.getBlockPos(), partialTick) : 0.0F;
        double glow = cradle.folding() ? CradleWorks.foldGlow(cradle.foldProgress(partialTick))
                : CradleWorks.glow(cradle.progress(partialTick), cradle.running(), cradle.after(partialTick), beat);
        // Failing, the light stutters and runs hot: violet towards an angry red.
        double strain = CollapseCradleRenderer.strain(cradle);
        long tick = cradle.getLevel() == null ? 0L : cradle.getLevel().getGameTime();
        glow *= CradleWorks.flicker(strain, tick);
        double[] hot = CradleWorks.hot(strain);
        int red = (int) Math.round(glow * hot[0] * 255.0D);
        int green = (int) Math.round(glow * hot[1] * 255.0D);
        int blue = (int) Math.round(glow * hot[2] * 255.0D);
        if (Math.max(red, Math.max(green, blue)) <= 2) {
            return;
        }
        int colour = 0xFF_000000 | red << 16 | green << 8 | blue;
        getRenderer().reRender(model, poseStack, bufferSource, cradle, TYPE, bufferSource.getBuffer(TYPE),
                partialTick, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, colour);
    }
}
