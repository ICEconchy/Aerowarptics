package uk.co.iceconchy.aerowarptics.collapse;

import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;

/**
 * One of the eight blocks round the master in the bottom layer of a relic machine - a Collapse Cradle or a Rift
 * Plotter - and the reason that whole floor takes a pipe and a funnel, and each side of it a shaft.
 *
 * <p>The rest of a relic's body is inert ({@code CollapseCradlePartBlock}, {@code RiftPlotterPartBlock}): no block
 * entity, handing every question to the master. That was enough for a click and for redstone, and it was not enough
 * for Create. A Create pipe asks whether a block holds fluid by looking for a <em>block entity</em> there before it
 * asks for the capability, and so does a funnel - so no pipe would join any part, and the master, boxed in on every
 * side but the shaft's, took none.
 *
 * <p>So each base is a kinetic block with a block entity, which is what a pipe and a funnel look for; the capabilities
 * registered for the base block hand them the master's tank and slots. And the base in the middle of each side of the
 * floor has one shaft, on its face looking out (see {@link CradleStructure#shaftTowards}): the machine is turned from
 * its sides, by a line run into any of the four, or from under the master, which has a shaft on its underside. No
 * base has a second shaft and none faces another block of the machine, so no rotation crosses the floor or leaves
 * it - see {@link RelicFloor}, which is also how the master comes by its speed and where its load is counted.
 *
 * <p>Everything else is a part's: a base draws nothing, knows its master by its place in the footprint (x and z; it is
 * always in the bottom layer), passes clicks, redstone and a sneak-wrench to it, and breaking it breaks the machine.
 */
public abstract class RelicBaseBlock extends KineticBlock implements IBE<RelicBaseBlockEntity> {

    /** Where in the footprint this base stands, each 0..2 - it is always on the bottom layer. */
    public static final IntegerProperty X = IntegerProperty.create("x", 0, CradleStructure.SIZE - 1);
    public static final IntegerProperty Z = IntegerProperty.create("z", 0, CradleStructure.SIZE - 1);

    protected RelicBaseBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(X, 0).setValue(Z, 0));
    }

    /** Whether a state is this machine's master block. */
    protected abstract boolean isMaster(BlockState state);

    /** The machine, as the item a player picks it up as. */
    protected abstract ItemStack machine();

    /** Tells the master a redstone signal may have changed at one of its blocks. */
    protected abstract void signal(Level level, BlockPos master);

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(X, Z);
    }

    /** The base standing at a place on the footprint's floor. */
    public BlockState at(int x, int z) {
        return defaultBlockState().setValue(X, x).setValue(Z, z);
    }

    /** Where a base's master is. */
    public static BlockPos master(BlockPos pos, BlockState state) {
        return CradleStructure.master(pos, state.getValue(X), CradleStructure.MASTER_Y, state.getValue(Z));
    }

    /** Whether this base still belongs to a machine: its master is there. */
    public boolean stillValid(BlockGetter level, BlockPos pos, BlockState state) {
        return state.getBlock() == this && isMaster(level.getBlockState(master(pos, state)));
    }

    /** The master of the relic machine a block belongs to, or {@code null} if it belongs to none. */
    @Nullable
    public static BlockPos masterOf(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof RelicBaseBlock) {
            return master(pos, state);
        }
        if (state.getBlock() instanceof CollapseCradleBlock
                || state.getBlock() instanceof uk.co.iceconchy.aerowarptics.plotter.RiftPlotterBlock
                || state.getBlock() instanceof uk.co.iceconchy.aerowarptics.ward.RiftWardBlock) {
            return pos;
        }
        if (state.getBlock() instanceof uk.co.iceconchy.aerowarptics.ward.RiftWardPartBlock) {
            return uk.co.iceconchy.aerowarptics.ward.RiftWardPartBlock.master(pos, state);
        }
        if (state.getBlock() instanceof CollapseCradlePartBlock) {
            return CollapseCradlePartBlock.master(pos, state);
        }
        if (state.getBlock() instanceof uk.co.iceconchy.aerowarptics.plotter.RiftPlotterPartBlock) {
            return uk.co.iceconchy.aerowarptics.plotter.RiftPlotterPartBlock.master(pos, state);
        }
        return null;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    // ------------------------------------------------------------------ kinetics

    /** Nominal: most bases have no shaft at all, and the four that do have theirs along x or z. */
    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        if (!stillValid(level, pos, state)) {
            return false;
        }
        return CradleStructure.shaftTowards(master(pos, state), pos, face);
    }

    // ------------------------------------------------------------------ passing it on

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stillValid(level, pos, state)) {
            return ItemInteractionResult.FAIL;
        }
        BlockPos master = master(pos, state);
        return level.getBlockState(master).useItemOn(stack, level, player, hand, hit.withPosition(master));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!stillValid(level, pos, state)) {
            return InteractionResult.FAIL;
        }
        BlockPos master = master(pos, state);
        return level.getBlockState(master).useWithoutItem(level, player, hit.withPosition(master));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbour, BlockPos from,
                                   boolean moving) {
        super.neighborChanged(state, level, pos, neighbour, from, moving);
        if (!level.isClientSide && stillValid(level, pos, state)) {
            signal(level, master(pos, state));
        }
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos,
                                       Player player) {
        return machine();
    }

    /** A wrench does not turn a relay the machine is built on. */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }

    /** Picking a base up with a wrench picks up the machine, as it would from its master. */
    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!stillValid(level, pos, state)) {
            return InteractionResult.PASS;
        }
        BlockPos master = master(pos, state);
        UseOnContext onMaster = new UseOnContext(level, context.getPlayer(), context.getHand(), context.getItemInHand(),
                new BlockHitResult(context.getClickLocation(), context.getClickedFace(), master, context.isInside()));
        return super.onSneakWrenched(level.getBlockState(master), onMaster);
    }

    // ------------------------------------------------------------------ coming apart

    /** Breaking a base is breaking the machine, as a part - see {@code CollapseCradlePartBlock}. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (stillValid(level, pos, state)) {
            BlockPos master = master(pos, state);
            level.destroyBlockProgress(master.hashCode(), master, -1);
            if (!level.isClientSide && (player.isCreative() || !player.hasCorrectToolForDrops(level.getBlockState(master)))) {
                level.destroyBlock(master, false);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && stillValid(level, pos, state)) {
            level.destroyBlock(master(pos, state), true);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        if (!stillValid(level, pos, state) && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 1);
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    /** A base whose master has gone removes itself, a tick later rather than at once, as a part does. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!stillValid(level, pos, state)) {
            level.removeBlock(pos, false);
        }
    }

    @Override
    public boolean addLandingEffects(BlockState state, ServerLevel level, BlockPos pos, BlockState entityState,
                                     LivingEntity entity, int particles) {
        return true;
    }

    @Override
    public Class<RelicBaseBlockEntity> getBlockEntityClass() {
        return RelicBaseBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RelicBaseBlockEntity> getBlockEntityType() {
        return AWBlockEntities.RELIC_BASE.get();
    }
}
