package uk.co.iceconchy.aerowarptics.cooling;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import uk.co.iceconchy.aerowarptics.AeroWarptics;

/**
 * What cools a Rift Drive just by standing against it: ice of any kind.
 *
 * <p>A tag of this mod's own rather than vanilla's {@code minecraft:ice} read directly, though that is all
 * it holds, so a pack can add another mod's ice - or take one out - without touching what every other mod
 * means by ice. Kept apart from {@link DriveCooling} because naming a tag needs the game's registries and
 * the arithmetic must not.
 */
public final class Coolants {

    /** Blocks that cool a drive passively: no shaft, no fuel, and a smaller share than a vent's. */
    public static final TagKey<Block> PASSIVE = TagKey.create(Registries.BLOCK, AeroWarptics.id("drive_coolant"));

    private Coolants() {
    }

    public static boolean isPassive(BlockState state) {
        return state.is(PASSIVE);
    }
}
