package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorAccess;
import uk.co.iceconchy.aerowarptics.chute.ChuteTransfer;
import uk.co.iceconchy.aerowarptics.chute.RiftChute;
import uk.co.iceconchy.aerowarptics.chute.RiftChuteBlockEntity;
import uk.co.iceconchy.aerowarptics.chute.RiftChuteRegistry;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;

import java.util.List;
import java.util.UUID;

/**
 * A Rift Chute's panel, as the server sees it.
 *
 * <p>Carries the chute's own state and the list of chutes it could bind to. The list is capped in
 * {@link RiftChuteRegistry#bindableFrom} rather than only here, because a list codec throws when it
 * is asked to <em>encode</em> more than its limit - so an uncapped list is a server-side crash rather
 * than a truncated screen.
 *
 * @param bindable    chutes this player may bind to, nearest first
 * @param total       how many were available before the list was capped, so the panel can say so
 * @param reasonIndex what the last transfer attempt decided, as an ordinal
 */
public record ClientboundChutePanelPacket(BlockPos chutePos,
                                          UUID chuteId,
                                          String name,
                                          @Nullable UUID partner,
                                          WarpAnchorAccess access,
                                          boolean owned,
                                          boolean aboard,
                                          boolean riftOpen,
                                          int essence,
                                          int capacity,
                                          int costPerItem,
                                          int reasonIndex,
                                          List<RiftChute> bindable,
                                          int total) implements CustomPacketPayload {

    public static final Type<ClientboundChutePanelPacket> TYPE =
            new Type<>(AeroWarptics.id("chute_panel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundChutePanelPacket> STREAM_CODEC =
            StreamCodec.of(ClientboundChutePanelPacket::encode, ClientboundChutePanelPacket::decode);

    public ChuteTransfer.Reason reason() {
        ChuteTransfer.Reason[] values = ChuteTransfer.Reason.values();
        return values[Math.floorMod(reasonIndex, values.length)];
    }

    public boolean isBound() {
        return partner != null;
    }

    /** Whether the list was cut short, so the panel can say "nearest 256 of 400" rather than lying. */
    public boolean truncated() {
        return total > bindable.size();
    }

    public static void sendTo(ServerPlayer player, ServerLevel level, RiftChuteBlockEntity chute) {
        RiftChuteRegistry registry = RiftChuteRegistry.get(level);
        // Clear out anything whose block has demonstrably gone before listing. A player looking at a
        // list of chutes that no longer exist is already standing at the thing that can prove it.
        registry.prune(level);
        RiftChute record = registry.byId(chute.chuteId());
        if (record == null) {
            return;
        }
        List<RiftChute> bindable =
                registry.bindableFrom(player, level.dimension(), record.id(), record.pos());
        int total = registry.filtered(other -> other.enabled()
                && other.isVisibleTo(player)
                && other.dimension().equals(level.dimension())
                && !other.id().equals(record.id())).size();

        AWNetwork.sendTo(player, new ClientboundChutePanelPacket(
                chute.getBlockPos(),
                record.id(),
                record.name(),
                record.partner(),
                record.access(),
                record.owner() == null || record.isOwnedBy(player.getUUID()) || player.hasPermissions(2),
                chute.aboard(),
                chute.isRiftOpen(),
                chute.contents().getAmount(),
                RiftChuteBlockEntity.CAPACITY,
                AWConfig.CHUTE_COST_PER_ITEM.get(),
                chute.lastReason().ordinal(),
                bindable,
                total));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ClientboundChutePanelPacket packet) {
        buf.writeBlockPos(packet.chutePos);
        buf.writeUUID(packet.chuteId);
        buf.writeUtf(packet.name, 64);
        buf.writeBoolean(packet.partner != null);
        if (packet.partner != null) {
            buf.writeUUID(packet.partner);
        }
        buf.writeEnum(packet.access);
        buf.writeBoolean(packet.owned);
        buf.writeBoolean(packet.aboard);
        buf.writeBoolean(packet.riftOpen);
        buf.writeVarInt(packet.essence);
        buf.writeVarInt(packet.capacity);
        buf.writeVarInt(packet.costPerItem);
        buf.writeVarInt(packet.reasonIndex);
        RiftChute.STREAM_CODEC.apply(ByteBufCodecs.list(RiftChuteRegistry.MAX_LISTED))
                .encode(buf, packet.bindable);
        buf.writeVarInt(packet.total);
    }

    private static ClientboundChutePanelPacket decode(RegistryFriendlyByteBuf buf) {
        return new ClientboundChutePanelPacket(
                buf.readBlockPos(),
                buf.readUUID(),
                buf.readUtf(64),
                buf.readBoolean() ? buf.readUUID() : null,
                buf.readEnum(WarpAnchorAccess.class),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                RiftChute.STREAM_CODEC.apply(ByteBufCodecs.list(RiftChuteRegistry.MAX_LISTED)).decode(buf),
                buf.readVarInt());
    }

    public static void handle(ClientboundChutePanelPacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> AWClientHooks.acceptChutePanel(packet));
    }
}
