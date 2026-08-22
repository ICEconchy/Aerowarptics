package uk.co.iceconchy.aerowarptics.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import uk.co.iceconchy.aerowarptics.astrolabe.AstrolabeBlockEntity;
import uk.co.iceconchy.aerowarptics.client.fx.AstrolabeProjections;
import uk.co.iceconchy.aerowarptics.client.model.AstrolabeModel;

/**
 * Draws the Astrolabe Cartography Table.
 *
 * <p>The map it projects is drawn by {@link AstrolabeProjections} rather than from here. A block
 * entity renderer's pose stack has been transformed by the level renderer, by Sable for a table on an
 * airship, and by GeckoLib for the model, so it is the wrong frame to position a world-space object
 * in. All this renderer does about the projection is say the table is still there.
 */
@OnlyIn(Dist.CLIENT)
public class AstrolabeRenderer extends GeoBlockRenderer<AstrolabeBlockEntity> {

    private static final int FULL_BRIGHT = 0xF0_00F0;

    public AstrolabeRenderer() {
        super(new AstrolabeModel());
    }

    @Override
    public void render(AstrolabeBlockEntity table, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // Eight of the nine cells draw nothing at all: the model they are part of belongs to the
        // middle one and covers them.
        if (!table.isMaster()) {
            return;
        }
        if (table.getLevel() != null) {
            AstrolabeProjections.seen(table, table.getLevel().getGameTime());
        }
        super.render(table, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, AstrolabeBlockEntity animatable, GeoBone bone,
                                  RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                                  boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                                  int colour) {
        // The chart face is lit from underneath so it stays readable on a night deck, which is when a
        // pilot most wants to see where they are pointed.
        boolean glowing = "chart".equals(bone.getName()) || "ring".equals(bone.getName());
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender,
                partialTick, glowing ? FULL_BRIGHT : packedLight,
                glowing ? OverlayTexture.NO_OVERLAY : packedOverlay, colour);
    }

    /**
     * The projection reaches well past the block, and is only drawn while this renderer keeps
     * reporting the table in. Both need the table to still count as visible from off to one side.
     */
    @Override
    public boolean shouldRenderOffScreen(AstrolabeBlockEntity table) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
