package uk.co.iceconchy.aerowarptics.warp;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import org.joml.Vector3dc;

import java.util.Comparator;

/**
 * Keeps the far end of a warp loaded while a ship is on its way there.
 *
 * <p>An arrival lands a hull in a place nobody may have stood for a long time, and the chunks it lands
 * in have to come off disk. Left to itself that happens at the worst possible moment - during the
 * teleport - and shows up as a hitch just as the ship comes out of the aperture. Claiming the ground
 * when the warp is planned gives it the whole flight to load quietly instead.
 *
 * <p>The ticket carries its own lifespan, so there is nothing to release and no way to leak a
 * permanently loaded region if a warp ends somewhere unexpected. That matters more than it sounds:
 * forced chunks that outlive their reason are a slow, invisible drain on a server.
 */
public final class ArrivalTicket {

    /**
     * Long enough to cover a whole warp with room to spare, short enough that a warp which dies
     * badly has let go of the ground within half a minute.
     */
    private static final int LIFESPAN_TICKS = 600;

    private static final TicketType<ChunkPos> WARP_ARRIVAL =
            TicketType.create("aerowarptics:warp_arrival", Comparator.comparingLong(ChunkPos::toLong),
                    LIFESPAN_TICKS);

    private ArrivalTicket() {
    }

    /**
     * Claims the ground a hull is about to arrive on.
     *
     * @param level    the level the anchor lives in
     * @param arrival  where the hull will come to rest
     * @param hullSpan the hull's own extent, so a large ship claims more than a skiff
     */
    public static void hold(ServerLevel level, Vector3dc arrival, double hullSpan) {
        ChunkPos centre = new ChunkPos(Mth.floor(arrival.x()) >> 4, Mth.floor(arrival.z()) >> 4);
        level.getChunkSource().addRegionTicket(WARP_ARRIVAL, centre, radiusFor(hullSpan), centre);
    }

    /**
     * Chunks either side of the arrival to claim.
     *
     * <p>Enough for the hull itself plus the run it makes coming out of the aperture, and capped so a
     * very large vessel cannot ask the server to load an unreasonable area.
     */
    private static int radiusFor(double hullSpan) {
        return Mth.clamp((int) Math.ceil(hullSpan / 16.0D) + 2, 2, 8);
    }
}
