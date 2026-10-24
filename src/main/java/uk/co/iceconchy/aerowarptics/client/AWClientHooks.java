package uk.co.iceconchy.aerowarptics.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.airship.Fastening;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorBlockEntity;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.network.ClientboundCorridorPacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundAstrolabeChartPacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundDriveConsolePacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundGateDialPacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundWarpEffectPacket;
import uk.co.iceconchy.aerowarptics.client.fx.SummonBeacons;
import uk.co.iceconchy.aerowarptics.network.ClientboundRiftBeaconPacket;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;

/**
 * Dist-safe entry points into the client.
 *
 * <p>Common code calls these; nothing here touches a client class until the dist check has passed,
 * so a dedicated server never loads {@link ClientRuntime} or anything it references.
 */
public final class AWClientHooks {

    private AWClientHooks() {
    }

    private static boolean client() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }

    public static void requestRiftNavigation(RiftDriveBlockEntity drive) {
        if (client()) {
            ClientRuntime.requestRiftNavigation(drive);
        }
    }

    /** A player clicked a complete Astrolabe Cartography Table. */
    public static void requestAstrolabeChart(net.minecraft.core.BlockPos tablePos) {
        if (client()) {
            ClientRuntime.requestAstrolabeChart(tablePos);
        }
    }

    /**
     * A hull has jumped: move this client's copy of it, and its player if they went too, to the far side.
     *
     * <p>Without it Sable interpolates the teleport as movement - the hull is drawn part of the way
     * across, where there is no world loaded, and whoever is standing on it is thrown the same distance.
     */
    public static void foldCrossed(uk.co.iceconchy.aerowarptics.network.ClientboundFoldCrossedPacket packet) {
        if (client()) {
            ClientRuntime.foldCrossed(packet);
        }
    }

    /** This client's player has been put down: take back what the hull lent them, bar what it still has. */
    public static void settle(org.joml.Vector3dc hullVelocity) {
        if (client()) {
            ClientRuntime.settle(hullVelocity);
        }
    }

    /** A player clicked a Rift Probe. */
    public static void requestProbePanel(net.minecraft.core.BlockPos probePos) {
        if (client()) {
            ClientRuntime.requestProbePanel(probePos);
        }
    }

    public static void acceptProbePanel(uk.co.iceconchy.aerowarptics.network.ClientboundProbePacket packet) {
        if (client()) {
            ClientRuntime.acceptProbePanel(packet);
        }
    }

    /** Keeps a Rift Chute's aperture alive on the client, from the chute's own client tick. */
    public static void tickChuteAperture(uk.co.iceconchy.aerowarptics.chute.RiftChuteBlockEntity chute) {
        if (client()) {
            ClientRuntime.tickChuteAperture(chute);
        }
    }

    /** A player clicked a Rift Chute. */
    public static void requestChutePanel(net.minecraft.core.BlockPos chutePos) {
        if (client()) {
            ClientRuntime.requestChutePanel(chutePos);
        }
    }

    public static void acceptChutePanel(
            uk.co.iceconchy.aerowarptics.network.ClientboundChutePanelPacket packet) {
        if (client()) {
            ClientRuntime.acceptChutePanel(packet);
        }
    }

    /** A player clicked a Rift Coupler. */
    public static void requestCouplerPanel(net.minecraft.core.BlockPos couplerPos) {
        if (client()) {
            ClientRuntime.requestCouplerPanel(couplerPos);
        }
    }

    public static void acceptCouplerPanel(
            uk.co.iceconchy.aerowarptics.network.ClientboundCouplerPanelPacket packet) {
        if (client()) {
            ClientRuntime.acceptCouplerPanel(packet);
        }
    }

    public static void acceptMuster(uk.co.iceconchy.aerowarptics.network.ClientboundMusterPacket packet) {
        if (client()) {
            ClientRuntime.acceptMuster(packet);
        }
    }

    public static void acceptFlotillaCue(uk.co.iceconchy.aerowarptics.network.ClientboundFlotillaCuePacket packet) {
        if (client()) {
            ClientRuntime.acceptFlotillaCue(packet);
        }
    }

    /** A player clicked a Rift Modulator. */
    public static void requestModulatorPanel(net.minecraft.core.BlockPos modulatorPos) {
        if (client()) {
            ClientRuntime.requestModulatorPanel(modulatorPos);
        }
    }

    public static void acceptModulatorPanel(
            uk.co.iceconchy.aerowarptics.network.ClientboundModulatorPanelPacket packet) {
        if (client()) {
            ClientRuntime.acceptModulatorPanel(packet);
        }
    }

    public static void acceptProbeReading(
            uk.co.iceconchy.aerowarptics.network.ClientboundProbeReadingPacket packet) {
        if (client()) {
            ClientRuntime.acceptProbeReading(packet);
        }
    }

    /** A player clicked a Rift Gate's controller. */
    public static void requestGateDial(net.minecraft.core.BlockPos gatePos) {
        if (client()) {
            ClientRuntime.requestGateDial(gatePos);
        }
    }

    public static void acceptGateDial(ClientboundGateDialPacket packet) {
        if (client()) {
            ClientRuntime.acceptGateDial(packet);
        }
    }

    /**
     * Somebody has gone into a gate passage, or come out of one. For this client's own player that is the
     * cue to draw it, or to let the far side through; for anybody else, whether to draw them at all.
     */
    public static void gatePassage(uk.co.iceconchy.aerowarptics.network.ClientboundGatePassagePacket packet) {
        if (client()) {
            ClientRuntime.gatePassage(packet);
        }
    }

    /** The player's networks, and perhaps one of them in full, for whichever panel asked. */
    public static void acceptNetworkPanel(uk.co.iceconchy.aerowarptics.network.ClientboundNetworkPanelPacket packet) {
        if (client()) {
            ClientRuntime.acceptNetworkPanel(packet);
        }
    }

    public static void openWarpAnchorScreen(WarpAnchorBlockEntity anchor) {
        if (client()) {
            ClientRuntime.openWarpAnchorScreen(anchor);
        }
    }

    public static void acceptDriveConsole(ClientboundDriveConsolePacket packet) {
        if (client()) {
            ClientRuntime.acceptDriveConsole(packet);
        }
    }

    public static void acceptAstrolabeChart(ClientboundAstrolabeChartPacket packet) {
        if (client()) {
            ClientRuntime.acceptAstrolabeChart(packet);
        }
    }

    public static void acceptDestinationPreview(ClientboundAstrolabeChartPacket.Preview packet) {
        if (client()) {
            ClientRuntime.acceptDestinationPreview(packet);
        }
    }

    public static void playWarpEffect(ClientboundWarpEffectPacket packet) {
        if (client()) {
            ClientRuntime.playWarpEffect(packet);
        }
    }

    /** The server says how this player's ship's cruise is going. */
    public static void acceptSupercruise(uk.co.iceconchy.aerowarptics.network.ClientboundSupercruisePacket packet) {
        if (client()) {
            ClientRuntime.acceptSupercruise(packet);
        }
    }

    /** The server has put this player in, or taken them out of, a warp corridor. */
    public static void setInWarpCorridor(ClientboundCorridorPacket packet) {
        if (client()) {
            ClientRuntime.setInWarpCorridor(packet);
        }
    }

    /** Show this player the departure clearance a drive just measured for them. */
    public static void showClearance(
            uk.co.iceconchy.aerowarptics.network.ClientboundClearancePacket packet) {
        if (client()) {
            ClientRuntime.showClearance(packet);
        }
    }

    /** Ambient warp visuals, driven from the drive's own client tick. */
    public static void tickDriveEffects(RiftDriveBlockEntity drive) {
        if (client()) {
            ClientRuntime.tickDriveEffects(drive);
        }
    }

    public static void showWarpFeedback(WarpFailure failure, @Nullable Fastening fastening) {
        if (client()) {
            ClientRuntime.showWarpFeedback(failure, fastening);
        }
    }

    /** The server says whether it is tracing, and so whether this client should trace its own side. */
    public static void serverTracing(boolean tracing) {
        if (client()) {
            ClientTraceReport.serverTracing(tracing);
        }
    }

    /**
     * Keeps a Rift Fissure's tear alive on the client, from the fissure's own client tick.
     *
     * <p>Nothing is sent from the server to make a fissure visible. Whether a player can see one is a
     * fact about what is on their head, which is a question only their own client can answer - and
     * answering it here rather than server-side is also what stops a fissure's position being
     * broadcast to a client that has no business knowing it is there.
     */
    public static void tickFissure(uk.co.iceconchy.aerowarptics.fissure.RiftFissureBlockEntity fissure) {
        if (client()) {
            ClientRuntime.tickFissure(fissure);
        }
    }

    /** Follows a Collapse Cradle's ritual on the client: the rift, the hole, the column, the sound of it. */
    public static void tickCradle(uk.co.iceconchy.aerowarptics.collapse.CollapseCradleBlockEntity cradle) {
        if (client()) {
            ClientRuntime.tickCradle(cradle);
        }
    }

    /** The Collapse Cradle panel this player has open, as the server last saw it. */
    public static void acceptCradlePanel(uk.co.iceconchy.aerowarptics.collapse.CradlePanel panel) {
        if (client()) {
            CradlePanels.accept(panel);
        }
    }

    /** A player clicked a Rift Plotter: ask the server for its table. */
    public static void requestPlotterPanel(net.minecraft.core.BlockPos plotterPos) {
        if (client()) {
            ClientRuntime.requestPlotterPanel(plotterPos);
        }
    }

    /** A Rift Plotter's table, as the server has the machine. */
    public static void acceptPlotterPanel(uk.co.iceconchy.aerowarptics.network.ClientboundPlotterPanelPacket packet) {
        if (client()) {
            ClientRuntime.acceptPlotterPanel(packet);
        }
    }

    /** A model of the ground round a Rift Plotter's target, for its table or its projection. */
    public static void acceptPlotterSurvey(uk.co.iceconchy.aerowarptics.network.ClientboundPlotterSurveyPacket packet) {
        if (client()) {
            PlotterSurveyCache.accept(packet);
        }
    }

    /** Follows a Rift Plotter on the client: its entry rift, its beam, the sound of it charging. */
    public static void tickPlotter(uk.co.iceconchy.aerowarptics.plotter.RiftPlotterBlockEntity plotter) {
        if (client()) {
            ClientRuntime.tickPlotter(plotter);
        }
    }

    /** A player clicked a Berth: open its panel on the berth as this client has it. */
    public static void openBerthPanel(uk.co.iceconchy.aerowarptics.berth.BerthBlockEntity berth) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientRuntime.openBerthPanel(berth);
        }
    }

    /** A player clicked a Rift Ward: open its panel on the machine as this client has it. */
    public static void openWardPanel(uk.co.iceconchy.aerowarptics.ward.RiftWardBlockEntity ward) {
        if (client()) {
            ClientRuntime.openWardPanel(ward);
        }
    }

    /** Follows a Rift Ward on the client: the motes at its rim. */
    public static void tickWard(uk.co.iceconchy.aerowarptics.ward.RiftWardBlockEntity ward) {
        if (client()) {
            ClientRuntime.tickWard(ward);
        }
    }

    /** A Rift Ward is turning a rift aside, or failing to. */
    public static void wardCue(uk.co.iceconchy.aerowarptics.network.ClientboundWardCuePacket packet) {
        if (client()) {
            ClientRuntime.wardCue(packet);
        }
    }

    /** A Rift Wound standing in a client's world, from its own tick: keeps what is drawn of it alive. */
    public static void tickWound(net.minecraft.core.BlockPos pos) {
        if (client()) {
            uk.co.iceconchy.aerowarptics.client.fx.WellheadSpectacle.tickWound(pos);
        }
    }

    /** Where a Rift Wellhead's meltdown has got to. */
    /** Where a Rift Sunderer's tear has got to, for a player near enough to be shown it. */
    public static void sundererCue(uk.co.iceconchy.aerowarptics.network.ClientboundSundererCuePacket packet) {
        if (client()) {
            uk.co.iceconchy.aerowarptics.client.fx.SundererSpectacle.accept(packet);
        }
    }

    public static void meltdownCue(uk.co.iceconchy.aerowarptics.network.ClientboundMeltdownCuePacket packet) {
        if (client()) {
            uk.co.iceconchy.aerowarptics.client.fx.WellheadSpectacle.accept(packet);
        }
    }

    /** The unsettled ground a meltdown left, near this player. */
    public static void scarZone(uk.co.iceconchy.aerowarptics.network.ClientboundScarZonePacket packet) {
        if (client()) {
            uk.co.iceconchy.aerowarptics.client.fx.WellheadSpectacle.accept(packet);
        }
    }

    /** A Rift Wellhead's panel, as the server has it: opened by a click on the machine, or brought up to date. */
    public static void wellheadPanel(uk.co.iceconchy.aerowarptics.network.ClientboundWellheadPanelPacket packet) {
        if (client()) {
            uk.co.iceconchy.aerowarptics.client.screen.RiftWellheadScreen.accept(packet);
        }
    }

    /** Outlines the room a Collapse Cradle needed where it could not be set down. */
    public static void showCradleSpace(net.minecraft.core.BlockPos master) {
        if (client()) {
            CradleOutline.showSpace(master);
        }
    }

    /** A Collapse Cradle this client was watching has collapsed: the flash, the ring of air, the jolt. */
    public static void cradleCollapsed(uk.co.iceconchy.aerowarptics.collapse.CollapseCradleBlockEntity cradle) {
        if (client()) {
            ClientRuntime.cradleCollapsed(cradle);
        }
    }

    /** Keeps a lit Rift Brazier's small rift open over its bowl, from the brazier's own client tick. */
    public static void tickBrazier(uk.co.iceconchy.aerowarptics.rifting.RiftBrazierBlockEntity brazier) {
        if (client()) {
            ClientRuntime.tickBrazier(brazier);
        }
    }

    /** The steam a Cooling Vent forces out while its drive cools, from the vent's own client tick. */
    public static void tickCoolingVent(uk.co.iceconchy.aerowarptics.cooling.CoolingVentBlockEntity vent) {
        if (client()) {
            ClientRuntime.tickCoolingVent(vent);
        }
    }

    /** Ambient sparks around a fissure, drawn only for a player wearing the goggles. */
    public static void animateFissure(net.minecraft.world.level.Level level,
                                      net.minecraft.core.BlockPos pos,
                                      net.minecraft.util.RandomSource random) {
        if (client()) {
            ClientRuntime.animateFissure(level, pos, random);
        }
    }

    /** Essence being drawn into a Spatial Siphon, from the siphon's own client tick while it fills. */
    public static void animateSiphonDraw(net.minecraft.world.level.Level level,
                                         net.minecraft.core.BlockPos pos,
                                         net.minecraft.util.RandomSource random) {
        if (client()) {
            ClientRuntime.animateSiphonDraw(level, pos, random);
        }
    }

    /**
     * Whether the player at this client is looking through a rift-infused lens.
     *
     * <p>Asked by a fissure before it says anything to a goggle tooltip. Create offers that tooltip to
     * anyone wearing <em>any</em> registered goggles, an engineer's plain pair included, so a fissure
     * has to check for itself which lens is being worn - the tear, its sparks and its hit box all
     * already do, and the readout was the one place that did not.
     */
    public static boolean seesFissures() {
        return client() && ClientRuntime.seesFissures();
    }

    /**
     * A player has read the Navigator's Handbook.
     *
     * <p>The only entry point here with no packet behind it: the book says the same thing on every
     * world, so there is nothing for the server to answer with.
     */
    /** Lights the beam and throws the shockwave where a ship has just been called. */
    public static void markSummon(ClientboundRiftBeaconPacket packet) {
        if (client()) {
            SummonBeacons.mark(packet);
        }
    }

    /** A Rift Storm has begun or passed, or this client has just joined a server with one raging. */
    public static void setRiftStorm(boolean raging) {
        if (client()) {
            uk.co.iceconchy.aerowarptics.trace.ClientTrace.log(uk.co.iceconchy.aerowarptics.trace.ClientTrace.STORM,
                    "the server says a rift storm is {}", raging ? "raging" : "over");
            uk.co.iceconchy.aerowarptics.client.fx.RiftStormSky.set(raging);
        }
    }

    /** What a Stormglass reads here, by ordinal - clear on a dedicated server, which never asks. */
    public static int stormReading() {
        return client() ? ClientStormReading.get().ordinal() : 0;
    }

    /** What a Stormglass reads on this server, by {@link uk.co.iceconchy.aerowarptics.weather.StormForecast.Reading} ordinal. */
    public static void setStormReading(int reading) {
        if (client()) {
            ClientStormReading.set(reading);
        }
    }

    public static void openHandbook() {
        if (client()) {
            ClientRuntime.openHandbook();
        }
    }
}
