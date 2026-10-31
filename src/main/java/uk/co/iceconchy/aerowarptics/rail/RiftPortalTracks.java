package uk.co.iceconchy.aerowarptics.rail;

import com.simibubi.create.api.contraption.train.PortalTrackProvider;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.gate.GateFailure;
import uk.co.iceconchy.aerowarptics.gate.RiftGate;
import uk.co.iceconchy.aerowarptics.gate.RiftGateBlockEntity;
import uk.co.iceconchy.aerowarptics.gate.RiftGateRegistry;
import uk.co.iceconchy.aerowarptics.hold.Holds;
import uk.co.iceconchy.aerowarptics.registry.AWBlocks;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.util.AWLang;

/**
 * What Create is told about a Rift Gate when somebody runs track into one.
 *
 * <p>Create's portal tracks work through a registry of portal blocks: a straight track laid up to one asks the
 * block's provider where the other side is, lays a track there, and binds the two. A gate's pane and the
 * {@link uk.co.iceconchy.aerowarptics.gate.RiftSocketBlock socket} that stands in for it while the gate is shut
 * are both registered here, and their "other side" is the west end of a fresh corridor in the
 * {@link RailCorridor}. The far end of the corridor is laid by {@link RailWorks}, out of whichever gate this one is
 * routed to - that is the part a player chooses at the dial.
 *
 * <h2>Refusals</h2>
 * Create takes a refused track up and drops it, then says "Target portal not generated yet" to the nearest
 * player, which is no help at all. So every refusal here also says why, in the action bar, and traces itself.
 */
public final class RiftPortalTracks {

    /** How far from the track the player told about a refusal may be - Create's own figure. */
    private static final double TELL_RANGE = 10.0D;

    private RiftPortalTracks() {
    }

    /** Registered on the mod event bus from {@link uk.co.iceconchy.aerowarptics.AeroWarptics}. */
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            PortalTrackProvider.REGISTRY.register(AWBlocks.RIFT_PORTAL.get(), RiftPortalTracks::fromGate);
            PortalTrackProvider.REGISTRY.register(AWBlocks.RIFT_SOCKET.get(), RiftPortalTracks::fromGate);
            PortalTrackProvider.REGISTRY.register(AWBlocks.CORRIDOR_MOUTH.get(), RiftPortalTracks::fromMouth);
        });
    }

    /**
     * The far side of a gate's opening, for a track laid up to it.
     *
     * @param face the track, and the way from it into the opening
     */
    @Nullable
    static PortalTrackProvider.Exit fromGate(ServerLevel level, BlockFace face) {
        BlockPos track = face.getPos();
        Direction inward = face.getFace();
        BlockPos cell = face.getConnectedPos();
        MinecraftServer server = level.getServer();
        if (RailCorridor.isCorridor(level)) {
            return null; // a pane cannot stand in the corridor; nothing here is anybody's track
        }
        ServerLevel corridor = RailCorridor.level(server);
        if (!AWConfig.RAIL_GATES.get() || corridor == null) {
            return refuse(level, track, GateFailure.RAIL_DISABLED, "rail gates are off or the corridor is missing");
        }

        RailLaneRegistry lanes = RailLaneRegistry.get(server);
        RailLane existing = lanes.atTrack(level.dimension(), track);
        if (existing != null) {
            return relay(level, corridor, existing, track);
        }

        RiftGate record = gateAt(level, cell);
        if (record == null) {
            return refuse(level, track, GateFailure.NOT_FORMED, "no gate owns " + Trace.pos(cell));
        }
        if (Holds.inPlot(level, cell)) {
            return refuse(level, track, GateFailure.RAIL_ABOARD_SHIP, record.displayName() + " is aboard a ship");
        }
        RailLaneMap.NearResult near = RailLaneMap.near(record.shape(), track, inward);
        if (near.refusal() != null) {
            GateFailure reason = switch (near.refusal()) {
                case WRONG_AXIS -> GateFailure.RAIL_WRONG_WAY;
                case NOT_IN_OPENING -> GateFailure.NOT_FORMED;
                case NOT_BOTTOM_ROW -> GateFailure.RAIL_NOT_BOTTOM_ROW;
            };
            return refuse(level, track, reason, "track at " + Trace.pos(track) + " heading " + inward + " into "
                    + record.displayName() + ": " + near.refusal());
        }
        RiftGateBlockEntity gate = level.getBlockEntity(record.controller()) instanceof RiftGateBlockEntity found
                ? found : null;
        if (gate == null || !gate.isFormed()) {
            return refuse(level, track, GateFailure.NOT_FORMED, record.displayName() + "'s controller is not loaded");
        }
        if (gate.destination() == null) {
            return refuse(level, track, GateFailure.RAIL_NO_ROUTE, record.displayName() + " has no route");
        }
        int owned = lanes.ownedBy(record.id()).size();
        if (owned >= AWConfig.RAIL_MAX_LANES.get()) {
            return refuse(level, track, GateFailure.RAIL_TOO_MANY_LANES, record.displayName() + " already has "
                    + owned + " lanes of " + AWConfig.RAIL_MAX_LANES.get());
        }
        int slot = CorridorSlots.allocate(lanes.usedSlots());
        if (slot < 0) {
            return refuse(level, track, GateFailure.RAIL_DISABLED, "every corridor slot is taken");
        }

        Block material = level.getBlockState(track).getBlock();
        int length = CorridorFit.requiredLength(AWConfig.RAIL_CORRIDOR_LENGTH.get(),
                AllConfigs.server().trains.maxAssemblyLength.get());
        RailWorks.layCorridor(corridor, slot, length, material);
        RailLane lane = RailLane.laid(record.id(), level.dimension(), track, cell, inward, slot, length,
                BuiltInRegistries.BLOCK.getKey(material), gate.destination());
        lanes.put(lane);
        Trace.log(Trace.RAIL, "lane {} laid into {} at {} heading {} (corridor {}, {} blocks), routed to {}",
                lane.id(), record.displayName(), Trace.pos(track), inward, slot, length, gate.destination());
        RailWorks.layFar(server, lane);
        gate.laneCellsChanged();
        RailWorks.joinSoon(RailWorks.westTrack(slot));
        // Create lays and binds the near pair itself once this returns, which tears the graph down as surely as
        // anything here does.
        RailPoints.disturbed();
        return new PortalTrackProvider.Exit(corridor, new BlockFace(RailWorks.westTrack(slot), Direction.WEST));
    }

    /**
     * A track laid again where one of a lane's own tracks stood - a player mending a near track they took up, or
     * the far stub. It is bound back into the same corridor at the end it belongs to.
     */
    @Nullable
    private static PortalTrackProvider.Exit relay(ServerLevel level, ServerLevel corridor, RailLane lane,
                                                  BlockPos track) {
        boolean nearEnd = lane.dimension().equals(level.dimension()) && lane.track().equals(track);
        BlockPos end = nearEnd ? RailWorks.westTrack(lane.slot()) : RailWorks.eastTrack(lane.slot(), lane.length());
        // Whatever is left at the corridor's end is let go of quietly, so Create finds room to lay the new one.
        RailWorks.unbind(corridor, end);
        if (!corridor.getBlockState(end).isAir()) {
            corridor.setBlock(end, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        Trace.log(Trace.RAIL, "lane {}'s {} track at {} laid again, and bound back into corridor {}", lane.id(),
                nearEnd ? "near" : "far", Trace.pos(track), lane.slot());
        if (!nearEnd && lane.state() != RailLaneState.READY && lane.farDimension() != null) {
            RailLaneRegistry.get(level.getServer()).put(lane.withState(RailLaneState.READY));
        }
        RailWorks.joinSoon(end);
        RailPoints.disturbed();
        return new PortalTrackProvider.Exit(corridor,
                new BlockFace(end, nearEnd ? Direction.WEST : Direction.EAST));
    }

    /** Nobody lays track in the corridor, so a mouth leads nowhere. Said, in case somebody manages it. */
    @Nullable
    static PortalTrackProvider.Exit fromMouth(ServerLevel level, BlockFace face) {
        Trace.log(Trace.RAIL, "track laid up to a corridor mouth at {} in {} - refused; corridors are laid, not"
                + " built", Trace.pos(face.getPos()), level.dimension().location());
        return null;
    }

    /** The gate whose opening has this cell in it, in this level. */
    @Nullable
    public static RiftGate gateAt(ServerLevel level, BlockPos cell) {
        for (RiftGate gate : RiftGateRegistry.get(level).all()) {
            if (gate.isInSameDimension(level) && gate.shape().cells().contains(cell)) {
                return gate;
            }
        }
        return null;
    }

    @Nullable
    private static PortalTrackProvider.Exit refuse(ServerLevel level, BlockPos track, GateFailure reason, String why) {
        Trace.log(Trace.RAIL, "track at {} in {} refused as a lane: {} - {}", Trace.pos(track),
                level.dimension().location(), reason.getSerializedName(), why);
        Player nearest = level.getNearestPlayer(track.getX() + 0.5D, track.getY() + 0.5D, track.getZ() + 0.5D,
                TELL_RANGE, false);
        if (nearest != null) {
            nearest.displayClientMessage(AWLang.translate(reason.translationKey()).component(), true);
        }
        return null;
    }

    /** Whether a block is a track Create would try to run into a gate: straight, flat and not yet a portal. */
    public static boolean runsInto(BlockState state, Direction.Axis normal) {
        if (!(state.getBlock() instanceof TrackBlock)) {
            return false;
        }
        return switch (state.getValue(TrackBlock.SHAPE)) {
            case XO -> normal == Direction.Axis.X;
            case ZO -> normal == Direction.Axis.Z;
            default -> false;
        };
    }

    /** Whether a track is a lane's: in its portal shape and bound somewhere. */
    public static boolean isBoundPortal(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof TrackBlock && state.getValue(TrackBlock.SHAPE).isPortal()
                && level.getBlockEntity(pos) instanceof TrackBlockEntity track && track.boundLocation != null;
    }
}
