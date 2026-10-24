package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.plotter.Firing;
import uk.co.iceconchy.aerowarptics.plotter.Plot;
import uk.co.iceconchy.aerowarptics.plotter.PlotFailure;
import uk.co.iceconchy.aerowarptics.plotter.PlotGeometry;
import uk.co.iceconchy.aerowarptics.plotter.PlotRules;
import uk.co.iceconchy.aerowarptics.plotter.PlotterState;
import uk.co.iceconchy.aerowarptics.plotter.PlotterSurveys;
import uk.co.iceconchy.aerowarptics.plotter.RiftPlotterBlockEntity;

/**
 * What a Rift Plotter's table shows: the machine as the server has it, and the numbers the table needs to price
 * and check a plot as it is dragged about - which are the server's config, and so travel here rather than being
 * read on the client.
 *
 * @param open            whether this answer should open the table, rather than only update one already open
 * @param preview         what the machine would say to Engage right now, for the plot it holds
 * @param price           what the plot it holds would cost, as the server prices it
 * @param surveyRadius    blocks the model of the ground reaches from the target
 * @param maxWindow       the server's longest strike, in seconds, which the table's window slider stops at
 * @param maxExitSpeed    the server's fastest exit, in blocks a second
 * @param upkeep          what holding the plot open as a lane costs a second, in millibuckets
 * @param dockReach       how deep the server makes a dock nobody has sized, in blocks
 * @param maxDockRadius   the furthest from its rift's axis the server lets a dock be set to reach, in blocks
 * @param maxDockDepth    the furthest behind its entry the server lets a dock be set to reach, in blocks
 */
public record ClientboundPlotterPanelPacket(BlockPos pos, boolean open, int state, int stateTicks, int openTicks,
                                            long charge, long cost, int essence, int capacity, float speed,
                                            double minimumSpeed, int failure, int preview, Plot plot, int carried,
                                            double maxRadius, int surveyRadius, int baseCost,
                                            int perKm, int perRadius, int drawRate, boolean enabled,
                                            boolean carriesEntities, long price, Firing firing, boolean laneOpen,
                                            int maxWindow, double maxExitSpeed, long upkeep, boolean dockHolding,
                                            boolean dockLinedUp, int dockReach, int maxDockRadius,
                                            int maxDockDepth)
        implements CustomPacketPayload {

    public static final Type<ClientboundPlotterPanelPacket> TYPE = new Type<>(AeroWarptics.id("plotter_panel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundPlotterPanelPacket> STREAM_CODEC =
            StreamCodec.of(ClientboundPlotterPanelPacket::encode, ClientboundPlotterPanelPacket::decode);

    /** The machine as it stands, for the player at its table. */
    public static ClientboundPlotterPanelPacket of(RiftPlotterBlockEntity plotter, boolean open) {
        ServerLevel level = (ServerLevel) plotter.getLevel();
        Plot plot = plotter.plot();
        PlotFailure preview = level == null ? PlotFailure.NONE : PlotRules.preflight(new PlotRules.Situation(
                AWConfig.PLOTTER_ENABLED.get(), plotter.state().busy(), plot.set(), plotter.turning(),
                level.getWorldBorder().isWithinBounds(plot.targetX(), plot.targetZ()),
                PlotGeometry.exitCentre(PlotGeometry.target(plot), plot).y, level.getMinBuildHeight(),
                level.getMaxBuildHeight()));
        return new ClientboundPlotterPanelPacket(plotter.getBlockPos(), open, plotter.state().ordinal(),
                plotter.stateTicks(), plotter.openTicks(), plotter.charge(), plotter.cost(),
                plotter.contents().getAmount(), RiftPlotterBlockEntity.TANK_CAPACITY, plotter.getSpeed(),
                AWConfig.PLOTTER_MINIMUM_SPEED.get(), plotter.lastFailure().ordinal(), preview.ordinal(), plot,
                plotter.carried(), RiftPlotterBlockEntity.maxRadius(), PlotterSurveys.radius(), AWConfig.PLOTTER_BASE_COST.get(), AWConfig.PLOTTER_COST_PER_KM.get(),
                AWConfig.PLOTTER_COST_PER_RADIUS.get(), AWConfig.PLOTTER_DRAW_RATE.get(),
                AWConfig.PLOTTER_ENABLED.get(), AWConfig.PLOTTER_CARRIES_ENTITIES.get(), plotter.price(),
                plotter.firing(), plotter.laneOpen(), AWConfig.PLOTTER_MAX_OPEN_SECONDS.get(),
                AWConfig.PLOTTER_MAX_EXIT_SPEED.get(), plotter.upkeep(), plotter.dockHolding(), plotter.dockLinedUp(),
                AWConfig.PLOTTER_DOCK_REACH.get(), AWConfig.PLOTTER_MAX_DOCK_RADIUS.get(),
                AWConfig.PLOTTER_MAX_DOCK_DEPTH.get());
    }

    public PlotterState plotterState() {
        return PlotterState.byIndex(state);
    }

    public PlotFailure lastFailure() {
        return PlotFailure.byIndex(failure);
    }

    public PlotFailure previewFailure() {
        return PlotFailure.byIndex(preview);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ClientboundPlotterPanelPacket packet) {
        buf.writeBlockPos(packet.pos);
        buf.writeBoolean(packet.open);
        buf.writeVarInt(packet.state);
        buf.writeVarInt(packet.stateTicks);
        buf.writeVarInt(packet.openTicks);
        buf.writeVarLong(packet.charge);
        buf.writeVarLong(packet.cost);
        buf.writeVarInt(packet.essence);
        buf.writeVarInt(packet.capacity);
        buf.writeFloat(packet.speed);
        buf.writeDouble(packet.minimumSpeed);
        buf.writeVarInt(packet.failure);
        buf.writeVarInt(packet.preview);
        Plot.STREAM_CODEC.encode(buf, packet.plot);
        buf.writeVarInt(packet.carried);
        buf.writeDouble(packet.maxRadius);
        buf.writeVarInt(packet.surveyRadius);
        buf.writeVarInt(packet.baseCost);
        buf.writeVarInt(packet.perKm);
        buf.writeVarInt(packet.perRadius);
        buf.writeVarInt(packet.drawRate);
        buf.writeBoolean(packet.enabled);
        buf.writeBoolean(packet.carriesEntities);
        buf.writeVarLong(packet.price);
        Firing.STREAM_CODEC.encode(buf, packet.firing);
        buf.writeBoolean(packet.laneOpen);
        buf.writeVarInt(packet.maxWindow);
        buf.writeDouble(packet.maxExitSpeed);
        buf.writeVarLong(packet.upkeep);
        buf.writeBoolean(packet.dockHolding);
        buf.writeBoolean(packet.dockLinedUp);
        buf.writeVarInt(packet.dockReach);
        buf.writeVarInt(packet.maxDockRadius);
        buf.writeVarInt(packet.maxDockDepth);
    }

    private static ClientboundPlotterPanelPacket decode(RegistryFriendlyByteBuf buf) {
        return new ClientboundPlotterPanelPacket(buf.readBlockPos(), buf.readBoolean(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), buf.readVarInt(),
                buf.readVarInt(), buf.readFloat(), buf.readDouble(), buf.readVarInt(), buf.readVarInt(),
                Plot.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readDouble(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                buf.readBoolean(), buf.readVarLong(), Firing.STREAM_CODEC.decode(buf), buf.readBoolean(),
                buf.readVarInt(), buf.readDouble(), buf.readVarLong(), buf.readBoolean(), buf.readBoolean(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(ClientboundPlotterPanelPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> AWClientHooks.acceptPlotterPanel(packet));
    }
}
