package uk.co.iceconchy.aerowarptics.warp;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Comparator;

/**
 * Keeps the far end of a warp - and the corridor it is flown down - loaded while a ship is on its way.
 *
 * <p>An arrival lands a hull in a place nobody may have stood for a long time, and the chunks it lands
 * in have to come off disk. Left to itself that happens at the worst possible moment - during the
 * teleport - and shows up as a hitch just as the ship comes out of the aperture. Claiming the ground
 * when the warp is planned gives it the whole flight to load quietly instead.
 *
 * <p>The corridor is claimed for the same reason turned around: the departure path is proven clear at
 * the mooring ({@link LaunchClearance}), and that proof only stays true if a block cannot stream in or
 * update along the path during the several-second flight. Holding the corridor resident is what keeps
 * the proof honest until the hull is through.
 *
 * <p>Every ticket carries its own lifespan, so there is nothing to release and no way to leak a
 * permanently loaded region if a warp ends somewhere unexpected. That matters more than it sounds:
 * forced chunks that outlive their reason are a slow, invisible drain on a server.
 */
public final class ArrivalTicket {

    /**
     * Long enough to cover a whole warp with room to spare, short enough that a warp which dies
     * badly has let go of the ground within half a minute.
     */
    private static final int LIFESPAN_TICKS = 600;

    /**
     * How often a drive reconciles the persistent claim that keeps its airship - and any wilderness it
     * warped into - loaded (see {@link AirshipResidency}). Not a keep-alive: those forced chunks do not
     * expire, so this only has to run often enough to follow a ship to where it comes to rest, not to
     * stop the claim lapsing. Kept in step with the arrival ticket's own timescale so the two hand off
     * cleanly when a warp settles.
     */
    public static final int RESIDENCY_RENEW_INTERVAL = 400;

    private static final TicketType<ChunkPos> WARP_ARRIVAL =
            TicketType.create("aerowarptics:warp_arrival", Comparator.comparingLong(ChunkPos::toLong),
                    LIFESPAN_TICKS);

    /**
     * The corridor the hull is flown down, held for the duration of a flight.
     *
     * <p>The hull leaves its mooring and flies forward in real world space for the whole corridor
     * run before the single teleport carries it across. That path is proven clear before the rift
     * opens; without holding it resident, a block updating or streaming in mid-flight would pop
     * through the hull the proof said had a clear run.
     */
    private static final TicketType<ChunkPos> WARP_CORRIDOR =
            TicketType.create("aerowarptics:warp_corridor", Comparator.comparingLong(ChunkPos::toLong),
                    LIFESPAN_TICKS);

    /**
     * The drive's own chunk, held for the duration of a flight.
     *
     * <p>A drive lives in its airship's plot, which is an ordinary chunk of the level at extraordinary
     * coordinates - and an ordinary chunk can be unloaded. If that happens mid-warp the drive comes
     * back off disk, finds a sequence running, and drops it: the flight ends silently at whatever
     * stage it had reached and nothing finishes the journey or settles the crew.
     *
     * <p>Which is not hypothetical. It shows up on large hulls, where moving the plot is expensive
     * enough to stall the server, and it is what {@code WarpTrace.interrupted} now reports.
     */
    private static final TicketType<ChunkPos> WARP_DRIVE =
            TicketType.create("aerowarptics:warp_drive", Comparator.comparingLong(ChunkPos::toLong),
                    LIFESPAN_TICKS);

    private ArrivalTicket() {
    }

    /**
     * Claims the ground the drive itself is standing on.
     *
     * <p>One chunk either side, because a hull's plot is small and the only thing that has to survive
     * is the block entity running the sequence.
     */
    public static void holdDrive(ServerLevel level, BlockPos drivePos) {
        ChunkPos chunk = new ChunkPos(drivePos);
        level.getChunkSource().addRegionTicket(WARP_DRIVE, chunk, 2, chunk);
    }

    /**
     * Claims the ground a hull is about to arrive on.
     *
     * <p>The radius is derived from the whole arrival footprint - the hull, the run it makes coming
     * out of the aperture, and the aperture's own width - not the hull's span alone. The leading part
     * of a long run-out can otherwise sit in a chunk the ticket never claimed, briefly unheld at the
     * exact moment it matters.
     *
     * @param level         the level the anchor lives in
     * @param arrival       where the hull will come to rest
     * @param footprintSpan the full extent of the arrival, from {@link #arrivalSpan}
     */
    public static void hold(ServerLevel level, Vector3dc arrival, double footprintSpan) {
        ChunkPos centre = new ChunkPos(Mth.floor(arrival.x()) >> 4, Mth.floor(arrival.z()) >> 4);
        level.getChunkSource().addRegionTicket(WARP_ARRIVAL, centre, radiusFor(footprintSpan), centre);
    }

    /**
     * Claims the corridor path a hull is about to fly down, so the launch-clearance proof stays true
     * for the whole flight.
     *
     * <p>Centred on the midpoint of the run so one square region covers as much of the line as its
     * radius allows, and capped like every other ticket here: a very long corridor is not worth
     * force-loading end to end, and the far end is the least likely place a block updates into an
     * empty sky.
     *
     * @param level     the level the hull flies through, i.e. its own
     * @param departure where the hull sets off from
     * @param bow       unit vector the hull flies along
     * @param reach     how far ahead the hull flies before the teleport, from
     *                  {@link LaunchClearance#launchReach}
     */
    public static void holdCorridor(ServerLevel level, Vector3dc departure, Vector3dc bow, double reach) {
        Vector3d midpoint = new Vector3d(bow).mul(Math.max(0.0D, reach) * 0.5D).add(departure);
        ChunkPos centre = new ChunkPos(Mth.floor(midpoint.x) >> 4, Mth.floor(midpoint.z) >> 4);
        level.getChunkSource().addRegionTicket(WARP_CORRIDOR, centre, radiusForReach(reach), centre);
    }

    /**
     * Chunks either side of the arrival to claim.
     *
     * <p>Enough for the whole arrival footprint, and capped so a very large vessel cannot ask the
     * server to load an unreasonable area.
     */
    public static int radiusFor(double footprintSpan) {
        return Mth.clamp((int) Math.ceil(Math.max(0.0D, footprintSpan) / 16.0D) + 2, 2, 8);
    }

    /**
     * Chunks either side of the corridor midpoint to claim.
     *
     * <p>Half the reach has to fit inside the radius, because the midpoint sits at the middle of the
     * run. Capped higher than the arrival because a corridor is a long thin thing, but still capped:
     * beyond this the far end is left to whatever loads it naturally.
     */
    public static int radiusForReach(double reach) {
        return Mth.clamp((int) Math.ceil(Math.max(0.0D, reach) * 0.5D / 16.0D) + 2, 2, 12);
    }

    /**
     * The full extent of an arrival, for sizing the ticket: the hull, the run it coasts out of the
     * aperture, and the aperture's own width on each side.
     */
    public static double arrivalSpan(double hullSpan, double emergeRunOut, double apertureMargin) {
        return hullSpan + Math.max(0.0D, emergeRunOut) + 2.0D * Math.max(0.0D, apertureMargin);
    }

    /**
     * Whether a landing will stay loaded once this ticket lapses.
     *
     * <p>The pure decision, kept free of a server so it can be tested: a landing is durable when a
     * player is close enough to keep it in view, or a force-loaded region covers it. Neither is true
     * of an anchor sitting alone in unloaded wilderness, which is exactly the case a warp must not fly
     * a ship into and then let go of - the world under it unloads and Sable removes the hull.
     */
    public static boolean durableLanding(boolean hasChunkLoader, boolean withinViewDistance) {
        return hasChunkLoader || withinViewDistance;
    }

    /**
     * Whether the landing zone of a planned arrival is durable against a real level.
     *
     * <p>"Within view distance" is measured against a player actually being near enough that the
     * chunk stays ticking after the ticket goes; "has a loader" is a force-loaded chunk, the base
     * game's own way of keeping ground resident with nobody there.
     */
    public static boolean isLandingDurable(ServerLevel level, Vector3dc arrival) {
        ChunkPos landing = new ChunkPos(Mth.floor(arrival.x()) >> 4, Mth.floor(arrival.z()) >> 4);
        boolean forced = level.getForcedChunks().contains(landing.toLong());
        boolean inView = false;
        int viewChunks = level.getServer().getPlayerList().getViewDistance() + 1;
        for (ServerPlayer player : level.players()) {
            ChunkPos at = player.chunkPosition();
            if (Math.abs(at.x - landing.x) <= viewChunks && Math.abs(at.z - landing.z) <= viewChunks) {
                inView = true;
                break;
            }
        }
        return durableLanding(forced, inView);
    }
}
