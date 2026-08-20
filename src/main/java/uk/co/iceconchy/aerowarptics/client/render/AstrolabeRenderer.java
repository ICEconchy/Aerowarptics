package uk.co.iceconchy.aerowarptics.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import uk.co.iceconchy.aerowarptics.astrolabe.AstrolabeBlockEntity;
import uk.co.iceconchy.aerowarptics.client.fx.AWRenderTypes;
import uk.co.iceconchy.aerowarptics.client.fx.TerrainHologram;
import uk.co.iceconchy.aerowarptics.client.model.AstrolabeModel;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws the Astrolabe Cartography Table, and the map it projects.
 *
 * <p>A block entity renderer is a singleton shared by every table in the world, so the sampled terrain
 * cannot live on it. Each table gets its own cache, keyed weakly so an unloaded table takes its map
 * with it rather than leaking one per chunk visited.
 */
@OnlyIn(Dist.CLIENT)
public class AstrolabeRenderer extends GeoBlockRenderer<AstrolabeBlockEntity> {

    private static final int FULL_BRIGHT = 0xF0_00F0;

    /**
     * How high above the table's own block the projection's base floats, in blocks.
     *
     * <p>The relief is drawn upwards from here, so this is the clearance over the tabletop rather
     * than the middle of the picture: a little above the 13/16 the model stands, and the whole
     * landscape then sits between chest and eye height for somebody leaning on it.
     */
    private static final float PROJECTION_HEIGHT = 1.0F;

    /** Ticks the projection takes to fade up, so forming a table is an event rather than a pop. */
    private static final float FADE_TICKS = 20.0F;

    private final Map<AstrolabeBlockEntity, TerrainHologram> holograms = new WeakHashMap<>();
    private final Map<AstrolabeBlockEntity, Integer> ages = new WeakHashMap<>();

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
        super.render(table, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        renderProjection(table, partialTick, poseStack, bufferSource);
    }

    private void renderProjection(AstrolabeBlockEntity table, float partialTick, PoseStack poseStack,
                                  MultiBufferSource bufferSource) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.level instanceof ClientLevel level)) {
            return;
        }

        TerrainHologram hologram = holograms.computeIfAbsent(table, key -> new TerrainHologram());
        int age = ages.merge(table, 1, Integer::sum);

        Vec3 centre = TerrainHologram.worldCentreOf(level, table.getBlockPos());
        hologram.refresh(level, centre, level.getGameTime());

        float alpha = Math.min(1.0F, age / FADE_TICKS) * 0.8F;
        // A slow sweep, one pass every eight seconds. Fast enough to notice, slow enough to ignore.
        float sweep = ((level.getGameTime() % 160L) + partialTick) / 160.0F;

        poseStack.pushPose();
        poseStack.translate(0.5D, PROJECTION_HEIGHT, 0.5D);
        VertexConsumer buffer = bufferSource.getBuffer(AWRenderTypes.HOLOGRAM);
        hologram.render(poseStack, buffer, sweep, alpha);
        poseStack.popPose();
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

    /** The projection reaches well past the block, so the renderer has to survive frustum culling. */
    @Override
    public boolean shouldRenderOffScreen(AstrolabeBlockEntity table) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
