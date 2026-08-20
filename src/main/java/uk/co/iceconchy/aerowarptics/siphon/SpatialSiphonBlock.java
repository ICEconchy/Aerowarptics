package uk.co.iceconchy.aerowarptics.siphon;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;

/**
 * The Spatial Siphon block.
 *
 * <p>Deliberately plain to interact with: there is no screen and no controls, because there is nothing
 * to decide. It fills when the ship it is bolted to comes out of a rift, and anything that can drain a
 * tank can drain it.
 */
public class SpatialSiphonBlock extends Block implements IBE<SpatialSiphonBlockEntity> {

    public static final MapCodec<SpatialSiphonBlock> CODEC = simpleCodec(SpatialSiphonBlock::new);

    /** Waisted: a wide foot and a narrower vessel, so it reads as a vessel rather than a crate. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1.0D, 0.0D, 1.0D, 15.0D, 3.0D, 15.0D),
            Block.box(3.0D, 3.0D, 3.0D, 13.0D, 16.0D, 13.0D));

    public SpatialSiphonBlock(Properties properties) {
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
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public Class<SpatialSiphonBlockEntity> getBlockEntityClass() {
        return SpatialSiphonBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SpatialSiphonBlockEntity> getBlockEntityType() {
        return AWBlockEntities.SPATIAL_SIPHON.get();
    }
}
