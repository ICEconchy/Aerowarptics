package uk.co.iceconchy.aerowarptics.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorBlockEntity;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.network.ClientboundCorridorPacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundAstrolabeChartPacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundDriveConsolePacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundGateDialPacket;
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

    /**
     * A hull has crossed the fold: tell this client to read the jump as a jump.
     *
     * <p>Without it Sable treats the teleport as movement, builds a collision volume the length of
     * the whole warp, refuses it, and drops the crew through the deck.
     */
    public static void foldCrossed(java.util.UUID shipId) {
        if (client()) {
            ClientRuntime.foldCrossed(shipId);
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

    /** Ambient gate visuals, driven from the gate's own client tick. */
    public static void tickGateAperture(uk.co.iceconchy.aerowarptics.gate.RiftGateBlockEntity gate) {
        if (client()) {
            ClientRuntime.tickGateAperture(gate);
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

    /**
     * A player has read the Navigator's Handbook.
     *
     * <p>The only entry point here with no packet behind it: the book says the same thing on every
     * world, so there is nothing for the server to answer with.
     */
    public static void openHandbook() {
        if (client()) {
            ClientRuntime.openHandbook();
        }
    }
}
