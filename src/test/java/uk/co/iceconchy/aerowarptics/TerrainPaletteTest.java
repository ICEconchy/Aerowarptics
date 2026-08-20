package uk.co.iceconchy.aerowarptics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.MapColor;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.astrolabe.DestinationSurvey;
import uk.co.iceconchy.aerowarptics.astrolabe.TerrainPalette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the world becomes a pixel on either of the Astrolabe's maps.
 *
 * <p>Mostly here for the channel order. It is the kind of mistake that compiles, runs, looks like a
 * rendering problem and survives a review, because "the map is a funny colour" does not sound like a
 * one-line bug in a bitwise expression.
 */
class TerrainPaletteTest {

    private static int red(int argb) {
        return (argb >> 16) & 0xFF;
    }

    private static int green(int argb) {
        return (argb >> 8) & 0xFF;
    }

    private static int blue(int argb) {
        return argb & 0xFF;
    }

    // ---------------------------------------------------------------- colour

    /**
     * The bug this whole class exists to make impossible.
     *
     * <p>{@link MapColor#calculateRGBColor} returns the byte order vanilla's map texture wants, which
     * is ABGR. Read as ARGB - which is what every other colour in this mod is - it swaps red and blue,
     * and the result is a map with orange oceans that nobody thinks to blame on a shift.
     */
    @Test
    void grassIsGreenAndWaterIsBlue() {
        int grass = TerrainPalette.argb(MapColor.GRASS, MapColor.Brightness.NORMAL);
        assertTrue(green(grass) > red(grass) && green(grass) > blue(grass), "grass is not green");

        int water = TerrainPalette.argb(MapColor.WATER, MapColor.Brightness.NORMAL);
        assertTrue(blue(water) > red(water) && blue(water) > green(water), "water is not blue");

        // Specifically not what the vanilla helper hands back, so nobody "simplifies" this away.
        assertNotEquals(MapColor.WATER.calculateRGBColor(MapColor.Brightness.NORMAL), water);
    }

    @Test
    void nothingIsFullyTransparentRatherThanBlack() {
        assertEquals(0, TerrainPalette.argb(MapColor.NONE, MapColor.Brightness.NORMAL));
        assertEquals(0, TerrainPalette.argb((byte) 0));
    }

    @Test
    void aPackedColourSurvivesTheRoundTrip() {
        for (MapColor.Brightness brightness : MapColor.Brightness.values()) {
            byte packed = MapColor.STONE.getPackedId(brightness);
            assertEquals(TerrainPalette.argb(MapColor.STONE, brightness), TerrainPalette.argb(packed),
                    "stone at " + brightness);
        }
    }

    @Test
    void brightnessActuallyChangesTheColour() {
        int low = TerrainPalette.argb(MapColor.STONE, MapColor.Brightness.LOW);
        int high = TerrainPalette.argb(MapColor.STONE, MapColor.Brightness.HIGH);
        assertTrue(red(high) > red(low) && green(high) > green(low) && blue(high) > blue(low));
    }

    // ----------------------------------------------------------------- slope

    @Test
    void aRiseIsLitAndADropIsShadowed() {
        // Two blocks either way is past the dither on any parity, so this holds for every cell.
        assertEquals(MapColor.Brightness.HIGH, TerrainPalette.slope(66, 64, 1, 0, 0));
        assertEquals(MapColor.Brightness.HIGH, TerrainPalette.slope(66, 64, 1, 0, 1));
        assertEquals(MapColor.Brightness.LOW, TerrainPalette.slope(62, 64, 1, 0, 0));
        assertEquals(MapColor.Brightness.LOW, TerrainPalette.slope(62, 64, 1, 0, 1));
    }

    @Test
    void groundWithNothingToTheNorthIsShadedFlat() {
        assertEquals(MapColor.Brightness.NORMAL,
                TerrainPalette.slope(200, TerrainPalette.UNKNOWN_SURFACE, 1, 3, 7));
    }

    @Test
    void shallowsAreBrightAndDeepWaterIsDark() {
        assertEquals(MapColor.Brightness.HIGH, TerrainPalette.depth(1, 0, 0));
        assertEquals(MapColor.Brightness.LOW, TerrainPalette.depth(40, 0, 0));
    }

    // ---------------------------------------------------------------- survey

    /** A survey has to read back the cell that was written, or the picture is transposed. */
    @Test
    void aSurveyReadsBackRowMajorFromTheNorthWest() {
        int size = 2 * DestinationSurvey.RADIUS / DestinationSurvey.STEP + 1;
        byte[] colours = new byte[size * size];
        byte[] relief = new byte[size * size];
        colours[2 * size + 5] = MapColor.SAND.getPackedId(MapColor.Brightness.HIGH);
        relief[2 * size + 5] = 17;

        DestinationSurvey survey = new DestinationSurvey(DestinationSurvey.RADIUS, DestinationSurvey.STEP,
                BlockPos.ZERO, 64, colours, relief);

        assertEquals(size, survey.size());
        assertTrue(survey.known(5, 2));
        assertEquals(17, survey.relief(5, 2));
        assertEquals(TerrainPalette.argb(MapColor.SAND, MapColor.Brightness.HIGH), survey.colour(5, 2));
        // The transposed cell must be empty, which is what catches an x/z swap.
        assertFalse(survey.known(2, 5));
    }

    @Test
    void aSurveyIsSilentAboutCellsOutsideIt() {
        int size = 2 * DestinationSurvey.RADIUS / DestinationSurvey.STEP + 1;
        DestinationSurvey survey = new DestinationSurvey(DestinationSurvey.RADIUS, DestinationSurvey.STEP,
                BlockPos.ZERO, 64, new byte[size * size], new byte[size * size]);
        assertEquals(0, survey.colour(-1, 0));
        assertEquals(0, survey.colour(0, size));
        assertFalse(survey.known(size, size));
        assertEquals(0.0F, survey.coverage());
    }
}
