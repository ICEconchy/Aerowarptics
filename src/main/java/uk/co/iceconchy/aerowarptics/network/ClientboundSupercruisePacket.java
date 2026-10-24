package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;

/**
 * Tells one crew member how their ship's cruise is going, so their client can draw the starlines.
 *
 * <p>Addressed to the crew by name, for the same reason the corridor cue is: the server knows who is
 * aboard and a client guessing from Sable's tracking does not. Sent a few times a second while the
 * cruise lasts, which is also what lets somebody who boards mid-cruise see it - and what lets the
 * effect let go on its own if the ship stops sending, rather than streaking forever after a lost packet.
 *
 * @param phase      what has just happened
 * @param speed      how fast the ship is going, as a fraction of its top speed, 0 to 1
 * @param headingX   the heading's X, horizontal and unit length together with {@code headingZ}
 * @param headingZ   the heading's Z
 * @param colour     packed RGB the starlines are tinted towards - a Modulator's, or the tier's
 * @param intensity  a Modulator's intensity, or {@code 1.0}
 */
public record ClientboundSupercruisePacket(Phase phase, float speed, float headingX, float headingZ,
                                           int colour, float intensity) implements CustomPacketPayload {

    public enum Phase {
        /** The drive has just thrown the ship into cruise: the starlines stretch out of nowhere. */
        ENGAGE,
        /** Still cruising; here is the speed and heading now. */
        CRUISE,
        /** Letting go: the starlines draw back into points as the ship slows. */
        DROP,
        /** Over. Stop drawing. */
        LEAVE
    }

    public static final Type<ClientboundSupercruisePacket> TYPE = new Type<>(AeroWarptics.id("supercruise"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSupercruisePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeEnum(packet.phase);
                        buf.writeFloat(packet.speed);
                        buf.writeFloat(packet.headingX);
                        buf.writeFloat(packet.headingZ);
                        buf.writeInt(packet.colour);
                        buf.writeFloat(packet.intensity);
                    },
                    buf -> new ClientboundSupercruisePacket(buf.readEnum(Phase.class), buf.readFloat(),
                            buf.readFloat(), buf.readFloat(), buf.readInt(), buf.readFloat()));

    public static ClientboundSupercruisePacket leave() {
        return new ClientboundSupercruisePacket(Phase.LEAVE, 0.0F, 0.0F, 0.0F, 0, 1.0F);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClientboundSupercruisePacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> AWClientHooks.acceptSupercruise(packet));
    }
}
