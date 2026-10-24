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
import uk.co.iceconchy.aerowarptics.astrolabe.DestinationSurvey;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;
import uk.co.iceconchy.aerowarptics.warp.WarpQuote;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkOption;

import java.util.List;
import java.util.UUID;

/**
 * The Astrolabe's chart: every destination the ship could be sent to, priced by the drive it carries.
 *
 * <p>Because every number here was produced server-side, the chart is a pure view: it cannot offer a
 * destination the server would refuse, and the only things it ever sends back are an anchor id and
 * whether to hold altitude.
 *
 * <p>The preview arrives separately. Surveying terrain is much the most expensive part of drawing this
 * screen, so it is done once for whichever destination is actually being looked at rather than for all
 * of them up front.
 *
 * @param astrolabePos the table this chart belongs to
 * @param tierIndex    tier of the drive on this hull, for the range readout
 * @param charge       that drive's stored charge
 * @param maximumRange how far it can reach with nothing to carry, in blocks
 * @param shipRange    how far it can throw this ship on a full charge, in blocks - the figure the quotes
 *                     below are refused against. {@code -1} when the ship is too heavy to jump at all,
 *                     {@link ClientboundDriveConsolePacket#NO_SHIP} when there is no ship or no drive
 * @param airshipName  name of the airship, blank when unnamed
 * @param hasDrive     whether the hull carries a drive at all
 * @param access       why the chart is unusable, or {@link WarpFailure#NONE}
 * @param selected     the course the table is already set to, so the chart opens on it
 * @param courseFixElsewhere whether that fix is in another dimension, as a Rift Probe's sounding can be
 * @param holdAltitude whether the drive's standing course holds the ship's altitude; false with no course
 * @param totalAvailable how many anchors the player may see under the filter, which is more than
 *                       {@code quotes} holds when the chart had to be capped to fit a packet
 * @param quotes       one entry per anchor the player may see under the filter, best first once capped
 * @param filter       the network the list is filtered to, or {@code null} for every destination. What the
 *                     server applied, which is not always what was asked for: a network that has gone is
 *                     answered with everything, and the chart's button has to follow
 * @param networks     the networks the list can be filtered by, by name
 */
public record ClientboundAstrolabeChartPacket(BlockPos astrolabePos,
                                              int tierIndex,
                                              float charge,
                                              double maximumRange,
                                              double shipRange,
                                              String airshipName,
                                              boolean hasDrive,
                                              WarpFailure access,
                                              @Nullable UUID selected,
                                              @Nullable BlockPos courseFix,
                                              boolean courseFixElsewhere,
                                              String courseLabel,
                                              boolean holdAltitude,
                                              int totalAvailable,
                                              List<WarpQuote> quotes,
                                              @Nullable UUID filter,
                                              List<NetworkOption> networks) implements CustomPacketPayload {

    /** Whether there is a ship under the drive, and so a range of its own to show beside the tier's. */
    public boolean hasShipRange() {
        return shipRange > ClientboundDriveConsolePacket.NO_SHIP + 0.5D;
    }

    /** Whether there were more destinations than the chart could be sent. */
    public boolean truncated() {
        return totalAvailable > quotes.size();
    }

    /**
     * Whether the ship is aimed at somewhere no anchor marks.
     *
     * <p>A Rift Probe can set the drive's course to a bare position, and when it has, none of the
     * anchors in this list is the destination. The chart used to go on highlighting whichever anchor
     * it had last been shown, which made it quietly wrong about where the ship was going - the one
     * thing a chart must not be.
     */
    public boolean courseIsFix() {
        return courseFix != null;
    }

    public boolean hasCourse() {
        return selected != null || courseFix != null;
    }

    public static final Type<ClientboundAstrolabeChartPacket> TYPE =
            new Type<>(AeroWarptics.id("astrolabe_chart"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundAstrolabeChartPacket> STREAM_CODEC =
            StreamCodec.of(ClientboundAstrolabeChartPacket::encode, ClientboundAstrolabeChartPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ClientboundAstrolabeChartPacket packet) {
        buf.writeBlockPos(packet.astrolabePos);
        buf.writeVarInt(packet.tierIndex);
        buf.writeFloat(packet.charge);
        buf.writeDouble(packet.maximumRange);
        buf.writeDouble(packet.shipRange);
        buf.writeUtf(packet.airshipName, 64);
        buf.writeBoolean(packet.hasDrive);
        buf.writeEnum(packet.access);
        buf.writeBoolean(packet.selected != null);
        if (packet.selected != null) {
            buf.writeUUID(packet.selected);
        }
        buf.writeBoolean(packet.courseFix != null);
        if (packet.courseFix != null) {
            buf.writeBlockPos(packet.courseFix);
        }
        buf.writeBoolean(packet.courseFixElsewhere);
        buf.writeUtf(packet.courseLabel, 64);
        buf.writeBoolean(packet.holdAltitude);
        buf.writeVarInt(packet.totalAvailable);
        WarpQuote.STREAM_CODEC.apply(ByteBufCodecs.list(PacketLists.MAX_ROWS))
                .encode(buf, packet.quotes);
        buf.writeBoolean(packet.filter != null);
        if (packet.filter != null) {
            buf.writeUUID(packet.filter);
        }
        NetworkOption.STREAM_CODEC.apply(ByteBufCodecs.list(PacketLists.MAX_ROWS))
                .encode(buf, packet.networks);
    }

    private static ClientboundAstrolabeChartPacket decode(RegistryFriendlyByteBuf buf) {
        return new ClientboundAstrolabeChartPacket(
                buf.readBlockPos(),
                buf.readVarInt(),
                buf.readFloat(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readUtf(64),
                buf.readBoolean(),
                buf.readEnum(WarpFailure.class),
                buf.readBoolean() ? buf.readUUID() : null,
                buf.readBoolean() ? buf.readBlockPos() : null,
                buf.readBoolean(),
                buf.readUtf(64),
                buf.readBoolean(),
                buf.readVarInt(),
                WarpQuote.STREAM_CODEC.apply(ByteBufCodecs.list(PacketLists.MAX_ROWS)).decode(buf),
                buf.readBoolean() ? buf.readUUID() : null,
                NetworkOption.STREAM_CODEC.apply(ByteBufCodecs.list(PacketLists.MAX_ROWS)).decode(buf));
    }

    public static void handle(ClientboundAstrolabeChartPacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> AWClientHooks.acceptAstrolabeChart(packet));
    }

    /**
     * A destination's terrain, sent on its own once the player looks at one.
     *
     * @param anchorId which destination this is a picture of, so a late reply cannot be drawn under
     *                 the wrong heading after the player has moved on
     */
    public record Preview(UUID anchorId, DestinationSurvey survey) implements CustomPacketPayload {

        public static final Type<Preview> TYPE = new Type<>(AeroWarptics.id("astrolabe_preview"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Preview> STREAM_CODEC = StreamCodec.of(
                (buf, packet) -> {
                    buf.writeUUID(packet.anchorId);
                    DestinationSurvey.STREAM_CODEC.encode(buf, packet.survey);
                },
                buf -> new Preview(buf.readUUID(), DestinationSurvey.STREAM_CODEC.decode(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Preview packet, IPayloadContext context) {
            if (FMLEnvironment.dist != Dist.CLIENT) {
                return;
            }
            context.enqueueWork(() -> AWClientHooks.acceptDestinationPreview(packet));
        }
    }
}
