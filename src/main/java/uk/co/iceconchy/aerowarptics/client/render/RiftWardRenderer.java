package uk.co.iceconchy.aerowarptics.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import uk.co.iceconchy.aerowarptics.airship.DrawnAboard;
import uk.co.iceconchy.aerowarptics.client.fx.WardEffects;
import uk.co.iceconchy.aerowarptics.client.model.RiftWardModel;
import uk.co.iceconchy.aerowarptics.ward.RiftWardBlockEntity;
import uk.co.iceconchy.aerowarptics.ward.WardWorks;

import java.util.function.Consumer;

/**
 * Draws the Rift Ward - the whole three-block machine, from its master.
 *
 * <p>Posed, never animated, from {@link WardWorks}. The totem's three upper pieces sit on one another while the
 * machine is off; switched on they lift apart on the thread of void through them, and while the field stands each
 * turns against its neighbours. When the ward turns something aside they are dragged sideways towards the tear,
 * and ring back when it breaks; a ward that gives way lets them drop shut. The two rings round the totem turn with
 * the field, against each other. The model is the artist's.
 *
 * <p>Where the pieces are dragged, and how hard, is {@link WardEffects}', which is playing the scene the server
 * sent; the light in the carvings and the void is {@link WardGlowLayer}.
 */
@OnlyIn(Dist.CLIENT)
public class RiftWardRenderer extends GeoBlockRenderer<RiftWardBlockEntity> {

    public RiftWardRenderer() {
        super(new RiftWardModel());
        addRenderLayer(new WardGlowLayer(this));
    }

    @Override
    public boolean shouldRenderOffScreen(RiftWardBlockEntity ward) {
        return DrawnAboard.offScreen(ward);
    }

    /** The block entity's box: the whole machine - see {@code RenderBoundsTest}. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(RiftWardBlockEntity ward) {
        return ward.getRenderBoundingBox();
    }

    @Override
    public void preRender(PoseStack poseStack, RiftWardBlockEntity ward, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int colour) {
        pose(model, ward, partialTick);
        super.preRender(poseStack, ward, model, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
    }

    /** The bones of the pieces above the foot, lowest first - {@link WardWorks}' pieces 0, 1 and 2. */
    private static final String[] PIECES = {"totem_2", "totem_3", "totem_4"};

    private static void pose(BakedGeoModel model, RiftWardBlockEntity ward, float partialTick) {
        WardEffects.Pull pull = WardEffects.pull(ward.getBlockPos(), partialTick);
        WardWorks.Motion motion = ward.motion();
        // A ward giving way lets its pieces drop, whatever the machine was doing a moment before.
        double spread = motion.spread(partialTick) * (1.0D - pull.sag());
        double angle = motion.angle(partialTick);
        for (int piece = 0; piece < PIECES.length; piece++) {
            int which = piece;
            bone(model, PIECES[piece], bone -> {
                bone.setPosY((float) WardWorks.lift(which, spread));
                bone.setRotY((float) WardWorks.turn(which, angle));
                bone.setPosX((float) WardWorks.shearX(which, pull.ux(), pull.lean()));
                bone.setPosZ((float) WardWorks.shearZ(which, pull.uz(), pull.lean()));
            });
        }
        bone(model, "ring_outer", bone -> bone.setRotY((float) (angle * WardWorks.OUTER_RING_RATE)));
        bone(model, "ring_middle", bone -> bone.setRotY((float) (angle * WardWorks.MIDDLE_RING_RATE)));
    }

    private static void bone(BakedGeoModel model, String name, Consumer<GeoBone> pose) {
        model.getBone(name).ifPresent(pose);
    }
}
