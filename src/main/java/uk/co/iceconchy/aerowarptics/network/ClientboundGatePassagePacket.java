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
 * Says that somebody has gone into a gate passage, or come out of one.
 *
 * <p>One payload for two audiences, told apart by who is reading it. Sent to the traveller, it is their
 * cue: draw the passage over the view, and later, let the far side through. Sent to everybody else near
 * the far gate, it names somebody not to draw - the traveller is already standing there, held, for as
 * long as the passage lasts, and a suit of armour hanging in a gateway for three seconds is not what
 * coming through a gate looks like from outside.
 *
 * <p>The traveller is named by entity id rather than left implied, because of that second audience, and
 * because a client can only be sure a cue is its own by reading whose it is.
 *
 * @param phase    which end of the passage this is
 * @param entityId the traveller, as this client knows them
 * @param ticks    how long the server expects the passage to last, for {@link Phase#ENTER}. A forecast:
 *                 the client aims its flare at it and then waits for {@link Phase#ARRIVE}
 */
public record ClientboundGatePassagePacket(Phase phase, int entityId, int ticks) implements CustomPacketPayload {

    public enum Phase {
        /** The traveller has gone into the near gate, and is already - unseen - at the far one. */
        ENTER,
        /** The passage is over: they are through, and may be seen, and may see. */
        ARRIVE
    }

    /** The longest passage a client will believe, in ticks. Far beyond anything the server config allows. */
    public static final int MAX_TICKS = 1_200;

    public static final Type<ClientboundGatePassagePacket> TYPE = new Type<>(AeroWarptics.id("gate_passage"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundGatePassagePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeEnum(packet.phase);
                        buf.writeVarInt(packet.entityId);
                        buf.writeVarInt(packet.ticks);
                    },
                    buf -> new ClientboundGatePassagePacket(buf.readEnum(Phase.class), buf.readVarInt(),
                            Math.max(0, Math.min(MAX_TICKS, buf.readVarInt()))));

    public static ClientboundGatePassagePacket enter(int entityId, int ticks) {
        return new ClientboundGatePassagePacket(Phase.ENTER, entityId, Math.max(0, Math.min(MAX_TICKS, ticks)));
    }

    public static ClientboundGatePassagePacket arrive(int entityId) {
        return new ClientboundGatePassagePacket(Phase.ARRIVE, entityId, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClientboundGatePassagePacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> AWClientHooks.gatePassage(packet));
    }
}
