package uk.co.iceconchy.aerowarptics.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.berth.BerthBox;
import uk.co.iceconchy.aerowarptics.client.screen.BerthLayout;
import uk.co.iceconchy.aerowarptics.client.screen.BerthScreen;

/**
 * A Berth's slip, drawn over the world while its panel is open: the box of air a ship is put down in, an arrow
 * for the way a moored ship's bow points, and the lane behind it that a ship flies down to come in.
 *
 * <p>Drawn as the panel's sliders have it and not as the berth is saved, so a slip is set by eye - move a
 * slider and the box moves. These are instructions, the Rift Plotter's dock guide's kind and not a relic's
 * force: so they are drawn with {@link LightStrokes} in all four layers, edged dark and faintly through
 * terrain, to be found at noon and behind a quay. Nothing is drawn with the panel shut; a harbour is not
 * a diagram.
 *
 * <p>The lane is drawn a fixed length ({@link BerthLayout#LANE_SHOWN}). The lane a ship really flies down is
 * as long as that ship's run out, which no berth knows until one comes - the guide says which end ships come
 * in by, and that the way in has to be kept clear.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class BerthGuides {

    /** The slip: the dock guide's "lined up" teal, since both are instructions. */
    private static final int SLIP = 0x38E0C8;
    /** The lane: amber, the dock guide's "holding" - somewhere to keep clear rather than somewhere to be. */
    private static final int LANE = 0xF0A83C;
    /** A slip that is switched off. */
    private static final int OFF = 0x8E86A0;
    private static final int DARK = 0x0A0812;

    private BerthGuides() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // Under a shader pack this is drawn once the pack has finished the frame - see AfterShaders.
        if (AfterShaders.defer(event)) {
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(minecraft.screen instanceof BerthScreen panel)) {
            return;
        }
        BerthBox slip = panel.draftBox();
        BerthBox lane = slip.lane(BerthLayout.LANE_SHOWN, panel.draftSternFirst());
        int colour = panel.draftEnabled() ? SLIP : OFF;
        Matrix4f matrix = event.getPoseStack().last().pose();
        Vec3 eye = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = AfterShaders.buffers();
        for (LightStrokes.Layer layer : LightStrokes.Layer.values()) {
            LightStrokes.Pen pen = LightStrokes.open(buffers, layer, matrix, eye);
            switch (layer) {
                case THROUGH -> {
                    box(pen, slip, 2.0D, LightStrokes.argb(colour, 0.3D));
                    box(pen, lane, 1.5D, LightStrokes.argb(LANE, 0.18D));
                }
                case SHADE -> {
                    box(pen, slip, 5.0D, LightStrokes.argb(DARK, 0.55D));
                    arrow(pen, slip, 6.0D, LightStrokes.argb(DARK, 0.55D));
                }
                case BODY -> {
                    box(pen, slip, 2.5D, LightStrokes.argb(colour, 0.95D));
                    box(pen, lane, 1.5D, LightStrokes.argb(LANE, 0.6D));
                    arrow(pen, slip, 3.5D, LightStrokes.argb(colour, 0.95D));
                    chevrons(pen, lane, slip, panel.draftSternFirst(), LightStrokes.argb(LANE, 0.75D));
                }
                case GLOW -> box(pen, slip, 4.0D, LightStrokes.argb(colour, 0.25D));
            }
            LightStrokes.close(buffers, pen);
        }
    }

    private static Vec3 at(BerthBox box, double across, double up, double along) {
        Vector3d point = box.world(across, up, along);
        return new Vec3(point.x, point.y, point.z);
    }

    /** The twelve edges of a turned box. */
    private static void box(LightStrokes.Pen pen, BerthBox box, double pixels, int argb) {
        if (box.length() <= 0.0D) {
            return;
        }
        double w = box.width() * 0.5D;
        double l = box.length() * 0.5D;
        double h = box.height();
        Vec3[] floor = {at(box, -w, 0, -l), at(box, w, 0, -l), at(box, w, 0, l), at(box, -w, 0, l)};
        Vec3[] roof = {at(box, -w, h, -l), at(box, w, h, -l), at(box, w, h, l), at(box, -w, h, l)};
        for (int corner = 0; corner < 4; corner++) {
            int next = (corner + 1) % 4;
            LightStrokes.stroke(pen, floor[corner], floor[next], pixels, argb);
            LightStrokes.stroke(pen, roof[corner], roof[next], pixels, argb);
            LightStrokes.stroke(pen, floor[corner], roof[corner], pixels, argb);
        }
    }

    /** An arrow along the slip's floor, pointing the way a moored ship's bow points. */
    private static void arrow(LightStrokes.Pen pen, BerthBox box, double pixels, int argb) {
        double reach = box.length() * 0.35D;
        double barb = Math.min(box.width() * 0.3D, reach * 0.5D);
        double lift = 0.15D;
        Vec3 tail = at(box, 0.0D, lift, -reach);
        Vec3 head = at(box, 0.0D, lift, reach);
        LightStrokes.stroke(pen, tail, head, pixels, argb);
        LightStrokes.stroke(pen, head, at(box, -barb, lift, reach - barb), pixels, argb);
        LightStrokes.stroke(pen, head, at(box, barb, lift, reach - barb), pixels, argb);
    }

    /** Chevrons down the lane's floor, pointing into the slip: the way a ship travels as it comes in. */
    private static void chevrons(LightStrokes.Pen pen, BerthBox lane, BerthBox slip, boolean sternFirst, int argb) {
        if (lane.length() <= 0.0D) {
            return;
        }
        // In the lane's own terms "along" is the slip's facing; a ship backing in travels against it.
        double way = sternFirst ? -1.0D : 1.0D;
        double barb = Math.min(lane.width() * 0.25D, 2.5D);
        int count = Math.max(1, (int) (lane.length() / 6.0D));
        for (int index = 0; index < count; index++) {
            double along = -lane.length() * 0.5D + (index + 0.5D) * lane.length() / count;
            Vec3 point = at(lane, 0.0D, 0.15D, along + way * barb * 0.5D);
            LightStrokes.stroke(pen, point, at(lane, -barb, 0.15D, along - way * barb * 0.5D), 2.0D, argb);
            LightStrokes.stroke(pen, point, at(lane, barb, 0.15D, along - way * barb * 0.5D), 2.0D, argb);
        }
    }
}
