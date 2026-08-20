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

import java.util.List;
import java.util.UUID;

/**
 * The Astrolabe's chart: every destination the ship could be sent to, priced by the drive it carries.
 *
 * <p>Because every number here was produced server-side, the chart is a pure view: it cannot offer a
 * destination the server would refuse, and the only thing it ever sends back is an anchor id.
 *
 * <p>The preview arrives separately. Surveying terrain is much the most expensive part of drawing this
 * screen, so it is done once for whichever destination is actually being looked at rather than for all
 * of them up front.
 *
 * @param astrolabePos the table this chart belongs to
 * @param tierIndex    tier of the drive on this hull, for the range readout
 * @param charge       that drive's stored charge
 * @param maximumRange how far it can reach, in blocks
 * @param airshipName  name of the airship, blank when unnamed
 * @param hasDrive     whether the hull carries a drive at all
 * @param access       why the chart is unusable, or {@link WarpFailure#NONE}
 * @param selected     the course the table is already set to, so the chart opens on it
 * @param quotes       one entry per anchor the player may see
 */
public record ClientboundAstrolabeChartPacket(BlockPos astrolabePos,
                                              int tierIndex,
                                              float charge,
                                              double maximumRange,
                                              String airshipName,
                                              boolean hasDrive,
                                              WarpFailure access,
                                              @Nullable UUID selected,
                                              List<WarpQuote> quotes) implements CustomPacketPayload {

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
        buf.writeUtf(packet.airshipName, 64);
        buf.writeBoolean(packet.hasDrive);
        buf.writeEnum(packet.access);
        buf.writeBoolean(packet.selected != null);
        if (packet.selected != null) {
            buf.writeUUID(packet.selected);
        }
        WarpQuote.STREAM_CODEC.apply(ByteBufCodecs.list(256)).encode(buf, packet.quotes);
    }

    private static ClientboundAstrolabeChartPacket decode(RegistryFriendlyByteBuf buf) {
        return new ClientboundAstrolabeChartPacket(
                buf.readBlockPos(),
                buf.readVarInt(),
                buf.readFloat(),
                buf.readDouble(),
                buf.readUtf(64),
                buf.readBoolean(),
                buf.readEnum(WarpFailure.class),
                buf.readBoolean() ? buf.readUUID() : null,
                WarpQuote.STREAM_CODEC.apply(ByteBufCodecs.list(256)).decode(buf));
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
