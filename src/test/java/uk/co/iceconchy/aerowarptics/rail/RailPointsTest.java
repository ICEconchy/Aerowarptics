package uk.co.iceconchy.aerowarptics.rail;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which chunks are looked in for a station that has fallen off the graph.
 *
 * <p>The arithmetic matters because a miss is silent: a station in a chunk nobody looked in stays off the graph,
 * and all anybody sees is a schedule that cannot find a station standing in front of the train.
 */
class RailPointsTest {

    @Test
    void aTrackWellInsideAChunkIsLookedForInThatChunkAlone() {
        assertEquals(Set.of(new ChunkPos(0, 0)), RailPoints.chunksAround(List.of(new BlockPos(8, 64, 8))));
    }

    /** A station is bound to a track a few blocks off, so the track's own chunk is not always the one to look in. */
    @Test
    void aTrackByAChunkBorderReachesIntoTheNextChunkAlong() {
        Set<ChunkPos> chunks = RailPoints.chunksAround(List.of(new BlockPos(1, 64, 1)));
        assertTrue(chunks.contains(new ChunkPos(0, 0)), "its own chunk");
        assertTrue(chunks.contains(new ChunkPos(-1, 0)));
        assertTrue(chunks.contains(new ChunkPos(0, -1)));
        assertTrue(chunks.contains(new ChunkPos(-1, -1)), "the diagonal too");
    }

    /** The reach is the whole of the margin: a block further in and the next chunk is not looked at. */
    @Test
    void theReachIsHowFarOverABorderItLooks() {
        assertTrue(RailPoints.chunksAround(List.of(new BlockPos(RailPoints.REACH - 1, 64, 8)))
                .contains(new ChunkPos(-1, 0)));
        assertFalse(RailPoints.chunksAround(List.of(new BlockPos(RailPoints.REACH, 64, 8)))
                .contains(new ChunkPos(-1, 0)));
    }

    /** A corridor is a hundred and sixty blocks of track; looking in a chunk twice would be looking at every one. */
    @Test
    void manyTracksInOneChunkAreOneChunkToLookIn() {
        assertEquals(Set.of(new ChunkPos(0, 0)), RailPoints.chunksAround(List.of(
                new BlockPos(8, 64, 8), new BlockPos(9, 64, 8), new BlockPos(10, -58, 8))));
    }

    @Test
    void heightIsNothingToDoWithIt() {
        assertEquals(RailPoints.chunksAround(List.of(new BlockPos(8, 320, 8))),
                RailPoints.chunksAround(List.of(new BlockPos(8, -64, 8))));
    }

    @Test
    void nothingDisturbedIsNowhereToLook() {
        assertTrue(RailPoints.chunksAround(List.of()).isEmpty());
    }
}
