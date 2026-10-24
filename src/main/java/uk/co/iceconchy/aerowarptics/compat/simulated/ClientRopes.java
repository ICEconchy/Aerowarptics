package uk.co.iceconchy.aerowarptics.compat.simulated;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientRopePoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientRopeStrand;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector3d;
import uk.co.iceconchy.aerowarptics.airship.HullJump;

/**
 * This client's copy of the ropes a hull took through a warp - see {@link CarriedRopes}.
 *
 * <p>A client draws a rope by interpolating between the points the server last sent. Left alone, the first
 * points from the far side are interpolated towards from the near side's, and the rope is drawn for a tick
 * stretched the whole length of the jump. So the points it holds are put through the jump as soon as the
 * hull is known to have made it, the same way {@code FoldCrossings} moves the hull's own buffered poses.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientRopes {

    private ClientRopes() {
    }

    /**
     * Moves every rope that starts on {@code ship} through {@code jump}.
     *
     * <p>Only the rope's start is asked about, so a rope between two parts of the vessel, both of which are
     * told of the jump, is moved once. A point already on the far side is left where it is.
     */
    public static void carry(ClientLevel level, SubLevel ship, HullJump jump) {
        try {
            ClientLevelRopeManager ropes = ClientLevelRopeManager.getOrCreate(level);
            if (ropes == null) {
                return;
            }
            for (ClientRopeStrand strand : ropes.getAllStrands()) {
                if (strand.startAttachment == null || Sable.HELPER.getContaining(level, strand.startAttachment) != ship) {
                    continue;
                }
                for (ClientRopePoint point : strand.getPoints()) {
                    nearSide(point.position(), jump);
                    nearSide(point.previousPosition(), jump);
                    for (ClientRopePoint.Snapshot snapshot : point.snapshots()) {
                        nearSide(snapshot.position(), jump);
                    }
                }
            }
        } catch (LinkageError ignored) {
            // A Simulated that no longer matches draws one streak, as it did before ropes were carried.
        }
    }

    private static void nearSide(Vector3d point, HullJump jump) {
        if (point.distanceSquared(jump.from()) < point.distanceSquared(jump.to())) {
            jump.apply(point, point);
        }
    }
}
