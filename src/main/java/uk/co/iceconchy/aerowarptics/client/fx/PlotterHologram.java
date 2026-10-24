package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import uk.co.iceconchy.aerowarptics.client.screen.HologramMesh;
import uk.co.iceconchy.aerowarptics.plotter.Plot;
import uk.co.iceconchy.aerowarptics.plotter.PlotGeometry;
import uk.co.iceconchy.aerowarptics.plotter.PlotterSurvey;

/**
 * A plotter's survey, built into a model of the ground that can be drawn anywhere: on the plotting table, turned in
 * the player's hands. One vertex buffer, built once per survey from
 * {@link HologramMesh}, and drawn through whatever view and projection the caller is standing in.
 *
 * <p>The marks drawn over it - the target, the exit and the way it faces, the ring of ground the exit can be put on
 * - are drawn fresh each frame by {@link #marks}, because they move as the player drags them.
 *
 * <p>Coordinates are the mesh's: blocks from the survey's middle column, heights from the top of the ground there.
 * {@link #toModel} carries a world point into them, and every mark goes through it, so the target and the exit land
 * on the model where the server will put them in the world.
 */
@OnlyIn(Dist.CLIENT)
public final class PlotterHologram implements AutoCloseable {

    private static final int RING_SEGMENTS = 48;

    private final PlotterSurvey survey;
    private final int groundY;
    private final int[] tops;
    @Nullable
    private VertexBuffer buffer;
    private final int quads;
    private final float floor;

    private PlotterHologram(PlotterSurvey survey, int groundY, int[] tops, @Nullable VertexBuffer buffer, int quads,
                            float floor) {
        this.survey = survey;
        this.groundY = groundY;
        this.tops = tops;
        this.buffer = buffer;
        this.quads = quads;
        this.floor = floor;
    }

    /** Builds the model. On the render thread: it uploads a vertex buffer. */
    public static PlotterHologram of(PlotterSurvey survey) {
        int size = survey.size();
        int[] span = survey.span();
        int groundY = survey.groundY() == PlotterSurvey.UNKNOWN ? (span[0] + span[1]) / 2 : survey.groundY();
        int[] heights = new int[size * size];
        int[] colours = new int[size * size];
        int[] tops = new int[size * size];
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int index = z * size + x;
                boolean known = survey.known(x, z);
                heights[index] = known ? survey.height(x, z) : HologramMesh.UNKNOWN;
                colours[index] = known ? survey.colour(x, z) : 0;
                tops[index] = known ? survey.height(x, z) - groundY : HologramMesh.UNKNOWN;
            }
        }
        HologramMesh mesh = HologramMesh.build(size, heights, colours, groundY);
        VertexBuffer buffer = null;
        if (mesh.quads() > 0) {
            try (ByteBufferBuilder bytes = new ByteBufferBuilder(mesh.quads() * 4 * DefaultVertexFormat.POSITION_COLOR.getVertexSize())) {
                BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                float[] positions = mesh.positions();
                int[] shades = mesh.colours();
                for (int quad = 0; quad < mesh.quads(); quad++) {
                    int at = quad * HologramMesh.FLOATS_PER_QUAD;
                    for (int corner = 0; corner < 4; corner++) {
                        builder.addVertex(positions[at + corner * 3], positions[at + corner * 3 + 1],
                                positions[at + corner * 3 + 2]).setColor(shades[quad] | 0xFF000000);
                    }
                }
                MeshData data = builder.build();
                if (data != null) {
                    buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                    buffer.bind();
                    buffer.upload(data);
                    VertexBuffer.unbind();
                }
            }
        }
        return new PlotterHologram(survey, groundY, tops, buffer, mesh.quads(), mesh.floor());
    }

    public PlotterSurvey survey() {
        return survey;
    }

    public int size() {
        return survey.size();
    }

    public int radius() {
        return survey.radius();
    }

    public int quads() {
        return quads;
    }

    /** The bottom of the model's skirt, in its own heights. */
    public float floor() {
        return floor;
    }

    /** The survey's ground at its middle, which the model's heights are measured from. */
    public int groundY() {
        return groundY;
    }

    /** A cell's top in the model's heights, or {@link HologramMesh#UNKNOWN}. */
    public int top(int x, int z) {
        int size = survey.size();
        return x < 0 || z < 0 || x >= size || z >= size ? HologramMesh.UNKNOWN : tops[z * size + x];
    }

    /** The world column under a cell. */
    public int worldX(int x) {
        return survey.worldX(x);
    }

    public int worldZ(int z) {
        return survey.worldZ(z);
    }

    /** A cell's surface Y in the world, for a target picked on the model. */
    public int worldY(int x, int z) {
        return survey.height(x, z);
    }

    /** A world point in the model's coordinates. */
    public Vec3 toModel(Vec3 world) {
        return new Vec3(world.x - survey.centreX(), world.y - groundY - 1.0D, world.z - survey.centreZ());
    }

    /** The ground under a model point, in the model's heights, or 0 off the edge of what is known. */
    public double groundUnder(double modelX, double modelZ) {
        int top = top((int) Math.floor(modelX) + radius(), (int) Math.floor(modelZ) + radius());
        return top == HologramMesh.UNKNOWN ? 0.0D : top + 0.0D;
    }

    /** The ground's world Y under a world column, from the survey, or {@code fallback} where it is not known. */
    public int groundAt(int worldX, int worldZ, int fallback) {
        int x = worldX - survey.centreX() + radius();
        int z = worldZ - survey.centreZ() + radius();
        return survey.known(x, z) ? survey.height(x, z) : fallback;
    }

    /**
     * Draws the ground through a view and a projection, tinted and faded. Depth is the caller's to set up.
     *
     * @param rise how far the model has risen out of the table, 0..1: its heights are drawn that much of true
     */
    public void draw(Matrix4f view, Matrix4f projection, float red, float green, float blue, float alpha, float rise) {
        if (buffer == null || alpha <= 0.0F) {
            return;
        }
        Matrix4f model = new Matrix4f(view).scale(1.0F, Math.max(0.001F, rise), 1.0F);
        RenderSystem.setShaderColor(red, green, blue, alpha);
        buffer.bind();
        buffer.drawWithShader(model, projection, GameRenderer.getPositionColorShader());
        VertexBuffer.unbind();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * The marks over the ground: the target's beam and ring, the ring of ground the exit can stand over, the exit's
     * disc and the way it faces, and the line from it to the target. Drawn through the matrices already current
     * in {@link RenderSystem}, as lines.
     *
     * @param pulse 0..1, how bright the exit burns - the charge, or the rift standing open
     * @param colour the rift's colour, 0xRRGGBB
     */
    public void marks(Matrix4f pose, Plot plot, int targetY, double pulse, int colour, boolean showRange, double range) {
        Vec3 target = toModel(new Vec3(plot.targetX() + 0.5D, targetY + 1.0D, plot.targetZ() + 0.5D));
        Plot at = plot.withTargetY(targetY);
        PlotGeometry.Frame exit = PlotGeometry.exit(new Vec3(plot.targetX() + 0.5D, targetY + 1.0D, plot.targetZ() + 0.5D), at);
        Vec3 exitCentre = toModel(exit.centre());

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(2.0F);
        BufferBuilder lines = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        // The target: a beam up out of the ground and a ring round its foot.
        int targetColour = 0xFFFF5040;
        line(lines, pose, target, target.add(0.0D, 48.0D, 0.0D), targetColour);
        ring(lines, pose, target.add(0.0D, 0.05D, 0.0D), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 3.0D, targetColour);
        ring(lines, pose, target.add(0.0D, 0.05D, 0.0D), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 1.2D, targetColour);

        if (showRange) {
            ring(lines, pose, target.add(0.0D, 0.1D, 0.0D), new Vec3(1, 0, 0), new Vec3(0, 0, 1), range, 0x60E0C090);
        }

        // Where the exit stands over the ground: a stalk down to it, and a tick on the ground.
        double below = groundUnder(exitCentre.x, exitCentre.z);
        int stalk = 0x90FFFFFF;
        line(lines, pose, exitCentre, new Vec3(exitCentre.x, below, exitCentre.z), stalk);
        ring(lines, pose, new Vec3(exitCentre.x, below + 0.05D, exitCentre.z), new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                1.0D, stalk);

        // The exit itself: its rim, its rim again a little smaller as it burns, the way through it, and the line of fire.
        int rim = 0xFF000000 | colour;
        int bright = 0xFF000000 | brighten(colour, 0.5F + 0.5F * (float) pulse);
        Vec3 right = exit.right();
        Vec3 up = exit.up();
        ring(lines, pose, exitCentre, right, up, plot.radius(), rim);
        ring(lines, pose, exitCentre, right, up, plot.radius() * (0.55D + 0.35D * pulse), bright);
        Vec3 ahead = exitCentre.add(exit.normal().scale(Math.max(6.0D, plot.radius() * 2.5D)));
        line(lines, pose, exitCentre, ahead, bright);
        Vec3 back = exit.normal().scale(-2.0D);
        line(lines, pose, ahead, ahead.add(back).add(right.scale(1.5D)), bright);
        line(lines, pose, ahead, ahead.add(back).add(right.scale(-1.5D)), bright);
        dashed(lines, pose, exitCentre, target, 0xA0FFD0A0);

        BufferUploader.drawWithShader(lines.buildOrThrow());
        RenderSystem.lineWidth(1.0F);
    }

    private static void line(BufferBuilder lines, Matrix4f pose, Vec3 from, Vec3 to, int argb) {
        lines.addVertex(pose, (float) from.x, (float) from.y, (float) from.z).setColor(argb);
        lines.addVertex(pose, (float) to.x, (float) to.y, (float) to.z).setColor(argb);
    }

    private static void dashed(BufferBuilder lines, Matrix4f pose, Vec3 from, Vec3 to, int argb) {
        double length = from.distanceTo(to);
        int dashes = (int) Math.max(1, Math.min(64, length / 3.0D));
        for (int i = 0; i < dashes; i += 2) {
            Vec3 a = from.lerp(to, i / (double) dashes);
            Vec3 b = from.lerp(to, Math.min(1.0D, (i + 1) / (double) dashes));
            line(lines, pose, a, b, argb);
        }
    }

    private static void ring(BufferBuilder lines, Matrix4f pose, Vec3 centre, Vec3 u, Vec3 v, double radius, int argb) {
        Vec3 last = null;
        for (int i = 0; i <= RING_SEGMENTS; i++) {
            double angle = Math.PI * 2.0D * i / RING_SEGMENTS;
            Vec3 point = centre.add(u.scale(Math.cos(angle) * radius)).add(v.scale(Math.sin(angle) * radius));
            if (last != null) {
                line(lines, pose, last, point, argb);
            }
            last = point;
        }
    }

    private static int brighten(int rgb, float toWhite) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r += Math.round((255 - r) * toWhite * 0.6F);
        g += Math.round((255 - g) * toWhite * 0.6F);
        b += Math.round((255 - b) * toWhite * 0.6F);
        return r << 16 | g << 8 | b;
    }

    @Override
    public void close() {
        if (buffer != null) {
            buffer.close();
            buffer = null;
        }
    }
}
