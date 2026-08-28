package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;
import uk.co.iceconchy.aerowarptics.modulator.RiftModulatorBlockEntity;

/**
 * A Rift Modulator's panel, as the server sees it.
 *
 * <p>Carries enum ordinals rather than translated text for the linked drive's tier and state, the same
 * choice {@code RiftGateDialScreen} makes for a gate's own state - the client already has
 * {@code AWLang} and the enums to resolve them with, and sending raw text would mean this screen stops
 * matching the player's language the moment they change it.
 *
 * @param linked         whether a Rift Drive was found beside the Modulator
 * @param active         whether the Modulator is currently affording its upkeep and so actually
 *                       overriding the drive's colours right now
 * @param driveTierIndex the linked drive's tier, or -1 when {@code linked} is false
 * @param driveState     the linked drive's state ordinal, or -1 when {@code linked} is false
 * @param driveLabel     the linked drive's committed destination, or empty when there is none
 */
public record ClientboundModulatorPanelPacket(BlockPos modulatorPos,
                                              int colour,
                                              int accentColour,
                                              int themeOrdinal,
                                              float intensity,
                                              boolean linked,
                                              boolean active,
                                              int essence,
                                              int capacity,
                                              int upkeepCost,
                                              int upkeepInterval,
                                              int driveTierIndex,
                                              int driveState,
                                              String driveLabel) implements CustomPacketPayload {

    public static final Type<ClientboundModulatorPanelPacket> TYPE =
            new Type<>(AeroWarptics.id("modulator_panel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundModulatorPanelPacket> STREAM_CODEC =
            StreamCodec.of(ClientboundModulatorPanelPacket::encode, ClientboundModulatorPanelPacket::decode);

    public static void sendTo(ServerPlayer player, RiftModulatorBlockEntity modulator) {
        RiftDriveBlockEntity drive = modulator.linkedDrive();
        String label = drive == null ? "" : drive.destinationLabel();
        AWNetwork.sendTo(player, new ClientboundModulatorPanelPacket(
                modulator.getBlockPos(),
                modulator.colour(),
                modulator.accentColour(),
                modulator.theme().ordinal(),
                modulator.intensity(),
                drive != null,
                modulator.active(),
                modulator.contents().getAmount(),
                RiftModulatorBlockEntity.CAPACITY,
                AWConfig.MODULATOR_UPKEEP_COST.get(),
                AWConfig.MODULATOR_UPKEEP_INTERVAL.get(),
                drive == null ? -1 : drive.tier().index(),
                drive == null ? -1 : drive.state().ordinal(),
                label == null ? "" : label));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** The linked drive's tier, or the mod's baseline tier when there is nothing to show. */
    public RiftDriveTier driveTier() {
        return RiftDriveTier.byIndex(Math.max(0, driveTierIndex));
    }

    public uk.co.iceconchy.aerowarptics.drive.RiftDriveState driveStateValue() {
        return uk.co.iceconchy.aerowarptics.drive.RiftDriveState.byIndex(driveState);
    }

    private static void encode(RegistryFriendlyByteBuf buf, ClientboundModulatorPanelPacket packet) {
        buf.writeBlockPos(packet.modulatorPos);
        buf.writeInt(packet.colour);
        buf.writeInt(packet.accentColour);
        buf.writeVarInt(packet.themeOrdinal);
        buf.writeFloat(packet.intensity);
        buf.writeBoolean(packet.linked);
        buf.writeBoolean(packet.active);
        buf.writeVarInt(packet.essence);
        buf.writeVarInt(packet.capacity);
        buf.writeVarInt(packet.upkeepCost);
        buf.writeVarInt(packet.upkeepInterval);
        buf.writeVarInt(packet.driveTierIndex);
        buf.writeVarInt(packet.driveState);
        buf.writeUtf(packet.driveLabel == null ? "" : packet.driveLabel, 64);
    }

    private static ClientboundModulatorPanelPacket decode(RegistryFriendlyByteBuf buf) {
        return new ClientboundModulatorPanelPacket(
                buf.readBlockPos(),
                buf.readInt(),
                buf.readInt(),
                buf.readVarInt(),
                buf.readFloat(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readUtf(64));
    }

    public static void handle(ClientboundModulatorPanelPacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> AWClientHooks.acceptModulatorPanel(packet));
    }
}
