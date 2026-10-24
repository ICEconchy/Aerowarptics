package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.probe.ProbeBearing;
import uk.co.iceconchy.aerowarptics.probe.ProbeDimensions;
import uk.co.iceconchy.aerowarptics.probe.ProbeSounding;
import uk.co.iceconchy.aerowarptics.probe.ProbeState;
import uk.co.iceconchy.aerowarptics.probe.ProbeVerdict;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.probe.RiftProbeBlockEntity;
import uk.co.iceconchy.aerowarptics.warp.ArrivalHeight;
import uk.co.iceconchy.aerowarptics.warp.WarpCourse;

import java.util.ArrayList;
import java.util.List;

/**
 * A Rift Probe's panel, as the server sees it.
 *
 * <p>Everything here is small enough to resend twice a second. The reading itself - a survey of some
 * thousands of blocks of ground - travels separately in {@link ClientboundProbeReadingPacket}, once,
 * when there is a new one. Sending it on every refresh would make watching a probe cost more than
 * using one, which is the same mistake the Astrolabe's chart was careful to avoid.
 *
 * @param verdictIndex what the reading amounts to, or {@code -1} when there is no reading
 * @param groundY      surface height at the fix, meaningless without a reading
 * @param coverage     how much of the reading actually came back, 0..1
 * @param arrivalHeight        blocks above the ground a ship sent from here would come in at
 * @param maximumArrivalHeight the server's ceiling on that, for the slider's far end
 * @param dimensions           every dimension this probe may scan, its own first - see
 *                             {@link ProbeDimensions#offered}. Never empty: one entry is a probe kept
 *                             to where it stands
 * @param dimensionIndex       which of those it is aimed at
 */
public record ClientboundProbePacket(BlockPos probePos,
                                     List<ResourceLocation> dimensions,
                                     int dimensionIndex,
                                     int bearingIndex,
                                     int range,
                                     int minimumRange,
                                     int maximumRange,
                                     int arrivalHeight,
                                     int maximumArrivalHeight,
                                     int stateIndex,
                                     float reachProgress,
                                     int essence,
                                     int capacity,
                                     int cost,
                                     boolean hasDrive,
                                     boolean aboard,
                                     int verdictIndex,
                                     int groundY,
                                     float coverage,
                                     String readingLabel,
                                     boolean isCourse) implements CustomPacketPayload {

    public static final Type<ClientboundProbePacket> TYPE = new Type<>(AeroWarptics.id("probe_panel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundProbePacket> STREAM_CODEC =
            StreamCodec.of(ClientboundProbePacket::encode, ClientboundProbePacket::decode);

    public static ClientboundProbePacket of(RiftProbeBlockEntity probe, ServerPlayer player) {
        ProbeSounding reading = probe.sounding();
        // Whether the drive is aimed at this very reading. Without it the panel could only offer to
        // set a course and never say that it had, which made the button feel like it did nothing.
        RiftDriveBlockEntity drive = probe.drive();
        WarpCourse course = drive == null ? null : drive.standingCourse();
        // The height counts as part of "this very reading": a course sent at six blocks is not the one
        // the slider now says forty, and the button has to come back so the pilot can send it again.
        // A course an Astrolabe has told to hold altitude ignores the slider altogether, so it is not
        // this reading either. Nor is the same numbers in another dimension: a fix that names none is in
        // the drive's own, which is the probe's.
        boolean isCourse = reading != null && reading.usable() && course != null && course.isFix()
                && course.fix().equals(reading.fix())
                && (course.dimension() == null ? !probe.readingElsewhere()
                        : course.dimension().equals(probe.readingDimension()))
                && !course.holdAltitude()
                && ArrivalHeight.resolve(course.arrivalHeight()) == probe.arrivalHeight();
        List<ResourceLocation> dimensions = new ArrayList<>();
        int dimensionIndex = 0;
        if (probe.getLevel() instanceof ServerLevel here) {
            ResourceKey<Level> aimed = probe.soundedDimension();
            for (ResourceKey<Level> offered : ProbeDimensions.offered(here)) {
                if (offered.equals(aimed)) {
                    dimensionIndex = dimensions.size();
                }
                dimensions.add(offered.location());
            }
        }
        return new ClientboundProbePacket(
                probe.getBlockPos(),
                List.copyOf(dimensions),
                dimensionIndex,
                probe.bearing().index(),
                probe.range(),
                RiftProbeBlockEntity.minimumRange(),
                RiftProbeBlockEntity.maximumRange(),
                probe.arrivalHeight(),
                ArrivalHeight.maximum(),
                probe.state().index(),
                probe.reachProgress(),
                probe.essence(),
                RiftProbeBlockEntity.fullSupply(),
                probe.cost(),
                probe.drive() != null,
                probe.airship() != null,
                reading == null ? -1 : reading.verdict().ordinal(),
                reading == null ? 0 : reading.groundY(),
                reading == null ? 0.0F : reading.survey().coverage(),
                // The sounding's own label, not the course's: the dimension is on the dial above it, and
                // the line it is drawn on has no room to say it twice.
                reading == null ? "" : reading.label(),
                isCourse);
    }

    /** The dimension the probe is aimed at, or {@code null} for a packet that somehow names none. */
    @Nullable
    public ResourceLocation dimension() {
        return dimensions.isEmpty() ? null : dimensions.get(Math.max(0, Math.min(dimensions.size() - 1, dimensionIndex)));
    }

    /** Whether there is anywhere else to aim: with one dimension on offer the dial is a label. */
    public boolean canChangeDimension() {
        return dimensions.size() > 1;
    }

    public ProbeBearing bearing() {
        return ProbeBearing.byIndex(bearingIndex);
    }

    public ProbeState state() {
        return ProbeState.byIndex(stateIndex);
    }

    public boolean hasReading() {
        return verdictIndex >= 0;
    }

    public ProbeVerdict verdict() {
        return ProbeVerdict.values()[Math.max(0, verdictIndex)];
    }

    /** Whether the probe could pay for a sounding at the current settings. */
    public boolean affordable() {
        return essence >= cost;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ClientboundProbePacket packet) {
        buf.writeBlockPos(packet.probePos);
        ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(ProbeDimensions.MAX_LISTED)).encode(buf, packet.dimensions);
        buf.writeVarInt(packet.dimensionIndex);
        buf.writeVarInt(packet.bearingIndex);
        buf.writeVarInt(packet.range);
        buf.writeVarInt(packet.minimumRange);
        buf.writeVarInt(packet.maximumRange);
        buf.writeVarInt(packet.arrivalHeight);
        buf.writeVarInt(packet.maximumArrivalHeight);
        buf.writeVarInt(packet.stateIndex);
        buf.writeFloat(packet.reachProgress);
        buf.writeVarInt(packet.essence);
        buf.writeVarInt(packet.capacity);
        buf.writeVarInt(packet.cost);
        buf.writeBoolean(packet.hasDrive);
        buf.writeBoolean(packet.aboard);
        buf.writeVarInt(packet.verdictIndex + 1);
        buf.writeInt(packet.groundY);
        buf.writeFloat(packet.coverage);
        buf.writeUtf(packet.readingLabel, 64);
        buf.writeBoolean(packet.isCourse);
    }

    private static ClientboundProbePacket decode(RegistryFriendlyByteBuf buf) {
        return new ClientboundProbePacket(
                buf.readBlockPos(),
                // Bounded by the codec: a list longer than a panel is ever sent is refused, not allocated.
                ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(ProbeDimensions.MAX_LISTED)).decode(buf),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readFloat(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readVarInt() - 1,
                buf.readInt(),
                buf.readFloat(),
                buf.readUtf(64),
                buf.readBoolean());
    }

    public static void handle(ClientboundProbePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> AWClientHooks.acceptProbePanel(packet));
    }
}
