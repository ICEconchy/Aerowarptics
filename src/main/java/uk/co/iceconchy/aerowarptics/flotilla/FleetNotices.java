package uk.co.iceconchy.aerowarptics.flotilla;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.List;
import java.util.UUID;

/**
 * Tells the people a flotilla concerns what has just happened to it.
 *
 * <p>Addressed to the owners of the couplers involved - the people who can do something about it - and
 * only to those online. A ship that takes station with somebody else's flagship is a thing that happens
 * between two players, often far apart, and the one who was not at the screen has no other way of
 * finding out.
 */
public final class FleetNotices {

    private FleetNotices() {
    }

    /** A ship's name, or the mod's usual "Unnamed vessel" - Sable's names are optional and usually blank. */
    public static Component shipName(@Nullable CouplerRecord record) {
        String name = record == null ? "" : record.shipName().trim();
        return name.isEmpty() ? AWLang.translate("gui.rift_navigation.unnamed_ship").component()
                : Component.literal(name);
    }

    static void joined(ServerLevel level, JoinResult result, @Nullable CouplerRecord escort,
                       @Nullable CouplerRecord flagship) {
        if (escort == null || flagship == null) {
            return;
        }
        if (result == JoinResult.JOINED) {
            Component line = AWLang.translate("flotilla.notice.taken_station", shipName(escort), shipName(flagship))
                    .style(ChatFormatting.AQUA).component();
            tell(level, flagship.owner(), line);
            if (escort.owner() != null && !escort.owner().equals(flagship.owner())) {
                tell(level, escort.owner(), line);
            }
        } else if (result == JoinResult.REQUESTED) {
            tell(level, flagship.owner(), AWLang.translate("flotilla.notice.requested", shipName(escort))
                    .style(ChatFormatting.GOLD).component());
        }
    }

    static void refused(ServerLevel level, @Nullable CouplerRecord escort, @Nullable CouplerRecord flagship) {
        if (escort != null && flagship != null) {
            tell(level, escort.owner(), AWLang.translate("flotilla.notice.refused", shipName(flagship))
                    .style(ChatFormatting.GRAY).component());
        }
    }

    static void brokeOff(ServerLevel level, @Nullable CouplerRecord escort, @Nullable CouplerRecord flagship) {
        if (escort != null && flagship != null) {
            tell(level, flagship.owner(), AWLang.translate("flotilla.notice.broke_off", shipName(escort))
                    .style(ChatFormatting.GRAY).component());
        }
    }

    static void castOff(ServerLevel level, @Nullable CouplerRecord escort, @Nullable CouplerRecord flagship) {
        if (escort != null && flagship != null) {
            tell(level, escort.owner(), AWLang.translate("flotilla.notice.cast_off", shipName(flagship),
                    shipName(escort)).style(ChatFormatting.GRAY).component());
        }
    }

    /** Escorts let go because the flagship they followed stopped leading or was broken. */
    static void released(ServerLevel level, List<UUID> escorts, @Nullable CouplerRecord flagship) {
        if (escorts.isEmpty() || flagship == null) {
            return;
        }
        FlotillaRegistry registry = FlotillaRegistry.get(level);
        for (UUID id : escorts) {
            CouplerRecord escort = registry.coupler(id);
            if (escort != null) {
                tell(level, escort.owner(), AWLang.translate("flotilla.notice.disbanded", shipName(flagship),
                        shipName(escort)).style(ChatFormatting.GRAY).component());
            }
        }
    }

    /** Escorts dropped from the flotilla by a forced fold that left them behind. */
    static void leftBehind(ServerLevel level, @Nullable CouplerRecord escort, @Nullable CouplerRecord flagship) {
        if (escort != null && flagship != null) {
            tell(level, escort.owner(), AWLang.translate("flotilla.notice.left_behind", shipName(flagship),
                    shipName(escort)).style(ChatFormatting.RED).component());
        }
    }

    private static void tell(ServerLevel level, @Nullable UUID player, Component line) {
        if (player == null) {
            return;
        }
        ServerPlayer online = level.getServer().getPlayerList().getPlayer(player);
        if (online != null) {
            online.sendSystemMessage(line);
        }
    }
}
