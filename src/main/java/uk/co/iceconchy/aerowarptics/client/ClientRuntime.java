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
import uk.co.iceconchy.aerowarptics.client.screen.RiftNavigationScreen;
import uk.co.iceconchy.aerowarptics.client.screen.WarpAnchorScreen;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.network.ClientboundCorridorPacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundNavigationDataPacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundWarpEffectPacket;
import uk.co.iceconchy.aerowarptics.network.ServerboundNavigationRequestPacket;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;

/**
 * Client-only implementation behind {@link AWClientHooks}.
 *
 * <p>The client owns nothing authoritative. Opening the navigation console is a <em>request</em>;
 * the screen only appears once the server has answered with a
 * {@link ClientboundNavigationDataPacket}.
 */
@OnlyIn(Dist.CLIENT)
final class ClientRuntime {

    private ClientRuntime() {
    }

    static void requestRiftNavigation(RiftDriveBlockEntity drive) {
        PacketDistributor.sendToServer(new ServerboundNavigationRequestPacket(drive.getBlockPos()));
    }

    static void openWarpAnchorScreen(WarpAnchorBlockEntity anchor) {
        ScreenOpener.open(new WarpAnchorScreen(anchor));
    }

    static void acceptNavigationData(ClientboundNavigationDataPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof RiftNavigationScreen open && open.matches(packet.drivePos())) {
            open.accept(packet);
            return;
        }
        ScreenOpener.open(new RiftNavigationScreen(packet));
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
