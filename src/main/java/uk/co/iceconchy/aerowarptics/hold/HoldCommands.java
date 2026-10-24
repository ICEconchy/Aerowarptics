package uk.co.iceconchy.aerowarptics.hold;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.warp.ArrivalTicket;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /aerowarptics holds}: what this mod is keeping loaded, and the means to let go of it.
 *
 * <ul>
 *   <li>{@code list} - every ship held, by whom, for what and for how much longer; and the chunk keeping each
 *   level with a held ship awake.</li>
 *   <li>{@code release <ship>} - lets go of every hold on a ship now. Its drive takes residency again at its
 *   next renewal if it still wants it; this is for a ship an operator wants gone from memory, or a hold
 *   they want to see come back to prove who is taking it.</li>
 *   <li>{@code reach <x> <z> <radius>} - loads a square of ground, block-ticking, for fifteen seconds. For a
 *   ship stranded before ships were held properly: a hull Sable unloaded over ground no player's simulation
 *   distance covers all of never comes back on its own. Reach over where it was, and it does - and if it
 *   carries a drive, the drive holds it from then on.</li>
 * </ul>
 */
public final class HoldCommands {

    /** Chunks either side of the centre a reach may cover: a square thirty-three chunks a side. */
    private static final int MAX_REACH_RADIUS = 16;

    private HoldCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("holds")
                .requires(source -> source.hasPermission(2))
                .executes(HoldCommands::list)
                .then(Commands.literal("list").executes(HoldCommands::list))
                .then(Commands.literal("release")
                        .then(Commands.argument("ship", UuidArgument.uuid()).executes(HoldCommands::release)))
                .then(Commands.literal("reach")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(0, MAX_REACH_RADIUS))
                                                .executes(HoldCommands::reach)))));
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        HoldLedger ledger = HoldLedger.get(server);
        long now = server.overworld().getGameTime();
        List<HoldLedger.ShipHold> holds = ledger.holds();
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "AeroWarptics is holding %d ship hold(s); %d ship(s) noted for finding again.",
                holds.size(), ledger.ships().size())), false);
        for (HoldLedger.ShipHold hold : holds) {
            HoldLedger.ShipRecord ship = ledger.ship(hold.ship());
            ServerLevel level = server.getLevel(hold.dimension());
            boolean loaded = level != null && Holds.present(level, hold.ship()) != null;
            String line = String.format(Locale.ROOT, "  %s %s  ship %s '%s' in %s%s  lease %ds  by %s",
                    hold.key().purpose().id(), HoldRules.lapsed(hold.leaseUntil(), now) ? "(lapsed)" : "",
                    hold.ship(), ship == null ? "?" : ship.name(), hold.dimension().location(),
                    loaded ? "" : " (not loaded)", Math.max(0L, hold.leaseUntil() - now) / 20L, hold.key().owner());
            source.sendSuccess(() -> Component.literal(line), false);
        }
        for (ServerLevel level : server.getAllLevels()) {
            Long wake = ledger.wakeChunk(level.dimension());
            if (wake != null) {
                String line = "  " + level.dimension().location() + " kept awake by chunk " + new ChunkPos(wake);
                source.sendSuccess(() -> Component.literal(line), false);
            }
        }
        return holds.size();
    }

    private static int release(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        UUID ship = UuidArgument.getUuid(context, "ship");
        HoldLedger ledger = HoldLedger.get(server);
        List<HoldLedger.ShipHold> on = ledger.holdsOn(ship);
        for (HoldLedger.ShipHold hold : on) {
            Holds.releaseShip(server, hold.key().owner(), hold.key().purpose(), ship);
        }
        Trace.always(Trace.HOLD, "{} released {} hold(s) on ship {}", source.getTextName(), on.size(), ship);
        source.sendSuccess(() -> Component.literal(on.isEmpty()
                ? "Nothing of AeroWarptics' is holding that ship."
                : "Released " + on.size() + " hold(s). A drive still aboard takes residency again at its next"
                + " renewal if keepAirshipsLoaded is on."), true);
        return on.size();
    }

    private static int reach(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        int x = IntegerArgumentType.getInteger(context, "x") >> 4;
        int z = IntegerArgumentType.getInteger(context, "z") >> 4;
        int radius = IntegerArgumentType.getInteger(context, "radius");
        int held = Holds.reachGround(level, ArrivalTicket.tickingTile(x - radius, z - radius, x + radius, z + radius));
        Trace.always(Trace.HOLD, "{} reached {} chunk(s) round {} in {}", source.getTextName(), held,
                new ChunkPos(x, z), level.dimension().location());
        source.sendSuccess(() -> Component.literal("Holding " + held + " chunk(s) round " + new ChunkPos(x, z)
                + " for fifteen seconds. A ship Sable unloaded there comes back once every column under it is"
                + " loaded; /aerowarptics holds list shows it once its drive holds it."), true);
        return held;
    }
}
