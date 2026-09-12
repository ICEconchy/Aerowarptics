package uk.co.iceconchy.aerowarptics;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.astrolabe.DestinationSurvey;
import uk.co.iceconchy.aerowarptics.client.screen.AWAnim;
import uk.co.iceconchy.aerowarptics.probe.ProbeBearing;
import uk.co.iceconchy.aerowarptics.probe.ProbeSounding;
import uk.co.iceconchy.aerowarptics.probe.ProbeState;
import uk.co.iceconchy.aerowarptics.probe.ProbeVerdict;
import uk.co.iceconchy.aerowarptics.warp.ArrivalHeight;
import uk.co.iceconchy.aerowarptics.warp.WarpCourse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Rift Probe's arithmetic, and the course it produces.
 *
 * <p>Almost all of this is about one promise: that "three thousand blocks north-east" travels three
 * thousand blocks. A diagonal built from raw {@code (1, 1)} offsets travels four and a quarter
 * thousand, which is not something a player would ever spot as a bug - they would simply find that
 * diagonal soundings cost the same and went further, and use nothing else.
 */
class ProbeTest {

    private static final double EPSILON = 1.0e-9D;

    // -------------------------------------------------------------- bearings

    @Test
    void everyBearingIsAUnitStep() {
        for (ProbeBearing bearing : ProbeBearing.values()) {
            double length = Math.sqrt(bearing.dx() * bearing.dx() + bearing.dz() * bearing.dz());
            assertEquals(1.0D, length, 1.0e-9D,
                    bearing + " travels " + length + " blocks per block of range");
        }
    }

    @Test
    void aDiagonalGoesAsFarAsAStraightOne() {
        int range = 3_000;
        double straight = Math.hypot(ProbeBearing.NORTH.dx() * range, ProbeBearing.NORTH.dz() * range);
        double diagonal = Math.hypot(ProbeBearing.NORTH_EAST.dx() * range,
                ProbeBearing.NORTH_EAST.dz() * range);
        assertEquals(straight, diagonal, 1.0e-6D, "a diagonal sounding reached further than a straight one");
    }

    @Test
    void bearingsPointWhereMinecraftThinksTheyDo() {
        // North is negative Z and east is positive X, which is what the world uses.
        assertEquals(0.0D, ProbeBearing.NORTH.dx(), EPSILON);
        assertEquals(-1.0D, ProbeBearing.NORTH.dz(), EPSILON);
        assertEquals(1.0D, ProbeBearing.EAST.dx(), EPSILON);
        assertEquals(0.0D, ProbeBearing.EAST.dz(), EPSILON);
        assertEquals(0.0F, ProbeBearing.NORTH.degrees());
        assertEquals(90.0F, ProbeBearing.EAST.degrees());
        assertEquals(180.0F, ProbeBearing.SOUTH.degrees());
        assertEquals(270.0F, ProbeBearing.WEST.degrees());
    }

    @Test
    void theDialWrapsRatherThanRunningOff() {
        assertSame(ProbeBearing.NORTH, ProbeBearing.NORTH_WEST.next());
        assertSame(ProbeBearing.NORTH_WEST, ProbeBearing.NORTH.previous());
        assertSame(ProbeBearing.NORTH, ProbeBearing.byIndex(8));
        assertSame(ProbeBearing.NORTH_WEST, ProbeBearing.byIndex(-1));
    }

    // -------------------------------------------------------------- soundings

    private static DestinationSurvey survey(int groundY, float known) {
        int size = 2 * DestinationSurvey.RADIUS / DestinationSurvey.STEP + 1;
        int cells = size * size;
        byte[] colours = new byte[cells];
        byte[] relief = new byte[cells];
        // A colour of zero means "not read", so filling part of the grid sets the coverage.
        for (int index = 0; index < (int) (cells * known); index++) {
            colours[index] = 12;
        }
        return new DestinationSurvey(DestinationSurvey.RADIUS, DestinationSurvey.STEP,
                new BlockPos(1_000, 64, -2_000), groundY, colours, relief);
    }

    @Test
    void aSoundingThatFoundGroundIsUsable() {
        ProbeSounding sounding = new ProbeSounding(ProbeBearing.NORTH_EAST, 3_200, survey(71, 1.0F));
        assertTrue(sounding.usable());
        assertFalse(sounding.thin());
        assertSame(ProbeVerdict.CLEAR, sounding.verdict());
        // The fix keeps the survey's horizontal centre but takes the ground height, because that is
        // what the drive aims at - not the height the probe happened to sound from.
        assertEquals(new BlockPos(1_000, 71, -2_000), sounding.fix());
    }

    @Test
    void aSoundingWithNoGroundIsRefused() {
        ProbeSounding sounding = new ProbeSounding(ProbeBearing.WEST, 900,
                survey(DestinationSurvey.NO_GROUND, 1.0F));
        assertFalse(sounding.usable());
        assertSame(ProbeVerdict.NO_GROUND, sounding.verdict());
        assertFalse(sounding.verdict().usable());
    }

    /** A thin reading is still a reading; it just says so. */
    @Test
    void aPartialReadingIsUsableAndFlagged() {
        ProbeSounding sounding = new ProbeSounding(ProbeBearing.SOUTH, 1_500, survey(80, 0.4F));
        assertTrue(sounding.usable());
        assertTrue(sounding.thin());
        assertSame(ProbeVerdict.PARTIAL, sounding.verdict());
        assertTrue(sounding.verdict().usable(), "a thin reading should still be flyable");
    }

    @Test
    void theLabelSaysWhereAndHowFar() {
        assertEquals("NE 3,200",
                new ProbeSounding(ProbeBearing.NORTH_EAST, 3_200, survey(64, 1.0F)).label());
    }

    @Test
    void onlyReachingCountsAsBusy() {
        assertTrue(ProbeState.REACHING.busy());
        for (ProbeState state : ProbeState.values()) {
            if (state != ProbeState.REACHING) {
                assertFalse(state.busy(), state + " should not block the controls");
            }
        }
        assertSame(ProbeState.IDLE, ProbeState.byIndex(0));
        assertSame(ProbeState.IDLE, ProbeState.byIndex(ProbeState.values().length));
    }

    // ---------------------------------------------------------------- course

    /**
     * A course is one thing or the other.
     *
     * <p>A course that was neither would be a drive that believes it has somewhere to go and cannot
     * say where - which surfaces as an aborted warp halfway down a corridor rather than as a refusal
     * at the console.
     */
    @Test
    void aCourseIsAnAnchorOrAFixAndNeverBoth() {
        assertThrows(IllegalArgumentException.class,
                () -> new WarpCourse(null, null, ArrivalHeight.UNSET, "nowhere"));
        assertThrows(IllegalArgumentException.class,
                () -> new WarpCourse(java.util.UUID.randomUUID(), new BlockPos(0, 0, 0), 6, "both"));
    }

    @Test
    void aFixCourseSurvivesBeingSavedAndLoaded() {
        WarpCourse course = WarpCourse.toFix(new BlockPos(4_100, 92, -880), 40, "NE 3,200");
        CompoundTag tag = course.save();
        WarpCourse back = WarpCourse.load(tag);
        assertEquals(course, back);
        assertEquals(40, back.arrivalHeight(), "the height the probe asked for goes with the course");
        assertTrue(back.isFix());
        assertFalse(back.isAnchor());
    }

    /**
     * A fix set before heights were adjustable follows the server default, as it did when it was set.
     *
     * <p>Read as zero instead, a course a pilot armed before the upgrade would put the keel on the
     * ground the sounding found.
     */
    @Test
    void aFixSavedBeforeArrivalHeightsFollowsTheServerDefault() {
        CompoundTag tag = WarpCourse.toFix(new BlockPos(1, 2, 3), 40, "old").save();
        tag.remove("ArrivalHeight");

        WarpCourse back = WarpCourse.load(tag);

        assertEquals(ArrivalHeight.UNSET, back.arrivalHeight());
        assertEquals(ArrivalHeight.serverDefault(), ArrivalHeight.resolve(back.arrivalHeight()));
    }

    /**
     * An anchor course carries no height of its own: the anchor's is read when the rift opens.
     *
     * <p>Normalised rather than stored, so two courses to the same anchor compare equal whatever was
     * passed - the probe panel's "already the course" check is an equality test.
     */
    @Test
    void anAnchorCourseCarriesNoHeight() {
        java.util.UUID id = java.util.UUID.nameUUIDFromBytes("home".getBytes());
        assertEquals(WarpCourse.toAnchor(id, "Home"), new WarpCourse(id, null, 40, "Home"));
        assertEquals(ArrivalHeight.UNSET, WarpCourse.toAnchor(id, "Home").arrivalHeight());
    }

    @Test
    void anAnchorCourseSurvivesBeingSavedAndLoaded() {
        WarpCourse course = WarpCourse.toAnchor(
                java.util.UUID.nameUUIDFromBytes("home".getBytes()), "Home");
        WarpCourse back = WarpCourse.load(course.save());
        assertEquals(course, back);
        assertTrue(back.isAnchor());
    }

    @Test
    void nothingLoadsAsNoCourse() {
        assertNull(WarpCourse.load(null));
        assertNull(WarpCourse.load(new CompoundTag()), "an empty tag is not a course");
    }

    /** An anchor can be renamed after a course is set, and the console should not keep saying the old one. */
    @Test
    void aCourseCanBeRelabelledWithoutMoving() {
        WarpCourse course = WarpCourse.toFix(new BlockPos(1, 2, 3), 12, "old");
        WarpCourse renamed = course.withLabel("new");
        assertEquals(course.fix(), renamed.fix());
        assertEquals(12, renamed.arrivalHeight(), "relabelling does not move the ship up or down");
        assertEquals("new", renamed.label());
    }

    // -------------------------------------------------------------- easing

    @Test
    void anEasedValueArrivesAndStops() {
        AWAnim.Eased eased = new AWAnim.Eased(0.3F, 0.0F);
        eased.set(1.0F);
        for (int tick = 0; tick < 200; tick++) {
            eased.tick();
        }
        // Exactly, not nearly: a chase that never formally arrives leaves a bar at 99.97% forever.
        assertEquals(1.0F, eased.get(1.0F), 0.0F);
    }

    @Test
    void anEasedValueNeverOvershoots() {
        AWAnim.Eased eased = new AWAnim.Eased(0.5F, 0.0F);
        eased.set(1.0F);
        for (int tick = 0; tick < 40; tick++) {
            eased.tick();
            float value = eased.get(1.0F);
            assertTrue(value >= 0.0F && value <= 1.0F, "eased value left its range at " + value);
        }
    }

    @Test
    void snappingSkipsTheTravel() {
        AWAnim.Eased eased = new AWAnim.Eased(0.1F, 0.0F);
        eased.snap(0.75F);
        assertEquals(0.75F, eased.get(0.0F));
        assertEquals(0.75F, eased.get(1.0F));
    }

    @Test
    void easingCurvesStayInRange() {
        for (int step = -2; step <= 12; step++) {
            float t = step / 10.0F;
            for (float value : new float[]{AWAnim.easeOut(t), AWAnim.easeInOut(t)}) {
                assertTrue(value >= 0.0F && value <= 1.0F, "easing produced " + value + " at " + t);
            }
        }
        assertEquals(0.0F, AWAnim.easeOut(0.0F), 1.0e-6F);
        assertEquals(1.0F, AWAnim.easeOut(1.0F), 1.0e-6F);
    }

    @Test
    void blendingColoursCarriesAlpha() {
        assertEquals(0xFF_00_00_00, AWAnim.blend(0xFF_00_00_00, 0x00_FF_FF_FF, 0.0F));
        assertEquals(0x00_FF_FF_FF, AWAnim.blend(0xFF_00_00_00, 0x00_FF_FF_FF, 1.0F));
        assertEquals(0x80_FF_FF_FF, AWAnim.fade(0xFF_FF_FF_FF, 0.5F) | 0x00_FF_FF_FF);
    }

    @Test
    void aSweepRepeatsAndAPulseBreathes() {
        assertEquals(0.0F, AWAnim.sweep(0.0F, 40.0F), 1.0e-6F);
        assertEquals(0.0F, AWAnim.sweep(40.0F, 40.0F), 1.0e-6F);
        assertEquals(0.5F, AWAnim.sweep(20.0F, 40.0F), 1.0e-6F);
        for (int tick = 0; tick < 100; tick++) {
            float pulse = AWAnim.pulse(tick, 33.0F);
            assertTrue(pulse >= 0.0F && pulse <= 1.0F, "pulse left its range at " + pulse);
        }
    }
}
