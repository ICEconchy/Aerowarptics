package uk.co.iceconchy.aerowarptics.berth;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;

/**
 * The Berth: a bollard at the edge of a slip, which makes that slip somewhere a ship can be put.
 *
 * <p>It does nothing by itself. What a berth <em>is</em> - the slip, whose it is, what is in it - is a
 * record in {@link BerthRegistry}, which a warp is planned against from the far side of the world and
 * which the server's own tick holds a ship by ({@link Berths}); the block is where that record is set
 * down, changed and read off. What is in the slip is in the block's state too, as {@link #STATE}, because
 * that is the lamp on its face and the reading a comparator takes - both things a station is wired and
 * watched by.
 *
 * <p>It faces whoever set it down, and its slip starts out beyond it: a ship comes in towards the
 * bollard and lies with its bow to the quay.
 */
public class BerthBlock extends HorizontalDirectionalBlock implements IBE<BerthBlockEntity> {

    public static final MapCodec<BerthBlock> CODEC = simpleCodec(BerthBlock::new);

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<BerthState> STATE = EnumProperty.create("state", BerthState.class);

    public BerthBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(STATE, BerthState.EMPTY));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STATE);
    }

    /**
     * A berth is never moved: not by a contraption, not by a piston, and not onto a ship. Its slip is a
     * place in the world, and its record says where; carried off, it would go on handing ships a slip it is
     * no longer beside.
     */
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> BlockMovementChecks.registerMovementAllowedCheck((state, level, pos) ->
                state.getBlock() instanceof BerthBlock
                        ? BlockMovementChecks.CheckResult.FAIL
                        : BlockMovementChecks.CheckResult.PASS));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof BerthBlockEntity berth) {
            berth.placed(placer instanceof Player player ? player : null, state.getValue(FACING));
        }
    }

    /**
     * Opens the panel, off the berth as this client has it; an edit is the server's to accept. Sneaking, it
     * only says what the berth is and what is in it - and that is all one set down in a ship's plot ever does.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof BerthBlockEntity berth) {
            if (level.isClientSide) {
                if (!player.isShiftKeyDown() && berth.berth() != null) {
                    uk.co.iceconchy.aerowarptics.client.AWClientHooks.openBerthPanel(berth);
                }
            } else if (player.isShiftKeyDown() || berth.berth() == null) {
                berth.describe(player);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Going, a berth lets go of whatever it holds and is forgotten. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof BerthBlockEntity berth) {
            berth.broken();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    // ------------------------------------------------------------------ redstone

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return state.getValue(STATE).signal();
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbour, BlockPos from,
                                   boolean moving) {
        super.neighborChanged(state, level, pos, neighbour, from, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof BerthBlockEntity berth) {
            berth.signalled(level.hasNeighborSignal(pos));
        }
    }

    // ------------------------------------------------------------------ block entity

    @Override
    public Class<BerthBlockEntity> getBlockEntityClass() {
        return BerthBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BerthBlockEntity> getBlockEntityType() {
        return AWBlockEntities.BERTH.get();
    }
}
