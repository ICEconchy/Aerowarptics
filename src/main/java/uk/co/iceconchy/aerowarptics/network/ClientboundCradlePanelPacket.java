package uk.co.iceconchy.aerowarptics.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.collapse.CradlePanel;

import java.util.ArrayList;
import java.util.List;

/**
 * A Collapse Cradle's panel, to the one player who has it open - see {@link CradlePanel}. Bounded: at most
 * {@link CradlePanel#MOST_OFFERINGS} offerings, checked before anything is allocated, and a short outcome name.
 */
public record ClientboundCradlePanelPacket(CradlePanel panel) implements CustomPacketPayload {

    public static final Type<ClientboundCradlePanelPacket> TYPE = new Type<>(AeroWarptics.id("cradle_panel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundCradlePanelPacket> STREAM_CODEC =
            StreamCodec.of(ClientboundCradlePanelPacket::encode, ClientboundCradlePanelPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ClientboundCradlePanelPacket packet) {
        CradlePanel panel = packet.panel;
        buf.writeBlockPos(panel.pos());
        buf.writeVarInt(panel.mode());
        buf.writeVarInt(panel.next());
        buf.writeVarInt(panel.failure());
        buf.writeFloat(panel.progress());
        buf.writeFloat(panel.instability());
        buf.writeFloat(panel.heading());
        buf.writeBoolean(panel.held());
        buf.writeVarInt(panel.essence());
        buf.writeVarInt(panel.capacity());
        buf.writeVarInt(panel.essenceNeeded());
        buf.writeFloat(panel.speed());
        buf.writeFloat(panel.minimumSpeed());
        buf.writeFloat(panel.optimalSpeed());
        buf.writeVarInt(panel.stabilisers());
        buf.writeFloat(panel.leanX());
        buf.writeFloat(panel.leanZ());
        buf.writeFloat(panel.settleBelow());
        buf.writeFloat(panel.spitAbove());
        buf.writeVarInt(panel.offerings().size());
        for (CradlePanel.Offering offering : panel.offerings()) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, offering.stack());
            buf.writeByte(offering.x());
            buf.writeByte(offering.z());
        }
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, panel.result());
        buf.writeVarInt(panel.lastFailure());
        buf.writeUtf(panel.lastOutcome(), CradlePanel.OUTCOME_LENGTH);
    }

    private static ClientboundCradlePanelPacket decode(RegistryFriendlyByteBuf buf) {
        var pos = buf.readBlockPos();
        int mode = buf.readVarInt();
        int next = buf.readVarInt();
        int failure = buf.readVarInt();
        float progress = buf.readFloat();
        float instability = buf.readFloat();
        float heading = buf.readFloat();
        boolean held = buf.readBoolean();
        int essence = buf.readVarInt();
        int capacity = buf.readVarInt();
        int needed = buf.readVarInt();
        float speed = buf.readFloat();
        float minimum = buf.readFloat();
        float optimal = buf.readFloat();
        int stabilisers = buf.readVarInt();
        float leanX = buf.readFloat();
        float leanZ = buf.readFloat();
        float settle = buf.readFloat();
        float spit = buf.readFloat();
        int count = buf.readVarInt();
        if (count < 0 || count > CradlePanel.MOST_OFFERINGS) {
            throw new DecoderException("a cradle panel with " + count + " offerings");
        }
        List<CradlePanel.Offering> offerings = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            offerings.add(new CradlePanel.Offering(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readByte(), buf.readByte()));
        }
        ItemStack result = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        int lastFailure = buf.readVarInt();
        String lastOutcome = buf.readUtf(CradlePanel.OUTCOME_LENGTH);
        return new ClientboundCradlePanelPacket(new CradlePanel(pos, mode, next, failure, progress, instability,
                heading, held, essence, capacity, needed, speed, minimum, optimal, stabilisers, leanX, leanZ, settle,
                spit, offerings, result, lastFailure, lastOutcome));
    }

    public static void handle(ClientboundCradlePanelPacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> AWClientHooks.acceptCradlePanel(packet.panel()));
    }
}
