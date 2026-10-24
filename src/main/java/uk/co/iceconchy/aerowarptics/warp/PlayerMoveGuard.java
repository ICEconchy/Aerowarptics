package uk.co.iceconchy.aerowarptics.warp;

import net.minecraft.server.level.ServerPlayer;
import uk.co.iceconchy.aerowarptics.trace.Trace;

/**
 * Refuses a player movement no player could have made.
 *
 * <p>A player standing on a ship tells the server where they are in the ship's own plot coordinates, and
 * Sable turns that back into the world using the ship's pose. Around a warp's jump the client can make that
 * sum with a stale pose: its copy of the ship has been carried across, but a pose snapshot sent just before
 * the jump - Sable sends them on a channel of their own, with no ordering against this mod's notice of the
 * jump - lands a moment after it and puts the ship back at the near end for a tick. The position that tick
 * is off by the length of the warp, in plot space, and for a long enough warp it falls outside Sable's plot
 * grid - where Sable neither converts it nor sets it aside, and hands the server twenty-million-block plot
 * coordinates as a world position.
 *
 * <p>Vanilla would refuse that as "moved too quickly", except that it never refuses the owner of a
 * single-player world, and Sable widens that to anybody standing on a ship, whose ship may be moving fast.
 * So the server believed it: it swept the player's collision box across twenty million blocks, which held
 * the server thread for most of a minute - the ship "held in the void" mid-warp, with no world round it,
 * no menus, and the rest of the warp playing out a minute late.
 *
 * <p>This puts the refusal back for moves beyond {@link #LIMIT_BLOCKS} in a single tick, which nothing
 * legitimate comes near: the fastest thing this mod flies moves forty-eight blocks a tick. Refused, vanilla
 * does what it does for any player caught moving too quickly - keeps the server's position and teleports the
 * client there - which after a jump is exactly where they belong, on the far side, on the deck.
 */
public final class PlayerMoveGuard {

    /** The furthest a player may be believed to have moved since the server's last tick, in blocks. */
    public static final double LIMIT_BLOCKS = 1024.0D;

    private PlayerMoveGuard() {
    }

    /**
     * Whether a move is beyond anything a player could have done since the server last placed them.
     *
     * @param dx how far the reported position is from where the server had the player at the start of the
     *           tick, as vanilla measures it for its own "moved too quickly" check
     */
    public static boolean impossible(double dx, double dy, double dz) {
        double squared = dx * dx + dy * dy + dz * dz;
        // Not a number is impossible too; vanilla disconnects for NaN before this is asked, but a sum of
        // enormous values can overflow to infinity and must not slip through as "not greater than".
        return !(squared <= LIMIT_BLOCKS * LIMIT_BLOCKS);
    }

    /**
     * Asked by the server's move handler whether the "moved too quickly" exemption still applies to this
     * move. It does not for an impossible one.
     *
     * @param exempt what vanilla, and Sable after it, decided
     * @return whether to keep exempting the player
     */
    public static boolean keepExemption(boolean exempt, ServerPlayer player, double dx, double dy, double dz) {
        if (!exempt || !impossible(dx, dy, dz)) {
            return exempt;
        }
        Trace.always(Trace.CREW, "refused a move by {} of {} blocks in one tick, to ({}, {}, {}) - a position no"
                        + " player could reach, most likely a deck position worked out against a stale pose of a"
                        + " ship that has just jumped; kept them at ({}, {}, {})", Trace.who(player),
                Trace.num(Math.sqrt(dx * dx + dy * dy + dz * dz)),
                Trace.num(player.getX() + dx), Trace.num(player.getY() + dy), Trace.num(player.getZ() + dz),
                Trace.num(player.getX()), Trace.num(player.getY()), Trace.num(player.getZ()));
        return false;
    }
}
