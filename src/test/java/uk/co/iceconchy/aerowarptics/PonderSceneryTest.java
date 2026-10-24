package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Holds the Ponder schematics to what their storyboards assume is standing in them.
 *
 * <p>{@code PonderTextTest} already proves a scene has a schematic and that its words are translated.
 * It cannot prove the words are about the build, and several of these scenes do more than talk about
 * theirs: they reach into it by coordinate. The extender scene forms a frame it declares the size and
 * corner of, the stabiliser scene throws a switch by flipping whatever is powered in two named blocks,
 * and the detector scene lights a wire it names block by block.
 *
 * <p>Every one of those is a silent failure if {@code tools/ponder_schematics.py} moves. A frame
 * declared round a cube that is no longer there draws a drive the wrong size with loose casing still
 * visible inside it; a stabiliser that shipped switched on is switched <em>off</em> by the scene, so
 * every sentence after it is backwards; a wire that moved is lit where nothing is. None throws, none
 * shows up in a log, and all of them look plausible until
 * somebody who already knows the machine watches the scene.
 *
 * <p>So this reads the shipped files rather than trusting the script that wrote them, the way
 * {@code RiftFissureTest} reads the rift scars - but by position as well as by name, because where a
 * block is is exactly what the storyboards are relying on.
 */
class PonderSceneryTest {

    private static final Path SCENES = Path.of("src/main/resources/assets/aerowarptics/ponder");

    private static final String DRIVE = "aerowarptics:rift_drive_mk_ii";
    private static final String EXTENDER = "aerowarptics:drive_extender";
    private static final String STABILISER = "aerowarptics:spatial_stabiliser";
    private static final String FISSURE = "aerowarptics:rift_fissure";
    private static final String DETECTOR = "aerowarptics:spatial_wave_detector";
    private static final String VENT = "aerowarptics:cooling_vent";

    // ------------------------------------------------------------- extenders

    /**
     * {@code DriveScenes.driveExtender} declares a frame with its corner at (1, 1, 1), three blocks
     * along an edge, and the drive at (2, 2, 2) - and then tells every cell of it which drive it
     * belongs to. Those numbers are written in the storyboard, so the build has to keep them.
     */
    @Test
    void theExtenderSceneHasACubeToForm() {
        Schematic scene = read("drive_extender");

        assertEquals(List.of(new Pos(2, 2, 2)), scene.positionsOf(DRIVE),
                "the storyboard buries the drive at (2, 2, 2), in the middle of its frame");

        List<Pos> casing = scene.positionsOf(EXTENDER);
        assertEquals(26, casing.size(), "a three-cube round one drive is 26 extenders");
        for (Pos cell : casing) {
            assertTrue(cell.within(1, 3), cell + " is outside the frame the storyboard forms");
            assertEquals("false", scene.at(cell).properties().get("formed"),
                    cell + " ships formed, so the scene would have nothing left to form");
        }

        // Every cell of the box but the drive's, and no more: a frame with a hole in it never forms,
        // and a stray extender outside the cube would be left loose and visible beside a drawn drive.
        for (int x = 1; x <= 3; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = 1; z <= 3; z++) {
                    Pos cell = new Pos(x, y, z);
                    String expected = cell.equals(new Pos(2, 2, 2)) ? DRIVE : EXTENDER;
                    assertEquals(expected, scene.at(cell) == null ? null : scene.at(cell).name(),
                            "the frame has a gap at " + cell);
                }
            }
        }
    }

    /** The shaft has to arrive at casing, not at the drive - that is the whole lesson of the scene. */
    @Test
    void theExtenderScenesPowerArrivesAtTheFrameAndNotTheDrive() {
        Schematic scene = read("drive_extender");
        Pos shaft = new Pos(2, 1, 4);

        assertNotNull(scene.at(shaft), "no shaft where the storyboard points at one");
        assertEquals("create:shaft", scene.at(shaft).name(), "the storyboard points at a shaft here");
        assertEquals(EXTENDER, scene.at(new Pos(2, 1, 3)).name(),
                "the shaft touches the drive itself, which is the arrangement nobody needed shown");
    }

    // ------------------------------------------------------------ stabiliser

    /**
     * The scene throws the switch with {@code toggleRedstonePower}, which cycles whatever it finds
     * rather than setting it, so a stabiliser that shipped switched on would be switched off by the
     * one instruction whose whole purpose is to switch it on.
     */
    @Test
    void theStabiliserSceneStartsSwitchedOff() {
        Schematic scene = read("spatial_stabiliser");
        Pos stabiliser = new Pos(1, 1, 2);
        Pos lever = new Pos(1, 2, 2);

        assertEquals(STABILISER, scene.at(stabiliser).name(),
                "the storyboard points at the stabiliser here");
        assertEquals("false", scene.at(stabiliser).properties().get("powered"),
                "the stabiliser ships switched on, so the scene's flick switches it off");

        assertEquals("minecraft:lever", scene.at(lever).name(),
                "the switch the storyboard flicks is not where it flicks");
        assertEquals("false", scene.at(lever).properties().get("powered"),
                "the lever ships thrown, so the scene starts with the answer already given");
        assertEquals("floor", scene.at(lever).properties().get("face"),
                "the lever has come off the stabiliser's cap, and would rise without it");
    }

    /** A module has to be beside its drive, and this one has no shaft to imply it needs power. */
    @Test
    void theStabiliserSceneStandsItsModuleAgainstADrive() {
        Schematic scene = read("spatial_stabiliser");

        assertEquals(List.of(new Pos(2, 1, 2)), scene.positionsOf(DRIVE),
                "the storyboard points at the drive at (2, 1, 2)");
        assertTrue(scene.positionsOf("create:shaft").isEmpty(),
                "a shaft has appeared, which teaches that a stabiliser needs rotation - it does not");
    }

    // --------------------------------------------------------------- cooling

    /**
     * {@code CoolingScenes.coolingVent} says a vent's louvres face away from the drive and its shaft is at
     * the back, and that one set straight onto a turning drive is turned by it. The schematic has to be
     * that arrangement: a vent turned side-on would be shown working with nothing turning it.
     */
    @Test
    void theCoolingSceneBacksEveryVentOntoItsDrive() {
        Schematic scene = read("cooling_vent");
        Pos drive = new Pos(2, 1, 2);
        assertEquals(List.of(drive), scene.positionsOf(DRIVE), "the storyboard points at the drive at (2, 1, 2)");

        Map<String, int[]> step = Map.of(
                "west", new int[]{-1, 0, 0}, "east", new int[]{1, 0, 0}, "up", new int[]{0, 1, 0},
                "down", new int[]{0, -1, 0}, "north", new int[]{0, 0, -1}, "south", new int[]{0, 0, 1});
        List<Pos> vents = scene.positionsOf(VENT);
        for (Pos vent : List.of(new Pos(1, 1, 2), new Pos(3, 1, 2), new Pos(2, 2, 2))) {
            assertTrue(vents.contains(vent), "the storyboard shows a vent at " + vent);
        }
        for (Pos vent : vents) {
            int[] out = step.get(scene.at(vent).properties().get("facing"));
            assertNotNull(out, vent + " has no facing");
            assertEquals(drive, new Pos(vent.x() - out[0], vent.y() - out[1], vent.z() - out[2]),
                    "the vent at " + vent + " does not have its back to the drive");
        }
        assertEquals("create:shaft", scene.at(new Pos(2, 1, 3)).name(),
                "nothing turns the drive, so nothing turns the vents the scene says are turning");
    }

    /** The scene's last lesson is that four coolers count. Five in the picture would show one doing nothing. */
    @Test
    void theCoolingSceneShowsExactlyAsManyCoolersAsCount() {
        Schematic scene = read("cooling_vent");
        Pos ice = new Pos(2, 1, 1);
        assertNotNull(scene.at(ice), "no ice where the storyboard points at it");
        assertEquals("minecraft:ice", scene.at(ice).name(), "the storyboard points at a block of ice here");
        assertEquals(4, scene.positionsOf(VENT).size() + scene.positionsOf("minecraft:ice").size(),
                "four coolers count on one drive, and the scene says so with four");
    }

    // ------------------------------------------------------------- detector

    /**
     * {@code DetectorScenes.signal} writes a reading onto four named blocks - the detector, two dust
     * and a lamp - and relies on them all starting quiet. A wire that moved would be drawn lit where
     * nothing is, and one that shipped powered would show a signal before anything had arrived.
     */
    @Test
    void theDetectorSceneStartsQuietAndWiredWhereTheStoryboardLightsIt() {
        Schematic scene = read("spatial_wave_detector");

        assertEquals(List.of(new Pos(1, 1, 1)), scene.positionsOf(DETECTOR),
                "the storyboard sets the detector's signal here");
        assertEquals("0", scene.at(new Pos(1, 1, 1)).properties().get("power"),
                "the detector ships already pulsing");
        for (Pos dust : List.of(new Pos(2, 1, 1), new Pos(3, 1, 1))) {
            assertEquals("minecraft:redstone_wire", scene.at(dust).name(),
                    "the storyboard carries the signal down dust at " + dust);
            assertEquals("0", scene.at(dust).properties().get("power"), "the dust ships powered");
        }
        assertEquals("minecraft:redstone_lamp", scene.at(new Pos(4, 1, 1)).name(),
                "the storyboard lights a lamp at the end of the wire");
        assertEquals("false", scene.at(new Pos(4, 1, 1)).properties().get("lit"), "the lamp ships lit");
        assertEquals(List.of(new Pos(3, 1, 3)), scene.positionsOf("aerowarptics:warp_anchor"),
                "the storyboard marks the near arrival at the anchor");
    }

    // -------------------------------------------------------------- crafting

    /**
     * {@code CraftScenes.rifting} lights the brazier at (2, 1, 2) by throwing the lever at (2, 1, 1) beside it.
     * Both have to ship dark, or the scene's first sentence - give it a signal - describes a brazier already lit.
     */
    @Test
    void theBrazierSceneStartsDarkWithItsLeverBesideIt() {
        Schematic scene = read("rift_brazier");
        Pos brazier = new Pos(2, 1, 2);
        Pos lever = new Pos(2, 1, 1);
        assertEquals("aerowarptics:rift_brazier", scene.at(brazier).name(), "the storyboard lights a brazier here");
        assertEquals("false", scene.at(brazier).properties().get("lit"), "the brazier ships lit");
        assertEquals("false", scene.at(brazier).properties().get("powered"), "the brazier ships powered");
        assertEquals("minecraft:lever", scene.at(lever).name(), "the storyboard throws a lever here");
        assertEquals("false", scene.at(lever).properties().get("powered"), "the lever ships thrown");
    }

    /**
     * {@code CraftScenes.collapse} switches on four stabilisers by position, evenly round the cradle - which is
     * the lesson, so a stabiliser that moved would teach a lopsided cage as a good one.
     */
    @Test
    void theCollapseScenesStabilisersStartOffAndStandEvenly() {
        Schematic scene = read("collapse_cradle");
        assertEquals(List.of(new Pos(3, 1, 3)), scene.positionsOf("aerowarptics:collapse_cradle"),
                "the storyboard centres everything on the cradle at (3, 1, 3)");
        List<Pos> stabilisers = scene.positionsOf(STABILISER);
        assertEquals(4, stabilisers.size(), "the storyboard switches on four stabilisers");
        int sumX = 0;
        int sumZ = 0;
        for (Pos stabiliser : stabilisers) {
            assertEquals("false", scene.at(stabiliser).properties().get("powered"), stabiliser + " ships switched on");
            sumX += stabiliser.x() - 3;
            sumZ += stabiliser.z() - 3;
        }
        assertEquals(0, sumX, "the cage is lopsided east-west");
        assertEquals(0, sumZ, "the cage is lopsided north-south");
        assertEquals(8, scene.positionsOf("create:depot").size(), "the ring is eight depots");
    }

    // ------------------------------------------------------------------- nbt

    private record Pos(int x, int y, int z) {
        boolean within(int least, int most) {
            return x >= least && x <= most && y >= least && y <= most && z >= least && z <= most;
        }

        @Override
        public String toString() {
            return "(" + x + ", " + y + ", " + z + ")";
        }
    }

    private record Block(String name, Map<String, String> properties) {
    }

    private record Schematic(Map<Pos, Block> blocks) {
        Block at(Pos pos) {
            return blocks.get(pos);
        }

        /** Every position holding this block, in the order the file lists them. */
        List<Pos> positionsOf(String name) {
            List<Pos> found = new ArrayList<>();
            blocks.forEach((pos, block) -> {
                if (block.name().equals(name)) {
                    found.add(pos);
                }
            });
            return found;
        }
    }

    private static Schematic read(String name) {
        Path path = SCENES.resolve(name + ".nbt");
        assertTrue(Files.isRegularFile(path), "no schematic at " + path + ". Run tools/ponder_schematics.py.");

        Map<String, Object> root;
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(path)))) {
            assertEquals(10, in.readUnsignedByte(), path + " does not start with a compound");
            in.readUTF();
            root = asCompound(tag(in, 10));
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }

        List<Block> palette = new ArrayList<>();
        for (Object entry : asList(root.get("palette"))) {
            Map<String, Object> state = asCompound(entry);
            Map<String, String> properties = new LinkedHashMap<>();
            if (state.containsKey("Properties")) {
                asCompound(state.get("Properties"))
                        .forEach((key, value) -> properties.put(key, String.valueOf(value)));
            }
            palette.add(new Block(String.valueOf(state.get("Name")), properties));
        }

        Map<Pos, Block> blocks = new LinkedHashMap<>();
        for (Object entry : asList(root.get("blocks"))) {
            Map<String, Object> placed = asCompound(entry);
            List<Object> at = asList(placed.get("pos"));
            blocks.put(new Pos((Integer) at.get(0), (Integer) at.get(1), (Integer) at.get(2)),
                    palette.get((Integer) placed.get("state")));
        }
        return new Schematic(blocks);
    }

    /**
     * One NBT tag of the given type.
     *
     * <p>A whole reader rather than the byte-scan {@code RiftFissureTest} uses, because these
     * assertions are about where a block is and a scan for names cannot say.
     */
    private static Object tag(DataInputStream in, int type) throws IOException {
        switch (type) {
            case 1:
                return in.readByte();
            case 2:
                return in.readShort();
            case 3:
                return in.readInt();
            case 4:
                return in.readLong();
            case 5:
                return in.readFloat();
            case 6:
                return in.readDouble();
            case 7:
                return readList(in, 1, in.readInt());
            case 8:
                return in.readUTF();
            case 9:
                return readList(in, in.readUnsignedByte(), in.readInt());
            case 10: {
                Map<String, Object> compound = new LinkedHashMap<>();
                for (int element = in.readUnsignedByte(); element != 0; element = in.readUnsignedByte()) {
                    compound.put(in.readUTF(), tag(in, element));
                }
                return compound;
            }
            case 11:
                return readList(in, 3, in.readInt());
            case 12:
                return readList(in, 4, in.readInt());
            default:
                throw new IOException("unknown tag type " + type);
        }
    }

    private static List<Object> readList(DataInputStream in, int type, int length) throws IOException {
        List<Object> items = new ArrayList<>(Math.max(0, length));
        for (int i = 0; i < length; i++) {
            items.add(tag(in, type));
        }
        return items;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asCompound(Object tag) {
        return (Map<String, Object>) tag;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object tag) {
        return (List<Object>) tag;
    }
}
