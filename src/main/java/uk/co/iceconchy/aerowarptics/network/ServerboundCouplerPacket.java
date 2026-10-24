package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.flotilla.CouplerNotice;
import uk.co.iceconchy.aerowarptics.flotilla.CouplerPanels;
import uk.co.iceconchy.aerowarptics.flotilla.CouplerRole;
import uk.co.iceconchy.aerowarptics.flotilla.FleetNames;
import uk.co.iceconchy.aerowarptics.flotilla.JoinResult;
import uk.co.iceconchy.aerowarptics.flotilla.RiftCouplerBlockEntity;

import java.util.UUID;

/**
 * Everything a player can do standing at a Rift Coupler, as flagship or escort.
 *
 * <p>One payload for all of it, the Gate's reasoning: every action shares the same coupler, which has
 * to exist and be within reach. Whether the player may do the particular thing - command this coupler,
 * order this flotilla, take station with that flagship - is the block entity's to decide, on the server,
 * however the screen was built; this packet only carries what was asked.
 *
 * @param couplerPos the coupler, in its own level's coordinates
 * @param target     the other coupler an action is about, or nil
 * @param right      a slot's new starboard offset for {@link Action#MOVE_SLOT}
 * @param up         its new height
 * @param ahead      its new offset ahead
 * @param flag       the value for a toggle; the role for {@link Action#SET_ROLE} (true is flagship);
 *                   whether a {@link Action#FOLD} was forced
 * @param text       the new name for {@link Action#RENAME}
 */
public record ServerboundCouplerPacket(BlockPos couplerPos, Action action, UUID target, float right, float up,
                                       float ahead, boolean flag, String text) implements CustomPacketPayload {

    public enum Action {
        OPEN,
        SET_ROLE,
        TAKE_STATION,
        BREAK_OFF,
        ACCEPT,
        REFUSE,
        CAST_OFF,
        MOVE_SLOT,
        AUTO_ACCEPT,
        BEACON_CALLS_ALL,
        SOUND_STATIONS,
        FOLD,
        RENAME,
        SHARED_RIFT,
        STAND_DOWN,
        SLOT_HERE
    }

    private static final UUID NIL = new UUID(0L, 0L);

    public static final Type<ServerboundCouplerPacket> TYPE = new Type<>(AeroWarptics.id("coupler"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundCouplerPacket> STREAM_CODEC =
            StreamCodec.of(ServerboundCouplerPacket::encode, ServerboundCouplerPacket::decode);

    public static ServerboundCouplerPacket open(BlockPos pos) {
        return of(pos, Action.OPEN, NIL, false);
    }

    public static ServerboundCouplerPacket of(BlockPos pos, Action action, UUID target, boolean flag) {
        return new ServerboundCouplerPacket(pos, action, target == null ? NIL : target, 0.0F, 0.0F, 0.0F, flag, "");
    }

    public static ServerboundCouplerPacket moveSlot(BlockPos pos, UUID escort, float right, float up, float ahead) {
        return new ServerboundCouplerPacket(pos, Action.MOVE_SLOT, escort, right, up, ahead, false, "");
    }

    public static ServerboundCouplerPacket rename(BlockPos pos, String name) {
        return new ServerboundCouplerPacket(pos, Action.RENAME, NIL, 0.0F, 0.0F, 0.0F, false, name);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ServerboundCouplerPacket packet) {
        buf.writeBlockPos(packet.couplerPos);
        buf.writeEnum(packet.action);
        buf.writeUUID(packet.target == null ? NIL : packet.target);
        buf.writeFloat(packet.right);
        buf.writeFloat(packet.up);
        buf.writeFloat(packet.ahead);
        buf.writeBoolean(packet.flag);
        buf.writeUtf(packet.text == null ? "" : packet.text, FleetNames.MAX_LENGTH * 2);
    }

    private static ServerboundCouplerPacket decode(RegistryFriendlyByteBuf buf) {
        return new ServerboundCouplerPacket(buf.readBlockPos(), buf.readEnum(Action.class), buf.readUUID(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean(),
                buf.readUtf(FleetNames.MAX_LENGTH * 2));
    }

    public static void handle(ServerboundCouplerPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            RiftCouplerBlockEntity coupler = resolve(player, level, packet.couplerPos);
            if (coupler == null) {
                return;
            }
            UUID target = packet.target;
            // Whatever there is to say about this order is said on the screen it came from, which dims the
            // action bar out of sight - see CouplerNotice.
            CouplerNotice notice = switch (packet.action) {
                case OPEN -> CouplerNotice.NONE;
                case SET_ROLE -> {
                    coupler.setRole(player, packet.flag ? CouplerRole.FLAGSHIP : CouplerRole.ESCORT);
                    yield CouplerNotice.NONE;
                }
                case TAKE_STATION -> feedback(coupler.takeStation(player, target));
                case BREAK_OFF -> {
                    coupler.breakOff(player);
                    yield CouplerNotice.NONE;
                }
                case ACCEPT -> feedback(coupler.accept(player, target));
                case REFUSE -> {
                    coupler.refuse(player, target);
                    yield CouplerNotice.NONE;
                }
                case CAST_OFF -> {
                    coupler.castOff(player, target);
                    yield CouplerNotice.NONE;
                }
                case MOVE_SLOT -> coupler.moveSlot(player, target, packet.right, packet.up, packet.ahead);
                case SLOT_HERE -> coupler.slotWhereItIs(player, target);
                case AUTO_ACCEPT -> {
                    coupler.setAutoAccept(player, packet.flag);
                    yield CouplerNotice.NONE;
                }
                case BEACON_CALLS_ALL -> {
                    coupler.setBeaconCallsAll(player, packet.flag);
                    yield CouplerNotice.NONE;
                }
                case SHARED_RIFT -> {
                    coupler.setSharedRift(player, packet.flag);
                    yield CouplerNotice.NONE;
                }
                case SOUND_STATIONS -> {
                    coupler.soundStations(player);
                    yield CouplerNotice.NONE;
                }
                case STAND_DOWN -> {
                    coupler.standDown(player);
                    yield CouplerNotice.NONE;
                }
                case FOLD -> {
                    coupler.fold(player, packet.flag);
                    yield CouplerNotice.NONE;
                }
                case RENAME -> coupler.rename(player, packet.text) ? CouplerNotice.NONE
                        : CouplerNotice.of(CouplerNotice.NAME_TAKEN);
            };
            CouplerPanels.sendTo(player, coupler, notice);
        });
    }

    private static CouplerNotice feedback(JoinResult result) {
        return CouplerNotice.of(result.translationKey());
    }

    /**
     * Finds the coupler, having checked the player could be standing at it.
     *
     * <p>Measured the way a drive measures its pilot: a coupler aboard a ship has plot coordinates nothing
     * like the player's, so the player is taken into the ship's own space and measured there, where the
     * block really is. The Modulator skips the check aboard altogether; this does not, because a coupler
     * commands other people's ships as well as its own.
     */
    @Nullable
    private static RiftCouplerBlockEntity resolve(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof RiftCouplerBlockEntity coupler)) {
            return null;
        }
        double reach = AWConfig.MAX_INTERACTION_DISTANCE.get() + 2.0D;
        Airship ship = coupler.airship();
        Vec3 from = ship == null ? player.position() : ship.toShip(player.position());
        return from.distanceToSqr(Vec3.atCenterOf(pos)) <= reach * reach ? coupler : null;
    }
}
