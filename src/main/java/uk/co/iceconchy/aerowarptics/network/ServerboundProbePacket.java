package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.probe.ProbeBearing;
import uk.co.iceconchy.aerowarptics.probe.RiftProbeBlockEntity;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;

/**
 * Everything a player can do at a Rift Probe.
 *
 * <p>{@link Action#SOUND} is the one worth being careful about: it is a client asking a server to
 * generate terrain, which is the most expensive thing anything in this mod can ask for. So it is
 * checked twice - a cheap reach bound here, and the probe's own checks on essence, state and distance
 * afterwards - and it costs essence whether or not the reading turns out to be worth having.
 *
 * @param probePos the probe, in its own level's coordinates - inside an airship's plot, not the world
 * @param action   what the player is doing
 * @param value    the bearing index or the range in blocks, depending on the action
 */
public record ServerboundProbePacket(BlockPos probePos, Action action, int value)
        implements CustomPacketPayload {

    public enum Action {
        /** Open the panel: send this probe's state back. */
        OPEN,
        /** Aim the dial. */
        SET_BEARING,
        /** Set how far the sounding is thrown. */
        SET_RANGE,
        /** Throw a sounding, paying for it. */
        SOUND,
        /** Hand the current reading to the drive as a course. */
        SET_COURSE,
        /** Ask for the survey itself, which is far too large to ride on every state update. */
        FETCH_READING
    }

    public static final Type<ServerboundProbePacket> TYPE = new Type<>(AeroWarptics.id("probe"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundProbePacket> STREAM_CODEC =
            StreamCodec.of(ServerboundProbePacket::encode, ServerboundProbePacket::decode);

    public static ServerboundProbePacket open(BlockPos probePos) {
        return new ServerboundProbePacket(probePos, Action.OPEN, 0);
    }

    public static ServerboundProbePacket bearing(BlockPos probePos, ProbeBearing bearing) {
        return new ServerboundProbePacket(probePos, Action.SET_BEARING, bearing.index());
    }

    public static ServerboundProbePacket range(BlockPos probePos, int blocks) {
        return new ServerboundProbePacket(probePos, Action.SET_RANGE, blocks);
    }

    public static ServerboundProbePacket sound(BlockPos probePos) {
        return new ServerboundProbePacket(probePos, Action.SOUND, 0);
    }

    public static ServerboundProbePacket setCourse(BlockPos probePos) {
        return new ServerboundProbePacket(probePos, Action.SET_COURSE, 0);
    }

    public static ServerboundProbePacket fetchReading(BlockPos probePos) {
        return new ServerboundProbePacket(probePos, Action.FETCH_READING, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ServerboundProbePacket packet) {
        buf.writeBlockPos(packet.probePos);
        buf.writeEnum(packet.action);
        buf.writeVarInt(packet.value);
    }

    private static ServerboundProbePacket decode(RegistryFriendlyByteBuf buf) {
        return new ServerboundProbePacket(buf.readBlockPos(), buf.readEnum(Action.class), buf.readVarInt());
    }

    public static void handle(ServerboundProbePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            RiftProbeBlockEntity probe = resolve(player, level, packet.probePos);
            if (probe == null) {
                AWNetwork.sendTo(player, new ClientboundWarpFeedbackPacket(WarpFailure.NO_AIRSHIP));
                return;
            }

            switch (packet.action) {
                case OPEN -> { }
                case SET_BEARING -> probe.setBearing(ProbeBearing.byIndex(packet.value));
                case SET_RANGE -> probe.setRange(packet.value);
                case SOUND -> report(player, probe.sound(player));
                case SET_COURSE -> {
                    WarpFailure result = probe.setCourse(player);
                    report(player, result);
                    if (!result.isFailure() && probe.sounding() != null) {
                        // Said out loud, because setting a course moves nothing and looks like
                        // nothing. The only evidence used to be a field on another machine's screen.
                        uk.co.iceconchy.aerowarptics.util.AWLang
                                .translate("message.probe_course_set", probe.sounding().label())
                                .style(net.minecraft.ChatFormatting.AQUA)
                                .sendStatus(player);
                    }
                }
                case FETCH_READING -> {
                    if (probe.sounding() != null) {
                        AWNetwork.sendTo(player,
                                new ClientboundProbeReadingPacket(probe.getBlockPos(), probe.sounding()));
                    }
                }
            }
            AWNetwork.sendTo(player, ClientboundProbePacket.of(probe, player));
        });
    }

    private static void report(ServerPlayer player, WarpFailure result) {
        if (result.isFailure()) {
            AWNetwork.sendTo(player, new ClientboundWarpFeedbackPacket(result));
        }
    }

    /**
     * Finds the probe, having first checked the player could plausibly be standing at it.
     *
     * <p>A probe aboard an airship lives in a plot chunk whose coordinates have nothing to do with the
     * player's, so the cheap bound is only applied to one on the ground. The real authority check -
     * being close enough to the block in world space - is the probe's own, and it is applied to every
     * action that does anything.
     */
    @Nullable
    private static RiftProbeBlockEntity resolve(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof RiftProbeBlockEntity probe)) {
            return null;
        }
        double reach = AWConfig.MAX_INTERACTION_DISTANCE.get() + 16.0D;
        if (probe.airship() == null && player.blockPosition().distSqr(pos) > reach * reach) {
            return null;
        }
        return probe;
    }
}
