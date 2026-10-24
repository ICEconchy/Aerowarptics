package uk.co.iceconchy.aerowarptics.ward;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.trace.Trace;

import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /aerowarptics ward}, for operators: what fields stand on this server, and a way to take one down.
 *
 * <ul>
 *   <li>{@code list} - every Rift Ward the server knows of: where, whose, how wide, what it has banked and
 *   whether its field stands. A ward says nothing to the players it turns aside, so an operator asked "why did I
 *   come out over there" has nowhere else to look.</li>
 *   <li>{@code remove <id>} - takes a ward's field down at once. For a field planted over somebody else's
 *   ground. If the machine is loaded it is broken, and drops as an item. If it is not, nothing here loads it:
 *   its record is emptied and switched off instead, which the machine takes up when it next loads, so the field
 *   stays down until whoever owns it has switched it on and fed it again.</li>
 * </ul>
 */
public final class WardCommands {

    private WardCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("ward")
                .requires(source -> source.hasPermission(2))
                .executes(WardCommands::list)
                .then(Commands.literal("list").executes(WardCommands::list))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", UuidArgument.uuid()).executes(WardCommands::remove)));
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Collection<RiftWard> wards = WardRegistry.get(server).all();
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "%d Rift Ward(s)%s.", wards.size(),
                AWConfig.WARD_ENABLED.get() ? "" : " - wards are switched off in the server config")), false);
        long now = Wards.now(server);
        for (RiftWard ward : wards) {
            RiftWardBlockEntity block = Wards.loaded(server, ward);
            double charge = block != null ? block.charge() : !ward.enabled() ? ward.charge()
                    : WardCharge.after(ward.charge(), now - ward.countedAt(),
                    WardCharge.upkeepPerSecond(ward.radius(), AWConfig.WARD_UPKEEP.get()));
            boolean standing = block != null ? block.standing() : WardCharge.standing(ward.standing(), ward.enabled(),
                    charge, AWConfig.WARD_CAPACITY.get(), AWConfig.WARD_RECOVER_FRACTION.get());
            String line = String.format(Locale.ROOT, "  %s  %s in %s  %s  radius %d  %,d mB  %s%s%s", ward.id(),
                    ward.pos().toShortString(), ward.dimension().location(),
                    ward.ownerName().isBlank() ? "unowned" : ward.ownerName(), ward.radius(), Math.round(charge),
                    !ward.enabled() ? "off" : standing ? "standing" : "down",
                    "  " + ward.mode() + (ward.networks().isEmpty() ? "" : " " + ward.networks())
                            + (ward.selfTest() ? "  owner turned too" : ""), block == null ? "  (not loaded)" : "");
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return wards.size();
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        UUID id = UuidArgument.getUuid(context, "id");
        WardRegistry registry = WardRegistry.get(server);
        RiftWard ward = registry.byId(id);
        if (ward == null) {
            source.sendFailure(Component.literal("No Rift Ward has that id. /aerowarptics ward list shows them."));
            return 0;
        }
        RiftWardBlockEntity block = Wards.loaded(server, ward);
        Trace.log(Trace.WARD, "{} removed ward {} at {} ({})", source.getTextName(), id, Trace.pos(ward.pos()),
                block == null ? "not loaded: emptied and switched off in its record" : "loaded: the machine is broken");
        if (block != null) {
            ServerLevel level = server.getLevel(ward.dimension());
            if (level != null) {
                // Breaking the master forgets the record on its way out, and drops the machine.
                level.destroyBlock(ward.pos(), true);
            }
            registry.remove(id);
        } else {
            registry.register(ward.stoodDown(Wards.now(server)));
        }
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Removed the Rift Ward at %s%s.",
                ward.pos().toShortString(), block == null ? " - its machine is not loaded: it is left standing,"
                        + " emptied and switched off" : "")), true);
        return 1;
    }
}
