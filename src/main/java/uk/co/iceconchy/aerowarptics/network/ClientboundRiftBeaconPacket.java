package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;

/**
 * A ship has been called to a spot, and everyone nearby should see where.
 *
 * <p>Sent once, at the moment the summon is accepted, and then not mentioned again. The beam it
 * lights burns for a fixed time of its own rather than being held up by a stream of keep-alives -
 * the same decision the rest of this mod's effects make, for the same reason. A marker that needs
 * the server to keep saying "still going" is a marker that hangs forever the moment one packet is
 * dropped, and this one stands in the open where everybody can see it do that.
 *
 * @param target where the ship was called to
 * @param ticks  how long the beam stands
 * @param tier   the calling drive's tier, which colours the beam the way it colours its own rift
 */
public record ClientboundRiftBeaconPacket(BlockPos target, int ticks, int tier)
        implements CustomPacketPayload {

    public static final Type<ClientboundRiftBeaconPacket> TYPE = new Type<>(AeroWarptics.id("rift_beacon"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundRiftBeaconPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeBlockPos(packet.target);
                        buf.writeVarInt(packet.ticks);
                        buf.writeVarInt(packet.tier);
                    },
                    buf -> new ClientboundRiftBeaconPacket(
                            buf.readBlockPos(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Tells everyone near the summon site, rather than everyone near the ship being called. */
    public static void broadcast(ServerLevel level, BlockPos target, RiftDriveBlockEntity drive) {
        AWNetwork.sendToTracking(level, Vec3.atCenterOf(target), new ClientboundRiftBeaconPacket(
                target, AWConfig.BEACON_BEAM_TICKS.get(), drive.tier().index()));
    }

    public static void handle(ClientboundRiftBeaconPacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> AWClientHooks.markSummon(packet));
    }
}
