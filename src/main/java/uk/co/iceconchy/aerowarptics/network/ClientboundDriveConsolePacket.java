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
import uk.co.iceconchy.aerowarptics.airship.Fastening;
import uk.co.iceconchy.aerowarptics.airship.Fastenings;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchor;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorRegistry;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.warp.WarpCourse;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;
import uk.co.iceconchy.aerowarptics.warp.WarpValidator;

import java.util.Optional;
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
 * @param maximumRange  furthest this drive can reach with nothing to carry, in blocks
 * @param shipRange     how far it can throw the ship it is aboard on a full charge, in blocks: {@code -1}
 *                      when the ship is too heavy for it to jump at all, {@link #NO_SHIP} off a ship
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
 * @param modeIndex     the drive's {@code DriveMode}
 * @param cruisePhaseIndex where a cruise under way is in letting go - {@code CruisePhase.NONE} otherwise
 * @param cruiseDistance how far a cruise in flight has flown, in blocks
 * @param cruiseReach   how far a cruise may fly on its charge, in blocks
 * @param cruiseFailure why a cruise could not be engaged on the charge in hand, or {@link WarpFailure#NONE}
 * @param cruiseSpeed   how fast a cruise in flight is going, in blocks per second
 * @param cruiseSight   what it can see ahead, as a {@code CruiseSensor.Limit} ordinal
 * @param clearanceChecks whether the drive looks for terrain before it warps - off only when its pilot
 *                      has switched them off at this console
 * @param fastening     what ties the ship down, if anything does - asked afresh with every refresh, so the
 *                      row goes red the moment a Docking Connector locks, before anybody pulls a lever
 */
public record ClientboundDriveConsolePacket(BlockPos drivePos,
                                            int tierIndex,
                                            int stateIndex,
                                            float charge,
                                            float spinProgress,
                                            int cooldown,
                                            double maximumRange,
                                            double shipRange,
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
                                            boolean redstoneAllowed,
                                            int modeIndex,
                                            int cruisePhaseIndex,
                                            double cruiseDistance,
                                            double cruiseReach,
                                            WarpFailure cruiseFailure,
                                            double cruiseSpeed,
                                            int cruiseSight,
                                            boolean clearanceChecks,
                                            Optional<Fastening> fastening) implements CustomPacketPayload {

    /** {@link #shipRange} for a drive that is not aboard a ship: there is no hull to weigh. */
    public static final double NO_SHIP = -2.0D;

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
        WarpCourse course = drive.standingCourse();
        if (course == null) {
            courseFailure = WarpFailure.ANCHOR_MISSING;
        } else if (drive.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            if (course.isAnchor()) {
                WarpAnchor anchor = WarpAnchorRegistry.get(level).byId(course.anchorId());
                if (anchor == null) {
                    courseFailure = WarpFailure.ANCHOR_MISSING;
                } else {
                    // Read fresh rather than from the course: an anchor can be renamed after a
                    // course is set, and a console showing the old name is quietly lying.
                    courseName = anchor.displayName();
                    courseFailure = airship == null
                            ? WarpFailure.NO_AIRSHIP
                            : WarpValidator.validateDestination(airship, anchor, drive.tier(), drive.charge());
                }
            } else {
                courseName = course.label();
                courseFailure = airship == null
                        ? WarpFailure.NO_AIRSHIP
                        : WarpValidator.validateFix(airship, course, drive.tier(), drive.charge());
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
                airship == null ? NO_SHIP : WarpValidator.reach(airship, drive.tier()),
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
                AWConfig.ALLOW_REDSTONE_INITIATION.get(),
                drive.mode().ordinal(),
                drive.cruisePhase().ordinal(),
                drive.cruiseDistance(),
                drive.cruiseReach(),
                drive.cruiseAffordability(),
                drive.cruiseSpeedPerSecond(),
                drive.cruiseSight().ordinal(),
                drive.clearanceChecks(),
                airship == null ? Optional.empty() : Fastenings.holding(airship));
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
        buf.writeDouble(packet.shipRange);
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
        buf.writeVarInt(packet.modeIndex);
        buf.writeVarInt(packet.cruisePhaseIndex);
        buf.writeDouble(packet.cruiseDistance);
        buf.writeDouble(packet.cruiseReach);
        buf.writeEnum(packet.cruiseFailure);
        buf.writeDouble(packet.cruiseSpeed);
        buf.writeVarInt(packet.cruiseSight);
        buf.writeBoolean(packet.clearanceChecks);
        Fastening.OPTIONAL_STREAM_CODEC.encode(buf, packet.fastening);
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
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readEnum(WarpFailure.class),
                buf.readDouble(),
                buf.readVarInt(),
                buf.readBoolean(),
                Fastening.OPTIONAL_STREAM_CODEC.decode(buf));
    }

    /** Whether the drive is aboard a ship, and so has a range of its own to show. */
    public boolean hasShipRange() {
        return shipRange > NO_SHIP + 0.5D;
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
