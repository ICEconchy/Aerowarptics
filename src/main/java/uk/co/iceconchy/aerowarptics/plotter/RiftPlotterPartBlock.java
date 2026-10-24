package uk.co.iceconchy.aerowarptics.plotter;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.api.equipment.goggles.IProxyHoveringInformation;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import uk.co.iceconchy.aerowarptics.registry.AWItems;

/**
 * One of the twenty-six blocks of a Rift Plotter that are not its master - see {@link PlotterStructure}. The
 * Collapse Cradle's part, for the plotter: the same pattern, and for the same reasons.
 *
 * <p>Draws nothing, holds nothing and decides nothing. It stands in the footprint so the machine has a body a
 * player bumps into, stands on and clicks, and everything it is asked it passes to the master: a click with an
 * item or without, anything asking for its capabilities without a block entity (Create's pipes and funnels ask for
 * one first, so they join the floor's {@link uk.co.iceconchy.aerowarptics.collapse.RelicBaseBlock}s instead), a redstone
 * signal arriving at the machine's side, the goggles' tooltip, a wrench, a pick-block. Breaking any part breaks
 * the machine - the master drops the plotter, and takes every other part with it - as a bed or a door does. The
 * pattern is Create's, from the Large Water Wheel's structural blocks.
 *
 * <p>A part whose master has gone removes itself, a tick later rather than at once, so a machine is never
 * torn up by the order its blocks happen to arrive in.
 */
public class RiftPlotterPartBlock extends Block implements IWrenchable, IProxyHoveringInformation {

    public static final MapCodec<RiftPlotterPartBlock> CODEC = simpleCodec(RiftPlotterPartBlock::new);

    /** Where in the footprint this part stands, each 0..2 - see {@link PlotterStructure#placeOf}. */
    public static final IntegerProperty X = IntegerProperty.create("x", 0, PlotterStructure.SIZE - 1);
    public static final IntegerProperty Y = IntegerProperty.create("y", 0, PlotterStructure.SIZE - 1);
    public static final IntegerProperty Z = IntegerProperty.create("z", 0, PlotterStructure.SIZE - 1);

    public RiftPlotterPartBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(X, 0).setValue(Y, 0).setValue(Z, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(X, Y, Z);
    }

    /** The part standing at a place in the footprint. */
    public BlockState at(int x, int y, int z) {
        return defaultBlockState().setValue(X, x).setValue(Y, y).setValue(Z, z);
    }

    /** Where this part's master is. */
    public static BlockPos master(BlockPos pos, BlockState state) {
        return PlotterStructure.master(pos, state.getValue(X), state.getValue(Y), state.getValue(Z));
    }

    /** Whether this part still belongs to a machine: its master is there. */
    public static boolean stillValid(BlockGetter level, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof RiftPlotterPartBlock
                && level.getBlockState(master(pos, state)).getBlock() instanceof RiftPlotterBlock;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    // ------------------------------------------------------------------ passing it on

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
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

    /** A signal at the machine's side is a signal to the machine: the master reads every block of itself. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbour, BlockPos from,
                                   boolean moving) {
        super.neighborChanged(state, level, pos, neighbour, from, moving);
        if (!level.isClientSide && stillValid(level, pos, state)) {
            RiftPlotterBlock.readSignal(level, master(pos, state));
        }
    }

    @Override
    public BlockPos getInformationSource(Level level, BlockPos pos, BlockState state) {
        return stillValid(level, pos, state) ? master(pos, state) : pos;
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos,
                                       Player player) {
        return new ItemStack(AWItems.RIFT_PLOTTER.get());
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }

    /** Picking a part up with a wrench picks up the machine, as it would from its master. */
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
        return IWrenchable.super.onSneakWrenched(level.getBlockState(master), onMaster);
    }

    // ------------------------------------------------------------------ coming apart

    /**
     * A player breaking any part is breaking the machine. In creative, or without the tool the machine needs,
     * the master goes first and drops nothing; otherwise {@link #onRemove} takes it down with its drops.
     */
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

    /**
     * Whatever took this part - a pickaxe, an explosion, a command - takes the machine. When it is the master
     * going that takes the parts, the master is already gone by the time each part hears of it, so this does
     * nothing and nothing is dropped twice.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        // Swapped for a base by its own master - a machine set down before its floor was bases - is not a breakage.
        if (!state.is(newState.getBlock()) && stillValid(level, pos, state)
                && !(newState.getBlock() instanceof uk.co.iceconchy.aerowarptics.collapse.RelicBaseBlock)) {
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
        return state;
    }

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
}
