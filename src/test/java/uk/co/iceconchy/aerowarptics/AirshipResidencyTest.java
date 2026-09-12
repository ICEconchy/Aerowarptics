package uk.co.iceconchy.aerowarptics;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.TicketSet;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.AirshipResidency;

import java.util.HashSet;
import java.util.Set;
import java.util.function.LongPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That a world never reinstates a forced chunk inside airship plot space.
 *
 * <p>The shutdown hang. Residency used to force a hull's plot chunks, which are Sable's and not
 * vanilla's; on exit vanilla dropped those tickets and waited for chunks that can never report
 * themselves ready to save, and the world sat on "Saving worlds" indefinitely. Every world that had a
 * drive in it carries those claims on disk, so the load-time strip is what actually rescues them - and
 * the failure that would not throw is a strip that misses one kind of claim, which leaves the world
 * hanging exactly as before with nothing in the log to say why.
 *
 * <p>What this cannot prove without a running game is the hang itself going away; that rests on the
 * Sable behaviour described in {@link AirshipResidency}.
 */
class AirshipResidencyTest {

    /** Sable's plot grid starts a long way out; anything at chunk 1,000,000 or beyond stands in for it. */
    private static final LongPredicate IN_PLOT = chunk -> ChunkPos.getX(chunk) >= 1_000_000;

    private static final long PLOT_A = ChunkPos.asLong(1_280_064, 1_280_064);
    private static final long PLOT_B = ChunkPos.asLong(1_280_065, 1_280_063);
    private static final long GROUND_A = ChunkPos.asLong(7, -3);
    private static final long GROUND_B = ChunkPos.asLong(-5, -1);

    private record Removal(long chunk, boolean ticking) {
    }

    /**
     * Every plot claim is removed, ticking and non-ticking alike, each with the flag it was stored
     * under; no ground claim is touched.
     *
     * <p>The drive's own chunk was claimed ticking and the rest of the plot not, and NeoForge keeps the
     * two apart - a release with the wrong flag releases nothing. The saved world this was diagnosed
     * on had exactly that split.
     */
    @Test
    void everyPlotClaimIsDroppedWithItsOwnFlagAndTheGroundIsKept() {
        TicketSet tickets = new TicketSet(
                new LongOpenHashSet(new long[]{PLOT_A, GROUND_A, GROUND_B}),
                new LongOpenHashSet(new long[]{PLOT_B}));
        Set<Removal> removed = new HashSet<>();
        AirshipResidency.forEachPlotClaim(tickets, IN_PLOT, (chunk, ticking) -> removed.add(new Removal(chunk, ticking)));

        assertEquals(Set.of(new Removal(PLOT_A, false), new Removal(PLOT_B, true)), removed);
    }

    /**
     * A remover that edits the very sets being read still sees every plot claim.
     *
     * <p>Which is what NeoForge's helper does when the claims are live. Walking the set while it
     * shrinks under the walk would skip entries or throw, and a skipped claim is a world that still
     * cannot shut down.
     */
    @Test
    void removingWhileWalkingMissesNothing() {
        LongOpenHashSet nonTicking = new LongOpenHashSet();
        for (int i = 0; i < 200; i++) {
            nonTicking.add(ChunkPos.asLong(1_280_000 + i, 1_280_000));
            nonTicking.add(ChunkPos.asLong(i, 0));
        }
        TicketSet tickets = new TicketSet(nonTicking, new LongOpenHashSet());
        int[] seen = {0};
        AirshipResidency.forEachPlotClaim(tickets, IN_PLOT, (chunk, ticking) -> {
            nonTicking.remove(chunk);
            seen[0]++;
        });

        assertEquals(200, seen[0], "every plot claim must be handed over, not only the ones walked first");
        assertEquals(200, nonTicking.size(), "and only those: the ground claims all remain");
        assertTrue(nonTicking.longStream().noneMatch(IN_PLOT::test));
    }

    /** A world with no plot claims - every world from here on - is left exactly as it was. */
    @Test
    void groundOnlyClaimsAreLeftAlone() {
        TicketSet tickets = new TicketSet(
                new LongOpenHashSet(new long[]{GROUND_A, GROUND_B}), new LongOpenHashSet());
        AirshipResidency.forEachPlotClaim(tickets, IN_PLOT, (chunk, ticking) -> {
            throw new AssertionError("a ground claim at " + new ChunkPos(chunk) + " was dropped");
        });
    }
}
