package uk.co.iceconchy.aerowarptics.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Where a Rift Storm hangs the things it draws at infinity: the aurora, the tears, the vortex and the streaks.
 *
 * <p>Each is drawn at a fixed distance from the camera in the sky's own frame - turned with the view, never
 * moved with the player - which is what puts it at infinity: walk a thousand blocks and it has not come any
 * closer, exactly like the stars. The distance is a share of the way to the far plane, past every block the
 * world draws and still inside the plane, so a depth test lets it through only where the sky shows. Each
 * effect takes a slightly different share, so where two cross the nearer one is always the same one.
 *
 * <p>Pulled out of {@link RiftStormAurora}, which was the only one of them until the storm's sky grew more.
 */
@OnlyIn(Dist.CLIENT)
final class SkyFrame {

    private SkyFrame() {
    }

    /**
     * The camera's rotation alone - the same one vanilla draws the sky dome with - which is what keeps these
     * at infinity rather than at a place. The sky stage has not put it on the model-view stack yet, so it is
     * applied here; by the weather stage the stack carries it, and applying it again would turn them twice.
     */
    static Matrix4f view(RenderLevelStageEvent event) {
        return event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY
                ? new Matrix4f(event.getModelViewMatrix()) : new Matrix4f();
    }

    /** How far out something hangs that takes this share of the distance to the far plane. */
    static float radius(float reach) {
        return Minecraft.getInstance().gameRenderer.getDepthFar() * reach;
    }

    /** Vanilla does not draw the sky at all from inside lava or powder snow; neither does the storm. */
    static boolean hidden(RenderLevelStageEvent event) {
        FogType inside = event.getCamera().getFluidInCamera();
        return inside == FogType.LAVA || inside == FogType.POWDER_SNOW;
    }

    /** A point on the sky at a bearing and an elevation, both in radians, {@code radius} from the eye. */
    static void point(float[] out, float bearing, float elevation, float radius) {
        float flat = Mth.cos(elevation) * radius;
        out[0] = Mth.cos(bearing) * flat;
        out[1] = Mth.sin(elevation) * radius;
        out[2] = Mth.sin(bearing) * flat;
    }

    /** An 8-bit alpha and a colour, packed the way the vertex writers take it. */
    static int argb(int rgb, float alpha) {
        return (Mth.clamp(Math.round(alpha * 255.0F), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }
}
