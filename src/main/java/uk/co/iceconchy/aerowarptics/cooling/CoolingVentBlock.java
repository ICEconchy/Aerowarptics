package uk.co.iceconchy.aerowarptics.cooling;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;

/**
 * The Cooling Vent block: a Rift Drive module that carries the heat of a warp out as steam.
 *
 * <p>Set against any face of a drive - or of the frame of Drive Extenders it is built into - and turned by
 * a shaft, it shortens the time the drive takes to recover after a warp. How much, and how many of them a
 * drive takes any good from, is {@link DriveCooling}'s; what one is doing at any moment is
 * {@link CoolingVentBlockEntity}'s.
 *
 * <h2>Which way it faces</h2>
 * {@link #FACING} is where the louvres point, which is where the steam goes. The shaft comes in at the
 * back, as an Encased Fan's does. Placed, the louvres point out of whatever the vent was set against -
 * so one put on a drive blows away from it, and its back meets the drive. A drive carries rotation on every
 * face, as its extenders do, so a vent set straight onto a turning drive is turning too, at the drive's
 * speed and the drive's cost. Turn it with a wrench to feed it from a slower shaft of its own: it only has
 * to be <em>beside</em> its drive, not backed onto it.
 *
 * <p>No block state says whether it is venting. That changes with every warp, and each rewrite of a block
 * in a ship's plot is a collider rebuilt under a ship in flight - the Spatial Stabiliser's reason. The
 * steam is the client's, drawn from the vent's own status.
 */
public class CoolingVentBlock extends DirectionalKineticBlock implements IBE<CoolingVentBlockEntity> {

    public static final MapCodec<CoolingVentBlock> CODEC = simpleCodec(CoolingVentBlock::new);

    public CoolingVentBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends DirectionalKineticBlock> codec() {
        return CODEC;
    }

    // ------------------------------------------------------------ create hooks

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    /** One shaft, in at the back. The louvres are where the steam leaves and nothing drives it from there. */
    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING).getOpposite();
    }

    @Override
    public IRotate.SpeedLevel getMinimumRequiredSpeedLevel() {
        return IRotate.SpeedLevel.MEDIUM;
    }

    /**
     * Louvres out of the surface it was set against, not towards the player as Create's own machines
     * turn. A vent is nearly always set down on the drive it is for, and the one arrangement that works
     * with no shaft laid at all is the one where its back meets that drive.
     *
     * <p>Sneaking does not flip it, as it flips most of Create's. A click on a drive opens its console, so
     * sneaking is how anything is placed on one - and the ordinary way to place a vent must not be the
     * way that puts it on backwards. A wrench turns it.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    // ------------------------------------------------------------ block entity

    @Override
    public Class<CoolingVentBlockEntity> getBlockEntityClass() {
        return CoolingVentBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CoolingVentBlockEntity> getBlockEntityType() {
        return AWBlockEntities.COOLING_VENT.get();
    }
}
