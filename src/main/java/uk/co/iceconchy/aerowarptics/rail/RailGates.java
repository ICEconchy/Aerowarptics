package uk.co.iceconchy.aerowarptics.rail;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import net.createmod.catnip.data.Couple;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;

/**
 * The few places Create's train code calls into this mod - through the mixins in {@code mixin/rail}, which may
 * hold nothing but the call.
 *
 * <p>Three questions, all asked on the server thread in the middle of Create moving a train:
 * <ul>
 *   <li><b>May this carriage go through this portal?</b> Create asks a listener whenever a carriage reaches an edge
 *   that changes dimension. {@link #portalListener} answers first for the edges that lead into a Rift Corridor, and
 *   leaves every other portal - a Nether portal, anybody else's - to Create.</li>
 *   <li><b>Was that why the train stopped?</b> A blocked carriage makes Create stop the train dead, cancel its
 *   navigation and report the end of the track. A train waiting at a gate is not at the end of anything: it keeps
 *   its route and its schedule, as it would at a red signal, and its owner is told why it is waiting instead -
 *   {@link #waitingAtGate}.</li>
 * </ul>
 *
 * <p>Every answer fails open. Should anything here throw, the train is treated as Create would have treated it, and
 * the failure is logged once: a mod that stopped every train on the server because of a bug in a gate would be far
 * worse than one gate letting a train through shut.
 */
public final class RailGates {

    private static boolean complained;

    private RailGates() {
    }

    /** Create's portal listener for one carriage's travel, with the gates asked first. */
    public static TravellingPoint.IPortalListener portalListener(Carriage carriage,
                                                                 TravellingPoint.IPortalListener original) {
        return couple -> holds(carriage, couple) || original.test(couple);
    }

    private static boolean holds(Carriage carriage, Couple<TrackNodeLocation> edge) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread() || !AWConfig.SERVER_SPEC.isLoaded() || !AWConfig.RAIL_GATES.get()
                || carriage.train == null || edge == null) {
            return false;
        }
        try {
            return RailTransit.holds(server, carriage, edge.getFirst(), edge.getSecond());
        } catch (RuntimeException failure) {
            if (!complained) {
                complained = true;
                AeroWarptics.LOGGER.error("A rail gate failed to answer for a train at a portal; letting it through as"
                        + " Create would. Further failures are not logged.", failure);
            }
            return false;
        }
    }

    /**
     * Whether a train Create has just found blocked was blocked by a gate this tick, and so should keep its
     * navigation and not be reported as having run out of track.
     */
    public static boolean waitingAtGate(Train train) {
        try {
            return train != null && RailTransit.heldThisTick(train);
        } catch (RuntimeException failure) {
            return false;
        }
    }
}
