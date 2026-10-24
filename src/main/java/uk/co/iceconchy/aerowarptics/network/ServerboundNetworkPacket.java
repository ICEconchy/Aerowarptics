package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkNotice;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkOrders;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkRules;
import uk.co.iceconchy.aerowarptics.warpnet.WarpNetworkRegistry;
import uk.co.iceconchy.aerowarptics.warpnet.WarpNetworks;

import java.util.UUID;

/**
 * Everything a player can ask of a network from its members panel.
 *
 * <p>Carries no position, unlike every other serverbound payload here, because a network is not
 * anywhere: it has no block to stand at. What stands in for reach is who the player is - every order is
 * checked against the network's own record of its owner and members, in {@link NetworkOrders}, and the
 * client's idea of either is never asked.
 *
 * <p>A network is founded from a machine, not from here: see {@link ServerboundConfigureAnchorPacket}
 * and {@link ServerboundGatePacket}. A name with nothing on it would be a network of no use to anybody.
 *
 * @param network the network being asked about, or nil for none
 * @param target  the member to remove, for {@link Action#REMOVE}, otherwise ignored
 * @param text    the new name for {@link Action#RENAME}, a player's name for {@link Action#ADD},
 *                otherwise ignored
 */
public record ServerboundNetworkPacket(Action action, UUID network, UUID target, String text)
        implements CustomPacketPayload {

    public enum Action {
        /** Send back the networks this player is in, and the one named in full. */
        LIST,
        /** Rename the network. Its owner only. */
        RENAME,
        /** Add a player, by name, who has to be online. Its owner only. */
        ADD,
        /** Remove a member, and take their machines off the network. Its owner only. */
        REMOVE,
        /** Walk out of the network, taking this player's machines off it. Any member but its owner. */
        LEAVE,
        /** End the network, taking every machine off it. Its owner only. */
        DISBAND
    }

    private static final UUID NIL = new UUID(0L, 0L);

    /** A player's name is sixteen characters; a network's is {@link NetworkRules#NAME_MAX}. */
    private static final int TEXT_LENGTH = 32;

    public static final Type<ServerboundNetworkPacket> TYPE = new Type<>(AeroWarptics.id("network"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundNetworkPacket> STREAM_CODEC =
            StreamCodec.of(ServerboundNetworkPacket::encode, ServerboundNetworkPacket::decode);

    public static ServerboundNetworkPacket list(@Nullable UUID network) {
        return new ServerboundNetworkPacket(Action.LIST, network == null ? NIL : network, NIL, "");
    }

    public static ServerboundNetworkPacket rename(UUID network, String name) {
        return new ServerboundNetworkPacket(Action.RENAME, network, NIL, name);
    }

    public static ServerboundNetworkPacket add(UUID network, String playerName) {
        return new ServerboundNetworkPacket(Action.ADD, network, NIL, playerName);
    }

    public static ServerboundNetworkPacket remove(UUID network, UUID member) {
        return new ServerboundNetworkPacket(Action.REMOVE, network, member, "");
    }

    public static ServerboundNetworkPacket leave(UUID network) {
        return new ServerboundNetworkPacket(Action.LEAVE, network, NIL, "");
    }

    public static ServerboundNetworkPacket disband(UUID network) {
        return new ServerboundNetworkPacket(Action.DISBAND, network, NIL, "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ServerboundNetworkPacket packet) {
        buf.writeEnum(packet.action);
        buf.writeUUID(packet.network == null ? NIL : packet.network);
        buf.writeUUID(packet.target == null ? NIL : packet.target);
        String text = packet.text == null ? "" : packet.text;
        buf.writeUtf(text.length() > TEXT_LENGTH ? text.substring(0, TEXT_LENGTH) : text, TEXT_LENGTH);
    }

    private static ServerboundNetworkPacket decode(RegistryFriendlyByteBuf buf) {
        return new ServerboundNetworkPacket(buf.readEnum(Action.class), buf.readUUID(), buf.readUUID(),
                buf.readUtf(TEXT_LENGTH));
    }

    public static void handle(ServerboundNetworkPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || player.getServer() == null) {
                return;
            }
            MinecraftServer server = player.getServer();
            WarpNetworkRegistry registry = WarpNetworkRegistry.get(server);
            UUID id = NIL.equals(packet.network) ? null : packet.network;
            NetworkNotice notice = id == null || packet.action == Action.LIST
                    ? NetworkNotice.NONE : carryOut(packet, server, registry, player, id);
            ClientboundNetworkPanelPacket.sendTo(player, registry, id, notice);
        });
    }

    private static NetworkNotice carryOut(ServerboundNetworkPacket packet, MinecraftServer server,
                                          WarpNetworkRegistry registry, ServerPlayer player, UUID id) {
        UUID actor = player.getUUID();
        boolean operator = player.hasPermissions(2);
        switch (packet.action) {
            case RENAME -> {
                NetworkNotice notice = NetworkOrders.rename(registry, id, actor, operator, packet.text);
                if (!notice.refused()) {
                    WarpNetworks.renamed(server, id);
                }
                return notice;
            }
            case ADD -> {
                // Only somebody online. Looking an absent player up by name can mean asking Mojang, on this
                // thread; an operator who needs to add one has the command, which may take its time.
                String name = packet.text == null ? "" : packet.text.trim();
                ServerPlayer added = name.isEmpty() ? null : server.getPlayerList().getPlayerByName(name);
                if (added == null) {
                    Trace.log(Trace.NETWORK, "{} may not add \"{}\" to {}: nobody of that name is online",
                            Trace.who(player), name, id);
                    return NetworkNotice.refusal(NetworkNotice.NO_PLAYER, name);
                }
                return NetworkOrders.add(registry, id, actor, operator, added.getUUID(),
                        added.getGameProfile().getName(), WarpNetworks.caps());
            }
            case REMOVE -> {
                NetworkNotice notice = NetworkOrders.remove(registry, id, actor, operator, packet.target);
                if (!notice.refused()) {
                    WarpNetworks.detach(server, id, packet.target);
                }
                return notice;
            }
            case LEAVE -> {
                NetworkNotice notice = NetworkOrders.leave(registry, id, actor);
                if (!notice.refused()) {
                    WarpNetworks.detach(server, id, actor);
                }
                return notice;
            }
            case DISBAND -> {
                NetworkNotice notice = NetworkOrders.disband(registry, id, actor, operator);
                if (!notice.refused()) {
                    WarpNetworks.detach(server, id, null);
                }
                return notice;
            }
            default -> {
                return NetworkNotice.NONE;
            }
        }
    }
}
