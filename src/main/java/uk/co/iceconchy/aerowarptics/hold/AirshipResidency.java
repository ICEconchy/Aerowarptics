package uk.co.iceconchy.aerowarptics.hold;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import net.neoforged.neoforge.common.world.chunk.TicketSet;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.trace.Trace;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.LongPredicate;

/**
 * This mod's NeoForge forced chunks: now only the one chunk per level that keeps a level with held ships
 * awake, and the clean-up of what earlier versions forced.
 *
 * <p><strong>What it used to do.</strong> Residency forced the ground under a parked ship, chunk by chunk,
 * and relied on Sable keeping a ship loaded over loaded ground. That is not quite Sable's rule - it keeps a
 * ship only while every column under its box is in block-ticking range, not merely loaded - and the claim
 * was renewed every twenty seconds, so a ship that moved (a warp, above all) sat on ground the claim had
 * not caught up with, and Sable unloaded it. A ship is now held with Sable's own force-load ticket instead
 * ({@link Holds#holdShip}), which keeps the ground under it wherever it goes. See {@link HoldLedger}.
 *
 * <p><strong>What it does now.</strong> Sable's ticket is not a forced chunk as far as the game is
 * concerned, and a level with no players and no forced chunks stops ticking its block entities after
 * fifteen seconds - the drive of a held ship included. One non-ticking forced chunk under a held ship
 * keeps the level going. {@link HoldUpkeep} chooses it ({@link HoldRules#wakeChunk}); it is already held by
 * that ship's own ticket, so forcing it loads nothing.
 *
 * <p><strong>Never a chunk in plot space.</strong> A ship's plot chunks are not vanilla chunks: Sable serves
 * them itself, and parks its own {@code PlotChunkHolder}s in vanilla's chunk map so lookups find them.
 * Sable cancels the ordinary {@code addRegionTicket} for plot coordinates for exactly that reason, but
 * NeoForge's forced chunks go through the overload that also takes a ticking flag, which Sable does not
 * guard. This class used to force the hull's plot chunks that way, and it cost every world with a drive in
 * it the ability to shut down: on exit vanilla drops every ticket and waits for each chunk it was holding
 * to unload, a {@code PlotChunkHolder} answers {@code isReadyForSaving} with a flat {@code false}, and the
 * save on exit never finishes. The same tickets sent vanilla off to load chunks at plot coordinates, which
 * is where the empty {@code region/r.40001.40001.mca} files in an affected save came from.
 */
public final class AirshipResidency {

    /** Kept from before, so the claims every older world has on disk are still this controller's to judge. */
    private static final TicketController CONTROLLER =
            new TicketController(AeroWarptics.id("airship_residency"), AirshipResidency::validateOnLoad);

    /** The owner of every wake chunk. One per level at most, and a level's forced chunks are its own. */
    static final UUID WAKE_OWNER = UUID.nameUUIDFromBytes("aerowarptics:wake".getBytes(StandardCharsets.UTF_8));

    private AirshipResidency() {
    }

    /** Registered on the mod event bus from {@link AeroWarptics}. */
    public static void onRegisterControllers(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    /**
     * Decides, before the level reinstates anything, which of this mod's forced chunks it keeps.
     *
     * <ul>
     *   <li>Anything in plot space goes, whoever owns it - the shutdown hang in the class description.
     *   Stripped here rather than later because this runs before NeoForge re-adds anything, so the ticket
     *   is never created at all.</li>
     *   <li>The wake chunk stays only if the ledger still has a ship held in this level. A server that
     *   stopped with nothing held, or whose holds have all lapsed, does not come back with a level kept
     *   awake for nothing.</li>
     *   <li>Any other entity-owned claim is not one this version writes, and goes.</li>
     *   <li>Ground claims owned by a block are what drives forced before ships were held with Sable's
     *   ticket. They stay, once: they are what loads an old ship back in, and its drive, on its first
     *   tick, takes the new hold and lets every one of them go.</li>
     * </ul>
     */
    private static void validateOnLoad(ServerLevel level, TicketHelper ticketHelper) {
        LongPredicate inPlot = plotSpace(level);
        int[] dropped = {0};
        ticketHelper.getBlockTickets().forEach((owner, tickets) ->
                forEachPlotClaim(tickets, inPlot, (chunk, ticking) -> {
                    ticketHelper.removeTicket(owner, chunk, ticking);
                    dropped[0]++;
                }));
        if (dropped[0] > 0) {
            AeroWarptics.LOGGER.info("Dropped {} forced chunk claim(s) inside airship plot space in {} - "
                    + "left over from an older version, and what stopped the world saving on exit",
                    dropped[0], level.dimension().location());
        }

        HoldLedger ledger = HoldLedger.get(level.getServer());
        boolean held = ledger.anyLiveHoldIn(level.dimension(), level.getServer().overworld().getGameTime());
        ticketHelper.getEntityTickets().forEach((owner, tickets) -> {
            if (owner.equals(WAKE_OWNER) && held) {
                forEachPlotClaim(tickets, inPlot, (chunk, ticking) -> ticketHelper.removeTicket(owner, chunk, ticking));
                return;
            }
            ticketHelper.removeAllTickets(owner);
        });
        if (!held) {
            ledger.setWakeChunk(level.dimension(), null);
        }
    }

    /**
     * Moves a level's wake chunk: forces {@code chunk} and lets go of {@code previous}.
     *
     * <p>Only ever asked for a chunk a held ship is over, which that ship's ticket already holds - NeoForge
     * loads a chunk synchronously as it forces it, and this must never be the thing that makes it do so for
     * a chunk that was not already there. Never ticking: residency wants the level's block entities
     * ticking, not random ticks and mob spawning in a chunk nobody is in.
     */
    static void wake(ServerLevel level, long previous, boolean hadPrevious, long chunk, boolean hasChunk) {
        if (hasChunk && !plotSpace(level).test(chunk)) {
            CONTROLLER.forceChunk(level, WAKE_OWNER, ChunkPos.getX(chunk), ChunkPos.getZ(chunk), true, false);
        }
        if (hadPrevious && !(hasChunk && previous == chunk)) {
            CONTROLLER.forceChunk(level, WAKE_OWNER, ChunkPos.getX(previous), ChunkPos.getZ(previous), false, false);
        }
        if (Trace.enabled()) {
            Trace.log(Trace.HOLD, "{} kept awake by {}{}", level.dimension().location(),
                    hasChunk ? new ChunkPos(chunk) : "nothing - no ship held there",
                    hadPrevious && (!hasChunk || previous != chunk) ? " (was " + new ChunkPos(previous) + ")" : "");
        }
    }

    /**
     * Lets go of one ground chunk a drive forced before ships were held with Sable's ticket.
     *
     * <p>A chunk in plot space is refused, since releasing one drops the ticket level of whatever
     * {@code PlotChunkHolder} the claim had reached - the shutdown hang by another route. Those were stripped
     * when the world loaded anyway.
     */
    public static void releaseLegacy(ServerLevel level, BlockPos owner, long chunk) {
        if (plotSpace(level).test(chunk)) {
            return;
        }
        boolean done = CONTROLLER.forceChunk(level, owner, ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false, false);
        if (Trace.enabled()) {
            Trace.log(Trace.RESIDENCY, "drive {} lets go of its old forced chunk {} in {}{}", Trace.pos(owner),
                    new ChunkPos(chunk), level.dimension().location(), done ? "" : " - it was not held");
        }
    }

    /** Whether a packed chunk position lies in this level's airship plot space. */
    public static LongPredicate plotSpace(ServerLevel level) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return chunk -> false;
        }
        return chunk -> container.inBounds(ChunkPos.getX(chunk), ChunkPos.getZ(chunk));
    }

    /** Receives one plot-space claim to remove, and whether it was the ticking kind. */
    @FunctionalInterface
    public interface ClaimRemover {
        void remove(long chunk, boolean ticking);
    }

    /**
     * Hands every claim in {@code tickets} that lies in plot space to {@code remove}, ticking and
     * non-ticking alike, and leaves every other claim alone.
     *
     * <p>The plot claims are gathered before any is handed over, so a remover that edits the very sets
     * being read cannot skip one. NeoForge keeps ticking and non-ticking claims apart, and a claim
     * released with the wrong flag is not released at all - so each is reported with the flag it was
     * stored under, never a guess.
     */
    public static void forEachPlotClaim(TicketSet tickets, LongPredicate inPlot, ClaimRemover remove) {
        LongList nonTicking = new LongArrayList();
        LongList ticking = new LongArrayList();
        tickets.nonTicking().forEach(chunk -> {
            if (inPlot.test(chunk)) {
                nonTicking.add(chunk);
            }
        });
        tickets.ticking().forEach(chunk -> {
            if (inPlot.test(chunk)) {
                ticking.add(chunk);
            }
        });
        nonTicking.forEach(chunk -> remove.remove(chunk, false));
        ticking.forEach(chunk -> remove.remove(chunk, true));
    }
}
