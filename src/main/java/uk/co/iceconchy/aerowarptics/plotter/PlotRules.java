package uk.co.iceconchy.aerowarptics.plotter;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The Rift Plotter's refusals, as arithmetic.
 *
 * <p>Two moments. {@link #preflight} is asked before a single millibucket is drawn, of everything that can be
 * known at the machine: is it allowed, free, told where, turning, and is the far end somewhere a rift could be at
 * all. {@link #farEnd} is asked once the far end has loaded, of the ground there - which cannot be read sooner,
 * because nobody is standing at the far end, which is the whole point of the machine.
 *
 * <p>Pure: the block entity reads the world and hands the answers in, so each rule can be walked in a test.
 */
public final class PlotRules {

    /** Rings of samples across a disc, besides its middle, for {@link #buried}. */
    private static final int RINGS = 3;

    /** Samples round each ring. */
    private static final int SPOKES = 8;

    private PlotRules() {
    }

    /**
     * What the machine knows before it draws anything.
     *
     * <p>There is no closest an exit may be: a plotter sending freight across a base is as much a plotter as one
     * shelling the far side of the world. An exit put right beside its own entry makes a loop - whatever comes out
     * and flies back in goes round again - which is the player's to build or not.
     *
     * @param insideBorder    whether the target is inside the world border
     * @param exitY           the exit middle's height, from the last ground known at the target
     */
    public record Situation(boolean enabled, boolean busy, boolean targetSet, boolean turning, boolean insideBorder,
                            double exitY, int minBuildHeight, int maxBuildHeight) {
    }

    /** The first reason not to begin, or {@link PlotFailure#NONE}. */
    public static PlotFailure preflight(Situation at) {
        if (!at.enabled()) {
            return PlotFailure.DISABLED;
        }
        if (at.busy()) {
            return PlotFailure.BUSY;
        }
        if (!at.targetSet()) {
            return PlotFailure.NO_TARGET;
        }
        if (!at.turning()) {
            return PlotFailure.NOT_TURNING;
        }
        return outside(at.insideBorder(), at.exitY(), at.minBuildHeight(), at.maxBuildHeight())
                ? PlotFailure.OUTSIDE_WORLD : PlotFailure.NONE;
    }

    /** Whether a target or an exit is somewhere nothing can be: past the border, or off the top or bottom. */
    public static boolean outside(boolean insideBorder, double exitY, int minBuildHeight, int maxBuildHeight) {
        return !insideBorder || !(exitY >= minBuildHeight) || !(exitY < maxBuildHeight);
    }

    /**
     * The far end, once it has loaded: where the target's ground really is changes the exit's height, so the world's
     * limits are asked again, and then whether either disc is in the ground.
     */
    public static PlotFailure farEnd(boolean insideBorder, double exitY, int minBuildHeight, int maxBuildHeight,
                                     boolean exitBuried, boolean entryBuried) {
        if (outside(insideBorder, exitY, minBuildHeight, maxBuildHeight)) {
            return PlotFailure.OUTSIDE_WORLD;
        }
        if (exitBuried) {
            return PlotFailure.EXIT_BURIED;
        }
        return entryBuried ? PlotFailure.ENTRY_BURIED : PlotFailure.NONE;
    }

    /**
     * Whether a disc is buried: more than half the points sampled across it are solid.
     *
     * <p>Half, not any. A strike aimed to come out of a hillside's shoulder, or through a wall, is a choice a
     * player may make - what comes out meets the wall. A rift that is mostly rock has nowhere for anything to be.
     */
    public static boolean buried(int solid, int sampled) {
        return sampled > 0 && solid * 2 > sampled;
    }

    /** Points spread across a disc to be read for {@link #buried}: its middle, and rings of spokes out to its rim. */
    public static List<Vec3> samples(PlotGeometry.Frame frame, double radius) {
        List<Vec3> points = new ArrayList<>(1 + RINGS * SPOKES);
        points.add(frame.centre());
        for (int ring = 1; ring <= RINGS; ring++) {
            double out = radius * ring / (RINGS + 0.5D);
            for (int spoke = 0; spoke < SPOKES; spoke++) {
                double angle = Math.PI * 2.0D * (spoke + (ring % 2) * 0.5D) / SPOKES;
                points.add(frame.world(Math.cos(angle) * out, Math.sin(angle) * out, 0.0D));
            }
        }
        return points;
    }
}
