package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorAccess;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorBlockEntity;
import uk.co.iceconchy.aerowarptics.registry.AWSounds;
import uk.co.iceconchy.aerowarptics.util.AWLang;

/**
 * A player's edit to a Warp Anchor: name, visibility, network label and on/off.
 *
 * <p>The server re-checks ownership and the name-collision rule; the client's copy of the anchor is
 * never authoritative.
 */
public record ServerboundConfigureAnchorPacket(BlockPos anchorPos,
                                               String name,
                                               WarpAnchorAccess access,
                                               String network,
                                               boolean enabled) implements CustomPacketPayload {

    public static final Type<ServerboundConfigureAnchorPacket> TYPE =
            new Type<>(AeroWarptics.id("configure_anchor"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundConfigureAnchorPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeBlockPos(packet.anchorPos);
                        buf.writeUtf(packet.name, 64);
                        buf.writeEnum(packet.access);
                        buf.writeUtf(packet.network, 32);
                        buf.writeBoolean(packet.enabled);
                    },
                    buf -> new ServerboundConfigureAnchorPacket(
                            buf.readBlockPos(),
                            buf.readUtf(64),
                            buf.readEnum(WarpAnchorAccess.class),
                            buf.readUtf(32),
                            buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ServerboundConfigureAnchorPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            BlockPos pos = packet.anchorPos();
            double reach = AWConfig.MAX_INTERACTION_DISTANCE.get() + 4.0D;
            if (!level.isLoaded(pos) || player.blockPosition().distSqr(pos) > reach * reach) {
                return;
            }
            if (!(level.getBlockEntity(pos) instanceof WarpAnchorBlockEntity anchor)) {
                return;
            }
            if (!anchor.mayEdit(player)) {
                AWLang.translate("message.anchor_not_yours").sendStatus(player);
                return;
            }

            boolean applied = anchor.applyEdit(level, packet.name(), packet.access(), packet.network(), packet.enabled());
            if (!applied) {
                AWLang.translate("message.anchor_name_taken").sendStatus(player);
                return;
            }
            level.playSound(null, pos, AWSounds.ANCHOR_CONFIGURED.get(), SoundSource.BLOCKS, 0.7F, 1.2F);
            AWLang.translate("message.anchor_saved", anchor.displayName()).sendStatus(player);
        });
    }
}
