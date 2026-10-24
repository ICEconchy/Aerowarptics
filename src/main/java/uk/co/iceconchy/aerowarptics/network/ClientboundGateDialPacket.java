package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorAccess;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.gate.GateFailure;
import uk.co.iceconchy.aerowarptics.gate.RiftGate;
import uk.co.iceconchy.aerowarptics.gate.RiftGateBlockEntity;
import uk.co.iceconchy.aerowarptics.gate.RiftGateRegistry;
import uk.co.iceconchy.aerowarptics.gate.RiftGateState;
import uk.co.iceconchy.aerowarptics.rail.RailLane;
import uk.co.iceconchy.aerowarptics.rail.RailLaneRegistry;
import uk.co.iceconchy.aerowarptics.rail.RailSummary;
import uk.co.iceconchy.aerowarptics.rail.RailTransit;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkFilter;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkNotice;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkOption;
import uk.co.iceconchy.aerowarptics.warpnet.WarpNetworkRegistry;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * A gate's dial panel: everything the screen draws, already decided.
 *
 * <p>A pure view, like the Astrolabe's chart. Costs, refusals and the list of reachable gates are all
 * worked out server-side, so the screen cannot offer a connection the gate would turn down and the
 * only thing it ever sends back is an id.
 *
 * @param reachable gates this player may dial from here, already filtered to this dimension and to the
 *                  network the list is filtered by, nearest first once there are more than one packet can carry
 * @param totalReachable how many there were before that cap, so the panel can admit to it
 * @param network   the network this gate is on, or {@code null} for none
 * @param networkName that network's name, blank for none
 * @param filter    the network the list is filtered to, or {@code null} for every gate - what the server
 *                  applied, which is everything when the network asked for has gone
 * @param networks  the networks the list can be filtered by, which is also where a row's network gets
 *                  its name
 * @param notice    what the gate has to say about the last thing this player asked of its network
 * @param essence   how much Rift Essence this gate is holding, in millibuckets
 * @param cost      what a dial from this gate would spend
 * @param speed     how fast the gate is turning, and what it needs, so a refusal can be read off
 */
public record ClientboundGateDialPacket(BlockPos gatePos,
                                        String name,
                                        int stateIndex,
                                        int width,
                                        int height,
                                        @Nullable UUID selected,
                                        @Nullable UUID connected,
                                        int essence,
                                        int capacity,
                                        int cost,
                                        float speed,
                                        int requiredSpeed,
                                        WarpAnchorAccess access,
                                        boolean owned,
                                        GateFailure failure,
                                        int totalReachable,
                                        List<RiftGate> reachable,
                                        @Nullable UUID network,
                                        String networkName,
                                        @Nullable UUID filter,
                                        List<NetworkOption> networks,
                                        NetworkNotice notice,
                                        RailSummary rails) implements CustomPacketPayload {

    /** Whether there were more gates in reach than the panel could be sent. */
    public boolean truncated() {
        return totalReachable > reachable.size();
    }

    public static final Type<ClientboundGateDialPacket> TYPE = new Type<>(AeroWarptics.id("gate_dial"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundGateDialPacket> STREAM_CODEC =
            StreamCodec.of(ClientboundGateDialPacket::encode, ClientboundGateDialPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public RiftGateState state() {
        return RiftGateState.byIndex(stateIndex);
    }

    public boolean formed() {
        return state() != RiftGateState.UNFORMED;
    }

    /**
     * Builds and sends the panel for one gate to one player.
     *
     * @param wanted the network the player's list is filtered to, or {@code null} for every gate
     * @param notice what to tell them about what they just asked, or {@link NetworkNotice#NONE}
     */
    public static void sendTo(ServerPlayer player, ServerLevel level, RiftGateBlockEntity gate,
                              @Nullable UUID wanted, NetworkNotice notice) {
        RiftGateRegistry registry = RiftGateRegistry.get(level);
        WarpNetworkRegistry networks = WarpNetworkRegistry.get(level);
        RiftGate record = registry.byId(gate.gateId());
        // Gates in other dimensions too, where the server lets rails run there: a route may name one, though a dial
        // may not. The panel marks them as trains only.
        boolean crossDimension = AWConfig.RAIL_GATES.get() && AWConfig.RAIL_CROSS_DIMENSION.get();
        List<RiftGate> dialable = !gate.isFormed() ? List.<RiftGate>of()
                : crossDimension ? registry.routableFrom(player, networks, level.dimension(), gate.gateId())
                : registry.dialableFrom(player, networks, level.dimension(), gate.gateId());
        // Offered from every gate in reach and applied before the cut, as the Astrolabe's chart does it.
        List<NetworkOption> offered = PacketLists.cap(
                NetworkFilter.options(dialable.stream().map(RiftGate::network).toList(), networks::nameOf),
                NetworkFilter.BY_NAME);
        UUID filter = NetworkFilter.applied(offered, wanted);
        List<RiftGate> inReach = filter == null ? dialable
                : dialable.stream().filter(other -> filter.equals(other.network())).toList();
        // Same unbounded list, same encoder limit, same crash as the Astrolabe's chart. Ranked by how
        // far away each gate is from this one, because that is the only ordering the panel can offer
        // that a player would agree with; below the limit the registry's own name order is kept.
        BlockPos here = gate.getBlockPos();
        List<RiftGate> reachable = PacketLists.cap(inReach,
                Comparator.<RiftGate, Boolean>comparing(other -> !other.dimension().equals(level.dimension()))
                        .thenComparingDouble(other -> other.controller().distSqr(here))
                        .thenComparing(RiftGate::displayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(other -> other.id().toString()));

        AWNetwork.sendTo(player, new ClientboundGateDialPacket(
                gate.getBlockPos(),
                record == null ? "" : record.name(),
                gate.state().index(),
                gate.shape() == null ? 0 : gate.shape().width(),
                gate.shape() == null ? 0 : gate.shape().height(),
                gate.destination(),
                gate.connected(),
                gate.essence(),
                RiftGateBlockEntity.CAPACITY,
                gate.dialCost(),
                gate.getSpeed(),
                AWConfig.GATE_MINIMUM_RPM.get(),
                record == null ? WarpAnchorAccess.PUBLIC : record.access(),
                // Operators too: the server lets them edit any gate, and a panel that greyed the controls
                // out for them would be refusing what it would in fact be allowed.
                record == null || record.mayEdit(player),
                gate.lastFailure(),
                inReach.size(),
                reachable,
                record == null ? null : record.network(),
                record == null ? "" : networks.nameOf(record.network()),
                filter,
                offered,
                notice,
                railSummary(level, gate)));
    }

    private static RailSummary railSummary(ServerLevel level, RiftGateBlockEntity gate) {
        net.minecraft.server.MinecraftServer server = level.getServer();
        RailLaneRegistry lanes = RailLaneRegistry.get(server);
        List<RailLane> owned = lanes.ownedBy(gate.gateId());
        return new RailSummary(owned.size(), (int) owned.stream().filter(RailLane::ready).count(),
                lanes.comingOutOf(gate.gateId()).size(), RailTransit.waitingAt(gate.gateId()),
                RailTransit.runningThrough(server, gate.gateId()), level.hasNeighborSignal(gate.getBlockPos()));
    }

    private static void encode(RegistryFriendlyByteBuf buf, ClientboundGateDialPacket packet) {
        buf.writeBlockPos(packet.gatePos);
        buf.writeUtf(packet.name, 64);
        buf.writeVarInt(packet.stateIndex);
        buf.writeVarInt(packet.width);
        buf.writeVarInt(packet.height);
        buf.writeBoolean(packet.selected != null);
        if (packet.selected != null) {
            buf.writeUUID(packet.selected);
        }
        buf.writeBoolean(packet.connected != null);
        if (packet.connected != null) {
            buf.writeUUID(packet.connected);
        }
        buf.writeVarInt(packet.essence);
        buf.writeVarInt(packet.capacity);
        buf.writeVarInt(packet.cost);
        buf.writeFloat(packet.speed);
        buf.writeVarInt(packet.requiredSpeed);
        buf.writeEnum(packet.access);
        buf.writeBoolean(packet.owned);
        buf.writeEnum(packet.failure);
        buf.writeVarInt(packet.totalReachable);
        RiftGate.STREAM_CODEC.apply(ByteBufCodecs.list(PacketLists.MAX_ROWS))
                .encode(buf, packet.reachable);
        buf.writeBoolean(packet.network != null);
        if (packet.network != null) {
            buf.writeUUID(packet.network);
        }
        buf.writeUtf(packet.networkName, 32);
        buf.writeBoolean(packet.filter != null);
        if (packet.filter != null) {
            buf.writeUUID(packet.filter);
        }
        NetworkOption.STREAM_CODEC.apply(ByteBufCodecs.list(PacketLists.MAX_ROWS))
                .encode(buf, packet.networks);
        NetworkNotice.encode(buf, packet.notice);
        packet.rails.encode(buf);
    }

    private static ClientboundGateDialPacket decode(RegistryFriendlyByteBuf buf) {
        return new ClientboundGateDialPacket(
                buf.readBlockPos(),
                buf.readUtf(64),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean() ? buf.readUUID() : null,
                buf.readBoolean() ? buf.readUUID() : null,
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readFloat(),
                buf.readVarInt(),
                buf.readEnum(WarpAnchorAccess.class),
                buf.readBoolean(),
                buf.readEnum(GateFailure.class),
                buf.readVarInt(),
                RiftGate.STREAM_CODEC.apply(ByteBufCodecs.list(PacketLists.MAX_ROWS)).decode(buf),
                buf.readBoolean() ? buf.readUUID() : null,
                buf.readUtf(32),
                buf.readBoolean() ? buf.readUUID() : null,
                NetworkOption.STREAM_CODEC.apply(ByteBufCodecs.list(PacketLists.MAX_ROWS)).decode(buf),
                NetworkNotice.decode(buf),
                RailSummary.decode(buf));
    }

    public static void handle(ClientboundGateDialPacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> AWClientHooks.acceptGateDial(packet));
    }
}
