package uk.co.iceconchy.aerowarptics.client.fx;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * The ships over a patch of the storm's rain, and how high each column of it is stopped by one.
 * {@link RainCover} is the rule; this finds the ships and reads their plots.
 *
 * <p>Made once for a frame's ring of columns or a tick's splashes, so the ships about the camera are looked
 * up once rather than for each of several hundred columns - and with none about, which is nearly always,
 * a column costs nothing at all.
 */
final class RainCovers {

    /** Far enough up and down to take in a ship at any height: one a long way overhead is still a roof. */
    private static final double ANY_HEIGHT = 1.0e7D;

    private static final RainCovers NOTHING = new RainCovers(null, List.of(), false);

    private final ClientLevel level;
    private final List<SubLevel> ships;
    private final Pose3dc[] poses;
    private final RainCover.Plot plot = this::stops;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    /** The last chunk read: a column's walk stays in one, and looking a chunk up costs more than reading it. */
    private LevelChunk chunk;
    private int chunkX;
    private int chunkZ;

    private RainCovers(ClientLevel level, List<SubLevel> ships, boolean drawn) {
        this.level = level;
        this.ships = ships;
        this.poses = new Pose3dc[ships.size()];
        for (int index = 0; index < poses.length; index++) {
            SubLevel ship = ships.get(index);
            // A hull is drawn between its last pose and this one. Rain that is drawn has to end on the hull
            // as it is drawn, or it ends a tick's travel off a ship that is climbing.
            poses[index] = drawn && ship instanceof ClientSubLevel client ? client.renderPose() : ship.logicalPose();
        }
    }

    /** The ships over the columns within {@code reach} of this one, where this frame draws them. */
    static RainCovers drawn(ClientLevel level, int x, int z, int reach) {
        return about(level, x, z, reach, true);
    }

    /** The ships over the columns within {@code reach} of this one, where this tick has them. */
    static RainCovers ticked(ClientLevel level, int x, int z, int reach) {
        return about(level, x, z, reach, false);
    }

    private static RainCovers about(ClientLevel level, int x, int z, int reach, boolean drawn) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return NOTHING;
        }
        BoundingBox3d patch = new BoundingBox3d(
                x - reach, -ANY_HEIGHT, z - reach, x + reach + 1, ANY_HEIGHT, z + reach + 1);
        List<SubLevel> ships = null;
        for (SubLevel ship : container.queryIntersecting(patch)) {
            if (ship.isRemoved()) {
                continue;
            }
            if (ships == null) {
                ships = new ArrayList<>();
            }
            ships.add(ship);
        }
        return ships == null ? NOTHING : new RainCovers(level, ships, drawn);
    }

    /**
     * The lowest height rain reaches in this column before a ship stops it, or {@link RainCover#NONE}.
     * Take the higher of this and the ground's.
     */
    double over(int x, int z) {
        double height = RainCover.NONE;
        for (int index = 0; index < poses.length; index++) {
            height = Math.max(height, RainCover.height(poses[index], ships.get(index).boundingBox(), x, z, plot));
        }
        return height;
    }

    /** What the heightmap rain stops on counts: anything that blocks motion, and any fluid. */
    private boolean stops(int x, int y, int z) {
        int cx = x >> 4;
        int cz = z >> 4;
        if (chunk == null || cx != chunkX || cz != chunkZ) {
            chunk = level.getChunk(cx, cz);
            chunkX = cx;
            chunkZ = cz;
        }
        BlockState state = chunk.getBlockState(cursor.set(x, y, z));
        return state.blocksMotion() || !state.getFluidState().isEmpty();
    }
}
