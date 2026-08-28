package uk.co.iceconchy.aerowarptics.client;

import net.createmod.catnip.gui.ScreenOpener;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorBlockEntity;
import uk.co.iceconchy.aerowarptics.client.fx.WarpCorridorOverlay;
import uk.co.iceconchy.aerowarptics.client.fx.WarpEffects;
import uk.co.iceconchy.aerowarptics.client.screen.AstrolabeChartScreen;
import uk.co.iceconchy.aerowarptics.client.screen.RiftDriveConsoleScreen;
import uk.co.iceconchy.aerowarptics.client.fx.RiftEffectManager;
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
        PacketDistributor.sendToServer(ServerboundAstrolabePacket.open(tablePos));
    }

    static void requestGateDial(net.minecraft.core.BlockPos gatePos) {
        PacketDistributor.sendToServer(ServerboundGatePacket.open(gatePos));
    }

    static void acceptGateDial(ClientboundGateDialPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof RiftGateDialScreen open && open.matches(packet.gatePos())) {
            open.accept(packet);
            return;
        }
        ScreenOpener.open(new RiftGateDialScreen(packet));
    }

    static void foldCrossed(java.util.UUID shipId) {
        FoldCrossings.expect(shipId);
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
        RiftEffectManager.hold(holder, centre, toViewer.normalize(),
                CHUTE_RADIUS, CHUTE_RADIUS, CHUTE_COLOUR, CHUTE_OPEN_TICKS);
        RiftEffectManager.aim(holder, toViewer.normalize());
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
        WarpEffects.onStage(packet);
    }

    static void setInWarpCorridor(ClientboundCorridorPacket packet) {
        WarpCorridorOverlay.accept(packet);
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
        if (!uk.co.iceconchy.aerowarptics.fissure.RiftGogglesItem.isWorn(minecraft.player)) {
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
        RiftEffectManager.hold(holder, centre, toViewer.normalize(), radius, radius,
                FISSURE_COLOUR, FISSURE_OPEN_TICKS);
        RiftEffectManager.aim(holder, toViewer.normalize());
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

    static void openHandbook() {
        ScreenOpener.open(new uk.co.iceconchy.aerowarptics.client.screen.HandbookScreen());
    }

    static void showWarpFeedback(WarpFailure failure) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (failure.isFailure()) {
            AWLang.translate(failure.translationKey()).style(ChatFormatting.RED).sendStatus(minecraft.player);
        } else {
            AWLang.translate("message.warp_engaged").style(ChatFormatting.AQUA).sendStatus(minecraft.player);
        }
    }
}
