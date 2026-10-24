package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.client.fx.RiftEffectManager.ActiveRift;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Space bending round a Mostly Dangerous rift, drawn for real.
 *
 * <p>Everything else a rift draws is geometry laid over the world. This is the one thing that moves the
 * world itself: it copies the frame, then redraws a disc round the rift out of that copy, each point of
 * the disc showing what is a little nearer the middle than it is - so the sky, the hills and the clouds
 * behind the rift are seen pulled out and round it for a moment, the way light is bent passing something
 * massive. Where each point looks is {@link LensProfile}'s business, worked out here on the CPU for each
 * corner of the disc and handed to the shader as a texture coordinate, so the shader does nothing but
 * read the copy and the maths stays where a test can reach it.
 *
 * <h2>Why the copy is taken when it is</h2>
 * After every rift's face and throat are in the frame, and never before - which is not the obvious
 * order. The obvious one takes the copy first, so the lens bends only the world behind a rift and never
 * the rift. But the face and the throat are what hide a hull going through, and a frame copied before
 * they are drawn still has that hull in it: the lens would pick it up from behind the face and draw it,
 * bent, round the outside of the rift, which is the one thing nothing in this mod may do. Taken after,
 * the copy holds nothing the rift is hiding, and the lens can only move about what was already on
 * screen - bending the rift's own black edge a little along with everything else, which the cloud drawn
 * over it afterwards covers anyway.
 *
 * <p>The disc is depth-tested and writes no depth, so anything standing in front of the rift - a hill,
 * a hull coming out - stays in front of the bending, and everything drawn after it draws as if it were
 * not there.
 *
 * <h2>When it does not</h2>
 * Whenever no single copy of the frame holds both the world and the faces hiding what is in it. A shader
 * pack draws the world into buffers of its own; Fabulous graphics splits the frame into layers and draws
 * a rift's face into a different one from the world behind it, so a copy of the world's layer still has
 * the hull in it that the face is hiding. Under either, as with the {@code riftLensing} setting off, this
 * draws nothing and the rift draws a ring of bent light instead (see {@code RiftFurniture}). And it only
 * draws while the lens is actually bending: the copy is a cost, and a lens held open is a smudge on the
 * screen.
 */
@OnlyIn(Dist.CLIENT)
public final class GravityLens {

    /** Rings from the middle of the disc to its edge, and segments round it. Fine enough to bend smoothly. */
    private static final int RINGS = 18;
    private static final int SEGMENTS = 48;

    /** How far out the disc starts to fade into the frame around it, as a fraction of it. */
    private static final float FEATHER = 0.82F;

    /**
     * How much of the distance to the rift the disc may reach across.
     *
     * <p>Every corner of the disc has to be in front of the camera, or the place it looks is a
     * projection from behind the eye and means nothing. Up close the disc is drawn smaller rather than
     * not at all, and bends that much less.
     */
    private static final double ROOM = 0.85D;

    private static ShaderInstance shader;
    private static TextureTarget copy;

    private static boolean irisChecked;
    private static Object iris;
    private static Method irisInUse;
    private static Method irisShadowPass;

    /** Render-thread scratch for {@link #project}. */
    private static final Vector4f CLIP = new Vector4f();

    private GravityLens() {
    }

    /** Handed the shader once it has loaded - see {@code AWClientSetup.registerShaders}. */
    public static void setShader(ShaderInstance loaded) {
        shader = loaded;
    }

    /** The shader the lens draws with, or {@code null} if it never loaded. */
    static ShaderInstance shader() {
        return shader;
    }

    /** Points the lens's sampler at the copy. The texture state of {@link AWRenderTypes#GRAVITY_LENS}. */
    static void bindCopy() {
        if (copy != null) {
            RenderSystem.setShaderTexture(0, copy.getColorTextureId());
        }
    }

    /**
     * Whether the view can really be bent this frame: the shader loaded, the setting is on, the frame is
     * in one piece rather than Fabulous graphics' layers, and no shader pack is drawing the world
     * somewhere this cannot see.
     */
    static boolean available() {
        return shader != null && AWConfig.RIFT_LENSING.get() && !Minecraft.useShaderTransparency()
                && !shaderPackInUse();
    }

    /**
     * Whether the lens will bend the view, and if not, which of {@link #available}'s conditions says no -
     * for the client trace's header, since a lens that stood down looks the same as a lens that broke.
     */
    public static String status() {
        if (shader == null) {
            return "OFF - shader failed to load (see earlier errors)";
        }
        if (!AWConfig.RIFT_LENSING.get()) {
            return "off - riftLensing is off";
        }
        if (Minecraft.useShaderTransparency()) {
            return "standing down - Fabulous graphics";
        }
        return shaderPackInUse() ? "standing down - a shader pack is in use" : "on";
    }

    /** Whether a shader pack is drawing the world, for the client trace's header. */
    public static boolean shaderPackActive() {
        return shaderPackInUse();
    }

    /** How hard a rift is bending the view right now, 0 to 1. Nothing for any rift but a cloud. */
    static float strength(ActiveRift rift, float partialTick) {
        if (rift.surface != ThemeLook.Surface.NEBULA) {
            return 0.0F;
        }
        float time = Mth.lerp(partialTick, rift.lastAge, rift.age);
        return LensProfile.strength(time, rift.openTicks, rift.transitFrom, rift.openTicks + rift.holdTicks);
    }

    /**
     * Bends the view round every rift that is bending it, if any is.
     *
     * <p>Called at the end of the membrane pass, once every face and throat is in the frame - see the
     * class notes for why the order is the whole of what makes this safe.
     */
    static void draw(List<ActiveRift> rifts, MultiBufferSource.BufferSource buffers, Camera camera,
                     float partialTick) {
        boolean bending = false;
        for (ActiveRift rift : rifts) {
            if (strength(rift, partialTick) > 0.001F) {
                bending = true;
                break;
            }
        }
        if (!bending || !available()) {
            return;
        }
        capture();
        Vec3 eye = camera.getPosition();
        Vector3f look = camera.getLookVector();
        // What the shader will do to each corner, done here as well: the same two matrices it is handed,
        // so a corner that looks straight at itself lands exactly on the pixel it is drawn over.
        Matrix4f toScreen = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix());
        VertexConsumer consumer = buffers.getBuffer(AWRenderTypes.GRAVITY_LENS);
        for (ActiveRift rift : rifts) {
            float strength = strength(rift, partialTick);
            if (strength > 0.001F) {
                disc(consumer, toScreen, rift.centre, rift.radius, eye, look, strength);
            }
        }
        buffers.endBatch(AWRenderTypes.GRAVITY_LENS);
    }

    /**
     * Something other than a rift bending the view: a Collapse Cradle's hole.
     *
     * @param centre   the middle of the bending
     * @param radius   the size it is scaled to, as a rift's radius would be - it bends hardest {@link Nebula#REACH}
     *                 of these out, and reaches twice that
     * @param strength how hard, 0 to 1
     */
    public record Bend(Vec3 centre, double radius, float strength) {
    }

    /**
     * Bends the view round each of these, from a copy of the frame taken now - so whatever the caller drew
     * before is bent along with the world, and whatever it draws after stands in front of the bending. A
     * collapse draws its hole after this, which is what keeps the hole's edge crisp against the sky it bends.
     */
    public static void bend(List<Bend> bends, MultiBufferSource.BufferSource buffers, Camera camera) {
        if (bends.isEmpty() || !available()) {
            return;
        }
        capture();
        Vec3 eye = camera.getPosition();
        Vector3f look = camera.getLookVector();
        Matrix4f toScreen = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix());
        VertexConsumer consumer = buffers.getBuffer(AWRenderTypes.GRAVITY_LENS);
        for (Bend bend : bends) {
            if (bend.strength() > 0.001F) {
                disc(consumer, toScreen, bend.centre(), bend.radius(), eye, look, bend.strength());
            }
        }
        buffers.endBatch(AWRenderTypes.GRAVITY_LENS);
    }

    /**
     * Copies the frame as it stands into a texture the lens can read.
     *
     * <p>A blit, because the frame cannot be read by the same draw that is writing into it. The copy is
     * kept between frames and remade only when the window changes size.
     */
    private static void capture() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        // Whatever was bound and however the viewport stood, put back exactly: making or resizing the
        // copy binds it and sets the viewport to its own size, and everything drawn after this - ours and
        // the game's - expects the frame it had.
        int drawing = GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int reading = GlStateManager._getInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        if (copy == null) {
            copy = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            copy.setFilterMode(GL11.GL_LINEAR);
        } else if (copy.width != main.width || copy.height != main.height) {
            copy.resize(main.width, main.height, Minecraft.ON_OSX);
            copy.setFilterMode(GL11.GL_LINEAR);
        }
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, copy.width, copy.height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, reading);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawing);
        GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
    }

    /**
     * One lens: a disc facing the camera, centred on a rift or a hole, each corner showing the frame from
     * where {@link LensProfile#sample} says.
     */
    private static void disc(VertexConsumer consumer, Matrix4f toScreen, Vec3 centre, double radius, Vec3 eye,
                             Vector3f look, float strength) {
        double toX = centre.x - eye.x;
        double toY = centre.y - eye.y;
        double toZ = centre.z - eye.z;
        double ahead = toX * look.x + toY * look.y + toZ * look.z;
        double reach = radius * LensProfile.REACH;
        double lens = Math.min(reach, ahead * ROOM);
        if (lens <= radius) {
            return; // too close to the rift, or past it, for a lens worth drawing
        }
        // A disc squeezed smaller by the room in front of the camera bends proportionally less of it, so
        // walking up to a rift does not make the bending suddenly lurch inward.
        float bend = strength * (float) (lens / reach);

        double distance = Math.sqrt(toX * toX + toY * toY + toZ * toZ);
        double fx = toX / distance;
        double fy = toY / distance;
        double fz = toZ / distance;
        // Square to the line of sight, so the bending is round from wherever it is watched.
        double rx;
        double ry;
        double rz;
        if (Math.abs(fy) > 0.99D) {
            rx = 1.0D;
            ry = 0.0D;
            rz = 0.0D;
        } else {
            rx = -fz;
            ry = 0.0D;
            rz = fx;
            double length = Math.sqrt(rx * rx + rz * rz);
            rx /= length;
            rz /= length;
        }
        double ux = ry * fz - rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy - ry * fx;

        for (int ring = 0; ring < RINGS; ring++) {
            float inner = ring / (float) RINGS;
            float outer = (ring + 1) / (float) RINGS;
            float innerLooks = LensProfile.sample(inner, bend);
            float outerLooks = LensProfile.sample(outer, bend);
            for (int segment = 0; segment < SEGMENTS; segment++) {
                double a0 = segment * (Math.PI * 2.0D / SEGMENTS);
                double a1 = (segment + 1) * (Math.PI * 2.0D / SEGMENTS);
                corner(consumer, toScreen, toX, toY, toZ, rx, ry, rz, ux, uy, uz, a0, inner, innerLooks, lens);
                corner(consumer, toScreen, toX, toY, toZ, rx, ry, rz, ux, uy, uz, a1, inner, innerLooks, lens);
                corner(consumer, toScreen, toX, toY, toZ, rx, ry, rz, ux, uy, uz, a1, outer, outerLooks, lens);
                corner(consumer, toScreen, toX, toY, toZ, rx, ry, rz, ux, uy, uz, a0, outer, outerLooks, lens);
            }
        }
    }

    /** One corner of the disc: where it is, and where in the copy it looks. Camera-relative throughout. */
    private static void corner(VertexConsumer consumer, Matrix4f toScreen, double cx, double cy, double cz,
                               double rx, double ry, double rz, double ux, double uy, double uz,
                               double angle, float out, float looks, double lens) {
        double dx = rx * Math.cos(angle) + ux * Math.sin(angle);
        double dy = ry * Math.cos(angle) + uy * Math.sin(angle);
        double dz = rz * Math.cos(angle) + uz * Math.sin(angle);
        double at = out * lens;
        double from = looks * lens;
        project(toScreen, cx + dx * from, cy + dy * from, cz + dz * from);
        lensVertex(consumer, (float) (cx + dx * at), (float) (cy + dy * at), (float) (cz + dz * at),
                CLIP.x, CLIP.y, feather(out));
    }

    /** Where a camera-relative point lands on the screen, as a texture coordinate into the copy, in {@link #CLIP}. */
    private static void project(Matrix4f toScreen, double x, double y, double z) {
        CLIP.set((float) x, (float) y, (float) z, 1.0F);
        toScreen.transform(CLIP);
        float w = CLIP.w;
        CLIP.set((CLIP.x / w + 1.0F) * 0.5F, (CLIP.y / w + 1.0F) * 0.5F, 0.0F, 1.0F);
    }

    /** Solid through the middle and fading to nothing at the edge, so the disc has no rim to see. */
    private static float feather(float out) {
        if (out <= FEATHER) {
            return 1.0F;
        }
        float t = Math.min(1.0F, (out - FEATHER) / (1.0F - FEATHER));
        return 1.0F - t * t * (3.0F - 2.0F * t);
    }

    /**
     * The one place a vertex of the lens is written, in exactly the elements {@link AWRenderTypes#GRAVITY_LENS}
     * asks for - position, texture coordinate and colour. The colour is white: only its alpha says anything.
     */
    private static void lensVertex(VertexConsumer consumer, float x, float y, float z, float u, float v,
                                   float alpha) {
        consumer.addVertex(x, y, z).setUv(u, v).setColor(1.0F, 1.0F, 1.0F, alpha);
    }

    /**
     * Whether a shader pack is drawing the world, asked of Iris - or of Oculus, which answers to the same
     * name - without depending on either. If neither is installed there is no pack, and if the question
     * cannot be put, the answer is taken to be yes: a lens drawn over a pack's world is the one outcome to
     * avoid. {@link CloudlessDepth} asks too, for the same reason.
     */
    /**
     * Whether what is being drawn this moment is a shader pack's shadow map and not the view: the world drawn
     * again from the sun, announcing the same stages with the sun's matrices. False if that cannot be asked.
     */
    static boolean shadowPass() {
        if (!shaderPackInUse() || irisShadowPass == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(irisShadowPass.invoke(iris));
        } catch (ReflectiveOperationException | RuntimeException unanswered) {
            return false;
        }
    }

    static boolean shaderPackInUse() {
        if (!irisChecked) {
            irisChecked = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                iris = api.getMethod("getInstance").invoke(null);
                irisInUse = api.getMethod("isShaderPackInUse");
                irisShadowPass = api.getMethod("isRenderingShadowPass");
            } catch (ReflectiveOperationException | LinkageError absent) {
                iris = null;
                irisInUse = null;
                irisShadowPass = null;
            }
        }
        if (irisInUse == null) {
            return false;
        }
        try {
            return !Boolean.FALSE.equals(irisInUse.invoke(iris));
        } catch (ReflectiveOperationException | RuntimeException unanswered) {
            return true;
        }
    }
}
