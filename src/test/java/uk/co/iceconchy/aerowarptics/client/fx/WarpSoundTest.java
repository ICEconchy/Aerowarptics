package uk.co.iceconchy.aerowarptics.client.fx;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The warp's sounds a player can switch off, and the wind-up among them.
 *
 * <p>Neither half fails loudly. A name in the switch that is not a sound the mod ships simply never
 * matches, and the sound it was meant to cover goes on playing for a player who turned it off. A pitch
 * past what the sound engine plays is clamped without a word, and the wind-up stops climbing part-way.
 */
class WarpSoundTest {

    private static final Path SOUNDS = Path.of("src/main/resources/assets/aerowarptics/sounds.json");

    private static JsonObject sounds() {
        try (Reader reader = Files.newBufferedReader(SOUNDS, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------ the switch

    @Test
    void everySoundTheSwitchNamesIsOneTheModShips() {
        JsonObject sounds = sounds();
        for (String name : WarpSoundSwitch.names()) {
            assertTrue(sounds.has(name), "the warp sound switch names " + name + ", which sounds.json does not define");
        }
    }

    @Test
    void theSwitchCoversTheWholeOfAWarpAndTheWindUp() {
        for (String name : new String[]{"drive_charging", "drive_charged", "destination_lock", "stabilizing",
                "drive_spin_up", "rift_open", "warp_travel", "warp_exit", "warp_failed", "warp_scatter",
                "drive_cooldown"}) {
            assertTrue(WarpSoundSwitch.covers("aerowarptics", name), name);
        }
    }

    @Test
    void theSwitchLeavesEveryOtherSoundAlone() {
        // A fleet has to be heard becoming ready, and a cradle is not a warp.
        for (String name : new String[]{"flotilla_horn", "flotilla_bell", "drive_hum_mk_i", "collapse_boom",
                "anchor_configured"}) {
            assertFalse(WarpSoundSwitch.covers("aerowarptics", name), name);
        }
        // Another mod's sound, or the game's, that happens to share a name is not this mod's to drop.
        assertFalse(WarpSoundSwitch.covers("minecraft", "rift_open"));
        assertFalse(WarpSoundSwitch.covers("create", "warp_travel"));
    }

    // ------------------------------------------------------------ the wind-up

    @Test
    void theWindUpOnlyEverClimbs() {
        float previousPitch = SpinUpVoice.pitch(0.0F, 0.0F);
        float previousVolume = SpinUpVoice.volume(0.0F);
        for (int step = 1; step < 1000; step++) {
            float progress = step / 1000.0F;
            float pitch = SpinUpVoice.pitch(progress, step);
            float volume = SpinUpVoice.volume(progress);
            assertTrue(pitch >= previousPitch, "the pitch fell at " + progress);
            assertTrue(volume >= previousVolume, "the volume fell at " + progress);
            previousPitch = pitch;
            previousVolume = volume;
        }
        // And it is a climb worth the name: well over an octave from a standing start to full spin.
        assertTrue(SpinUpVoice.pitch(1.0F, 0.0F) > SpinUpVoice.pitch(0.0F, 0.0F) * 2.5F);
    }

    @Test
    void thePitchStaysInsideWhatTheSoundEngineWillPlay() {
        for (int step = 0; step <= 100; step++) {
            for (float ticks = 0.0F; ticks < 400.0F; ticks += 0.37F) {
                float pitch = SpinUpVoice.pitch(step / 100.0F, ticks);
                assertTrue(pitch > SpinUpVoice.PITCH_FLOOR && pitch < SpinUpVoice.PITCH_CEILING,
                        "a pitch of " + pitch + " would be clamped");
            }
        }
    }

    @Test
    void aDriveHeldAtFullSpinWaversAndOneStillWindingDoesNot() {
        float low = Float.MAX_VALUE;
        float high = -Float.MAX_VALUE;
        for (int ticks = 0; ticks < 200; ticks++) {
            float pitch = SpinUpVoice.pitch(1.0F, ticks);
            low = Math.min(low, pitch);
            high = Math.max(high, pitch);
            // Part-wound, the clock does nothing: the climb is the only movement.
            assertEquals(SpinUpVoice.pitch(0.6F, 0.0F), SpinUpVoice.pitch(0.6F, ticks), 0.0F);
        }
        assertTrue(high - low > 0.03F, "a held drive's voice is dead still");
        assertTrue(high - low < 0.2F, "a held drive's voice wobbles like a siren");
    }

    @Test
    void nonsenseInIsADriveThatHasNotStarted() {
        assertEquals(SpinUpVoice.pitch(0.0F, 0.0F), SpinUpVoice.pitch(Float.NaN, 0.0F), 0.0F);
        assertEquals(SpinUpVoice.pitch(0.0F, 0.0F), SpinUpVoice.pitch(-4.0F, 0.0F), 0.0F);
        assertEquals(SpinUpVoice.volume(1.0F), SpinUpVoice.volume(9.0F), 0.0F);
        assertTrue(SpinUpVoice.volume(0.0F) > 0.0F, "a wind-up that starts silent is skipped by the sound engine");
    }
}
