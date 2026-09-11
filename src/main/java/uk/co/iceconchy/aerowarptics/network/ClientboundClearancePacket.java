package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a drive's departure clearance for the one player who asked, via
 * {@code /aerowarptics warp clearance}.
 *
 * <p>Carries only what the wireframe needs: whether the overlay is on at all, the bare hull sweep, the
 * padded volume the check actually tests, the blocks found fouling it, and how long one snapshot lasts.
 * The command is a toggle, so while it is on the server resends this a few times a second to keep the
 * picture live as the ship moves; each snapshot outlives the refresh interval, so it neither flickers
 * nor lingers if the refreshes stop. An {@code active == false} packet clears the overlay at once.
 *
 * @param active   whether to show the overlay ({@code false} clears it; the boxes are then ignored)
 * @param core     the hull's own swept corridor, no padding
 * @param padded   the volume the clearance check widens to and tests
 * @param hits     the solid blocks standing in the padded volume (capped server-side)
 * @param duration ticks to keep this snapshot up before it fades on its own
 */
public record ClientboundClearancePacket(boolean active, List<AABB> core, List<AABB> padded,
                                         List<BlockPos> hits,
                                         int duration) implements CustomPacketPayload {

    public static final Type<ClientboundClearancePacket> TYPE =
            new Type<>(AeroWarptics.id("clearance"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundClearancePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeBoolean(packet.active);
                        writeBoxes(buf, packet.core);
                        writeBoxes(buf, packet.padded);
                        buf.writeVarInt(packet.hits.size());
                        for (BlockPos pos : packet.hits) {
                            buf.writeBlockPos(pos);
                        }
                        buf.writeVarInt(packet.duration);
                    },
                    buf -> {
                        boolean active = buf.readBoolean();
                        List<AABB> core = readBoxes(buf);
                        List<AABB> padded = readBoxes(buf);
                        int count = buf.readVarInt();
                        List<BlockPos> hits = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) {
                            hits.add(buf.readBlockPos());
                        }
                        return new ClientboundClearancePacket(active, core, padded, hits, buf.readVarInt());
                    });

    /** Show one snapshot of the corridor. */
    public static ClientboundClearancePacket on(List<AABB> core, List<AABB> padded, List<BlockPos> hits,
                                                int duration) {
        return new ClientboundClearancePacket(true, core, padded, hits, duration);
    }

    /** Clear the overlay now. */
    public static ClientboundClearancePacket off() {
        return new ClientboundClearancePacket(false, List.of(), List.of(), List.of(), 0);
    }

    private static void writeBoxes(RegistryFriendlyByteBuf buf, List<AABB> boxes) {
        buf.writeVarInt(boxes.size());
        for (AABB box : boxes) {
            writeBox(buf, box);
        }
    }

    private static List<AABB> readBoxes(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<AABB> boxes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            boxes.add(readBox(buf));
        }
        return boxes;
    }

    private static void writeBox(RegistryFriendlyByteBuf buf, AABB box) {
        buf.writeDouble(box.minX);
        buf.writeDouble(box.minY);
        buf.writeDouble(box.minZ);
        buf.writeDouble(box.maxX);
        buf.writeDouble(box.maxY);
        buf.writeDouble(box.maxZ);
    }

    private static AABB readBox(RegistryFriendlyByteBuf buf) {
        return new AABB(buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClientboundClearancePacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> AWClientHooks.showClearance(packet));
    }
}
