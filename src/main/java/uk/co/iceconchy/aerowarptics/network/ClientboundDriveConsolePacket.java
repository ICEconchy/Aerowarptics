package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchor;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorRegistry;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;
import uk.co.iceconchy.aerowarptics.warp.WarpValidator;

import java.util.UUID;

/**
 * Everything the Rift Drive's console shows: what the machine is doing, and what it is still waiting
 * for.
 *
 * <p>The console has no destination list and no launch button. Choosing where to go is the Astrolabe
 * Cartography Table's job and firing the jump is the redstone input's, which leaves this screen with
 * exactly one purpose - telling a player why the drive is not going anywhere yet. Every requirement it
 * draws is answered here, on the server, from the same values the warp sequence itself reads, so the
 * panel cannot say a drive is ready when the drive would refuse.
 *
 * @param drivePos      the drive this belongs to
 * @param tierIndex     drive tier, for the title and the range readout
 * @param stateIndex    current {@code RiftDriveState}
 * @param charge        0..1 stored charge
 * @param spinProgress  0..1 through the spin-up, meaningful only while stabilising
 * @param cooldown      remaining cooldown ticks
 * @param maximumRange  furthest this drive can reach, in blocks
 * @param currentRpm    what the shaft is actually turning at
 * @param requiredRpm   what it has to turn at before the drive will charge
 * @param stressImpact  Create stress units per RPM, for the readout
 * @param airshipName   name of the airship, blank when unnamed
 * @param airshipMass   Sable's mass for the airship
 * @param heading       the drive's bow setting
 * @param bearing       the compass point that setting currently resolves to
 * @param access        why the console is unusable, or {@link WarpFailure#NONE}
 * @param courseName    display name of the standing course, blank when none is set
 * @param courseFailure why that course could not be flown right now
 * @param redstoneAllowed whether the server permits a redstone input to start a warp at all
 */
public record ClientboundDriveConsolePacket(BlockPos drivePos,
                                            int tierIndex,
                                            int stateIndex,
                                            float charge,
                                            float spinProgress,
                                            int cooldown,
                                            double maximumRange,
                                            float currentRpm,
                                            int requiredRpm,
                                            float stressImpact,
                                            String airshipName,
                                            double airshipMass,
                                            int heading,
                                            String bearing,
                                            WarpFailure access,
                                            String courseName,
                                            WarpFailure courseFailure,
                                            boolean redstoneAllowed) implements CustomPacketPayload {

    public static final Type<ClientboundDriveConsolePacket> TYPE =
            new Type<>(AeroWarptics.id("drive_console"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundDriveConsolePacket> STREAM_CODEC =
            StreamCodec.of(ClientboundDriveConsolePacket::encode, ClientboundDriveConsolePacket::decode);

    public static ClientboundDriveConsolePacket of(RiftDriveBlockEntity drive, @Nullable Airship airship,
                                                   WarpFailure access) {
        String name = airship == null || airship.name() == null ? "" : airship.name();
        double mass = airship == null ? 0.0D : airship.mass();

        String courseName = "";
        WarpFailure courseFailure = WarpFailure.NONE;
        UUID course = drive.standingDestination();
        if (course == null) {
            courseFailure = WarpFailure.ANCHOR_MISSING;
        } else if (drive.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            WarpAnchor anchor = WarpAnchorRegistry.get(level).byId(course);
            if (anchor == null) {
                courseFailure = WarpFailure.ANCHOR_MISSING;
            } else {
                courseName = anchor.displayName();
                courseFailure = airship == null
                        ? WarpFailure.NO_AIRSHIP
                        : WarpValidator.validateDestination(airship, anchor, drive.tier(), drive.charge());
            }
        }

        return new ClientboundDriveConsolePacket(
                drive.getBlockPos(),
                drive.tier().index(),
                drive.state().ordinal(),
                drive.charge(),
                drive.sequenceProgress(),
                drive.cooldownTicks(),
                drive.tier().maximumRange(),
                Math.abs(drive.getSpeed()),
                drive.tier().minimumRpm(),
                (float) drive.tier().stressImpact(),
                name,
                mass,
                drive.heading().ordinal(),
                drive.resolvedBearing(airship),
                access,
                courseName,
                courseFailure,
                AWConfig.ALLOW_REDSTONE_INITIATION.get());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ClientboundDriveConsolePacket packet) {
        buf.writeBlockPos(packet.drivePos);
        buf.writeVarInt(packet.tierIndex);
        buf.writeVarInt(packet.stateIndex);
        buf.writeFloat(packet.charge);
        buf.writeFloat(packet.spinProgress);
        buf.writeVarInt(packet.cooldown);
        buf.writeDouble(packet.maximumRange);
        buf.writeFloat(packet.currentRpm);
        buf.writeVarInt(packet.requiredRpm);
        buf.writeFloat(packet.stressImpact);
        buf.writeUtf(packet.airshipName, 64);
        buf.writeDouble(packet.airshipMass);
        buf.writeVarInt(packet.heading);
        buf.writeUtf(packet.bearing, 16);
        buf.writeEnum(packet.access);
        buf.writeUtf(packet.courseName, 64);
        buf.writeEnum(packet.courseFailure);
        buf.writeBoolean(packet.redstoneAllowed);
    }

    private static ClientboundDriveConsolePacket decode(RegistryFriendlyByteBuf buf) {
        return new ClientboundDriveConsolePacket(
                buf.readBlockPos(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readVarInt(),
                buf.readDouble(),
                buf.readFloat(),
                buf.readVarInt(),
                buf.readFloat(),
                buf.readUtf(64),
                buf.readDouble(),
                buf.readVarInt(),
                buf.readUtf(16),
                buf.readEnum(WarpFailure.class),
                buf.readUtf(64),
                buf.readEnum(WarpFailure.class),
                buf.readBoolean());
    }

    /** Whether a course is set at all, as opposed to set and currently unflyable. */
    public boolean hasCourse() {
        return !courseName.isBlank();
    }

    public static void handle(ClientboundDriveConsolePacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> AWClientHooks.acceptDriveConsole(packet));
    }
}
