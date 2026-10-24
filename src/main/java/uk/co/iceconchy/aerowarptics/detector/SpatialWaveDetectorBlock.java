package uk.co.iceconchy.aerowarptics.detector;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;

/**
 * The Spatial Wave Detector block.
 *
 * <p>A redstone source and nothing else. What it is putting out is held in the block state as
 * {@link #POWER}, the way a daylight detector holds its own, because that is what neighbouring wiring
 * reads - and it doubles as the lit look, so a pulse can be seen from across a yard as well as wired.
 * When to put it out is the block entity's business; see {@link SpatialWaveDetectorBlockEntity}.
 */
public class SpatialWaveDetectorBlock extends Block implements IBE<SpatialWaveDetectorBlockEntity> {

    public static final MapCodec<SpatialWaveDetectorBlock> CODEC = simpleCodec(SpatialWaveDetectorBlock::new);

    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    public SpatialWaveDetectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWER, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }

    @Override
    public Class<SpatialWaveDetectorBlockEntity> getBlockEntityClass() {
        return SpatialWaveDetectorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SpatialWaveDetectorBlockEntity> getBlockEntityType() {
        return AWBlockEntities.SPATIAL_WAVE_DETECTOR.get();
    }
}
