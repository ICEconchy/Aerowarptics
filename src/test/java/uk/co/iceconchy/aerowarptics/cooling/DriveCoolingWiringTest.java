package uk.co.iceconchy.aerowarptics.cooling;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The drive actually asks what is cooling it, and asks the way every other module is asked.
 *
 * <p>{@link DriveCoolingTest} holds the arithmetic, but arithmetic nobody calls cools nothing, and every
 * way this can come unhooked is silent: the drive still warps, still cools down, the vents still turn and
 * still draw stress. Read from the sources, since the drive cannot be loaded without a game.
 */
class DriveCoolingWiringTest {

    private static final Path SOURCES = Path.of("src/main/java/uk/co/iceconchy/aerowarptics");

    private static String source(String path) {
        try {
            return Files.readString(SOURCES.resolve(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    /** The body of a method, from its signature to the next line that closes a member at class depth. */
    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "could not find " + signature);
        Matcher end = Pattern.compile("\\R    }\\R").matcher(source);
        assertTrue(end.find(start), "could not find the end of " + signature);
        return source.substring(start, end.end());
    }

    @Test
    void aCompletedWarpsCooldownGoesThroughTheCoolers() {
        String drive = source("drive/RiftDriveBlockEntity.java");
        String complete = method(drive, "private void completeWarp()");
        assertTrue(complete.contains("int cooldown = cool(RiftStorm.cooldownTicks(level, tier));"),
                "the cooldown a warp earns is no longer cooled - vents and ice would do nothing");
        // One figure, used for both. A hull told the uncooled figure would refuse a second drive aboard
        // long after the first had finished cooling.
        assertTrue(complete.contains("release(false, level.getGameTime() + cooldown)"),
                "the hull's own cooldown is no longer the cooled one");
        assertTrue(complete.contains("cooldownTicks = cooldown;") && complete.contains("cooldownTotal = cooldown;"),
                "the drive's countdown or its progress bar is no longer the cooled figure");
    }

    /**
     * A neighbour search written against the drive's six sides compiles, works beside a lone drive, and
     * ignores every face of an extended one - the bug the frame's surroundings exist to prevent.
     */
    @Test
    void coolersAreLookedForAllRoundTheFrame() {
        String coolers = method(source("drive/RiftDriveBlockEntity.java"), "public Coolers coolers()");
        assertTrue(coolers.contains("DriveStructure.surroundings(frame())"),
                "coolers are no longer looked for round the whole frame");
        assertFalse(coolers.contains("Direction.values()"), "coolers are looked for beside the drive block only");
        assertTrue(coolers.contains("vent.isTurning()"), "a vent that is not turning would cool for free");
        assertTrue(coolers.contains("vent.linkedDrive() == this"),
                "a vent between two drives would cool both of them");
        assertTrue(coolers.contains("Coolants.isPassive("), "ice is no longer counted");
        assertTrue(coolers.contains("level.isLoaded(around)"), "counting coolers may now load a chunk");
    }

    @Test
    void aVentFindsItsDriveThroughAFormedExtender() {
        String vent = source("cooling/CoolingVentBlockEntity.java");
        String linked = method(vent, "public RiftDriveBlockEntity linkedDrive()");
        assertTrue(linked.contains("DriveAssembly.driveAt("),
                "a vent against an extended drive's frame would not find its drive");
        assertFalse(vent.contains("instanceof RiftDriveBlockEntity"),
                "a vent looks for a drive block rather than asking DriveAssembly.driveAt");
    }

    @Test
    void aVentIsAStressConsumerAndIceIsNot() {
        // Anchored to the start of a line, so the registration commented out is the registration missing.
        Pattern registered = Pattern.compile(
                "^\\s*BlockStressValues\\.IMPACTS\\.register\\(AWBlocks\\.COOLING_VENT\\.get\\(\\), "
                        + "AWConfig\\.COOLING_VENT_STRESS::get\\);", Pattern.MULTILINE);
        assertTrue(registered.matcher(source("registry/AWStress.java")).find(),
                "a vent with no registered impact turns for nothing");
    }

    /**
     * The steam is drawn from a status the server sends, and whether a vent counts turns on the server's
     * config. The client reading that config for itself is the thing the packet exists to avoid.
     */
    @Test
    void theClientNeverReadsTheServersCoolingConfig() {
        String runtime = source("client/ClientRuntime.java");
        String plume = method(runtime, "static void tickCoolingVent(");
        assertFalse(plume.contains("AWConfig.COOLING_"), "the plume reads a server-only setting");
        String goggles = method(source("cooling/CoolingVentBlockEntity.java"), "public boolean addToGoggleTooltip(");
        assertFalse(goggles.contains("AWConfig."), "the vent's goggles read a server-only setting");
    }
}
