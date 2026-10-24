package uk.co.iceconchy.aerowarptics.pack;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.util.AWLang;

/**
 * The <em>Classic recipes</em> data pack: the drive ladder as it was before the crafting layer.
 *
 * <p>The reshaped ladder - opened lenses, folded crystal, a collapse out of a scar - is the default. A world
 * that wants the old one, or one with no rift scars to find, turns this pack on when it is created, or later
 * with {@code /datapack enable}. Everything in it is an ordinary recipe file at the same path as the one it
 * replaces, so it wins by being later in the pack order and nothing in the code has to know it exists.
 *
 * <p>Why a pack and not a config switch: recipes load with the world's data packs, before a world's server
 * config is read, and this mod has no common config to put a switch in. {@link PackSource#FEATURE} is what
 * makes it opt-in - it is listed, and left off - where {@code BUILT_IN} would switch it on in every world,
 * which would quietly undo the default for everybody.
 */
public final class ClassicRecipes {

    /** Where the pack lives under the jar's resources. */
    public static final String PATH = "datapacks/classic_recipes";

    private ClassicRecipes() {
    }

    public static void onAddPackFinders(AddPackFindersEvent event) {
        event.addPackFinders(AeroWarptics.id(PATH), PackType.SERVER_DATA,
                AWLang.component("pack.classic_recipes.title"), PackSource.FEATURE, false, Pack.Position.TOP);
    }
}
