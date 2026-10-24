package uk.co.iceconchy.aerowarptics;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.level.ChunkPos;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.hold.HoldRules;
import uk.co.iceconchy.aerowarptics.warp.ArrivalTicket;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How much ground a warp claims, and whether a landing will keep.
 *
 * <p>Two silent failures live here. A claim that does not reach the place a hull is actually put down
 * leaves it over ground nothing holds, at the exact moment it matters - which on a long hull was the
 * whole reappearance point, and Sable unloaded the ship mid-warp. And a warp to an anchor nobody is
 * near lands the ship, holds the ground for the flight, and then lets go.
 */
class ArrivalTicketTest {

    /** Either side of an arrival: half the hull across the bearing and the aperture's reach. */
    @Test
    void theArrivalHalfWidthIsHalfTheHullAndTheAperture() {
        assertEquals(36.0D, ArrivalTicket.arrivalHalfWidth(60.0D, 6.0D), 1.0e-9D);
        assertEquals(0.0D, ArrivalTicket.arrivalHalfWidth(-10.0D, -1.0D), 1.0e-9D,
                "negatives cannot make a claim narrower than nothing");
    }

    /** The packing matches vanilla's, or every claim and release would name the wrong chunk. */
    @Test
    void chunksArePackedExactlyAsVanillaPacksThem() {
        for (int[] at : new int[][]{{0, 0}, {1, -1}, {-1, 1}, {1_280_000, -937}, {-30_000_000 >> 4, 29_999_999 >> 4}}) {
            assertEquals(ChunkPos.asLong(at[0], at[1]), ArrivalTicket.pack(at[0], at[1]),
                    at[0] + ", " + at[1]);
        }
    }

    /**
     * Every point within the half width of the run - and past its ends by the overhang - is inside
     * some square.
     *
     * <p>Swept over lengths, widths and bearings, including runs that are not axis aligned and that
     * start and end mid-chunk, because the coverage argument is about the worst-placed point.
     */
    @Test
    void theSquaresCoverEveryPointNearTheRun() {
        double[][] runs = {
                {0, 0, 0, 0},
                {5.5, 7.25, 40, -3},
                {-100, 20, 522, 20},
                {300.3, -91.7, -250.1, 480.9},
                {0, 0, 1000, 1000},
        };
        for (double[] run : runs) {
            for (double halfWidth : new double[]{0.0D, 7.0D, 36.0D, 120.0D}) {
                for (double overhang : new double[]{0.0D, 30.0D, 202.0D}) {
                    List<ArrivalTicket.Square> squares =
                            ArrivalTicket.centresAlong(run[0], run[1], run[2], run[3], halfWidth, overhang);
                    assertCovered(squares, run, halfWidth, overhang);
                }
            }
        }
    }

    /**
     * The two places a hull is set down come first, so a budget trims the coast between them instead.
     */
    @Test
    void theEndsOfTheRunAreTheFirstSquares() {
        List<ArrivalTicket.Square> squares = ArrivalTicket.centresAlong(-400, 12, 122, 12, 40, 200);
        assertTrue(squares.get(0).containsBlock(-400, 12), "the reappearance point is first");
        assertTrue(squares.get(1).containsBlock(122, 12), "the resting place is second");

        List<ArrivalTicket.Square> trimmed = ArrivalTicket.withinBudget(squares, 1);
        assertEquals(1, trimmed.size(), "the first square is kept whatever the budget");
        assertEquals(squares.get(0), trimmed.get(0));

        int both = ArrivalTicket.chunksOf(squares.subList(0, 2)).size();
        List<ArrivalTicket.Square> ends = ArrivalTicket.withinBudget(squares, both);
        assertTrue(ends.size() >= 2 && ends.get(1).equals(squares.get(1)),
                "a budget for exactly the two ends keeps exactly the two ends");
        assertTrue(ArrivalTicket.chunksOf(ends).size() <= both);
    }

    /** A budget is honoured: never more distinct chunks than it allows, past the first square. */
    @Test
    void aBudgetIsNeverExceeded() {
        List<ArrivalTicket.Square> squares = ArrivalTicket.centresAlong(0, 0, 3000, 1700, 90, 300);
        for (int cap : new int[]{200, 1024, 4096}) {
            int held = ArrivalTicket.chunksOf(ArrivalTicket.withinBudget(squares, cap)).size();
            assertTrue(held <= Math.max(cap, ArrivalTicket.chunksOf(squares.subList(0, 1)).size()),
                    held + " chunks held against a cap of " + cap);
        }
    }

    /**
     * The old arrival ticket, kept here as the thing being moved away from: one square about the
     * resting place, radius capped at eight chunks.
     *
     * <p>A four-hundred-block Mk II hull reappears about five hundred and twenty blocks behind where it
     * comes to rest. That point was outside the old square, which is how the ground under a large hull
     * came to be held by nothing at the moment of the crossing. The replacement holds it.
     */
    @Test
    void theOldSingleSquareLeftALargeHullsReappearanceUnheld() {
        Vector3d rests = new Vector3d(10_000.5D, 90.0D, -4_000.5D);
        Vector3d bow = new Vector3d(0.0D, 0.0D, -1.0D);
        double emergeDistance = 521.83D;
        Vector3d reappears = new Vector3d(bow).mul(-emergeDistance).add(rests);

        int legacyRadius = Math.min(8, (int) Math.ceil((400.0D + emergeDistance + 30.0D) / 16.0D) + 2);
        ArrivalTicket.Square legacy = new ArrivalTicket.Square((int) Math.floor(rests.x) >> 4,
                (int) Math.floor(rests.z) >> 4, legacyRadius);
        assertFalse(legacy.containsBlock(reappears.x, reappears.z),
                "if this ever passes the legacy arithmetic in the test is wrong, not the fix right");

        List<ArrivalTicket.Square> now = ArrivalTicket.centresAlong(reappears.x, reappears.z,
                rests.x, rests.z, ArrivalTicket.arrivalHalfWidth(60.0D, 15.0D), 202.0D);
        assertTrue(now.stream().anyMatch(square -> square.containsBlock(reappears.x, reappears.z)));
    }

    /** Tiling a rectangle leaves no chunk of it out, and keeps each square within what a ticket can hold. */
    @Test
    void tilingARectangleLeavesNoGaps() {
        int[][] rectangles = {{0, 0, 0, 0}, {-3, 5, 4, 5}, {10, -40, 12, 90}, {-100, -100, 100, 100}};
        for (int[] r : rectangles) {
            List<ArrivalTicket.Square> squares = ArrivalTicket.tile(r[0], r[1], r[2], r[3]);
            LongOpenHashSet covered = ArrivalTicket.chunksOf(squares);
            for (int x = r[0]; x <= r[2]; x++) {
                for (int z = r[1]; z <= r[3]; z++) {
                    assertTrue(covered.contains(ArrivalTicket.pack(x, z)),
                            "chunk " + x + ", " + z + " of " + java.util.Arrays.toString(r));
                }
            }
            assertTrue(squares.stream().allMatch(s -> s.radius() <= ArrivalTicket.MAX_RADIUS));
            assertTrue(covered.size() == (r[2] - r[0] + 1) * (r[3] - r[1] + 1),
                    "the tiling of " + java.util.Arrays.toString(r) + " claimed chunks outside it");
        }
    }

    /**
     * Every chunk a ticking tile covers is loaded enough for Sable to keep a ship over it.
     *
     * <p>Sable keeps a ship - and rebuilds an unloaded one - only while every column under its box is at
     * ticket level 32 or below. A plain {@link ArrivalTicket#tile} brings its whole rectangle to full status,
     * but its squares' outer rings sit at 33: loaded, and not enough. That gap is how a ship brought down
     * onto held ground was unloaded anyway. Swept across shapes from a single chunk to a strip longer than
     * the widest square, so the edges between squares are covered as well as the rim.
     */
    @Test
    void tickingTileMakesEveryChunkBlockTicking() {
        int[][] rectangles = {
                {0, 0, 0, 0}, {0, 0, 1, 0}, {-3, -3, 3, 3}, {0, 0, 25, 3}, {-40, 5, 40, 9},
                {10, 10, 90, 150}, {-2, -70, 1, 70}};
        for (int[] r : rectangles) {
            List<ArrivalTicket.Square> squares = ArrivalTicket.tickingTile(r[0], r[1], r[2], r[3]);
            assertTrue(squares.stream().allMatch(s -> s.radius() <= ArrivalTicket.MAX_RADIUS),
                    "a square wider than a ticket can hold in " + java.util.Arrays.toString(r));
            for (int x = r[0]; x <= r[2]; x++) {
                for (int z = r[1]; z <= r[3]; z++) {
                    int best = Integer.MAX_VALUE;
                    for (ArrivalTicket.Square square : squares) {
                        int distance = Math.max(Math.abs(x - square.chunkX()), Math.abs(z - square.chunkZ()));
                        best = Math.min(best, HoldRules.ticketLevel(square.radius(), distance));
                    }
                    assertTrue(best <= HoldRules.BLOCK_TICKING_LEVEL, "chunk " + x + ", " + z + " of "
                            + java.util.Arrays.toString(r) + " only reaches level " + best);
                }
            }
        }
    }

    /**
     * The guard above is not satisfied by accident: the plain tiling it replaces fails it.
     *
     * <p>A guard that has never failed is a guard nobody has checked. The rim of a plain tile is exactly the
     * ground a warp's own tickets used to leave a ship on.
     */
    @Test
    void aPlainTileLeavesItsRimMerelyLoaded() {
        List<ArrivalTicket.Square> plain = ArrivalTicket.tile(0, 0, 8, 8);
        boolean rimShort = false;
        for (int x = 0; x <= 8; x++) {
            int best = Integer.MAX_VALUE;
            for (ArrivalTicket.Square square : plain) {
                int distance = Math.max(Math.abs(x - square.chunkX()), Math.abs(0 - square.chunkZ()));
                best = Math.min(best, HoldRules.ticketLevel(square.radius(), distance));
            }
            rimShort |= best > HoldRules.BLOCK_TICKING_LEVEL;
        }
        assertTrue(rimShort, "a plain tile's rim is block-ticking after all - the ticking tile is redundant");
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

    // ---------------------------------------------------------------- helpers

    /** Walks a grid over the stretched, widened run and checks every sample is inside some square. */
    static void assertCovered(List<ArrivalTicket.Square> squares, double[] run, double halfWidth,
                              double overhang) {
        double length = Math.hypot(run[2] - run[0], run[3] - run[1]);
        double dirX = length > 1.0e-9D ? (run[2] - run[0]) / length : 1.0D;
        double dirZ = length > 1.0e-9D ? (run[3] - run[1]) / length : 0.0D;
        double step = 3.7D;
        for (double u = -overhang; u <= length + overhang + 1.0e-9D; u += step) {
            for (double v = -halfWidth; v <= halfWidth + 1.0e-9D; v += step) {
                double x = run[0] + dirX * u - dirZ * v;
                double z = run[1] + dirZ * u + dirX * v;
                boolean inside = false;
                for (ArrivalTicket.Square square : squares) {
                    if (square.containsBlock(x, z)) {
                        inside = true;
                        break;
                    }
                }
                assertTrue(inside, String.format(java.util.Locale.ROOT,
                        "(%.1f, %.1f) unheld on run %s width %.1f overhang %.1f",
                        x, z, java.util.Arrays.toString(run), halfWidth, overhang));
            }
        }
    }
}
