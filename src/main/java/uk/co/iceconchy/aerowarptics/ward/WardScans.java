package uk.co.iceconchy.aerowarptics.ward;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.astrolabe.DestinationSurvey;
import uk.co.iceconchy.aerowarptics.plotter.PlotterSurvey;
import uk.co.iceconchy.aerowarptics.trace.Trace;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What a Rift Ward does to a stranger's picture of the ground under its field: gives them noise in place of it.
 *
 * <p>Two things look at ground nobody is standing on - a Rift Probe's sounding and a Rift Plotter's table - and
 * either would otherwise show an attacker the inside of a base before a ship or a shell was sent at it. A column
 * inside a standing field comes back as interference to anyone the field would turn aside: a colour and a height
 * out of {@link WardRules#interference}, which is the same every time the same column is asked for, so scanning
 * twice tells nothing the first scan did not. Ground outside the field is untouched, and so is everything for the
 * ward's owner and its network. An Astrolabe's preview of an anchor is not asked here at all: a chart only lists
 * anchors its reader may use, and an anchor is an invitation.
 *
 * <p>It gives a ward away to anyone who scouts first, which is meant: the surprise is for the pilot who did not
 * look. The ground's height at the very middle of a probe's reading is left as it is - it is one number, and the
 * fix a probe hands a drive has to be a real place.
 */
public final class WardScans {

    /** The colours interference is drawn in, darkest first: the fields' own violets, and no ground's. */
    private static final MapColor[] STATIC = {
            MapColor.COLOR_BLACK, MapColor.COLOR_PURPLE, MapColor.TERRACOTTA_PURPLE, MapColor.COLOR_MAGENTA};

    /** Blocks either side of a probe's ground that its interference pretends to. */
    private static final int RELIEF = 14;

    private WardScans() {
    }

    /**
     * A Rift Probe's sounding, as whoever threw it is allowed to see it. The wards whose fields it read pay for
     * the scrambling.
     */
    public static DestinationSurvey probe(ServerLevel level, DestinationSurvey survey, @Nullable UUID who) {
        List<WardField.Circle> fields = Wards.scrambling(level, who);
        if (fields.isEmpty()) {
            return survey;
        }
        List<WardField.Circle> touched = new ArrayList<>();
        DestinationSurvey out = scramble(survey, fields, seed(level), touched);
        if (!touched.isEmpty()) {
            Trace.log(Trace.WARD, "{}'s sounding at {} read ground inside {} field(s): scrambled", Trace.who(who),
                    Trace.pos(survey.centre()), touched.size());
            Wards.scanned(level, touched);
        }
        return out;
    }

    /** A Rift Plotter's table, as whoever is at it is allowed to see it. */
    public static PlotterSurvey table(ServerLevel level, PlotterSurvey survey, @Nullable UUID who) {
        List<WardField.Circle> fields = Wards.scrambling(level, who);
        if (fields.isEmpty()) {
            return survey;
        }
        List<WardField.Circle> touched = new ArrayList<>();
        PlotterSurvey out = scramble(survey, fields, seed(level), touched);
        if (!touched.isEmpty()) {
            Trace.log(Trace.WARD, "{}'s plotting table at ({}, {}) read ground inside {} field(s): scrambled",
                    Trace.who(who), survey.centreX(), survey.centreZ(), touched.size());
            Wards.scanned(level, touched);
        }
        return out;
    }

    private static long seed(ServerLevel level) {
        return level.getSeed() ^ 0x57_41_52_44_53L;
    }

    // ------------------------------------------------------------------ the arithmetic

    /**
     * A sounding with every column inside one of some fields replaced by interference.
     *
     * @param touched filled with the fields that had a column read in them; left empty, the survey handed back
     *                is the one handed in
     */
    public static DestinationSurvey scramble(DestinationSurvey survey, List<WardField.Circle> fields, long seed,
                                             List<WardField.Circle> touched) {
        int size = 2 * survey.radius() / Math.max(1, survey.step()) + 1;
        if (survey.colours().length != size * size) {
            return survey;
        }
        byte[] colours = survey.colours().clone();
        byte[] relief = survey.relief().clone();
        int originX = survey.centre().getX() - survey.radius();
        int originZ = survey.centre().getZ() - survey.radius();
        for (int row = 0; row < size; row++) {
            int z = originZ + row * survey.step();
            for (int column = 0; column < size; column++) {
                int x = originX + column * survey.step();
                WardField.Circle field = holding(fields, x, z);
                if (field == null) {
                    continue;
                }
                note(touched, field);
                int index = row * size + column;
                colours[index] = noise(x, z, seed);
                relief[index] = (byte) WardRules.interference(x, z, seed, -RELIEF, 2 * RELIEF + 1);
            }
        }
        return touched.isEmpty() ? survey
                : new DestinationSurvey(survey.radius(), survey.step(), survey.centre(), survey.groundY(), colours, relief);
    }

    /** A plotter's table with every column inside one of some fields replaced by interference. */
    public static PlotterSurvey scramble(PlotterSurvey survey, List<WardField.Circle> fields, long seed,
                                         List<WardField.Circle> touched) {
        int size = survey.size();
        byte[] colours = survey.colours().clone();
        short[] heights = survey.heights().clone();
        int[] span = survey.span();
        int low = span[0] - 4;
        int reach = Math.max(8, span[1] - span[0] + 9);
        for (int row = 0; row < size; row++) {
            int z = survey.worldZ(row);
            for (int column = 0; column < size; column++) {
                int x = survey.worldX(column);
                WardField.Circle field = holding(fields, x, z);
                if (field == null) {
                    continue;
                }
                note(touched, field);
                int index = row * size + column;
                colours[index] = noise(x, z, seed);
                heights[index] = (short) WardRules.interference(x, z, seed, low, reach);
            }
        }
        return touched.isEmpty() ? survey
                : new PlotterSurvey(survey.centreX(), survey.centreZ(), survey.radius(), survey.groundY(), colours, heights);
    }

    @Nullable
    private static WardField.Circle holding(List<WardField.Circle> fields, int x, int z) {
        for (WardField.Circle field : fields) {
            if (field.contains(x + 0.5D, z + 0.5D)) {
                return field;
            }
        }
        return null;
    }

    private static void note(List<WardField.Circle> touched, WardField.Circle field) {
        if (!touched.contains(field)) {
            touched.add(field);
        }
    }

    /** A column's interference as a packed map colour: one of a few violets, at one of a map's four brightnesses. */
    private static byte noise(int x, int z, long seed) {
        int pick = WardRules.interference(x, z, seed ^ 0x9E37L, 0, STATIC.length * 4);
        return STATIC[pick / 4].getPackedId(MapColor.Brightness.values()[pick % 4]);
    }
}
