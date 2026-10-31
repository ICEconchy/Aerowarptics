package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.rifting.RiftFlame;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Rift Brazier's fire, held to its shape.
 *
 * <p>A flame that burns out of the box its renderer is culled by vanishes at the edge of the screen; a rim that
 * folds in over the core draws the dark on top of the light; a fire that never quite goes out keeps a brazier
 * looking lit. None of those throws.
 */
class RiftFlameTest {

    @Test
    void aTongueIsRoundAtItsFootAndAPointAtItsTip() {
        assertTrue(RiftFlame.shape(0.0D) > 0.5D, "a tongue's foot is a point");
        assertEquals(0.0D, RiftFlame.shape(1.0D), 1.0e-6D, "a tongue's tip is not a point");
        double widest = 0.0D;
        for (double s = 0.0D; s <= 1.0D; s += 0.01D) {
            widest = Math.max(widest, RiftFlame.shape(s));
        }
        assertEquals(1.0D, widest, 0.01D);
    }

    /** The rim tears in and out, as a rift's does, but never so far that it could cross back over the core. */
    @Test
    void theRimWandersButNeverFolds() {
        for (double time = 0.0D; time < 400.0D; time += 0.37D) {
            for (double s = 0.0D; s <= 1.0D; s += 0.05D) {
                double wander = RiftFlame.wander(s, time, 1.3D);
                assertTrue(wander >= 1.0D - RiftFlame.TEAR - 1.0e-9D && wander <= 1.0D + RiftFlame.TEAR + 1.0e-9D,
                        "the rim tore " + wander);
            }
        }
        assertTrue(RiftFlame.GLOW_FROM < RiftFlame.CORE && RiftFlame.CORE < 1.0D && RiftFlame.HAZE > 1.0D,
                "the core, the rim and the haze are out of order");
    }

    /** Every flame, rim, sway and haze included, stays in the box the brazier hands its renderer. */
    @Test
    void everyFlameStaysInTheRenderBox() {
        for (RiftFlame.Tongue tongue : RiftFlame.TONGUES) {
            for (double time = 0.0D; time < 2_000.0D; time += 0.7D) {
                double height = RiftFlame.height(tongue, time, 1.0D);
                assertTrue(RiftFlame.FLOOR + height <= 1.0D + RiftFlame.REACH_UP, "a flame burns out of the top of its box");
                for (double s = 0.0D; s <= 1.0D; s += 0.1D) {
                    double reach = RiftFlame.halfWidth(tongue, s, time, 1.0D) * RiftFlame.HAZE
                            + Math.abs(RiftFlame.sway(s, time, tongue.phase()));
                    for (double centre : new double[]{tongue.x(), tongue.z()}) {
                        assertTrue(centre - reach >= -RiftFlame.REACH_OUT && centre + reach <= 1.0D + RiftFlame.REACH_OUT,
                                "a flame burns out of the side of its box");
                    }
                }
            }
        }
    }

    @Test
    void theFlickerStaysInItsBand() {
        for (double time = 0.0D; time < 1_000.0D; time += 0.3D) {
            double flicker = RiftFlame.flicker(time, 2.2D);
            assertTrue(flicker >= 1.0D - RiftFlame.FLICKER - 1.0e-9D && flicker <= 1.0D + RiftFlame.FLICKER + 1.0e-9D);
        }
    }

    @Test
    void itCatchesQuicklyAndDiesDownAndThenIsOut() {
        double level = 0.0D;
        int ticks = 0;
        while (level < 1.0D) {
            double next = RiftFlame.step(level, true);
            assertTrue(next > level && next <= 1.0D);
            level = next;
            assertTrue(++ticks <= 10, "the fire takes too long to catch");
        }
        ticks = 0;
        while (level > 0.0D) {
            double next = RiftFlame.step(level, false);
            assertTrue(next < level && next >= 0.0D);
            level = next;
            assertTrue(++ticks <= 15, "the fire takes too long to die down");
        }
        assertEquals(0.0D, RiftFlame.height(RiftFlame.TONGUES.get(0), 12.0D, level), "an out fire still has flames");
    }

    /** Dark first, then light over it - a rift's face and fire, in order - and no rift held open any more. */
    @Test
    void theDarkIsDrawnBeforeTheLightAndNoRiftIsHeld() {
        String renderer = read("src/main/java/uk/co/iceconchy/aerowarptics/client/render/RiftBrazierRenderer.java");
        int render = renderer.indexOf("public void render(");
        int dark = renderer.indexOf("AWRenderTypes.RIFT_SHARD", render);
        int light = renderer.indexOf("AWRenderTypes.RIFT_FIRE", render);
        assertTrue(render > 0 && dark > render && light > dark, "the fire is drawn before the dark it burns round");
        String client = read("src/main/java/uk/co/iceconchy/aerowarptics/client/ClientRuntime.java");
        String brazier = client.substring(client.indexOf("static void tickBrazier("));
        brazier = brazier.substring(0, brazier.indexOf("\n    }\n"));
        assertFalse(brazier.contains("RiftEffectManager"), "the brazier still holds a rift open over itself");
    }

    private static String read(String path) {
        try {
            return Files.readString(Path.of(path)).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
