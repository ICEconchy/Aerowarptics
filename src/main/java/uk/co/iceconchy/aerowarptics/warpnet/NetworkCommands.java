package uk.co.iceconchy.aerowarptics.warpnet;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorRegistry;
import uk.co.iceconchy.aerowarptics.gate.RiftGateRegistry;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code /aerowarptics network}: the members panel, for an operator.
 *
 * <p>Everything the panel does and three things it cannot: found a network for somebody else, hand one to
 * another owner, and add a player who is not online. All of it goes through {@link NetworkOrders}, the
 * same as the panel, with the operator's leave to manage a network that is not theirs.
 *
 * <ul>
 *   <li>{@code list} - every network, its owner and how many are in it.</li>
 *   <li>{@code info <name>} - who is in one, and how many anchors and gates are on it.</li>
 *   <li>{@code create <name> <owner>}, {@code rename <name> <to>}, {@code disband <name>}.</li>
 *   <li>{@code add <name> <players>}, {@code remove <name> <players>}, {@code owner <name> <player>}.</li>
 * </ul>
 *
 * <p>A name with a space in it goes in quotes.
 */
public final class NetworkCommands {

    private static final SuggestionProvider<CommandSourceStack> NAMES = (context, builder) ->
            SharedSuggestionProvider.suggest(WarpNetworkRegistry.get(context.getSource().getServer()).all().stream()
                    .map(network -> quoted(network.name())), builder);

    private NetworkCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("network")
                .requires(source -> source.hasPermission(2))
                .executes(NetworkCommands::list)
                .then(Commands.literal("list").executes(NetworkCommands::list))
                .then(Commands.literal("info")
                        .then(Commands.argument("name", StringArgumentType.string()).suggests(NAMES)
                                .executes(NetworkCommands::info)))
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .then(Commands.argument("owner", GameProfileArgument.gameProfile())
                                        .executes(NetworkCommands::create))))
                .then(Commands.literal("rename")
                        .then(Commands.argument("name", StringArgumentType.string()).suggests(NAMES)
                                .then(Commands.argument("to", StringArgumentType.string())
                                        .executes(NetworkCommands::rename))))
                .then(Commands.literal("add")
                        .then(Commands.argument("name", StringArgumentType.string()).suggests(NAMES)
                                .then(Commands.argument("players", GameProfileArgument.gameProfile())
                                        .executes(NetworkCommands::add))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.string()).suggests(NAMES)
                                .then(Commands.argument("players", GameProfileArgument.gameProfile())
                                        .executes(NetworkCommands::remove))))
                .then(Commands.literal("owner")
                        .then(Commands.argument("name", StringArgumentType.string()).suggests(NAMES)
                                .then(Commands.argument("owner", GameProfileArgument.gameProfile())
                                        .executes(NetworkCommands::transfer))))
                .then(Commands.literal("disband")
                        .then(Commands.argument("name", StringArgumentType.string()).suggests(NAMES)
                                .executes(NetworkCommands::disband)));
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Collection<WarpNetwork> all = WarpNetworkRegistry.get(source.getServer()).all();
        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.literal("There are no networks."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal(all.size() + " network(s):"), false);
        for (WarpNetwork network : all) {
            source.sendSuccess(() -> Component.literal("  " + network.name() + " - " + network.ownerName() + "'s, "
                    + network.size() + " in it"), false);
        }
        return all.size();
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        WarpNetwork network = named(context);
        if (network == null) {
            return 0;
        }
        UUID id = network.id();
        int anchors = WarpAnchorRegistry.get(server).filtered(anchor -> id.equals(anchor.network())).size();
        int gates = RiftGateRegistry.get(server).filtered(gate -> id.equals(gate.network())).size();
        String members = network.members().values().stream().sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));
        source.sendSuccess(() -> Component.literal(network.name() + " (" + id + ")\n  Owner: " + network.ownerName()
                + "\n  Members: " + (members.isEmpty() ? "none" : members)
                + "\n  On it: " + anchors + " anchor(s), " + gates + " gate(s)"), false);
        return network.size();
    }

    private static int create(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        GameProfile owner = one(context, "owner");
        if (owner == null) {
            return 0;
        }
        String name = StringArgumentType.getString(context, "name");
        NetworkNotice notice = NetworkOrders.found(WarpNetworkRegistry.get(source.getServer()), name, owner.getId(),
                owner.getName());
        return said(source, notice, "Founded " + NetworkRules.clean(name) + " for " + owner.getName() + ".");
    }

    private static int rename(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        WarpNetwork network = named(context);
        if (network == null) {
            return 0;
        }
        String to = StringArgumentType.getString(context, "to");
        NetworkNotice notice = NetworkOrders.rename(WarpNetworkRegistry.get(source.getServer()), network.id(),
                actor(source), true, to);
        if (!notice.refused()) {
            WarpNetworks.renamed(source.getServer(), network.id());
        }
        return said(source, notice, network.name() + " is now " + NetworkRules.clean(to) + ".");
    }

    private static int add(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        WarpNetwork network = named(context);
        if (network == null) {
            return 0;
        }
        int added = 0;
        for (GameProfile player : GameProfileArgument.getGameProfiles(context, "players")) {
            NetworkNotice notice = NetworkOrders.add(WarpNetworkRegistry.get(source.getServer()), network.id(),
                    actor(source), true, player.getId(), player.getName(), WarpNetworks.caps());
            added += said(source, notice, player.getName() + " added to " + network.name() + ".");
        }
        return added;
    }

    private static int remove(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        WarpNetwork network = named(context);
        if (network == null) {
            return 0;
        }
        int removed = 0;
        for (GameProfile player : GameProfileArgument.getGameProfiles(context, "players")) {
            NetworkNotice notice = NetworkOrders.remove(WarpNetworkRegistry.get(server), network.id(), actor(source),
                    true, player.getId());
            int machines = notice.refused() ? 0 : WarpNetworks.detach(server, network.id(), player.getId());
            removed += said(source, notice, player.getName() + " removed from " + network.name() + ", and "
                    + machines + " of their machine(s) taken off it.");
        }
        return removed;
    }

    private static int transfer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        WarpNetwork network = named(context);
        GameProfile owner = network == null ? null : one(context, "owner");
        if (owner == null) {
            return 0;
        }
        NetworkNotice notice = NetworkOrders.transfer(WarpNetworkRegistry.get(source.getServer()), network.id(),
                owner.getId(), owner.getName());
        return said(source, notice, network.name() + " is " + owner.getName() + "'s now; " + network.ownerName()
                + " stays in it.");
    }

    private static int disband(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        WarpNetwork network = named(context);
        if (network == null) {
            return 0;
        }
        NetworkNotice notice = NetworkOrders.disband(WarpNetworkRegistry.get(server), network.id(), actor(source),
                true);
        int machines = notice.refused() ? 0 : WarpNetworks.detach(server, network.id(), null);
        return said(source, notice, network.name() + " disbanded, and " + machines + " machine(s) taken off it.");
    }

    // ---------------------------------------------------------------- helpers

    /** The network a command names, having told the operator if there is none. */
    @Nullable
    private static WarpNetwork named(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        WarpNetwork network = WarpNetworkRegistry.get(context.getSource().getServer()).byName(name);
        if (network == null) {
            context.getSource().sendFailure(Component.literal("There is no network called " + name + "."));
        }
        return network;
    }

    /** The one player an argument names, having told the operator if it named several. */
    @Nullable
    private static GameProfile one(CommandContext<CommandSourceStack> context, String argument)
            throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, argument);
        if (profiles.size() != 1) {
            context.getSource().sendFailure(Component.literal("Name one player."));
            return null;
        }
        return profiles.iterator().next();
    }

    /** Who is giving the order, for the trace: the operator, or nobody for the console. */
    @Nullable
    private static UUID actor(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player == null ? null : player.getUUID();
    }

    /**
     * Tells the operator what came of an order.
     *
     * @return {@code 1} if it was carried out, {@code 0} if it was refused
     */
    private static int said(CommandSourceStack source, NetworkNotice notice, String done) {
        if (notice.refused()) {
            source.sendFailure(AWLang.translate(notice.key(), notice.arg()).component());
            return 0;
        }
        source.sendSuccess(() -> Component.literal(done), true);
        return 1;
    }

    /** A name as it has to be typed back: in quotes, unless it is one bare word. */
    private static String quoted(String name) {
        return name.matches("[A-Za-z0-9_.+-]+") ? name : "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
