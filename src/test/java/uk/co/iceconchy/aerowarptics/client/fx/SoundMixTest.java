package uk.co.iceconchy.aerowarptics.client.fx;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How loud the mod's sounds are let out.
 *
 * <p>None of it fails loudly. A volume past one is clamped by the sound engine without a word, so a sound
 * that was only scaled plays exactly as loud as before and nobody is told; and a caller that multiplies by
 * the setting itself is simply turned down twice.
 */
class SoundMixTest {

    private static final Path FX = Path.of("src/main/java/uk/co/iceconchy/aerowarptics/client/fx");

    @Test
    void everySoundIsQuieterThanItWasAskedFor() {
        for (float asked : new float[]{0.25F, 0.6F, 1.0F, 1.3F, 3.0F, 8.0F}) {
            float gain = SoundMix.gain(asked, 1.0D);
            assertTrue(gain < Math.min(asked, 1.0F), asked + " is played at " + gain);
            assertTrue(gain > 0.0F, asked + " is not played at all");
        }
    }

    @Test
    void aSoundAskedForPastOneIsNoLouderThanOneAskedForAtOne() {
        // Past one is reach. Scaling it instead leaves three at one and a half, which the engine clamps
        // straight back to full.
        assertEquals(SoundMix.gain(1.0F, 1.0D), SoundMix.gain(3.0F, 1.0D), 0.0F);
        assertEquals(SoundMix.gain(1.0F, 1.0D), SoundMix.gain(8.0F, 1.0D), 0.0F);
    }

    @Test
    void aSoundCarriesAsFarAsItDidBefore() {
        // The engine fades a sound over its distance times its volume, or times one if that is more.
        for (float asked : new float[]{0.25F, 1.0F, 1.3F, 3.0F, 8.0F}) {
            for (int attenuation : new int[]{16, 32, 48}) {
                float before = Math.max(asked, 1.0F) * attenuation;
                float after = Math.max(SoundMix.gain(asked, 1.0D), 1.0F) * SoundMix.reach(attenuation, asked);
                assertEquals(before, after, 0.5F, asked + " over " + attenuation);
            }
        }
    }

    @Test
    void theSettingTurnsItDownToNothingAndUpToWhatItWas() {
        assertEquals(0.0F, SoundMix.gain(1.0F, 0.0D), 0.0F);
        assertEquals(0.0F, SoundMix.level(0.0D), 0.0F);
        // The top of the setting's range undoes the trim, and no more: nothing is ever past full.
        assertEquals(1.0F, SoundMix.gain(1.0F, 2.0D), 1.0e-6F);
        assertEquals(0.6F, SoundMix.gain(0.6F, 2.0D), 1.0e-6F);
        assertEquals(1.0F, SoundMix.gain(8.0F, 2.0D), 1.0e-6F);
        assertTrue(SoundMix.gain(0.5F, 0.5D) < SoundMix.gain(0.5F, 1.0D));
    }

    @Test
    void nonsenseInIsSilence() {
        assertEquals(0.0F, SoundMix.gain(Float.NaN, 1.0D), 0.0F);
        assertEquals(0.0F, SoundMix.gain(-1.0F, 1.0D), 0.0F);
        assertEquals(0.0F, SoundMix.gain(1.0F, Double.NaN), 0.0F);
        assertEquals(0.0F, SoundMix.level(-3.0D), 0.0F);
        assertEquals(16, SoundMix.reach(16, Float.NaN));
    }

    @Test
    void onlyTheModsOwnSoundsAreTurnedDown() {
        assertTrue(SoundMix.covers("aerowarptics"));
        assertFalse(SoundMix.covers("minecraft"));
        assertFalse(SoundMix.covers("create"));
    }

    /**
     * The setting is read where a sound enters the engine, so anything that reads it again is turned down
     * twice. The two that may are the storm's thunder and rain - the game's sounds, which the filter does
     * not cover - and the cradle's show, for the two it borrows.
     */
    @Test
    void nothingElseMultipliesByTheSettingItself() throws IOException {
        List<String> may = List.of("TrimmedSound.java", "RiftStormSky.java", "RiftStormRain.java",
                "CollapseSpectacle.java");
        try (Stream<Path> files = Files.walk(FX)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                boolean reads = Files.readString(file, StandardCharsets.UTF_8).contains("EFFECT_VOLUME");
                assertEquals(may.contains(file.getFileName().toString()), reads,
                        file.getFileName() + " and the effectVolume setting");
            }
        }
    }

    @Test
    void theFilterLeavesALoopToTrimItself() throws IOException {
        String filter = Files.readString(FX.resolve("WarpSoundFilter.java"), StandardCharsets.UTF_8);
        assertTrue(filter.contains("instanceof TickableSoundInstance"),
                "a wrapped loop is no longer ticked: it never moves, changes pitch or stops");
        for (String loop : new String[]{"FlotillaHum.java", "DriveSpinUpSound.java", "PlotterEffects.java",
                "WardEffects.java", "CollapseSpectacle.java"}) {
            assertTrue(Files.readString(FX.resolve(loop), StandardCharsets.UTF_8).contains("TrimmedSound.level()"),
                    loop + " has a loop the filter does not wrap, and it does not trim itself");
        }
    }
}
