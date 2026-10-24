package uk.co.iceconchy.aerowarptics.rail;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AWConfig;

/**
 * Asks a gate to dial while a scheduled train is still on its way to it.
 *
 * <p>A gate takes two seconds to open. A train that only asks when it reaches the pane is held there for those two
 * seconds, and Create stops a blocked train dead - from full speed, in a tick. So a scheduled train heading for a
 * lane's track within {@code preDialDistance} asks the gate at that end to dial, and at top speed covers that
 * distance in about the time the gate needs.
 *
 * <h2>Why by where the train is heading, not by its path</h2>
 * This first read the path Create's navigation keeps, and never fired: on a plain line the path holds nothing to
 * read - it records the turns a train has to make, and a line into a gate has none (seen in game, 2026-10-18). So it
 * goes by geometry: the train's leading end, the way it is moving, and whether a lane's track lies ahead of it, in
 * reach, with the train heading into the gate rather than away. A train on a parallel line heading the same way past
 * a gate can open it for nothing, which costs a dial; a train driven by hand never asks, and waits at the pane.
 */
final class RailPreDial {

    /** How square to the lane a train must be heading, as the cosine of the angle, to count as heading into it. */
    private static final double HEADING_INTO = 0.5D;

    private RailPreDial() {
    }

    static void look(MinecraftServer server) {
        int reach = AWConfig.RAIL_PRE_DIAL_DISTANCE.get();
        if (reach <= 0) {
            return;
        }
        RailLaneRegistry lanes = RailLaneRegistry.get(server);
        if (lanes.all().isEmpty()) {
            return;
        }
        double reachSqr = (double) reach * reach;
        for (Train train : Create.RAILWAYS.trains.values()) {
            if (train.navigation == null || train.navigation.destination == null || train.derailed
                    || train.carriages.isEmpty() || train.graph == null) {
                continue;
            }
            Heading heading = heading(train);
            if (heading == null) {
                continue;
            }
            for (RailLane lane : lanes.all()) {
                if (!lane.ready()) {
                    continue;
                }
                if (ahead(heading, lane.dimension(), lane.track(), lane.heading(), reachSqr)) {
                    RailTransit.askAhead(server, lane, true, train.id);
                } else if (lane.farDimension() != null && lane.farStub() != null && lane.farHeading() != null
                        && ahead(heading, lane.farDimension(), lane.farStub(), lane.farHeading().getOpposite(),
                        reachSqr)) {
                    RailTransit.askAhead(server, lane, false, train.id);
                }
            }
        }
    }

    /**
     * Where a train's leading end is and which way it is going.
     *
     * @param dimension the dimension that end is in
     * @param front     the leading end
     * @param going     a unit vector the way it is going
     */
    private record Heading(ResourceKey<Level> dimension, Vec3 front, Vec3 going) {
    }

    @Nullable
    private static Heading heading(Train train) {
        Carriage first = train.carriages.get(0);
        Carriage last = train.carriages.get(train.carriages.size() - 1);
        TravellingPoint frontPoint = first.getLeadingPoint();
        TravellingPoint backPoint = last.getTrailingPoint();
        if (frontPoint.node1 == null || frontPoint.node2 == null || backPoint.node1 == null || frontPoint.edge == null
                || backPoint.edge == null) {
            return null;
        }
        ResourceKey<Level> frontDimension = frontPoint.node1.getLocation().dimension;
        ResourceKey<Level> backDimension = backPoint.node1.getLocation().dimension;
        Vec3 front = frontPoint.getPosition(train.graph);
        Vec3 back = backPoint.getPosition(train.graph);
        // Backing towards its destination, the trailing end leads.
        boolean backwards = train.navigation.destinationBehindTrain || train.speed < 0.0D;
        Vec3 leading = backwards ? back : front;
        Vec3 trailing = backwards ? front : back;
        ResourceKey<Level> dimension = backwards ? backDimension : frontDimension;
        Vec3 going = leading.subtract(trailing);
        if (!frontDimension.equals(backDimension) || going.lengthSqr() < 1.0e-4D) {
            // A train half through a portal, or a single bogey with nothing to measure along: the bogey's own edge
            // says which way it lies, and that will do.
            Vec3 along = frontPoint.node2.getLocation().getLocation().subtract(frontPoint.node1.getLocation().getLocation());
            if (along.lengthSqr() < 1.0e-4D) {
                return null;
            }
            going = backwards ? along.reverse() : along;
        }
        return new Heading(dimension, leading, going.normalize());
    }

    /**
     * Whether a lane's track is ahead of the train and the train is heading into the gate through it.
     *
     * @param inward which way a train on that track goes as it enters the gate
     */
    private static boolean ahead(Heading heading, ResourceKey<Level> dimension, BlockPos track, Direction inward,
                                 double reachSqr) {
        if (!heading.dimension().equals(dimension)) {
            return false;
        }
        Vec3 toTrack = track.getBottomCenter().subtract(heading.front());
        if (toTrack.lengthSqr() > reachSqr) {
            return false;
        }
        Vec3 into = Vec3.atLowerCornerOf(inward.getNormal());
        return heading.going().dot(into) >= HEADING_INTO && toTrack.dot(heading.going()) > 0.0D;
    }
}
