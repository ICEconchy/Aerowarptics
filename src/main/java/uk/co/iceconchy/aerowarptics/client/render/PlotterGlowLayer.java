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
import uk.co.iceconchy.aerowarptics.plotter.PlotterWorks;
import uk.co.iceconchy.aerowarptics.plotter.RiftPlotterBlockEntity;

/**
 * The light in the Rift Plotter: its glyphs, the channels in its deck, the folded crystals on its pylons, the veins
 * of its armillary and the lens itself - the cradle's {@link CradleGlowLayer}, lit by {@link PlotterWorks#glow} in
 * the colour of the rifts it tears, so a Modulator that dresses the rifts dresses the machine too.
 */
@OnlyIn(Dist.CLIENT)
public final class PlotterGlowLayer extends GeoRenderLayer<RiftPlotterBlockEntity> {

    private static final ResourceLocation GLOW = AeroWarptics.id("textures/block/rift_plotter_glow.png");
    private static final RenderType TYPE = RenderType.eyes(GLOW);
    private static final int FULL_BRIGHT = 0xF0_00F0;

    public PlotterGlowLayer(GeoRenderer<RiftPlotterBlockEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, RiftPlotterBlockEntity plotter, BakedGeoModel model, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                       int packedLight, int packedOverlay) {
        double time = (plotter.getLevel() == null ? 0L : plotter.getLevel().getGameTime()) + partialTick;
        double glow = PlotterWorks.glow(plotter.state(), plotter.charged(), plotter.stateTicks(), time);
        // Something just came out of the far end: the machine flares with it.
        glow = Math.min(1.0D, glow + Math.max(0.0D, 1.0D - plotter.flash() / 10.0D) * 0.6D);
        int tint = plotter.riftColour();
        // The glow sheet is painted white-violet; tinting it by a colour darker than itself would dim a lit machine
        // to nothing, so the tint is lifted halfway to white first.
        int red = (int) Math.round(glow * (127 + ((tint >> 16) & 0xFF) / 2));
        int green = (int) Math.round(glow * (127 + ((tint >> 8) & 0xFF) / 2));
        int blue = (int) Math.round(glow * (127 + (tint & 0xFF) / 2));
        if (Math.max(red, Math.max(green, blue)) <= 2) {
            return;
        }
        int colour = 0xFF_000000 | Math.min(255, red) << 16 | Math.min(255, green) << 8 | Math.min(255, blue);
        getRenderer().reRender(model, poseStack, bufferSource, plotter, TYPE, bufferSource.getBuffer(TYPE),
                partialTick, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, colour);
    }
}
