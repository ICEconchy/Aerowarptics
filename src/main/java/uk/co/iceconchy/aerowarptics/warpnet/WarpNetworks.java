package uk.co.iceconchy.aerowarptics.warpnet;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchor;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorBlockEntity;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorRegistry;
import uk.co.iceconchy.aerowarptics.gate.RiftGate;
import uk.co.iceconchy.aerowarptics.gate.RiftGateRegistry;
import uk.co.iceconchy.aerowarptics.trace.Trace;

import java.util.List;
import java.util.UUID;

/**
 * Where networks meet the world: the server's limits, and what a change to a network means for the
 * machines on it.
 *
 * <p>{@link NetworkOrders} decides and records; this follows up. A network's members are in one file and
 * the machines on it are in two others and, for anchors, in their blocks as well - so a member leaving is
 * four things to keep in step, and they are kept in step here.
 */
public final class WarpNetworks {

    private WarpNetworks() {
    }

    /** The server's limits as they stand now. Read on the server only. */
    public static NetworkOrders.Caps caps() {
        return new NetworkOrders.Caps(AWConfig.ALLOW_ANCHOR_NETWORKS.get(), AWConfig.MAX_NETWORKS_PER_PLAYER.get(),
                AWConfig.MAX_NETWORK_MEMBERS.get());
    }

    /**
     * Makes a real network of a label typed before networks were real.
     *
     * <p>The old Network box kept whatever was typed into it and nothing ever read it. Such a label becomes
     * a network its machine's owner owns - or, if they are already in one of that name, that one. Who may
     * travel to the machine does not change: its access stays whatever it was. A label somebody else's
     * network already has is dropped, since joining a network is not something a stale label gets to do.
     *
     * @param machine what is adopting, for the trace
     * @return the network, or {@code null} when the label was dropped
     */
    @Nullable
    public static UUID adopt(ServerLevel level, String label, @Nullable UUID owner, String ownerName, String machine) {
        if (owner == null) {
            Trace.log(Trace.NETWORK, "{} dropped its old network label \"{}\": it has no owner to own one",
                    machine, label);
            return null;
        }
        NetworkOrders.Resolved resolved = NetworkOrders.resolve(WarpNetworkRegistry.get(level), label, owner, owner,
                ownerName, false, caps());
        Trace.log(Trace.NETWORK, "{} {} its old network label \"{}\"{}", machine,
                resolved.network() == null ? "dropped" : resolved.founded() ? "founded a network from" : "joined by",
                label, resolved.refused() ? " (" + resolved.notice().key() + ")" : "");
        return resolved.network();
    }

    /**
     * Takes machines off a network: all of them, for one disbanded, or one player's, for a member gone.
     *
     * <p>Anything set to Network access becomes Private as it comes off - see {@link WarpAnchor#withoutNetwork}.
     * The records are changed whether or not the blocks are loaded. A loaded anchor is told as well, because
     * its block is what re-asserts its record and would otherwise write the network straight back; an
     * unloaded one finds out for itself when it next loads ({@link NetworkRules#mayStay}).
     *
     * @param ownedBy only this player's machines, or {@code null} for every machine on the network
     * @return how many machines came off
     */
    public static int detach(MinecraftServer server, UUID network, @Nullable UUID ownedBy) {
        List<WarpAnchor> anchors = WarpAnchorRegistry.get(server).detach(anchor -> network.equals(anchor.network())
                && (ownedBy == null || anchor.isOwnedBy(ownedBy)));
        for (WarpAnchor anchor : anchors) {
            WarpAnchorBlockEntity block = loaded(server, anchor);
            if (block != null) {
                block.leaveNetwork();
            }
        }
        List<RiftGate> gates = RiftGateRegistry.get(server).detach(gate -> network.equals(gate.network())
                && (ownedBy == null || gate.isOwnedBy(ownedBy)));
        // A ward names networks by id too, and one that has gone must come off every ward that named it. Only
        // a disbanding: a ward may name a network its owner is not in, so a member leaving changes no ward.
        int wards = ownedBy == null ? uk.co.iceconchy.aerowarptics.ward.Wards.forget(server, network).size() : 0;
        if (wards > 0) {
            Trace.log(Trace.NETWORK, "{} ward(s) no longer name network {}", wards, network);
        }
        if (!anchors.isEmpty() || !gates.isEmpty()) {
            Trace.log(Trace.NETWORK, "{} anchor(s) and {} gate(s) of {} taken off network {}", anchors.size(),
                    gates.size(), ownedBy == null ? "anybody's" : Trace.who(ownedBy) + "'s", network);
        }
        return anchors.size() + gates.size() + wards;
    }

    /**
     * Tells every loaded anchor on a network to send its clients the network's name again, after a rename.
     *
     * <p>Nothing depends on it but what the goggles and the anchor's panel read - a machine knows its
     * network by id - so an unloaded anchor needs nothing and picks the new name up when it loads.
     */
    public static void renamed(MinecraftServer server, UUID network) {
        for (WarpAnchor anchor : WarpAnchorRegistry.get(server).filtered(a -> network.equals(a.network()))) {
            WarpAnchorBlockEntity block = loaded(server, anchor);
            if (block != null) {
                block.sendData();
            }
        }
    }

    /** An anchor's block, if it is in the world and loaded. Never loads it. */
    @Nullable
    private static WarpAnchorBlockEntity loaded(MinecraftServer server, WarpAnchor anchor) {
        ServerLevel level = server.getLevel(anchor.dimension());
        if (level == null || !level.isLoaded(anchor.pos())) {
            return null;
        }
        return level.getBlockEntity(anchor.pos()) instanceof WarpAnchorBlockEntity block
                && block.anchorId().equals(anchor.id()) ? block : null;
    }
}
