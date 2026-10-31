package uk.co.iceconchy.aerowarptics.rail;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import com.simibubi.create.content.trains.track.TrackTargetingBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import uk.co.iceconchy.aerowarptics.trace.Trace;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Puts Create's edge points - stations, signals, train observers - back on the graph after this mod has changed it.
 *
 * <h2>Why this has to exist</h2>
 * Create tears a graph down before it builds it again: {@code TrackPropagator.onRailAdded} walks outward over the
 * connected network and calls {@code TrackGraph.removeNode} on every node it reaches, and that strips every edge
 * point off them. Normally nobody notices, because {@code removeNode} first calls
 * {@code TrackEdgePoint.invalidate}, which resets the station's block entity so its next tick builds the point
 * again.
 *
 * <p>{@code invalidate} resolves that block entity out of <em>the level it was handed</em>, ignoring the point's own
 * {@code blockEntityDimension} - which {@code SingleBlockEntityEdgePoint} goes to the trouble of storing. A lane's
 * graph spans two dimensions, so laying one strips every point whose block entity is in the other world and
 * invalidates none of them: {@link RailWorks#layFar} joins the corridor's end with the <em>corridor's</em> level,
 * over a graph whose stations stand in the player's. And {@code TrackTargetingBehaviour.tick} is
 * {@code if (edgePoint != null) return;}, so a point that was never invalidated never comes back. The station is
 * still standing there, bound and lit, and a schedule naming it says <em>No station on graph matches 'West'</em>.
 *
 * <p>Found in game on 2026-10-25 and read out of the save: one graph, 37 nodes, both dimensions, and
 * {@code Points { create:station: [] }} - with both stations' blocks present, bound, holding their own edge point
 * UUIDs. Swapping the order of the two joins does not help; whichever runs last, one of them propagates with a
 * level that is wrong for half the graph.
 *
 * <h2>What this does instead</h2>
 * It finds the block entity by walking the chunks it could be standing in, which means it is found <em>in its own
 * level</em>, and only then asks Create to invalidate it. That one difference is the whole fix.
 *
 * <p>A point that is still properly registered is left alone, because {@code invalidate} does not take the old
 * registration off the graph: invalidating an attached point would leave two of it. A point whose chunk is not
 * loaded needs nothing either - its block entity is built afresh when the chunk comes back, with no point of its
 * own yet, and attaches on its first tick. That is the one true half of the old note about a station re-attaching
 * "when somebody next loads it": by being unloaded and loaded, never by being ticked.
 */
public final class RailPoints {

    /**
     * How far from its track a point's own block may stand, in blocks.
     *
     * <p>A station is bound to a track a few blocks off it, so the chunk to look in is not always the chunk the
     * track is in. Four is margin over anything Create's own placement allows.
     */
    static final int REACH = 4;

    /** Ticks after the last disturbance before the sweep runs, so a join deferred to a later tick happens first. */
    private static final int SETTLE_TICKS = 2;

    /** Ticks between safety-net sweeps of the ground around every lane. */
    private static final int SWEEP_INTERVAL = 100;

    private static int settle;

    private RailPoints() {
    }

    /**
     * Says that this mod has just changed Create's graph, so the points on it want looking at.
     *
     * <p>Takes no position on purpose. A teardown reaches as far as the network does - a station two hundred blocks
     * down the line comes off the graph just as the one beside the gate does - so there is no radius worth guessing
     * at, and the sweep that follows walks every graph instead. Anything that lays or takes up track says so here.
     */
    public static void disturbed() {
        settle = SETTLE_TICKS;
    }

    /**
     * Called every server tick from {@link RailWorks#onServerTick}.
     *
     * <p>Twice over, on purpose. A disturbance sweeps within a couple of ticks, so a station is back before anybody
     * has walked to it; the standing sweep then covers whatever no disturbance was recorded for - a graph Create
     * tore down on somebody else's account, or a lane laid before this code existed. Both sweeps look everywhere,
     * because a bounded one does not work: measured against the bench in game, a sweep of the ground around a
     * lane's own two ends reaches the near station and misses the far one by a single chunk.
     */
    static void tick(MinecraftServer server, long now) {
        if (settle > 0 && --settle == 0) {
            sweep(server, "a lane changed the graph");
            return;
        }
        if (now % SWEEP_INTERVAL == 0L) {
            sweep(server, "a standing sweep");
        }
    }

    /**
     * Every graph's track, by the world it stands in.
     *
     * <p>All of it, because that is how far a teardown goes. The work is a chunk lookup per node, which stops dead
     * where the chunk is not loaded, and a walk of the block entities in the loaded ones.
     */
    private static Map<ResourceKey<Level>, Set<BlockPos>> everyGraph() {
        Map<ResourceKey<Level>, Set<BlockPos>> track = new HashMap<>();
        for (TrackGraph graph : Create.RAILWAYS.trackNetworks.values()) {
            for (TrackNodeLocation node : graph.getNodes()) {
                track.computeIfAbsent(node.getDimension(), key -> new LinkedHashSet<>())
                        .add(BlockPos.containing(node.getLocation()));
            }
        }
        return track;
    }

    /** Looks over the chunks around every graph's track and puts back whatever has fallen off. */
    private static void sweep(MinecraftServer server, String why) {
        Map<ResourceKey<Level>, Set<BlockPos>> track = everyGraph();
        int repaired = 0;
        for (Map.Entry<ResourceKey<Level>, Set<BlockPos>> entry : track.entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey());
            if (level == null) {
                continue;
            }
            for (ChunkPos at : chunksAround(entry.getValue())) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(at.x, at.z);
                if (chunk == null) {
                    continue; // not loaded, and so not anybody's problem: it attaches itself on the way back in
                }
                for (BlockEntity standing : List.copyOf(chunk.getBlockEntities().values())) {
                    if (reattach(level, standing)) {
                        repaired++;
                    }
                }
            }
        }
        if (repaired > 0) {
            Trace.log(Trace.RAIL, "put {} edge point(s) back on the graph after {}", repaired, why);
        }
    }

    /**
     * The chunks a point pinned to one of these tracks could be standing in.
     *
     * <p>Pure, and the reason it is not simply "the chunk the track is in": a station sits a few blocks off its
     * track, which across a chunk border is the next chunk along.
     */
    static Set<ChunkPos> chunksAround(Collection<BlockPos> tracks) {
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        for (BlockPos track : tracks) {
            for (int x = -REACH; x <= REACH; x += REACH) {
                for (int z = -REACH; z <= REACH; z += REACH) {
                    chunks.add(new ChunkPos(track.offset(x, 0, z)));
                }
            }
        }
        return chunks;
    }

    /**
     * Resets one block entity if the point it holds has fallen off its graph.
     *
     * @param level the level this block entity was found in, which is the level its point must be invalidated with
     * @return whether it was reset, and so will build itself a new point on its next tick
     */
    private static boolean reattach(ServerLevel level, BlockEntity standing) {
        if (!(standing instanceof SmartBlockEntity smart)) {
            return false;
        }
        TrackTargetingBehaviour<?> behaviour = smart.getBehaviour(TrackTargetingBehaviour.TYPE);
        if (behaviour == null) {
            return false;
        }
        TrackEdgePoint point = behaviour.getEdgePoint();
        if (point == null) {
            return false; // it holds none yet, and its own tick is already trying to build one
        }
        TrackGraphLocation where = behaviour.determineGraphLocation();
        if (where == null || where.graph == null) {
            return false; // its track is gone or in no graph, so there is nothing to be on
        }
        TrackEdgePoint on = pointOn(where.graph, point);
        if (on == point) {
            return false;
        }
        if (on != null) {
            Trace.changed("rail-point:" + standing.getBlockPos(), Trace.RAIL, "the point at {} is answered for by"
                    + " another of the same id on graph {}", Trace.pos(standing.getBlockPos()), where.graph.id);
            return false;
        }
        // Invalidated with the level it was found in. Create's own call site hands over whichever level it happened
        // to be propagating, which for half of a lane's graph is the wrong world - and a silent no-op.
        point.invalidate(level);
        Trace.log(Trace.RAIL, "put the {} at {} back on graph {}", name(point), Trace.pos(standing.getBlockPos()),
                where.graph.id);
        return true;
    }

    /** Whatever this graph has registered under the same type and id as this point, if anything. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static TrackEdgePoint pointOn(TrackGraph graph, TrackEdgePoint point) {
        EdgePointType type = point.getType();
        return type == null ? null : (TrackEdgePoint) graph.getPoint(type, point.getId());
    }

    private static String name(TrackEdgePoint point) {
        EdgePointType<?> type = point.getType();
        return type == null ? "point" : type.getId().getPath();
    }
}
