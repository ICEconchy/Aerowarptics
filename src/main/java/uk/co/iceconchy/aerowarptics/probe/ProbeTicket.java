package uk.co.iceconchy.aerowarptics.probe;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.Comparator;

/**
 * Holds the ground a sounding landed on, long enough to read it.
 *
 * <p>This is the part that makes a blind jump possible at all. {@code DestinationSurvey} only reads
 * chunks that are already loaded, which is exactly right for an anchor - somebody has been there - and
 * useless for a probe, where nobody has and the terrain may not have been generated yet. So the probe
 * claims the region first and waits, and the wait a player sees is the server making that ground
 * exist.
 *
 * <p>The ticket expires on its own. A probe whose block is broken mid-sounding, or whose chunk is
 * unloaded, therefore cannot leave a patch of the world forced open forever - which is the failure
 * mode that matters here, because unlike a warp there is no arrival to tidy up after.
 */
public final class ProbeTicket {

    /**
     * Long enough for a slow server to finish generating the region and for the survey to be read,
     * with room over the configured timeout.
     */
    private static final int LIFESPAN_TICKS = 1_200;

    private static final TicketType<ChunkPos> PROBE_SOUNDING =
            TicketType.create("aerowarptics:probe_sounding", Comparator.comparingLong(ChunkPos::toLong),
                    LIFESPAN_TICKS);

    private ProbeTicket() {
    }

    /**
     * Claims the region a sounding is reading.
     *
     * @param radiusBlocks how far the survey reaches from the centre
     */
    public static void hold(ServerLevel level, BlockPos centre, int radiusBlocks) {
        ChunkPos chunk = new ChunkPos(centre);
        level.getChunkSource().addRegionTicket(PROBE_SOUNDING, chunk, radiusFor(radiusBlocks), chunk);
    }

    /**
     * Chunks either side of the centre to claim.
     *
     * <p>Enough to cover the survey, plus one so the edge samples are reading real chunks rather than
     * the boundary of what was loaded. Capped, because this is a player-triggered request to generate
     * terrain and an uncapped one is a way to ask a server to do arbitrary work.
     */
    public static int radiusFor(int radiusBlocks) {
        return Math.min(8, (radiusBlocks >> 4) + 2);
    }
}
