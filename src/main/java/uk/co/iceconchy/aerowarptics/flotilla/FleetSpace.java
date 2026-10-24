package uk.co.iceconchy.aerowarptics.flotilla;

import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import uk.co.iceconchy.aerowarptics.airship.Airship;

/**
 * The bridge from live airships to {@link Formation}'s pure frame: where a ship is in its flagship's
 * formation, where a slot is in the world, and how much room a hull takes.
 *
 * <p>Everything goes through the flagship's own ship space, not the world. Ship space turns and moves
 * with the hull, so a slot measured in it stays where the pilots think it is when the flagship comes
 * about, pitches in a gust, or crosses a fold - which a slot kept in world axes would not.
 */
public final class FleetSpace {

    private FleetSpace() {
    }

    /** The flagship's hull centre, in its own ship space. */
    public static Vec3 centreInShip(Airship flagship) {
        Vector3d centre = flagship.centre(new Vector3d());
        return flagship.toShip(new Vec3(centre.x, centre.y, centre.z));
    }

    /**
     * Where a world point sits in a flagship's formation.
     *
     * @param bow the flagship's bow in its own ship space, from its drive
     */
    public static FormationSlot slotOf(Airship flagship, Direction bow, Vector3dc world) {
        Vec3 centre = centreInShip(flagship);
        Vec3 point = flagship.toShip(new Vec3(world.x(), world.y(), world.z()));
        return Formation.toSlot(point.x - centre.x, point.y - centre.y, point.z - centre.z,
                bow.getStepX(), bow.getStepZ());
    }

    /** Where an escort's hull centre sits in a flagship's formation. */
    public static FormationSlot slotOf(Airship flagship, Direction bow, Airship escort) {
        return slotOf(flagship, bow, escort.centre(new Vector3d()));
    }

    /** Where a slot is in the world, with the flagship where it is now. */
    public static Vec3 worldOf(Airship flagship, Direction bow, FormationSlot slot) {
        Vec3 centre = centreInShip(flagship);
        double[] step = Formation.toShip(slot, bow.getStepX(), bow.getStepZ());
        return flagship.toWorld(centre.add(step[0], step[1], step[2]));
    }

    /**
     * A slot as a step in the world from the flagship's centre - which is how far apart two ships stay
     * across a fold, since a fold moves a hull without turning it.
     */
    public static Vector3d worldStep(Airship flagship, Direction bow, FormationSlot slot) {
        double[] step = Formation.toShip(slot, bow.getStepX(), bow.getStepZ());
        return flagship.toWorldDirection(new Vector3d(step[0], step[1], step[2]));
    }

    /**
     * How much room a hull takes in a formation, from its plot box.
     *
     * <p>The plot box and not the world box: a world box grows and shrinks as a hull turns, so a footprint
     * taken from it would let two slots clash at one heading and not at another. The plot box is the hull
     * in its own axes, the same at every heading.
     */
    public static Formation.Footprint footprint(Airship ship) {
        BoundingBox3ic box = ship.shipBounds();
        return Formation.Footprint.ofBox(box.maxX() - box.minX() + 1, box.maxY() - box.minY() + 1,
                box.maxZ() - box.minZ() + 1);
    }
}
