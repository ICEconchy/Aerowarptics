package uk.co.iceconchy.aerowarptics.weather;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** What a Stormglass reads, and when breaking one calls a storm. */
class StormForecastTest {

    @Test
    void theFourReadings() {
        assertEquals(StormForecast.Reading.RAGING, StormForecast.read(true, true, true, 0));
        assertEquals(StormForecast.Reading.GATHERING, StormForecast.read(false, true, true, 12_000));
        assertEquals(StormForecast.Reading.GATHERING, StormForecast.read(false, true, true, StormForecast.GATHERING_WINDOW));
        assertEquals(StormForecast.Reading.CLEAR, StormForecast.read(false, true, true, StormForecast.GATHERING_WINDOW + 1));
        assertEquals(StormForecast.Reading.STILL, StormForecast.read(false, false, true, 100));
        assertEquals(StormForecast.Reading.STILL, StormForecast.read(false, true, false, 100));
    }

    @Test
    void aCalmNotYetRolledReadsClear() {
        // A calm of zero means "not yet rolled", not "due now" - the same rule that stops a new world opening
        // with a storm on its first tick.
        assertEquals(StormForecast.Reading.CLEAR, StormForecast.read(false, true, true, 0));
    }

    @Test
    void aStormRagingUnderFrozenWeatherStillReadsRaging() {
        assertEquals(StormForecast.Reading.RAGING, StormForecast.read(true, false, false, 0));
    }

    @Test
    void theCallRules() {
        assertEquals(StormForecast.Call.CALLED, StormForecast.call(true, true, false, true, -1L, 24_000));
        assertEquals(StormForecast.Call.CALLED, StormForecast.call(true, true, false, true, 24_000L, 24_000));
        assertEquals(StormForecast.Call.RAGING, StormForecast.call(true, true, true, true, -1L, 24_000));
        assertEquals(StormForecast.Call.FROZEN, StormForecast.call(true, true, false, false, -1L, 24_000));
        assertEquals(StormForecast.Call.TOO_SOON, StormForecast.call(true, true, false, true, 100L, 24_000));
        // Forced off when natural storms are off: the operator chose no storms, and a glass does not overrule it.
        assertEquals(StormForecast.Call.FORBIDDEN, StormForecast.call(true, false, false, true, -1L, 24_000));
        assertEquals(StormForecast.Call.FORBIDDEN, StormForecast.call(false, true, false, true, -1L, 24_000));
    }

    @Test
    void readingsSurviveTheWireByOrdinal() {
        for (StormForecast.Reading reading : StormForecast.Reading.values()) {
            assertEquals(reading, StormForecast.Reading.byOrdinal(reading.ordinal()));
        }
        assertEquals(StormForecast.Reading.CLEAR, StormForecast.Reading.byOrdinal(99));
    }
}
