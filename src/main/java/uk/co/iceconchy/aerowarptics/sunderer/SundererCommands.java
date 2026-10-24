package uk.co.iceconchy.aerowarptics.sunderer;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.gate.RiftGateShape;
import uk.co.iceconchy.aerowarptics.network.ClientboundSundererCuePacket;

import java.util.Locale;

/**
 * {@code /aerowarptics sunderer}: an operator's way to look at a sundering without spending one.
 *
 * <p>A tear uses its Sunderer up and leaves a fissure that refuses a second within forty-eight blocks, and the
 * hand comes one time in fifty - a poor way to look at either twice. {@code preview} plays the scene in front of
 * whoever asks, dry, round a five-block opening that is not there: nothing in the world is changed, moved or
 * hurt. {@code preview hand} brings the hand. {@code survey} says what the nearest Sunderer would answer an edge
 * with. Both need the permission level a {@code /setblock} does.
 */
public final class SundererCommands {

    private static final int SEARCH_CHUNKS = 3;
    private static final int PREVIEW_SIZE = 5;

    private SundererCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("sunderer")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("preview")
                        .executes(context -> preview(context, false))
                        .then(Commands.literal("hand").executes(context -> preview(context, true))))
                .then(Commands.literal("survey").executes(SundererCommands::survey));
    }

    /** A dry sundering of a five-block opening, standing across whoever asks's view a few blocks ahead. */
    private static int preview(CommandContext<CommandSourceStack> context, boolean hand) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        Direction facing = Direction.fromYRot(source.getRotation().y);
        BlockPos middle = BlockPos.containing(source.getPosition()).relative(facing, 6).above(2);
        Direction.Axis span = facing.getAxis() == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z;
        int half = PREVIEW_SIZE / 2;
        BlockPos min = span == Direction.Axis.X ? middle.offset(-half, -half, 0) : middle.offset(0, -half, -half);
        BlockPos max = span == Direction.Axis.X ? middle.offset(half, half, 0) : middle.offset(0, half, half);
        RiftGateShape shape = new RiftGateShape(span, min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ());
        BlockPos sunderer = span == Direction.Axis.X ? middle.offset(-half - 1, 0, 0) : middle.offset(0, 0, -half - 1);
        BlockPos grip = middle.above(half + 1);
        int range = AWConfig.SUNDERER_SPECTACLE_RANGE.get();
        // A burst third for the frame that is not there: the top-left run and the right side's lower run, as a real
        // ring's burst would fall, and clear of the block the hand reaches for above the middle.
        java.util.List<BlockPos> lifted = new java.util.ArrayList<>();
        for (int along = -half - 1; along < 0; along++) {
            lifted.add(span == Direction.Axis.X ? middle.offset(along, half + 1, 0) : middle.offset(0, half + 1, along));
        }
        for (int down = 0; down >= -half - 1; down--) {
            lifted.add(span == Direction.Axis.X ? middle.offset(half + 1, down, 0) : middle.offset(0, down, half + 1));
        }
        ClientboundSundererCuePacket cue = new ClientboundSundererCuePacket(sunderer, shape, facing.getOpposite(),
                middle, grip, lifted, 0, level.random.nextLong(), hand, ClientboundSundererCuePacket.RUNNING, range, true);
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(middle) <= (double) range * range) {
                PacketDistributor.sendToPlayer(player, cue);
            }
        }
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Previewing a sundering at %s%s: nothing in the world is changed.", middle.toShortString(),
                hand ? ", with the hand" : "")), true);
        return 1;
    }

    private static int survey(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        RiftSundererBlockEntity sunderer = nearest(source);
        if (sunderer == null) {
            source.sendFailure(Component.literal("No Rift Sunderer within a few chunks."));
            return 0;
        }
        SundererSurvey.Result result = SundererSurvey.full(source.getLevel(), sunderer);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Sunderer at %s: %s%s",
                sunderer.getBlockPos().toShortString(), result.ok() ? "would fire" : result.failure().getSerializedName(),
                result.ring() == null ? "" : String.format(Locale.ROOT, ", opening %dx%d, tear at %s",
                        result.ring().shape().width(), result.ring().shape().height(),
                        result.ring().centre().toShortString()))), false);
        return 1;
    }

    @Nullable
    private static RiftSundererBlockEntity nearest(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos at = BlockPos.containing(source.getPosition());
        RiftSundererBlockEntity best = null;
        double nearest = Double.MAX_VALUE;
        int cx = at.getX() >> 4;
        int cz = at.getZ() >> 4;
        for (int x = cx - SEARCH_CHUNKS; x <= cx + SEARCH_CHUNKS; x++) {
            for (int z = cz - SEARCH_CHUNKS; z <= cz + SEARCH_CHUNKS; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (entity instanceof RiftSundererBlockEntity sunderer) {
                        double distance = sunderer.getBlockPos().distSqr(at);
                        if (distance < nearest) {
                            nearest = distance;
                            best = sunderer;
                        }
                    }
                }
            }
        }
        return best;
    }
}
