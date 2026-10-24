package uk.co.iceconchy.aerowarptics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.fissure.FissureDrain;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rift Fissures: the arithmetic, and the chain of data files that decides whether one ever appears.
 *
 * <p>Two quite different failures live here and both are silent. The first is essence being minted or
 * destroyed - a tear that hands out more than it holds, or a transfer that takes from the tear what
 * the vessel had no room for, neither of which throws and neither of which anybody would notice until
 * the numbers stopped adding up weeks later.
 *
 * <p>The second is worse, because it produces nothing at all. A structure reaches the world through
 * four files that name each other: a set names a structure, a structure names a template pool, a pool
 * names a template, a template contains blocks. One typo anywhere in that chain and the game logs a
 * line at startup that nobody reads, and the ruins simply never generate - which is indistinguishable
 * from bad luck for a very long time.
 */
class RiftFissureTest {

    private static final Path DATA = Path.of("src/main/resources/data/aerowarptics");

    // ---------------------------------------------------------------- the tear

    /** A fissure never hands out what it does not have, nor more than a vessel can hold. */
    @Test
    void aTransferIsBoundedByTheTearTheVesselAndTheRate() {
        assertEquals(10, FissureDrain.transfer(100, 100, 10), "the rate should bind");
        assertEquals(7, FissureDrain.transfer(7, 100, 10), "what is left should bind");
        assertEquals(3, FissureDrain.transfer(100, 3, 10), "the vessel's room should bind");
        assertEquals(0, FissureDrain.transfer(0, 100, 10));
        assertEquals(0, FissureDrain.transfer(100, 0, 10));
        assertEquals(0, FissureDrain.transfer(-50, 100, 10), "a spent tear gives nothing");
        assertEquals(0, FissureDrain.transfer(100, -5, 10), "a full vessel takes nothing");
    }

    /**
     * Draining a fissure a tick at a time empties it exactly.
     *
     * <p>The property that matters is conservation: what the vessel gained is what the tear lost, to
     * the millibucket, however the rate divides into the reservoir.
     */
    @Test
    void drainingATearMovesExactlyWhatWasInIt() {
        for (int rate : new int[]{1, 7, 12, 500, 9_999}) {
            int reservoir = 6_000;
            int collected = 0;
            int guard = 0;
            while (reservoir > 0) {
                int moved = FissureDrain.transfer(reservoir, Integer.MAX_VALUE, rate);
                assertTrue(moved > 0, "a drain at rate " + rate + " stalled with " + reservoir + " left");
                reservoir -= moved;
                collected += moved;
                assertTrue(++guard < 100_000, "a drain at rate " + rate + " never finished");
            }
            assertEquals(6_000, collected, "rate " + rate + " did not conserve essence");
            assertEquals(0, reservoir);
        }
    }

    @Test
    void aReservoirIsAlwaysWithinItsBounds() {
        for (long seed = -5_000L; seed < 5_000L; seed += 7L) {
            int rolled = FissureDrain.reservoir(seed, 6_000, 18_000);
            assertTrue(rolled >= 6_000 && rolled < 18_000,
                    "seed " + seed + " rolled " + rolled + ", outside 6000..18000");
        }
        // A range with nothing in it is a fixed size rather than a crash or a negative modulus.
        assertEquals(6_000, FissureDrain.reservoir(1L, 6_000, 6_000));
        assertEquals(6_000, FissureDrain.reservoir(1L, 6_000, 10));
    }

    /**
     * The same tear is the same size for everybody, every time it is asked.
     *
     * <p>It is rolled rather than stored, so the server, every client, and the same chunk read back
     * after an unload all work it out independently. If that were not stable they would disagree, and
     * the disagreement would show as a tear that changes size when you walk away and come back.
     */
    @Test
    void aReservoirIsStableForAGivenPlace() {
        for (long seed : new long[]{0L, 1L, -1L, 4_398_046_511_104L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            assertEquals(FissureDrain.reservoir(seed, 6_000, 18_000),
                    FissureDrain.reservoir(seed, 6_000, 18_000),
                    "seed " + seed + " does not roll the same twice");
        }
    }

    /** Neighbouring fissures are not all much of a size, which a weak mix of a block position gives. */
    @Test
    void nearbyFissuresAreNotAllTheSameSize() {
        Set<Integer> sizes = new HashSet<>();
        // Positions a few blocks apart, which is what packed block positions actually look like.
        for (long step = 0; step < 40; step++) {
            sizes.add(FissureDrain.reservoir(0x4000_0000_0000L + step * 3L, 6_000, 18_000));
        }
        assertTrue(sizes.size() > 30,
                "forty neighbouring fissures produced only " + sizes.size() + " distinct sizes");
    }

    /** A tear closes visibly as it empties, and is never drawn at nothing while it still has some. */
    @Test
    void anEmptyingTearShrinksButDoesNotVanishEarly() {
        assertEquals(1.0F, FissureDrain.openness(1_000, 1_000), 1.0e-4F);
        assertEquals(0.0F, FissureDrain.openness(0, 1_000), 1.0e-4F);
        float previous = Float.MAX_VALUE;
        for (int left = 1_000; left > 0; left -= 50) {
            float open = FissureDrain.openness(left, 1_000);
            assertTrue(open > 0.0F, "a tear with " + left + "mB in it was drawn as gone");
            assertTrue(open <= previous, "a tear grew while it was being drained");
            previous = open;
        }
        // Nothing to divide by, rather than an exception.
        assertEquals(0.0F, FissureDrain.openness(500, 0), 1.0e-4F);
    }

    // --------------------------------------------------------------- the ruins

    private static JsonObject read(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    /** Turns {@code aerowarptics:rift_scar/ring} into the file it must be. */
    private static Path resolve(String id, String folder, String extension) {
        assertTrue(id.startsWith("aerowarptics:"), id + " is not this mod's");
        return DATA.resolve(folder).resolve(id.substring("aerowarptics:".length()) + extension);
    }

    /**
     * Every name in the chain from the structure set to the templates points at a file that exists.
     */
    @Test
    void theStructureChainResolvesEndToEnd() {
        JsonObject set = read(DATA.resolve("worldgen/structure_set/rift_scar.json"));
        JsonArray structures = set.getAsJsonArray("structures");
        assertTrue(!structures.isEmpty(), "the structure set names no structures");

        for (JsonElement element : structures) {
            String structureId = element.getAsJsonObject().get("structure").getAsString();
            Path structurePath = resolve(structureId, "worldgen/structure", ".json");
            assertTrue(Files.exists(structurePath), "no structure at " + structurePath);

            JsonObject structure = read(structurePath);
            String poolId = structure.get("start_pool").getAsString();
            Path poolPath = resolve(poolId, "worldgen/template_pool", ".json");
            assertTrue(Files.exists(poolPath), "no template pool at " + poolPath);

            String biomes = structure.get("biomes").getAsString();
            if (biomes.startsWith("#aerowarptics:")) {
                Path tag = DATA.resolve("tags/worldgen/biome")
                        .resolve(biomes.substring("#aerowarptics:".length()) + ".json");
                assertTrue(Files.exists(tag), "no biome tag at " + tag);
                assertTrue(!read(tag).getAsJsonArray("values").isEmpty(),
                        "the biome tag lists nothing, so the structure generates nowhere");
            }

            JsonObject pool = read(poolPath);
            JsonArray elements = pool.getAsJsonArray("elements");
            assertTrue(!elements.isEmpty(), "the template pool is empty");
            for (JsonElement entry : elements) {
                JsonObject piece = entry.getAsJsonObject().getAsJsonObject("element");
                Path template = resolve(piece.get("location").getAsString(), "structure", ".nbt");
                assertTrue(Files.exists(template), "no template at " + template);
            }
        }
    }

    /**
     * Every ruin actually has a fissure in it.
     *
     * <p>The ruins are generated by {@code tools/rift_scar_structure.py} and are gzipped NBT, so this
     * reads the palette out of them rather than trusting the script that wrote them. A Rift Scar with
     * no tear in it is a pile of bricks in a field, and the only way to find that out in game is to go
     * and stand in one wearing the goggles.
     */
    @Test
    void everyRuinContainsATear() {
        List<String> problems = new ArrayList<>();
        Path folder = DATA.resolve("structure/rift_scar");
        List<Path> templates;
        try (var stream = Files.list(folder)) {
            templates = stream.filter(path -> path.toString().endsWith(".nbt")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        assertTrue(templates.size() >= 2,
                "only " + templates.size() + " ruin(s) - every scar would look the same");

        for (Path template : templates) {
            String names = String.join(" ", blockNames(template));
            if (!names.contains("aerowarptics:rift_fissure")) {
                problems.add(template.getFileName() + " has no fissure in it");
            }
            if (!names.contains("minecraft:")) {
                problems.add(template.getFileName() + " looks empty");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /**
     * Pulls the block names out of a structure template.
     *
     * <p>A deliberately crude read: the palette entries are {@code Name} strings in the NBT, and every
     * string in the file is length-prefixed UTF-8, so scanning the decompressed bytes for text that
     * looks like a block id finds them without a full NBT parser in the test sources.
     */
    private static List<String> blockNames(Path template) {
        byte[] raw;
        try (DataInputStream stream = new DataInputStream(
                new GZIPInputStream(Files.newInputStream(template)))) {
            raw = stream.readAllBytes();
        } catch (IOException e) {
            throw new AssertionError("could not read " + template, e);
        }
        String text = new String(raw, StandardCharsets.UTF_8);
        List<String> found = new ArrayList<>();
        for (String candidate : text.split("[^a-z0-9_:]+")) {
            if (candidate.contains(":") && !candidate.endsWith(":")) {
                found.add(candidate);
            }
        }
        return found;
    }

    // -------------------------------------------------------------- the seal

    private static final Path FISSURE_SOURCE =
            Path.of("src/main/java/uk/co/iceconchy/aerowarptics/fissure/RiftFissureBlockEntity.java");

    private static String source(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A sealed fissure leaves exactly one Scar Heart, which never despawns.
     *
     * <p>Spawned rather than dropped: {@code popResource} drops nothing with {@code doTileDrops} off, and a
     * fissure has no loot table and must not get one. And unlimited, because a tear usually seals with nobody
     * watching - the heart has to be there when somebody comes back for it.
     */
    @Test
    void aSealLeavesExactlyOneHeartThatNeverDespawns() {
        String entity = source(FISSURE_SOURCE);
        int close = entity.indexOf("private void close()");
        String body = entity.substring(close, entity.indexOf("\n    }\n", close));
        assertEquals(1, body.split("new ItemEntity\\(", -1).length - 1, "a seal should spawn exactly one item entity");
        assertTrue(body.contains("AWItems.SCAR_HEART"), "what a seal leaves is not a Scar Heart");
        assertTrue(body.contains("setUnlimitedLifetime()"), "the heart would despawn before anybody came back");
        assertTrue(!Files.exists(DATA.resolve("loot_table/blocks/rift_fissure.json")), "a fissure gained a loot table");
    }

    /** A fissure refuses to move, so a hull assembled round one leaves it standing where it was. */
    @Test
    void aFissureRefusesToMove() {
        String block = source(Path.of("src/main/java/uk/co/iceconchy/aerowarptics/fissure/RiftFissureBlock.java"));
        String mod = source(Path.of("src/main/java/uk/co/iceconchy/aerowarptics/AeroWarptics.java"));
        assertTrue(block.contains("registerMovementAllowedCheck"), "no movement check is registered");
        assertTrue(block.contains("CheckResult.FAIL"), "the movement check never refuses");
        assertTrue(mod.contains("RiftFissureBlock::onCommonSetup"), "the movement check is never registered");
    }

    /**
     * A fissure changes no block but its own.
     *
     * <p>It once grew ore in the stone round it, and that is retired for good: a fissure never touches the
     * ground about it. Read off the source so that seeding cannot quietly come back - the only block a fissure
     * may set is its own, and only to remove itself.
     */
    @Test
    void aFissureChangesNoBlockButItsOwn() {
        String entity = source(FISSURE_SOURCE);
        for (String call : List.of("setBlock(", "setBlockAndUpdate(", "destroyBlock(", "setBlocksDirty(")) {
            assertTrue(!entity.contains(call), "a fissure calls " + call + " - it may change no block but its own");
        }
        // Its own block, by its two ends: sealed, which leaves a heart, and consumed by a meltdown, which does not.
        assertEquals(2, entity.split("removeBlock\\(worldPosition", -1).length - 1,
                "a fissure should remove exactly one block, its own, when it seals or is consumed");
        assertTrue(!entity.contains("Seeded") && !entity.contains("maybeSeedOre"), "ore seeding has come back");
    }

    /**
     * A tear a Rift Wellhead holds is the well's: it hands nothing to a siphon and rifts nothing, and the tear
     * a meltdown takes leaves no heart. Read off the source, since each is one line that compiles without it.
     */
    @Test
    void aHarnessedFissureIsTheWellsAlone() {
        String entity = source(FISSURE_SOURCE);
        int drain = entity.indexOf("private int drainInto()");
        String guard = entity.substring(drain, entity.indexOf("int rate", drain));
        assertTrue(guard.contains("harness != null"), "a harnessed fissure still drains into siphons");

        int consume = entity.indexOf("public void consume()");
        String body = entity.substring(consume, entity.indexOf("\n    }", consume));
        assertTrue(!body.contains("SCAR_HEART") && !body.contains("ItemEntity"), "a meltdown left a heart");

        String rifting = source(Path.of("src/main/java/uk/co/iceconchy/aerowarptics/rifting/RiftingType.java"));
        int catalyst = rifting.indexOf("public static boolean isCatalyst");
        assertTrue(rifting.substring(catalyst, rifting.indexOf("\n    }", catalyst)).contains("HARNESSED"),
                "a harnessed fissure is still a rifting catalyst");

        // And it is the wellhead that marks it, never the fissure: see aFissureChangesNoBlockButItsOwn.
        String assembly = source(Path.of("src/main/java/uk/co/iceconchy/aerowarptics/wellhead/WellheadAssembly.java"));
        assertTrue(assembly.contains("RiftFissureBlock.HARNESSED, true")
                && assembly.contains("RiftFissureBlock.HARNESSED, false"), "nothing marks a fissure harnessed");
    }

    /**
     * How much a found fissure holds is rolled from where it is, never stored in the ruin that places it. The one
     * stored reservoir is a conjured tear's, handed it by the Sunderer that tore it.
     */
    @Test
    void theReservoirIsRolledFromThePlaceRatherThanStored() {
        String entity = source(FISSURE_SOURCE);
        assertTrue(entity.contains("FissureDrain.reservoir(worldPosition.asLong()"),
                "a fissure's size is no longer rolled from its position");
        int roll = entity.indexOf("FissureDrain.reservoir(worldPosition.asLong()");
        assertTrue(entity.substring(entity.lastIndexOf("if (", roll), roll).contains("reservoir < 0"),
                "the roll no longer waits for a reservoir nobody has given it");
        assertTrue(entity.contains("public void conjure(int reservoir, long unsettledTicks)"),
                "a conjured tear has no way to be given its reservoir");
        String drain = source(Path.of("src/main/java/uk/co/iceconchy/aerowarptics/fissure/FissureDrain.java"));
        assertTrue(drain.contains("Rolled from the position rather than from the level's random"),
                "the reason the reservoir is rolled from the place has gone from FissureDrain");
    }

    /**
     * A conjured tear is the Sunderer's bargain and nothing more: it seals with no heart (the Singularity is gated
     * on a found one), never drains into a siphon (or tearing one and emptying it would pay), and gutters shut
     * only while nothing holds it. Read off the source, since each is one condition that compiles without it.
     */
    @Test
    void aConjuredTearIsTheSunderersBargain() {
        String entity = source(FISSURE_SOURCE);

        int close = entity.indexOf("private void close()");
        String seal = entity.substring(close, entity.indexOf("\n    }\n", close));
        int guard = seal.indexOf("if (conjured)");
        assertTrue(guard >= 0 && guard < seal.indexOf("new ItemEntity("), "a conjured tear's seal is not kept from the heart");
        assertTrue(seal.substring(guard, seal.indexOf("new ItemEntity(")).contains("} else {"),
                "the heart is not in the found tear's branch alone");

        int drain = entity.indexOf("private int drainInto()");
        assertTrue(entity.substring(drain, entity.indexOf("int rate", drain)).contains("conjured"),
                "a conjured tear drains into siphons");

        int tick = entity.indexOf("private void serverTick()");
        String server = entity.substring(tick, entity.indexOf("\n    }\n", tick));
        int gutter = server.indexOf("unsettledUntil");
        assertTrue(gutter >= 0, "a conjured tear never gutters");
        String condition = server.substring(server.lastIndexOf("if (", gutter), server.indexOf("{", gutter));
        assertTrue(condition.contains("conjured") && condition.contains("harness == null"),
                "a tear gutters whether or not a wellhead holds it: " + condition);

        int release = entity.indexOf("public void release()");
        assertTrue(entity.substring(release, entity.indexOf("\n    }\n", release)).contains("unsettledUntil ="),
                "a frame letting go does not restart the conjured tear's clock");
    }

    /** A revealed tear is saved and sent to the client, so a newborn one is seen by everybody watching. */
    @Test
    void aRevealIsSavedAndSent() {
        String entity = source(FISSURE_SOURCE);
        int write = entity.indexOf("protected void write(");
        assertTrue(entity.substring(write, entity.indexOf("\n    }\n", write)).contains("\"Revealed\""),
                "a reveal is not written, so the client never hears of it");
        int read = entity.indexOf("protected void read(");
        assertTrue(entity.substring(read, entity.indexOf("\n    }\n", read)).contains("\"Revealed\""),
                "a reveal is not read back");
        assertTrue(entity.contains("public void reveal(long ticks)"));
    }
}
