package uk.co.iceconchy.aerowarptics.detector;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Spatial Wave Detector hears what is in range, grades it honestly, and always has a model to show.
 *
 * <p>Each of these fails quietly in game rather than loudly: a reading of nought at the edge of range
 * reads as a detector that did not notice, a signal that climbs with distance reads as a wiring fault,
 * and a signal strength with no blockstate variant is a purple-and-black cube that only appears when
 * something arrives at exactly that distance.
 */
class WaveDetectorTest {

    private static final Path BLOCKSTATE =
            Path.of("src/main/resources/assets/aerowarptics/blockstates/spatial_wave_detector.json");

    @Test
    void aPointInsideTheBoxIsNoDistanceAtAll() {
        assertEquals(0.0D, WaveReach.distanceToBox(5, 5, 5, 0, 0, 0, 10, 10, 10), 1e-9);
    }

    @Test
    void distanceIsToTheNearestPartOfTheBoxNotItsMiddle() {
        // A long hull whose end is three blocks away has arrived three blocks away, however far off
        // its middle is - which is the whole reason arrivals are measured as boxes.
        assertEquals(3.0D, WaveReach.distanceToBox(-3, 5, 5, 0, 0, 0, 200, 10, 10), 1e-9);
        // Off a corner, it is the straight line to the corner.
        assertEquals(5.0D, WaveReach.distanceToBox(13, 14, 5, 0, 0, 0, 10, 10, 10), 1e-9);
    }

    @Test
    void fullStrengthOnTopOfItAndOneAtTheEdge() {
        assertEquals(15, WaveReach.strength(0.0D, 32));
        assertEquals(1, WaveReach.strength(32.0D, 32));
        assertEquals(0, WaveReach.strength(32.001D, 32));
    }

    @Test
    void theSignalNeverRisesWithDistanceAndNeverVanishesInsideRange() {
        for (int range = 1; range <= SpatialWaveDetectorBlockEntity.MAX_RANGE; range++) {
            int previous = WaveReach.FULL;
            for (double distance = 0.0D; distance <= range; distance += 0.25D) {
                int strength = WaveReach.strength(distance, range);
                assertTrue(strength >= 1 && strength <= WaveReach.FULL,
                        "range " + range + " at " + distance + " gave " + strength);
                assertTrue(strength <= previous, "range " + range + " rose at " + distance);
                previous = strength;
            }
        }
    }

    @Test
    void nothingIsHeardWithoutARangeOrAMeaningfulDistance() {
        assertEquals(0, WaveReach.strength(0.0D, 0));
        assertEquals(0, WaveReach.strength(Double.NaN, 32));
        assertEquals(0, WaveReach.strength(-1.0D, 32));
    }

    @Test
    void everySignalStrengthHasAModel() throws IOException {
        JsonObject variants;
        try (Reader reader = Files.newBufferedReader(BLOCKSTATE, StandardCharsets.UTF_8)) {
            variants = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("variants");
        }
        for (int power = 0; power <= WaveReach.FULL; power++) {
            JsonObject variant = variants.getAsJsonObject("power=" + power);
            assertTrue(variant != null, "no model for power=" + power);
            String model = variant.get("model").getAsString();
            // Quiet at nought and lit at anything else - the lit look is the signal.
            assertEquals(power == 0 ? "aerowarptics:block/spatial_wave_detector"
                    : "aerowarptics:block/spatial_wave_detector_on", model, "power=" + power);
        }
    }
}
