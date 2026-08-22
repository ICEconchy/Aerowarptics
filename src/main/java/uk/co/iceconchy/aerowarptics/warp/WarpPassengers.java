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
 *   <li><b>Once the bow is through the aperture</b>, and until the hull has settled, they are also put
 *       back where they were standing. Not as a courtesy: there is no ground inside an aperture, no
 *       air, and nowhere to walk to, and on the run out the hull is still being flown at speed towards
 *       somewhere the passenger never chose. Only on the approach, with the world still real and the
 *       ship still where they boarded it, is stepping off a thing they are allowed to do.</li>
 * </ul>
 *
 * <p>Seats are remembered in ship space, which is what makes putting somebody back meaningful at all:
 * the deck they fell off has since moved, and on the far side of the corridor it has moved to another
 * part of the world entirely. Their seat has not.
 *
 * <h2>Coming off is not the only way to be lost</h2>
 * The obvious failure is a passenger who is no longer on the sub-level, and that is the one this
 * class was first written for. The crossing produces a subtler one. A client briefly loses the hull
 * around the teleport, and a client with nothing under it falls - while the server, seeing them still
 * inside the hull's bounds on the way down, goes on answering "yes, aboard" until they are through the
 * keel. So a passenger who is <em>still aboard</em> but sinking through their own deck is recovered
 * too, on the evidence of the sinking rather than on Sable's answer.
 *
 * <p>And the manifest counts riders. A player sitting in a seat is a passenger of that seat, which
 * {@code Airship.passengers()} deliberately skips - correct for moving the ship, wrong for this, and
 * the reason sitting down used to make no difference at all.
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
     * Whether a passenger who has come adrift at this point should be put back.
     *
     * <p>Wider than {@link #insideTheFold}, and deliberately so. Coming out of the exit aperture is
     * not inside a hole in space any more, but the hull is still being flown by the drive at speed
     * towards a resting place the passenger never chose - so somebody who comes off during the run
     * out is no more responsible for it than somebody who came off in the corridor, and is just as
     * far from anywhere they meant to be.
     *
     * <p>Leaving the run out uncovered was a real hole: strays were <em>forgotten</em> during it
     * rather than recovered, so {@link #settle} could not put them back either, and the last stage of
     * every journey quietly wrote off anyone who had not made it aboard yet.
     */
    public static boolean recoverable(WarpFlight.Stage stage) {
        return insideTheFold(stage) || stage == WarpFlight.Stage.EMERGE;
    }

    /**
     * How far below their seat a passenger may sink before they are put back on it.
     *
     * <p>This is what catches the failure the fold crossing actually produces. A client briefly loses
     * the sub-level around the teleport - Sable logs {@code "Received a sub-level movement packet for
     * a non-existent sub-level"} while it catches up - and a client with no ship under it starts
     * falling. The server still believes that player is aboard, because they are still inside the
     * hull's bounds on the way down, so asking Sable "are they on the ship" answers yes right up
     * until they are through the keel and it is too late.
     *
     * <p>Two blocks is below anything a floor can be stepped off inside a hull and well above the
     * noise of standing on a deck that is moving.
     */
    private static final double SLIP = 2.0D;

    /**
     * Notes who is aboard, and deals with anybody who is not any more.
     *
     * <p>Called once per tick of the flight, before the hull is moved, so the seats recorded are the
     * ones the passengers were actually in when the tick began.
     *
     * @return how many passengers had to be put back this tick
     */
    public int hold(Airship airship, WarpFlight.Stage stage) {
        boolean recovering = recoverable(stage);
        int recovered = 0;
        Set<UUID> present = new HashSet<>();

        for (Entity entity : manifest(airship)) {
            UUID id = entity.getUUID();
            if (!present.add(id)) {
                continue;
            }
            Vec3 here = airship.toShip(entity.position());
            Vec3 seat = seats.get(id);

            // Only a supported passenger's position is worth remembering. Somebody in mid-air is
            // either jumping or falling, and in both cases the seat worth putting them back in is the
            // last one they were actually standing in.
            if (seat == null || supported(entity)) {
                seats.put(id, here);
                continue;
            }
            if (recovering && seat.y - here.y > SLIP) {
                // Sinking through their own deck: the client has lost the hull and is falling on its
                // own account, while the server still counts them aboard.
                reseat(airship, entity, seat);
                recovered++;
            }
        }

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
            if (!recovering) {
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
     * Everyone the hull is carrying, riders included.
     *
     * <p>{@code Airship.passengers()} skips anything that is riding something else, because for
     * <em>moving</em> the hull that is right - a rider is carried by its vehicle and must not be
     * shoved about independently of it. For <em>recovering</em> one it is exactly wrong: a player in a
     * seat who gets dismounted mid-warp was never on the manifest, so nothing noticed they had gone
     * and nothing put them back. That is why sitting down did not help.
     *
     * <p>{@code crew()} already resolves a player through whatever they are riding, so the union of
     * the two is every entity aboard plus every player aboard something aboard.
     */
    private static List<Entity> manifest(Airship airship) {
        List<Entity> aboard = new ArrayList<>(airship.passengers());
        aboard.addAll(airship.crew());
        return aboard;
    }

    /**
     * Whether something is being held up rather than falling.
     *
     * <p>A rider counts: whatever it is sitting in is responsible for it, and its own
     * {@code onGround} is meaningless.
     */
    private static boolean supported(Entity entity) {
        return entity.onGround() || entity.isPassenger();
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
        for (Entity entity : manifest(airship)) {
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
