package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.registry.AWSounds;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.ward.RiftWardBlockEntity;
import uk.co.iceconchy.aerowarptics.ward.WardMode;
import uk.co.iceconchy.aerowarptics.ward.WardRules;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkRules;
import uk.co.iceconchy.aerowarptics.warpnet.WarpNetwork;
import uk.co.iceconchy.aerowarptics.warpnet.WarpNetworkRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A player's edit to a Rift Ward: how wide its field is, whom it turns aside, and whether it is on.
 *
 * <p>The server re-checks who is asking, clamps the radius to its own ceiling and looks every network up for
 * itself; the client's copy of the ward is never authoritative.
 *
 * @param networks the networks the mode is about, by name as typed. Names and not ids because a ward may name a
 *                 network its owner is not in - a rival's, to shut it out - and a name is all a player has of
 *                 one of those. A name no network has refuses the edit: nothing is founded from here
 * @param selfTest whether the ward's owner is turned too
 */
public record ServerboundConfigureWardPacket(BlockPos wardPos, int radius, WardMode mode, List<String> networks,
                                             boolean enabled, boolean selfTest) implements CustomPacketPayload {

    public static final Type<ServerboundConfigureWardPacket> TYPE = new Type<>(AeroWarptics.id("configure_ward"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundConfigureWardPacket> STREAM_CODEC =
            StreamCodec.of(ServerboundConfigureWardPacket::encode, ServerboundConfigureWardPacket::decode);

    private static void encode(RegistryFriendlyByteBuf buf, ServerboundConfigureWardPacket packet) {
        buf.writeBlockPos(packet.wardPos);
        buf.writeVarInt(packet.radius);
        buf.writeVarInt(packet.mode.ordinal());
        int count = Math.min(packet.networks.size(), WardRules.MOST_NETWORKS);
        buf.writeVarInt(count);
        for (int index = 0; index < count; index++) {
            buf.writeUtf(packet.networks.get(index), 32);
        }
        buf.writeBoolean(packet.enabled);
        buf.writeBoolean(packet.selfTest);
    }

    private static ServerboundConfigureWardPacket decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int radius = buf.readVarInt();
        WardMode mode = WardMode.byIndex(buf.readVarInt());
        int count = buf.readVarInt();
        // Checked before anything is read into it: a sender must not be able to make the server allocate at will.
        if (count < 0 || count > WardRules.MOST_NETWORKS) {
            throw new IllegalArgumentException("a ward naming " + count + " networks is out of range");
        }
        List<String> networks = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            networks.add(buf.readUtf(32));
        }
        return new ServerboundConfigureWardPacket(pos, radius, mode, List.copyOf(networks), buf.readBoolean(),
                buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ServerboundConfigureWardPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            BlockPos pos = packet.wardPos();
            double reach = AWConfig.MAX_INTERACTION_DISTANCE.get() + 4.0D;
            if (!level.isLoaded(pos) || player.blockPosition().distSqr(pos) > reach * reach) {
                return;
            }
            if (!(level.getBlockEntity(pos) instanceof RiftWardBlockEntity ward)) {
                return;
            }
            if (!ward.mayEdit(player)) {
                Trace.log(Trace.WARD, "{} may not set the ward at {}: it is {}'s", Trace.who(player), Trace.pos(pos),
                        ward.ownerName());
                AWLang.translate("message.ward_not_yours").sendStatus(player);
                return;
            }
            WarpNetworkRegistry registry = WarpNetworkRegistry.get(level);
            List<UUID> named = new ArrayList<>();
            for (String typed : packet.networks()) {
                String name = NetworkRules.clean(typed);
                if (name.isEmpty()) {
                    continue;
                }
                WarpNetwork network = registry.byName(name);
                if (network == null) {
                    // Nothing is saved. Said in the action bar, which is in view again: Save closes the panel.
                    Trace.log(Trace.WARD, "{}'s edit of the ward at {} refused: no network is called \"{}\"",
                            Trace.who(player), Trace.pos(pos), name);
                    AWLang.translate("message.ward_no_such_network", name).sendStatus(player);
                    return;
                }
                if (!named.contains(network.id())) {
                    named.add(network.id());
                }
            }
            ward.applyEdit(packet.radius(), packet.mode(), named, packet.enabled(), packet.selfTest());
            level.playSound(null, pos, AWSounds.ANCHOR_CONFIGURED.get(), SoundSource.BLOCKS, 0.7F, 0.8F);
            AWLang.translate("message.ward_saved").sendStatus(player);
        });
    }
}
