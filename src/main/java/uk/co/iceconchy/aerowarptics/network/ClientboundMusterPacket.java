package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A flotilla's muster, as its crews' clients draw it: the slots in the sky, the tethers between the
 * couplers, the hums and the escort pilot's station HUD.
 *
 * <p>Positions are sent in ship space - a coupler's block in its plot, the flagship's centre in its own
 * frame - and never in the world. The client turns them into world positions itself, every frame, from
 * the poses it already has for each ship, so a tether stays fastened to a coupler on a ship that is
 * moving between packets rather than being dragged along behind it half a second late.
 *
 * <p>Sent every half second while a muster is on. A client that stops hearing about one lets it lapse,
 * which is how a muster that ended out of earshot still clears.
 *
 * @param flagshipShip    the flagship's sub-level
 * @param flagshipCoupler the flagship's coupler, in its plot
 * @param centre          the flagship's hull centre in its own ship space
 * @param bowX            the flagship's bow in its ship space
 * @param bowZ            the same
 * @param phase           the muster's phase ordinal
 * @param countdown       0 to 1 through the countdown, while it runs
 * @param tolerance       the station tolerance, in blocks
 * @param flagshipTier    the flagship's drive tier index
 * @param flagshipTether  the flagship's tether colour
 */
public record ClientboundMusterPacket(UUID flagshipShip, BlockPos flagshipCoupler, Vec3 centre, int bowX, int bowZ,
                                      int phase, float countdown, float tolerance, int flagshipTier,
                                      int flagshipTether, List<Entry> escorts) implements CustomPacketPayload {

    /**
     * One escort.
     *
     * @param ship    its sub-level, or the nil id when out of contact
     * @param coupler its coupler, in its plot
     * @param number  its slot number, 1 up
     * @param status  a {@code StationStatus} ordinal
     * @param harmony 0 to 1
     * @param dRight  where the ship actually is, less its slot, in the formation's frame - what the escort
     *                pilot's HUD steers by; zero out of contact
     */
    public record Entry(UUID ship, BlockPos coupler, int number, double right, double up, double ahead,
                        float radius, float halfHeight, int status, float harmony, int tether, int tier,
                        boolean inStation, float dRight, float dUp, float dAhead) {
    }

    public static final Type<ClientboundMusterPacket> TYPE = new Type<>(AeroWarptics.id("muster"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundMusterPacket> STREAM_CODEC =
            StreamCodec.of(ClientboundMusterPacket::encode, ClientboundMusterPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ClientboundMusterPacket packet) {
        buf.writeUUID(packet.flagshipShip);
        buf.writeBlockPos(packet.flagshipCoupler);
        buf.writeDouble(packet.centre.x);
        buf.writeDouble(packet.centre.y);
        buf.writeDouble(packet.centre.z);
        buf.writeByte(packet.bowX);
        buf.writeByte(packet.bowZ);
        buf.writeVarInt(packet.phase);
        buf.writeFloat(packet.countdown);
        buf.writeFloat(packet.tolerance);
        buf.writeVarInt(packet.flagshipTier);
        buf.writeInt(packet.flagshipTether);
        List<Entry> escorts = packet.escorts.size() > PacketLists.MAX_ROWS
                ? packet.escorts.subList(0, PacketLists.MAX_ROWS) : packet.escorts;
        buf.writeVarInt(escorts.size());
        for (Entry entry : escorts) {
            buf.writeUUID(entry.ship);
            buf.writeBlockPos(entry.coupler);
            buf.writeVarInt(entry.number);
            buf.writeDouble(entry.right);
            buf.writeDouble(entry.up);
            buf.writeDouble(entry.ahead);
            buf.writeFloat(entry.radius);
            buf.writeFloat(entry.halfHeight);
            buf.writeVarInt(entry.status);
            buf.writeFloat(entry.harmony);
            buf.writeInt(entry.tether);
            buf.writeVarInt(entry.tier);
            buf.writeBoolean(entry.inStation);
            buf.writeFloat(entry.dRight);
            buf.writeFloat(entry.dUp);
            buf.writeFloat(entry.dAhead);
        }
    }

    private static ClientboundMusterPacket decode(RegistryFriendlyByteBuf buf) {
        UUID flagship = buf.readUUID();
        BlockPos coupler = buf.readBlockPos();
        Vec3 centre = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        int bowX = buf.readByte();
        int bowZ = buf.readByte();
        int phase = buf.readVarInt();
        float countdown = buf.readFloat();
        float tolerance = buf.readFloat();
        int tier = buf.readVarInt();
        int tether = buf.readInt();
        int count = buf.readVarInt();
        // Checked before anything is allocated for it: a count is a number the sender chose.
        if (count < 0 || count > PacketLists.MAX_ROWS) {
            throw new IllegalArgumentException("muster lists " + count + " escorts");
        }
        List<Entry> escorts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            escorts.add(new Entry(buf.readUUID(), buf.readBlockPos(), buf.readVarInt(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readVarInt(),
                    buf.readFloat(), buf.readInt(), buf.readVarInt(), buf.readBoolean(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat()));
        }
        return new ClientboundMusterPacket(flagship, coupler, centre, Integer.signum(bowX), Integer.signum(bowZ),
                phase, countdown, tolerance, tier, tether, List.copyOf(escorts));
    }

    public static void handle(ClientboundMusterPacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> AWClientHooks.acceptMuster(packet));
    }
}
