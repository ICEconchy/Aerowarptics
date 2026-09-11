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
import uk.co.iceconchy.aerowarptics.astrolabe.AstrolabeStructure;
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

    /**
     * How much the instrument tower is shrunk this frame, {@code size / MAX_SIZE}.
     *
     * <p>Held between {@link #render} working it out and {@link #renderRecursively} applying it to the
     * tower bone. It cannot be applied in {@code render}: GeckoLib resets every bone to its authored
     * scale while playing the animation, which happens inside the {@code super.render} call, so a scale
     * set before that is wiped before the bones are ever drawn.
     */
    private float towerScale = 1.0F;

    public AstrolabeRenderer() {
        super(new AstrolabeModel());
    }

    @Override
    public void render(AstrolabeBlockEntity table, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // Every cell but one draws nothing at all: the model they are part of belongs to the
        // table's origin and covers them.
        if (!table.isMaster()) {
            return;
        }
        if (table.getLevel() != null) {
            AstrolabeProjections.seen(table, table.getLevel().getGameTime());
        }

        // One model, sized to the footprint it was built on. The geometry is authored for a full
        // three-by-three, and a smaller table has to read as the same instrument shrunk, not stretched.
        // Two things are scaled differently to get there:
        //
        //   - The flat table body (base, deck, chart, the spinning corner instruments) is scaled in
        //     width and depth only, never height. These are low slabs, so a height held constant is
        //     invisible on them, and it keeps the tabletop at a consistent, leanable height and its
        //     footprint filling the block's waist-high collision box whatever the size.
        //   - The instrument tower on top - pillar, ring, gimbal, lens - is scaled uniformly instead,
        //     down in height as much as in width, so it never becomes the thin spire that a
        //     width-only squeeze made of it. That is done to the "tower" bone in renderRecursively;
        //     here we just work out by how much. It shrinks towards its foot on the tabletop, so it
        //     stays seated whatever size it is drawn at.
        int size = Math.max(1, table.size());
        float scale = size / (float) AstrolabeStructure.MAX_SIZE;
        float offset = (size - 1) * 0.5F;

        towerScale = scale;
        poseStack.pushPose();
        // Before the renderer's own translate to the block's middle, so the model ends up centred on
        // the table rather than on the corner cell that happens to hold its state.
        poseStack.translate(offset, 0.0D, offset);
        scaleWidth = scale;
        scaleHeight = 1.0F;
        super.render(table, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }

    @Override
    public void renderRecursively(PoseStack poseStack, AstrolabeBlockEntity animatable, GeoBone bone,
                                  RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                                  boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                                  int colour) {
        // The tower carries the model's width scale from the pose already; giving its own bone a
        // matching height scale is what turns that into a uniform shrink rather than a squeeze. Done
        // here rather than in render because the animation pass ahead of this resets the bone first.
        if ("tower".equals(bone.getName())) {
            bone.setScaleY(towerScale);
        }

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
