package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * The depth of the solid world, kept aside so a Rift Storm's rain and aurora can be drawn over the clouds.
 *
 * <h2>Why</h2>
 * Vanilla draws the clouds late - after every block, entity and particle - and writes their depth. The
 * storm's aurora used to be drawn first of all, straight after the sky, so the clouds painted over it the way
 * they paint over the stars; its rain is drawn after the clouds, but tested against their depth, so from a
 * deck at cloud height the rain vanished into every cloud round the ship. Both are the storm, and a player
 * standing in it should see both. Neither may go in front of a hill, a hull or a player, though, and the
 * only record of what is solid is that same depth buffer - with the clouds in it.
 *
 * <h2>How</h2>
 * The depth is copied aside once the solid world is drawn ({@code AFTER_BLOCK_ENTITIES}), before any glass,
 * water, particle or cloud is added to it. At the weather stage, after the clouds, the storm swaps that copy
 * in, draws, and puts the full depth back for whatever is drawn next. So terrain, hulls, entities and
 * machines still stand in front of the storm, and nothing translucent does: rain behind a window shows
 * through it, and the aurora is seen through glass the way the sky behind it is. A rift's face is drawn with
 * the glass and left out with it, so the aurora's faint light can show on a rift standing against the sky -
 * a far smaller fault than an aurora that vanished behind every window on a ship.
 *
 * <p>Two copies the size of the frame, both kept between frames and remade only when the window changes size,
 * and four blits a frame - only while a storm is being drawn, and only with clouds switched on. Not under
 * Fabulous graphics, where the clouds and the weather are separate layers merged by depth afterwards: there
 * the rain writes a depth of its own, as vanilla's does, and the merge sorts it; the aurora stays behind the
 * clouds.
 */
@OnlyIn(Dist.CLIENT)
final class CloudlessDepth {

    /** The world's depth before anything translucent or any cloud was drawn into it. */
    private static TextureTarget solid;
    /** The whole depth, clouds and all, held while the storm draws against {@link #solid}. */
    private static TextureTarget full;
    /** Whether {@link #solid} holds this frame's depth, rather than a stale one or nothing. */
    private static boolean kept;

    private CloudlessDepth() {
    }

    /**
     * Whether there are clouds to get out from under, and one depth buffer to do it in: not Fabulous graphics'
     * layers, and not a shader pack's targets, which are not the frame this copies.
     */
    static boolean available() {
        return !Minecraft.useShaderTransparency()
                && Minecraft.getInstance().options.getCloudsType() != CloudStatus.OFF
                && !GravityLens.shaderPackInUse();
    }

    /** Copies the solid world's depth aside. Called once the last solid thing is drawn. */
    static void keep() {
        kept = false;
        if (!available()) {
            return;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        solid = fit(solid, main);
        blitDepth(main, solid);
        kept = true;
    }

    /**
     * Runs {@code draw} against the solid world's depth, if it was kept this frame, and puts the full depth
     * back afterwards. Without a kept copy it simply draws against whatever depth there is.
     */
    static void drawOver(Runnable draw) {
        if (!kept || !available()) {
            draw.run();
            return;
        }
        kept = false;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        full = fit(full, main);
        blitDepth(main, full);
        blitDepth(solid, main);
        try {
            draw.run();
        } finally {
            blitDepth(full, main);
        }
    }

    /** Marks this frame's copy spent, drawn over or not, so a later frame never draws against it. */
    static void release() {
        kept = false;
    }

    /**
     * A target the main one's size, with a depth buffer the same shape as its - a depth blit between two
     * framebuffers is refused unless their formats match, and NeoForge gives the main one a stencil when any
     * mod asks.
     */
    static TextureTarget fit(TextureTarget target, RenderTarget main) {
        // Making or resizing a target binds it and sets the viewport to it; put back whatever stood.
        int drawing = GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int reading = GlStateManager._getInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        if (target == null) {
            target = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        }
        if (main.isStencilEnabled() && !target.isStencilEnabled()) {
            target.enableStencil();
        }
        if (target.width != main.width || target.height != main.height) {
            target.resize(main.width, main.height, Minecraft.ON_OSX);
        }
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, reading);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawing);
        GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        return target;
    }

    /** Copies one target's depth into another's, leaving the framebuffer bindings as they were. */
    static void blitDepth(RenderTarget from, RenderTarget to) {
        int drawing = GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int reading = GlStateManager._getInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, from.width, from.height, 0, 0, to.width, to.height,
                GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, reading);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawing);
    }
}
