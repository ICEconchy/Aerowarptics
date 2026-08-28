package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.modulator.RiftModulatorBlockEntity;
import uk.co.iceconchy.aerowarptics.modulator.RiftModulatorTheme;

/**
 * Everything a player can do standing at a Rift Modulator.
 *
 * <p>Unlike the Chute or the Gate, nothing here is checked for ownership. A Modulator carries no
 * identity of its own to own - it is a filter over whichever drive happens to be sitting next to it,
 * and the drive itself is already the thing this mod gates behind permission. Changing a colour is not
 * a decision worth an access model.
 *
 * @param modulatorPos the Modulator the player clicked, in its own level's coordinates
 * @param colour       packed RGB for {@link Action#SET_COLOUR} or {@link Action#SET_ACCENT_COLOUR},
 *                     otherwise ignored
 * @param themeOrdinal a {@link RiftModulatorTheme} ordinal for {@link Action#SET_THEME}, otherwise
 *                     ignored
 * @param intensity    a fraction for {@link Action#SET_INTENSITY}, otherwise ignored - clamped and
 *                     validated server-side regardless of what a client sends
 */
public record ServerboundModulatorPacket(BlockPos modulatorPos, Action action, int colour, int themeOrdinal,
                                         float intensity) implements CustomPacketPayload {

    public enum Action {
        /** Send this Modulator's panel back. */
        OPEN,
        /** Set the rift's core colour. */
        SET_COLOUR,
        /** Set the rift's rim colour. */
        SET_ACCENT_COLOUR,
        /** Set the rift's look and opening animation. */
        SET_THEME,
        /** Set how strongly the rift's flourish reads. */
        SET_INTENSITY
    }

    public static final Type<ServerboundModulatorPacket> TYPE = new Type<>(AeroWarptics.id("modulator"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundModulatorPacket> STREAM_CODEC =
            StreamCodec.of(ServerboundModulatorPacket::encode, ServerboundModulatorPacket::decode);

    public static ServerboundModulatorPacket open(BlockPos modulatorPos) {
        return new ServerboundModulatorPacket(modulatorPos, Action.OPEN, 0, 0, 0.0F);
    }

    public static ServerboundModulatorPacket setColour(BlockPos modulatorPos, int colour) {
        return new ServerboundModulatorPacket(modulatorPos, Action.SET_COLOUR, colour, 0, 0.0F);
    }

    public static ServerboundModulatorPacket setAccentColour(BlockPos modulatorPos, int colour) {
        return new ServerboundModulatorPacket(modulatorPos, Action.SET_ACCENT_COLOUR, colour, 0, 0.0F);
    }

    public static ServerboundModulatorPacket setTheme(BlockPos modulatorPos, RiftModulatorTheme theme) {
        return new ServerboundModulatorPacket(modulatorPos, Action.SET_THEME, 0, theme.ordinal(), 0.0F);
    }

    public static ServerboundModulatorPacket setIntensity(BlockPos modulatorPos, float intensity) {
        return new ServerboundModulatorPacket(modulatorPos, Action.SET_INTENSITY, 0, 0, intensity);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ServerboundModulatorPacket packet) {
        buf.writeBlockPos(packet.modulatorPos);
        buf.writeEnum(packet.action);
        buf.writeInt(packet.colour);
        buf.writeVarInt(packet.themeOrdinal);
        buf.writeFloat(packet.intensity);
    }

    private static ServerboundModulatorPacket decode(RegistryFriendlyByteBuf buf) {
        return new ServerboundModulatorPacket(buf.readBlockPos(), buf.readEnum(Action.class),
                buf.readInt(), buf.readVarInt(), buf.readFloat());
    }

    public static void handle(ServerboundModulatorPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            RiftModulatorBlockEntity modulator = resolve(player, level, packet.modulatorPos);
            if (modulator == null) {
                return;
            }
            switch (packet.action) {
                case OPEN -> {
                }
                case SET_COLOUR -> modulator.setColour(packet.colour);
                case SET_ACCENT_COLOUR -> modulator.setAccentColour(packet.colour);
                case SET_THEME -> modulator.setTheme(RiftModulatorTheme.byIndex(packet.themeOrdinal));
                // The block entity clamps this itself, so a modified client sending something absurd
                // just gets clamped rather than trusted - the same rule every other setter here follows.
                case SET_INTENSITY -> modulator.setIntensity(packet.intensity);
            }
            ClientboundModulatorPanelPacket.sendTo(player, modulator);
        });
    }

    /**
     * Finds the Modulator, having first checked the player could plausibly be standing at it.
     *
     * <p>The same shape as {@code ServerboundChutePacket.resolve}: a Modulator aboard an airship has
     * plot-grid coordinates nowhere near the player's own, so the cheap distance bound only applies to
     * one standing on the ground.
     */
    private static RiftModulatorBlockEntity resolve(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof RiftModulatorBlockEntity modulator)) {
            return null;
        }
        double reach = AWConfig.MAX_INTERACTION_DISTANCE.get() + 16.0D;
        if (!modulator.aboard() && player.blockPosition().distSqr(pos) > reach * reach) {
            return null;
        }
        return modulator;
    }
}
