package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.gate.RiftGateShape;

/**
 * Where a Rift Sunderer's tear has got to: sent as it begins, at every beat, once a second between, and once
 * more as it ends, to everyone near enough to be shown it.
 *
 * <p>The meltdown's shape and for its reason: it says how far through the scene is rather than what to do next,
 * so a player who arrives half-way joins it half-way, and everything the client draws - the cracks, the hand,
 * which of the ring bursts - is grown from the seed it carries, so every client draws the same. Who the hand
 * turns to is the one thing not sent: each client has it turn to its own player, if they are near.
 *
 * @param origin  the Sunderer
 * @param shape   the opening it tears
 * @param facing  which way the Sunderer faces: the membrane bows the other way
 * @param centre  the cell the fissure is set in
 * @param grip    the block of frame a hand takes, or {@code origin} when there is no hand
 * @param lifted  the burst third of the frame, which left the world as it fired and is drawn unravelling
 * @param elapsed ticks since it began
 * @param seed    the sundering's seed
 * @param hand    whether something reaches through
 * @param outcome what has become of it - see the constants
 * @param range   blocks from it within which it is shown; the server's to set
 * @param dry     the scene only: nothing in the world is changed, and the client pulls nobody
 */
public record ClientboundSundererCuePacket(BlockPos origin, RiftGateShape shape, Direction facing, BlockPos centre,
                                           BlockPos grip, java.util.List<BlockPos> lifted, int elapsed, long seed,
                                           boolean hand, int outcome,
                                           int range, boolean dry)
        implements CustomPacketPayload {

    /** Still going, or torn: which of the two the clock says. */
    public static final int RUNNING = 0;
    /** It stopped while reaching: the aperture closes and nothing else happens. */
    public static final int MISFIRED = 1;
    /** It failed after committing: the membrane blows out. */
    public static final int RUPTURED = 2;

    /** The most burst blocks a cue carries: a third of the largest ring's frame, with room to spare. */
    public static final int MOST_LIFTED = 64;

    public static final Type<ClientboundSundererCuePacket> TYPE = new Type<>(AeroWarptics.id("sunderer_cue"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSundererCuePacket> STREAM_CODEC =
            StreamCodec.of(ClientboundSundererCuePacket::encode, ClientboundSundererCuePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ClientboundSundererCuePacket packet) {
        buf.writeBlockPos(packet.origin);
        RiftGateShape.STREAM_CODEC.encode(buf, packet.shape);
        buf.writeVarInt(packet.facing.get2DDataValue());
        buf.writeBlockPos(packet.centre);
        buf.writeBlockPos(packet.grip);
        buf.writeVarInt(Math.min(MOST_LIFTED, packet.lifted.size()));
        for (int index = 0; index < Math.min(MOST_LIFTED, packet.lifted.size()); index++) {
            buf.writeBlockPos(packet.lifted.get(index));
        }
        buf.writeVarInt(packet.elapsed);
        buf.writeLong(packet.seed);
        buf.writeBoolean(packet.hand);
        buf.writeVarInt(packet.outcome);
        buf.writeVarInt(packet.range);
        buf.writeBoolean(packet.dry);
    }

    private static ClientboundSundererCuePacket decode(RegistryFriendlyByteBuf buf) {
        BlockPos origin = buf.readBlockPos();
        RiftGateShape shape = RiftGateShape.STREAM_CODEC.decode(buf);
        Direction facing = Direction.from2DDataValue(Math.floorMod(buf.readVarInt(), 4));
        BlockPos centre = buf.readBlockPos();
        BlockPos grip = buf.readBlockPos();
        int count = Math.max(0, Math.min(MOST_LIFTED, buf.readVarInt()));
        java.util.List<BlockPos> lifted = new java.util.ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            lifted.add(buf.readBlockPos());
        }
        int elapsed = Math.max(0, Math.min(24_000, buf.readVarInt()));
        long seed = buf.readLong();
        boolean hand = buf.readBoolean();
        int outcome = Math.max(RUNNING, Math.min(RUPTURED, buf.readVarInt()));
        int range = Math.max(16, Math.min(1_024, buf.readVarInt()));
        return new ClientboundSundererCuePacket(origin, shape, facing, centre, grip, java.util.List.copyOf(lifted),
                elapsed, seed, hand, outcome, range, buf.readBoolean());
    }

    public static void handle(ClientboundSundererCuePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> AWClientHooks.sundererCue(packet));
    }
}
