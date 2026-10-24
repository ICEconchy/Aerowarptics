package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.plotter.Firing;
import uk.co.iceconchy.aerowarptics.plotter.Plot;
import uk.co.iceconchy.aerowarptics.plotter.PlotterSurveys;
import uk.co.iceconchy.aerowarptics.plotter.RiftPlotterBlockEntity;
import uk.co.iceconchy.aerowarptics.trace.Trace;

/**
 * An order from a Rift Plotter's table.
 *
 * <p>Every order but {@link Action#OPEN} and {@link Action#REFRESH} carries the whole plot and firing as the table has them,
 * because the table is where it is edited: the server clamps it, keeps it, and answers with the machine as it now
 * stands. A plot the machine will not take - it is busy - is simply not taken, and the answer shows the one it has.
 *
 * <p>Honoured only for a player standing within reach of the machine, as a chest is. The table is the permission:
 * there is no owner, because a weapon anyone can walk up to is a weapon anyone can walk up to.
 */
public record ServerboundPlotterPacket(BlockPos pos, Action action, Plot plot, Firing firing) implements CustomPacketPayload {

    public enum Action {
        /** The table was opened: answer with the machine, and open the table. */
        OPEN,
        /** The table is still open: answer with the machine. */
        REFRESH,
        /** Keep this plot. */
        SET,
        /** Keep this plot and send back a model of the ground round its target, loading it if it must. */
        SURVEY,
        /** Keep this plot and begin a strike on it. */
        ENGAGE,
        /** Stand the strike under way down. */
        STAND_DOWN
    }

    public static final Type<ServerboundPlotterPacket> TYPE = new Type<>(AeroWarptics.id("plotter"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundPlotterPacket> STREAM_CODEC =
            StreamCodec.of(ServerboundPlotterPacket::encode, ServerboundPlotterPacket::decode);

    public static ServerboundPlotterPacket open(BlockPos pos) {
        return new ServerboundPlotterPacket(pos, Action.OPEN, Plot.NONE, Firing.DEFAULT);
    }

    public static ServerboundPlotterPacket refresh(BlockPos pos) {
        return new ServerboundPlotterPacket(pos, Action.REFRESH, Plot.NONE, Firing.DEFAULT);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ServerboundPlotterPacket packet) {
        buf.writeBlockPos(packet.pos);
        buf.writeEnum(packet.action);
        Plot.STREAM_CODEC.encode(buf, packet.plot);
        Firing.STREAM_CODEC.encode(buf, packet.firing);
    }

    private static ServerboundPlotterPacket decode(RegistryFriendlyByteBuf buf) {
        return new ServerboundPlotterPacket(buf.readBlockPos(), buf.readEnum(Action.class), Plot.STREAM_CODEC.decode(buf),
                Firing.STREAM_CODEC.decode(buf));
    }

    public static void handle(ServerboundPlotterPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            RiftPlotterBlockEntity plotter = resolve(player, level, packet.pos);
            if (plotter == null) {
                return;
            }
            switch (packet.action) {
                case OPEN, REFRESH -> {
                }
                case SET -> {
                    plotter.setPlot(packet.plot);
                    plotter.setFiring(packet.firing);
                }
                case SURVEY -> {
                    plotter.setPlot(packet.plot);
                    plotter.setFiring(packet.firing);
                    // Of the plot the machine now holds: a busy one keeps the plot it is firing on, and the model
                    // is of that plot's ground rather than the one the table asked about.
                    Plot surveyed = plotter.plot();
                    PlotterSurveys.request(player, level, plotter.getBlockPos(), surveyed.targetX(), surveyed.targetZ());
                }
                case ENGAGE -> {
                    plotter.setPlot(packet.plot);
                    plotter.setFiring(packet.firing);
                    plotter.engage(player);
                }
                case STAND_DOWN -> plotter.standDown(player);
            }
            AWNetwork.sendTo(player, ClientboundPlotterPanelPacket.of(plotter, packet.action == Action.OPEN));
        });
    }

    @Nullable
    private static RiftPlotterBlockEntity resolve(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof RiftPlotterBlockEntity plotter)) {
            return null;
        }
        // The table is three blocks across, so reach is measured to its middle with the machine's half-width added.
        double reach = AWConfig.MAX_INTERACTION_DISTANCE.get() + 4.0D;
        if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 1.5D, pos.getZ() + 0.5D) > reach * reach) {
            Trace.changed("plotter-reach:" + player.getUUID(), Trace.PLOTTER,
                    "{} sent an order to the plotter at {} from out of reach - ignored", Trace.who(player), Trace.pos(pos));
            return null;
        }
        return plotter;
    }
}
