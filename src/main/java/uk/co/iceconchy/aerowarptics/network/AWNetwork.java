package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;

import java.util.Collection;

/**
 * Networking, built on NeoForge's payload registrar - the platform's own system, not a second
 * framework.
 *
 * <p>The trust boundary is strict. Serverbound payloads carry only a block position and, at most, an
 * anchor UUID or a player's choice of setting; the server re-derives the airship, the anchor record, the distance, the cost and the
 * player's authority before acting. Clientbound payloads carry only what is needed to render.
 */
public final class AWNetwork {

    /** Bumped when a payload's shape changes. */
    private static final String VERSION = "43";

    private AWNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);

        registrar.playToServer(ServerboundDriveConsolePacket.TYPE,
                ServerboundDriveConsolePacket.STREAM_CODEC,
                ServerboundDriveConsolePacket::handle);
        registrar.playToServer(ServerboundAstrolabePacket.TYPE,
                ServerboundAstrolabePacket.STREAM_CODEC,
                ServerboundAstrolabePacket::handle);
        registrar.playToServer(ServerboundWarpCommandPacket.TYPE,
                ServerboundWarpCommandPacket.STREAM_CODEC,
                ServerboundWarpCommandPacket::handle);
        registrar.playToServer(ServerboundGatePacket.TYPE,
                ServerboundGatePacket.STREAM_CODEC,
                ServerboundGatePacket::handle);
        registrar.playToServer(ServerboundProbePacket.TYPE,
                ServerboundProbePacket.STREAM_CODEC,
                ServerboundProbePacket::handle);
        registrar.playToServer(ServerboundChutePacket.TYPE,
                ServerboundChutePacket.STREAM_CODEC,
                ServerboundChutePacket::handle);
        registrar.playToServer(ServerboundCouplerPacket.TYPE,
                ServerboundCouplerPacket.STREAM_CODEC,
                ServerboundCouplerPacket::handle);
        registrar.playToServer(ServerboundModulatorPacket.TYPE,
                ServerboundModulatorPacket.STREAM_CODEC,
                ServerboundModulatorPacket::handle);
        registrar.playToServer(ServerboundCradleStartPacket.TYPE,
                ServerboundCradleStartPacket.STREAM_CODEC,
                ServerboundCradleStartPacket::handle);
        registrar.playToClient(ClientboundCradlePanelPacket.TYPE,
                ClientboundCradlePanelPacket.STREAM_CODEC,
                ClientboundCradlePanelPacket::handle);
        registrar.playToServer(ServerboundPlotterPacket.TYPE,
                ServerboundPlotterPacket.STREAM_CODEC,
                ServerboundPlotterPacket::handle);
        registrar.playToClient(ClientboundPlotterPanelPacket.TYPE,
                ClientboundPlotterPanelPacket.STREAM_CODEC,
                ClientboundPlotterPanelPacket::handle);
        registrar.playToClient(ClientboundPlotterSurveyPacket.TYPE,
                ClientboundPlotterSurveyPacket.STREAM_CODEC,
                ClientboundPlotterSurveyPacket::handle);

        registrar.playToServer(ServerboundConfigureWardPacket.TYPE,
                ServerboundConfigureWardPacket.STREAM_CODEC,
                ServerboundConfigureWardPacket::handle);
        registrar.playToServer(ServerboundBerthPacket.TYPE,
                ServerboundBerthPacket.STREAM_CODEC,
                ServerboundBerthPacket::handle);
        registrar.playToClient(ClientboundMeltdownCuePacket.TYPE,
                ClientboundMeltdownCuePacket.STREAM_CODEC,
                ClientboundMeltdownCuePacket::handle);
        registrar.playToClient(ClientboundSundererCuePacket.TYPE,
                ClientboundSundererCuePacket.STREAM_CODEC,
                ClientboundSundererCuePacket::handle);
        registrar.playToClient(ClientboundScarZonePacket.TYPE,
                ClientboundScarZonePacket.STREAM_CODEC,
                ClientboundScarZonePacket::handle);
        registrar.playToClient(ClientboundWellheadPanelPacket.TYPE,
                ClientboundWellheadPanelPacket.STREAM_CODEC,
                ClientboundWellheadPanelPacket::handle);
        registrar.playToServer(ServerboundWellheadOrderPacket.TYPE,
                ServerboundWellheadOrderPacket.STREAM_CODEC,
                ServerboundWellheadOrderPacket::handle);
        registrar.playToClient(ClientboundWardCuePacket.TYPE,
                ClientboundWardCuePacket.STREAM_CODEC,
                ClientboundWardCuePacket::handle);
        registrar.playToServer(ServerboundConfigureAnchorPacket.TYPE,
                ServerboundConfigureAnchorPacket.STREAM_CODEC,
                ServerboundConfigureAnchorPacket::handle);
        registrar.playToServer(ServerboundNetworkPacket.TYPE,
                ServerboundNetworkPacket.STREAM_CODEC,
                ServerboundNetworkPacket::handle);

        registrar.playToClient(ClientboundDriveConsolePacket.TYPE,
                ClientboundDriveConsolePacket.STREAM_CODEC,
                ClientboundDriveConsolePacket::handle);
        registrar.playToClient(ClientboundAstrolabeChartPacket.TYPE,
                ClientboundAstrolabeChartPacket.STREAM_CODEC,
                ClientboundAstrolabeChartPacket::handle);
        registrar.playToClient(ClientboundAstrolabeChartPacket.Preview.TYPE,
                ClientboundAstrolabeChartPacket.Preview.STREAM_CODEC,
                ClientboundAstrolabeChartPacket.Preview::handle);
        registrar.playToClient(ClientboundProbePacket.TYPE,
                ClientboundProbePacket.STREAM_CODEC,
                ClientboundProbePacket::handle);
        registrar.playToClient(ClientboundProbeReadingPacket.TYPE,
                ClientboundProbeReadingPacket.STREAM_CODEC,
                ClientboundProbeReadingPacket::handle);
        registrar.playToClient(ClientboundChutePanelPacket.TYPE,
                ClientboundChutePanelPacket.STREAM_CODEC,
                ClientboundChutePanelPacket::handle);
        registrar.playToClient(ClientboundCouplerPanelPacket.TYPE,
                ClientboundCouplerPanelPacket.STREAM_CODEC,
                ClientboundCouplerPanelPacket::handle);
        registrar.playToClient(ClientboundMusterPacket.TYPE,
                ClientboundMusterPacket.STREAM_CODEC,
                ClientboundMusterPacket::handle);
        registrar.playToClient(ClientboundFlotillaCuePacket.TYPE,
                ClientboundFlotillaCuePacket.STREAM_CODEC,
                ClientboundFlotillaCuePacket::handle);
        registrar.playToClient(ClientboundModulatorPanelPacket.TYPE,
                ClientboundModulatorPanelPacket.STREAM_CODEC,
                ClientboundModulatorPanelPacket::handle);

        registrar.playToClient(ClientboundGateDialPacket.TYPE,
                ClientboundGateDialPacket.STREAM_CODEC,
                ClientboundGateDialPacket::handle);
        registrar.playToClient(ClientboundGatePassagePacket.TYPE,
                ClientboundGatePassagePacket.STREAM_CODEC,
                ClientboundGatePassagePacket::handle);
        registrar.playToClient(ClientboundNetworkPanelPacket.TYPE,
                ClientboundNetworkPanelPacket.STREAM_CODEC,
                ClientboundNetworkPanelPacket::handle);
        registrar.playToClient(ClientboundWarpEffectPacket.TYPE,
                ClientboundWarpEffectPacket.STREAM_CODEC,
                ClientboundWarpEffectPacket::handle);
        registrar.playToClient(ClientboundFoldCrossedPacket.TYPE,
                ClientboundFoldCrossedPacket.STREAM_CODEC,
                ClientboundFoldCrossedPacket::handle);
        registrar.playToClient(ClientboundSettlePacket.TYPE,
                ClientboundSettlePacket.STREAM_CODEC,
                ClientboundSettlePacket::handle);
        registrar.playToClient(ClientboundCorridorPacket.TYPE,
                ClientboundCorridorPacket.STREAM_CODEC,
                ClientboundCorridorPacket::handle);
        registrar.playToClient(ClientboundSupercruisePacket.TYPE,
                ClientboundSupercruisePacket.STREAM_CODEC,
                ClientboundSupercruisePacket::handle);
        registrar.playToClient(ClientboundClearancePacket.TYPE,
                ClientboundClearancePacket.STREAM_CODEC,
                ClientboundClearancePacket::handle);
        registrar.playToClient(ClientboundRiftBeaconPacket.TYPE,
                ClientboundRiftBeaconPacket.STREAM_CODEC,
                ClientboundRiftBeaconPacket::handle);
        registrar.playToClient(ClientboundRiftStormPacket.TYPE,
                ClientboundRiftStormPacket.STREAM_CODEC,
                ClientboundRiftStormPacket::handle);

        registrar.playToClient(ClientboundWarpFeedbackPacket.TYPE,
                ClientboundWarpFeedbackPacket.STREAM_CODEC,
                ClientboundWarpFeedbackPacket::handle);
        registrar.playToClient(ClientboundTracePacket.TYPE,
                ClientboundTracePacket.STREAM_CODEC,
                ClientboundTracePacket::handle);
    }

    /** How far from an effect {@link #sendToTracking} reaches, in blocks. */
    private static final double TRACKING_RANGE = 256.0D;

    /** Sends an effect payload to everyone who could plausibly see it. */
    public static void sendToTracking(ServerLevel level, Vec3 origin, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersNear(level, null, origin.x, origin.y, origin.z, TRACKING_RANGE, payload);
    }

    /**
     * {@link #sendToTracking}, and also to each of {@code also} in that level who is out of its reach.
     *
     * <p>For an effect some players have to see wherever they are standing: the crew of a long hull, whose
     * stern can sit deeper in an aperture's throat than the range reaches from its mouth. Each player
     * gets it once, whichever way they qualified.
     */
    public static void sendToTracking(ServerLevel level, Vec3 origin, Collection<ServerPlayer> also,
                                      CustomPacketPayload payload) {
        sendToTracking(level, origin, payload);
        for (ServerPlayer player : also) {
            if (player.serverLevel() == level
                    && player.distanceToSqr(origin) >= TRACKING_RANGE * TRACKING_RANGE) {
                sendTo(player, payload);
            }
        }
    }

    public static void sendTo(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    /**
     * Resolves the drive a serverbound payload refers to, re-checking everything.
     *
     * @return the drive, or {@code null} when the position does not hold one the player may command
     */
    @Nullable
    static RiftDriveBlockEntity resolveDrive(ServerPlayer player, BlockPos pos) {
        Level level = player.level();
        // Guard against a client pointing at an arbitrary far-away position.
        double reach = AWConfig.MAX_INTERACTION_DISTANCE.get() + 16.0D;
        if (!level.isLoaded(pos)) {
            return null;
        }
        if (!(level.getBlockEntity(pos) instanceof RiftDriveBlockEntity drive)) {
            return null;
        }
        // The drive may sit inside an airship plot far from the player's raw coordinates, so distance
        // is checked in world space by WarpValidator; this is only a cheap sanity bound.
        if (drive.airship() == null && player.blockPosition().distSqr(pos) > reach * reach) {
            return null;
        }
        return drive;
    }
}
