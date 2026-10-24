package uk.co.iceconchy.aerowarptics.airship;

import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import uk.co.iceconchy.aerowarptics.network.ClientboundFoldCrossedPacket;

/**
 * The part of a creature's motion that Sable lent it from the hull it was standing on.
 *
 * <p>Sable does not carry a passenger by giving it the ship's velocity. It keeps a separate vector on
 * every living entity - the distance the deck carried its feet last tick - and while the entity is off
 * the deck it keeps adding that on, bleeding away only one percent a tick in the air. That is what
 * lets someone jump on a moving ship and come down on the same plank.
 *
 * <p>It is also what threw crews off warping ships. The vector is not the entity's delta movement, so
 * zeroing that - which is what "take the ship's momentum back" used to do, and what the game's own
 * motion packet does - leaves it completely alone. A player who was in the air when the hull stopped
 * in the corridor carried on at passage speed; one whose client drew the hull half-way across a
 * teleport was carried half the teleport in a tick and kept going at that. Anything here that means
 * "stop" has to say it to this vector as well.
 *
 * <p>The vector belongs to Sable's collision mixin rather than to its API, which is why every read and
 * write goes through this class and nowhere else.
 */
public final class BorrowedMotion {

    private BorrowedMotion() {
    }

    /** The live vector, in blocks per tick, or {@code null} for an entity Sable does not lend motion to. */
    @Nullable
    public static Vector3d of(Entity entity) {
        return entity instanceof LivingEntityMovementExtension moving ? moving.sable$getInheritedVelocity() : null;
    }

    /**
     * Replaces what the entity has borrowed.
     *
     * <p>Given the hull's own velocity rather than zero wherever the hull is still moving: someone put
     * back on a deck that is flying has to be moving with it until their feet find it, or the deck
     * slides out from under them before they land.
     */
    public static void lend(Entity entity, Vector3dc blocksPerTick) {
        Vector3d borrowed = of(entity);
        if (borrowed != null) {
            borrowed.set(sane(blocksPerTick));
        }
    }

    /** Takes back everything the hull lent: vanilla motion, the borrowed vector and the fall. */
    public static void settle(Entity entity, Vector3dc hullBlocksPerTick) {
        entity.setDeltaMovement(Vec3.ZERO);
        entity.fallDistance = 0.0F;
        lend(entity, hullBlocksPerTick);
    }

    /**
     * Whether a borrowed speed can only have come from a teleport misread as motion.
     *
     * <p>The same line the fold crossing draws between flight and a jump, for the same reason: nothing
     * a hull does under its own power carries anyone that far in one tick.
     */
    public static boolean impossible(double blocksPerTick) {
        return !Double.isFinite(blocksPerTick) || blocksPerTick >= ClientboundFoldCrossedPacket.JUMP_BLOCKS;
    }

    /** A lent velocity that cannot itself be the fling it is meant to prevent. */
    public static Vector3d sane(Vector3dc blocksPerTick) {
        return impossible(blocksPerTick.length()) ? new Vector3d() : new Vector3d(blocksPerTick);
    }
}
