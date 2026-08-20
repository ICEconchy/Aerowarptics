package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Vector3d;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.warp.WarpFlight;

import java.util.UUID;

/**
 * Tells nearby clients that the warp sequence reached a stage worth showing, and where.
 *
 * <p>This is a cue plus geometry, not a state transfer. The drive's authoritative state still arrives
 * through Create's normal block-entity sync; this payload carries the one thing that cannot be derived
 * client-side - where an aperture is, how big it is and which way it faces - so both ends of the
 * journey can be drawn even though only one of them is anywhere near the drive.
 *
 * @param drivePos  drive that fired the cue, in its own level's coordinates
 * @param stage     which cue
 * @param tierIndex tier, used to scale the effect
 * @param airship   the vessel this belongs to, so a client can tell whose rift it is looking at
 * @param centre    world centre of the aperture, or of the effect when there is no aperture
 * @param normal    unit vector the airship travels through the aperture along
 * @param radius    aperture radius in blocks
 * @param duration  ticks the cue runs for, where that is the server's to decide - how long an
 *                  aperture holds open, or how long a hull takes to pass through one
 * @param throat    how deep the aperture's throat runs, in blocks, signed along {@code normal}:
 *                  positive for an aperture a hull goes <em>into</em>, negative for one it comes out
 *                  of. Zero for a cue with no aperture behind it.
 */
public record ClientboundWarpEffectPacket(BlockPos drivePos,
                                          Stage stage,
                                          int tierIndex,
                                          UUID airship,
                                          Vec3 centre,
                                          Vec3 normal,
                                          double radius,
                                          int duration,
                                          float throat) implements CustomPacketPayload {

    /** Points in the sequence that have a distinct visual and audible signature. */
    public enum Stage {
        /** Destination accepted; the drive begins committing to it. */
        DESTINATION_LOCK,
        /** Rings aligning, energy gathering ahead of the bow. */
        STABILIZING,
        /** The entry aperture tears open. */
        RIFT_OPEN,
        /** A hull is passing through an aperture: the tear reacts to what is going through it. */
        RIFT_TRANSIT,
        /** The hull is through and running the corridor. */
        CORRIDOR,
        /** The hull is clear of an aperture and it can stop holding itself open. */
        RIFT_CLOSE,
        /** The far aperture opens and the hull comes out of it. */
        WARP_EXIT,
        /** The sequence was abandoned. */
        FAILED
    }

    private static final UUID NO_AIRSHIP = new UUID(0L, 0L);

    public static final Type<ClientboundWarpEffectPacket> TYPE =
            new Type<>(AeroWarptics.id("warp_effect"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundWarpEffectPacket> STREAM_CODEC =
            StreamCodec.of(ClientboundWarpEffectPacket::encode, ClientboundWarpEffectPacket::decode);

    /** A cue with a real aperture behind it. */
    public static ClientboundWarpEffectPacket rift(BlockPos drivePos, Stage stage, int tierIndex,
                                                   UUID airship, WarpFlight.Rift rift, int duration,
                                                   double throat) {
        return new ClientboundWarpEffectPacket(drivePos, stage, tierIndex, airship,
                toVec3(rift.centre()), toVec3(rift.normal()), rift.radius(), duration, (float) throat);
    }

    /** A cue with no aperture - sparks, a failure, a lock-on - anchored at a point. */
    public static ClientboundWarpEffectPacket at(BlockPos drivePos, Stage stage, int tierIndex,
                                                 UUID airship, Vec3 centre) {
        return new ClientboundWarpEffectPacket(drivePos, stage, tierIndex, airship,
                centre, new Vec3(0.0D, 1.0D, 0.0D), 0.0D, 0, 0.0F);
    }

    /** Whether this cue carries an aperture that should be drawn. */
    public boolean hasRift() {
        return radius > 0.0D;
    }

    private static Vec3 toVec3(Vector3d vec) {
        return new Vec3(vec.x, vec.y, vec.z);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ClientboundWarpEffectPacket packet) {
        buf.writeBlockPos(packet.drivePos);
        buf.writeEnum(packet.stage);
        buf.writeVarInt(packet.tierIndex);
        buf.writeUUID(packet.airship == null ? NO_AIRSHIP : packet.airship);
        buf.writeDouble(packet.centre.x);
        buf.writeDouble(packet.centre.y);
        buf.writeDouble(packet.centre.z);
        buf.writeFloat((float) packet.normal.x);
        buf.writeFloat((float) packet.normal.y);
        buf.writeFloat((float) packet.normal.z);
        buf.writeFloat((float) packet.radius);
        buf.writeVarInt(packet.duration);
        buf.writeFloat(packet.throat);
    }

    private static ClientboundWarpEffectPacket decode(RegistryFriendlyByteBuf buf) {
        return new ClientboundWarpEffectPacket(
                buf.readBlockPos(),
                buf.readEnum(Stage.class),
                buf.readVarInt(),
                buf.readUUID(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()),
                buf.readFloat(),
                buf.readVarInt(),
                buf.readFloat());
    }

    public static void handle(ClientboundWarpEffectPacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> AWClientHooks.playWarpEffect(packet));
    }
}
