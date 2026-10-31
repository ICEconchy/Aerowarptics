package uk.co.iceconchy.aerowarptics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.gate.RiftGateShape;
import uk.co.iceconchy.aerowarptics.gate.RiftGateStructure;
import uk.co.iceconchy.aerowarptics.rail.RailLaneMap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The debug test bench in {@code debug/aerowarptics_test} builds what it says it builds.
 *
 * <p>The pack is a datapack, so nothing compiles it and nothing but a live world runs it - and every way it can rot
 * is quiet. An id renamed in the mod leaves a {@code setblock} that errors in chat an hour into a session. A gate
 * ring laid a block out leaves a gate that never forms, which reads in game as a mod bug rather than a bench bug.
 *
 * <p>So the geometry is read back out of the function and put through the mod's own rules: the rings are handed to
 * {@link RiftGateStructure} to see that they form, and every track the bench lays against one is handed to
 * {@link RailLaneMap} to see that it would be a lane and to work out where its far end lands. Nothing here names a
 * coordinate the function does not: the rail bench is now three gates and four lanes, and a test that spelled those
 * out by hand would have to be rewritten every time the yard moved, which is how a test stops being read.
 *
 * <p>What this cannot prove is what Create's own screens do - placing and naming the stations, assembling the train,
 * writing the schedules - and that is the whole of what the bench asks of a player.
 */
class TestBenchTest {

    private static final Path PACK = Path.of("debug/aerowarptics_test/data/aerowarptics_test/function");

    private static final Path LANG = Path.of("src/main/resources/assets/aerowarptics/lang/en_us.json");

    private static final Path CONFIG = Path.of("src/main/java/uk/co/iceconchy/aerowarptics/AWConfig.java");

    private static final String RAILS = "rails.mcfunction";

    /** A UUID written into the pack as an int-array, kept as the digits so it can be a map key. */
    private static final Pattern GATE_ID = Pattern.compile("GateId:\\[I;([-\\d,]+)]");

    private static final Pattern DESTINATION = Pattern.compile("Destination:\\[I;([-\\d,]+)]");

    /** A positioned block, as a function left it. Air is an absence, which is what the gate rules want. */
    private record Placed(Map<BlockPos, String> blocks) {

        Predicate<BlockPos> frame() {
            return pos -> {
                String block = blocks.get(pos);
                return "aerowarptics:rift_gate_frame".equals(block) || "aerowarptics:rift_gate".equals(block);
            };
        }

        Predicate<BlockPos> open() {
            return pos -> !blocks.containsKey(pos);
        }

        List<BlockPos> of(String block) {
            return blocks.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(block))
                    .map(Map.Entry::getKey)
                    .sorted((a, b) -> Integer.compare(a.getX(), b.getX()))
                    .toList();
        }
    }

    /**
     * One lane the bench has laid the near end of.
     *
     * @param controller the gate it runs into
     * @param shape      that gate's opening
     * @param across     the column of the opening it enters, which is what decides where it comes out
     * @param near       the lane's near end as the mod reads it
     */
    private record Lane(BlockPos controller, RiftGateShape shape, int across, RailLaneMap.Near near) {
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    private static List<Path> functions() {
        try (Stream<Path> files = Files.list(PACK)) {
            return files.filter(path -> path.toString().endsWith(".mcfunction")).sorted().toList();
        } catch (IOException e) {
            throw new AssertionError("could not list " + PACK, e);
        }
    }

    private static List<String> lines(String function) {
        return read(PACK.resolve(function)).lines().toList();
    }

    /** A figure the mod's own config declares, so the bench and the mod cannot drift apart silently. */
    private static int configured(String key) {
        Matcher found = Pattern.compile("defineInRange\\(\"" + key + "\", ([\\d_]+)").matcher(read(CONFIG));
        assertTrue(found.find(), key + " has moved out of AWConfig");
        return Integer.parseInt(found.group(1).replace("_", ""));
    }

    /** The coordinate a {@code ~} argument names, relative to the runner's feet. */
    private static int relative(String argument) {
        assertTrue(argument.startsWith("~"), "the bench is laid relative to the runner: " + argument);
        String offset = argument.substring(1);
        return offset.isEmpty() ? 0 : Integer.parseInt(offset);
    }

    /** The block a {@code setblock} or {@code fill} argument names, without its state or its data. */
    private static String block(String argument) {
        int state = argument.indexOf('[');
        int data = argument.indexOf('{');
        int end = state < 0 ? argument.length() : state;
        if (data >= 0 && data < end) {
            end = data;
        }
        return argument.substring(0, end);
    }

    private static Placed lay(String function) {
        return lay(lines(function));
    }

    /**
     * Runs a function's {@code setblock} and {@code fill} lines over an empty world.
     *
     * <p>Only those two: a {@code data merge block} changes what is already there and places nothing, and a
     * {@code tellraw} is only ever talk. Air removes, so an absence means air throughout - which is how the
     * predicates {@link RiftGateStructure} takes read a cleared opening.
     *
     * <p>Takes the lines rather than the file so a prefix of them can be laid instead, which is what
     * {@link #everyRingIsClosedOnlyAfterItsIdIsWritten} needs.
     */
    private static Placed lay(List<String> lines) {
        Map<BlockPos, String> blocks = new LinkedHashMap<>();
        for (String line : lines) {
            String[] words = line.trim().split("\\s+");
            boolean fill = words[0].equals("fill");
            if (!fill && !words[0].equals("setblock")) {
                continue;
            }
            String block = block(words[fill ? 7 : 4]);
            BlockPos from = new BlockPos(relative(words[1]), relative(words[2]), relative(words[3]));
            BlockPos to = fill
                    ? new BlockPos(relative(words[4]), relative(words[5]), relative(words[6]))
                    : from;
            for (int x = Math.min(from.getX(), to.getX()); x <= Math.max(from.getX(), to.getX()); x++) {
                for (int y = Math.min(from.getY(), to.getY()); y <= Math.max(from.getY(), to.getY()); y++) {
                    for (int z = Math.min(from.getZ(), to.getZ()); z <= Math.max(from.getZ(), to.getZ()); z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (block.equals("minecraft:air")) {
                            blocks.remove(pos);
                        } else {
                            blocks.put(pos, block);
                        }
                    }
                }
            }
        }
        return new Placed(blocks);
    }

    /** Every opening the bench stands up, by its controller, in west-to-east order. */
    private static Map<BlockPos, RiftGateShape> openings(Placed bench) {
        Map<BlockPos, RiftGateShape> shapes = new LinkedHashMap<>();
        for (BlockPos controller : bench.of("aerowarptics:rift_gate")) {
            RiftGateStructure.Opening opening = RiftGateStructure.find(controller, bench.frame(), bench.open());
            assertNotNull(opening, "the ring round the controller at " + controller + " does not form");
            shapes.put(controller, opening.shape());
        }
        return shapes;
    }

    /**
     * Every lane the bench's own track would make, found the way the mod finds them.
     *
     * <p>{@code RiftGateBlockEntity.noticeTracks} walks every column of an opening, takes the lowest open cell of
     * it, and makes a lane of any track running into that cell from either side. This does the same, so a track run
     * into a column the bench did not mean to use is a lane this finds and the count below refuses.
     */
    private static List<Lane> lanes(Placed bench) {
        List<Lane> found = new ArrayList<>();
        openings(bench).forEach((controller, shape) -> {
            for (int across = 0; across < shape.width(); across++) {
                int up = RailLaneMap.lowestOpen(shape, across);
                if (up < 0) {
                    continue;
                }
                BlockPos cell = RailLaneMap.cellAt(shape, across, up);
                for (Direction.AxisDirection sign : Direction.AxisDirection.values()) {
                    Direction outward = Direction.fromAxisAndDirection(shape.normal(), sign);
                    BlockPos track = cell.relative(outward);
                    if (!"create:track".equals(bench.blocks().get(track))) {
                        continue;
                    }
                    RailLaneMap.NearResult near = RailLaneMap.near(shape, track, outward.getOpposite());
                    assertNull(near.refusal(), "the bench runs track into " + cell + " and the mod refuses it as a"
                            + " lane: " + near.refusal());
                    assertNotNull(near.near());
                    found.add(new Lane(controller, shape, across, near.near()));
                }
            }
        });
        return found;
    }

    /** Each controller's written gate id, and the route it was given, as the digits in the pack. */
    private static Map<BlockPos, String> written(Pattern what) {
        Map<BlockPos, String> byController = new LinkedHashMap<>();
        for (String line : lines(RAILS)) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("data merge block")) {
                continue;
            }
            Matcher found = what.matcher(trimmed);
            if (!found.find()) {
                continue;
            }
            String[] words = trimmed.split("\\s+");
            byController.put(new BlockPos(relative(words[3]), relative(words[4]), relative(words[5])),
                    found.group(1));
        }
        return byController;
    }

    /** Both rings and every lane: the whole rail bench put through the mod's rules in one place. */
    @Test
    void everyRingTheRailBenchLaysFormsAGate() {
        Placed bench = lay(RAILS);
        Map<BlockPos, RiftGateShape> shapes = openings(bench);
        assertEquals(3, shapes.size(), "the rail bench stands three gates up: two origins and the hub");
        shapes.forEach((controller, shape) -> {
            assertEquals(15, shape.openCells(),
                    "the bench's openings are five by three, as its README and its signs say");
            assertEquals(Direction.Axis.X, shape.normal(),
                    "the bench's track runs along X, so its openings must face along X");
            assertEquals(5, shape.width(), "five columns is what gives room for two lanes a side and a walkway");
        });
    }

    /**
     * Every track the bench runs into a gate really would be a lane, in the column the layout claims.
     *
     * <p>The one that matters most: a track into any cell but the lowest open one of its column is refused
     * ({@link RailLaneMap.Refusal#NOT_BOTTOM_ROW}), and a bench whose rings sat a block higher would look right,
     * build cleanly, and never carry a train. The columns are pinned too, because the ground left empty for the far
     * ends is worked out from them - get the column wrong and the stub lands under the bench's own track.
     */
    @Test
    void everyTrackTheRailBenchRunsIntoAGateIsALane() {
        Placed bench = lay(RAILS);
        List<Lane> lanes = lanes(bench);
        assertEquals(4, lanes.size(), "two lanes into each of the two origin gates, and none into the hub");

        Map<BlockPos, Set<Integer>> columns = new LinkedHashMap<>();
        for (Lane lane : lanes) {
            columns.computeIfAbsent(lane.controller(), key -> new TreeSet<>()).add(lane.across());
            assertEquals(lane.across(), lane.near().across(), "the mod reads a different column from the one laid");
        }
        assertEquals(2, columns.size(), "only the two origin gates have track run into them");
        for (Set<Integer> used : columns.values()) {
            assertEquals(2, used.size(), "each origin gate takes two lanes");
            assertFalse(used.contains(2), "column 2 is the walkway and must stay trackless");
        }
        assertEquals(Set.of(Set.of(0, 1), Set.of(3, 4)), Set.copyOf(columns.values()),
                "one origin uses the north pair of columns and the other the south pair");
    }

    /**
     * The ground the mod lays every lane's far end on is left empty.
     *
     * <p>A stub comes out of the far gate's matching column and an apron of straight track comes off the stub.
     * Track already standing there is somebody else's, and the bench must not be the one to put it there. Where the
     * far end lands is not guessed: the lane's own near end and the routed gate's shape go through
     * {@link RailLaneMap#far}, which is the same arithmetic the mod runs.
     */
    @Test
    void theRailBenchLeavesEveryLanesStubAndApronToTheMod() {
        Placed bench = lay(RAILS);
        Map<BlockPos, RiftGateShape> shapes = openings(bench);
        Map<BlockPos, String> ids = written(GATE_ID);
        Map<BlockPos, String> routes = written(DESTINATION);
        Map<String, BlockPos> byId = new HashMap<>();
        ids.forEach((controller, id) -> byId.put(id, controller));
        int apron = configured("apronLength");

        List<Lane> lanes = lanes(bench);
        assertFalse(lanes.isEmpty());
        for (Lane lane : lanes) {
            String route = routes.get(lane.controller());
            assertNotNull(route, "the gate at " + lane.controller() + " has a lane run into it but no route, so the"
                    + " mod would never lay its far end");
            BlockPos far = byId.get(route);
            assertNotNull(far, "the route written at " + lane.controller() + " names no gate this bench builds");
            RailLaneMap.Far landing = RailLaneMap.far(lane.shape(), lane.near(), shapes.get(far), apron);
            assertNotNull(landing, "the routed gate for " + lane.controller() + " has no opening to come out of");

            List<BlockPos> theirs = new ArrayList<>(landing.apron());
            theirs.add(landing.stub());
            for (BlockPos pos : theirs) {
                assertFalse(bench.blocks().containsKey(pos),
                        "the bench lays " + bench.blocks().get(pos) + " at " + pos + ", where the mod lays the far"
                                + " end of the lane entering " + lane.near().cell());
            }
        }
    }

    /**
     * Every motor the bench stands up turns fast enough to hold a gate open.
     *
     * <p>A Creative Motor makes 16 rpm where it is left alone and a gate wants 32, so the bench sets each one by
     * hand. Lower the config figure or raise the motor's and this is the test that notices they have crossed.
     */
    @Test
    void theRailBenchesMotorsBeatTheMinimumRpm() {
        int wanted = configured("gateMinimumRpm");
        Placed bench = lay(RAILS);
        int gates = bench.of("aerowarptics:rift_gate").size();

        Matcher scrolled = Pattern.compile("\\{ScrollValue:(\\d+)}").matcher(read(PACK.resolve(RAILS)));
        List<Integer> speeds = new ArrayList<>();
        while (scrolled.find()) {
            speeds.add(Integer.parseInt(scrolled.group(1)));
        }
        assertEquals(gates, speeds.size(), "the rail bench sets one motor for every gate it stands up");
        for (int speed : speeds) {
            assertTrue(speed >= wanted, "a bench motor turns at " + speed + " rpm and a gate needs " + wanted);
        }
    }

    /**
     * Every route the bench writes names a gate the bench builds.
     *
     * <p>The routes are written as hand-typed UUIDs, and a typo in one is as quiet as a bench bug gets: the gate
     * forms, the ring looks right, and the lane simply never appears because the destination is a gate that does not
     * exist. Only gates with track run into them need a route - {@code noticeTracks} does nothing without one, and a
     * gate asked to open for a train is handed its target directly - so the hub is expected to have none.
     */
    @Test
    void everyRouteTheRailBenchWritesNamesAGateItBuilds() {
        Placed bench = lay(RAILS);
        Map<BlockPos, String> ids = written(GATE_ID);
        Map<BlockPos, String> routes = written(DESTINATION);

        assertEquals(Set.copyOf(bench.of("aerowarptics:rift_gate")), ids.keySet(),
                "every gate the bench stands up is given a written id, and nothing else is");
        assertEquals(ids.size(), Set.copyOf(ids.values()).size(), "two gates share a written id");

        routes.forEach((controller, route) -> assertTrue(ids.containsValue(route),
                "the gate at " + controller + " is routed to " + route + ", which is no gate this bench builds"));

        Set<BlockPos> withTrack = lanes(bench).stream().map(Lane::controller).collect(Collectors.toSet());
        assertEquals(withTrack, routes.keySet(),
                "exactly the gates with track run into them carry a route: the hub needs none, and a gate with track"
                        + " and no route lays no lane");
    }

    /**
     * No ring is closed until its id has been written - which is the whole reason the routes can be scripted.
     *
     * <p>A gate mints a random id the first time anything asks for one, and {@code tryForm} asks as the controller
     * is placed. So the bench lays each ring a block short, writes the id and the route into a gate that has not
     * formed yet, and only then closes it: {@code unform} leaves {@code gateId} null, and the gate forms on its own
     * revalidate with the id it was given. Reorder those three commands and the bench still builds, still looks
     * right, and quietly registers three random ids whose routes point at nothing. This is the test that notices.
     */
    @Test
    void everyRingIsClosedOnlyAfterItsIdIsWritten() {
        List<String> lines = lines(RAILS);
        Placed whole = lay(lines);
        Map<BlockPos, String> ids = written(GATE_ID);
        assertFalse(ids.isEmpty(), "the rail bench writes no gate ids at all");

        for (BlockPos controller : ids.keySet()) {
            int merge = -1;
            for (int line = 0; line < lines.size(); line++) {
                String trimmed = lines.get(line).trim();
                if (trimmed.startsWith("data merge block") && GATE_ID.matcher(trimmed).find()) {
                    String[] words = trimmed.split("\\s+");
                    if (new BlockPos(relative(words[3]), relative(words[4]), relative(words[5])).equals(controller)) {
                        merge = line;
                        break;
                    }
                }
            }
            assertTrue(merge >= 0, "no id is written for the gate at " + controller);

            Placed sofar = lay(lines.subList(0, merge + 1));
            assertNull(RiftGateStructure.find(controller, sofar.frame(), sofar.open()),
                    "the ring at " + controller + " is already complete when its id is written, so the gate will"
                            + " have minted a random one first and the route written here points at nothing");
            assertNotNull(RiftGateStructure.find(controller, whole.frame(), whole.open()),
                    "the ring at " + controller + " is never closed");
        }
    }

    /**
     * No sign the bench writes breaks out of its own quoting.
     *
     * <p>A sign's messages are single-quoted SNBT strings holding double-quoted JSON, so a single apostrophe in
     * the prose ends its string early. Minecraft then refuses the <em>whole function</em> at load and
     * {@code /function} reports it as unknown - which looks like a missing datapack and nothing at all like a
     * typo in a sign, and cost a session finding out (2026-10-26). An even count of apostrophes on a line that
     * places a block is the cheap form of the check: this pack never needs an escaped one, so reword instead.
     */
    @Test
    void noSignTextBreaksOutOfItsQuotes() {
        for (Path function : functions()) {
            List<String> lines = read(function).lines().toList();
            for (int line = 0; line < lines.size(); line++) {
                String trimmed = lines.get(line).trim();
                if (!trimmed.startsWith("setblock") && !trimmed.startsWith("fill")) {
                    continue;
                }
                assertEquals(0L, trimmed.chars().filter(character -> character == 0x27).count() % 2L,
                        function.getFileName() + " line " + (line + 1) + " leaves an SNBT string open: an"
                                + " apostrophe in sign text ends it early, and Minecraft refuses the whole"
                                + " function at load rather than just that sign. Reword it without one.");
            }
        }
    }

    /**
     * Every Create block the bench places, and every Create item it stocks, is one Create actually has.
     *
     * <p>{@link #everyModIdTheBenchNamesStillExists} covers this mod's own ids and nothing else, and the gap cost
     * a session: the bench stocked {@code create:small_bogey}, Create has no such item - bogeys are made by using
     * Train Casing on a track - so the barrel quietly dropped them and the only complaint was a
     * "Tried to load invalid item" line in a log nobody reads (2026-10-26).
     *
     * <p>Blocks and items are checked separately, and against different evidence, because that distinction is the
     * whole of the bug: {@code small_bogey} is a perfectly real block that no item places. A registered block has
     * a blockstate file; a registered item has an item model. Create's jar is gitignored, so where it has not
     * been unpacked this stands aside instead of failing.
     */
    @Test
    void everyCreateIdTheBenchNamesIsOneCreateHas() throws IOException {
        List<Path> jars;
        try (Stream<Path> found = Files.list(Path.of("Depends"))) {
            jars = found.filter(path -> path.getFileName().toString().startsWith("create-")
                    && path.getFileName().toString().endsWith(".jar")).sorted().toList();
        } catch (IOException absent) {
            jars = List.of();
        }
        assumeTrue(!jars.isEmpty(), "Depends/create-*.jar is not unpacked here, so Create's registry cannot be read");

        Set<String> entries = new HashSet<>();
        try (ZipFile jar = new ZipFile(jars.getFirst().toFile())) {
            jar.stream().forEach(entry -> entries.add(entry.getName()));
        }

        Pattern stocked = Pattern.compile("id:\"create:([a-z0-9_]+)\"");
        for (Path function : functions()) {
            for (String line : read(function).lines().toList()) {
                String trimmed = line.trim();
                if (trimmed.startsWith("#")) {
                    continue;
                }
                String[] words = trimmed.split("\\s+");
                boolean fill = words[0].equals("fill");
                if ((fill || words[0].equals("setblock")) && words.length > (fill ? 7 : 4)) {
                    String placed = block(words[fill ? 7 : 4]);
                    if (placed.startsWith("create:")) {
                        String id = placed.substring("create:".length());
                        assertTrue(entries.contains("assets/create/blockstates/" + id + ".json"),
                                function.getFileName() + " places create:" + id + ", which is no block Create has");
                    }
                }
                Matcher items = stocked.matcher(trimmed);
                while (items.find()) {
                    String id = items.group(1);
                    assertTrue(entries.contains("assets/create/models/item/" + id + ".json"),
                            function.getFileName() + " stocks create:" + id + ", which is no item Create has. It may"
                                    + " well be a block all the same - that is exactly how small_bogey got in.");
                }
            }
        }
    }

    /**
     * Every block and item the pack names is one the mod still has.
     *
     * <p>Against the lang file rather than the registries, because that is the one list every registered thing is
     * on whatever way it was registered - a drive tier builds its own name, and a plain item is registered by a
     * different call from a block. {@code LangCoverageTest} is what keeps that list honest.
     */
    @Test
    void everyModIdTheBenchNamesStillExists() {
        JsonObject lang = JsonParser.parseString(read(LANG)).getAsJsonObject();
        Set<String> known = lang.keySet().stream()
                .map(Pattern.compile("^(?:block|item|fluid)\\.aerowarptics\\.([a-z0-9_]+)$")::matcher)
                .filter(Matcher::matches)
                .map(matcher -> matcher.group(1))
                .collect(Collectors.toSet());

        Pattern named = Pattern.compile("aerowarptics:([a-z0-9_]+)");
        for (Path function : functions()) {
            for (String line : read(function).lines().toList()) {
                if (line.strip().startsWith("#")) {
                    continue;
                }
                Matcher ids = named.matcher(line);
                while (ids.find()) {
                    assertTrue(known.contains(ids.group(1)), function.getFileName() + " names aerowarptics:"
                            + ids.group(1) + ", which the mod no longer has");
                }
            }
        }
    }
}
