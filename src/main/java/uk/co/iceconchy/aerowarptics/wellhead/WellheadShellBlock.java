package uk.co.iceconchy.aerowarptics.wellhead;

import com.simibubi.create.api.equipment.goggles.IProxyHoveringInformation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.fissure.RiftFissureBlockEntity;

/**
 * A block of a formed Rift Wellhead's shell that is not its collar - see {@link WellheadStructure}.
 *
 * <p>Holds nothing and decides nothing. It stands where a block of casing or glass stood so the machine keeps
 * the body the player built, and everything it is asked it passes to the collar: a click, a redstone signal at
 * the machine's side, the goggles' tooltip, a comparator, a pick-block. The pattern is the Collapse Cradle's
 * parts', with one difference that matters - a part here does not know where its master is. It knows where its
 * <em>fissure</em> is, by three numbers in its block state, and the fissure knows which collar holds it. So a
 * frame of any size, with its collar in any side, needs no more state than a three-cube.
 *
 * <p>Breaking one breaks the frame open: every other block of it goes back to the casing or glass it was, and
 * this one drops what it was. What that does to a well with matter in it is the collar's affair.
 */
public abstract class WellheadShellBlock extends Block implements IProxyHoveringInformation {

    /** Where this block stands from its fissure, each 0..6 for -3..3 - see {@link WellheadStructure#offsetOf}. */
    public static final IntegerProperty X = IntegerProperty.create("x", 0, 2 * WellheadStructure.STATE_BIAS);
    public static final IntegerProperty Y = IntegerProperty.create("y", 0, 2 * WellheadStructure.STATE_BIAS);
    public static final IntegerProperty Z = IntegerProperty.create("z", 0, 2 * WellheadStructure.STATE_BIAS);

    protected WellheadShellBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(X, Y, Z);
    }

    /** This block, standing at an offset from its fissure. */
    public BlockState at(Vec3i offset) {
        int[] state = WellheadStructure.stateOf(offset);
        return defaultBlockState().setValue(X, state[0]).setValue(Y, state[1]).setValue(Z, state[2]);
    }

    public static Vec3i offset(BlockState state) {
        return WellheadStructure.offsetOf(state.getValue(X), state.getValue(Y), state.getValue(Z));
    }

    /** Where this block's fissure is. */
    public static BlockPos centre(BlockPos pos, BlockState state) {
        return pos.subtract(offset(state));
    }

    /**
     * The collar this block belongs to, or {@code null} if it has come apart: asked of the fissure, and then of
     * the collar itself, which has to agree that its frame reaches this far.
     */
    @Nullable
    public static RiftCollarBlockEntity collar(BlockGetter level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof WellheadShellBlock)
                || !(level.getBlockEntity(centre(pos, state)) instanceof RiftFissureBlockEntity fissure)) {
            return null;
        }
        BlockPos at = fissure.harnessedBy();
        if (at == null || !(level.getBlockEntity(at) instanceof RiftCollarBlockEntity collar) || collar.isRemoved()) {
            return null;
        }
        return collar.radius() == WellheadStructure.radiusOf(offset(state)) ? collar : null;
    }

    public static boolean stillValid(BlockGetter level, BlockPos pos, BlockState state) {
        return collar(level, pos, state) != null;
    }

    /** What this block was before the frame formed, and is again when it comes apart. */
    public abstract BlockState original(BlockState state);

    // ------------------------------------------------------------------ passing it on

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        RiftCollarBlockEntity collar = collar(level, pos, state);
        if (collar == null) {
            return ItemInteractionResult.FAIL;
        }
        BlockPos at = collar.getBlockPos();
        return level.getBlockState(at).useItemOn(stack, level, player, hand, hit.withPosition(at));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        RiftCollarBlockEntity collar = collar(level, pos, state);
        if (collar == null) {
            return InteractionResult.FAIL;
        }
        BlockPos at = collar.getBlockPos();
        return level.getBlockState(at).useWithoutItem(level, player, hit.withPosition(at));
    }

    /** A signal at the machine's side is a signal to the machine: the collar reads every block of its frame. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbour, BlockPos from,
                                   boolean moving) {
        super.neighborChanged(state, level, pos, neighbour, from, moving);
        if (!level.isClientSide) {
            RiftCollarBlockEntity collar = collar(level, pos, state);
            if (collar != null) {
                collar.readSignal();
            }
        }
    }

    /** A comparator against any block of the frame reads the well, as one against the collar does. */
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        RiftCollarBlockEntity collar = collar(level, pos, state);
        return collar == null ? 0 : collar.comparatorSignal();
    }

    @Override
    public BlockPos getInformationSource(Level level, BlockPos pos, BlockState state) {
        RiftCollarBlockEntity collar = collar(level, pos, state);
        return collar == null ? pos : collar.getBlockPos();
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos,
                                       Player player) {
        return new ItemStack(original(state).getBlock());
    }

    // ------------------------------------------------------------------ coming apart

    /**
     * Whatever took this block - a pickaxe, an explosion, a command - has broken the frame open. When it is the
     * frame coming apart that takes it, the collar has already let go by the time this hears of it, so this does
     * nothing and nothing is dropped twice.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!level.isClientSide && !(newState.getBlock() instanceof WellheadShellBlock)) {
            RiftCollarBlockEntity collar = collar(level, pos, state);
            if (collar != null) {
                if (newState.isAir()) {
                    Block.popResource(level, pos, new ItemStack(original(state).getBlock()));
                }
                WellheadAssembly.breach(level, collar, pos);
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    /** A block whose machine has gone - a world edit, a chunk put back - turns back into what it was, a tick later. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        if (!stillValid(level, pos, state) && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 1);
        }
        return state;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!stillValid(level, pos, state)) {
            level.setBlock(pos, original(state), Block.UPDATE_ALL);
        }
    }

    @Override
    public boolean addLandingEffects(BlockState state, ServerLevel level, BlockPos pos, BlockState entityState,
                                     LivingEntity entity, int particles) {
        return true;
    }
}
