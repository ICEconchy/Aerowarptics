package uk.co.iceconchy.aerowarptics.rail;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AeroWarptics;

/**
 * The Rift Corridor: the small, empty dimension a train runs through between two Rift Gates.
 *
 * <h2>Why there is a dimension at all</h2>
 * Create carries a train through a portal only when the portal's two ends are in different dimensions. A
 * track edge whose ends share a dimension is an ordinary straight edge as long as the real distance between
 * them, and a carriage keeps exactly one entity per dimension, so a gate joining two places in the Overworld
 * cannot simply be told to Create as a portal - the train would be drawn sliding through the hillside. Two
 * hops through somewhere else are both real dimension changes, and then everything Create already does for a
 * Nether portal - splitting the carriage at the plane, carrying the passengers, finding a path for a schedule
 * - does it here too. Nothing in Create had to be taught what a gate is.
 *
 * <h2>What is in it</h2>
 * Nothing but the corridors: one straight run of track per lane, a barrier floor under it, and a mouth block at
 * each end that the track takes for a portal. It generates as void, has no sky light and no weather, and is
 * kept out of everything else in the mod that lists dimensions - a probe cannot scan it, a ship cannot be sent
 * there, a player cannot build in it. See {@link CorridorSlots} for where each corridor lies.
 */
public final class RailCorridor {

    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, AeroWarptics.id("rift_corridor"));

    /** The corridor as a level stem - what a world's list of dimensions holds. */
    public static final ResourceKey<net.minecraft.world.level.dimension.LevelStem> STEM =
            ResourceKey.create(Registries.LEVEL_STEM, AeroWarptics.id("rift_corridor"));

    private RailCorridor() {
    }

    public static boolean isCorridor(@Nullable ResourceKey<Level> dimension) {
        return KEY.equals(dimension);
    }

    public static boolean isCorridor(@Nullable Level level) {
        return level != null && isCorridor(level.dimension());
    }

    /**
     * How many dimensions a world counts when deciding whether its settings are stable - the corridor left out.
     *
     * <p>Vanilla calls a world stable only with exactly vanilla's dimensions, and warns that a world is experimental
     * on every load otherwise. The corridor is known to be stable, so it is not counted; anything else extra still is.
     */
    public static int countForStability(int dimensions, boolean corridorPresent) {
        return corridorPresent ? dimensions - 1 : dimensions;
    }

    /**
     * The corridor's level, or {@code null} if this server has none - a world whose data packs left the
     * dimension out, which turns rail gates off rather than crashing anything.
     */
    @Nullable
    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(KEY);
    }
}
