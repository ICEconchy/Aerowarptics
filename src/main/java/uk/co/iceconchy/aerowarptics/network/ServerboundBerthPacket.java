package uk.co.iceconchy.aerowarptics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchor;
import uk.co.iceconchy.aerowarptics.berth.Berth;
import uk.co.iceconchy.aerowarptics.berth.BerthBlockEntity;
import uk.co.iceconchy.aerowarptics.berth.BerthReserve;
import uk.co.iceconchy.aerowarptics.berth.BerthRules;
import uk.co.iceconchy.aerowarptics.berth.Berths;
import uk.co.iceconchy.aerowarptics.registry.AWSounds;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkRules;
import uk.co.iceconchy.aerowarptics.warpnet.WarpNetwork;
import uk.co.iceconchy.aerowarptics.warpnet.WarpNetworkRegistry;

import java.util.UUID;

/**
 * A player's order at a Berth's panel: its settings saved, its ship cast off, or its anchor looked for again.
 *
 * <p>The server re-checks who is asking, holds every figure to its own ceiling and looks the network up for
 * itself; the client's copy of the berth is never authoritative. Every answer goes to the action bar, which
 * is in view again by the time it is read: each of these closes the panel.
 *
 * @param network the network a berth kept for one is kept for, by name as typed; a name no network has, or one
 *                the player is not in, refuses the edit - nothing is founded from here, and a berth cannot be
 *                handed to a network its owner could not use it through
 */
public record ServerboundBerthPacket(BlockPos pos, Action action, String name, int offsetX, int offsetY, int offsetZ,
                                     int length, int width, int height, int yaw, int priority, boolean enabled,
                                     boolean sternFirst, boolean tractor, BerthReserve reserve, String network)
        implements CustomPacketPayload {

    /** What is being asked. Sent by ordinal, so a new one is appended. */
    public enum Action {
        SAVE, CAST_OFF, RELINK;

        static Action byIndex(int index) {
            return index >= 0 && index < values().length ? values()[index] : SAVE;
        }
    }

    public static final Type<ServerboundBerthPacket> TYPE = new Type<>(AeroWarptics.id("berth"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundBerthPacket> STREAM_CODEC =
            StreamCodec.of(ServerboundBerthPacket::encode, ServerboundBerthPacket::decode);

    /** An order that changes no setting: cast off, or look for an anchor. */
    public static ServerboundBerthPacket order(BlockPos pos, Action action) {
        return new ServerboundBerthPacket(pos, action, "", 0, 0, 0, 0, 0, 0, 0, 0, false, false, false,
                BerthReserve.ANYONE, "");
    }

    private static void encode(RegistryFriendlyByteBuf buf, ServerboundBerthPacket packet) {
        buf.writeBlockPos(packet.pos);
        buf.writeVarInt(packet.action.ordinal());
        buf.writeUtf(packet.name, BerthRules.NAME_LENGTH);
        buf.writeVarInt(packet.offsetX);
        buf.writeVarInt(packet.offsetY);
        buf.writeVarInt(packet.offsetZ);
        buf.writeVarInt(packet.length);
        buf.writeVarInt(packet.width);
        buf.writeVarInt(packet.height);
        buf.writeVarInt(packet.yaw);
        buf.writeVarInt(packet.priority);
        buf.writeBoolean(packet.enabled);
        buf.writeBoolean(packet.sternFirst);
        buf.writeBoolean(packet.tractor);
        buf.writeVarInt(packet.reserve.ordinal());
        buf.writeUtf(packet.network, 32);
    }

    private static ServerboundBerthPacket decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Action action = Action.byIndex(buf.readVarInt());
        String name = buf.readUtf(BerthRules.NAME_LENGTH);
        int offsetX = buf.readVarInt();
        int offsetY = buf.readVarInt();
        int offsetZ = buf.readVarInt();
        int length = buf.readVarInt();
        int width = buf.readVarInt();
        int height = buf.readVarInt();
        int yaw = buf.readVarInt();
        int priority = buf.readVarInt();
        boolean enabled = buf.readBoolean();
        boolean sternFirst = buf.readBoolean();
        boolean tractor = buf.readBoolean();
        int reserve = buf.readVarInt();
        BerthReserve[] reserves = BerthReserve.values();
        return new ServerboundBerthPacket(pos, action, name, offsetX, offsetY, offsetZ, length, width, height, yaw,
                priority, enabled, sternFirst, tractor,
                reserve >= 0 && reserve < reserves.length ? reserves[reserve] : BerthReserve.ANYONE, buf.readUtf(32));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ServerboundBerthPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            BlockPos pos = packet.pos();
            // Further than a block is reached from: the panel's camera is flown out, but the player has not moved.
            double reach = AWConfig.MAX_INTERACTION_DISTANCE.get() + 4.0D;
            if (!level.isLoaded(pos) || player.blockPosition().distSqr(pos) > reach * reach) {
                return;
            }
            if (!(level.getBlockEntity(pos) instanceof BerthBlockEntity block) || block.berth() == null) {
                return;
            }
            Berth berth = block.berth();
            MinecraftServer server = level.getServer();
            switch (packet.action()) {
                case CAST_OFF -> castOff(server, level, player, block, berth);
                case RELINK -> relink(level, player, block, berth);
                case SAVE -> save(level, player, block, berth, packet);
            }
        });
    }

    /** Its owner may cast anything off; so may anybody aboard the ship it holds, which is never a berth's to trap. */
    private static void castOff(MinecraftServer server, ServerLevel level, ServerPlayer player, BerthBlockEntity block,
                                Berth berth) {
        if (!berth.state().holds()) {
            return;
        }
        Airship aboard = Airship.aboard(player);
        boolean crew = aboard != null && aboard.vessel().stream().anyMatch(member -> berth.concerns(member.uuid()));
        if (!block.mayEdit(player) && !crew) {
            Trace.log(Trace.BERTH, "{} may not cast off at the berth at {}: it is {}'s and they are not aboard",
                    Trace.who(player), Trace.pos(berth.pos()), berth.ownerName());
            AWLang.translate("message.berth_not_yours").sendStatus(player);
            return;
        }
        Berths.castOff(server, berth, Trace.who(player) + " cast it off at the panel");
        AWLang.translate("message.berth_cast_off").sendStatus(player);
    }

    private static void relink(ServerLevel level, ServerPlayer player, BerthBlockEntity block, Berth berth) {
        if (!block.mayEdit(player)) {
            AWLang.translate("message.berth_not_yours").sendStatus(player);
            return;
        }
        // Its owner's anchors, not the asker's: an operator tidying a station does not take it over.
        UUID owner = berth.owner() != null ? berth.owner() : player.getUUID();
        WarpAnchor anchor = Berths.nearestAnchor(level, berth.pos(), owner);
        if (anchor == null) {
            AWLang.translate("gui.berth.placed_unlinked", AWConfig.BERTH_LINK_REACH.get()).sendStatus(player);
            return;
        }
        block.take(block.berth().withAnchor(anchor.id()));
        AWLang.translate("gui.berth.placed_linked", anchor.displayName()).sendStatus(player);
    }

    private static void save(ServerLevel level, ServerPlayer player, BerthBlockEntity block, Berth berth,
                             ServerboundBerthPacket packet) {
        if (!block.mayEdit(player)) {
            Trace.log(Trace.BERTH, "{} may not set the berth at {}: it is {}'s", Trace.who(player),
                    Trace.pos(berth.pos()), berth.ownerName());
            AWLang.translate("message.berth_not_yours").sendStatus(player);
            return;
        }
        UUID network = null;
        if (packet.reserve() == BerthReserve.NETWORK) {
            String name = NetworkRules.clean(packet.network());
            WarpNetwork named = name.isEmpty() ? null : WarpNetworkRegistry.get(level).byName(name);
            if (named == null) {
                Trace.log(Trace.BERTH, "{}'s edit of the berth at {} refused: no network is called \"{}\"",
                        Trace.who(player), Trace.pos(berth.pos()), name);
                AWLang.translate("message.berth_no_such_network", name).sendStatus(player);
                return;
            }
            if (!WarpNetworkRegistry.get(level).isMember(named.id(), player.getUUID()) && !player.hasPermissions(2)) {
                Trace.log(Trace.BERTH, "{}'s edit of the berth at {} refused: they are not in \"{}\"",
                        Trace.who(player), Trace.pos(berth.pos()), name);
                AWLang.translate("message.berth_not_in_network", named.name()).sendStatus(player);
                return;
            }
            network = named.id();
        }
        Berth updated = berth.withSettings(packet.name(), packet.offsetX(), packet.offsetY(), packet.offsetZ(),
                BerthRules.size(packet.length(), AWConfig.BERTH_MAX_LENGTH.get()),
                BerthRules.size(packet.width(), AWConfig.BERTH_MAX_WIDTH.get()),
                BerthRules.size(packet.height(), AWConfig.BERTH_MAX_HEIGHT.get()),
                packet.yaw(), packet.priority(), packet.enabled(), packet.sternFirst(), packet.tractor(),
                packet.reserve(), network);
        Berths.changed(level.getServer(), berth, updated);
        level.playSound(null, berth.pos(), AWSounds.ANCHOR_CONFIGURED.get(), SoundSource.BLOCKS, 0.7F, 0.8F);
        AWLang.translate("message.berth_saved").sendStatus(player);
    }
}
