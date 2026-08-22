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
 * @param reachable gates this player may dial from here, already filtered to this dimension,
 *                  nearest first once there are more than one packet can carry
 * @param totalReachable how many there were before that cap, so the panel can admit to it
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
                                        List<RiftGate> reachable) implements CustomPacketPayload {

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

    /** Builds and sends the panel for one gate to one player. */
    public static void sendTo(ServerPlayer player, ServerLevel level, RiftGateBlockEntity gate) {
        RiftGateRegistry registry = RiftGateRegistry.get(level);
        RiftGate record = registry.byId(gate.gateId());
        List<RiftGate> inReach = gate.isFormed()
                ? registry.dialableFrom(player, level.dimension(), gate.gateId())
                : List.<RiftGate>of();
        // Same unbounded list, same encoder limit, same crash as the Astrolabe's chart. Ranked by how
        // far away each gate is from this one, because that is the only ordering the panel can offer
        // that a player would agree with; below the limit the registry's own name order is kept.
        BlockPos here = gate.getBlockPos();
        List<RiftGate> reachable = PacketLists.cap(inReach,
                Comparator.<RiftGate>comparingDouble(other -> other.controller().distSqr(here))
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
                record == null || record.owner() == null || record.isOwnedBy(player.getUUID()),
                gate.lastFailure(),
                inReach.size(),
                reachable));
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
                RiftGate.STREAM_CODEC.apply(ByteBufCodecs.list(PacketLists.MAX_ROWS)).decode(buf));
    }

    public static void handle(ClientboundGateDialPacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> AWClientHooks.acceptGateDial(packet));
    }
}
