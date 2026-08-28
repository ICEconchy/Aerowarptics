package uk.co.iceconchy.aerowarptics.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import uk.co.iceconchy.aerowarptics.client.model.GeoBlockItemModel;
import uk.co.iceconchy.aerowarptics.registry.GeoBlockItem;

/**
 * Draws a block item in the inventory, in a hand, on the ground and in an item frame, using the
 * block's own GeckoLib model.
 *
 * <p>One renderer serves every one of them: the model binding asks the item which geometry and skin
 * to use, so a new machine or a new drive tier needs nothing here at all.
 */
@OnlyIn(Dist.CLIENT)
public class GeoBlockItemRenderer extends GeoItemRenderer<GeoBlockItem> {

    public GeoBlockItemRenderer() {
        super(new GeoBlockItemModel());
    }

    /**
     * Stands the model in the middle of the slot rather than on the floor of it, and leaves out the
     * parts of a machine that only exist once it is working.
     *
     * <h2>The half block</h2>
     * GeckoLib's item renderer expects a model built the way an entity is: centred on its own origin,
     * reaching half a block each way. A <em>block</em> model is built the way a block is, standing on
     * its origin with its floor at zero - which is right for the world, where the block renderer puts
     * that origin in the bottom corner of the block.
     *
     * <p>Given a block model, then, GeckoLib's positioning leaves it sitting half a block high, and in
     * a GUI slot that is about five pixels of machine hanging out of the top of the slot and over the
     * one above it. Hence the drop: it is the difference between the two conventions, and nothing more
     * mysterious than that. Applied after {@code super}, because {@code super} is what does the
     * positioning this is correcting, and only when this is not a re-render, for the same reason it
     * skips its own.
     *
     * <p>The horizontal axes need no such thing - a block model is already centred on x and z, which
     * is what {@code ResourceIntegrityTest} pins so this correction cannot quietly become wrong.
     */
    @Override
    public void preRender(PoseStack poseStack, GeoBlockItem item, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int colour) {
        // Safe to do to the shared baked model, because the block's own renderer assigns these bones
        // their visibility every frame rather than only turning them on - so a siphon in the world is
        // never left hidden by an item having been drawn a moment earlier.
        for (String name : item.hiddenBones()) {
            model.getBone(name).ifPresent(bone -> bone.setHidden(true));
        }
        super.preRender(poseStack, item, model, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
        if (!isReRender) {
            poseStack.translate(0.0F, -0.5F, 0.0F);
        }
    }

    /**
     * One animation state per kind of item, rather than per stack.
     *
     * <p>GeckoLib's default keys an item's animation to an id stamped into the stack, which is right
     * for a sword somebody has been swinging and wrong here: these are idle loops on machines sitting
     * in a chest, and keying them per stack would mean a double chest of drives holding fifty-four
     * separate animation states that all look identical. The registry id is stable for the run and
     * distinct per item, so all twelve drives in a stack turn together and cost one.
     */
    @Override
    public long getInstanceId(GeoBlockItem item) {
        return BuiltInRegistries.ITEM.getId(item);
    }
}
