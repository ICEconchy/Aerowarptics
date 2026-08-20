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

    /**
     * Keeps a gate's aperture standing.
     *
     * <p>Held rather than opened once, so it survives the player leaving and coming back, and closes
     * on its own if the gate stops saying it is there. See {@code RiftEffectManager.hold}.
     */
    static void tickGateAperture(uk.co.iceconchy.aerowarptics.gate.RiftGateBlockEntity gate) {
        long holder = gate.getBlockPos().asLong();
        uk.co.iceconchy.aerowarptics.gate.RiftGateShape shape = gate.shape();
        if (shape == null || !gate.state().hasAperture()) {
            RiftEffectManager.release(holder);
            return;
        }
        net.minecraft.world.phys.Vec3 normal =
                shape.normal() == net.minecraft.core.Direction.Axis.X
                        ? new net.minecraft.world.phys.Vec3(1.0D, 0.0D, 0.0D)
                        : new net.minecraft.world.phys.Vec3(0.0D, 0.0D, 1.0D);
        RiftEffectManager.hold(holder, shape.centre(), normal, shape.halfWidth(), shape.halfHeight(),
                GATE_COLOUR, GATE_OPEN_TICKS);
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
        WarpEffects.tickDrive(drive);
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
