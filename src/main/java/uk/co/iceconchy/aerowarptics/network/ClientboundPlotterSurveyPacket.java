package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.plotter.PlotterSurvey;

/**
 * The ground round a Rift Plotter's target, for the model on its table.
 *
 * <p>Up to a couple of hundred kilobytes for the widest survey, which is why it is sent once per look, to the one
 * player looking, and never on a timer. {@link PlotterSurvey}'s decoder bounds what it will allocate.
 */
public record ClientboundPlotterSurveyPacket(BlockPos plotter, PlotterSurvey survey) implements CustomPacketPayload {

    public static final Type<ClientboundPlotterSurveyPacket> TYPE = new Type<>(AeroWarptics.id("plotter_survey"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundPlotterSurveyPacket> STREAM_CODEC =
            StreamCodec.of(ClientboundPlotterSurveyPacket::encode, ClientboundPlotterSurveyPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ClientboundPlotterSurveyPacket packet) {
        buf.writeBlockPos(packet.plotter);
        PlotterSurvey.STREAM_CODEC.encode(buf, packet.survey);
    }

    private static ClientboundPlotterSurveyPacket decode(RegistryFriendlyByteBuf buf) {
        return new ClientboundPlotterSurveyPacket(buf.readBlockPos(), PlotterSurvey.STREAM_CODEC.decode(buf));
    }

    public static void handle(ClientboundPlotterSurveyPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> AWClientHooks.acceptPlotterSurvey(packet));
    }
}
