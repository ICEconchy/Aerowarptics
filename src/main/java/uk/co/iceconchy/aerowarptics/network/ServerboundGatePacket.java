package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorAccess;
import uk.co.iceconchy.aerowarptics.gate.RiftGate;
import uk.co.iceconchy.aerowarptics.gate.RiftGateBlockEntity;
import uk.co.iceconchy.aerowarptics.gate.RiftGateRegistry;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkNotice;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkOrders;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkRules;
import uk.co.iceconchy.aerowarptics.warpnet.WarpNetworkRegistry;
import uk.co.iceconchy.aerowarptics.warpnet.WarpNetworks;

import java.util.UUID;

/**
 * Everything a player can do standing at a Rift Gate.
 *
 * <p>One payload for all of it because they share the same gate: the controller has to exist, be
 * within reach, and be a gate this player may work. Splitting them into four packets would mean
 * writing that gate four times.
 *
 * @param gatePos  the controller the player clicked
 * @param action   what they are doing
 * @param target   destination for {@link Action#DIAL}, otherwise ignored
 * @param text     new name for {@link Action#RENAME}, the network's name for {@link Action#SET_NETWORK},
 *                 otherwise ignored
 * @param filter   the network the panel's list is filtered to, or nil for every gate. Sent with every
 *                 action, because every one is answered with the panel again
 */
public record ServerboundGatePacket(BlockPos gatePos, Action action, UUID target, String text, UUID filter)
        implements CustomPacketPayload {

    public enum Action {
        /** Send this gate's dial list back. */
        OPEN,
        /** Strike a connection to the named gate. */
        DIAL,
        /** Let go of whatever connection is standing. */
        HANG_UP,
        /** Rename this gate. */
        RENAME,
        /** Step this gate on to the next of public, network and private. */
        CYCLE_ACCESS,
        /** Put this gate on the network named, founding it if the name is new, or on none if blank. */
        SET_NETWORK,
        /** Route this gate's rail lanes to the named gate, without dialling it. Appended: actions go by ordinal. */
        SET_ROUTE
    }

    private static final UUID NIL = new UUID(0L, 0L);

    public static final Type<ServerboundGatePacket> TYPE = new Type<>(AeroWarptics.id("gate"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundGatePacket> STREAM_CODEC =
            StreamCodec.of(ServerboundGatePacket::encode, ServerboundGatePacket::decode);

    public static ServerboundGatePacket open(BlockPos gatePos, @Nullable UUID filter) {
        return new ServerboundGatePacket(gatePos, Action.OPEN, NIL, "", orNil(filter));
    }

    public static ServerboundGatePacket dial(BlockPos gatePos, UUID target, @Nullable UUID filter) {
        return new ServerboundGatePacket(gatePos, Action.DIAL, target, "", orNil(filter));
    }

    public static ServerboundGatePacket setRoute(BlockPos gatePos, UUID target, @Nullable UUID filter) {
        return new ServerboundGatePacket(gatePos, Action.SET_ROUTE, target, "", orNil(filter));
    }

    public static ServerboundGatePacket hangUp(BlockPos gatePos, @Nullable UUID filter) {
        return new ServerboundGatePacket(gatePos, Action.HANG_UP, NIL, "", orNil(filter));
    }

    public static ServerboundGatePacket rename(BlockPos gatePos, String name, @Nullable UUID filter) {
        return new ServerboundGatePacket(gatePos, Action.RENAME, NIL, name, orNil(filter));
    }

    public static ServerboundGatePacket cycleAccess(BlockPos gatePos, @Nullable UUID filter) {
        return new ServerboundGatePacket(gatePos, Action.CYCLE_ACCESS, NIL, "", orNil(filter));
    }

    public static ServerboundGatePacket setNetwork(BlockPos gatePos, String network, @Nullable UUID filter) {
        return new ServerboundGatePacket(gatePos, Action.SET_NETWORK, NIL, network, orNil(filter));
    }

    private static UUID orNil(@Nullable UUID id) {
        return id == null ? NIL : id;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ServerboundGatePacket packet) {
        buf.writeBlockPos(packet.gatePos);
        buf.writeEnum(packet.action);
        buf.writeUUID(packet.target == null ? NIL : packet.target);
        buf.writeUtf(packet.text == null ? "" : packet.text, 64);
        buf.writeUUID(orNil(packet.filter));
    }

    private static ServerboundGatePacket decode(RegistryFriendlyByteBuf buf) {
        return new ServerboundGatePacket(buf.readBlockPos(), buf.readEnum(Action.class),
                buf.readUUID(), buf.readUtf(64), buf.readUUID());
    }

    public static void handle(ServerboundGatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            RiftGateBlockEntity gate = resolve(player, level, packet.gatePos);
            if (gate == null) {
                return;
            }

            NetworkNotice notice = NetworkNotice.NONE;
            switch (packet.action) {
                case OPEN -> {
                }
                case DIAL -> gate.dial(player, packet.target);
                // Refused while a train is part of the way through; see playerHangUp.
                case HANG_UP -> gate.playerHangUp();
                case SET_ROUTE -> gate.setRoute(player, NIL.equals(packet.target) ? null : packet.target);
                case RENAME -> rename(level, gate, player, packet.text);
                case CYCLE_ACCESS -> cycleAccess(level, gate, player);
                case SET_NETWORK -> notice = setNetwork(level, gate, player, packet.text);
            }
            ClientboundGateDialPacket.sendTo(player, level, gate,
                    NIL.equals(packet.filter) ? null : packet.filter, notice);
        });
    }

    /**
     * Finds the gate, having first checked the player is entitled to be standing at it.
     *
     * <p>The reach bound is deliberately generous and deliberately cheap. It exists to stop a client
     * driving a gate on the far side of the world, not to be the authority on who may dial what -
     * that authority is the destination's, and it is applied when a connection is actually struck.
     *
     * <p>A gate aboard an airship lives in a plot chunk whose coordinates have nothing to do with the
     * player's, so the cheap bound is only applied to one on the ground - the same reason every other
     * machine that can ride a hull skips it too. Without this, a gate built on any sub-level never
     * opened its dial panel at all: the distance came back enormous and every request was refused
     * before the packet handler ever looked at the gate.
     */
    private static RiftGateBlockEntity resolve(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof RiftGateBlockEntity gate)) {
            return null;
        }
        double reach = AWConfig.MAX_INTERACTION_DISTANCE.get() + 16.0D;
        if (gate.airship() == null && player.blockPosition().distSqr(pos) > reach * reach) {
            return null;
        }
        return gate;
    }

    /** Only the owner may rename a gate, and only to a name nothing else has taken. */
    private static void rename(ServerLevel level, RiftGateBlockEntity gate, ServerPlayer player, String name) {
        RiftGateRegistry registry = RiftGateRegistry.get(level);
        RiftGate record = registry.byId(gate.gateId());
        if (record == null || !record.mayEdit(player)) {
            return;
        }
        registry.rename(record.id(), name);
    }

    private static void cycleAccess(ServerLevel level, RiftGateBlockEntity gate, ServerPlayer player) {
        RiftGateRegistry registry = RiftGateRegistry.get(level);
        RiftGate record = registry.byId(gate.gateId());
        if (record == null || !record.mayEdit(player)) {
            return;
        }
        registry.register(record.withAccess(
                record.access().next(record.network() != null, WarpAnchorAccess::permitted)));
    }

    /**
     * Puts this gate on the network typed into its panel, or takes it off one.
     *
     * @return why not, to be said in the panel - which stays open, so the action bar behind it would not do
     */
    private static NetworkNotice setNetwork(ServerLevel level, RiftGateBlockEntity gate, ServerPlayer player,
                                            String typed) {
        RiftGateRegistry registry = RiftGateRegistry.get(level);
        RiftGate record = registry.byId(gate.gateId());
        if (record == null || !record.mayEdit(player)) {
            return NetworkNotice.NONE;
        }
        NetworkOrders.Resolved resolved = NetworkOrders.resolve(WarpNetworkRegistry.get(level), typed,
                record.owner(), player.getUUID(), player.getGameProfile().getName(), player.hasPermissions(2),
                WarpNetworks.caps());
        if (resolved.refused()) {
            return resolved.notice();
        }
        if (!java.util.Objects.equals(resolved.network(), record.network())) {
            registry.register(resolved.network() == null
                    ? record.withoutNetwork() : record.withNetwork(resolved.network()));
            Trace.log(Trace.GATE, "{} put on network {} by {}", Trace.pos(gate.getBlockPos()),
                    resolved.network() == null ? "none" : "\"" + NetworkRules.clean(typed) + "\"", Trace.who(player));
        }
        return NetworkNotice.NONE;
    }
}
