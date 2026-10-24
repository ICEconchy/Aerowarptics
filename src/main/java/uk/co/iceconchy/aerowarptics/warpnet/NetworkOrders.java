package uk.co.iceconchy.aerowarptics.warpnet;

import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.trace.Trace;

import java.util.UUID;

/**
 * Everything that can be asked of a network, with who is asking - checked and carried out in one place.
 *
 * <p>The members panel, a machine's network box and the operator's commands all come through here, so
 * the answer to "may they?" cannot differ by which of the three somebody used. Each order answers with
 * a {@link NetworkNotice}: a refusal that says why, or what was done.
 *
 * <p>Takes ids and facts rather than players, and the registry rather than a server, so the whole of it
 * runs in a test. What an order means for the machines on a network - taking a leaver's off it - is the
 * caller's to follow up, in {@link WarpNetworks}.
 */
public final class NetworkOrders {

    private NetworkOrders() {
    }

    /**
     * The server's limits, as the orders need them.
     *
     * @param enabled    whether the server allows networks at all
     * @param maxOwned   most networks one player may own; nought or less for no limit
     * @param maxMembers most players a network may hold, its owner included; nought or less for no limit
     *                   short of {@link WarpNetwork#MAX_MEMBERS}
     */
    public record Caps(boolean enabled, int maxOwned, int maxMembers) {

        public static final Caps NONE = new Caps(true, 0, 0);
    }

    /**
     * What the name typed into a machine's network box came to.
     *
     * @param network the network the machine is to go on, or {@code null} for none
     * @param notice  why not, when it is a refusal
     * @param founded whether the name was a new one and a network was founded for it
     */
    public record Resolved(@Nullable UUID network, NetworkNotice notice, boolean founded) {

        public boolean refused() {
            return notice.refused();
        }
    }

    /**
     * Turns the name typed on a machine into the network that machine goes on.
     *
     * <p>Nothing typed is no network. A name a network already has joins it, if the machine's owner is in
     * that network. A name nothing has founds one, owned by whoever typed it.
     *
     * @param machineOwner who owns the machine being put on the network, or {@code null} for an unowned
     *                     one. It is the owner who has to be in the network, not whoever is at the panel:
     *                     an operator setting up somebody else's anchor must not leave it on a network
     *                     its own owner cannot use
     * @param actor        who typed the name
     */
    public static Resolved resolve(WarpNetworkRegistry registry, @Nullable String typed, @Nullable UUID machineOwner,
                                   UUID actor, String actorName, boolean operator, Caps caps) {
        String name = NetworkRules.clean(typed);
        if (name.isEmpty()) {
            return new Resolved(null, NetworkNotice.NONE, false);
        }
        if (!caps.enabled()) {
            return unresolved(actor, "put a machine on \"" + name + "\"", NetworkNotice.refusal(NetworkNotice.DISABLED));
        }
        UUID subject = machineOwner != null ? machineOwner : actor;
        WarpNetwork existing = registry.byName(name);
        if (existing != null) {
            if (!NetworkRules.mayAttach(existing, subject, machineOwner == null && operator)) {
                return unresolved(actor, "put a machine of " + Trace.who(subject) + "'s on \"" + existing.name() + "\"",
                        NetworkNotice.refusal(NetworkNotice.NOT_MEMBER, existing.name()));
            }
            return new Resolved(existing.id(), NetworkNotice.NONE, false);
        }
        if (!NetworkRules.validName(name)) {
            return unresolved(actor, "found \"" + name + "\"", NetworkNotice.refusal(NetworkNotice.NAME_INVALID));
        }
        // A network is founded by, and for, whoever typed its name - so only from a machine that is theirs
        // or nobody's. Founded from somebody else's, its first machine would belong to a player not in it.
        if (!subject.equals(actor)) {
            return unresolved(actor, "found \"" + name + "\" from a machine of " + Trace.who(subject) + "'s",
                    NetworkNotice.refusal(NetworkNotice.NOT_MEMBER, name));
        }
        if (!operator && NetworkRules.atCap(registry.countOwnedBy(actor), caps.maxOwned())) {
            return unresolved(actor, "found \"" + name + "\" (owns " + registry.countOwnedBy(actor) + ", the most is "
                    + caps.maxOwned() + ")", NetworkNotice.refusal(NetworkNotice.TOO_MANY));
        }
        WarpNetwork founded = registry.create(name, actor, actorName);
        if (founded == null) {
            return unresolved(actor, "found \"" + name + "\"", NetworkNotice.refusal(NetworkNotice.NAME_TAKEN, name));
        }
        Trace.log(Trace.NETWORK, "{} founded \"{}\" ({}); {} network(s) now", by(actor), founded.name(),
                founded.id(), registry.all().size());
        return new Resolved(founded.id(), NetworkNotice.NONE, true);
    }

    /**
     * Founds a network outright, for a named owner. The operator's command; a player founds one by typing
     * its name on a machine.
     */
    public static NetworkNotice found(WarpNetworkRegistry registry, @Nullable String typed, UUID owner,
                                      String ownerName) {
        String name = NetworkRules.clean(typed);
        if (!NetworkRules.validName(name)) {
            return NetworkNotice.refusal(NetworkNotice.NAME_INVALID);
        }
        WarpNetwork holder = registry.byName(name);
        if (holder != null || registry.create(name, owner, ownerName) == null) {
            return NetworkNotice.refusal(NetworkNotice.NAME_TAKEN, holder == null ? name : holder.name());
        }
        Trace.log(Trace.NETWORK, "an operator founded \"{}\" for {}", name, Trace.who(owner));
        return NetworkNotice.NONE;
    }

    public static NetworkNotice rename(WarpNetworkRegistry registry, UUID id, @Nullable UUID actor, boolean operator,
                                       @Nullable String typed) {
        WarpNetwork network = registry.byId(id);
        if (network == null) {
            return NetworkNotice.refusal(NetworkNotice.GONE);
        }
        if (!NetworkRules.mayManage(network, actor, operator)) {
            return refused(actor, "rename \"" + network.name() + "\"", NetworkNotice.refusal(NetworkNotice.NOT_OWNER));
        }
        String name = NetworkRules.clean(typed);
        if (!NetworkRules.validName(name)) {
            return refused(actor, "rename \"" + network.name() + "\" to \"" + name + "\"",
                    NetworkNotice.refusal(NetworkNotice.NAME_INVALID));
        }
        WarpNetwork holder = registry.byName(name);
        if ((holder != null && !holder.id().equals(id)) || !registry.rename(id, name)) {
            return refused(actor, "rename \"" + network.name() + "\" to \"" + name + "\"",
                    NetworkNotice.refusal(NetworkNotice.NAME_TAKEN, holder == null ? name : holder.name()));
        }
        Trace.log(Trace.NETWORK, "{} renamed \"{}\" to \"{}\" ({})", by(actor), network.name(), name, id);
        return NetworkNotice.done(NetworkNotice.RENAMED, name);
    }

    public static NetworkNotice add(WarpNetworkRegistry registry, UUID id, @Nullable UUID actor, boolean operator,
                                    UUID player, String playerName, Caps caps) {
        WarpNetwork network = registry.byId(id);
        if (network == null) {
            return NetworkNotice.refusal(NetworkNotice.GONE);
        }
        if (!NetworkRules.mayManage(network, actor, operator)) {
            return refused(actor, "add " + playerName + " to \"" + network.name() + "\"",
                    NetworkNotice.refusal(NetworkNotice.NOT_OWNER));
        }
        if (network.has(player)) {
            return NetworkNotice.refusal(NetworkNotice.ALREADY_MEMBER, playerName);
        }
        if (NetworkRules.atCap(network.size(), caps.maxMembers())
                || network.members().size() >= WarpNetwork.MAX_MEMBERS) {
            return refused(actor, "add " + playerName + " to \"" + network.name() + "\" (holds " + network.size()
                    + ", the most is " + caps.maxMembers() + ")", NetworkNotice.refusal(NetworkNotice.FULL));
        }
        registry.update(network.withMember(player, playerName));
        Trace.log(Trace.NETWORK, "{} added {} to \"{}\"; {} in it now", by(actor), playerName, network.name(),
                network.size() + 1);
        return NetworkNotice.done(NetworkNotice.ADDED, playerName);
    }

    /** Takes a member out. Their machines are the caller's to take off the network - see {@link WarpNetworks}. */
    public static NetworkNotice remove(WarpNetworkRegistry registry, UUID id, @Nullable UUID actor, boolean operator,
                                       UUID player) {
        WarpNetwork network = registry.byId(id);
        if (network == null) {
            return NetworkNotice.refusal(NetworkNotice.GONE);
        }
        if (!NetworkRules.mayManage(network, actor, operator)) {
            return refused(actor, "remove " + Trace.who(player) + " from \"" + network.name() + "\"",
                    NetworkNotice.refusal(NetworkNotice.NOT_OWNER));
        }
        return takeOut(registry, network, actor, player);
    }

    /** A member walking out. An owner cannot: a network with nobody to run it is one nobody can mend. */
    public static NetworkNotice leave(WarpNetworkRegistry registry, UUID id, UUID actor) {
        WarpNetwork network = registry.byId(id);
        if (network == null) {
            return NetworkNotice.refusal(NetworkNotice.GONE);
        }
        return takeOut(registry, network, actor, actor);
    }

    private static NetworkNotice takeOut(WarpNetworkRegistry registry, WarpNetwork network, @Nullable UUID actor,
                                         UUID player) {
        if (network.isOwnedBy(player)) {
            return NetworkNotice.refusal(NetworkNotice.OWNER_STAYS);
        }
        String known = network.members().get(player);
        if (known == null) {
            return NetworkNotice.refusal(NetworkNotice.NOT_MEMBER, network.name());
        }
        registry.update(network.withoutMember(player));
        Trace.log(Trace.NETWORK, "{} took {} out of \"{}\"; {} in it now", by(actor), known, network.name(),
                network.size() - 1);
        return NetworkNotice.done(NetworkNotice.REMOVED, known);
    }

    /** Hands a network to another player, keeping the old owner on as a member. The operator's command. */
    public static NetworkNotice transfer(WarpNetworkRegistry registry, UUID id, UUID newOwner, String newOwnerName) {
        WarpNetwork network = registry.byId(id);
        if (network == null) {
            return NetworkNotice.refusal(NetworkNotice.GONE);
        }
        registry.update(network.withOwner(newOwner, newOwnerName));
        Trace.log(Trace.NETWORK, "\"{}\" handed from {} to {}", network.name(), network.ownerName(), newOwnerName);
        return NetworkNotice.NONE;
    }

    /** Ends a network. Every machine on it is the caller's to take off it - see {@link WarpNetworks}. */
    public static NetworkNotice disband(WarpNetworkRegistry registry, UUID id, @Nullable UUID actor,
                                        boolean operator) {
        WarpNetwork network = registry.byId(id);
        if (network == null) {
            return NetworkNotice.refusal(NetworkNotice.GONE);
        }
        if (!NetworkRules.mayManage(network, actor, operator)) {
            return refused(actor, "disband \"" + network.name() + "\"", NetworkNotice.refusal(NetworkNotice.NOT_OWNER));
        }
        registry.remove(id);
        Trace.log(Trace.NETWORK, "{} disbanded \"{}\" ({}, {} in it)", by(actor), network.name(), id,
                network.size());
        return NetworkNotice.NONE;
    }

    private static Resolved unresolved(UUID actor, String what, NetworkNotice notice) {
        return new Resolved(null, refused(actor, what, notice), false);
    }

    /** Who gave an order, for the trace. Only the console and a command block give one with no id. */
    private static String by(@Nullable UUID actor) {
        return actor == null ? "an operator" : Trace.who(actor);
    }

    /** A refusal, traced with who was refused and what they were trying to do. */
    private static NetworkNotice refused(@Nullable UUID actor, String what, NetworkNotice notice) {
        Trace.log(Trace.NETWORK, "{} may not {}: {}", by(actor), what, notice.key());
        return notice;
    }
}
