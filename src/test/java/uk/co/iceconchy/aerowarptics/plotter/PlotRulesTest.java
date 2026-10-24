package uk.co.iceconchy.aerowarptics.plotter;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Rift Plotter's refusals: each is found in the order a player fixes things, before anything is drawn - and at
 * the far end, before anything is moved.
 */
class PlotRulesTest {

    private static PlotRules.Situation ready() {
        return new PlotRules.Situation(true, false, true, true, true, 90.0D, -64, 320);
    }

    @Test
    void aReadyPlotterIsNotRefused() {
        assertEquals(PlotFailure.NONE, PlotRules.preflight(ready()));
    }

    @Test
    void eachRefusalIsFoundInOrder() {
        assertEquals(PlotFailure.DISABLED, PlotRules.preflight(
                new PlotRules.Situation(false, true, false, false, false, 1.0e9D, -64, 320)));
        assertEquals(PlotFailure.BUSY, PlotRules.preflight(
                new PlotRules.Situation(true, true, false, false, false, 1.0e9D, -64, 320)));
        assertEquals(PlotFailure.NO_TARGET, PlotRules.preflight(
                new PlotRules.Situation(true, false, false, false, false, 1.0e9D, -64, 320)));
        assertEquals(PlotFailure.NOT_TURNING, PlotRules.preflight(
                new PlotRules.Situation(true, false, true, false, false, 1.0e9D, -64, 320)));
        assertEquals(PlotFailure.OUTSIDE_WORLD, PlotRules.preflight(
                new PlotRules.Situation(true, false, true, true, false, 90.0D, -64, 320)));
    }

    /** There is no closest an exit may be: a plot across a base is as good a plot as one across the world. */
    @Test
    void anExitRightBesideThePlotterIsNotRefused() {
        assertEquals(PlotFailure.NONE, PlotRules.preflight(ready()));
        assertEquals(PlotFailure.NONE, PlotRules.farEnd(true, 72.0D, -64, 320, false, false));
    }

    @Test
    void anExitOffTheTopOrBottomOfTheWorldIsOutside() {
        assertTrue(PlotRules.outside(true, 320.0D, -64, 320), "the build limit is exclusive");
        assertTrue(PlotRules.outside(true, -64.5D, -64, 320));
        assertTrue(PlotRules.outside(true, Double.NaN, -64, 320), "nowhere is outside");
        assertFalse(PlotRules.outside(true, -64.0D, -64, 320));
        assertFalse(PlotRules.outside(true, 319.9D, -64, 320));
    }

    @Test
    void theFarEndAsksAgainWithTheRealGroundThenAboutTheDiscs() {
        assertEquals(PlotFailure.OUTSIDE_WORLD, PlotRules.farEnd(true, 400.0D, -64, 320, true, true));
        assertEquals(PlotFailure.EXIT_BURIED, PlotRules.farEnd(true, 90.0D, -64, 320, true, true));
        assertEquals(PlotFailure.ENTRY_BURIED, PlotRules.farEnd(true, 90.0D, -64, 320, false, true));
        assertEquals(PlotFailure.NONE, PlotRules.farEnd(true, 90.0D, -64, 320, false, false));
    }

    /** Half the disc in rock is a strike aimed out of a hillside; more than half is a rift with nowhere to be. */
    @Test
    void aDiscIsBuriedOnlyPastHalf() {
        assertFalse(PlotRules.buried(0, 25));
        assertFalse(PlotRules.buried(12, 25));
        assertTrue(PlotRules.buried(13, 25));
        assertFalse(PlotRules.buried(0, 0), "nothing sampled is nothing buried");
    }

    @Test
    void theSamplesLieOnTheDiscInsideItsRim() {
        PlotGeometry.Frame frame = PlotGeometry.frame(new Vec3(10, 70, -4), 33, -20);
        List<Vec3> samples = PlotRules.samples(frame, 6.0D);
        assertEquals(25, samples.size());
        for (Vec3 point : samples) {
            Vec3 local = frame.local(point);
            assertEquals(0.0D, local.z, 1.0e-9D, "in the plane");
            assertTrue(Math.hypot(local.x, local.y) <= 6.0D, "inside the rim");
        }
    }
}
