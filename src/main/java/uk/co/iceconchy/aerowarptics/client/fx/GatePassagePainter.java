package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import uk.co.iceconchy.aerowarptics.gate.GatePassageStyle;
import uk.co.iceconchy.aerowarptics.gate.PassageFrame;

import java.util.EnumMap;
import java.util.Map;

/**
 * Draws a gate passage over the whole view, in whichever style it is asked for.
 *
 * <p>Every style but the plain one is one quad on a shader of its own - {@code shaders/core/passage_*},
 * sharing a vertex program - because a picture that replaces everything a player can see is the one thing
 * the mod's usual way of drawing light, geometry hung in the world, cannot do: the world would still be
 * there round it, and the far gate with it. None of them has a texture. Each is arithmetic on where a
 * pixel is, so there is nothing to ship and nothing for a resource pack to leave out.
 *
 * <p>A shader knows nothing about time. It is handed a {@link PassageFrame} - the same few numbers for
 * every style, off the one tested clock - and draws. What it must not do is let the world through while
 * the frame's veil is whole; see {@link GatePassageStyle}.
 *
 * <p>Drawn in the GUI's own pass, after the world and whatever a shader pack made of it, so a pack
 * neither sees it nor changes it.
 */
@OnlyIn(Dist.CLIENT)
public final class GatePassagePainter {

    /** The dark of the plain style's veil, and the light it brightens into. */
    private static final int QUIET_DARK = 0x0B0716;
    private static final int QUIET_LIGHT = 0xECE8F8;
    /** The pane's own light, which the plain style begins in as the others do. */
    private static final int PANE_LIGHT = 0x9FB6FF;

    private static final Map<GatePassageStyle, ShaderInstance> SHADERS = new EnumMap<>(GatePassageStyle.class);

    private GatePassagePainter() {
    }

    /**
     * Handed a style's shader once it has loaded, or {@code null} when it has not - see
     * {@code AWClientSetup.registerShaders}. Every reload of the game's resources hands them over again,
     * and one that fails then must not leave the shader it replaced, which has been closed, in its place.
     */
    public static void setShader(GatePassageStyle style, @Nullable ShaderInstance loaded) {
        if (loaded == null) {
            SHADERS.remove(style);
        } else {
            SHADERS.put(style, loaded);
        }
    }

    /**
     * The style a passage will really be drawn in, asked for this one: itself if it can be, and the plain
     * one if it needs a shader that did not load. A passage is never left undrawn.
     */
    public static GatePassageStyle drawable(GatePassageStyle wanted) {
        return wanted.shader() == null || SHADERS.containsKey(wanted) ? wanted : GatePassageStyle.QUIET;
    }

    /**
     * Draws one frame of a passage over the whole of the GUI.
     *
     * @param style the style to draw it in; anything not {@link #drawable} is drawn plain
     */
    public static void draw(GuiGraphics graphics, GatePassageStyle style, PassageFrame frame) {
        if (frame.veil() <= 0.0F) {
            return;
        }
        ShaderInstance program = SHADERS.get(style);
        if (style.shader() == null || program == null) {
            quiet(graphics, frame);
            return;
        }
        // Whatever the GUI has batched so far goes down first, under this.
        graphics.flush();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        for (Map.Entry<String, float[]> uniform : frame.uniforms(height <= 0 ? 1.0F : width / (float) height).entrySet()) {
            float[] value = uniform.getValue();
            // A dummy for any name this style's shader does not declare, which is most of them for some.
            if (value.length == 2) {
                program.safeGetUniform(uniform.getKey()).set(value[0], value[1]);
            } else {
                program.safeGetUniform(uniform.getKey()).set(value[0]);
            }
        }

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> program);
        Matrix4f pose = graphics.pose().last().pose();
        BufferBuilder quad = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        // The order vanilla's own blit goes round a quad in, so that it faces the same way.
        corner(quad, pose, 0.0F, 0.0F, 0.0F, 1.0F);
        corner(quad, pose, 0.0F, height, 0.0F, 0.0F);
        corner(quad, pose, width, height, 1.0F, 0.0F);
        corner(quad, pose, width, 0.0F, 1.0F, 1.0F);
        BufferUploader.drawWithShader(quad.buildOrThrow());
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /**
     * The one place a corner of the quad is written, in exactly the two elements its format asks for -
     * position and texture. {@code RiftVertexFormatTest} holds the two to each other.
     */
    private static void corner(BufferBuilder quad, Matrix4f pose, float x, float y, float u, float v) {
        quad.addVertex(pose, x, y, 0.0F).setUv(u, v);
    }

    /**
     * The plain passage: the pane's light, then dark, then light, then the far side. No motion at all,
     * which is the point of it.
     */
    private static void quiet(GuiGraphics graphics, PassageFrame frame) {
        int colour = blend(QUIET_DARK, PANE_LIGHT, frame.splash());
        colour = blend(colour, QUIET_LIGHT, frame.flare());
        int alpha = Mth.clamp(Math.round(frame.veil() * 255.0F), 0, 255);
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24 | colour);
    }

    private static int blend(int from, int to, float amount) {
        float t = Mth.clamp(amount, 0.0F, 1.0F);
        int red = Math.round(Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF));
        int green = Math.round(Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF));
        int blue = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
        return red << 16 | green << 8 | blue;
    }
}
