package uk.co.iceconchy.aerowarptics.stabiliser;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;

/**
 * The Spatial Stabiliser block.
 *
 * <p>One control and one only: a redstone signal switches it on, and losing the signal switches it
 * off. No screen, because there is nothing to choose between on a ship's own deck - how hard it
 * cushions is a server's balance decision, in the config, not a pilot's. See
 * {@link SpatialStabiliserBlockEntity} for what "on" does.
 *
 * <p>{@link #POWERED} is the switch's position and nothing more, which is why it is held in the block
 * state rather than worked out by the block entity: it is what a lever wired to it says, and it lights
 * the core so the lever's position can be read from across a deck. Whether that switch is actually
 * biting - linked, aboard, not standing down for a warp - changes far more often and is the goggles'
 * business. Putting that in the block state too would rewrite a block in the plot every time a warp
 * started and stopped, and each rewrite is a collider rebuilt under a ship in flight.
 */
public class SpatialStabiliserBlock extends Block implements IBE<SpatialStabiliserBlockEntity> {

    public static final MapCodec<SpatialStabiliserBlock> CODEC = simpleCodec(SpatialStabiliserBlock::new);

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    /**
     * A foot, a collar, a drum and a framed cap - the same stance as the probe, so the two read as one machine
     * shop. Box for box the artist's model, which {@code StabiliserModelTest} holds it to.
     */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1.0D, 0.0D, 1.0D, 15.0D, 4.0D, 15.0D),
            Block.box(2.0D, 4.0D, 2.0D, 14.0D, 7.0D, 14.0D),
            Block.box(2.0D, 12.0D, 2.0D, 14.0D, 15.0D, 4.0D),
            Block.box(2.0D, 12.0D, 12.0D, 14.0D, 15.0D, 14.0D),
            Block.box(2.0D, 12.0D, 4.0D, 4.0D, 15.0D, 12.0D),
            Block.box(12.0D, 12.0D, 4.0D, 14.0D, 15.0D, 12.0D),
            Block.box(4.0D, 12.5D, 4.0D, 12.0D, 14.5D, 12.0D),
            Block.box(3.0D, 4.0D, 3.0D, 13.0D, 12.0D, 13.0D));

    public SpatialStabiliserBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /**
     * Whether a block is a stabiliser with its switch on - all a Collapse Cradle asks of one, and all a client
     * needs to see to know the cradle counts it.
     */
    public static boolean switchedOn(BlockState state) {
        return state.getBlock() instanceof SpatialStabiliserBlock && state.getValue(POWERED);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    /** Placed into a signal that is already there, it starts on rather than waiting for it to change. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(POWERED,
                context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbour,
                                   BlockPos neighbourPos, boolean movedByPiston) {
        if (level.isClientSide) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public Class<SpatialStabiliserBlockEntity> getBlockEntityClass() {
        return SpatialStabiliserBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SpatialStabiliserBlockEntity> getBlockEntityType() {
        return AWBlockEntities.SPATIAL_STABILISER.get();
    }
}
