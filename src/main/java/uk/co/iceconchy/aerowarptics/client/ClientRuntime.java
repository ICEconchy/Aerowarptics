package uk.co.iceconchy.aerowarptics.client;

import net.createmod.catnip.gui.ScreenOpener;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.airship.Fastening;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorBlockEntity;
import uk.co.iceconchy.aerowarptics.client.fx.WarpCorridorOverlay;
import uk.co.iceconchy.aerowarptics.client.fx.WarpEffects;
import uk.co.iceconchy.aerowarptics.client.screen.AstrolabeChartScreen;
import uk.co.iceconchy.aerowarptics.client.screen.RiftDriveConsoleScreen;
import uk.co.iceconchy.aerowarptics.client.fx.RiftEffectManager;
import uk.co.iceconchy.aerowarptics.client.screen.NetworkScreen;
import uk.co.iceconchy.aerowarptics.client.screen.RiftGateDialScreen;
import uk.co.iceconchy.aerowarptics.client.screen.WarpAnchorScreen;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.network.ClientboundCorridorPacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundAstrolabeChartPacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundGateDialPacket;
import uk.co.iceconchy.aerowarptics.network.ServerboundGatePacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundDriveConsolePacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundWarpEffectPacket;
import uk.co.iceconchy.aerowarptics.network.ServerboundAstrolabePacket;
import uk.co.iceconchy.aerowarptics.network.ServerboundDriveConsolePacket;
import uk.co.iceconchy.aerowarptics.trace.ClientTrace;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;

/**
 * Client-only implementation behind {@link AWClientHooks}.
 *
 * <p>The client owns nothing authoritative. Opening a console or a chart is a <em>request</em>;
 * the screen only appears once the server has answered with the data to draw.
 */
@OnlyIn(Dist.CLIENT)
final class ClientRuntime {

    private ClientRuntime() {
    }

    static void requestRiftNavigation(RiftDriveBlockEntity drive) {
        PacketDistributor.sendToServer(new ServerboundDriveConsolePacket(drive.getBlockPos()));
    }

    static void requestAstrolabeChart(net.minecraft.core.BlockPos tablePos) {
        PacketDistributor.sendToServer(ServerboundAstrolabePacket.open(tablePos, null));
    }

    static void requestGateDial(net.minecraft.core.BlockPos gatePos) {
        PacketDistributor.sendToServer(ServerboundGatePacket.open(gatePos, null));
    }

    static void acceptGateDial(ClientboundGateDialPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof RiftGateDialScreen open && open.matches(packet.gatePos())) {
            open.accept(packet);
            return;
        }
        // The members panel, opened from this gate's dial and standing in front of it. The dial sends what
        // was typed into it as it is covered, and the answer to that would otherwise find no dial on
        // screen, open a new one, and throw the player out of the panel they had just opened.
        if (minecraft.screen instanceof NetworkScreen panel
                && panel.parent() instanceof RiftGateDialScreen behind && behind.matches(packet.gatePos())) {
            behind.accept(packet);
            return;
        }
        ScreenOpener.open(new RiftGateDialScreen(packet));
    }

    static void gatePassage(uk.co.iceconchy.aerowarptics.network.ClientboundGatePassagePacket packet) {
        ClientTrace.log(ClientTrace.CUE, "gate passage: {}", packet);
        uk.co.iceconchy.aerowarptics.client.fx.GatePassageOverlay.accept(packet);
    }

    static void acceptNetworkPanel(uk.co.iceconchy.aerowarptics.network.ClientboundNetworkPanelPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof NetworkScreen open) {
            open.accept(packet);
        } else if (minecraft.screen instanceof WarpAnchorScreen open) {
            open.acceptNetworks(packet);
        }
    }

    static void foldCrossed(uk.co.iceconchy.aerowarptics.network.ClientboundFoldCrossedPacket packet) {
        FoldCrossings.crossed(packet.shipId(), packet.jump(), packet.carry());
    }

    static void settle(org.joml.Vector3dc hullVelocity) {
        ClientTrace.log(ClientTrace.FOLD, "settled on deck: borrowed motion taken back, keeping the hull's {}",
                Trace.vec(hullVelocity));
        FoldCrossings.settle(hullVelocity);
    }

    static void requestProbePanel(net.minecraft.core.BlockPos probePos) {
        PacketDistributor.sendToServer(uk.co.iceconchy.aerowarptics.network.ServerboundProbePacket.open(probePos));
    }

    static void acceptProbePanel(uk.co.iceconchy.aerowarptics.network.ClientboundProbePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof uk.co.iceconchy.aerowarptics.client.screen.RiftProbeScreen open
                && open.matches(packet.probePos())) {
            open.accept(packet);
            return;
        }
        ScreenOpener.open(new uk.co.iceconchy.aerowarptics.client.screen.RiftProbeScreen(packet));
    }

    static void requestPlotterPanel(net.minecraft.core.BlockPos plotterPos) {
        PacketDistributor.sendToServer(uk.co.iceconchy.aerowarptics.network.ServerboundPlotterPacket.open(plotterPos));
    }

    static void acceptPlotterPanel(uk.co.iceconchy.aerowarptics.network.ClientboundPlotterPanelPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof uk.co.iceconchy.aerowarptics.client.screen.RiftPlotterScreen open
                && open.matches(packet.pos())) {
            open.accept(packet);
            return;
        }
        if (packet.open()) {
            ScreenOpener.open(new uk.co.iceconchy.aerowarptics.client.screen.RiftPlotterScreen(packet));
        }
    }

    static void tickPlotter(uk.co.iceconchy.aerowarptics.plotter.RiftPlotterBlockEntity plotter) {
        if (!inTheWorld(plotter) || dev.ryanhcode.sable.Sable.HELPER.isInPlotGrid(plotter)) {
            return;
        }
        uk.co.iceconchy.aerowarptics.client.fx.PlotterEffects.tick(plotter);
    }

    static void requestChutePanel(net.minecraft.core.BlockPos chutePos) {
        PacketDistributor.sendToServer(
                uk.co.iceconchy.aerowarptics.network.ServerboundChutePacket.open(chutePos));
    }

    static void acceptChutePanel(uk.co.iceconchy.aerowarptics.network.ClientboundChutePanelPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof uk.co.iceconchy.aerowarptics.client.screen.RiftChuteScreen open
                && open.matches(packet.chutePos())) {
            open.accept(packet);
            return;
        }
        ScreenOpener.open(new uk.co.iceconchy.aerowarptics.client.screen.RiftChuteScreen(packet));
    }

    static void requestCouplerPanel(net.minecraft.core.BlockPos couplerPos) {
        PacketDistributor.sendToServer(
                uk.co.iceconchy.aerowarptics.network.ServerboundCouplerPacket.open(couplerPos));
    }

    /**
     * The coupler's panel: the Fleet Board for a flagship, the Station screen for an escort. A panel that
     * arrives for a coupler whose screen is already open updates it in place, and one whose role has
     * changed underneath it swaps to the other screen.
     */
    static void acceptCouplerPanel(uk.co.iceconchy.aerowarptics.network.ClientboundCouplerPanelPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof uk.co.iceconchy.aerowarptics.client.screen.CouplerScreen open
                && open.matches(packet.couplerPos()) && open.shows(packet.role())) {
            open.accept(packet);
            return;
        }
        if (minecraft.screen instanceof uk.co.iceconchy.aerowarptics.client.screen.CouplerScreen open
                && !open.matches(packet.couplerPos())) {
            // A panel for some other coupler, arriving late: not the one this player is looking at.
            return;
        }
        ScreenOpener.open(packet.role() == uk.co.iceconchy.aerowarptics.flotilla.CouplerRole.FLAGSHIP
                ? new uk.co.iceconchy.aerowarptics.client.screen.FleetBoardScreen(packet)
                : new uk.co.iceconchy.aerowarptics.client.screen.StationScreen(packet));
    }

    static void acceptMuster(uk.co.iceconchy.aerowarptics.network.ClientboundMusterPacket packet) {
        uk.co.iceconchy.aerowarptics.client.fx.FlotillaEffects.accept(packet);
    }

    static void acceptFlotillaCue(uk.co.iceconchy.aerowarptics.network.ClientboundFlotillaCuePacket packet) {
        ClientTrace.log(ClientTrace.FLEET, "cue: {}", packet);
        uk.co.iceconchy.aerowarptics.client.fx.FlotillaEffects.cue(packet);
    }

    static void requestModulatorPanel(net.minecraft.core.BlockPos modulatorPos) {
        PacketDistributor.sendToServer(
                uk.co.iceconchy.aerowarptics.network.ServerboundModulatorPacket.open(modulatorPos));
    }

    static void acceptModulatorPanel(
            uk.co.iceconchy.aerowarptics.network.ClientboundModulatorPanelPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof uk.co.iceconchy.aerowarptics.client.screen.RiftModulatorScreen open
                && open.matches(packet.modulatorPos())) {
            open.accept(packet);
            return;
        }
        ScreenOpener.open(new uk.co.iceconchy.aerowarptics.client.screen.RiftModulatorScreen(packet));
    }

    static void acceptProbeReading(uk.co.iceconchy.aerowarptics.network.ClientboundProbeReadingPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof uk.co.iceconchy.aerowarptics.client.screen.RiftProbeScreen open
                && open.matches(packet.probePos())) {
            open.acceptReading(packet.sounding());
        }
    }

    /**
     * Whether a block entity is actually in the world the player is looking at.
     *
     * <p>Ponder runs scenes in a level of its own, and the block entities in it tick like any other -
     * so a Rift Chute sitting in a scene would otherwise ask for an aperture, and get one drawn in
     * the real world at the scene's coordinates. Ambient effects are for the world; a scene draws
     * itself.
     */
    private static boolean inTheWorld(net.minecraft.world.level.block.entity.BlockEntity block) {
        return block.getLevel() != null && block.getLevel() == Minecraft.getInstance().level;
    }

    /** Colour of a chute's aperture. The same violet the gates are lit in, so the family reads. */
    private static final int CHUTE_COLOUR = 0xA24BFF;

    /** Ticks a chute's aperture takes to shatter open - the same break a gate's does. */
    private static final int CHUTE_OPEN_TICKS = 30;

    /** Half-width of the pane, in blocks. Sized to sit inside the cage without touching the posts. */
    private static final double CHUTE_RADIUS = 0.28D;

    /**
     * Holds a Rift Chute's aperture open, and turns it to face the viewer.
     *
     * <p>The same aperture an airship flies through, down to the shatter and the seal - a chute is a
     * small hole in space and there is no reason for it to break differently from a large one. It is
     * billboarded rather than fixed, because the housing is open on all four sides and a pane with a
     * fixed normal would be edge-on and invisible from half of them.
     */
    static void tickChuteAperture(uk.co.iceconchy.aerowarptics.chute.RiftChuteBlockEntity chute) {
        if (!inTheWorld(chute)) {
            return;
        }
        long holder = chute.getBlockPos().asLong();
        if (!chute.isRiftOpen()) {
            // seal(), not release(): a chute running dry should shut the way a gate does, rather than
            // blinking out.
            RiftEffectManager.seal(holder);
            return;
        }
        net.minecraft.world.phys.Vec3 centre =
                net.minecraft.world.phys.Vec3.atCenterOf(chute.getBlockPos());
        net.minecraft.client.Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        net.minecraft.world.phys.Vec3 toViewer = camera.getPosition().subtract(centre);
        if (toViewer.lengthSqr() < 1.0e-6D) {
            toViewer = new net.minecraft.world.phys.Vec3(0.0D, 0.0D, 1.0D);
        }
        // No ground strike: a chute encompasses its own rift inside a cage, so a bolt straight down
        // would only ever hit the block it is housed in. See RiftEffectManager.ActiveRift.groundStrikes.
        RiftEffectManager.hold(holder, centre, toViewer.normalize(),
                CHUTE_RADIUS, CHUTE_RADIUS, CHUTE_COLOUR, CHUTE_OPEN_TICKS, false);
        RiftEffectManager.aim(holder, toViewer.normalize());
    }

    static void openBerthPanel(uk.co.iceconchy.aerowarptics.berth.BerthBlockEntity berth) {
        ScreenOpener.open(new uk.co.iceconchy.aerowarptics.client.screen.BerthScreen(berth));
    }

    static void openWardPanel(uk.co.iceconchy.aerowarptics.ward.RiftWardBlockEntity ward) {
        ScreenOpener.open(new uk.co.iceconchy.aerowarptics.client.screen.RiftWardScreen(ward));
    }

    static void tickWard(uk.co.iceconchy.aerowarptics.ward.RiftWardBlockEntity ward) {
        if (!inTheWorld(ward) || dev.ryanhcode.sable.Sable.HELPER.isInPlotGrid(ward)) {
            return;
        }
        uk.co.iceconchy.aerowarptics.client.fx.WardEffects.tick(ward);
    }

    static void wardCue(uk.co.iceconchy.aerowarptics.network.ClientboundWardCuePacket packet) {
        if (ClientTrace.enabled()) {
            ClientTrace.log(ClientTrace.WARD, "ward at {} {}: aimed {}, went {}, {}t, aboard={}", packet.ward(),
                    packet.kind(), packet.aimed(), packet.went(), packet.total(), packet.aboard());
        }
        uk.co.iceconchy.aerowarptics.client.fx.WardEffects.accept(packet);
    }

    static void openWarpAnchorScreen(WarpAnchorBlockEntity anchor) {
        ScreenOpener.open(new WarpAnchorScreen(anchor));
    }

    static void acceptDriveConsole(ClientboundDriveConsolePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof RiftDriveConsoleScreen open && open.matches(packet.drivePos())) {
            open.accept(packet);
            return;
        }
        ScreenOpener.open(new RiftDriveConsoleScreen(packet));
    }

    static void acceptAstrolabeChart(ClientboundAstrolabeChartPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof AstrolabeChartScreen open && open.matches(packet.astrolabePos())) {
            open.accept(packet);
            return;
        }
        ScreenOpener.open(new AstrolabeChartScreen(packet));
    }

    static void acceptDestinationPreview(ClientboundAstrolabeChartPacket.Preview packet) {
        if (Minecraft.getInstance().screen instanceof AstrolabeChartScreen open) {
            open.acceptPreview(packet);
        }
    }

    static void playWarpEffect(ClientboundWarpEffectPacket packet) {
        if (ClientTrace.enabled()) {
            traceCue(packet);
        }
        WarpEffects.onStage(packet);
    }

    /**
     * A rift cue as this client received it: where, how big, and whether this client even has the drive
     * and the ship it is about. A cue for a ship the client does not hold is the "ship vanished" report
     * from this side; one that arrives from further than the player can see explains a rift nobody saw.
     */
    private static void traceCue(ClientboundWarpEffectPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        net.minecraft.client.multiplayer.ClientLevel level = minecraft.level;
        boolean hasDrive = level != null && level.isLoaded(packet.drivePos())
                && level.getBlockEntity(packet.drivePos()) instanceof RiftDriveBlockEntity;
        dev.ryanhcode.sable.api.sublevel.SubLevelContainer container = level == null ? null
                : dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        boolean hasShip = container != null && container.getSubLevel(packet.airship()) != null;
        double away = minecraft.player == null || packet.centre() == null ? -1.0D
                : minecraft.player.position().distanceTo(packet.centre());
        ClientTrace.log(ClientTrace.CUE, "{} from drive {} ship {} at {} r={} aspect={} for {}t throat={} theme={}"
                        + " - {} blocks away, drive {}, ship {}", packet.stage(), Trace.pos(packet.drivePos()),
                packet.airship(), Trace.vec(packet.centre()), Trace.num(packet.radius()), Trace.num(packet.aspect()),
                packet.duration(), Trace.num(packet.throat()), packet.themeOverride(), Trace.num(away),
                hasDrive ? "held" : "NOT HELD", hasShip ? "held" : "NOT HELD");
    }

    static void setInWarpCorridor(ClientboundCorridorPacket packet) {
        ClientTrace.log(ClientTrace.FOLD, "corridor: {}", packet);
        WarpCorridorOverlay.accept(packet);
    }

    static void acceptSupercruise(uk.co.iceconchy.aerowarptics.network.ClientboundSupercruisePacket packet) {
        ClientTrace.log(ClientTrace.FOLD, "supercruise: {}", packet);
        uk.co.iceconchy.aerowarptics.client.fx.HyperspaceStreaks.accept(packet);
    }

    static void showClearance(uk.co.iceconchy.aerowarptics.network.ClientboundClearancePacket packet) {
        uk.co.iceconchy.aerowarptics.client.fx.ClearanceOverlay.show(packet);
    }

    static void tickDriveEffects(RiftDriveBlockEntity drive) {
        if (!inTheWorld(drive)) {
            return;
        }
        WarpEffects.tickDrive(drive);
    }

    /**
     * The colour a fissure burns.
     *
     * <p>Deeper than a gate's, and dimmer. A gate is a doorway somebody built and lit; a fissure is
     * an old wound in the same fabric, and it should read as the thing the machines were reverse
     * engineered from rather than as another machine.
     */
    private static final int FISSURE_COLOUR = 0x8B4FE0;

    /** Half-width of a fissure's tear at full size, in blocks. */
    private static final double FISSURE_RADIUS = 1.15D;

    /** Ticks a fissure's tear takes to shatter into view when the goggles go on. */
    private static final int FISSURE_OPEN_TICKS = 26;

    static void tickFissure(uk.co.iceconchy.aerowarptics.fissure.RiftFissureBlockEntity fissure) {
        if (!inTheWorld(fissure)) {
            return;
        }
        long holder = fissure.getBlockPos().asLong();
        Minecraft minecraft = Minecraft.getInstance();
        if (fissure.isSealing()) {
            RiftEffectManager.seal(holder);
            return;
        }
        // A tear a Rift Wellhead holds is drawn for everybody: the machine built round it gave it away already. So,
        // for a while, is one a Rift Sunderer has just torn: the scene that made it ends on it, goggles or none.
        long now = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        boolean revealed = now < fissure.revealedUntil();
        if (!fissure.isHarnessed() && !revealed
                && !uk.co.iceconchy.aerowarptics.fissure.RiftGogglesItem.isWorn(minecraft.player)) {
            // Not sealed and not released: simply stop renewing it. A held aperture nobody is
            // renewing collapses on its own after a moment, so taking the goggles off closes the
            // tear rather than deleting it - and putting them straight back on catches it before it
            // has gone anywhere.
            return;
        }
        net.minecraft.world.phys.Vec3 centre =
                net.minecraft.world.phys.Vec3.atCenterOf(fissure.getBlockPos());
        net.minecraft.world.phys.Vec3 toViewer =
                minecraft.gameRenderer.getMainCamera().getPosition().subtract(centre);
        if (toViewer.lengthSqr() < 1.0e-6D) {
            toViewer = new net.minecraft.world.phys.Vec3(0.0D, 0.0D, 1.0D);
        }
        // Facing the camera, for the same reason a chute's does: a fissure hangs in the middle of a
        // room with nothing to say which way it ought to lie, and a fixed plane would be edge-on and
        // invisible from half the places a player can stand.
        double radius = FISSURE_RADIUS * Math.max(0.05F, fissure.openness());
        if (revealed) {
            // Newborn: half again its size, settling to its own over its first four seconds.
            double age = uk.co.iceconchy.aerowarptics.sunderer.SundererBreach.REVEALED_FOR - (fissure.revealedUntil() - now);
            radius *= 1.0D + 0.5D * (1.0D - uk.co.iceconchy.aerowarptics.collapse.CollapseChoreography.smooth(age / 80.0D));
        }
        if (fissure.isConjured() && !fissure.isHarnessed() && fissure.unsettledUntil() >= 0L
                && minecraft.level != null) {
            // An unheld conjured tear trembles, and worse as it nears guttering: the last five minutes shake it
            // from a flicker to a stutter. Two unrelated sines, so it never settles into a beat.
            double left = (fissure.unsettledUntil() - minecraft.level.getGameTime()) / (20.0D * 300.0D);
            double urgency = 1.0D - Math.max(0.0D, Math.min(1.0D, left));
            double time = minecraft.level.getGameTime() + holder % 97L;
            double tremble = Math.sin(time * 0.9D) * 0.6D + Math.sin(time * 2.3D + 1.7D) * 0.4D;
            radius *= 1.0D + (0.04D + 0.22D * urgency) * tremble;
        }
        // A fissure is an open wound hanging over the ground it tore through, so it strikes it - unlike
        // a chute above. See RiftEffectManager.ActiveRift.groundStrikes.
        RiftEffectManager.hold(holder, centre, toViewer.normalize(), radius, radius,
                FISSURE_COLOUR, FISSURE_OPEN_TICKS, true);
        // A fissure is the one rift that gets built round: its light is drawn ahead of Warp Glass, not behind it.
        RiftEffectManager.underGlass(holder);
        RiftEffectManager.aim(holder, toViewer.normalize());
    }

    /**
     * A lit Rift Brazier's sparks: a few a second off the tips of its flames, and a burst as it catches. The
     * flames themselves are the brazier's renderer's, drawn in its own frame so they burn aboard a ship too;
     * a spark is a particle in the world, so aboard one it is thrown from where the ship puts the brazier.
     */
    static void tickBrazier(uk.co.iceconchy.aerowarptics.rifting.RiftBrazierBlockEntity brazier) {
        if (!inTheWorld(brazier)) {
            return;
        }
        float caught = brazier.flame(1.0F);
        if (caught <= 0.0F || !(brazier.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel level)) {
            return;
        }
        net.minecraft.util.RandomSource random = level.random;
        boolean burst = brazier.justCaught();
        double density = uk.co.iceconchy.aerowarptics.AWConfig.PARTICLE_DENSITY.get();
        int sparks = burst ? (int) Math.round(18 * density)
                : (random.nextDouble() < 0.35D * caught * density ? 1 : 0);
        boolean aboard = dev.ryanhcode.sable.Sable.HELPER.isInPlotGrid(brazier);
        java.util.List<uk.co.iceconchy.aerowarptics.rifting.RiftFlame.Tongue> tongues =
                uk.co.iceconchy.aerowarptics.rifting.RiftFlame.TONGUES;
        long time = level.getGameTime();
        for (int spark = 0; spark < sparks; spark++) {
            uk.co.iceconchy.aerowarptics.rifting.RiftFlame.Tongue tongue = tongues.get(random.nextInt(tongues.size()));
            double tip = uk.co.iceconchy.aerowarptics.rifting.RiftFlame.FLOOR
                    + uk.co.iceconchy.aerowarptics.rifting.RiftFlame.height(tongue, time, caught) * (burst ? 0.3D : 0.85D);
            net.minecraft.world.phys.Vec3 at = new net.minecraft.world.phys.Vec3(
                    brazier.getBlockPos().getX() + tongue.x(), brazier.getBlockPos().getY() + tip,
                    brazier.getBlockPos().getZ() + tongue.z());
            if (aboard) {
                at = uk.co.iceconchy.aerowarptics.client.fx.WarpEffects.toWorld(level, at);
            }
            double spread = burst ? 0.18D : 0.02D;
            level.addParticle(uk.co.iceconchy.aerowarptics.registry.AWParticles.RIFT_SPARK.get(), at.x, at.y, at.z,
                    (random.nextDouble() - 0.5D) * spread, 0.04D + random.nextDouble() * (burst ? 0.12D : 0.06D),
                    (random.nextDouble() - 0.5D) * spread);
        }
    }

    /**
     * The steam a Cooling Vent forces out while its drive cools: how much and how fast is {@link
     * uk.co.iceconchy.aerowarptics.cooling.VentPlume}'s, and this only throws it. Out through the louvres,
     * spread across them, and under pressure a jet of Create's own steam on top - the same steam an engine
     * blows, so the vent reads as that kind of machine. Particles are in the world, so aboard a ship both the
     * place and the direction are carried out of the plot: a vent on a banking hull blows the way it points.
     */
    static void tickCoolingVent(uk.co.iceconchy.aerowarptics.cooling.CoolingVentBlockEntity vent) {
        int ticks = vent.plumeTicks();
        if (ticks < 0 || !inTheWorld(vent)
                || !(vent.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel level)) {
            return;
        }
        double jets = uk.co.iceconchy.aerowarptics.cooling.VentPlume.jets(ticks,
                uk.co.iceconchy.aerowarptics.AWConfig.PARTICLE_DENSITY.get());
        net.minecraft.util.RandomSource random = level.random;
        int count = (int) jets + (random.nextDouble() < jets - Math.floor(jets) ? 1 : 0);
        if (count <= 0) {
            return;
        }
        net.minecraft.core.Direction facing = vent.getBlockState()
                .getValue(uk.co.iceconchy.aerowarptics.cooling.CoolingVentBlock.FACING);
        net.minecraft.world.phys.Vec3 out = net.minecraft.world.phys.Vec3.atLowerCornerOf(facing.getNormal());
        // Two directions across the louvres, whichever way they face.
        net.minecraft.world.phys.Vec3 across = facing.getAxis() == net.minecraft.core.Direction.Axis.Y
                ? new net.minecraft.world.phys.Vec3(1.0D, 0.0D, 0.0D) : new net.minecraft.world.phys.Vec3(0.0D, 1.0D, 0.0D);
        net.minecraft.world.phys.Vec3 along = out.cross(across);
        net.minecraft.world.phys.Vec3 mouth = net.minecraft.world.phys.Vec3.atCenterOf(vent.getBlockPos())
                .add(out.scale(0.55D));
        boolean aboard = dev.ryanhcode.sable.Sable.HELPER.isInPlotGrid(vent);
        boolean jet = uk.co.iceconchy.aerowarptics.cooling.VentPlume.isJet(ticks);
        double speed = uk.co.iceconchy.aerowarptics.cooling.VentPlume.speed(ticks);
        for (int puff = 0; puff < count; puff++) {
            net.minecraft.world.phys.Vec3 from = mouth
                    .add(across.scale((random.nextDouble() - 0.5D) * 0.6D))
                    .add(along.scale((random.nextDouble() - 0.5D) * 0.6D));
            net.minecraft.world.phys.Vec3 velocity = out.scale(speed * (0.7D + random.nextDouble() * 0.6D))
                    .add(across.scale((random.nextDouble() - 0.5D) * speed * 0.25D))
                    .add(along.scale((random.nextDouble() - 0.5D) * speed * 0.25D));
            if (aboard) {
                net.minecraft.world.phys.Vec3 tip =
                        uk.co.iceconchy.aerowarptics.client.fx.WarpEffects.toWorld(level, from.add(velocity));
                from = uk.co.iceconchy.aerowarptics.client.fx.WarpEffects.toWorld(level, from);
                velocity = tip.subtract(from);
            }
            level.addParticle(jet ? net.minecraft.core.particles.ParticleTypes.CLOUD
                            : net.minecraft.core.particles.ParticleTypes.POOF,
                    from.x, from.y, from.z, velocity.x, velocity.y, velocity.z);
        }
        if (jet && ticks % 4 == 0) {
            net.minecraft.world.phys.Vec3 from = mouth;
            net.minecraft.world.phys.Vec3 aim = out;
            if (aboard) {
                net.minecraft.world.phys.Vec3 tip =
                        uk.co.iceconchy.aerowarptics.client.fx.WarpEffects.toWorld(level, from.add(aim));
                from = uk.co.iceconchy.aerowarptics.client.fx.WarpEffects.toWorld(level, from);
                aim = tip.subtract(from);
            }
            // A steam jet does not travel: it stands where it is thrown, turned to lie along what it is given.
            level.addParticle(new com.simibubi.create.content.kinetics.steamEngine.SteamJetParticleData(1.0F),
                    from.x, from.y, from.z, aim.x, aim.y, aim.z);
        }
    }

    /**
     * A Collapse Cradle's ritual, followed from the cradle's own client tick - see {@link
     * uk.co.iceconchy.aerowarptics.client.fx.CollapseSpectacle} - and the threads to its ring ({@link
     * uk.co.iceconchy.aerowarptics.client.fx.CradleLinks}). Not drawn aboard a ship: a ritual never runs
     * on one, and a cradle carried aboard has nothing to show.
     */
    static void tickCradle(uk.co.iceconchy.aerowarptics.collapse.CollapseCradleBlockEntity cradle) {
        if (!inTheWorld(cradle) || dev.ryanhcode.sable.Sable.HELPER.isInPlotGrid(cradle)) {
            return;
        }
        uk.co.iceconchy.aerowarptics.client.fx.CollapseSpectacle.watch(cradle);
        uk.co.iceconchy.aerowarptics.client.fx.CradleEffects.tick(cradle);
        uk.co.iceconchy.aerowarptics.client.fx.CradleLinks.tick(cradle);
    }

    /** A cradle this client was watching has just collapsed. */
    static void cradleCollapsed(uk.co.iceconchy.aerowarptics.collapse.CollapseCradleBlockEntity cradle) {
        if (!inTheWorld(cradle) || dev.ryanhcode.sable.Sable.HELPER.isInPlotGrid(cradle)) {
            return;
        }
        uk.co.iceconchy.aerowarptics.client.fx.CollapseSpectacle.collapsed(cradle);
    }

    /** Whether this client's own player is wearing the rift-infused lens. */
    static boolean seesFissures() {
        return uk.co.iceconchy.aerowarptics.fissure.RiftGogglesItem.isWorn(Minecraft.getInstance().player);
    }

    static void animateFissure(net.minecraft.world.level.Level level,
                               net.minecraft.core.BlockPos pos,
                               net.minecraft.util.RandomSource random) {
        if (!seesFissures()) {
            return;
        }
        for (int spark = 0; spark < 2; spark++) {
            level.addParticle(uk.co.iceconchy.aerowarptics.registry.AWParticles.RIFT_SPARK.get(),
                    pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 2.2D,
                    pos.getY() + 0.5D + (random.nextDouble() - 0.5D) * 2.2D,
                    pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 2.2D,
                    (random.nextDouble() - 0.5D) * 0.02D,
                    (random.nextDouble() - 0.5D) * 0.02D,
                    (random.nextDouble() - 0.5D) * 0.02D);
        }
    }

    /**
     * Motes of essence streaming into a Spatial Siphon while it is drawing.
     *
     * <p>Spawned out in the air around and above the funnel and given a velocity straight at its mouth,
     * so they read as being pulled in from the rift rather than drifting. The spark's own friction and
     * lack of gravity do the rest: it slows as it arrives, which is what makes the mouth look like it is
     * swallowing them. Not gated on the goggles - the siphon is a solid machine anyone can see working,
     * unlike the tear it feeds from.
     */
    static void animateSiphonDraw(net.minecraft.world.level.Level level,
                                  net.minecraft.core.BlockPos pos,
                                  net.minecraft.util.RandomSource random) {
        // The funnel mouth sits just above the top of the vessel.
        double mx = pos.getX() + 0.5D;
        double my = pos.getY() + 1.0D;
        double mz = pos.getZ() + 0.5D;
        for (int mote = 0; mote < 2; mote++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = 0.8D + random.nextDouble() * 0.7D;
            double sx = mx + Math.cos(angle) * radius;
            double sy = my + 0.2D + random.nextDouble() * 1.1D;
            double sz = mz + Math.sin(angle) * radius;
            double dx = mx - sx;
            double dy = my - sy;
            double dz = mz - sz;
            double len = Math.max(1.0e-4D, Math.sqrt(dx * dx + dy * dy + dz * dz));
            double speed = 0.09D + random.nextDouble() * 0.05D;
            level.addParticle(uk.co.iceconchy.aerowarptics.registry.AWParticles.RIFT_SPARK.get(),
                    sx, sy, sz, dx / len * speed, dy / len * speed, dz / len * speed);
        }
    }

    static void openHandbook() {
        ScreenOpener.open(new uk.co.iceconchy.aerowarptics.client.screen.HandbookScreen());
    }

    static void showWarpFeedback(WarpFailure failure, @Nullable Fastening fastening) {
        // What the player was actually told, so a report of "it said X" can be matched to the server's refusal.
        ClientTrace.log(ClientTrace.FEEDBACK, "shown to the player: {}{}",
                failure.isFailure() ? failure.getSerializedName() : "warp engaged",
                fastening == null ? "" : " - " + fastening);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (failure.isFailure() && fastening != null) {
            AWLang.translate("warp.fastening.reason", AWLang.component(failure.translationKey()),
                    FasteningText.describe(fastening)).style(ChatFormatting.RED).sendStatus(minecraft.player);
        } else if (failure.isFailure()) {
            AWLang.translate(failure.translationKey()).style(ChatFormatting.RED).sendStatus(minecraft.player);
        } else {
            AWLang.translate("message.warp_engaged").style(ChatFormatting.AQUA).sendStatus(minecraft.player);
        }
    }
}
