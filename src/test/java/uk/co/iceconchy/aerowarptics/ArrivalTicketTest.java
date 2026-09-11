package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import net.minecraft.world.level.ChunkPos;
import uk.co.iceconchy.aerowarptics.warp.ArrivalTicket;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How much ground a warp claims, and whether a landing will keep.
 *
 * <p>Two silent failures live here. The arrival ticket sized from the hull's span alone leaves the
 * leading edge of a long run-out in an unheld chunk, briefly unloaded at the exact moment it matters;
 * and a warp to an anchor nobody is near lands the ship, holds the ground for the flight, and then
 * lets go - and the world unloads out from under a hull that had arrived perfectly well.
 */
class ArrivalTicketTest {

    /** The whole arrival footprint, not the bare hull span. */
    @Test
    void theArrivalSpanCountsTheRunOutAndTheAperture() {
        // hull 40, run-out 120, aperture margin 6 -> 40 + 120 + 12.
        assertEquals(172.0D, ArrivalTicket.arrivalSpan(40.0D, 120.0D, 6.0D), 1.0e-9D);
        // Negatives cannot shrink it below the hull.
        assertEquals(40.0D, ArrivalTicket.arrivalSpan(40.0D, -10.0D, -1.0D), 1.0e-9D);
    }

    /**
     * The arrival radius grows with the footprint and is capped.
     *
     * <p>A skiff claims the floor of two chunks either side; a dreadnought's whole footprint is
     * covered up to the cap, past which force-loading more is a server cost not worth paying.
     */
    @Test
    void theArrivalRadiusFollowsTheFootprintAndIsCapped() {
        assertEquals(2, ArrivalTicket.radiusFor(0.0D), "a tiny hull still claims a floor of 2");
        assertEquals(3, ArrivalTicket.radiusFor(8.0D), "half a chunk of footprint still rounds up to a chunk");
        assertTrue(ArrivalTicket.radiusFor(200.0D) > ArrivalTicket.radiusFor(40.0D),
                "a bigger footprint claims more");
        assertEquals(8, ArrivalTicket.radiusFor(10_000.0D), "capped so a huge hull cannot ask for the world");
    }

    /** The corridor radius has to reach half the run and is capped higher, being a long thin thing. */
    @Test
    void theCorridorRadiusReachesHalfTheRunAndIsCapped() {
        assertEquals(2, ArrivalTicket.radiusForReach(0.0D));
        assertTrue(ArrivalTicket.radiusForReach(400.0D) > ArrivalTicket.radiusForReach(80.0D));
        assertEquals(12, ArrivalTicket.radiusForReach(100_000.0D), "even a very long corridor is capped");
    }

    /**
     * A landing keeps only when a player can see it or a loader holds it.
     *
     * <p>The pure decision behind the commit-time refusal. An anchor alone in unloaded wilderness is
     * neither, which is exactly the warp that loses a ship once the flight's own ticket lapses.
     */
    @Test
    void aLandingIsDurableOnlyWithAPlayerOrALoader() {
        assertTrue(ArrivalTicket.durableLanding(true, false), "a chunk loader keeps it");
        assertTrue(ArrivalTicket.durableLanding(false, true), "a nearby player keeps it");
        assertTrue(ArrivalTicket.durableLanding(true, true));
        assertFalse(ArrivalTicket.durableLanding(false, false),
                "unattended wilderness with no loader is not somewhere to leave a ship");
    }

    /**
     * The plot claim covers every chunk of the hull, at any size.
     *
     * <p>This is the ship-lost bug. Residency held a fixed patch around the drive, so a hull whose
     * plot spanned more than that patch left its far chunks unheld; once the arrival ticket lapsed
     * they unloaded and Sable removed the sub-level, and the crew - ordinary entities - arrived onto
     * a ship that no longer existed. Sizing the claim from the plot's own extent is what fixes it, so
     * the property to lock down is total coverage of the plot, whatever its size.
     */
    @Test
    void thePlotClaimCoversTheWholeHullAtEverySize() {
        // Every size a real hull comes in. Above the cap the claim deliberately stops growing, which
        // aTinyPlotIsNotPaddedAndAHugeOneIsClamped covers.
        for (int span : new int[]{1, 3, 8}) {
            ChunkPos min = new ChunkPos(-span, 100 - span);
            ChunkPos max = new ChunkPos(span, 100 + span);
            Set<Long> held = ArrivalTicket.plotResidencyChunks(min, max, 1);
            for (int x = min.x; x <= max.x; x++) {
                for (int z = min.z; z <= max.z; z++) {
                    assertTrue(held.contains(ChunkPos.asLong(x, z)),
                            "plot chunk " + x + "," + z + " of a " + (2 * span + 1) + "-wide hull was not held");
                }
            }
        }
    }

    /**
     * A drive can never ask for an unbounded number of chunks.
     *
     * <p>This is the server-hang bug, and it is the mirror of the one above. Residency was sized from
     * {@code LevelPlot.getChunkMin/Max}, which is the fixed square Sable <em>reserves</em> for a
     * sub-level rather than anything to do with how big the ship is - so placing a drive on any hull
     * at all force-loaded that entire reservation, ticking, on the drive's first tick, and the server
     * went away for over a minute. Two things fix it: the drive now sizes from the hull's own bounding
     * box, and this cap makes sure no plot geometry can ever produce a claim with no ceiling again.
     *
     * <p>The property is stated as a ceiling rather than an exact figure on purpose - what matters is
     * that it is bounded, not that it is precisely four hundred.
     */
    @Test
    void thePlotClaimIsBoundedHoweverLargeTheExtentIs() {
        for (int span : new int[]{16, 32, 256, 4096}) {
            Set<Long> held = ArrivalTicket.plotResidencyChunks(
                    new ChunkPos(-span, 100 - span), new ChunkPos(span, 100 + span), 1);
            int cap = ArrivalTicket.PLOT_RESIDENCY_MAX_SIDE * ArrivalTicket.PLOT_RESIDENCY_MAX_SIDE;
            assertTrue(held.size() <= cap,
                    "a " + (2 * span + 1) + "-chunk extent asked for " + held.size()
                            + " chunks, over the " + cap + " cap");
        }
    }

    /** What is kept when the extent is clamped is the middle, not an arbitrary corner. */
    @Test
    void aClampedClaimKeepsTheMiddleOfTheHull() {
        int span = 500;
        Set<Long> held = ArrivalTicket.plotResidencyChunks(
                new ChunkPos(-span, 100 - span), new ChunkPos(span, 100 + span), 1);
        assertTrue(held.contains(ChunkPos.asLong(0, 100)),
                "the centre of the hull must survive the clamp - it is where the drive is");
        assertFalse(held.contains(ChunkPos.asLong(-span, 100 - span)),
                "the far corner of an absurd extent must not be held");
    }

    /** The claim is the plot's extent grown by exactly the margin - no less, and not unbounded. */
    @Test
    void thePlotClaimIsTheExtentPlusItsMargin() {
        ChunkPos min = new ChunkPos(4, 4);
        ChunkPos max = new ChunkPos(9, 12); // 6 x 9 chunks
        Set<Long> held = ArrivalTicket.plotResidencyChunks(min, max, 1);
        assertEquals((6 + 2) * (9 + 2), held.size());
        assertTrue(held.contains(ChunkPos.asLong(min.x - 1, min.z - 1)), "the margin's corner is held");
        assertFalse(held.contains(ChunkPos.asLong(min.x - 2, min.z)), "nothing beyond the margin is held");
    }

    /** A single-chunk plot still yields its 3x3 with margin, matching the old small-ship behaviour. */
    @Test
    void aSingleChunkPlotStillGetsItsMargin() {
        Set<Long> held = ArrivalTicket.plotResidencyChunks(new ChunkPos(0, 0), new ChunkPos(0, 0), 1);
        assertEquals(9, held.size());
    }
}
