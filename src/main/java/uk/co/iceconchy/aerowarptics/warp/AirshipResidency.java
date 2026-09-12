package uk.co.iceconchy.aerowarptics.warp;

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

import java.util.function.LongPredicate;

/**
 * Keeps an airship - and the ground under it - loaded across a server restart.
 *
 * <p>{@link ArrivalTicket}'s renewing claim keeps a landing resident while the server runs, but it
 * cannot survive a restart: region tickets are not written to disk, and a drive whose chunk is cold
 * on boot never ticks to renew one. This uses NeoForge's <em>forced chunks</em> instead, which are
 * persisted with the level and reinstated when the server comes back up.
 *
 * <p>What is forced is the real-world ground under the hull, and only that. Sable keeps a sub-level
 * loaded for as long as the world chunk it sits over is loaded, and brings it - plot, blocks, drive and
 * all - back in when that chunk comes back off disk. So holding the ground is holding the ship, and the
 * drive aboard it loads back in with the ship and carries on maintaining the claim.
 *
 * <p><strong>Never a chunk in plot space.</strong> A ship's plot chunks are not vanilla chunks: Sable
 * serves them itself, and parks its own {@code PlotChunkHolder}s in vanilla's chunk map so lookups find
 * them. Sable cancels the ordinary {@code addRegionTicket} for plot coordinates for exactly that reason,
 * but NeoForge's forced chunks go through the overload that also takes a ticking flag, which Sable does
 * not guard. This class used to force the hull's plot chunks that way, and it cost every world with a
 * drive in it the ability to shut down. When the server closes, vanilla drops every ticket and waits
 * for each chunk it was holding to unload; a {@code PlotChunkHolder} answers {@code isReadyForSaving}
 * with a flat {@code false}, so its unload re-queues itself forever, and the save on exit never
 * finishes - thirty minutes and counting. The same tickets sent vanilla off to load chunks at plot
 * coordinates, which is where the empty {@code region/r.40001.40001.mca} files in an affected save came
 * from, and the likeliest source of the light-engine crash seen alongside them.
 *
 * <p>The price of persistence is that a forced chunk <em>can</em> leak where a renewing ticket cannot:
 * it outlives its owner unless something explicitly lets it go. The drive does, from its removal hook;
 * and because a ship can only be taken apart while it is loaded, the drive is always present to do so.
 * The one residual risk - a drive block removed by an external tool while its chunk was somehow not
 * loaded - is the same risk every persistent chunk loader carries.
 */
public final class AirshipResidency {

    private static final TicketController CONTROLLER =
            new TicketController(AeroWarptics.id("airship_residency"), AirshipResidency::validateOnLoad);

    private AirshipResidency() {
    }

    /** Registered on the mod event bus from {@link AeroWarptics}. */
    public static void onRegisterControllers(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    /**
     * Reinstates every persisted ground claim, and drops every claim in plot space before it can be.
     *
     * <p>Ground claims are all kept: each was written because a drive asked for it, and keeping them is
     * what lets the ship - and so the drive - load back in and re-validate itself on its first renewal,
     * releasing anything it no longer wants and the lot if its block has gone.
     *
     * <p>Plot claims are the ones earlier versions wrote, and they have to go here rather than from the
     * drive. This runs before NeoForge re-adds anything, so stripping one never creates a ticket at all;
     * letting it be reinstated and releasing it afterwards would drop a {@code PlotChunkHolder}'s level
     * and send it into the very unload that never finishes. See the class description.
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
    }

    /**
     * Forces or unforces one ground chunk owned by the drive at {@code owner}.
     *
     * <p>A chunk in plot space is refused either way, adding or releasing. Adding one is the bug in the
     * class description; releasing one is the same bug by another route, since a release drops the
     * ticket level of whatever {@code PlotChunkHolder} the claim had reached.
     *
     * <p>Never ticking. A ticking forced chunk has vanilla random-tick and spawn in it with nobody
     * there, and residency only ever wanted the blocks present. The drive's own ticking comes from
     * Sable ticking the plot it is in, not from anything forced here.
     *
     * @param add {@code true} to claim the chunk, {@code false} to let it go
     */
    public static void set(ServerLevel level, BlockPos owner, long chunk, boolean add) {
        if (plotSpace(level).test(chunk)) {
            return;
        }
        ChunkPos pos = new ChunkPos(chunk);
        CONTROLLER.forceChunk(level, owner, pos.x, pos.z, add, false);
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
