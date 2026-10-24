package uk.co.iceconchy.aerowarptics.gate;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import uk.co.iceconchy.aerowarptics.rail.RailLaneRegistry;
import uk.co.iceconchy.aerowarptics.registry.AWBlocks;

/**
 * What stands in a rail lane's cell of a gate while the gate is shut.
 *
 * <p>Nothing anybody sees. It exists because of one line of Create: a portal-shaped track turns itself to air
 * the moment the block it points into stops being a portal Create knows about. A gate's pane is that portal
 * while the gate is open, and is gone when it shuts - so without something to stand in for it, every hang-up
 * would quietly eat the end of every track run into the gate, and the corridor bound to it with it. The socket
 * is registered with Create as the same kind of portal the pane is, and the gate swaps one for the other in a
 * lane's cell, never leaving the cell empty.
 *
 * <p>Invisible, untouchable and unbreakable for the pane's reasons: there is nothing to collide with in a hole,
 * and the way to be rid of one is to take up the track - or break the ring - which the gate notices and tidies
 * up after. It cannot be built into either, which is what stops the doorway being blocked by accident.
 *
 * <p>It does not move anything, any more than the pane does. See {@link RiftPortalBlock}.
 */
public class RiftSocketBlock extends Block {

    public static final MapCodec<RiftSocketBlock> CODEC = simpleCodec(RiftSocketBlock::new);

    public RiftSocketBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return true;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    /**
     * Takes itself away once no rail lane runs through its cell any more.
     *
     * <p>Asked of the lane registry, not of the ring. A pane checks it is still ringed by walking out through the
     * rest of the pane to the frame, and that works because a pane fills its whole opening; a socket stands in one
     * cell of an opening that is otherwise empty air, so the same walk stops at the cell beside it and finds no
     * frame. Asked that way - as it first was - every socket took itself away within a minute or two of its gate
     * shutting, and Create took the lane's track with it (seen in game, 2026-10-18). The gate tears its lanes down
     * when its ring breaks, so this is only the backstop for a socket nothing accounts for.
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!RailLaneRegistry.get(level.getServer()).isLaneCell(level.dimension(), pos)) {
            level.removeBlock(pos, false);
        }
    }

    /** Whether a block is a gate's opening standing in for a pane - either the pane itself or this. */
    public static boolean isLaneCell(BlockState state) {
        return state.is(AWBlocks.RIFT_PORTAL.get()) || state.is(AWBlocks.RIFT_SOCKET.get());
    }

    static boolean isSocket(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos).is(AWBlocks.RIFT_SOCKET.get());
    }
}
