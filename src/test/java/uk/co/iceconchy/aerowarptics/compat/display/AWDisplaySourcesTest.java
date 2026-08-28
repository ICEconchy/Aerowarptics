package uk.co.iceconchy.aerowarptics.compat.display;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.chute.RiftChuteBlockEntity;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.gate.RiftGateBlockEntity;
import uk.co.iceconchy.aerowarptics.modulator.RiftModulatorBlockEntity;
import uk.co.iceconchy.aerowarptics.probe.RiftProbeBlockEntity;
import uk.co.iceconchy.aerowarptics.siphon.SpatialSiphonBlockEntity;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards on the Display Link sources.
 *
 * <p>The line formatters take a live block entity and cannot run without a game, so what is checked
 * here is everything around them - and everything around them is exactly the sort of thing that
 * fails silently. A source missing from {@link AWDisplaySources#ALL} simply never appears in the
 * link's dropdown. A missing translation renders as a raw key on somebody's sign. A changed
 * registry id orphans every link that was configured before the change, in every save, with no
 * error anywhere.
 */
class AWDisplaySourcesTest {

    private static final Path LANG =
            Path.of("src/main/resources/assets/aerowarptics/lang/en_us.json");

    /**
     * Every machine is reportable.
     *
     * <p>Deliberately spelled out rather than derived: the point is to fail when a machine is added
     * and nobody thinks about its readouts. The blocks left out are left out on purpose - a Warp
     * Anchor and an Astrolabe hold configuration rather than state, a Rift Fissure is invisible to
     * anyone without the goggles, and a Rift Gate Frame has no block entity to read.
     *
     * <p>Checked by breaking it: removing the Rift Chute from {@code ALL} fails here.
     */
    @Test
    void everyMachineHasASource() {
        Set<Class<?>> expected = Set.of(
                RiftDriveBlockEntity.class,
                RiftGateBlockEntity.class,
                RiftProbeBlockEntity.class,
                SpatialSiphonBlockEntity.class,
                RiftChuteBlockEntity.class,
                RiftModulatorBlockEntity.class);

        Set<Class<?>> covered = AWDisplaySources.ALL.stream()
                .map(AWDisplaySource::machineType)
                .collect(Collectors.toSet());

        assertEquals(expected, covered,
                "a machine with no display source is a machine no Display Link will offer to read");
    }

    /** Two sources under one id would silently overwrite each other in Create's registry. */
    @Test
    void sourceIdsAreUnique() {
        Set<String> seen = new HashSet<>();
        for (AWDisplaySource<?> source : AWDisplaySources.ALL) {
            assertTrue(seen.add(source.sourceId()), "duplicate source id: " + source.sourceId());
        }
    }

    /**
     * The ids themselves, written out.
     *
     * <p>An id is baked into the save data of every link configured against it. Renaming one is not
     * a refactor, it is a migration, and this test is here to make that decision deliberate.
     */
    @Test
    void sourceIdsAreStable() {
        assertEquals(
                List.of("rift_drive", "rift_gate", "rift_probe", "spatial_siphon", "rift_chute",
                        "rift_modulator"),
                AWDisplaySources.ALL.stream().map(AWDisplaySource::sourceId).toList());
    }

    /** Mode names are what the scroll input's translation keys are built from. */
    @Test
    void modeNamesAreUsableAsKeys() {
        for (AWDisplaySource<?> source : AWDisplaySources.ALL) {
            List<String> modes = source.modes();
            assertFalse(modes.isEmpty(), source.sourceId() + " offers no modes");
            assertEquals(modes.size(), Set.copyOf(modes).size(),
                    source.sourceId() + " has a duplicate mode name, so two scroll positions would "
                            + "read the same translation key");
            for (String mode : modes) {
                assertTrue(mode.matches("[a-z0-9_]+"),
                        source.sourceId() + " mode \"" + mode + "\" is not a usable key fragment");
            }
        }
    }

    /**
     * Every key the configuration screen and the dropdown ask for exists.
     *
     * <p>{@code LangCoverageTest} covers the keys the code passes to {@code AWLang} as literals. It
     * cannot see these ones: the source's name is assembled by Create from the registry id, and the
     * mode options are assembled by {@code CreateLang.translatedOptions}, which hard-prefixes
     * {@code create.} - so keys that belong to this mod have to live under Create's namespace.
     */
    @Test
    void everyConfigurationKeyIsTranslated() {
        JsonObject lang = lang();
        List<String> missing = new ArrayList<>();

        for (AWDisplaySource<?> source : AWDisplaySources.ALL) {
            String id = source.sourceId();
            // DisplaySource.getName() builds "<namespace>.display_source.<path>".
            check(lang, missing, "aerowarptics.display_source." + id);
            // The scroll input's own title, and one entry per option.
            check(lang, missing, "create.display_source." + id + ".mode");
            for (String mode : source.modes()) {
                check(lang, missing, "create.display_source." + id + "." + mode);
            }
        }

        if (!missing.isEmpty()) {
            fail("these would render as raw keys in the Display Link screen:\n  "
                    + String.join("\n  ", missing));
        }
    }

    /** And the reverse: a stale key for a mode that no longer exists is dead weight in the file. */
    @Test
    void nothingIsTranslatedThatIsNoLongerOffered() {
        Set<String> live = new HashSet<>();
        for (AWDisplaySource<?> source : AWDisplaySources.ALL) {
            live.add("create.display_source." + source.sourceId() + ".mode");
            for (String mode : source.modes()) {
                live.add("create.display_source." + source.sourceId() + "." + mode);
            }
        }

        List<String> stale = lang().keySet().stream()
                .filter(key -> key.startsWith("create.display_source."))
                .filter(key -> !live.contains(key))
                .sorted()
                .toList();

        assertTrue(stale.isEmpty(), "lang entries for modes that no longer exist:\n  "
                + String.join("\n  ", stale));
    }

    /**
     * A stored mode index is clamped into range.
     *
     * <p>It comes off disk, from a link that may have been set up against an older version of this
     * mod with a different number of modes. Unclamped, it falls through to whatever the switch's
     * default branch is - a reading of something nobody asked for, on a display nobody is watching
     * closely enough to notice it changed.
     */
    @Test
    void modeIndexIsClamped() {
        assertEquals(0, DisplayReadout.clampMode(-1, 5));
        assertEquals(0, DisplayReadout.clampMode(Integer.MIN_VALUE, 5));
        assertEquals(0, DisplayReadout.clampMode(0, 5));
        assertEquals(4, DisplayReadout.clampMode(4, 5));
        assertEquals(4, DisplayReadout.clampMode(5, 5));
        assertEquals(4, DisplayReadout.clampMode(Integer.MAX_VALUE, 5));
        // A source with no modes at all should still index something valid rather than throw.
        assertEquals(0, DisplayReadout.clampMode(3, 0));
    }

    /**
     * Cooldowns round up.
     *
     * <p>A readout that says zero while the machine is still waiting is a readout the pilot stops
     * trusting.
     */
    @Test
    void cooldownSecondsRoundUp() {
        assertEquals(0, DisplayReadout.seconds(0));
        assertEquals(0, DisplayReadout.seconds(-5));
        assertEquals(1, DisplayReadout.seconds(1));
        assertEquals(1, DisplayReadout.seconds(20));
        assertEquals(2, DisplayReadout.seconds(21));
        assertEquals(10, DisplayReadout.seconds(200));
        assertEquals(100, DisplayReadout.seconds(2000));
    }

    private static void check(JsonObject lang, List<String> missing, String key) {
        if (!lang.has(key)) {
            missing.add(key);
        }
    }

    private static JsonObject lang() {
        try (Reader reader = Files.newBufferedReader(LANG, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not read " + LANG, e);
        }
    }
}
