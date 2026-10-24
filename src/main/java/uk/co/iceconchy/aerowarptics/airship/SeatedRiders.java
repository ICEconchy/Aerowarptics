package uk.co.iceconchy.aerowarptics.airship;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.trace.Trace;

/**
 * Puts a mob that was sitting on a ship's seat back on it as the world loads.
 *
 * <h2>What goes wrong</h2>
 * A Create seat aboard a ship is an entity in the ship's plot, but whatever sits on it is carried in world space, and is
 * saved there. When the seat is loaded again, Sable's {@code EntityTypeMixin} hooks the {@code startRiding} call inside
 * {@code EntityType.loadEntityRecursive}: if the vehicle is in a loaded ship, it takes the rider's saved position for a
 * plot position and carries it out to the world ({@code EntityRidingSubLevelVehicleHelper.kickRidingEntity}). The rider
 * was in the world already, so it is carried out a second time - twenty million blocks off and below the floor.
 *
 * <p>From there it goes one of two ways, both reported by players. Most often the rider is filed in a section nobody
 * loads: still on the seat as far as the seat knows, keeping it occupied, but never ticked and never seen - the mob has
 * "gone", and every later load moves it further. Otherwise its seat pulls it back after a tick, by which time it has
 * taken the four points of void damage the game deals below the world, so a pig on a seat dies on its third reload.
 *
 * <p>It only happens when the ship is loaded before its seats are, which is why a ship a drive keeps loaded
 * ({@code keepAirshipsLoaded}) shows it on every restart: Sable's force-load ticket brings the ship back as the level is
 * built. A ship that unloads and is walked back to does it too. Nothing in this mod moves the rider; the drive's hold
 * only makes the order of loading the one that trips Sable up.
 *
 * <h2>The repair</h2>
 * The game asks before it files a loaded entity, so a rider that has been carried impossibly far from its seat is put
 * back on it here - before it is ever in the wrong section, and before anything can hurt it. Sable's own rider
 * positioning settles it into the seat on its first tick. A rider already where it belongs is left alone, so this does
 * nothing on a Sable that stops making the mistake, and can go once that version is the oldest supported.
 *
 * <p>Players are not touched: they are loaded from their own data, never through {@code loadEntityRecursive}, and the
 * crew systems look after them.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID)
public final class SeatedRiders {

    /**
     * How far from its seat a rider has to be before it counts as misplaced.
     *
     * <p>A rider is never more than a block or two from the vehicle it sits on, and Sable's mistake puts it millions of
     * blocks away, so the line can sit anywhere between; this is far enough that no seat, saddle or offset reaches it.
     */
    private static final double MISPLACED = 16.0D;

    private SeatedRiders() {
    }

    /** Whether a rider this far from its seat has been put somewhere it never went. Shared with the test. */
    public static boolean misplaced(double blocks) {
        return blocks > MISPLACED;
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Entity rider = event.getEntity();
        Entity vehicle = rider.getVehicle();
        if (vehicle == null || rider instanceof Player) {
            return;
        }
        Airship ship = Airship.containing(level, vehicle.blockPosition());
        if (ship == null) {
            return;
        }
        Vec3 seat = ship.toWorld(vehicle.position());
        if (!misplaced(rider.position().distanceTo(seat))) {
            return;
        }
        Trace.log(Trace.CREW, "{} loaded on {} in ship {} at {}, {} blocks from its seat: put back at {}",
                rider.getName().getString(), vehicle.getType().getDescriptionId(), ship.uuid(),
                Trace.vec(rider.position()), Trace.num(rider.position().distanceTo(seat)), Trace.vec(seat));
        rider.setPos(seat);
    }
}
