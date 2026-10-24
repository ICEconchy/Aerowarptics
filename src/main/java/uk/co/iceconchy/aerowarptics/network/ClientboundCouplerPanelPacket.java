package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.flotilla.CouplerRole;
import uk.co.iceconchy.aerowarptics.flotilla.FlagshipStatus;
import uk.co.iceconchy.aerowarptics.flotilla.StationStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A Rift Coupler's panel: the Fleet Board for a flagship, the Station screen for an escort.
 *
 * <p>One packet for both, because a coupler is one block whose role can change while its screen is
 * open, and the client has to be able to swap one screen for the other on the next refresh. The fields
 * that do not apply to a role are sent empty. Names are sent raw - an unnamed ship's name is blank, and
 * the client supplies "Unnamed vessel" in the player's own language.
 *
 * @param maySteer      whether this player may give this coupler orders
 * @param essence       millibuckets in the coupler's tank
 * @param status        a {@link FlagshipStatus} ordinal for a flagship, a {@link StationStatus} one for an escort
 * @param phase         the muster's phase ordinal
 * @param destination   the flagship's course, or the flagship's name on the Station screen
 * @param distance      the flagship's jump in blocks, or -1 with no course
 * @param cap           the flagship's escort cap, -1 unlimited
 * @param sharedRift    whether the flotilla folds through one rift the flagship tears for all of it
 * @param essenceNeeded what a fold with every escort asks for
 * @param width         the formation's width, in blocks
 * @param height        its height
 * @param length        its length
 * @param foldable      whether every row is ready
 * @param charge        this coupler's own drive's charge, 0 to 1
 * @param radius        this coupler's own ship's footprint radius
 * @param rows          a flagship's escorts; an escort's own row alone, as it stands in its flagship's muster
 * @param requests      ships waiting on a flagship's owner
 * @param nearby        flagships an escort may take station with
 * @param flagship      the flagship an escort follows, or nil
 * @param pending       the flagship an escort is waiting on, or nil
 * @param slot          an escort's slot number, 1 up, or 0
 * @param notice        something to tell this player about what they just asked for, as a translation key
 *                      without the mod's prefix, or blank - see {@code CouplerNotice}
 * @param noticeArg     the ship that notice names, raw
 */
public record ClientboundCouplerPanelPacket(BlockPos couplerPos, CouplerRole role, boolean linked, boolean aboard,
                                            boolean maySteer, int tier, int essence, int capacity, String shipName,
                                            float tolerance, int status, int phase, String destination,
                                            float distance, int cap, boolean autoAccept, boolean beaconCallsAll,
                                            boolean sharedRift, int essenceNeeded, float width, float height, float length,
                                            boolean foldable, float charge, float radius, List<Row> rows,
                                            List<Request> requests, List<Nearby> nearby, UUID flagship,
                                            UUID pending, int slot, String notice, String noticeArg)
        implements CustomPacketPayload {

    public static final UUID NIL = new UUID(0L, 0L);

    /** Longest name the wire carries. Sable's own names can be longer; they are shown cut. */
    public static final int NAME_LENGTH = 48;

    /** Longest notice key the wire carries. Every one is a short key of this mod's own. */
    public static final int NOTICE_LENGTH = 64;

    /** The same panel with a notice for the player it is going to. */
    public ClientboundCouplerPanelPacket withNotice(String key, String arg) {
        return new ClientboundCouplerPanelPacket(couplerPos, role, linked, aboard, maySteer, tier, essence, capacity,
                shipName, tolerance, status, phase, destination, distance, cap, autoAccept, beaconCallsAll,
                sharedRift, essenceNeeded, width, height, length, foldable, charge, radius, rows, requests, nearby,
                flagship, pending, slot, key == null ? "" : key, arg == null ? "" : arg);
    }

    /**
     * One escort on the Fleet Board.
     *
     * @param right       its slot, in the formation's frame
     * @param dRight      where it actually is, less its slot; zero out of contact
     * @param contact     whether it could be read at all
     */
    public record Row(UUID coupler, String name, float right, float up, float ahead, float dRight, float dUp,
                      float dAhead, boolean contact, float radius, int status, float drift, float charge,
                      float harmony, int tether) {

        public StationStatus statusValue() {
            return StationStatus.byIndex(status);
        }
    }

    /** A ship asking to take station. */
    public record Request(UUID coupler, String name, float distance) {
    }

    /** A flagship an escort might join. */
    public record Nearby(UUID coupler, String name, float distance, int escorts, int cap) {

        public boolean full() {
            return cap >= 0 && escorts >= cap;
        }
    }

    public static final Type<ClientboundCouplerPanelPacket> TYPE = new Type<>(AeroWarptics.id("coupler_panel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundCouplerPanelPacket> STREAM_CODEC =
            StreamCodec.of(ClientboundCouplerPanelPacket::encode, ClientboundCouplerPanelPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public FlagshipStatus flagshipStatus() {
        return FlagshipStatus.byIndex(status);
    }

    public StationStatus stationStatus() {
        return StationStatus.byIndex(status);
    }

    private static String cut(String text) {
        String safe = text == null ? "" : text;
        return safe.length() > NAME_LENGTH ? safe.substring(0, NAME_LENGTH) : safe;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ClientboundCouplerPanelPacket p) {
        buf.writeBlockPos(p.couplerPos);
        buf.writeEnum(p.role);
        buf.writeBoolean(p.linked);
        buf.writeBoolean(p.aboard);
        buf.writeBoolean(p.maySteer);
        buf.writeVarInt(p.tier);
        buf.writeVarInt(p.essence);
        buf.writeVarInt(p.capacity);
        buf.writeUtf(cut(p.shipName), NAME_LENGTH);
        buf.writeFloat(p.tolerance);
        buf.writeVarInt(p.status);
        buf.writeVarInt(p.phase);
        buf.writeUtf(cut(p.destination), NAME_LENGTH);
        buf.writeFloat(p.distance);
        buf.writeVarInt(p.cap);
        buf.writeBoolean(p.autoAccept);
        buf.writeBoolean(p.beaconCallsAll);
        buf.writeBoolean(p.sharedRift);
        buf.writeVarInt(p.essenceNeeded);
        buf.writeFloat(p.width);
        buf.writeFloat(p.height);
        buf.writeFloat(p.length);
        buf.writeBoolean(p.foldable);
        buf.writeFloat(p.charge);
        buf.writeFloat(p.radius);
        List<Row> rows = capped(p.rows);
        buf.writeVarInt(rows.size());
        for (Row row : rows) {
            buf.writeUUID(row.coupler);
            buf.writeUtf(cut(row.name), NAME_LENGTH);
            buf.writeFloat(row.right);
            buf.writeFloat(row.up);
            buf.writeFloat(row.ahead);
            buf.writeFloat(row.dRight);
            buf.writeFloat(row.dUp);
            buf.writeFloat(row.dAhead);
            buf.writeBoolean(row.contact);
            buf.writeFloat(row.radius);
            buf.writeVarInt(row.status);
            buf.writeFloat(row.drift);
            buf.writeFloat(row.charge);
            buf.writeFloat(row.harmony);
            buf.writeInt(row.tether);
        }
        List<Request> requests = capped(p.requests);
        buf.writeVarInt(requests.size());
        for (Request request : requests) {
            buf.writeUUID(request.coupler);
            buf.writeUtf(cut(request.name), NAME_LENGTH);
            buf.writeFloat(request.distance);
        }
        List<Nearby> nearby = capped(p.nearby);
        buf.writeVarInt(nearby.size());
        for (Nearby entry : nearby) {
            buf.writeUUID(entry.coupler);
            buf.writeUtf(cut(entry.name), NAME_LENGTH);
            buf.writeFloat(entry.distance);
            buf.writeVarInt(entry.escorts);
            buf.writeVarInt(entry.cap);
        }
        buf.writeUUID(p.flagship);
        buf.writeUUID(p.pending);
        buf.writeVarInt(p.slot);
        String notice = p.notice == null ? "" : p.notice;
        buf.writeUtf(notice.length() > NOTICE_LENGTH ? "" : notice, NOTICE_LENGTH);
        buf.writeUtf(cut(p.noticeArg), NAME_LENGTH);
    }

    private static ClientboundCouplerPanelPacket decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        CouplerRole role = buf.readEnum(CouplerRole.class);
        boolean linked = buf.readBoolean();
        boolean aboard = buf.readBoolean();
        boolean maySteer = buf.readBoolean();
        int tier = buf.readVarInt();
        int essence = buf.readVarInt();
        int capacity = buf.readVarInt();
        String shipName = buf.readUtf(NAME_LENGTH);
        float tolerance = buf.readFloat();
        int status = buf.readVarInt();
        int phase = buf.readVarInt();
        String destination = buf.readUtf(NAME_LENGTH);
        float distance = buf.readFloat();
        int cap = buf.readVarInt();
        boolean autoAccept = buf.readBoolean();
        boolean beaconCallsAll = buf.readBoolean();
        boolean sharedRift = buf.readBoolean();
        int essenceNeeded = buf.readVarInt();
        float width = buf.readFloat();
        float height = buf.readFloat();
        float length = buf.readFloat();
        boolean foldable = buf.readBoolean();
        float charge = buf.readFloat();
        float radius = buf.readFloat();
        int rowCount = count(buf);
        List<Row> rows = new ArrayList<>(rowCount);
        for (int i = 0; i < rowCount; i++) {
            rows.add(new Row(buf.readUUID(), buf.readUtf(NAME_LENGTH), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean(),
                    buf.readFloat(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readInt()));
        }
        int requestCount = count(buf);
        List<Request> requests = new ArrayList<>(requestCount);
        for (int i = 0; i < requestCount; i++) {
            requests.add(new Request(buf.readUUID(), buf.readUtf(NAME_LENGTH), buf.readFloat()));
        }
        int nearbyCount = count(buf);
        List<Nearby> nearby = new ArrayList<>(nearbyCount);
        for (int i = 0; i < nearbyCount; i++) {
            nearby.add(new Nearby(buf.readUUID(), buf.readUtf(NAME_LENGTH), buf.readFloat(), buf.readVarInt(),
                    buf.readVarInt()));
        }
        return new ClientboundCouplerPanelPacket(pos, role, linked, aboard, maySteer, tier, essence, capacity,
                shipName, tolerance, status, phase, destination, distance, cap, autoAccept, beaconCallsAll,
                sharedRift, essenceNeeded, width, height, length, foldable, charge, radius, List.copyOf(rows),
                List.copyOf(requests), List.copyOf(nearby), buf.readUUID(), buf.readUUID(), buf.readVarInt(),
                buf.readUtf(NOTICE_LENGTH), buf.readUtf(NAME_LENGTH));
    }

    /** A list count, checked before anything is allocated for it. */
    private static int count(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > PacketLists.MAX_ROWS) {
            throw new IllegalArgumentException("coupler panel lists " + count + " rows");
        }
        return count;
    }

    private static <T> List<T> capped(List<T> rows) {
        return rows.size() > PacketLists.MAX_ROWS ? rows.subList(0, PacketLists.MAX_ROWS) : rows;
    }

    public static void handle(ClientboundCouplerPanelPacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> AWClientHooks.acceptCouplerPanel(packet));
    }
}
