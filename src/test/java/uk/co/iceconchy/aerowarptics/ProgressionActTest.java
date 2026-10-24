package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The drive ladder asks for a different act at each step, and this holds it to that.
 *
 * <p>{@code RecipeBalanceTest} prices things, and a price proves nothing about an act: a Mk III that could
 * be built without ever having folded anything would cost exactly as much as one that could not. So this
 * reads the recipes as a graph and asks two kinds of question of it (see {@code docs/crafting-plan.md},
 * "The ladder, reshaped"):
 * <ul>
 *   <li>an <b>or</b> - there is <em>some</em> road to a thing that avoids an act. The Mk I is built, not
 *       opened or folded: it has to have a road with no essence, no rift, no fold and no collapse in it,
 *       or the first rift would need a rift to build;</li>
 *   <li>an <b>and</b> - <em>every</em> road to a thing goes through an act. Take the act away and the thing
 *       can no longer be made at all.</li>
 * </ul>
 *
 * <p>Only the default recipes are held to this. The Classic pack is the ladder from before, on purpose.
 */
class ProgressionActTest {

    private static final String ESSENCE = "aerowarptics:rift_essence";

    /**
     * Whether something can be made with the recipes that pass a filter.
     *
     * <p>Anything no recipe of this mod's makes is a raw material and counts as available - vanilla items,
     * Create's parts - unless it is one of this mod's own, which only this mod's recipes (or the world) make.
     * An item with no recipe at all among this mod's own is available only if {@code found} says the world
     * hands it out.
     */
    static final class Roads {

        private final RecipeBook book;
        private final Predicate<RecipeBook.Recipe> allowed;
        private final Set<String> barred;
        private final Set<String> found;
        private final Map<String, Boolean> memo = new HashMap<>();

        Roads(RecipeBook book, Predicate<RecipeBook.Recipe> allowed, Set<String> barred, Set<String> found) {
            this.book = book;
            this.allowed = allowed;
            this.barred = barred;
            this.found = found;
        }

        boolean canMake(String id) {
            return canMake(id, new HashSet<>());
        }

        private boolean canMake(String id, Set<String> visiting) {
            if (barred.contains(id)) {
                return false;
            }
            Boolean known = memo.get(id);
            if (known != null) {
                return known;
            }
            List<RecipeBook.Recipe> roads = book.byOutput().getOrDefault(id, List.of());
            boolean ours = id.startsWith("aerowarptics:");
            if (roads.isEmpty() || !ours) {
                boolean raw = !ours || found.contains(id);
                memo.put(id, raw);
                return raw;
            }
            if (!visiting.add(id)) {
                return false;
            }
            boolean any = false;
            for (RecipeBook.Recipe road : roads) {
                if (road.type().equals("create:emptying") || !allowed.test(road)) {
                    continue;
                }
                boolean all = true;
                for (String input : road.inputs()) {
                    if (!canMake(input, visiting)) {
                        all = false;
                        break;
                    }
                }
                if (all) {
                    any = true;
                    break;
                }
            }
            visiting.remove(id);
            memo.put(id, any);
            return any;
        }
    }

    private static boolean isType(RecipeBook.Recipe recipe, String type) {
        return recipe.type().equals(type);
    }

    @Test
    void theMkIIsBuiltWithNoEssenceRiftFoldOrCollapse() {
        RecipeBook book = RecipeBook.standard();
        Roads roads = new Roads(book,
                recipe -> !isType(recipe, "aerowarptics:rifting")
                        && !isType(recipe, "aerowarptics:folding")
                        && !isType(recipe, "aerowarptics:collapse"),
                Set.of(ESSENCE, "aerowarptics:opened_rift_lens", "aerowarptics:rift_pearl",
                        "aerowarptics:scar_heart"),
                Set.of());
        assertTrue(roads.canMake("aerowarptics:rift_drive_mk_i"),
                "there is no road to a Mk I without essence, a rift, a fold or a collapse - the first rift"
                        + " would need a rift to build");
    }

    /** The found things, which every AND below is allowed: the world hands them out. */
    private static final Set<String> FOUND = Set.of(ESSENCE, "aerowarptics:scar_heart", "aerowarptics:stormglass");

    /** And the one part of the machine that is found rather than made. */
    private static final Set<String> FOUND_WITH_THE_RELIC = Set.of(ESSENCE, "aerowarptics:scar_heart",
            "aerowarptics:stormglass", "aerowarptics:relic_armillary");

    @Test
    void theMkIINeedsAnOpenedLens() {
        Roads roads = new Roads(RecipeBook.standard(), recipe -> true, Set.of("aerowarptics:opened_rift_lens"), FOUND);
        assertFalse(roads.canMake("aerowarptics:rift_drive_mk_ii"),
                "a Mk II can be built without an Opened Rift Lens - the second drive would not need a rift seen");
    }

    /**
     * The Mk III is folded for: every road to it goes through a fold, which is the Collapse Cradle's now. (It was a
     * fold on a long enough jump, in a Fold Crucible aboard; the crucible is gone and essence is the price.)
     */
    @Test
    void theMkIIINeedsAFold() {
        Roads roads = new Roads(RecipeBook.standard(), recipe -> !isType(recipe, "aerowarptics:folding"),
                Set.of(), FOUND);
        assertFalse(roads.canMake("aerowarptics:rift_drive_mk_iii"), "a Mk III can be built without folding anything");
    }

    @Test
    void theSingularityNeedsACollapseAndAFold() {
        Roads noCollapse = new Roads(RecipeBook.standard(), recipe -> !isType(recipe, "aerowarptics:collapse"),
                Set.of(), FOUND);
        assertFalse(noCollapse.canMake("aerowarptics:rift_drive_singularity"),
                "a Singularity can be built without a collapse");
        Roads noFold = new Roads(RecipeBook.standard(), recipe -> !isType(recipe, "aerowarptics:folding"),
                Set.of(), FOUND);
        assertFalse(noFold.canMake("aerowarptics:rift_drive_singularity"),
                "a Singularity can be built without folding anything");
    }

    /**
     * Nothing folding makes is needed to build the machine that folds - or the fold would need a fold before it,
     * and every tier from the Mk III up could not be made. The question the crucible's removal had to answer.
     */
    @Test
    void theCradleNeedsNothingFolded() {
        Roads roads = new Roads(RecipeBook.standard(), recipe -> !isType(recipe, "aerowarptics:folding"),
                Set.of(), FOUND_WITH_THE_RELIC);
        assertTrue(roads.canMake("aerowarptics:collapse_cradle"),
                "the Collapse Cradle needs something only a fold makes - which only a cradle can do");
    }

    /**
     * The Rift Plotter is late and a relic: every road to it goes through a fold at a cradle and a Relic Armillary
     * found in a scar - so the weapon comes after the machine that makes the Mk III, never before it - and with
     * both, it can be made.
     */
    @Test
    void thePlotterNeedsAFoldAndAFoundRelic() {
        Roads noFold = new Roads(RecipeBook.standard(), recipe -> !isType(recipe, "aerowarptics:folding"),
                Set.of(), FOUND_WITH_THE_RELIC);
        assertFalse(noFold.canMake("aerowarptics:rift_plotter"), "a Rift Plotter can be built without folding anything");
        Roads noRelic = new Roads(RecipeBook.standard(), recipe -> true, Set.of(), FOUND);
        assertFalse(noRelic.canMake("aerowarptics:rift_plotter"), "a Rift Plotter can be built without a found relic");
        Roads both = new Roads(RecipeBook.standard(), recipe -> true, Set.of(), FOUND_WITH_THE_RELIC);
        assertTrue(both.canMake("aerowarptics:rift_plotter"), "nothing makes a Rift Plotter at all");
    }

    /** The collapse's stake is at its fixed place: slot 1 is the heart, so a failed ritual keeps only that. */
    @Test
    void theCollapseAsksForAHeartInItsHeartSlot() {
        for (RecipeBook.Recipe recipe : RecipeBook.standard().all()) {
            if (isType(recipe, "aerowarptics:collapse")) {
                assertEquals("aerowarptics:scar_heart", recipe.json().getAsJsonArray("ingredients").get(1)
                        .getAsJsonObject().get("item").getAsString(), recipe.file() + " has no heart in slot 1");
            }
        }
    }

    @Test
    void everyTierCanBeMadeAtAll() {
        RecipeBook book = RecipeBook.standard();
        Roads roads = new Roads(book, recipe -> true, Set.of(),
                Set.of(ESSENCE, "aerowarptics:scar_heart", "aerowarptics:stormglass"));
        for (String tier : List.of("aerowarptics:rift_drive_mk_i", "aerowarptics:rift_drive_mk_ii",
                "aerowarptics:rift_drive_mk_iii", "aerowarptics:rift_drive_singularity")) {
            assertTrue(roads.canMake(tier), "nothing makes " + tier + " at all");
        }
        assertFalse(roads.canMake("aerowarptics:rift_drive_creative"), "the creative drive gained a recipe");
    }
}
