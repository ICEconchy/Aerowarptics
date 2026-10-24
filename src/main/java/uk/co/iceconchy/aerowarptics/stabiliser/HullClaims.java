package uk.co.iceconchy.aerowarptics.stabiliser;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Which one stabiliser is allowed to cushion each hull this tick.
 *
 * <p>Stabilisers must not stack. Each one compares the hull against the velocity <em>it</em> left it
 * at, so two aboard the same ship would each absorb three quarters of what the other had already let
 * through - two make the hull sixteen times heavier, three sixty-four - and the obvious thing a player
 * tries, bolting on another to make it stronger, would quietly turn the ship into a brick. So the first
 * stabiliser to tick on a hull claims it for that tick, and any other one aboard stands by and says so.
 *
 * <p>Keyed by the hull's UUID and the owner's position rather than holding a block entity, so a
 * stabiliser that is broken mid-flight leaves nothing behind in here but a stale tick number, which
 * the next claim simply overwrites. Tick order within a level is stable, so in practice the same
 * stabiliser wins every tick and its comparison stays continuous; if the winner ever changes, the new
 * one has not been cushioning and starts from a fresh tick of its own.
 *
 * <p>Plain Java so the rule can be tested. Only the server thread touches the shared instance.
 */
public final class HullClaims {

    private record Claim(long tick, long owner) {
    }

    private final Map<UUID, Claim> claims = new HashMap<>();

    /**
     * Asks to cushion a hull this tick.
     *
     * @param hull  the airship's UUID
     * @param tick  the level's game time
     * @param owner the claimant's {@code BlockPos.asLong()} - unique within the hull's level
     * @return {@code true} when the claimant may act: nobody has claimed the hull this tick, or it
     *         already has
     */
    public boolean claim(UUID hull, long tick, long owner) {
        Claim existing = claims.get(hull);
        if (existing != null && existing.tick() == tick && existing.owner() != owner) {
            return false;
        }
        claims.put(hull, new Claim(tick, owner));
        return true;
    }

    /** Gives a hull up, if this owner held it. Keeps the map to the hulls actually being cushioned. */
    public void release(UUID hull, long owner) {
        Claim existing = claims.get(hull);
        if (existing != null && existing.owner() == owner) {
            claims.remove(hull);
        }
    }

    int size() {
        return claims.size();
    }
}
