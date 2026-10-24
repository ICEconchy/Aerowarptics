package uk.co.iceconchy.aerowarptics.client.fx;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.modulator.RiftModulatorTheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An aperture with a hull going through it.
 *
 * <p>Every way this goes wrong draws a ship where there should be a hole. The crew are sent the far
 * aperture for the first time by the cue that says they are already inside it, so an aperture opened
 * that way has to be whole - face and throat - on its first frame; and an aperture whose hold was the
 * server's forecast has to outlast a server that fell behind it, or it closes round a hull still in it.
 */
class RiftPassageTest {

    private static final int OPEN = 20;
    private static final int CLOSE = 20;
    /** Negative: an aperture a hull comes out of. */
    private static final float EXIT_THROAT = -30.0F;

    private static RiftEffectManager.ActiveRift rift(int hold, float throat) {
        return new RiftEffectManager.ActiveRift(new Vec3(100.5D, 90.0D, -40.5D), new Vec3(0.0D, 0.0D, 1.0D),
                8.0D, 0x8844FF, 0x8844FF, throat, RiftModulatorTheme.STANDARD, OPEN, hold, CLOSE);
    }

    /** One client tick's worth of the clock, as {@code onClientTick} runs it. */
    private static void tick(RiftEffectManager.ActiveRift rift) {
        rift.lastAge = rift.age;
        rift.age++;
    }

    /**
     * Opened by the cue that says a hull is already inside, as the crew's far aperture is: whole at once,
     * and its throat with it, because a ship is sitting in it.
     */
    @Test
    void anApertureOpenedForAPassageIsWholeAtOnce() {
        RiftEffectManager.ActiveRift exit = rift(0, EXIT_THROAT);
        exit.passage(30);
        assertEquals(1.0F, exit.aperture(0.0F), "the face must be fully open on the first frame");
        assertEquals(1.0F, exit.aperture(1.0F));
        assertEquals(1.0F, exit.throatOpen, "the throat must already be there round the hull");
        assertEquals(1.0F, exit.throatOpenLast, "and must not interpolate up from nothing");
        assertFalse(exit.isCollapsing());
    }

    /** Opened with no hold at all, it still stands for the whole of the passage it was opened for. */
    @Test
    void itStandsForTheWholePassage() {
        int passage = 30;
        RiftEffectManager.ActiveRift exit = rift(0, EXIT_THROAT);
        exit.passage(passage);
        for (int t = 0; t < passage; t++) {
            tick(exit);
            assertFalse(exit.isCollapsing(), "collapsing " + t + " ticks into a " + passage + "-tick passage");
            assertEquals(1.0F, exit.aperture(0.5F), 1.0e-6F);
        }
    }

    /**
     * The open cue's hold is a forecast. A server that fell behind it - a big hull's crossing, the far
     * ground loading - sends the passage late, and the aperture must stand for all of it anyway.
     */
    @Test
    void aPassageOutlastsAForecastThatRanShort() {
        int forecast = 60;
        RiftEffectManager.ActiveRift exit = rift(forecast, EXIT_THROAT);
        while (exit.age < OPEN + forecast - 5) {
            tick(exit);
        }
        int passage = 40;
        exit.passage(passage);
        for (int t = 0; t < passage; t++) {
            tick(exit);
            assertFalse(exit.isCollapsing(), "closed round a hull " + t + " ticks into its passage");
        }
    }

    /** Only the backstop moves. The cue that says the hull is out still ends it then and there. */
    @Test
    void aCloseStillEndsItWhenTheHullIsOut() {
        RiftEffectManager.ActiveRift exit = rift(0, EXIT_THROAT);
        exit.passage(30);
        for (int t = 0; t < 30; t++) {
            tick(exit);
        }
        exit.collapse();
        assertTrue(exit.isCollapsing());
        for (int t = 0; t < CLOSE; t++) {
            tick(exit);
        }
        assertTrue(exit.expired(), "a collapsed aperture must be gone once its close has run");
    }

    /** And the backstop is still there: an aperture whose close never arrives does not stand for ever. */
    @Test
    void anApertureNobodyClosesStillGoes() {
        int passage = 30;
        RiftEffectManager.ActiveRift exit = rift(0, EXIT_THROAT);
        exit.passage(passage);
        for (int t = 0; t < passage + RiftEffectManager.PASSAGE_SPARE_TICKS + CLOSE; t++) {
            tick(exit);
        }
        assertTrue(exit.expired());
    }

    /** A passage only ever lengthens a hold, never shortens one - a gate's stands for days. */
    @Test
    void aPassageNeverShortensAHold() {
        RiftEffectManager.ActiveRift entry = rift(500, 30.0F);
        entry.passage(10);
        assertEquals(500, entry.holdTicks);
    }
}
