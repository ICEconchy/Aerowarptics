package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.client.fx.RiftLightning;
import uk.co.iceconchy.aerowarptics.client.fx.RiftShatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A spark of lightning discharging off a rift.
 *
 * <p>The failures worth guarding here are the silent ones: a field that never actually flashes reads
 * as a feature quietly doing nothing, and a bolt that draws the same path every time it fires reads as
 * a decal rather than as lightning. Neither would show up as an exception - just as an aperture that
 * looks exactly as it did before this existed.
 */
class RiftLightningTest {

    private static int[] seeds() {
        int[] seeds = new int[16];
        for (int i = 0; i < seeds.length; i++) {
            seeds[i] = RiftShatter.seedFor(i * 11.5D, 70.0D - i, i * 23.0D);
        }
        return seeds;
    }

    // ------------------------------------------------------------ the field

    @Test
    void oneRiftAlwaysHasTheSameEmittersInIt() {
        int seed = RiftShatter.seedFor(200.0D, 64.0D, -50.0D);
        RiftLightning.Emitter[] first = RiftLightning.apertureField(seed);
        RiftLightning.Emitter[] second = RiftLightning.apertureField(seed);
        assertEquals(RiftLightning.APERTURE_BOLTS, first.length);
        for (int i = 0; i < first.length; i++) {
            assertEquals(first[i], second[i], "the same rift sparked differently at " + i);
        }
    }

    @Test
    void apertureAndCorridorFieldsDoNotShareEmitters() {
        // If the two ever came out equal the corridor bolts would flash in lockstep with the aperture
        // ones instead of on their own clocks, which is the exact "synchronised pulse" this whole
        // per-emitter-clock design exists to avoid.
        int seed = RiftShatter.seedFor(12.0D, 90.0D, 12.0D);
        RiftLightning.Emitter[] aperture = RiftLightning.apertureField(seed);
        RiftLightning.Emitter[] corridor = RiftLightning.corridorField(seed);
        assertTrue(aperture.length > 0 && corridor.length > 0);
        boolean anyDifferent = false;
        int shared = Math.min(aperture.length, corridor.length);
        for (int i = 0; i < shared; i++) {
            if (!aperture[i].equals(corridor[i])) {
                anyDifferent = true;
                break;
            }
        }
        assertTrue(anyDifferent, "the aperture and corridor fields were identical");
    }

    // ----------------------------------------------------------------- flash

    @Test
    void everyEmitterActuallyFlashesSometimes() {
        // A field where nothing ever fires is a config toggle that quietly does nothing.
        for (int seed : seeds()) {
            for (RiftLightning.Emitter emitter : RiftLightning.apertureField(seed)) {
                boolean fired = false;
                for (float tick = 0.0F; tick < 400.0F; tick += 1.0F) {
                    if (RiftLightning.flash(emitter, tick) >= 0.0F) {
                        fired = true;
                        break;
                    }
                }
                assertTrue(fired, "an emitter never flashed across four hundred ticks");
            }
        }
    }

    @Test
    void aFlashStaysWithinItsOwnLife() {
        for (int seed : seeds()) {
            for (RiftLightning.Emitter emitter : RiftLightning.apertureField(seed)) {
                for (float tick = 0.0F; tick < 500.0F; tick += 0.5F) {
                    float progress = RiftLightning.flash(emitter, tick);
                    assertTrue(progress < 0.0F || (progress >= 0.0F && progress < 1.0F),
                            "a flash's own progress left [0, 1) at tick " + tick + ": " + progress);
                }
            }
        }
    }

    @Test
    void theFieldIsScatteredRatherThanSynchronised() {
        // Read the whole field at a handful of ticks and some should be dark while others flash - a
        // field that always agrees is one emitter with extra steps.
        RiftLightning.Emitter[] emitters = RiftLightning.apertureField(RiftShatter.seedFor(4.0D, 70.0D, 4.0D));
        boolean sawBothAtOnce = false;
        for (float tick = 0.0F; tick < 400.0F && !sawBothAtOnce; tick += 1.0F) {
            boolean anyDark = false;
            boolean anyLit = false;
            for (RiftLightning.Emitter emitter : emitters) {
                if (RiftLightning.flash(emitter, tick) < 0.0F) {
                    anyDark = true;
                } else {
                    anyLit = true;
                }
            }
            sawBothAtOnce = anyDark && anyLit;
        }
        assertTrue(sawBothAtOnce, "the field never had some emitters dark while others were lit");
    }

    // ---------------------------------------------------------------- cycle

    @Test
    void differentCyclesDrawDifferentPaths() {
        // The whole mechanism behind a bolt looking new every time it fires: two different cycle
        // numbers for the same emitter and the same point of the path must not agree.
        int seed = RiftShatter.seedFor(7.0D, 65.0D, -7.0D);
        boolean anyDifferent = false;
        for (int cycleNumber = 0; cycleNumber < 20; cycleNumber++) {
            float a = RiftLightning.jitter(seed, 0, cycleNumber, 1);
            float b = RiftLightning.jitter(seed, 0, cycleNumber + 1, 1);
            if (Math.abs(a - b) > 1.0e-4F) {
                anyDifferent = true;
                break;
            }
        }
        assertTrue(anyDifferent, "consecutive flashes drew the same path");
    }

    @Test
    void jitterStaysWithinItsStatedRange() {
        int seed = RiftShatter.seedFor(1.0D, 64.0D, 1.0D);
        for (int emitterIndex = 0; emitterIndex < 8; emitterIndex++) {
            for (int cycleNumber = 0; cycleNumber < 40; cycleNumber++) {
                for (int point = 0; point <= RiftLightning.SEGMENTS; point++) {
                    float value = RiftLightning.jitter(seed, emitterIndex, cycleNumber, point);
                    assertTrue(value >= -1.0F && value <= 1.0F, "jitter left [-1, 1]: " + value);
                }
            }
        }
    }

    @Test
    void theSameFlashAlwaysDrawsTheSamePath() {
        // Two viewers watching one rift have to draw the same bolt, or the two clients disagree about
        // what a flash they are both looking at looks like.
        int seed = RiftShatter.seedFor(3.0D, 66.0D, 9.0D);
        for (int point = 0; point <= RiftLightning.SEGMENTS; point++) {
            assertEquals(RiftLightning.jitter(seed, 2, 5, point), RiftLightning.jitter(seed, 2, 5, point));
        }
    }

    // ------------------------------------------------------------ brightness

    @Test
    void brightnessFadesFromFullToNothing() {
        assertEquals(1.0F, RiftLightning.brightness(0.0F), 1.0e-6F, "a flash did not start at full brightness");
        assertEquals(0.0F, RiftLightning.brightness(1.0F), 1.0e-6F, "a flash did not fade to nothing");
        assertEquals(0.0F, RiftLightning.brightness(-1.0F), "a dark emitter reported brightness");

        float previous = 1.0F;
        for (float progress = 0.05F; progress <= 1.0F; progress += 0.05F) {
            float brightness = RiftLightning.brightness(progress);
            assertTrue(brightness <= previous, "brightness rose partway through a flash");
            previous = brightness;
        }
    }
}
