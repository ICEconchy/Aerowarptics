package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.CrossDimensionWarp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether a hull still has somewhere to be in a dimension of another height.
 *
 * <p>Dimensional Sable puts every saved section back at the index it had, and a level with fewer sections
 * than that index has no slot to put it in - so loading throws part-way through a crossing. The preflight
 * refuses that case at the mooring instead; this pins where the line falls.
 */
class CrossDimensionFitTest {

    private static final int OVERWORLD_FLOOR = -64;
    private static final int NETHER_SECTIONS = 16;
    private static final int OVERWORLD_SECTIONS = 24;

    @Test
    void aHullAroundTheOverworldPlotCentreFitsTheNether() {
        // Sable builds a plot round y = 128 in the Overworld: section index 12 of 24.
        assertTrue(CrossDimensionWarp.fits(100, 190, OVERWORLD_FLOOR, NETHER_SECTIONS));
    }

    @Test
    void aHullReachingPastIndexFifteenDoesNot() {
        // y = 192 is section index 16 counted up from -64, one past the Nether's last.
        assertTrue(CrossDimensionWarp.fits(100, 191, OVERWORLD_FLOOR, NETHER_SECTIONS));
        assertFalse(CrossDimensionWarp.fits(100, 192, OVERWORLD_FLOOR, NETHER_SECTIONS));
        assertTrue(CrossDimensionWarp.fits(100, 192, OVERWORLD_FLOOR, OVERWORLD_SECTIONS),
                "the same hull stays put in a level as tall as its own");
    }

    @Test
    void aHullBelowItsOwnFloorNeverFits() {
        assertFalse(CrossDimensionWarp.fits(-65, 10, OVERWORLD_FLOOR, OVERWORLD_SECTIONS));
        assertTrue(CrossDimensionWarp.fits(-64, 10, OVERWORLD_FLOOR, OVERWORLD_SECTIONS));
    }

    @Test
    void theNetherToTheOverworldAlwaysFits() {
        // Sixteen sections go into twenty-four whatever the hull does.
        assertTrue(CrossDimensionWarp.fits(0, 255, 0, OVERWORLD_SECTIONS));
    }

    /**
     * Only a machine a crossing stopped is joined to its neighbours again. A turning one is left alone: joining
     * it again would take its line apart and put it back for nothing.
     */
    @Test
    void onlyAMachineLeftWithNoRotationIsRelinked() {
        assertTrue(CrossDimensionWarp.lostItsRotation(0.0F, false));
        assertFalse(CrossDimensionWarp.lostItsRotation(256.0F, true), "a machine still fed");
        assertFalse(CrossDimensionWarp.lostItsRotation(-64.0F, false), "a generator, which has no source to lose");
        assertFalse(CrossDimensionWarp.lostItsRotation(0.0F, true), "an overstressed line reads nought and is fed");
    }

    /**
     * The drive that finishes a crossing asks for it. Whether rotation comes back cannot be tested without a
     * running game - it was found in one, where a rebuilt drive read no rotation beside a turning shaft - so
     * this only holds the hook-up: lose the call and every crossing strands some machines again, a different
     * set each time, with nothing failing.
     */
    @Test
    void aFinishedCrossingRelinksTheMachinesAboard() {
        String drive = read("src/main/java/uk/co/iceconchy/aerowarptics/drive/RiftDriveBlockEntity.java");
        int finish = drive.indexOf("private boolean finishCrossing()");
        int end = drive.indexOf("\n    }\n", finish);
        assertTrue(finish >= 0 && end > finish, "finishCrossing has moved or been renamed");
        assertTrue(drive.substring(finish, end).contains("CrossDimensionWarp.relinkLater("),
                "finishCrossing no longer asks for the vessel's machines to be relinked");
    }

    private static String read(String path) {
        try {
            return Files.readString(Path.of(path)).replace("\r\n", "\n");
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
