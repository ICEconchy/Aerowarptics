package uk.co.iceconchy.aerowarptics.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorBlockEntity;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.network.ClientboundCorridorPacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundAstrolabeChartPacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundDriveConsolePacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundWarpEffectPacket;
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

    /** The server has put this player in, or taken them out of, a warp corridor. */
    public static void setInWarpCorridor(ClientboundCorridorPacket packet) {
        if (client()) {
            ClientRuntime.setInWarpCorridor(packet);
        }
    }

    /** Ambient warp visuals, driven from the drive's own client tick. */
    public static void tickDriveEffects(RiftDriveBlockEntity drive) {
        if (client()) {
            ClientRuntime.tickDriveEffects(drive);
        }
    }

    public static void showWarpFeedback(WarpFailure failure) {
        if (client()) {
            ClientRuntime.showWarpFeedback(failure);
        }
    }
}
