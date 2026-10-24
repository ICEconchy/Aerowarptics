package uk.co.iceconchy.aerowarptics.wellhead;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * {@code /aerowarptics wellhead}: an operator's way to put a well where they want it, for trying the scene.
 *
 * <p>A meltdown takes minutes of neglect to reach by honest means and destroys the site it happens at, which is
 * a poor way to look at one twice. {@code strain} sets the nearest formed well's strain outright; {@code meltdown}
 * sends it straight over; {@code preview} plays the scene alone, with no well at all and nothing changed. Neither is anything a player can do: both need the permission level a
 * {@code /setblock} does.
 */
public final class WellheadCommands {

    /** How far from whoever asks a well is looked for, in chunks. */
    private static final int SEARCH_CHUNKS = 3;

    private WellheadCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("wellhead")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("strain")
                        .then(Commands.argument("strain", DoubleArgumentType.doubleArg(0.0D, 1.0D))
                                .executes(WellheadCommands::strain)))
                .then(Commands.literal("meltdown").executes(WellheadCommands::meltdown))
                .then(Commands.literal("preview")
                        .then(Commands.literal("i").executes(context -> preview(context, WellheadTier.I)))
                        .then(Commands.literal("ii").executes(context -> preview(context, WellheadTier.II)))
                        .then(Commands.literal("iii").executes(context -> preview(context, WellheadTier.III))));
    }

    /**
     * Plays a meltdown of a size in front of whoever asks, dry: the whole scene, and nothing in the world changed,
     * moved or hurt. No frame and no fissure needed - it is for looking at.
     */
    private static int preview(CommandContext<CommandSourceStack> context, WellheadTier tier) {
        CommandSourceStack source = context.getSource();
        int crater = uk.co.iceconchy.aerowarptics.AWConfig.WELLHEAD_CRATER.get(tier).get();
        net.minecraft.world.phys.Vec2 rotation = source.getRotation();
        net.minecraft.world.phys.Vec3 ahead = net.minecraft.world.phys.Vec3.directionFromRotation(0.0F, rotation.y)
                .scale(crater * 1.5D + 16.0D);
        BlockPos centre = BlockPos.containing(source.getPosition().add(ahead)).above(2);
        Meltdowns.begin(source.getLevel(), centre, tier, tier.radius(), true);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Previewing a %s meltdown at %s: crater %d, nothing in the world is changed.",
                tier.getSerializedName(), centre.toShortString(), crater)), true);
        return 1;
    }

    private static int strain(CommandContext<CommandSourceStack> context) {
        RiftCollarBlockEntity collar = nearest(context.getSource());
        if (collar == null) {
            context.getSource().sendFailure(Component.literal("No formed Rift Wellhead within a few chunks."));
            return 0;
        }
        double strain = DoubleArgumentType.getDouble(context, "strain");
        collar.forceStrain(strain);
        context.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Wellhead at %s set to %.0f per cent strain.", collar.getBlockPos().toShortString(), strain * 100.0D)), true);
        return 1;
    }

    private static int meltdown(CommandContext<CommandSourceStack> context) {
        RiftCollarBlockEntity collar = nearest(context.getSource());
        if (collar == null) {
            context.getSource().sendFailure(Component.literal("No formed Rift Wellhead within a few chunks."));
            return 0;
        }
        BlockPos at = collar.getBlockPos();
        collar.forceStrain(1.0D);
        context.getSource().sendSuccess(() -> Component.literal("Wellhead at " + at.toShortString()
                + " sent over. It goes within a second."), true);
        return 1;
    }

    /** The nearest formed collar among the block entities of the chunks already loaded round whoever asks. */
    @Nullable
    private static RiftCollarBlockEntity nearest(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos from = BlockPos.containing(source.getPosition());
        RiftCollarBlockEntity found = null;
        double best = Double.MAX_VALUE;
        for (int cx = (from.getX() >> 4) - SEARCH_CHUNKS; cx <= (from.getX() >> 4) + SEARCH_CHUNKS; cx++) {
            for (int cz = (from.getZ() >> 4) - SEARCH_CHUNKS; cz <= (from.getZ() >> 4) + SEARCH_CHUNKS; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (entity instanceof RiftCollarBlockEntity collar && collar.isFormed()) {
                        double distance = collar.getBlockPos().distSqr(from);
                        if (distance < best) {
                            best = distance;
                            found = collar;
                        }
                    }
                }
            }
        }
        return found;
    }
}
