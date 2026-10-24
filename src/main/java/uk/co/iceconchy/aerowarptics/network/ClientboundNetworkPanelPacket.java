package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkFilter;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkNotice;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkOption;
import uk.co.iceconchy.aerowarptics.warpnet.WarpNetwork;
import uk.co.iceconchy.aerowarptics.warpnet.WarpNetworkRegistry;

import java.util.List;
import java.util.UUID;

/**
 * What a player's networks look like from where they stand: the ones they are in, by name, and one of
 * them in full.
 *
 * <p>Only one in full, because only one is ever on screen, and a network's member list is the bulk of
 * it. A machine's network box wants the names and nothing more.
 *
 * @param mine    every network this player is in, by name, for a machine's network box to offer
 * @param asked   the network that was asked about, or {@code null} for none. Sent back so that a members
 *                panel can tell an answer about its own network from one about no network at all - a
 *                machine's panel asks only for the names, and its answer has no network in it either
 * @param viewing that network in full, or {@code null} when there is none to show: none was asked for, it
 *                has gone, or this player is not in it - which is how the panel learns to close after a
 *                Leave or a Disband
 * @param notice  what to tell them about what they just asked
 */
public record ClientboundNetworkPanelPacket(List<NetworkOption> mine, @Nullable UUID asked,
                                            @Nullable WarpNetwork viewing,
                                            NetworkNotice notice) implements CustomPacketPayload {

    public static final Type<ClientboundNetworkPanelPacket> TYPE = new Type<>(AeroWarptics.id("network_panel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundNetworkPanelPacket> STREAM_CODEC =
            StreamCodec.of(ClientboundNetworkPanelPacket::encode, ClientboundNetworkPanelPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Builds and sends the panel to one player.
     *
     * @param asked the network to send in full, or {@code null} for none
     */
    public static void sendTo(ServerPlayer player, WarpNetworkRegistry registry, @Nullable UUID asked,
                              NetworkNotice notice) {
        List<NetworkOption> mine = PacketLists.cap(
                registry.networksOf(player.getUUID()).stream()
                        .map(network -> new NetworkOption(network.id(), network.name())).toList(),
                NetworkFilter.BY_NAME);
        // Who is in a network is shown to the people in it, and to an operator. Nobody else is told.
        WarpNetwork network = registry.byId(asked);
        WarpNetwork viewing = network != null && (network.has(player.getUUID()) || player.hasPermissions(2))
                ? network : null;
        AWNetwork.sendTo(player, new ClientboundNetworkPanelPacket(mine, asked, viewing, notice));
    }

    private static void encode(RegistryFriendlyByteBuf buf, ClientboundNetworkPanelPacket packet) {
        NetworkOption.STREAM_CODEC.apply(ByteBufCodecs.list(PacketLists.MAX_ROWS)).encode(buf, packet.mine);
        buf.writeBoolean(packet.asked != null);
        if (packet.asked != null) {
            buf.writeUUID(packet.asked);
        }
        buf.writeBoolean(packet.viewing != null);
        if (packet.viewing != null) {
            WarpNetwork.STREAM_CODEC.encode(buf, packet.viewing);
        }
        NetworkNotice.encode(buf, packet.notice);
    }

    private static ClientboundNetworkPanelPacket decode(RegistryFriendlyByteBuf buf) {
        return new ClientboundNetworkPanelPacket(
                NetworkOption.STREAM_CODEC.apply(ByteBufCodecs.list(PacketLists.MAX_ROWS)).decode(buf),
                buf.readBoolean() ? buf.readUUID() : null,
                buf.readBoolean() ? WarpNetwork.STREAM_CODEC.decode(buf) : null,
                NetworkNotice.decode(buf));
    }

    public static void handle(ClientboundNetworkPanelPacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> AWClientHooks.acceptNetworkPanel(packet));
    }
}
