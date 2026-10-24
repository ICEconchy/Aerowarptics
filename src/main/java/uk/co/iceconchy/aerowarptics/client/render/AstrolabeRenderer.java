package uk.co.iceconchy.aerowarptics.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import uk.co.iceconchy.aerowarptics.airship.DrawnAboard;
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

        // The model is the one drawn for this size (AstrolabeModel), centred on its own footprint. The cell
        // holding the state is the table's corner, so the footprint's middle is (size - 1) / 2 blocks along
        // each way from it. Moved before the renderer's own translate to the block's middle.
        int size = Math.max(1, table.size());
        float offset = (size - 1) * 0.5F;
        poseStack.pushPose();
        poseStack.translate(offset, 0.0D, offset);
        super.render(table, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }

    /**
     * The projection reaches well past the block, and is only drawn while this renderer keeps
     * reporting the table in. Both need the table to still count as visible from off to one side.
     *
     * <p>Only in the world. Aboard a ship Sable draws, and so reports, every table on the hull every frame,
     * and the off-screen list is the one a warp can quietly empty - see {@link DrawnAboard}.
     */
    @Override
    public boolean shouldRenderOffScreen(AstrolabeBlockEntity table) {
        return DrawnAboard.offScreen(table);
    }

    /**
     * The table's own box, which covers the whole footprint. NeoForge culls a block entity renderer by
     * asking the renderer, and without this it asked about the origin cell alone - so a three-by-three
     * table disappeared whenever its corner was off the edge of the screen. See
     * {@link RiftDriveRenderer#getRenderBoundingBox}.
     */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(AstrolabeBlockEntity table) {
        return table.getRenderBoundingBox();
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
