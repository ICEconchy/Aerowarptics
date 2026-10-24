package uk.co.iceconchy.aerowarptics.wellhead;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parts of a Rift Wellhead that need a world to run, read off the source instead: each is a line that
 * compiles without it and fails silently, and none of them can be reached by a test with no game. And the
 * crater, which is arithmetic and can simply be counted.
 */
class WellheadWiringTest {

    private static final Path WELLHEAD = Path.of("src/main/java/uk/co/iceconchy/aerowarptics/wellhead");

    private static String source(String file) {
        try {
            return Files.readString(WELLHEAD.resolve(file)).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new AssertionError("cannot read " + file, e);
        }
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "no " + signature);
        return source.substring(start, source.indexOf("\n    }", start));
    }

    // ------------------------------------------------------------------ the crater

    @Test
    void aCraterIsABallTakenFromTheMiddleOutwards() {
        for (int radius : new int[]{1, 16, 28, 48}) {
            int[][] shells = CraterPlan.shells(radius);
            assertEquals(radius + 1, shells.length);
            assertEquals(1, shells[0].length, "the middle is not the first thing taken");
            assertEquals(CraterPlan.pack(0, 0, 0), shells[0][0]);
            Set<Integer> seen = new HashSet<>();
            double last = -1.0D;
            for (int[] shell : shells) {
                double nearest = Double.MAX_VALUE;
                for (int packed : shell) {
                    int x = CraterPlan.x(packed);
                    int y = CraterPlan.y(packed);
                    int z = CraterPlan.z(packed);
                    assertEquals(packed, CraterPlan.pack(x, y, z), "an offset does not survive being packed");
                    assertTrue(seen.add(packed), "(" + x + ", " + y + ", " + z + ") is taken twice");
                    assertTrue(CraterPlan.within(x, y, z, radius));
                    nearest = Math.min(nearest, Math.sqrt(x * x + y * y + z * z));
                }
                assertTrue(shell.length > 0, "a shell of radius " + radius + " is empty");
                assertTrue(nearest > last, "a shell is nearer the middle than the one before it");
                last = nearest;
            }
            assertEquals(seen.size(), CraterPlan.blocks(radius));
            // A ball, give or take its blocky skin - not a cube (a quarter as many again) and not a diamond.
            double ball = 4.0D / 3.0D * Math.PI * Math.pow(radius + 0.5D, 3);
            assertEquals(ball, seen.size(), ball * 0.12D + 8.0D, "radius " + radius);
        }
        CraterPlan.forget();
    }

    @Test
    void aCraterReachesItsRadiusAlongEveryAxisAndNoFurther() {
        for (int radius : new int[]{16, 28, 48}) {
            assertTrue(CraterPlan.within(radius, 0, 0, radius));
            assertTrue(CraterPlan.within(0, -radius, 0, radius));
            assertFalse(CraterPlan.within(radius + 1, 0, 0, radius));
            assertFalse(CraterPlan.within(radius, radius, radius, radius), "the crater has corners");
        }
    }

    /** The largest crater is half a million blocks; the default budget has to get through it inside the detonation. */
    @Test
    void theDefaultBudgetTakesTheLargestCraterInsideItsDetonation() {
        int length = 900;
        int budget = 8_000;
        double detonation = (MeltdownChoreography.BLAST_END - MeltdownChoreography.HUSH_END) * length;
        int blocks = CraterPlan.blocks(48);
        assertTrue(blocks > 400_000 && blocks < 520_000, "a radius-48 crater is " + blocks + " blocks");
        assertTrue(blocks <= budget * detonation, blocks + " blocks do not fit " + (int) detonation + " ticks of " + budget);
        CraterPlan.forget();
    }

    /** The bowl's lining is the ground just outside the crater, and no block of the crater itself. */
    @Test
    void theLiningIsJustOutsideTheCrater() {
        for (int radius : new int[]{16, 28}) {
            int[] lining = CraterPlan.lining(radius);
            assertTrue(lining.length > 4.0D * Math.PI * radius * radius, "a lining thinner than the wall it lines");
            for (int packed : lining) {
                int x = CraterPlan.x(packed);
                int y = CraterPlan.y(packed);
                int z = CraterPlan.z(packed);
                assertFalse(CraterPlan.within(x, y, z, radius), "the lining is inside the crater");
                assertTrue(Math.sqrt(x * x + y * y + z * z) <= radius + 2.0D, "the lining reaches past the wall");
            }
        }
    }

    /** Ground left hanging hangs clear of the wall, apart from its neighbours, and is the same for the same seed. */
    @Test
    void theHangingGroundIsInsideTheCraterAndKnownFromItsSeed() {
        for (int radius : new int[]{16, 28, 48}) {
            List<int[]> islands = CraterPlan.islands(42L, radius, 9);
            assertFalse(islands.isEmpty(), "nothing is left hanging in a crater of radius " + radius);
            List<int[]> again = CraterPlan.islands(42L, radius, 9);
            assertEquals(islands.size(), again.size());
            for (int i = 0; i < islands.size(); i++) {
                int[] island = islands.get(i);
                assertEquals(java.util.Arrays.toString(island), java.util.Arrays.toString(again.get(i)));
                assertTrue(island[1] < 0, "ground is left hanging above the fissure, where there is none");
                assertTrue(Math.sqrt(island[0] * island[0] + island[1] * island[1] + island[2] * island[2]) + island[3]
                        < radius, "a blob touches the wall");
                assertTrue(CraterPlan.inIsland(island[0], island[1], island[2], islands));
            }
            assertFalse(CraterPlan.inIsland(0, radius, 0, islands), "the top of the crater is part of a blob");
        }
    }

    // ------------------------------------------------------------------ the meltdown

    /** A dry run is the scene and nothing else: everything that changes a block is behind the one switch. */
    @Test
    void aDryRunChangesNothingInTheWorld() {
        String meltdowns = source("Meltdowns.java");
        assertTrue(meltdowns.contains("boolean breaks = !dry &&"), "a dry meltdown may break blocks");
        String tick = method(meltdowns, "private boolean tick(ServerLevel level)");
        String detonate = method(meltdowns, "private void detonate(");
        for (String call : List.of("breach(level", "clearFrame(level", "peel(level", "carve(level", "line(level",
                "settle(level", "veins(level", "fallout(level", "islands(level")) {
            String body = call.equals("islands(level") ? detonate : tick;
            int at = body.indexOf(call);
            assertTrue(at >= 0, "nothing calls " + call);
            while (at >= 0) {
                int guard = body.lastIndexOf("if (breaks)", at);
                assertTrue(guard >= 0 && at - guard < 520, call + " is called without asking whether blocks may break");
                at = body.indexOf(call, at + 1);
            }
        }
        for (String harm : List.of("draw(level", "wave(level", "strike(level", "ScarZones.leave(")) {
            int at = tick.indexOf(harm);
            int guard = tick.lastIndexOf("if (!dry)", at);
            assertTrue(at >= 0 && guard >= 0 && at - guard < 120, harm + " runs in a dry meltdown");
        }
        assertTrue(detonate.indexOf("if (!dry)") < detonate.indexOf("fissure.consume()"), "a dry meltdown takes a fissure");
        assertTrue(detonate.indexOf("if (!dry)") < detonate.indexOf("RIFT_WOUND"), "a dry meltdown leaves a wound");
    }

    /** Held by the one chunk the tear stands in, a large crater skipped everything past it. */
    @Test
    void aMeltdownHoldsItsGroundOutToWhatItTakes() {
        assertTrue(source("Meltdowns.java").contains(
                        "Holds.holdGround(level, centre, (int) Math.ceil(crater * OUTER_REACH / 16.0D) + 1)"),
                "a meltdown's hold does not reach the edge of its crater");
    }

    /** A wound is a meltdown's alone to leave, is nothing a player makes or collects, and no frame forms round one. */
    @Test
    void aWoundIsLeftByAMeltdownAndByNothingElse() throws IOException {
        try (java.util.stream.Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (Files.readString(file).contains("RIFT_WOUND.get().defaultBlockState()")) {
                    assertEquals("Meltdowns.java", file.getFileName().toString(), file + " places a Rift Wound");
                }
            }
        }
        Path resources = Path.of("src/main/resources");
        assertTrue(Files.exists(resources.resolve("assets/aerowarptics/blockstates/rift_wound.json")));
        assertFalse(Files.exists(resources.resolve("data/aerowarptics/recipe/rift_wound.json")));
        assertFalse(Files.exists(resources.resolve("data/aerowarptics/loot_table/blocks/rift_wound.json")));
        assertFalse(Files.exists(resources.resolve("assets/aerowarptics/models/item/rift_wound.json")));
        assertFalse(source("WellheadAssembly.java").contains("RiftWound"), "the frame's survey knows of wounds");
        // Spared by being unbreakable, as a fissure's own block is - not by a name the rule would have to keep up with.
        assertTrue(method(source("WellheadMeltdown.java"), "static boolean mayTake(").contains("getDestroySpeed"));
    }

    // ------------------------------------------------------------------ the machine

    /** A scram that still answered in a meltdown would let a lever undo one. */
    @Test
    void aScramDoesNothingOnceAMeltdownHasBegun() {
        String scram = method(source("RiftCollarBlockEntity.java"), "public boolean scram()");
        assertTrue(scram.indexOf("Band.MELTDOWN") >= 0 && scram.indexOf("Band.MELTDOWN") < scram.indexOf("reactor.dump()"),
                "a scram dumps the gullet before asking whether it is too late");
    }

    /** The hatch takes only what the well digests; a handler that takes anything lets a pipe fill it with anything. */
    @Test
    void theHatchRefusesBeforeItSwallowsAndTheTapOnlyGives() {
        String collar = source("RiftCollarBlockEntity.java");
        String accepts = method(collar, "private int accepts(ItemStack stack)");
        assertTrue(accepts.contains("lockoutSeconds > 0"), "the hatch takes feed during a scram's lockout");
        assertTrue(accepts.contains("recipeFor(stack)"), "the hatch takes what has no recipe");
        assertTrue(accepts.contains("WELLHEAD_GULLET"), "the hatch takes feed with no room for it");
        String insert = method(collar, "public ItemStack insertItem(int slot, ItemStack stack, boolean simulate)");
        assertTrue(insert.indexOf("accepts(stack)") < insert.indexOf("swallow("), "the hatch swallows before it asks");
        assertTrue(insert.contains("!simulate"), "a simulated insert feeds the well");
        String fill = method(collar, "public int fill(FluidStack resource, FluidAction action)");
        assertTrue(fill.contains("return 0;"), "a pipe can push fluid into a well's tap");
    }

    /** Let go first: a part being swapped back asks whether it still has a machine, and has to hear no. */
    @Test
    void aFrameIsLetGoBeforeItsBlocksAreSwappedBack() {
        String unform = method(source("WellheadAssembly.java"), "public static void unform(");
        assertTrue(unform.indexOf("collar.setRadius(0)") < unform.indexOf("shell.original(state)"),
                "a frame's parts are swapped back while they still answer to it - each would break it open");
        String form = method(source("WellheadAssembly.java"), "private static void form(");
        assertTrue(form.indexOf("collar.setRadius(radius)") < form.indexOf("WellheadStructure.shell(radius)"),
                "a frame's parts are set down before the collar holds it - each would take itself for an orphan");
    }

    /** A refusal leaves every block as the player set it: the survey changes nothing. */
    @Test
    void lookingAtAFrameChangesNothing() {
        String survey = method(source("WellheadAssembly.java"), "public static Survey survey(");
        for (String call : List.of("setBlock(", "removeBlock(", "destroyBlock(", ".harness(", ".release(")) {
            assertFalse(survey.contains(call), "the survey calls " + call);
        }
    }

    /** Create reports no rotation for the first ticks after a load; a well must not take that for a stopped shaft. */
    @Test
    void aLoadedWellWaitsOutItsGrace() {
        String collar = source("RiftCollarBlockEntity.java");
        assertTrue(method(collar, "public void onLoad()").contains("WELLHEAD_RESUME_GRACE"));
        assertTrue(method(collar, "private void work(ServerLevel server)").contains("graceTicks > 0"),
                "the grace after a load is never handed to the reactor");
    }
}
