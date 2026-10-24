package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import uk.co.iceconchy.aerowarptics.plotter.PlotGeometry;

/**
 * Light drawn as geometry, for effects that have to read by day as well as by night: strokes turned to the viewer,
 * rings, discs, bands and bolts, each written into one of four layers.
 *
 * <p>Why not lines. A GL line is a pixel or two wide at any distance, however wide it was asked to be on a driver that
 * ignores the width, and additive light over a noon sky is white laid on white - which is how the Rift Plotter's first
 * dock guide vanished in daylight. A stroke here is a quad whose width grows with its distance, so it stays a few
 * pixels across wherever it is seen from, and the {@link Layer#BODY body} is ordinary alpha blending, which can darken
 * as well as lighten; a dark {@link Layer#SHADE shade} under it stands it off a bright sky.
 *
 * <p>Camera-relative throughout: a world coordinate cast to a float is a wobble at the edge of a big world.
 */
@OnlyIn(Dist.CLIENT)
final class LightStrokes {

    /** Blocks one pixel spans one block away, near enough, at the default field of view on a 1080p screen. */
    private static final double PIXEL = 1.0D / 770.0D;

    /** The glow pass's light level: full, so it is never shaded by the world round it. */
    private static final int GLOW_LIGHT = 240;

    /** The four passes a guide or a spectacle is drawn in, in this order. */
    enum Layer {
        /** Faint, through everything, so a guide behind a hill or under water can be found. */
        THROUGH(AWRenderTypes.HOLOGRAM_THROUGH),
        /** Dark edging and tinted walls, under the body, so light strokes stand off a bright sky. */
        SHADE(AWRenderTypes.HOLOGRAM),
        /** The strokes themselves, alpha-blended, so they read by day. */
        BODY(AWRenderTypes.HOLOGRAM),
        /** Additive glow over the top: bloom at night, and the spectacle's fire. */
        GLOW(AWRenderTypes.RIFT_FIRE);

        final RenderType type;

        Layer(RenderType type) {
            this.type = type;
        }
    }

    /** One layer's buffer, open, and where the camera is. */
    static final class Pen {
        final Layer layer;
        final Vec3 eye;
        private final VertexConsumer consumer;
        private final Matrix4f matrix;

        private Pen(Layer layer, VertexConsumer consumer, Matrix4f matrix, Vec3 eye) {
            this.layer = layer;
            this.consumer = consumer;
            this.matrix = matrix;
            this.eye = eye;
        }
    }

    private LightStrokes() {
    }

    /** Opens a layer. Each is a batch of its own - see {@link #close}. */
    static Pen open(MultiBufferSource.BufferSource buffers, Layer layer, Matrix4f matrix, Vec3 eye) {
        return new Pen(layer, buffers.getBuffer(layer.type), matrix, eye);
    }

    /**
     * Draws a layer. The shade and the body share a render type, and a translucent batch is sorted far to near
     * before it is drawn, so were they one batch an edging and the stroke it edges - the same distance off - would
     * swap places from one frame to the next. Ending each layer on its own keeps the shade under the body.
     */
    static void close(MultiBufferSource.BufferSource buffers, Pen pen) {
        buffers.endBatch(pen.layer.type);
    }

    // ------------------------------------------------------------------ colour

    /** A colour at an opacity. */
    static int argb(int rgb, double alpha) {
        return (Mth.clamp((int) Math.round(alpha * 255.0D), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** Two colours mixed, {@code t} of the way from the first to the second. */
    static int mix(int from, int to, double t) {
        float f = (float) Mth.clamp(t, 0.0D, 1.0D);
        int red = Math.round(Mth.lerp(f, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
        int green = Math.round(Mth.lerp(f, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
        int blue = Math.round(Mth.lerp(f, from & 0xFF, to & 0xFF));
        return red << 16 | green << 8 | blue;
    }

    // ------------------------------------------------------------------ shapes

    /** A width that keeps a stroke at a point about this many pixels across, however far off it is. */
    static double width(Pen pen, Vec3 at, double pixels) {
        return Math.max(0.03D, at.distanceTo(pen.eye) * pixels * PIXEL);
    }

    /** A straight stroke between two points, turned to the viewer, about this many pixels wide. */
    static void stroke(Pen pen, Vec3 a, Vec3 b, double pixels, int argb) {
        stroke(pen, a, b, width(pen, a, pixels), width(pen, b, pixels), argb, argb);
    }

    /** A straight stroke of light between two points, turned to the viewer, its width and colour running along it. */
    static void stroke(Pen pen, Vec3 a, Vec3 b, double widthA, double widthB, int argbA, int argbB) {
        if ((argbA >>> 24) == 0 && (argbB >>> 24) == 0) {
            return;
        }
        Vec3 along = b.subtract(a);
        Vec3 side = along.cross(pen.eye.subtract(a.add(b).scale(0.5D)));
        if (side.lengthSqr() < 1.0e-12D) {
            return;
        }
        side = side.normalize();
        Vec3 sa = side.scale(widthA * 0.5D);
        Vec3 sb = side.scale(widthB * 0.5D);
        quad(pen, a.subtract(sa), a.add(sa), b.add(sb), b.subtract(sb), argbA, argbA, argbB, argbB);
    }

    /** A ring in a frame's plane, drawn as strokes turned to the viewer so it reads edge-on as well as face-on. */
    static void ring(Pen pen, PlotGeometry.Frame frame, double radius, double pixels, int argb) {
        arc(pen, frame, radius, 0.0D, Math.PI * 2.0D, pixels, argb);
    }

    /** Part of a ring, from one angle round to another. */
    static void arc(Pen pen, PlotGeometry.Frame frame, double radius, double from, double to, double pixels, int argb) {
        if ((argb >>> 24) == 0 || radius <= 0.0D) {
            return;
        }
        int segments = Mth.clamp((int) Math.ceil(Math.abs(to - from) / (Math.PI * 2.0D) * 64.0D), 3, 64);
        Vec3 last = null;
        for (int i = 0; i <= segments; i++) {
            double angle = from + (to - from) * i / segments;
            Vec3 point = frame.world(Math.cos(angle) * radius, Math.sin(angle) * radius, 0.0D);
            if (last != null) {
                stroke(pen, last, point, pixels, argb);
            }
            last = point;
        }
    }

    /** A flat band in a frame's plane, from one radius to another, its colour running from the inside out. */
    static void band(Pen pen, PlotGeometry.Frame frame, double inner, double outer, int argbInner, int argbOuter) {
        if (((argbInner | argbOuter) >>> 24) == 0 || outer <= inner) {
            return;
        }
        int segments = 64;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments;
            double a1 = Math.PI * 2.0D * (i + 1) / segments;
            quad(pen, frame.world(Math.cos(a0) * inner, Math.sin(a0) * inner, 0.0D),
                    frame.world(Math.cos(a1) * inner, Math.sin(a1) * inner, 0.0D),
                    frame.world(Math.cos(a1) * outer, Math.sin(a1) * outer, 0.0D),
                    frame.world(Math.cos(a0) * outer, Math.sin(a0) * outer, 0.0D), argbInner, argbInner, argbOuter, argbOuter);
        }
    }

    /** A cylinder's wall along a frame's axis, from one depth through it to another. */
    static void tube(Pen pen, PlotGeometry.Frame frame, double radius, double from, double to, int argbFrom, int argbTo) {
        if (((argbFrom | argbTo) >>> 24) == 0) {
            return;
        }
        int segments = 48;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments;
            double a1 = Math.PI * 2.0D * (i + 1) / segments;
            double x0 = Math.cos(a0) * radius;
            double y0 = Math.sin(a0) * radius;
            double x1 = Math.cos(a1) * radius;
            double y1 = Math.sin(a1) * radius;
            quad(pen, frame.world(x0, y0, from), frame.world(x1, y1, from), frame.world(x1, y1, to), frame.world(x0, y0, to),
                    argbFrom, argbFrom, argbTo, argbTo);
        }
    }

    /** A disc of light turned to the viewer: bright at the middle, fading to its edge - a glow seen the same from anywhere. */
    static void disc(Pen pen, Vec3 centre, double inner, double outer, int argbInner, int argbOuter) {
        if (((argbInner | argbOuter) >>> 24) == 0 || outer <= 0.0D) {
            return;
        }
        Vec3 toward = pen.eye.subtract(centre);
        if (toward.lengthSqr() < 1.0e-9D) {
            return;
        }
        Vec3 up = Math.abs(toward.normalize().y) > 0.99D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = toward.cross(up).normalize();
        up = right.cross(toward).normalize();
        PlotGeometry.Frame facing = new PlotGeometry.Frame(centre, toward.normalize(), up, right);
        band(pen, facing, inner, outer, argbInner, argbOuter);
    }

    /** A jagged bolt between two points, pinned at both ends, as strokes turned to the viewer. */
    static void bolt(Pen pen, Vec3 from, Vec3 to, int seed, double jag, double pixels, int argb) {
        Vec3 along = to.subtract(from);
        Vec3 side = along.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0e-9D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        }
        side = side.normalize();
        Vec3 lift = along.cross(side).normalize();
        int points = 10;
        Vec3 last = from;
        for (int point = 1; point <= points; point++) {
            double t = point / (double) points;
            double pinned = Math.sin(Math.PI * t);
            double a = (RiftShatter.noise(seed, point * 2) - 0.5D) * 2.0D * jag * pinned;
            double b = (RiftShatter.noise(seed, point * 2 + 1) - 0.5D) * 2.0D * jag * pinned;
            Vec3 next = from.add(along.scale(t)).add(side.scale(a)).add(lift.scale(b));
            stroke(pen, last, next, pixels, argb);
            last = next;
        }
    }

    /** Four corners, in order round the quad, each its own colour. */
    static void quad(Pen pen, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int argbA, int argbB, int argbC, int argbD) {
        if (pen.layer == Layer.GLOW) {
            glowVertex(pen, a, argbA);
            glowVertex(pen, b, argbB);
            glowVertex(pen, c, argbC);
            glowVertex(pen, d, argbD);
        } else {
            flatVertex(pen, a, argbA);
            flatVertex(pen, b, argbB);
            flatVertex(pen, c, argbC);
            flatVertex(pen, d, argbD);
        }
    }

    // ------------------------------------------------------------------ the two writers

    /** The one place a hologram vertex is written: position and colour, the hologram's format. */
    private static void flatVertex(Pen pen, Vec3 at, int argb) {
        pen.consumer.addVertex(pen.matrix, (float) (at.x - pen.eye.x), (float) (at.y - pen.eye.y), (float) (at.z - pen.eye.z))
                .setColor(argb);
    }

    /**
     * The one place a glowing vertex is written. {@code RIFT_FIRE} is the beacon beam's format, so a texture
     * coordinate, a light level and a normal go with every colour - see {@code RiftVertexFormatTest}.
     */
    private static void glowVertex(Pen pen, Vec3 at, int argb) {
        pen.consumer.addVertex(pen.matrix, (float) (at.x - pen.eye.x), (float) (at.y - pen.eye.y), (float) (at.z - pen.eye.z))
                .setColor(argb)
                .setUv(0.5F, 0.5F)
                .setUv2(GLOW_LIGHT, GLOW_LIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }
}
