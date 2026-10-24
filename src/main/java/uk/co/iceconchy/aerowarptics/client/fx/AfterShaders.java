package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import uk.co.iceconchy.aerowarptics.AeroWarptics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Draws the mod's light in the world after a shader pack has finished with the frame, instead of inside it.
 *
 * <p>A shader pack replaces the program behind every render type drawn while the world is, and what it puts
 * there is the pack's to decide. Seen in a game under Bliss (2026-10-11): a rift's glow gone and its throat
 * washed pale. Its {@code shaders.properties} turns the beacon beam's additive blend into an ordinary one and
 * its vertex program throws away every beam vertex that is not fully opaque - which is all of the fire, the
 * rim and the cracks - while position-colour geometry is lit by the sun as a particle is, so a dark throat is
 * a sunlit one. {@link AWRenderTypes#RIFT_FIRE} was moved onto the beacon beam's program for Complementary's
 * sake, and Bliss undoes that in two lines. Every pack answers differently, and there is no render type all
 * of them leave alone that can draw both dark and light.
 *
 * <p>So under a pack the mod stops asking. Each effect that draws at the translucent or particle stage hands
 * its event here ({@link #defer}) and draws nothing; once the level has been rendered and the pack has
 * composited it, {@link #replay} runs the same code against the same camera. By then Iris has stopped
 * swapping programs and the main target holds the finished picture - but not the world's depth, which the
 * pack's last pass leaves in no state to test against (the first version drew against it and nothing showed).
 * So the depth is copied aside as the particle stage is announced, when everything solid, everything
 * translucent and the hand are in it, and put back before the effects are drawn: a hull in front of a rift
 * still hides it and a hull behind is still painted over, exactly as with no pack at all. That has to happen before
 * {@code GameRenderer} clears depth for the hand, which is why it hangs off a mixin and not an event: the
 * last stage NeoForge announces is still inside the pack's frame.
 *
 * <p>What is given up: the pack's bloom, fog and clouds do not touch a rift. It looks as it does with no
 * pack, set into the pack's world. A pack that renders the world smaller than the window and scales it up
 * has a depth buffer that does not line up, and there the edges where a hull crosses a rift will be rough.
 *
 * <p>With no pack nothing here does anything, and the effects draw where they always did.
 */
@OnlyIn(Dist.CLIENT)
public final class AfterShaders {

    /** A stage the world announced while a pack was drawing it, kept until the pack is done. */
    private record Held(RenderLevelStageEvent.Stage stage, LevelRenderer renderer, Matrix4f pose, Matrix4f modelView,
                        Matrix4f projection, Matrix4f drawnModelView, Matrix4f drawnProjection, int renderTick,
                        DeltaTracker partialTick, Camera camera, Frustum frustum) {
    }

    private static final List<Held> HELD = new ArrayList<>();
    /** The world's depth as it stood at the particle stage, and whether it is this frame's. */
    private static TextureTarget depth;
    private static boolean depthKept;
    private static MultiBufferSource.BufferSource own;
    private static boolean replaying;
    private static boolean failed;

    private AfterShaders() {
    }

    /**
     * Whether an effect should draw nothing for this event, because a shader pack is drawing the world and the
     * event has been kept for {@link #replay}. Asked first by every effect that draws at the translucent or
     * particle stage; false for an event {@link #replay} itself hands out.
     */
    public static boolean defer(RenderLevelStageEvent event) {
        if (replaying || failed) {
            return false;
        }
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                && stage != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return false;
        }
        if (!GravityLens.shaderPackActive()) {
            return false;
        }
        if (GravityLens.shadowPass()) {
            // A pack draws the world a second time, from the sun, to make its shadows - and announces the same
            // stages as it does. Nothing of the mod's belongs in a shadow map, and the first version here kept
            // that pass's matrices for the real one's, which drew every rift where the sun would have seen it.
            return true;
        }
        // The last word wins, in case a pass this cannot tell apart was announced first.
        HELD.removeIf(held -> held.stage() == stage);
        // Copies: the matrices are the renderer's own and are written over before the frame is out.
        HELD.add(new Held(stage, event.getLevelRenderer(), new Matrix4f(event.getPoseStack().last().pose()),
                new Matrix4f(event.getModelViewMatrix()), new Matrix4f(event.getProjectionMatrix()),
                // What the renderer itself was drawing with at that moment, which is not what the event says:
                // by these stages the level renderer has taken the camera's turn back off the model-view stack
                // and the event's pose carries it instead. Put back the event's and every rift is turned twice,
                // out of sight - which is what the second attempt did.
                new Matrix4f(RenderSystem.getModelViewMatrix()), new Matrix4f(RenderSystem.getProjectionMatrix()),
                event.getRenderTick(), event.getPartialTick(), event.getCamera(), event.getFrustum()));
        if (stage == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            // The later of the two stages, so the copy holds all that either would have been tested against.
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            depth = CloudlessDepth.fit(depth, main);
            CloudlessDepth.blitDepth(main, depth);
            depthKept = true;
        }
        return true;
    }

    /**
     * The buffers an effect draws into: the game's own, or while drawing over a pack's frame a set of this
     * class's. Iris hands out a source of its own in place of the game's for as long as it counts the level as
     * being rendered, one that keeps everything until it chooses to flush - and it was still doing so at the
     * moment {@link #replay} runs, so the first late pass queued every rift and drew none.
     */
    public static MultiBufferSource.BufferSource buffers() {
        if (!replaying) {
            return Minecraft.getInstance().renderBuffers().bufferSource();
        }
        if (own == null) {
            own = MultiBufferSource.immediate(new ByteBufferBuilder(1536));
        }
        return own;
    }

    /** Whether the effects are being drawn now, over a shader pack's finished frame. */
    public static boolean replaying() {
        return replaying;
    }

    /**
     * Draws what was put off, in the order it was announced. Called once the level is rendered and before
     * anything clears its depth - see {@code GameRendererMixin}.
     */
    public static void replay() {
        if (HELD.isEmpty()) {
            return;
        }
        List<Held> stages = List.copyOf(HELD);
        HELD.clear();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        VertexSorting sorting = RenderSystem.getVertexSorting();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        replaying = true;
        try {
            RenderTarget main = minecraft.getMainRenderTarget();
            if (depthKept && depth != null && depth.width == main.width && depth.height == main.height) {
                CloudlessDepth.blitDepth(depth, main);
            }
            depthKept = false;
            main.bindWrite(false);
            for (Held held : stages) {
                modelView.pushMatrix();
                modelView.set(held.drawnModelView());
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(held.drawnProjection(), VertexSorting.DISTANCE_TO_ORIGIN);
                try {
                    PoseStack pose = new PoseStack();
                    pose.last().pose().set(held.pose());
                    RenderLevelStageEvent event = new RenderLevelStageEvent(held.stage(), held.renderer(), pose,
                            held.modelView(), held.projection(), held.renderTick(), held.partialTick(),
                            held.camera(), held.frustum());
                    for (Consumer<RenderLevelStageEvent> effect : EFFECTS) {
                        effect.accept(event);
                    }
                    // Whatever an effect left unfinished, before the matrices it was written for are gone.
                    buffers().endBatch();
                } finally {
                    modelView.popMatrix();
                    RenderSystem.applyModelViewMatrix();
                }
            }
        } catch (RuntimeException | LinkageError broken) {
            // Once, and then never again this session: the effects go back to drawing inside the pack's frame,
            // which looks wrong and is better than a game that will not draw.
            failed = true;
            AeroWarptics.LOGGER.error("Drawing over a shader pack's frame failed; rifts go back to being drawn"
                    + " inside it for the rest of this session", broken);
        } finally {
            replaying = false;
            RenderSystem.setProjectionMatrix(projection, sorting);
        }
    }

    /**
     * Every effect that defers, in the order the event bus would have called them. An effect that asks
     * {@link #defer} and is not listed here is simply never drawn under a pack - {@code AfterShadersTest}
     * reads the source for exactly that.
     */
    private static final List<Consumer<RenderLevelStageEvent>> EFFECTS = List.of(
            RiftEffectManager::onRenderLevel,
            CollapseSpectacle::onRenderLevel,
            WellheadSpectacle::onRenderLevel,
            SundererSpectacle::onRenderLevel,
            PlotterEffects::onRenderLevel,
            WardEffects::onRenderLevel,
            BerthGuides::onRenderLevel,
            RiftShimmer::onRenderLevel,
            HyperspaceStreaks::onRenderLevel,
            FlotillaEffects::onRenderLevel,
            CradleLinks::onRenderLevel,
            SummonBeacons::onRenderLevel,
            AstrolabeProjections::onRenderLevel,
            ClearanceOverlay::onRenderLevel);
}
