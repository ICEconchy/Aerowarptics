package uk.co.iceconchy.aerowarptics.wellhead;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;

/**
 * The Rift Collar: the one block of a Rift Wellhead that thinks.
 *
 * <p>A wellhead is built, not placed: a hollow cube of Rift Casing and Warp Glass round a fissure the player
 * found, with this set in the middle of one side. The collar looks straight back along its own facing for the
 * tear - one block behind it for a three-cube, two for a five, three for a seven - and when every block of the
 * shell is right it forms the frame, swapping the shell for parts and taking the fissure. From then on any block
 * of the frame is the well to whatever touches it. See {@link WellheadStructure} for the shape,
 * {@link WellheadAssembly} for the forming and {@link RiftCollarBlockEntity} for the machine.
 *
 * <h2>Which way it faces</h2>
 * Out of the machine, at the player who set it - so it is set down standing in front of the tear and looking at
 * it. Its one shaft is on that face. No other block of the frame has a shaft at all: a Create block passes
 * rotation between any two faces it has shafts on, and a frame with two would be a gearbox.
 *
 * <p>A loose collar is a block like any other and can be carried, pushed or assembled into a hull. A formed one
 * refuses all three, with every block of its frame - a fissure never moves, and nor does what is built round it.
 */
public class RiftCollarBlock extends HorizontalKineticBlock implements IBE<RiftCollarBlockEntity> {

    public static final MapCodec<RiftCollarBlock> CODEC = simpleCodec(RiftCollarBlock::new);

    /** Whether this collar holds a frame. Kept in the state so it can light, and so a formed one refuses to move. */
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public RiftCollarBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FORMED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalKineticBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FORMED);
    }

    // -------------------------------------------------------------- kinetics

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(HORIZONTAL_FACING).getAxis();
    }

    /** One shaft, on the face looking out of the machine. See {@link WellheadStructure#shaftTowards}. */
    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(HORIZONTAL_FACING);
    }

    // --------------------------------------------------------------- redstone

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof RiftCollarBlockEntity collar ? collar.comparatorSignal() : 0;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbour, BlockPos from,
                                   boolean moving) {
        super.neighborChanged(state, level, pos, neighbour, from, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RiftCollarBlockEntity collar) {
            collar.readSignal();
        }
    }

    // ------------------------------------------------------------ interaction

    /** A crystal in the hand is seated in the collar; anything else is a click on the machine. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof RiftCollarBlockEntity collar)
                || RiftCollarBlockEntity.tierOf(stack) == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            collar.seat(player, hand, stack);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Says what the collar is doing, or why it is doing nothing; crouching with an empty hand takes the crystal
     * back out of a well that is cold.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof RiftCollarBlockEntity collar)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                collar.unseat(player);
            } else {
                collar.report(player);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // -------------------------------------------------------------- assembly

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // Only a real departure. Forming and unforming flip FORMED with a setBlock of this same block.
        if (!level.isClientSide && !newState.is(this) && level.getBlockEntity(pos) instanceof RiftCollarBlockEntity collar) {
            collar.onCollarRemoved();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** A formed collar, and every block of its frame, refuses to move - for the reason a fissure does. */
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> BlockMovementChecks.registerMovementAllowedCheck((state, level, pos) ->
                state.getBlock() instanceof WellheadShellBlock
                        || state.getBlock() instanceof RiftWoundBlock
                        || state.getBlock() instanceof RiftCollarBlock && state.getValue(FORMED)
                        ? BlockMovementChecks.CheckResult.FAIL
                        : BlockMovementChecks.CheckResult.PASS));
    }

    // ------------------------------------------------------------ block entity

    @Override
    public Class<RiftCollarBlockEntity> getBlockEntityClass() {
        return RiftCollarBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RiftCollarBlockEntity> getBlockEntityType() {
        return AWBlockEntities.RIFT_COLLAR.get();
    }
}
