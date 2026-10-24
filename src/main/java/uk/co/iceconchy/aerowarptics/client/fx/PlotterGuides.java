package uk.co.iceconchy.aerowarptics.client.fx;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import uk.co.iceconchy.aerowarptics.plotter.DockAlign;
import uk.co.iceconchy.aerowarptics.plotter.Firing;
import uk.co.iceconchy.aerowarptics.plotter.Plot;
import uk.co.iceconchy.aerowarptics.plotter.PlotGeometry;

/**
 * The Rift Plotter's guides in the world: the entry as it is being set, and the dock a pilot waits in.
 *
 * <p>Both are drawn with {@link LightStrokes} in all four of its layers - a faint ghost through terrain, a dark edge,
 * the strokes themselves and a little bloom - so they read at noon over bright sky as well as at night. The dock is
 * amber, the entry teal; the dock turns teal too once the ship it holds is lined up, since then only the rift is
 * wanted.
 */
@OnlyIn(Dist.CLIENT)
final class PlotterGuides {

    static final int TEAL = 0x49D9C4;
    static final int AMBER = 0xFFB23A;
    /** The dark edging under every stroke: a burnt brown, not black, so it reads as the stroke's shadow. */
    private static final int SHADOW = 0x1C0F03;

    /** Pixels across a guide's main strokes, and its edging's. */
    private static final double EDGE = 3.2D;
    private static final double EDGING = 7.0D;
    private static final double FINE = 2.0D;

    private PlotterGuides() {
    }

    // ------------------------------------------------------------------ the entry, as it is being set

    /**
     * The entry a player is setting, drawn where it will tear: its rim and face, the way through it, the beam out to it
     * from the ball, and - with the dock helper on - the dock behind it, as wide and as deep as it is being set.
     *
     * @param dockReach how deep the server makes a dock nobody has sized
     */
    static void entry(LightStrokes.Pen pen, BlockPos pos, Plot plot, Firing firing, double dockReach, double time) {
        PlotGeometry.Frame entry = PlotGeometry.input(pos, plot);
        double radius = plot.radius();
        double pulse = 0.8D + 0.2D * Math.sin(time * 0.15D);
        double reach = Math.max(4.0D, radius * 1.4D);
        Vec3 tail = entry.world(0, 0, -reach * 0.6D);
        Vec3 head = entry.world(0, 0, reach);
        Vec3 ball = PlotGeometry.focus(pos, plot);
        switch (pen.layer) {
            case THROUGH -> {
                LightStrokes.ring(pen, entry, radius, FINE, LightStrokes.argb(TEAL, 0.3D));
                LightStrokes.stroke(pen, tail, head, FINE, LightStrokes.argb(TEAL, 0.3D));
            }
            case SHADE -> {
                LightStrokes.band(pen, entry, 0.0D, radius, LightStrokes.argb(TEAL, 0.1D), LightStrokes.argb(TEAL, 0.16D));
                LightStrokes.ring(pen, entry, radius, EDGING, LightStrokes.argb(SHADOW, 0.5D));
                LightStrokes.stroke(pen, tail, head, EDGING, LightStrokes.argb(SHADOW, 0.5D));
                arrowhead(pen, entry, reach, radius * 0.35D, EDGING, LightStrokes.argb(SHADOW, 0.5D));
                beam(pen, ball, entry.centre(), time, EDGING, LightStrokes.argb(SHADOW, 0.4D));
            }
            case BODY -> {
                int rim = LightStrokes.argb(TEAL, pulse);
                LightStrokes.ring(pen, entry, radius, EDGE, rim);
                LightStrokes.ring(pen, entry, radius * 0.66D, FINE, LightStrokes.argb(TEAL, 0.55D));
                LightStrokes.ring(pen, entry, radius * 0.33D, FINE, LightStrokes.argb(TEAL, 0.4D));
                for (int spoke = 0; spoke < 8; spoke++) {
                    double angle = Math.PI * 2.0D * spoke / 8.0D + time * 0.01D;
                    LightStrokes.stroke(pen, entry.world(Math.cos(angle) * radius * 0.33D, Math.sin(angle) * radius * 0.33D, 0),
                            entry.world(Math.cos(angle) * radius, Math.sin(angle) * radius, 0), FINE,
                            LightStrokes.argb(TEAL, 0.45D));
                }
                LightStrokes.stroke(pen, tail, head, EDGE, rim);
                arrowhead(pen, entry, reach, radius * 0.35D, EDGE, rim);
                beam(pen, ball, entry.centre(), time, FINE, LightStrokes.argb(TEAL, 0.85D));
            }
            case GLOW -> LightStrokes.ring(pen, entry, radius, EDGE * 2.5D, LightStrokes.argb(TEAL, 0.25D));
        }
        if (firing.dock()) {
            dock(pen, entry, radius, firing.dockRadius(radius), firing.dockDepth(dockReach), false, false, false, time);
        }
    }

    /** The beam from the ball out to where the rift will tear, as dashes crawling outward. */
    private static void beam(LightStrokes.Pen pen, Vec3 ball, Vec3 to, double time, double pixels, int argb) {
        Vec3 along = to.subtract(ball);
        int dashes = (int) Math.max(2, Math.min(64, along.length() / 1.5D));
        double crawl = (time * 0.08D) % 2.0D;
        for (int i = 0; i < dashes; i += 2) {
            double a = Math.min(1.0D, (i + crawl) / dashes);
            double b = Math.min(1.0D, (i + 1 + crawl) / dashes);
            LightStrokes.stroke(pen, ball.add(along.scale(a)), ball.add(along.scale(b)), pixels, argb);
        }
    }

    // ------------------------------------------------------------------ the dock

    /**
     * The dock: the cylinder behind the entry a waiting vehicle is caught in ({@link DockAlign#inDock}), the mark its
     * nearest end is held at ({@link DockAlign#STANDOFF} short of the rift), arrowheads crawling along the axis the way
     * it will be sent, bands of light running along the walls towards the rift, and - while the rift is shut - the rim
     * where it will tear. Amber while it waits, brighter and breathing faster while it holds a vehicle, teal once that
     * vehicle is lined up.
     *
     * @param radius the rift's radius, which sizes the mark and the arrowheads
     * @param zone   the dock's own radius, which a player may have set wider or narrower than suits the rift
     * @param depth  how far behind the entry the dock reaches
     */
    static void dock(LightStrokes.Pen pen, PlotGeometry.Frame entry, double radius, double zone, double depth,
                     boolean holding, boolean linedUp, boolean riftOpen, double time) {
        int colour = linedUp ? TEAL : AMBER;
        double breath = 0.85D + 0.15D * Math.sin(time * (holding ? 0.3D : 0.08D));
        double strength = holding ? 1.0D : 0.85D;
        PlotGeometry.Frame back = shifted(entry, -depth);
        PlotGeometry.Frame middle = shifted(entry, -depth / 2.0D);
        switch (pen.layer) {
            case THROUGH -> {
                int ghost = LightStrokes.argb(colour, 0.28D * strength);
                LightStrokes.ring(pen, entry, zone, FINE, ghost);
                LightStrokes.ring(pen, back, zone, FINE, ghost);
                sides(pen, entry, zone, depth, FINE, LightStrokes.argb(colour, 0.14D));
                mark(pen, entry, radius, FINE, ghost);
                if (!riftOpen) {
                    LightStrokes.ring(pen, entry, radius, FINE, LightStrokes.argb(TEAL, 0.25D));
                }
            }
            case SHADE -> {
                int edging = LightStrokes.argb(SHADOW, 0.5D * strength);
                LightStrokes.ring(pen, entry, zone, EDGING, edging);
                LightStrokes.ring(pen, back, zone, EDGING, edging);
                sides(pen, entry, zone, depth, EDGING * 0.7D, LightStrokes.argb(SHADOW, 0.3D));
                mark(pen, entry, radius, EDGING, edging);
                chevrons(pen, entry, radius, depth, time, EDGING, SHADOW, 0.45D * strength);
                if (!riftOpen) {
                    LightStrokes.ring(pen, entry, radius, EDGING, edging);
                }
                // The walls, faintly tinted, with bands of light running along them to the rift.
                LightStrokes.tube(pen, entry, zone, -depth, 0.0D, LightStrokes.argb(colour, 0.03D),
                        LightStrokes.argb(colour, 0.08D));
                for (int band = 0; band < 3; band++) {
                    double u = ((time / 70.0D) + band / 3.0D) % 1.0D;
                    double at = -depth * (1.0D - u);
                    double fade = Math.sin(Math.PI * u) * (holding ? 0.3D : 0.2D);
                    LightStrokes.tube(pen, entry, zone, at - 0.6D, at + 0.6D, LightStrokes.argb(colour, 0.0D),
                            LightStrokes.argb(colour, fade));
                }
            }
            case BODY -> {
                int bright = LightStrokes.argb(colour, 0.95D * breath * strength);
                LightStrokes.ring(pen, entry, zone, EDGE, bright);
                LightStrokes.ring(pen, back, zone, EDGE, bright);
                LightStrokes.ring(pen, middle, zone, FINE, LightStrokes.argb(colour, 0.4D * strength));
                sides(pen, entry, zone, depth, FINE, LightStrokes.argb(colour, 0.55D * strength));
                mark(pen, entry, radius, EDGE, LightStrokes.argb(colour, 1.0D));
                LightStrokes.disc(pen, entry.world(0, 0, -DockAlign.STANDOFF), 0.0D, Math.max(0.6D, radius * 0.15D),
                        LightStrokes.argb(colour, 0.35D), LightStrokes.argb(colour, 0.0D));
                chevrons(pen, entry, radius, depth, time, EDGE, colour, 0.9D * strength);
                if (!riftOpen) {
                    LightStrokes.ring(pen, entry, radius, EDGE, LightStrokes.argb(TEAL, 0.9D * breath));
                }
            }
            case GLOW -> {
                int bloom = LightStrokes.argb(colour, 0.22D * breath);
                LightStrokes.ring(pen, entry, zone, EDGE * 2.5D, bloom);
                LightStrokes.ring(pen, back, zone, EDGE * 2.5D, bloom);
                LightStrokes.disc(pen, entry.world(0, 0, -DockAlign.STANDOFF), 0.0D, Math.max(1.2D, radius * 0.3D),
                        LightStrokes.argb(colour, 0.4D * breath), LightStrokes.argb(colour, 0.0D));
            }
        }
    }

    /** Eight lines along the dock's walls, front to back. */
    private static void sides(LightStrokes.Pen pen, PlotGeometry.Frame entry, double zone, double depth, double pixels,
                              int argb) {
        for (int side = 0; side < 8; side++) {
            double angle = Math.PI * 2.0D * side / 8.0D;
            LightStrokes.stroke(pen, entry.world(Math.cos(angle) * zone, Math.sin(angle) * zone, 0),
                    entry.world(Math.cos(angle) * zone, Math.sin(angle) * zone, -depth), pixels, argb);
        }
    }

    /** Where a caught vehicle's nearest end waits: a diamond on the axis, short of the rift. */
    private static void mark(LightStrokes.Pen pen, PlotGeometry.Frame entry, double radius, double pixels, int argb) {
        Vec3 mark = entry.world(0, 0, -DockAlign.STANDOFF);
        double d = Math.max(0.6D, radius * 0.15D);
        Vec3 up = entry.up().scale(d);
        Vec3 right = entry.right().scale(d);
        LightStrokes.stroke(pen, mark.add(up), mark.add(right), pixels, argb);
        LightStrokes.stroke(pen, mark.add(right), mark.subtract(up), pixels, argb);
        LightStrokes.stroke(pen, mark.subtract(up), mark.subtract(right), pixels, argb);
        LightStrokes.stroke(pen, mark.subtract(right), mark.add(up), pixels, argb);
    }

    /** Arrowheads on the dock's axis, crawling from its back towards the rift: this way through. */
    private static void chevrons(LightStrokes.Pen pen, PlotGeometry.Frame entry, double radius, double depth, double time,
                                 double pixels, int colour, double alpha) {
        double spacing = Math.max(4.0D, depth / 5.0D);
        double size = Math.max(1.0D, radius * 0.35D);
        double crawl = (time * 0.08D) % spacing;
        for (double back = depth - crawl; back > DockAlign.STANDOFF + size; back -= spacing) {
            // Fading in at the dock's back and out as each nears the mark, so none pops in or out.
            double fade = Math.min(1.0D, Math.min((depth - back) / spacing, (back - DockAlign.STANDOFF - size) / spacing));
            arrowhead(pen, entry, -back, size, pixels, LightStrokes.argb(colour, alpha * Math.max(0.0D, fade)));
        }
    }

    /** A four-armed arrowhead on a frame's axis, its point so far through, pointing through. */
    private static void arrowhead(LightStrokes.Pen pen, PlotGeometry.Frame frame, double through, double size, double pixels,
                                  int argb) {
        Vec3 tip = frame.world(0, 0, through);
        LightStrokes.stroke(pen, tip, frame.world(size * 0.7D, 0, through - size), pixels, argb);
        LightStrokes.stroke(pen, tip, frame.world(-size * 0.7D, 0, through - size), pixels, argb);
        LightStrokes.stroke(pen, tip, frame.world(0, size * 0.7D, through - size), pixels, argb);
        LightStrokes.stroke(pen, tip, frame.world(0, -size * 0.7D, through - size), pixels, argb);
    }

    /** The same frame, moved along its own axis. */
    private static PlotGeometry.Frame shifted(PlotGeometry.Frame frame, double through) {
        return new PlotGeometry.Frame(frame.world(0, 0, through), frame.normal(), frame.up(), frame.right());
    }
}
