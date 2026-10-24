package uk.co.iceconchy.aerowarptics;

import com.google.gson.JsonObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Holds the crafting progression to a shape a player can actually read.
 *
 * <p>Balance is usually a matter of taste, but some of it is arithmetic, and the arithmetic is worth
 * a test. An earlier version of these recipes asked for <em>four complete Mk III drives</em> and five
 * Singularity Cores to build one Singularity, which cascaded to something in the region of thirty-six
 * Refined Radiance. Nobody wrote that on purpose; it is what happens when a five-by-five pattern is
 * drawn for how it looks and never counted up.
 *
 * <p>So the ladder is asserted rather than eyeballed: each tier consumes exactly one of the tier below,
 * and the whole cost of building one roughly doubles each step. Costs are weighted by rough crafting
 * effort rather than counted, because a Refined Radiance and a brass sheet are not the same thing and
 * a plain item count would let the top of the tree quietly deflate.
 *
 * <p>Every assertion runs twice: on the default recipes, and on the default recipes with the Classic pack
 * laid over them, which is the ladder as it was before the crafting layer and is still a way to play.
 * The processes the crafting layer added - rifting, folding, the collapse - are priced at nothing beyond
 * their ingredients here, because what they ask for is an act, not effort, and a fixed price proves nothing
 * about an act. {@code ProgressionActTest} holds the acts.
 */
class RecipeBalanceTest {

    /**
     * Roughly what each raw ingredient costs to get hold of, relative to a brass sheet.
     *
     * <p>Deliberately coarse. It exists to stop the ladder drifting, not to model the economy - the
     * ratios between tiers are what matter, and those barely move if any one of these is a little off.
     * Anything reachable in a recipe must appear here, so adding a new ingredient is a decision rather
     * than an accident.
     */
    private static final Map<String, Double> EFFORT = Map.ofEntries(
            Map.entry("minecraft:book", 3.0D),
            Map.entry("minecraft:glass", 0.5D),
            Map.entry("minecraft:glass_bottle", 0.5D),
            Map.entry("minecraft:amethyst_shard", 1.0D),
            Map.entry("minecraft:lodestone", 12.0D),
            Map.entry("minecraft:obsidian", 2.0D),
            Map.entry("minecraft:ender_pearl", 3.0D),
            Map.entry("minecraft:compass", 5.0D),
            Map.entry("minecraft:map", 3.0D),
            Map.entry("minecraft:shulker_shell", 6.0D),
            Map.entry("minecraft:iron_bars", 0.5D),
            Map.entry("create:brass_sheet", 1.0D),
            Map.entry("create:brass_ingot", 1.0D),
            Map.entry("create:cogwheel", 1.0D),
            Map.entry("create:goggles", 6.0D),
            Map.entry("create:shaft", 1.0D),
            Map.entry("create:andesite_alloy", 1.0D),
            Map.entry("create:copper_casing", 3.0D),
            Map.entry("create:brass_casing", 4.0D),
            Map.entry("create:railway_casing", 12.0D),
            Map.entry("create:electron_tube", 4.0D),
            Map.entry("create:precision_mechanism", 8.0D),
            Map.entry("create:depot", 2.0D),
            Map.entry("create:propeller", 3.0D),
            Map.entry("simulated:gyroscopic_mechanism", 10.0D),
            Map.entry("minecraft:end_crystal", 20.0D),
            Map.entry("minecraft:echo_shard", 20.0D),
            Map.entry("aeronautics:end_stone_powder", 1.0D),
            // Our own crystal stock stands in for Aeronautics' levitite/pearlescent levitite, which
            // have no item form. The finished crystals are priced as base materials at the values the
            // levitite they replace carried, so the drive ladder is unchanged by how they are made; the
            // intermediates are priced too, so a recipe's ingredients can always be valued.
            Map.entry("aerowarptics:warp_dust", 1.0D),
            Map.entry("aerowarptics:warp_shard", 1.5D),
            Map.entry("aerowarptics:warp_crystal", 2.0D),
            Map.entry("aerowarptics:pearlescent_warp_crystal", 8.0D),
            // Found, never made: the journey to a scar is the price, and this is what it is worth to the
            // ladder. See docs/crafting-plan.md, "Why the heart is the stake".
            Map.entry("aerowarptics:scar_heart", 30.0D),
            // Found too, in the caches in those same ruins and not in every one: a trip or two, once, for the
            // machine rather than for each core it makes.
            Map.entry("aerowarptics:relic_armillary", 40.0D),
            // Per millibucket. A siphon's four buckets are some fifteen to thirty jumps, or one fissure.
            Map.entry("aerowarptics:rift_essence", 0.004D));

    /** The drive ladder, in order. Each is expected to be built from the one before it. */
    private static final List<String> TIERS = List.of(
            "aerowarptics:rift_drive_mk_i",
            "aerowarptics:rift_drive_mk_ii",
            "aerowarptics:rift_drive_mk_iii",
            "aerowarptics:rift_drive_singularity");

    /** Everything this mod expects to be craftable one way or another. */
    private static final List<String> CRAFTABLE = List.of(
            "aerowarptics:handbook",
            "aerowarptics:rift_goggles",
            "aerowarptics:rift_lens",
            "aerowarptics:rift_core",
            "aerowarptics:rift_casing",
            "aerowarptics:rift_pearl",
            "aerowarptics:stabiliser_ring",
            "aerowarptics:warp_crystal",
            "aerowarptics:pearlescent_warp_crystal",
            "aerowarptics:singularity_core",
            "aerowarptics:warp_anchor",
            "aerowarptics:astrolabe",
            "aerowarptics:spatial_siphon",
            "aerowarptics:spatial_stabiliser",
            "aerowarptics:spatial_wave_detector",
            "aerowarptics:cooling_vent",
            "aerowarptics:drive_extender",
            "aerowarptics:rift_bond",
            "aerowarptics:berth",
            "aerowarptics:rift_gate",
            "aerowarptics:rift_gate_frame",
            "aerowarptics:rift_drive_mk_i",
            "aerowarptics:rift_drive_mk_ii",
            "aerowarptics:rift_drive_mk_iii",
            "aerowarptics:rift_drive_singularity",
            "aerowarptics:rift_brazier",
            "aerowarptics:rift_collar",
            "aerowarptics:collapse_cradle",
            "aerowarptics:rift_plotter",
            "aerowarptics:rift_ward",
            "aerowarptics:opened_rift_lens",
            "aerowarptics:warp_glass",
            "aerowarptics:folded_warp_crystal",
            "aerowarptics:scar_chart",
            "aerowarptics:ship_compass",
            "aerowarptics:unset_stormglass");

    static Stream<Named> books() {
        return Stream.of(new Named("default", RecipeBook.standard()), new Named("classic", RecipeBook.classic()));
    }

    record Named(String name, RecipeBook book) {
        @Override
        public String toString() {
            return name;
        }
    }

    // ------------------------------------------------------------------ pricing

    /**
     * What one craft of something costs, all the way down to raw ingredients.
     *
     * <p>The cheapest of its roads, where it has several - that is the road a player takes. Emptying
     * recipes are not roads to anything: their first result is the vessel handed back, so reading one as a
     * way to make an ender pearl out of a Rift Pearl is a cycle, not a recipe.
     */
    private static double effort(String id, RecipeBook book, int depth) {
        Double raw = EFFORT.get(id);
        if (raw != null) {
            return raw;
        }
        assertTrue(depth < 16, "recipes for " + id + " appear to be circular");
        List<RecipeBook.Recipe> roads = book.byOutput().getOrDefault(id, List.of()).stream()
                .filter(recipe -> !recipe.type().equals("create:emptying")
                        && !recipe.type().equals("aerowarptics:agitating"))
                .toList();
        assertTrue(!roads.isEmpty(), id + " is used in a recipe but nothing makes it and it has no "
                + "entry in the effort table - add one or fix the reference");
        double best = Double.MAX_VALUE;
        for (RecipeBook.Recipe road : roads) {
            best = Math.min(best, cost(road, book, depth));
        }
        return best;
    }

    private static double cost(RecipeBook.Recipe recipe, RecipeBook book, int depth) {
        assertTrue(recipe.tags().isEmpty(), recipe.file() + " asks for a tag, which this test cannot price: "
                + recipe.tags().keySet());
        double total = 0.0D;
        for (Map.Entry<String, Integer> item : recipe.items().entrySet()) {
            total += effort(item.getKey(), book, depth + 1) * item.getValue();
        }
        for (Map.Entry<String, Integer> fluid : recipe.fluids().entrySet()) {
            Double perMillibucket = EFFORT.get(fluid.getKey());
            assertTrue(perMillibucket != null, recipe.file() + " asks for " + fluid.getKey()
                    + ", a fluid with no price");
            total += perMillibucket * fluid.getValue();
        }
        return total;
    }

    // -------------------------------------------------------------------- tests

    @ParameterizedTest
    @MethodSource("books")
    void everythingTheModAddsCanBeMade(Named named) {
        Map<String, List<RecipeBook.Recipe>> outputs = named.book().byOutput();
        List<String> missing = new ArrayList<>();
        for (String id : CRAFTABLE) {
            if (!outputs.containsKey(id)) {
                missing.add(id);
            }
        }
        assertTrue(missing.isEmpty(), "no recipe for " + missing);
    }

    @ParameterizedTest
    @MethodSource("books")
    void everyIngredientIsSomethingWeCanPrice(Named named) {
        // Reaching an unknown ingredient means either a typo in an item id or a new one nobody has
        // decided the cost of. Both are worth failing over. Every recipe is walked, not every output: an
        // output priced straight from the table would otherwise hide whatever its own recipe asks for.
        for (RecipeBook.Recipe recipe : named.book().all()) {
            // Nor what a Rift Wellhead digests: that is cobblestone turned to essence over time, at a risk, and
            // pricing it as a craft would price essence at the cost of a stone.
            if (!recipe.type().equals("create:emptying") && !recipe.type().equals("aerowarptics:agitating")) {
                cost(recipe, named.book(), 0);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("books")
    void eachDriveTierIsBuiltFromExactlyOneOfTheTierBelow(Named named) {
        Map<String, List<RecipeBook.Recipe>> outputs = named.book().byOutput();
        for (int tier = 1; tier < TIERS.size(); tier++) {
            String previous = TIERS.get(tier - 1);
            String current = TIERS.get(tier);
            for (RecipeBook.Recipe recipe : outputs.get(current)) {
                Integer used = recipe.items().get(previous);
                assertEquals(Integer.valueOf(1), used,
                        current + " should be an upgrade of one " + previous + ", not " + used);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("books")
    void theLadderRoughlyDoublesEachStep(Named named) {
        double previous = effort(TIERS.get(0), named.book(), 0);
        for (int tier = 1; tier < TIERS.size(); tier++) {
            double current = effort(TIERS.get(tier), named.book(), 0);
            double step = current / previous;
            assertTrue(step >= 1.7D && step <= 3.0D, String.format(
                    "%s: %s costs %.1f against %.1f for the tier below - a step of x%.2f, which is %s",
                    named, TIERS.get(tier), current, previous, step,
                    step < 1.7D ? "barely an upgrade" : "a wall"));
            previous = current;
        }
    }

    @ParameterizedTest
    @MethodSource("books")
    void anAnchorIsCheapEnoughToBuildANetworkOf(Named named) {
        // Anchors are placed in numbers; a warp network of twenty should be a project, not a wall.
        // The expensive component belongs in the drive that reaches them, not in every destination.
        double anchor = effort("aerowarptics:warp_anchor", named.book(), 0);
        double drive = effort("aerowarptics:rift_drive_mk_i", named.book(), 0);
        assertTrue(anchor < drive * 0.5D, String.format(
                "an anchor costs %.0f against %.0f for the cheapest drive - too much for scenery",
                anchor, drive));
    }

    @ParameterizedTest
    @MethodSource("books")
    void aFullChartTableCostsLessThanTheDriveItSteers(Named named) {
        // Nine blocks make one table, so the interesting number is nine crafts' worth - and a craft
        // makes three. A chart room should be an afternoon's brass, not a second drive.
        double perCraft = effort("aerowarptics:astrolabe", named.book(), 0);
        double table = perCraft * 3.0D;
        double drive = effort("aerowarptics:rift_drive_mk_i", named.book(), 0);
        assertTrue(table < drive, String.format(
                "a whole table costs %.0f against %.0f for the cheapest drive - too much for furniture",
                table, drive));
    }

    @ParameterizedTest
    @MethodSource("books")
    void extendingADriveCostsLessThanTheDrivesItDresses(Named named) {
        // A frame makes a drive bigger, not better - it flies no further for it - so the casing is priced
        // as casing. Counted in whole frames, because nobody builds one extender: a three-cube is
        // twenty-six of them and a seven-cube three hundred and forty-two.
        JsonObject recipe = named.book().file("drive_extender.json").json();
        double each = effort("aerowarptics:drive_extender", named.book(), 0)
                / recipe.getAsJsonObject("result").get("count").getAsInt();
        double three = each * (3 * 3 * 3 - 1);
        double seven = each * (7 * 7 * 7 - 1);
        double mkI = effort(TIERS.get(0), named.book(), 0);
        double top = effort(TIERS.get(TIERS.size() - 1), named.book(), 0);
        assertTrue(three < mkI, String.format(
                "a three-cube of extenders costs %.0f against %.0f for the cheapest drive", three, mkI));
        assertTrue(seven < top, String.format(
                "a full seven-cube of extenders costs %.0f against %.0f for the top drive", seven, top));
    }

    @ParameterizedTest
    @MethodSource("books")
    void aFullSetOfVentsCostsLessThanTheDriveItCools(Named named) {
        // A drive takes any good from four coolers at most, so four vents is the whole of what cooling can
        // cost. They make the cheapest drive warp more often, not further - an accessory, priced as one -
        // and one vent has to be within reach of whoever has just built that drive.
        double vent = effort("aerowarptics:cooling_vent", named.book(), 0);
        double drive = effort(TIERS.get(0), named.book(), 0);
        assertTrue(vent * 4.0D < drive, String.format(
                "four vents cost %.0f against %.0f for the cheapest drive - too much for plumbing", vent * 4.0D, drive));
        assertTrue(vent > effort("aerowarptics:rift_casing", named.book(), 0),
                "a vent is built round a casing and cannot cost less than one");
    }

    @ParameterizedTest
    @MethodSource("books")
    void theRelicsCostMoreThanTheFirstDriveAndLessThanTheLast(Named named) {
        // Each relic is a found Armillary and a machine round it: a project past the first drive, which it is
        // built after, and never a rival to the top of the ladder, which the cradle is a step on the way to.
        double mkI = effort(TIERS.get(0), named.book(), 0);
        double top = effort(TIERS.get(TIERS.size() - 1), named.book(), 0);
        for (String relic : List.of("aerowarptics:collapse_cradle", "aerowarptics:rift_plotter", "aerowarptics:rift_ward")) {
            double cost = effort(relic, named.book(), 0);
            assertTrue(cost > mkI && cost < top, String.format(
                    "%s: %s costs %.0f, outside %.0f (Mk I) to %.0f (Singularity)", named, relic, cost, mkI, top));
        }
    }

    @ParameterizedTest
    @MethodSource("books")
    void aBerthCostsLessThanTheAnchorItServes(Named named) {
        // A station is one anchor and as many berths as it has ships to park, so the slip is the cheaper part.
        double berth = effort("aerowarptics:berth", named.book(), 0);
        double anchor = effort("aerowarptics:warp_anchor", named.book(), 0);
        assertTrue(berth < anchor, String.format("a berth costs %.0f against %.0f for its anchor", berth, anchor));
    }

    @ParameterizedTest
    @MethodSource("books")
    void aCouplerCostsLessThanHalfTheCheapestDrive(Named named) {
        // Every ship of a flotilla carries one, beside the drive it already paid for.
        double coupler = effort("aerowarptics:rift_coupler", named.book(), 0);
        double drive = effort(TIERS.get(0), named.book(), 0);
        assertTrue(coupler < drive * 0.5D, String.format(
                "a coupler costs %.0f against %.0f for the cheapest drive", coupler, drive));
    }

    @ParameterizedTest
    @MethodSource("books")
    void theTopTierIsExpensiveWithoutBeingAbsurd(Named named) {
        // The old Singularity recipe expanded to well over ten thousand once its four Mk III drives
        // and five Singularity Cores were followed down. This is the guard against that returning.
        double singularity = effort("aerowarptics:rift_drive_singularity", named.book(), 0);
        assertTrue(singularity > 300.0D, "the endgame drive should be a serious build: " + singularity);
        assertTrue(singularity < 1_500.0D, "the endgame drive has run away: " + singularity);
    }

    /** The numbers, for anyone tuning them: run this and read the output. */
    @ParameterizedTest
    @MethodSource("books")
    void printTheLadder(Named named) {
        StringBuilder line = new StringBuilder(named + " ladder:");
        for (String tier : TIERS) {
            line.append(String.format(" %s=%.1f", tier.substring(tier.indexOf(':') + 1), effort(tier, named.book(), 0)));
        }
        System.out.println(line);
    }
}
