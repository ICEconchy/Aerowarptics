package uk.co.iceconchy.aerowarptics.hold;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * The decisions behind every hold, kept free of a running game so they can be tested directly.
 *
 * <p>Nothing here loads anything. {@link Holds} does the loading and asks this what to do.
 */
public final class HoldRules {

    /**
     * The widest a ticket's level may be for Sable to count a column as loaded enough to keep a ship in.
     *
     * <p>Sable keeps a sub-level only while every column under its box is in <em>block-ticking</em>
     * range - ticket level 32 or below - and a chunk that is merely loaded (33) does not count. A
     * region ticket of radius {@code r} sits at {@code 33 - r} at its centre and climbs one level per
     * chunk outward, so it only reaches 32 or below within {@code r - 1} chunks of its centre. The
     * outer ring of every square this mod used to lay was 33: loaded, drawn, and not enough to keep a
     * ship that landed on it.
     */
    public static final int BLOCK_TICKING_LEVEL = 32;

    /** The level a region ticket of this radius gives a chunk this many chunks (Chebyshev) from its centre. */
    public static int ticketLevel(int radius, int distance) {
        return 33 - radius + Math.max(0, distance);
    }

    /** Whether that chunk is loaded enough for Sable to keep a ship over it. */
    public static boolean blockTicking(int radius, int distance) {
        return ticketLevel(radius, distance) <= BLOCK_TICKING_LEVEL;
    }

    private HoldRules() {
    }

    // ------------------------------------------------------------------ leases

    /** When a lease taken now for {@code leaseTicks} runs out. */
    public static long leaseUntil(long now, int leaseTicks) {
        return now + Math.max(1, leaseTicks);
    }

    /**
     * Whether a lease has run out.
     *
     * <p>Leases are counted in the overworld's game time, which stands still while the server is down. So
     * a hold renewed a moment before a shutdown is still good when the world comes back, and its owner has
     * the whole lease to load, tick and renew it; and a hold whose owner has gone runs out the same number
     * of ticks after the restart as it would have without one.
     */
    public static boolean lapsed(long leaseUntil, long now) {
        return now >= leaseUntil;
    }

    /**
     * Whether a live lease is worth writing again.
     *
     * <p>Only once half of it is gone. An owner asks every second; rewriting the ledger every second would
     * mark it for saving every second, for no change anybody could see.
     */
    public static boolean dueForRenewal(long leaseUntil, long now, int leaseTicks) {
        return leaseUntil - now < Math.max(1, leaseTicks) / 2;
    }

    // --------------------------------------------------------------- residency

    /** What a drive should do about its ship's residency hold. */
    public enum Residency {
        /** Keep holding it, from where it was first held. */
        HOLD,
        /** Hold it, and count any further wandering from where it is now. */
        REANCHOR,
        /** Let go of it, or do not take it. */
        RELEASE
    }

    /**
     * Decides a ship's residency.
     *
     * <p>Residency is for a ship that has been left somewhere. A hold that followed a ship wherever it
     * went would be a chunk loader with a propeller on it: an unmanned hull drifting, or flying on a
     * redstone clock, would load and generate country the whole way. So while somebody is aboard, or a
     * warp is flying it, the hold simply follows (the place it counts from moves with it); once the ship
     * is on its own it is held where it was left, and if it wanders further than {@code roamLimit} from
     * there without anybody aboard, it is let go. Sable then files it wherever it stops, like any build
     * nobody is holding - saved, not lost. It is taken again once it comes to rest.
     *
     * @param enabled    whether residency is on at all ({@code keepAirshipsLoaded})
     * @param held       whether the ship is being held for residency now
     * @param roamed     how far, in blocks across the ground, it has come from where it was held
     * @param roamLimit  how far it may come before it is let go
     * @param attended   whether anybody is aboard, or a warp is flying it
     * @param atRest     whether it is essentially stationary
     */
    public static Residency residency(boolean enabled, boolean held, double roamed, double roamLimit,
                                      boolean attended, boolean atRest) {
        if (!enabled) {
            return Residency.RELEASE;
        }
        if (attended) {
            return Residency.REANCHOR;
        }
        if (!held) {
            return atRest ? Residency.REANCHOR : Residency.RELEASE;
        }
        return roamed > Math.max(0.0D, roamLimit) ? Residency.RELEASE : Residency.HOLD;
    }

    /** Horizontal distance between two points, in blocks. */
    public static double roamed(double fromX, double fromZ, double toX, double toZ) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ---------------------------------------------------------------- the wake

    /**
     * Which chunk to keep forced so a level with held ships keeps ticking, or {@code null} for none.
     *
     * <p>Sable's own ticket keeps a ship's ground loaded but does not count as a forced chunk, and a level
     * with no players and no forced chunks stops ticking block entities after fifteen seconds - the drive
     * aboard included, while the hull it is meant to be flying carries on without it. One forced chunk is
     * enough to keep the whole level awake, so one is all this ever asks for: the chunk under a held ship,
     * which that ship's ticket already holds, so forcing it loads nothing.
     *
     * <p>The one already forced is kept for as long as it is still under a held ship, so a level full of
     * parked ships does not move its forced chunk about every time one of them does. And while any ship in
     * the level is in flight it is kept wherever it is: following a warp or a cruise moved it every time the
     * hull crossed a chunk, forcing and releasing a chunk a second for nothing. It settles under a held ship
     * once the flying is over, so it does not stay behind at the far end of a journey, loading ground nobody
     * is at.
     *
     * @param current    the chunk forced now, or {@code null}
     * @param candidates the chunk under each held ship in the level, packed
     * @param flying     whether any ship in the level is held for a flight right now
     */
    @Nullable
    public static Long wakeChunk(@Nullable Long current, Collection<Long> candidates, boolean flying) {
        if (candidates.isEmpty()) {
            return null;
        }
        if (current != null && (flying || candidates.contains(current))) {
            return current;
        }
        long lowest = Long.MAX_VALUE;
        for (long candidate : candidates) {
            lowest = Math.min(lowest, candidate);
        }
        return lowest;
    }

    // ------------------------------------------------------------------ ground

    /**
     * The chunks under a box, grown by a margin, as {@code {minChunkX, minChunkZ, maxChunkX, maxChunkZ}}.
     *
     * <p>Sable reads every column under a ship's box grown by one block, so reaching for a ship holds that
     * much and a block more.
     */
    public static int[] chunksUnder(double minX, double minZ, double maxX, double maxZ, double marginBlocks) {
        double margin = Math.max(0.0D, marginBlocks);
        return new int[]{
                (int) Math.floor(minX - margin) >> 4,
                (int) Math.floor(minZ - margin) >> 4,
                (int) Math.floor(maxX + margin) >> 4,
                (int) Math.floor(maxZ + margin) >> 4};
    }
}
