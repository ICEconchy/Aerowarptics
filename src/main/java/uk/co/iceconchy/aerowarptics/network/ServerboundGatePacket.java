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
import uk.co.iceconchy.aerowarptics.gate.GateFailure;
import uk.co.iceconchy.aerowarptics.gate.RiftGate;
import uk.co.iceconchy.aerowarptics.gate.RiftGateBlockEntity;
import uk.co.iceconchy.aerowarptics.gate.RiftGateRegistry;

import java.util.UUID;

/**
 * Everything a player can do standing at a Rift Gate.
 *
 * <p>One payload for all of it because they share the same gate: the controller has to exist, be
 * within reach, and be a gate this player may work. Splitting them into four packets would mean
 * writing that gate four times.
 *
 * @param gatePos  the controller the player clicked
 * @param action   what they are doing
 * @param target   destination for {@link Action#DIAL}, otherwise ignored
 * @param text     new name for {@link Action#RENAME}, otherwise ignored
 */
public record ServerboundGatePacket(BlockPos gatePos, Action action, UUID target, String text)
        implements CustomPacketPayload {

    public enum Action {
        /** Send this gate's dial list back. */
        OPEN,
        /** Strike a connection to the named gate. */
        DIAL,
        /** Let go of whatever connection is standing. */
        HANG_UP,
        /** Rename this gate. */
        RENAME,
        /** Flip this gate between public and private. */
        CYCLE_ACCESS
    }

    private static final UUID NIL = new UUID(0L, 0L);

    public static final Type<ServerboundGatePacket> TYPE = new Type<>(AeroWarptics.id("gate"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundGatePacket> STREAM_CODEC =
            StreamCodec.of(ServerboundGatePacket::encode, ServerboundGatePacket::decode);

    public static ServerboundGatePacket open(BlockPos gatePos) {
        return new ServerboundGatePacket(gatePos, Action.OPEN, NIL, "");
    }

    public static ServerboundGatePacket dial(BlockPos gatePos, UUID target) {
        return new ServerboundGatePacket(gatePos, Action.DIAL, target, "");
    }

    public static ServerboundGatePacket hangUp(BlockPos gatePos) {
        return new ServerboundGatePacket(gatePos, Action.HANG_UP, NIL, "");
    }

    public static ServerboundGatePacket rename(BlockPos gatePos, String name) {
        return new ServerboundGatePacket(gatePos, Action.RENAME, NIL, name);
    }

    public static ServerboundGatePacket cycleAccess(BlockPos gatePos) {
        return new ServerboundGatePacket(gatePos, Action.CYCLE_ACCESS, NIL, "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ServerboundGatePacket packet) {
        buf.writeBlockPos(packet.gatePos);
        buf.writeEnum(packet.action);
        buf.writeUUID(packet.target == null ? NIL : packet.target);
        buf.writeUtf(packet.text == null ? "" : packet.text, 64);
    }

    private static ServerboundGatePacket decode(RegistryFriendlyByteBuf buf) {
        return new ServerboundGatePacket(buf.readBlockPos(), buf.readEnum(Action.class),
                buf.readUUID(), buf.readUtf(64));
    }

    public static void handle(ServerboundGatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            RiftGateBlockEntity gate = resolve(player, level, packet.gatePos);
            if (gate == null) {
                return;
            }

            switch (packet.action) {
                case OPEN -> {
                }
                case DIAL -> gate.dial(player, packet.target);
                case HANG_UP -> gate.hangUp(GateFailure.NONE);
                case RENAME -> rename(level, gate, player, packet.text);
                case CYCLE_ACCESS -> cycleAccess(level, gate, player);
            }
            ClientboundGateDialPacket.sendTo(player, level, gate);
        });
    }

    /**
     * Finds the gate, having first checked the player is entitled to be standing at it.
     *
     * <p>The reach bound is deliberately generous and deliberately cheap. It exists to stop a client
     * driving a gate on the far side of the world, not to be the authority on who may dial what -
     * that authority is the destination's, and it is applied when a connection is actually struck.
     */
    private static RiftGateBlockEntity resolve(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof RiftGateBlockEntity gate)) {
            return null;
        }
        double reach = AWConfig.MAX_INTERACTION_DISTANCE.get() + 16.0D;
        return player.blockPosition().distSqr(pos) > reach * reach ? null : gate;
    }

    /** Only the owner may rename a gate, and only to a name nothing else has taken. */
    private static void rename(ServerLevel level, RiftGateBlockEntity gate, ServerPlayer player, String name) {
        RiftGateRegistry registry = RiftGateRegistry.get(level);
        RiftGate record = registry.byId(gate.gateId());
        if (record == null || !mayEdit(record, player)) {
            return;
        }
        registry.rename(record.id(), name);
    }

    private static void cycleAccess(ServerLevel level, RiftGateBlockEntity gate, ServerPlayer player) {
        RiftGateRegistry registry = RiftGateRegistry.get(level);
        RiftGate record = registry.byId(gate.gateId());
        if (record == null || !mayEdit(record, player)) {
            return;
        }
        WarpAnchorAccess next = record.access().next();
        registry.register(record.withAccess(next.permitted() ? next : record.access()));
    }

    /** An unowned gate is anybody's - which is what makes a gate placed by a command block usable. */
    private static boolean mayEdit(RiftGate gate, ServerPlayer player) {
        return gate.owner() == null || gate.isOwnedBy(player.getUUID()) || player.hasPermissions(2);
    }
}
