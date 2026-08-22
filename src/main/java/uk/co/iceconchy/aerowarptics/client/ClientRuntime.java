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

    /**
     * The colour a gate's aperture burns.
     *
     * <p>Its own, rather than any drive tier's. A doorway standing in a field is not the same thing as
     * a ship's drive tearing one open, and telling them apart at a glance is worth one constant.
     */
    private static final int GATE_COLOUR = 0xA24BFF;

    /**
     * Ticks a gate's aperture takes to shatter open.
     *
     * <p>A constant rather than the server's dial time. This decides how long a piece of animation
     * runs and nothing else, and a client reading a server setting to find out is a coin toss over a
     * number that does not matter.
     */
    private static final int GATE_OPEN_TICKS = 40;

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
     * so a Rift Gate sitting in a scene would otherwise ask for an aperture, and get one drawn in the
     * real world at the scene's coordinates. Ambient effects are for the world; a scene draws itself.
     */
    private static boolean inTheWorld(net.minecraft.world.level.block.entity.BlockEntity block) {
        return block.getLevel() != null && block.getLevel() == Minecraft.getInstance().level;
    }

    /**
     * Keeps a gate's aperture standing.
     *
     * <p>Held rather than opened once, so it survives the player leaving and coming back, and closes
     * on its own if the gate stops saying it is there. See {@code RiftEffectManager.hold}.
     */
    /** Colour of a chute's aperture. The same violet a gate tears, so the family reads. */
    private static final int CHUTE_COLOUR = 0xA24BFF;

    /** Ticks a chute's aperture takes to shatter open - the same break a gate's does. */
    private static final int CHUTE_OPEN_TICKS = 30;

    /** Half-width of the pane, in blocks. Sized to sit inside the cage without touching the posts. */
    private static final double CHUTE_RADIUS = 0.28D;

    /**
     * Holds a Rift Chute's aperture open, and turns it to face the viewer.
     *
     * <p>The same aperture a Rift Gate tears, down to the shatter and the seal - a chute is a small
     * hole in space and there is no reason for it to break differently from a large one. It is
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

    /** How many angles a gate's aperture is shaped by. Fine enough to follow one block's edge. */
    private static final int GATE_PROFILE_STEPS = 192;

    /**
     * Samples a gate opening's reach at each angle, for {@code RiftEffectManager.shapeTo}.
     *
     * <p>Recomputed each tick rather than cached. It is ninety-six marches over a mask of at most two
     * hundred cells, which is nothing beside what the aperture costs to draw, and caching it would
     * mean noticing when a ring is rebuilt into a different shape.
     */
    private static float[] profileOf(uk.co.iceconchy.aerowarptics.gate.RiftGateShape shape) {
        float[] profile = new float[GATE_PROFILE_STEPS];
        for (int step = 0; step < GATE_PROFILE_STEPS; step++) {
            double angle = step * (Math.PI * 2.0D) / GATE_PROFILE_STEPS;
            profile[step] = (float) shape.reachAt(angle);
        }
        return profile;
    }

    static void tickGateAperture(uk.co.iceconchy.aerowarptics.gate.RiftGateBlockEntity gate) {
        if (!inTheWorld(gate)) {
            return;
        }
        long holder = gate.getBlockPos().asLong();
        uk.co.iceconchy.aerowarptics.gate.RiftGateShape shape = gate.shape();
        if (shape == null || !gate.state().hasAperture()) {
            RiftEffectManager.release(holder);
            return;
        }
        if (gate.state() == uk.co.iceconchy.aerowarptics.gate.RiftGateState.CLOSING) {
            // The gate's closing state is the animation, not the pause before it. Holding the
            // aperture open through it and letting go afterwards left the hole standing at full size
            // for a second and then shrinking, which reads as a gate that shuts a beat late.
            RiftEffectManager.seal(holder);
            return;
        }
        net.minecraft.world.phys.Vec3 normal =
                shape.normal() == net.minecraft.core.Direction.Axis.X
                        ? new net.minecraft.world.phys.Vec3(1.0D, 0.0D, 0.0D)
                        : new net.minecraft.world.phys.Vec3(0.0D, 0.0D, 1.0D);
        RiftEffectManager.hold(holder, shape.centre(), normal, shape.halfWidth(), shape.halfHeight(),
                GATE_COLOUR, GATE_OPEN_TICKS);
        // Bend the aperture to the opening. A ring is flood filled, so its hole is very often not a
        // rectangle, and an ellipse fitted to the bounding box hangs straight through the frame.
        RiftEffectManager.shapeTo(holder, profileOf(shape));
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
