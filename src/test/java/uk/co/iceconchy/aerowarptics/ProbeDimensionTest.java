package uk.co.iceconchy.aerowarptics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.network.ClientboundProbePacket;
import uk.co.iceconchy.aerowarptics.network.ServerboundProbePacket;
import uk.co.iceconchy.aerowarptics.probe.ProbeAim;
import uk.co.iceconchy.aerowarptics.probe.ProbeBearing;
import uk.co.iceconchy.aerowarptics.probe.ProbeDimensions;
import uk.co.iceconchy.aerowarptics.warp.ArrivalHeight;
import uk.co.iceconchy.aerowarptics.warp.WarpCourse;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Rift Probe aimed at a dimension it is not in.
 *
 * <p>Everything here is about one way of being quietly wrong: the same three numbers mean a different
 * place in every dimension, so a fix that loses track of which one it was read in still flies - to
 * somewhere nobody looked at. Nothing throws. The ship simply arrives over the wrong map.
 */
class ProbeDimensionTest {

    private static final ResourceKey<Level> OVERWORLD = dimension("minecraft:overworld");
    private static final ResourceKey<Level> NETHER = dimension("minecraft:the_nether");
    private static final ResourceKey<Level> END = dimension("minecraft:the_end");

    /** A world border nobody has moved. */
    private static final double EDGE = 2.9999984E7D;

    private static ResourceKey<Level> dimension(String id) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(id));
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);
    }

    private static BlockPos aim(double x, double y, double z, double scale, ProbeBearing bearing, int range) {
        return ProbeAim.target(x, y, z, scale, bearing, range, -EDGE, EDGE, -EDGE, EDGE, -64, 320);
    }

    // -------------------------------------------------------------------- aim

    /** In its own dimension a sounding lands exactly where it did before there was a choice. */
    @Test
    void aSoundingInItsOwnDimensionLandsWhereItAlwaysDid() {
        for (ProbeBearing bearing : ProbeBearing.values()) {
            BlockPos expected = BlockPos.containing(100.5D + bearing.dx() * 3_000, 80.0D,
                    -40.5D + bearing.dz() * 3_000);
            assertEquals(expected, aim(100.5D, 80.0D, -40.5D, 1.0D, bearing, 3_000), bearing.toString());
        }
    }

    /**
     * Thrown into the Nether, a sounding starts from where a portal under the ship would come out.
     *
     * <p>Left unscaled, a ship eight thousand blocks out in the Overworld would sound the Nether eight
     * thousand blocks out too - sixty-four thousand blocks of Overworld from where it is.
     */
    @Test
    void aSoundingIntoAnotherDimensionStartsFromThisPlaceOverThere() {
        assertEquals(new BlockPos(1_500, 70, -200),
                aim(8_000.0D, 70.0D, -1_600.0D, 0.125D, ProbeBearing.EAST, 500));
        assertEquals(new BlockPos(800, 60, 300),
                aim(100.0D, 60.0D, 100.0D, 8.0D, ProbeBearing.NORTH, 500));
    }

    /** The range is blocks of the ground being sounded, whichever way it is thrown and whatever the scale. */
    @Test
    void theRangeIsMeasuredInTheDimensionBeingSounded() {
        for (double scale : new double[]{0.125D, 1.0D, 8.0D}) {
            for (ProbeBearing bearing : ProbeBearing.values()) {
                BlockPos target = aim(-4_000.0D, 64.0D, 12_000.0D, scale, bearing, 2_500);
                double travelled = Math.hypot(target.getX() + 0.5D - -4_000.0D * scale,
                        target.getZ() + 0.5D - 12_000.0D * scale);
                assertEquals(2_500.0D, travelled, 1.5D, bearing + " at a scale of " + scale);
            }
        }
    }

    /** A sounding thrown past the far world's border reads its edge, not nothing. */
    @Test
    void aSoundingPastTheBorderReadsTheEdge() {
        BlockPos east = ProbeAim.target(0.0D, 64.0D, 0.0D, 1.0D, ProbeBearing.EAST, 5_000,
                -1_000.0D, 1_000.0D, -1_000.0D, 1_000.0D, -64, 320);
        assertEquals(999, east.getX(), "the last block inside a border at 1,000");
        assertEquals(0, east.getZ());

        // A border that is not centred on the origin, which the old clamp could not see.
        BlockPos west = ProbeAim.target(5_000.0D, 64.0D, 5_000.0D, 1.0D, ProbeBearing.WEST, 9_000,
                4_000.0D, 6_000.0D, 4_000.0D, 6_000.0D, -64, 320);
        assertEquals(4_000, west.getX());
        assertEquals(5_000, west.getZ());
    }

    /** The height is only where the probe happened to be, but it has to be a height the far side has. */
    @Test
    void theHeightIsBroughtInsideTheSoundedDimension() {
        assertEquals(255, ProbeAim.target(0.0D, 300.0D, 0.0D, 0.125D, ProbeBearing.NORTH, 600,
                -EDGE, EDGE, -EDGE, EDGE, 0, 256).getY());
        assertEquals(0, ProbeAim.target(0.0D, -40.0D, 0.0D, 0.125D, ProbeBearing.NORTH, 600,
                -EDGE, EDGE, -EDGE, EDGE, 0, 256).getY());
        assertEquals(90, ProbeAim.target(0.0D, 90.7D, 0.0D, 0.125D, ProbeBearing.NORTH, 600,
                -EDGE, EDGE, -EDGE, EDGE, 0, 256).getY());
    }

    // ------------------------------------------------------------- dimensions

    /**
     * Out of the box a probe is offered the vanilla three and nothing a mod keeps for itself.
     *
     * <p>A probe reaches a dimension nobody has walked into, so "every dimension the server has" has to
     * be something its owner asked for.
     */
    @Test
    void theDefaultListIsTheVanillaThree() {
        List<? extends String> defaults = AWConfig.PROBE_DIMENSIONS.getDefault();
        for (ResourceKey<Level> vanilla : List.of(OVERWORLD, NETHER, END)) {
            assertTrue(ProbeDimensions.listed(defaults, vanilla.location()), vanilla.location() + " is not listed");
        }
        assertFalse(ProbeDimensions.listed(defaults, ResourceLocation.parse("compactmachines:compact_world")));
        assertFalse(defaults.contains(ProbeDimensions.EVERY), "the default offers every dimension there is");
    }

    @Test
    void theListNamesDimensionsOrEveryDimension() {
        ResourceLocation twilight = ResourceLocation.parse("twilightforest:twilight_forest");
        assertTrue(ProbeDimensions.listed(List.of("minecraft:the_end", " twilightforest:twilight_forest "), twilight),
                "a space round an id in a config file should not unlist it");
        assertTrue(ProbeDimensions.listed(List.of(ProbeDimensions.EVERY), twilight));
        assertFalse(ProbeDimensions.listed(List.of(), twilight), "an empty list keeps a probe where it is");
        assertFalse(ProbeDimensions.listed(List.of("twilightforest:twilight"), twilight),
                "half an id is not the id");
    }

    /** The probe's own dimension leads, whatever its id, and the rest read the same every time. */
    @Test
    void theProbesOwnDimensionComesFirstAndTheRestByName() {
        List<ResourceKey<Level>> ordered = ProbeDimensions.ordered(NETHER,
                List.of(END, dimension("aether:the_aether"), OVERWORLD, NETHER, END));
        assertEquals(List.of(NETHER, dimension("aether:the_aether"), OVERWORLD, END), ordered);
        assertEquals(List.of(OVERWORLD), ProbeDimensions.ordered(OVERWORLD, List.of()),
                "a probe kept to its own dimension is still offered that one");
    }

    /** Never more than a panel packet will carry, and the probe's own dimension is never the one cut. */
    @Test
    void theListIsCappedToWhatAPanelIsSent() {
        List<ResourceKey<Level>> many = new ArrayList<>();
        for (int index = 0; index < ProbeDimensions.MAX_LISTED * 2; index++) {
            many.add(dimension("pockets:room_" + String.format("%03d", index)));
        }
        List<ResourceKey<Level>> ordered = ProbeDimensions.ordered(dimension("zzz:last_by_name"), many);
        assertEquals(ProbeDimensions.MAX_LISTED, ordered.size());
        assertEquals(dimension("zzz:last_by_name"), ordered.getFirst());
    }

    @Test
    void aDimensionIsNamedFromItsId() {
        assertEquals("Overworld", ProbeDimensions.plainName(OVERWORLD.location()));
        assertEquals("The Nether", ProbeDimensions.plainName(NETHER.location()));
        assertEquals("Twilight Forest",
                ProbeDimensions.plainName(ResourceLocation.parse("twilightforest:twilight_forest")));
        assertEquals("Deep Dark", ProbeDimensions.plainName(ResourceLocation.parse("pockets:caves/deep_dark")),
                "only the last part of a path is the dimension's name");
    }

    /**
     * A course into another dimension is labelled with it, and the label still fits where it is sent.
     *
     * <p>A course's label travels under a sixty-four character limit that throws rather than trims, so
     * a dimension with an essay for an id would otherwise take the drive's console down with it.
     */
    @Test
    void aLabelNamingTheLongestDimensionStillFitsTheWire() {
        String name = ProbeDimensions.plainName(ResourceLocation.parse(
                "somemod:the_extremely_long_winded_dimension_of_endless_descriptive_naming"));
        String label = "NW " + String.format("%,d", 10_000_000) + ", " + name;
        assertTrue(label.length() <= 64, "\"" + label + "\" is " + label.length() + " characters");
        assertFalse(name.endsWith(" "), "a name cut to length should not end on the space it was cut at");

        RegistryFriendlyByteBuf buf = buffer();
        WarpCourse course = WarpCourse.toFix(NETHER, new BlockPos(1, 2, 3), 6, label);
        WarpCourse.STREAM_CODEC.encode(buf, course);
        assertEquals(course, WarpCourse.STREAM_CODEC.decode(buf));
    }

    /**
     * The three dimensions the game ships have real names.
     *
     * <p>Their keys are built from the dimension's id, so the scan that finds this mod's other missing
     * translations cannot see them.
     */
    @Test
    void theVanillaDimensionsAreTranslated() throws IOException {
        JsonObject lang;
        try (Reader reader = Files.newBufferedReader(
                Path.of("src/main/resources/assets/aerowarptics/lang/en_us.json"), StandardCharsets.UTF_8)) {
            lang = JsonParser.parseReader(reader).getAsJsonObject();
        }
        for (ResourceKey<Level> vanilla : List.of(OVERWORLD, NETHER, END)) {
            String key = ProbeDimensions.translationKey(vanilla.location());
            assertTrue(lang.has(key), key + " has no translation");
        }
        assertEquals("aerowarptics.dimension.minecraft.the_nether", ProbeDimensions.translationKey(NETHER.location()));
    }

    // ----------------------------------------------------------------- course

    @Test
    void aFixInAnotherDimensionSurvivesBeingSavedAndLoaded() {
        WarpCourse course = WarpCourse.toFix(NETHER, new BlockPos(512, 127, -64), 12, "NE 3,200, The Nether");
        WarpCourse back = WarpCourse.load(course.save());
        assertEquals(course, back);
        assertEquals(NETHER, back.dimension());
        assertFalse(back.fixIsIn(OVERWORLD));
        assertTrue(back.fixIsIn(NETHER));
    }

    /**
     * A fix saved before a probe could look elsewhere is in the drive's own dimension, wherever that is.
     *
     * <p>Which is also what a Rift Beacon's is: it summons a ship to where the player stands.
     */
    @Test
    void aFixThatNamesNoDimensionIsInTheDrivesOwn() {
        CompoundTag tag = WarpCourse.toFix(NETHER, new BlockPos(1, 2, 3), 40, "old").save();
        tag.remove("Dimension");
        WarpCourse back = WarpCourse.load(tag);
        assertNull(back.dimension());
        assertTrue(back.fixIsIn(OVERWORLD));
        assertTrue(back.fixIsIn(NETHER));
        assertEquals(back, WarpCourse.toFix(new BlockPos(1, 2, 3), 40, "old"));
    }

    /** A dimension that no longer reads as an id is no dimension, not a course that fails to load. */
    @Test
    void aDimensionThatIsNotAnIdLoadsAsTheDrivesOwn() {
        CompoundTag tag = WarpCourse.toFix(END, new BlockPos(1, 2, 3), 40, "odd").save();
        tag.putString("Dimension", "Not An Id");
        WarpCourse back = WarpCourse.load(tag);
        assertTrue(back.isFix());
        assertNull(back.dimension());
    }

    /**
     * The same numbers in another dimension are another course.
     *
     * <p>The probe's "the drive is aimed here" is an equality test in spirit: if these compared equal, a
     * drive aimed at the Overworld's 512, -64 would be reported as aimed at the Nether's.
     */
    @Test
    void theSameNumbersInAnotherDimensionAreAnotherCourse() {
        BlockPos fix = new BlockPos(512, 90, -64);
        assertNotEquals(WarpCourse.toFix(NETHER, fix, 12, "NE"), WarpCourse.toFix(END, fix, 12, "NE"));
        assertNotEquals(WarpCourse.toFix(NETHER, fix, 12, "NE"), WarpCourse.toFix(fix, 12, "NE"));
    }

    /** An anchor's dimension is the registry's to say, so an anchor course is normalised to none. */
    @Test
    void anAnchorCourseNamesNoDimension() {
        UUID id = UUID.nameUUIDFromBytes("home".getBytes(StandardCharsets.UTF_8));
        WarpCourse course = new WarpCourse(id, null, ArrivalHeight.UNSET, "Home", false, NETHER);
        assertNull(course.dimension());
        assertEquals(WarpCourse.toAnchor(id, "Home"), course);
    }

    /** Renaming a course, or holding its altitude, does not move it to another dimension. */
    @Test
    void relabellingAndHoldingKeepTheDimension() {
        WarpCourse course = WarpCourse.toFix(END, new BlockPos(1, 2, 3), 12, "old");
        assertEquals(END, course.withLabel("new").dimension());
        assertEquals(END, course.withHoldAltitude(true).dimension());
        assertEquals(course, course.withHoldAltitude(true).withHoldAltitude(false));
    }

    @Test
    void everyKindOfCourseSurvivesTheWire() {
        List<WarpCourse> courses = List.of(
                WarpCourse.toFix(NETHER, new BlockPos(512, 127, -64), 12, "NE 3,200, The Nether"),
                WarpCourse.toFix(new BlockPos(-9, 70, 4_000), ArrivalHeight.UNSET, "S 4,000").withHoldAltitude(true),
                WarpCourse.toAnchor(UUID.nameUUIDFromBytes("home".getBytes(StandardCharsets.UTF_8)), "Home"));
        for (WarpCourse course : courses) {
            RegistryFriendlyByteBuf buf = buffer();
            WarpCourse.STREAM_CODEC.encode(buf, course);
            assertEquals(course, WarpCourse.STREAM_CODEC.decode(buf));
            assertEquals(0, buf.readableBytes(), "bytes left over after " + course);
        }
    }

    // ---------------------------------------------------------------- packets

    private static ClientboundProbePacket panel(List<ResourceLocation> dimensions, int index) {
        return new ClientboundProbePacket(new BlockPos(4, 5, 6), dimensions, index, ProbeBearing.EAST.index(),
                2_000, 512, 100_000, 6, 64, 0, 0.0F, 900, 35_400, 1_100, true, true, -1, 0, 0.0F, "", false);
    }

    @Test
    void thePanelIsToldWhichDimensionsItMayOfferAndWhichIsChosen() {
        ClientboundProbePacket sent = panel(
                List.of(OVERWORLD.location(), END.location(), NETHER.location()), 2);
        RegistryFriendlyByteBuf buf = buffer();
        ClientboundProbePacket.STREAM_CODEC.encode(buf, sent);
        ClientboundProbePacket read = ClientboundProbePacket.STREAM_CODEC.decode(buf);

        assertEquals(sent, read);
        assertEquals(0, buf.readableBytes());
        assertEquals(NETHER.location(), read.dimension());
        assertTrue(read.canChangeDimension());
        assertSame(ProbeBearing.EAST, read.bearing(), "the fields after the list still line up");
    }

    /** One dimension on offer is a label, not a choice - and an index off the end is not a crash. */
    @Test
    void aProbeKeptToItsOwnDimensionHasNothingToChange() {
        ClientboundProbePacket only = panel(List.of(OVERWORLD.location()), 0);
        assertFalse(only.canChangeDimension());
        assertEquals(OVERWORLD.location(), only.dimension());
        assertEquals(END.location(), panel(List.of(OVERWORLD.location(), END.location()), 7).dimension());
        assertNull(panel(List.of(), 0).dimension());
    }

    /** A list longer than a panel is ever sent is refused on the way out, not allocated on the way in. */
    @Test
    void aPanelNeverCarriesMoreDimensionsThanItsCap() {
        List<ResourceLocation> many = new ArrayList<>();
        for (int index = 0; index <= ProbeDimensions.MAX_LISTED; index++) {
            many.add(ResourceLocation.parse("pockets:room_" + index));
        }
        assertThrows(EncoderException.class,
                () -> ClientboundProbePacket.STREAM_CODEC.encode(buffer(), panel(many, 0)));
    }

    /** The dial's order names its dimension by id, never by its row in a list the server may have changed. */
    @Test
    void aimingAtADimensionSendsItsId() {
        ServerboundProbePacket sent = ServerboundProbePacket.dimension(new BlockPos(4, 5, 6), NETHER.location());
        RegistryFriendlyByteBuf buf = buffer();
        ServerboundProbePacket.STREAM_CODEC.encode(buf, sent);
        ServerboundProbePacket read = ServerboundProbePacket.STREAM_CODEC.decode(buf);
        assertEquals(sent, read);
        assertEquals("minecraft:the_nether", read.dimension());
        assertSame(ServerboundProbePacket.Action.SET_DIMENSION, read.action());

        // Every other order carries no dimension, and still reads back as itself.
        RegistryFriendlyByteBuf plain = buffer();
        ServerboundProbePacket.STREAM_CODEC.encode(plain, ServerboundProbePacket.range(new BlockPos(4, 5, 6), 3_000));
        ServerboundProbePacket range = ServerboundProbePacket.STREAM_CODEC.decode(plain);
        assertEquals(3_000, range.value());
        assertEquals("", range.dimension());
    }
}
