package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;
import uk.co.iceconchy.aerowarptics.warp.WarpQuote;

import java.util.List;

/**
 * The complete, server-computed contents of the Rift Navigation screen.
 *
 * <p>Because every number here was produced server-side, the screen is a pure view: it cannot show a
 * destination as reachable when the server would refuse it.
 *
 * @param drivePos     the drive this data belongs to
 * @param tierIndex    drive tier, for the title and range readout
 * @param stateIndex   current {@code RiftDriveState}
 * @param charge       0..1 stored charge
 * @param cooldown     remaining cooldown ticks
 * @param maximumRange furthest this drive can reach, in blocks
 * @param airshipName  name of the airship, blank when unnamed
 * @param airshipMass  Sable's mass for the airship, for display
 * @param heading      the drive's bearing setting, so the console can show and change it
 * @param bearing      the heading actually resolved for this airship right now, as a compass point
 * @param access       why the console is unusable, or {@link WarpFailure#NONE}
 * @param quotes       one entry per anchor the player may see
 */
public record ClientboundNavigationDataPacket(BlockPos drivePos,
                                              int tierIndex,
                                              int stateIndex,
                                              float charge,
                                              int cooldown,
                                              double maximumRange,
                                              String airshipName,
                                              double airshipMass,
                                              int heading,
                                              String bearing,
                                              WarpFailure access,
                                              List<WarpQuote> quotes) implements CustomPacketPayload {

    public static final Type<ClientboundNavigationDataPacket> TYPE =
            new Type<>(AeroWarptics.id("navigation_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundNavigationDataPacket> STREAM_CODEC =
            StreamCodec.of(ClientboundNavigationDataPacket::encode, ClientboundNavigationDataPacket::decode);

    public static ClientboundNavigationDataPacket of(RiftDriveBlockEntity drive, @Nullable Airship airship,
                                                     List<WarpQuote> quotes, WarpFailure access) {
        String name = airship == null || airship.name() == null ? "" : airship.name();
        double mass = airship == null ? 0.0D : airship.mass();
        return new ClientboundNavigationDataPacket(
                drive.getBlockPos(),
                drive.tier().index(),
                drive.state().ordinal(),
                drive.charge(),
                drive.cooldownTicks(),
                drive.tier().maximumRange(),
                name,
                mass,
                drive.heading().ordinal(),
                drive.resolvedBearing(airship),
                access,
                quotes);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ClientboundNavigationDataPacket packet) {
        buf.writeBlockPos(packet.drivePos);
        buf.writeVarInt(packet.tierIndex);
        buf.writeVarInt(packet.stateIndex);
        buf.writeFloat(packet.charge);
        buf.writeVarInt(packet.cooldown);
        buf.writeDouble(packet.maximumRange);
        buf.writeUtf(packet.airshipName, 64);
        buf.writeDouble(packet.airshipMass);
        buf.writeVarInt(packet.heading);
        buf.writeUtf(packet.bearing, 16);
        buf.writeEnum(packet.access);
        WarpQuote.STREAM_CODEC.apply(ByteBufCodecs.list(256)).encode(buf, packet.quotes);
    }

    private static ClientboundNavigationDataPacket decode(RegistryFriendlyByteBuf buf) {
        return new ClientboundNavigationDataPacket(
                buf.readBlockPos(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readFloat(),
                buf.readVarInt(),
                buf.readDouble(),
                buf.readUtf(64),
                buf.readDouble(),
                buf.readVarInt(),
                buf.readUtf(16),
                buf.readEnum(WarpFailure.class),
                WarpQuote.STREAM_CODEC.apply(ByteBufCodecs.list(256)).decode(buf));
    }

    public static void handle(ClientboundNavigationDataPacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> AWClientHooks.acceptNavigationData(packet));
    }
}
