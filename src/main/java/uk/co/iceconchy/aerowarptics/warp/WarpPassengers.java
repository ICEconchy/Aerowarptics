package uk.co.iceconchy.aerowarptics.warp;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import uk.co.iceconchy.aerowarptics.airship.Airship;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps the crew on the deck while the hull is being flown through a rift.
 *
 * <p>A warp commands the hull's velocity outright, and inside the corridor that velocity is enormous -
 * nine blocks a tick, a hundred and eighty a second. Sable carries whatever is standing on a sub-level
 * along with it, and normally that is the end of the matter. But an entity can come off: a player who
 * jumps at the wrong moment, or one the collision solver loses for a tick while the deck is moving
 * faster than the deck is thick. When that happens the ship's motion has already been imparted to them,
 * and a person carrying a hundred and eighty blocks a second is not dropped, they are fired.
 *
 * <p>Two things are done about it, and which one depends on where the ship is:
 *
 * <ul>
 *   <li><b>Always</b>, the borrowed momentum is taken back. Whatever else happens to someone who comes
 *       off a warping ship, being thrown half a kilometre is not it.</li>
 *   <li><b>Inside the fold</b> - once the bow is through the aperture and until the stern is back out -
 *       they are also put back where they were standing. Not as a courtesy: there is no ground inside
 *       an aperture, no air, and nowhere to walk to, so a passenger left behind there is a passenger
 *       deleted. On the approach and on the way out the world is real and falling off a ship is an
 *       ordinary thing to do, so they are allowed to.</li>
 * </ul>
 *
 * <p>Seats are remembered in ship space, which is what makes putting somebody back meaningful at all:
 * the deck they fell off has since moved, and on the far side of the corridor it has moved to another
 * part of the world entirely. Their seat has not.
 */
public final class WarpPassengers {

    /** Where each remembered passenger was last standing, in the airship's own frame. */
    private final Map<UUID, Vec3> seats = new HashMap<>();

    /**
     * Whether somebody who has come off the hull at this point can be put back on it.
     *
     * <p>True exactly while the hull is inside an aperture. Outside one, coming off a ship is a thing
     * players are allowed to do and being teleported back would be a trap; inside one, there is
     * nothing to come off onto.
     */
    public static boolean insideTheFold(WarpFlight.Stage stage) {
        return stage == WarpFlight.Stage.TRANSIT
                || stage == WarpFlight.Stage.CORRIDOR
                || stage == WarpFlight.Stage.BREACH;
    }

    /**
     * Notes who is aboard, and deals with anybody who is not any more.
     *
     * <p>Called once per tick of the flight, before the hull is moved, so the seats recorded are the
     * ones the passengers were actually in when the tick began.
     *
     * @return how many passengers had to be put back this tick
     */
    public int hold(Airship airship, WarpFlight.Stage stage) {
        Set<UUID> present = new HashSet<>();
        for (Entity entity : airship.passengers()) {
            present.add(entity.getUUID());
            seats.put(entity.getUUID(), airship.toShip(entity.position()));
        }

        int recovered = 0;
        List<UUID> lost = new ArrayList<>();
        for (Map.Entry<UUID, Vec3> seat : seats.entrySet()) {
            if (present.contains(seat.getKey())) {
                continue;
            }
            Entity stray = airship.level().getEntity(seat.getKey());
            if (stray == null || stray.isRemoved()) {
                lost.add(seat.getKey());
                continue;
            }
            calm(stray);
            if (!insideTheFold(stage)) {
                // They are off the ship somewhere real, and that is allowed. Stop remembering them,
                // or the next stage would haul them back aboard from wherever they landed.
                lost.add(seat.getKey());
                continue;
            }
            reseat(airship, stray, seat.getValue());
            recovered++;
        }
        lost.forEach(seats::remove);
        return recovered;
    }

    /**
     * Ends the manifest, however the journey ended.
     *
     * <p>Anybody still remembered but not aboard is put back one last time, unconditionally. By this
     * point the hull is wherever it is finally going to be - the destination on a warp that worked,
     * its mooring on one that was called off - so this is the moment a passenger who came off inside
     * the fold gets to arrive with the ship rather than at the coordinates of a hole in space.
     *
     * <p>Everyone who is aboard has their fall reset. The hull has been under server control for the
     * whole journey, so no part of that fall was theirs, and arriving should not hurt.
     */
    public void settle(Airship airship) {
        Set<UUID> aboard = new HashSet<>();
        for (Entity entity : airship.passengers()) {
            aboard.add(entity.getUUID());
            entity.fallDistance = 0.0F;
        }
        for (Map.Entry<UUID, Vec3> seat : seats.entrySet()) {
            if (aboard.contains(seat.getKey())) {
                continue;
            }
            Entity stray = airship.level().getEntity(seat.getKey());
            if (stray != null && !stray.isRemoved()) {
                reseat(airship, stray, seat.getValue());
            }
        }
        seats.clear();
    }

    /**
     * Takes back momentum the passenger never asked for.
     *
     * <p>A player's own client is the authority on where they are, so zeroing the field on the server
     * is not enough on its own - the motion has to be sent, which is the same packet the game uses to
     * knock a player back. Everything else is told the ordinary way.
     */
    private static void calm(Entity entity) {
        entity.setDeltaMovement(Vec3.ZERO);
        entity.fallDistance = 0.0F;
        entity.hurtMarked = true;
        if (entity instanceof ServerPlayer player) {
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
    }

    /** Puts a passenger back in the seat they were in, wherever that seat has since got to. */
    private static void reseat(Airship airship, Entity entity, Vec3 seat) {
        Vec3 world = airship.toWorld(seat);
        if (entity instanceof ServerPlayer player) {
            // Not teleportTo: a player has to be told, and told in a way that resets what their own
            // client thinks it is doing, or it will carry on flying and be corrected back next tick.
            player.connection.teleport(world.x, world.y, world.z, player.getYRot(), player.getXRot());
        } else {
            entity.teleportTo(world.x, world.y, world.z);
        }
        calm(entity);
    }
}
