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
import uk.co.iceconchy.aerowarptics.client.model.RiftPlotterModel;
import uk.co.iceconchy.aerowarptics.plotter.PlotterWorks;
import uk.co.iceconchy.aerowarptics.plotter.RiftPlotterBlockEntity;

import java.util.function.Consumer;

/**
 * Draws the Rift Plotter - the whole three-block machine, from its master.
 *
 * <p>Posed, never animated, from {@link PlotterWorks}: the turntable turns the dish to the entry's yaw and the
 * trunnions tip it to its pitch, at a machine's pace, so an entry changed at the table is watched being faced; the
 * pinion turns with the shaft; as the charge climbs the rings in the yoke spin up about the beam and the ball at
 * their focus tumbles. The model is the artist's, built facing +z, which is Minecraft's yaw of nought; the pitch is
 * the game's own, the yaw is turned the other way round ({@link PlotterWorks#turntableYaw}) - its forward kinematics
 * say why. The rings and the ball turn about their own pivots, which lie on the beam - see {@code PlotterRigTest}.
 *
 * <p>The light in its channels is {@link PlotterGlowLayer}. The rifts, the beam and the dock's guide are not drawn
 * here - see {@code PlotterEffects}.
 */
@OnlyIn(Dist.CLIENT)
public class RiftPlotterRenderer extends GeoBlockRenderer<RiftPlotterBlockEntity> {

    /** The shaft's angle is kept to ten turns of the game clock, as the cradle keeps its own. */
    private static final long SHAFT_CYCLE = 12_000L;

    public RiftPlotterRenderer() {
        super(new RiftPlotterModel());
        addRenderLayer(new PlotterGlowLayer(this));
    }

    @Override
    public boolean shouldRenderOffScreen(RiftPlotterBlockEntity plotter) {
        return DrawnAboard.offScreen(plotter);
    }

    /** The block entity's box: the whole machine - see {@code RenderBoundsTest}. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(RiftPlotterBlockEntity plotter) {
        return plotter.getRenderBoundingBox();
    }

    @Override
    public void preRender(PoseStack poseStack, RiftPlotterBlockEntity plotter, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int colour) {
        pose(model, plotter, partialTick);
        super.preRender(poseStack, plotter, model, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
    }

    private static void pose(BakedGeoModel model, RiftPlotterBlockEntity plotter, float partialTick) {
        PlotterWorks.Aim aim = plotter.aim();
        double spin = plotter.spin().angle(partialTick);
        bone(model, "turntable", bone -> bone.setRotY((float) PlotterWorks.turntableYaw(aim.yaw(partialTick))));
        bone(model, "elevation", bone -> bone.setRotX((float) aim.pitch(partialTick)));
        bone(model, "pinion", bone -> bone.setRotY((float) (shaftAngle(plotter, partialTick) * PlotterWorks.PINION_RATIO)));
        // The rings about the beam - the inner one rides in the middle one - and the ball tumbling at their focus, at
        // its own paces on two axes. Each stands on its edge in the yoke, so a turn about its own y is about the beam.
        bone(model, "ring_middle", bone -> bone.setRotY((float) spin));
        bone(model, "ball", bone -> {
            bone.setRotX((float) (spin * PlotterWorks.RING_B_RATE));
            bone.setRotY((float) (-spin * PlotterWorks.RING_C_RATE));
        });
    }

    private static void bone(BakedGeoModel model, String name, Consumer<GeoBone> pose) {
        model.getBone(name).ifPresent(pose);
    }

    /** Radians the shaft has turned, from the plotter's own speed - see the cradle's renderer. */
    static double shaftAngle(RiftPlotterBlockEntity plotter, float partialTick) {
        if (plotter.getLevel() == null) {
            return 0.0D;
        }
        double ticks = Math.floorMod(plotter.getLevel().getGameTime(), SHAFT_CYCLE) + (double) partialTick;
        return ticks * plotter.getSpeed() / 1200.0D * Math.PI * 2.0D;
    }
}
