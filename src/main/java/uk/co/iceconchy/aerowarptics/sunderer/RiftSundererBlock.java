package uk.co.iceconchy.aerowarptics.sunderer;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;

/**
 * The Rift Sunderer: the heart of a gate that is built to go wrong.
 *
 * <p>Set in a ring of Rift Gate Frame where a gate's controller would go, filled with Rift Essence and turned,
 * a redstone edge has it open the ring onto nowhere - see {@link RiftSundererBlockEntity}. The aperture finds no
 * far side, strains, cracks and falls in on itself, and a fissure hangs where it was: the ruin a rift scar is,
 * made on purpose. It is used up doing it.
 *
 * <h2>Which way it faces</h2>
 * Out of the ring, at the player who set it, with its one shaft on its back - the face into the ring's plane
 * being frame on both sides, the back is the face left. One shaft, for the wellhead's reason: a Create block passes
 * rotation between any two faces it has shafts on.
 *
 * <p>{@link #CHARGE} is its light, 0 to 3: how full it is while it waits, and how hard it strains once it fires.
 * The room round a sundering is lit by the ring and nothing else.
 */
public class RiftSundererBlock extends HorizontalKineticBlock implements IBE<RiftSundererBlockEntity> {

    public static final MapCodec<RiftSundererBlock> CODEC = simpleCodec(RiftSundererBlock::new);

    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 3);

    public RiftSundererBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(CHARGE, 0));
    }

    /** The light each {@link #CHARGE} stage gives. */
    public static int light(BlockState state) {
        return state.getValue(CHARGE) * 4;
    }

    @Override
    protected MapCodec<? extends HorizontalKineticBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CHARGE);
    }

    /** Facing the player who set it, so its back - and its shaft - is away from them. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite());
    }

    // -------------------------------------------------------------- kinetics

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(HORIZONTAL_FACING).getAxis();
    }

    /** One shaft, on its back. */
    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(HORIZONTAL_FACING).getOpposite();
    }

    // --------------------------------------------------------------- redstone

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof RiftSundererBlockEntity sunderer ? sunderer.comparatorSignal() : 0;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbour, BlockPos from,
                                   boolean moving) {
        super.neighborChanged(state, level, pos, neighbour, from, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RiftSundererBlockEntity sunderer) {
            sunderer.readSignal();
        }
    }

    /** Says why it would not fire, or that it would. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof RiftSundererBlockEntity sunderer)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            sunderer.report(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // Only a real departure: a change of CHARGE is a setBlock of this same block.
        if (!level.isClientSide && !newState.is(this) && level.getBlockEntity(pos) instanceof RiftSundererBlockEntity sunderer) {
            sunderer.onRemoved();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** A Sunderer that has fired refuses to move, and so does its ring: a tear is anchored where it began. */
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> BlockMovementChecks.registerMovementAllowedCheck((state, level, pos) ->
                state.getBlock() instanceof RiftSundererBlock
                        && level.getBlockEntity(pos) instanceof RiftSundererBlockEntity sunderer && sunderer.running()
                        ? BlockMovementChecks.CheckResult.FAIL
                        : BlockMovementChecks.CheckResult.PASS));
    }

    // ------------------------------------------------------------ block entity

    @Override
    public Class<RiftSundererBlockEntity> getBlockEntityClass() {
        return RiftSundererBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RiftSundererBlockEntity> getBlockEntityType() {
        return AWBlockEntities.RIFT_SUNDERER.get();
    }
}
