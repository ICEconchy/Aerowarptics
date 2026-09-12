package uk.co.iceconchy.aerowarptics.weather;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.TimeArgument;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;
import uk.co.iceconchy.aerowarptics.network.AWNetwork;
import uk.co.iceconchy.aerowarptics.network.ClientboundRiftStormPacket;
import uk.co.iceconchy.aerowarptics.util.AWLang;

/**
 * The Rift Storm: rare weather in which space is already coming apart.
 *
 * <p>While one rages, a drive that completes a warp has less to recover from, and its cooldown is
 * shortened; but every drive's exit is as unsure as a Singularity's. The arithmetic of both is in
 * {@link RiftStormRules}, and the clock in {@link RiftStormCycle}. This class is the part that needs a
 * server: it persists the clock, runs it, tells the players, and owns {@code /weather rift_storm}.
 *
 * <p>Server-wide, like vanilla's weather, and stored on the overworld for the same reason
 * {@link uk.co.iceconchy.aerowarptics.anchor.WarpAnchorRegistry} is - one file, whatever dimension
 * asked. It rages only over levels with a sky and no ceiling, which is the same test for "has weather"
 * that vanilla's rain uses; a ship in the Nether does not feel a storm in the Overworld's sky.
 *
 * <p>A storm is a fact about the moment a drive acts, not a property of the drive. The cooldown is
 * fixed when a warp completes and the scatter roll when the rift opens, so a storm that passes mid-
 * cooldown leaves that cooldown short, and one that arrives mid-cooldown does not shorten it.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID)
public final class RiftStorm extends SavedData {

    private static final String FILE_ID = AeroWarptics.MODID + "_rift_storm";
    private static final String KEY_STORM = "StormTicks";
    private static final String KEY_CALM = "CalmTicks";

    private final RiftStormCycle cycle;

    private RiftStorm(RiftStormCycle cycle) {
        this.cycle = cycle;
    }

    private static SavedData.Factory<RiftStorm> factory() {
        return new SavedData.Factory<>(() -> new RiftStorm(new RiftStormCycle()), RiftStorm::load, null);
    }

    private static RiftStorm load(CompoundTag tag, HolderLookup.Provider registries) {
        return new RiftStorm(new RiftStormCycle(tag.getInt(KEY_STORM), tag.getInt(KEY_CALM)));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(KEY_STORM, cycle.stormTicks());
        tag.putInt(KEY_CALM, cycle.calmTicks());
        return tag;
    }

    public static RiftStorm get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    // ------------------------------------------------------------ queries

    /**
     * Whether a level has weather at all: a sky, and nothing overhead to keep it out.
     *
     * <p>Common rather than server-only, because the client asks the same question of the level its
     * player is standing in before it draws anything.
     */
    public static boolean underOpenSky(Level level) {
        DimensionType type = level.dimensionType();
        return type.hasSkyLight() && !type.hasCeiling();
    }

    /** Whether a storm is raging over this level. Always {@code false} on a client. */
    public static boolean ragingOver(Level level) {
        return level instanceof ServerLevel serverLevel
                && underOpenSky(level)
                && get(serverLevel.getServer()).cycle.raging();
    }

    /** The cooldown a warp completed now, in this level, earns a drive of this tier. */
    public static int cooldownTicks(Level level, RiftDriveTier tier) {
        return RiftStormRules.cooldownTicks(tier.cooldownTicks(), ragingOver(level),
                AWConfig.RIFT_STORM_COOLDOWN_MULTIPLIER.get());
    }

    /** The chance a rift opened now, in this level, by a drive of this tier scatters its exit. */
    public static double instability(Level level, RiftDriveTier tier) {
        return RiftStormRules.instability(tier, tier.instability(),
                RiftDriveTier.SINGULARITY.instability(), ragingOver(level));
    }

    // ------------------------------------------------------------ control

    /**
     * Starts a storm, or re-sets how long the one already raging has left.
     *
     * @param duration ticks, or zero or less to roll a length from the config
     */
    public static void start(MinecraftServer server, int duration) {
        RiftStorm storm = get(server);
        int length = duration > 0 ? duration : rollStorm(server.overworld().getRandom());
        boolean began = storm.cycle.start(length);
        storm.setDirty();
        if (began) {
            announce(server, true);
        }
    }

    /**
     * Ends any storm.
     *
     * @param calm ticks before the next natural storm may arrive, or zero or less to roll one
     */
    public static void stop(MinecraftServer server, int calm) {
        RiftStorm storm = get(server);
        boolean ended = storm.cycle.stop(calm);
        storm.setDirty();
        if (ended) {
            announce(server, false);
        }
    }

    // ------------------------------------------------------------- events

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        RiftStorm storm = get(server);
        RandomSource random = server.overworld().getRandom();
        boolean advance = server.overworld().getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE);
        RiftStormCycle.Change change = storm.cycle.tick(advance, AWConfig.NATURAL_RIFT_STORMS.get(),
                () -> rollCalm(random), () -> rollStorm(random));
        // Frozen weather is not marked dirty, so a world with doWeatherCycle off is not re-written on
        // every save for a clock that has not moved.
        if (advance) {
            storm.setDirty();
        }
        if (change != RiftStormCycle.Change.NONE) {
            announce(server, change == RiftStormCycle.Change.BEGAN);
        }
    }

    /** A player joining mid-storm is told about it, or their sky stays calm until the next change. */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            AWNetwork.sendTo(player, new ClientboundRiftStormPacket(get(player.getServer()).cycle.raging()));
        }
    }

    /**
     * {@code /weather rift_storm [duration]}, and {@code /weather clear} ending one.
     *
     * <p>Grafted onto vanilla's own {@code /weather} rather than living under {@code /aerowarptics},
     * because a storm is weather and that is where an operator will look for it. Brigadier merges a
     * literal registered under an existing name into the node already there, keeping that node's
     * permission requirement - so {@code rift_storm} sits beside {@code clear}, {@code rain} and
     * {@code thunder} behind the same operator gate, with nothing of vanilla's replaced.
     *
     * <p>{@code clear} is the one exception, and even that is wrapped rather than rewritten: merging a
     * node that carries a command replaces the command on the node it merges into, so the replacement
     * runs vanilla's own and then calms the rift storm too. "Clear" that left a Rift Storm raging would
     * be a clear sky with space still tearing open in it. Rain and thunder leave a storm alone; nothing
     * about rain says space has stopped coming apart.
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        LiteralArgumentBuilder<CommandSourceStack> weather = Commands.literal("weather")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("rift_storm")
                        .executes(context -> setRiftStorm(context, -1))
                        .then(Commands.argument("duration", TimeArgument.time(1))
                                .executes(context -> setRiftStorm(context,
                                        IntegerArgumentType.getInteger(context, "duration")))));

        CommandNode<CommandSourceStack> existing = dispatcher.getRoot().getChild("weather");
        CommandNode<CommandSourceStack> clear = existing == null ? null : existing.getChild("clear");
        if (clear != null && clear.getCommand() != null) {
            Command<CommandSourceStack> vanillaClear = clear.getCommand();
            LiteralArgumentBuilder<CommandSourceStack> calming = Commands.literal("clear")
                    .executes(context -> {
                        int result = vanillaClear.run(context);
                        stop(context.getSource().getServer(), -1);
                        return result;
                    });
            CommandNode<CommandSourceStack> timed = clear.getChild("duration");
            if (timed != null && timed.getCommand() != null) {
                Command<CommandSourceStack> vanillaTimed = timed.getCommand();
                calming.then(Commands.argument("duration", TimeArgument.time(1))
                        .executes(context -> {
                            int result = vanillaTimed.run(context);
                            stop(context.getSource().getServer(),
                                    IntegerArgumentType.getInteger(context, "duration"));
                            return result;
                        }));
            }
            weather.then(calming);
        }

        dispatcher.register(weather);
    }

    private static int setRiftStorm(CommandContext<CommandSourceStack> context, int duration) {
        CommandSourceStack source = context.getSource();
        start(source.getServer(), duration);
        source.sendSuccess(() -> AWLang.component("commands.weather.set.rift_storm"), true);
        return Math.max(1, duration);
    }

    // ------------------------------------------------------------ helpers

    private static void announce(MinecraftServer server, boolean raging) {
        ClientboundRiftStormPacket packet = new ClientboundRiftStormPacket(raging);
        Component line = raging
                ? AWLang.component("weather.rift_storm.began")
                : AWLang.component("weather.rift_storm.ended");
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            AWNetwork.sendTo(player, packet);
            // Everyone's sky is told, but only those under it are told in words. A player in the Nether
            // would otherwise read that a storm had begun, look up, and see a ceiling.
            if (underOpenSky(player.level())) {
                player.displayClientMessage(line, false);
            }
        }
    }

    private static int rollCalm(RandomSource random) {
        return RiftStormRules.roll(AWConfig.RIFT_STORM_CALM_LEAST.get(),
                AWConfig.RIFT_STORM_CALM_MOST.get(), random.nextDouble());
    }

    private static int rollStorm(RandomSource random) {
        return RiftStormRules.roll(AWConfig.RIFT_STORM_LENGTH_LEAST.get(),
                AWConfig.RIFT_STORM_LENGTH_MOST.get(), random.nextDouble());
    }
}
