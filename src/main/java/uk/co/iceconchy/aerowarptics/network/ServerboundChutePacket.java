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
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorAccess;
import uk.co.iceconchy.aerowarptics.chute.RiftChute;
import uk.co.iceconchy.aerowarptics.chute.RiftChuteBlockEntity;
import uk.co.iceconchy.aerowarptics.chute.RiftChuteRegistry;

import java.util.UUID;

/**
 * Everything a player can do standing at a Rift Chute.
 *
 * <p>The same shape as {@link ServerboundGatePacket}, and for the same reason: every action needs the
 * same chute resolved, reach-checked and permission-checked first, and writing that four times would
 * be four chances to write it differently.
 *
 * @param chutePos the chute the player clicked, in its own level's coordinates
 * @param target   the chute to bind to for {@link Action#BIND}, otherwise ignored
 * @param text     new name for {@link Action#RENAME}, otherwise ignored
 */
public record ServerboundChutePacket(BlockPos chutePos, Action action, UUID target, String text)
        implements CustomPacketPayload {

    public enum Action {
        /** Send this chute's panel back. */
        OPEN,
        /** Bind this chute to send to the named one. */
        BIND,
        /** Forget whatever this chute was bound to. */
        UNBIND,
        /** Rename this chute. */
        RENAME,
        /** Flip this chute between public and private. */
        CYCLE_ACCESS
    }

    private static final UUID NIL = new UUID(0L, 0L);

    public static final Type<ServerboundChutePacket> TYPE = new Type<>(AeroWarptics.id("chute"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundChutePacket> STREAM_CODEC =
            StreamCodec.of(ServerboundChutePacket::encode, ServerboundChutePacket::decode);

    public static ServerboundChutePacket open(BlockPos chutePos) {
        return new ServerboundChutePacket(chutePos, Action.OPEN, NIL, "");
    }

    public static ServerboundChutePacket bind(BlockPos chutePos, UUID target) {
        return new ServerboundChutePacket(chutePos, Action.BIND, target, "");
    }

    public static ServerboundChutePacket unbind(BlockPos chutePos) {
        return new ServerboundChutePacket(chutePos, Action.UNBIND, NIL, "");
    }

    public static ServerboundChutePacket rename(BlockPos chutePos, String name) {
        return new ServerboundChutePacket(chutePos, Action.RENAME, NIL, name);
    }

    public static ServerboundChutePacket cycleAccess(BlockPos chutePos) {
        return new ServerboundChutePacket(chutePos, Action.CYCLE_ACCESS, NIL, "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ServerboundChutePacket packet) {
        buf.writeBlockPos(packet.chutePos);
        buf.writeEnum(packet.action);
        buf.writeUUID(packet.target == null ? NIL : packet.target);
        buf.writeUtf(packet.text == null ? "" : packet.text, 64);
    }

    private static ServerboundChutePacket decode(RegistryFriendlyByteBuf buf) {
        return new ServerboundChutePacket(buf.readBlockPos(), buf.readEnum(Action.class),
                buf.readUUID(), buf.readUtf(64));
    }

    public static void handle(ServerboundChutePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            RiftChuteBlockEntity chute = resolve(player, level, packet.chutePos);
            if (chute == null) {
                return;
            }
            RiftChute record = chute.ensureRegistered(level, player);

            switch (packet.action) {
                case OPEN -> {
                }
                case BIND -> bind(level, record, player, packet.target);
                case UNBIND -> {
                    if (mayEdit(record, player)) {
                        RiftChuteRegistry.get(level).bind(record.id(), null);
                    }
                }
                case RENAME -> {
                    if (mayEdit(record, player)) {
                        RiftChuteRegistry.get(level).rename(record.id(), packet.text);
                    }
                }
                case CYCLE_ACCESS -> cycleAccess(level, record, player);
            }
            ClientboundChutePanelPacket.sendTo(player, level, chute);
        });
    }

    /**
     * Finds the chute, having first checked the player could plausibly be standing at it.
     *
     * <p>A chute aboard an airship lives in a plot chunk whose coordinates have nothing to do with
     * the player's, so the cheap distance bound is only applied to one on the ground.
     */
    private static RiftChuteBlockEntity resolve(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof RiftChuteBlockEntity chute)) {
            return null;
        }
        double reach = AWConfig.MAX_INTERACTION_DISTANCE.get() + 16.0D;
        if (!chute.aboard() && player.blockPosition().distSqr(pos) > reach * reach) {
            return null;
        }
        return chute;
    }

    /**
     * Binds this chute to another.
     *
     * <p>Checked at <em>both</em> ends: the player must be entitled to edit the sending chute, and
     * must be allowed to see the destination. Checking only the near end would let anybody route
     * their overflow into somebody else's private base.
     */
    private static void bind(ServerLevel level, RiftChute self, ServerPlayer player, UUID target) {
        if (!mayEdit(self, player)) {
            return;
        }
        RiftChuteRegistry registry = RiftChuteRegistry.get(level);
        RiftChute destination = registry.byId(target);
        if (destination == null || !destination.enabled() || !destination.isVisibleTo(player)) {
            return;
        }
        registry.bind(self.id(), destination.id());
    }

    private static void cycleAccess(ServerLevel level, RiftChute self, ServerPlayer player) {
        if (!mayEdit(self, player)) {
            return;
        }
        WarpAnchorAccess next = self.access().next();
        RiftChuteRegistry.get(level)
                .register(self.withAccess(next.permitted() ? next : self.access()));
    }

    /** An unowned chute is anybody's, which is what makes one placed by a command block usable. */
    private static boolean mayEdit(RiftChute chute, ServerPlayer player) {
        return chute.owner() == null || chute.isOwnedBy(player.getUUID()) || player.hasPermissions(2);
    }
}
