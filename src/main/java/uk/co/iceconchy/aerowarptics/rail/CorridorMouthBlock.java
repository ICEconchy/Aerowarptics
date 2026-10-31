package uk.co.iceconchy.aerowarptics.rail;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import uk.co.iceconchy.aerowarptics.registry.AWParticles;

/**
 * The far wall of a corridor: the end of the track, as Create sees it a portal.
 *
 * <p>Each end of a corridor's track is a portal-shaped track bound to a track in a gate's own dimension, and
 * Create keeps such a track only while the block it points into is a portal it knows about. This is that block.
 * It is drawn as a sheet of the gate's own shimmer, a few blocks across, so a rider looking ahead down the
 * corridor sees the doorway they are about to come out of rather than the end of the line.
 *
 * <p>Nobody lays track into one - nothing can be built in the corridor - so the portal Create is told it is
 * leads nowhere: see {@link RiftPortalTracks}.
 */
public class CorridorMouthBlock extends Block {

    public static final MapCodec<CorridorMouthBlock> CODEC = simpleCodec(CorridorMouthBlock::new);

    /** As thin as the pane, across the line of the track (which runs along X). */
    private static final VoxelShape SHAPE = Block.box(6.0D, 0.0D, 0.0D, 10.0D, 16.0D, 16.0D);

    public CorridorMouthBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
        return adjacent.is(this) || super.skipRendering(state, adjacent, side);
    }

    /**
     * The way back out: a right-click here returns a visitor to the spot they stepped in from.
     *
     * <p>Either mouth does it, not only the one they came in by - somebody who has walked the length of a corridor
     * after a train should not have to walk back to leave. Anybody the corridor has no record of, which is a rider
     * a train left behind or a visitor a restart forgot, is put out at the lane's own railhead instead.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            return CorridorDoor.leave(serverLevel, pos, serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(20) != 0) {
            return;
        }
        level.addParticle(AWParticles.RIFT_SPARK.get(), pos.getX() + 0.5D, pos.getY() + random.nextDouble(),
                pos.getZ() + random.nextDouble(), 0.0D, (random.nextDouble() - 0.3D) * 0.05D,
                (random.nextDouble() - 0.5D) * 0.06D);
    }
}
