package uk.co.iceconchy.aerowarptics.warp;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import uk.co.iceconchy.aerowarptics.AeroWarptics;

/**
 * Keeps an airship - and the ground under it - loaded across a server restart.
 *
 * <p>{@link ArrivalTicket}'s renewing claim keeps a landing resident while the server runs, but it
 * cannot survive a restart: region tickets are not written to disk, and a drive whose chunk is cold
 * on boot never ticks to renew one. This uses NeoForge's <em>forced chunks</em> instead, which are
 * persisted with the level and reinstated when the server comes back up. The claim is owned by the
 * drive's block position, and because the drive's own plot chunk is one of the chunks it forces, the
 * drive itself loads back off disk and carries on maintaining the claim - so an unattended ship parked
 * in empty wilderness is still there, still loaded, after a restart.
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
     * Reinstates every persisted claim rather than dropping any.
     *
     * <p>Each ticket was written because a drive asked for it, and each drive's own plot chunk is
     * among its tickets - so keeping them all is exactly what lets a drive load back in and
     * re-validate itself on its first tick, unforcing anything it no longer wants and releasing the
     * lot if its block has gone. Dropping a ticket here would unload the very chunk whose drive is the
     * only thing that could ever have decided the ticket was stale.
     */
    private static void validateOnLoad(ServerLevel level, TicketHelper ticketHelper) {
        // Intentionally empty: keep all tickets, let each drive tidy up its own.
    }

    /**
     * Forces or unforces one chunk owned by the drive at {@code owner}.
     *
     * <p><strong>Almost every chunk here must be non-ticking.</strong> A ticking forced chunk is not
     * one chunk: vanilla has to bring the ring around it up to full status for it to tick safely, so
     * each claim pulls its neighbours in too. Claiming a hull and its ground as ticking turned roughly
     * seven hundred tickets into four thousand loaded chunks and seventeen hundred more in generation,
     * and the server thread then spent minutes on end inside {@code ChunkMap.processUnloads} - which
     * looks from the game like everything having stopped: no block breaks, no chest opens, no menu
     * applies, because none of those are being read.
     *
     * <p>Only the drive's own chunk needs to tick, and only so the drive keeps renewing the claim
     * after a restart. Residency wants the blocks <em>present</em>, which non-ticking gives; it never
     * wanted the whole hull running.
     *
     * <p>NeoForge tracks ticking and non-ticking claims separately, so a chunk must be released with
     * the same flag it was claimed with, or the claim is left behind. That is why the caller derives
     * the flag from the drive's own position both times rather than remembering it.
     *
     * @param add     {@code true} to claim the chunk, {@code false} to let it go
     * @param ticking whether the chunk should tick - true only for the drive's own chunk
     */
    public static void set(ServerLevel level, BlockPos owner, long chunk, boolean add, boolean ticking) {
        ChunkPos pos = new ChunkPos(chunk);
        CONTROLLER.forceChunk(level, owner, pos.x, pos.z, add, ticking);
    }
}
