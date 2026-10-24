package uk.co.iceconchy.aerowarptics.client.fx;

import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import uk.co.iceconchy.aerowarptics.plotter.PlotGeometry;
import uk.co.iceconchy.aerowarptics.plotter.PlotterChoreography;
import uk.co.iceconchy.aerowarptics.plotter.PlotterState;
import uk.co.iceconchy.aerowarptics.plotter.RiftPlotterBlockEntity;

import java.util.List;

/**
 * A Rift Plotter's strike, drawn: the beam from the ball, the star at the tear point and the rings closing on it, the
 * arcs, the cracks, the burst and the shockwave, and the tethers that hold the rim. How much of each there is, and
 * when, is {@link PlotterChoreography}'s.
 *
 * <p>Drawn twice - once alpha-blended in {@link LightStrokes.Layer#BODY}, in the rift's own colour, so the strike has
 * a body at noon; once as additive fire in {@link LightStrokes.Layer#GLOW}, which is what it looks like at night.
 * The aperture itself is {@link RiftEffectManager}'s, held from {@code PlotterEffects}.
 */
@OnlyIn(Dist.CLIENT)
final class PlotterSpectacle {

    /** An arc jumping from the ball to the star, for a few ticks. */
    record Arc(int seed, Vec3 to, double born) {
    }

    /** Ticks an arc lives. */
    static final int ARC_LIFE = 5;

    private static final int WHITE = 0xFFFFFF;
    private static final int CRACKS = 11;
    private static final int TETHERS = 4;

    private PlotterSpectacle() {
    }

    /**
     * One plotter's strike, in one layer.
     *
     * @param ticks ticks into the state it is in, with the partial tick
     * @param time  the level's time, with the partial tick
     * @param spin  the armillary ball's own angle, which the tethers turn with
     */
    static void draw(LightStrokes.Pen pen, RiftPlotterBlockEntity plotter, double ticks, double time, double spin,
                     List<Arc> arcs) {
        PlotterState state = plotter.state();
        if (state == PlotterState.IDLE || pen.layer == LightStrokes.Layer.THROUGH || pen.layer == LightStrokes.Layer.SHADE) {
            return;
        }
        double charged = state == PlotterState.REACHING ? 1.0D : plotter.charged();
        PlotGeometry.Frame entry = plotter.entryFrame();
        double radius = plotter.plot().radius();
        Vec3 ball = PlotGeometry.focus(plotter.getBlockPos(), plotter.plot());
        Vec3 point = entry.centre();
        int colour = plotter.riftColour();
        int accent = plotter.riftAccent();
        int hot = LightStrokes.mix(colour, WHITE, 0.55D);

        double beam = PlotterChoreography.beam(state, charged, ticks);
        double star = PlotterChoreography.star(state, charged, ticks) * radius;
        double starGlow = PlotterChoreography.starGlow(state, charged, ticks);
        double gather = PlotterChoreography.gather(state, charged, ticks);
        double cracks = PlotterChoreography.cracks(state, ticks);
        double crackGlow = PlotterChoreography.crackGlow(state, ticks);
        double flash = PlotterChoreography.flash(state, ticks);
        double shock = PlotterChoreography.shock(state, ticks) * radius;
        double shockGlow = PlotterChoreography.shockGlow(state, ticks);
        double tethers = PlotterChoreography.tethers(state, ticks);
        // A flicker through the whole of it while it is live, as a field under load would.
        double flicker = 0.9D + 0.1D * Math.sin(time * 1.7D) * Math.sin(time * 0.53D);

        if (pen.layer == LightStrokes.Layer.BODY) {
            if (beam > 0.0D) {
                LightStrokes.stroke(pen, ball, point, LightStrokes.width(pen, ball, 2.0D + 4.0D * beam),
                        LightStrokes.width(pen, point, 2.0D + 4.0D * beam), LightStrokes.argb(colour, 0.35D * beam),
                        LightStrokes.argb(colour, 0.65D * beam));
            }
            if (starGlow > 0.0D) {
                LightStrokes.disc(pen, point, 0.0D, star, LightStrokes.argb(hot, 0.85D * starGlow * flicker),
                        LightStrokes.argb(colour, 0.0D));
            }
            if (cracks > 0.0D && crackGlow > 0.0D) {
                cracks(pen, entry, radius, cracks, time, 3.0D, LightStrokes.argb(colour, 0.8D * crackGlow));
            }
            if (shock > 0.0D) {
                LightStrokes.band(pen, entry, Math.max(0.0D, shock - radius * 0.6D), shock, LightStrokes.argb(colour, 0.0D),
                        LightStrokes.argb(colour, 0.5D * shockGlow));
            }
            if (flash > 0.0D) {
                LightStrokes.disc(pen, point, 0.0D, radius * 2.4D, LightStrokes.argb(WHITE, 0.6D * flash),
                        LightStrokes.argb(colour, 0.0D));
            }
            if (tethers > 0.0D) {
                tethers(pen, entry, radius, ball, spin, time, 2.5D, LightStrokes.argb(colour, 0.45D * tethers));
            }
            return;
        }

        // The fire.
        if (beam > 0.0D) {
            double reachWidth = Math.max(0.3D, star * 1.2D);
            LightStrokes.stroke(pen, ball, point, 0.2D + 0.3D * beam, reachWidth, LightStrokes.argb(colour, 0.3D * beam),
                    LightStrokes.argb(colour, 0.45D * beam));
            LightStrokes.stroke(pen, ball, point, 0.05D + 0.1D * beam, 0.08D + 0.12D * beam,
                    LightStrokes.argb(hot, 0.95D * beam * flicker), LightStrokes.argb(hot, 0.95D * beam * flicker));
            helix(pen, ball, point, beam, time, LightStrokes.argb(accent, 0.6D * beam));
        }
        if (starGlow > 0.0D) {
            LightStrokes.disc(pen, point, 0.0D, star * 2.4D, LightStrokes.argb(hot, starGlow * flicker),
                    LightStrokes.argb(colour, 0.0D));
            LightStrokes.disc(pen, point, 0.0D, star * 0.55D, LightStrokes.argb(WHITE, starGlow),
                    LightStrokes.argb(hot, 0.6D * starGlow));
        }
        if (gather > 0.0D) {
            double period = PlotterChoreography.gatherPeriod(charged);
            for (int ring = 0; ring < 3; ring++) {
                double u = (time / period + ring / 3.0D) % 1.0D;
                double at = radius * 1.8D * (1.0D - u);
                // Brightening as it closes, and gone at the moment it reaches the star.
                double alpha = gather * Math.sin(Math.PI * u) * 0.8D;
                LightStrokes.ring(pen, entry, Math.max(star, at), 2.5D, LightStrokes.argb(accent, alpha));
            }
        }
        if (cracks > 0.0D && crackGlow > 0.0D) {
            cracks(pen, entry, radius, cracks, time, 3.5D, LightStrokes.argb(hot, crackGlow));
            LightStrokes.ring(pen, entry, radius, 3.0D, LightStrokes.argb(colour, 0.6D * crackGlow * cracks));
        }
        for (Arc arc : arcs) {
            double age = time - arc.born();
            if (age < 0.0D || age > ARC_LIFE) {
                continue;
            }
            double fade = 1.0D - age / ARC_LIFE;
            LightStrokes.bolt(pen, ball, arc.to(), arc.seed(), Math.min(0.8D, ball.distanceTo(arc.to()) * 0.08D), 2.5D,
                    LightStrokes.argb(hot, fade));
        }
        if (flash > 0.0D) {
            LightStrokes.disc(pen, point, 0.0D, radius * 3.2D, LightStrokes.argb(WHITE, flash),
                    LightStrokes.argb(colour, 0.0D));
        }
        if (shock > 0.0D) {
            LightStrokes.band(pen, entry, Math.max(0.0D, shock - radius * 0.5D), shock, LightStrokes.argb(colour, 0.0D),
                    LightStrokes.argb(accent, 0.8D * shockGlow));
            LightStrokes.ring(pen, entry, shock, 4.0D, LightStrokes.argb(hot, 0.8D * shockGlow));
        }
        if (tethers > 0.0D) {
            tethers(pen, entry, radius, ball, spin, time, 3.0D, LightStrokes.argb(hot, 0.7D * tethers * flicker));
            for (int tether = 0; tether < TETHERS; tether++) {
                LightStrokes.disc(pen, anchor(entry, radius, spin, tether), 0.0D, 0.5D + radius * 0.06D,
                        LightStrokes.argb(hot, 0.8D * tethers), LightStrokes.argb(colour, 0.0D));
            }
        }
    }

    /** Strands of the field wound round the beam, pinched in at both ends and turning. */
    private static void helix(LightStrokes.Pen pen, Vec3 from, Vec3 to, double beam, double time, int argb) {
        Vec3 along = to.subtract(from);
        double length = along.length();
        if (length < 1.0e-3D) {
            return;
        }
        Vec3 across = along.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (across.lengthSqr() < 1.0e-9D) {
            across = new Vec3(1.0D, 0.0D, 0.0D);
        }
        across = across.normalize();
        Vec3 over = along.cross(across).normalize();
        int steps = 28;
        for (int strand = 0; strand < 3; strand++) {
            Vec3 last = null;
            for (int step = 0; step <= steps; step++) {
                double s = step / (double) steps;
                double angle = s * length * 1.3D + time * 0.25D + strand * Math.PI * 2.0D / 3.0D;
                double out = (0.12D + 0.3D * beam) * Math.sin(Math.PI * s);
                Vec3 point = from.add(along.scale(s)).add(across.scale(Math.cos(angle) * out))
                        .add(over.scale(Math.sin(angle) * out));
                if (last != null) {
                    LightStrokes.stroke(pen, last, point, 1.8D, argb);
                }
                last = point;
            }
        }
    }

    /** Cracks running out across the disc from its middle, so far towards the rim, re-drawn a little every few ticks. */
    private static void cracks(LightStrokes.Pen pen, PlotGeometry.Frame entry, double radius, double reach, double time,
                               double pixels, int argb) {
        int crackle = (int) (time / 3.0D);
        Vec3 centre = entry.centre();
        for (int crack = 0; crack < CRACKS; crack++) {
            int seed = RiftShatter.seedFor(centre.x, centre.y, centre.z) + crack * 7919;
            double angle = Math.PI * 2.0D * (crack + RiftShatter.noise(seed, 0) * 0.6D) / CRACKS;
            double length = radius * reach * (0.7D + 0.3D * RiftShatter.noise(seed, 1));
            Vec3 end = entry.world(Math.cos(angle) * length, Math.sin(angle) * length, 0.0D);
            LightStrokes.bolt(pen, centre, end, seed + crackle * 31, length * 0.1D, pixels, argb);
        }
    }

    /** Tethers of light from the ball to the rim, turning with the ball. */
    private static void tethers(LightStrokes.Pen pen, PlotGeometry.Frame entry, double radius, Vec3 ball, double spin,
                                double time, double pixels, int argb) {
        int crackle = (int) (time / 2.0D);
        for (int tether = 0; tether < TETHERS; tether++) {
            Vec3 anchor = anchor(entry, radius, spin, tether);
            LightStrokes.bolt(pen, ball, anchor, crackle * 17 + tether * 101, Math.min(0.5D, ball.distanceTo(anchor) * 0.03D),
                    pixels, argb);
        }
    }

    private static Vec3 anchor(PlotGeometry.Frame entry, double radius, double spin, int tether) {
        double angle = spin * 0.25D + Math.PI * 2.0D * tether / TETHERS;
        return entry.world(Math.cos(angle) * radius, Math.sin(angle) * radius, 0.0D);
    }
}
