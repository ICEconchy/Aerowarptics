package uk.co.iceconchy.aerowarptics.compat.simulated;

import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import uk.co.iceconchy.aerowarptics.airship.HullJump;
import uk.co.iceconchy.aerowarptics.trace.Trace;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The ropes a warp takes with it: every strand whose ends are all on the vessel.
 *
 * <p>A rope's middle is not in any plot. Simulated keeps it as a string of points in world space, simulated
 * by Sable as a physics object of its own, so a teleport that moves every plot by one rigid jump leaves the
 * strand hanging where the ship was, its two ends a warp away. Nothing in Sable moves a rope's points while
 * it is in the physics world. Out of it, though, the points are only vectors, and putting a rope back in
 * builds it afresh from wherever they are - which is what Simulated itself does when a rope's chunk comes
 * back from disk. So the strand is lifted out before the vessel moves, its points are put through the same
 * jump as the hull, and it is put back: its joint to each end is made again on its next physics tick.
 *
 * <p>A pilot reported a ship refused as "tied down" by a rope down to a loose weight hanging under it
 * (2026-10-16). The weight is a part of the vessel - see {@link SimulatedTies#ropedTo} - and this is how the
 * rope between them goes too.
 *
 * <p>Never throws. Simulated is a separate mod with its own releases; one that no longer matches leaves
 * nothing lifted, and the warp goes as it would have before ropes were carried.
 */
public final class CarriedRopes {

    private static final CarriedRopes NONE = new CarriedRopes(null, null, List.of());

    @Nullable
    private final ServerLevel level;
    @Nullable
    private final SubLevelPhysicsSystem physics;
    private final List<Lifted> lifted;

    private record Lifted(ServerRopeStrand strand, boolean wasActive) {
    }

    private CarriedRopes(@Nullable ServerLevel level, @Nullable SubLevelPhysicsSystem physics, List<Lifted> lifted) {
        this.level = level;
        this.physics = physics;
        this.lifted = lifted;
    }

    /**
     * Takes every strand with all of its ends on {@code vessel} out of the physics world, with its points
     * read back where the physics last had them.
     *
     * @param vessel the ids of the hull and every part the warp moves
     */
    public static CarriedRopes lift(ServerLevel level, Set<UUID> vessel) {
        try {
            ServerLevelRopeManager ropes = ServerLevelRopeManager.getOrCreate(level);
            SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(level);
            if (ropes == null || physics == null) {
                return NONE;
            }
            List<Lifted> lifted = new ArrayList<>();
            for (ServerRopeStrand strand : ropes.getAllStrands()) {
                if (!aboard(strand, vessel)) {
                    continue;
                }
                boolean active = strand.isActive();
                if (active) {
                    strand.updatePose();
                }
                physics.removeObject(strand);
                lifted.add(new Lifted(strand, active));
            }
            return lifted.isEmpty() ? NONE : new CarriedRopes(level, physics, lifted);
        } catch (LinkageError e) {
            Trace.warn(Trace.LAUNCH, "ropes cannot be carried this warp - Simulated no longer matches: {}", e.toString());
            return NONE;
        }
    }

    /** Whether any strand has an end on {@code vessel} - which a crossing into another dimension cannot take. */
    public static boolean anyTouching(ServerLevel level, Set<UUID> vessel) {
        try {
            ServerLevelRopeManager ropes = ServerLevelRopeManager.getOrCreate(level);
            if (ropes == null) {
                return false;
            }
            for (ServerRopeStrand strand : ropes.getAllStrands()) {
                for (RopeAttachment attachment : strand.getAttachments()) {
                    if (attachment.subLevelID() != null && vessel.contains(attachment.subLevelID())) {
                        return true;
                    }
                }
            }
            return false;
        } catch (LinkageError e) {
            return false;
        }
    }

    /** How many strands were lifted, for the trace. */
    public int count() {
        return lifted.size();
    }

    /**
     * Moves every lifted strand through {@code jump} and puts back the ones that were in the physics world.
     *
     * <p>One that cannot go back yet - its far side not loaded enough for physics - is left out, and the
     * block that owns it puts it back once it can, as it does with any rope whose ground was unloaded.
     */
    public void land(HullJump jump) {
        if (lifted.isEmpty() || level == null || physics == null) {
            return;
        }
        try {
            int back = 0;
            for (Lifted rope : lifted) {
                ServerRopeStrand strand = rope.strand();
                // The list is read-only; the points in it are the strand's own.
                for (Vector3d point : strand.getPoints()) {
                    jump.apply(point, point);
                }
                if (rope.wasActive() && physics.getTicketManager().wouldBeLoaded(level, strand)) {
                    physics.addObject(strand);
                    back++;
                }
            }
            Trace.log(Trace.ARRIVAL, "carried {} rope(s) {} blocks; {} back in the physics world now, the rest when"
                    + " their ground loads", lifted.size(), Trace.num(jump.distance()), back);
        } catch (LinkageError e) {
            Trace.warn(Trace.ARRIVAL, "ropes could not be put back after a warp - Simulated no longer matches: {}",
                    e.toString());
        }
    }

    private static boolean aboard(ServerRopeStrand strand, Set<UUID> vessel) {
        boolean any = false;
        for (RopeAttachment attachment : strand.getAttachments()) {
            UUID on = attachment.subLevelID();
            if (on == null || !vessel.contains(on)) {
                return false;
            }
            any = true;
        }
        return any;
    }
}
