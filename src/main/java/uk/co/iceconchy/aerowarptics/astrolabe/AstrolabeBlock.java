package uk.co.iceconchy.aerowarptics.astrolabe;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.registry.AWBlocks;
import uk.co.iceconchy.aerowarptics.util.AWLang;

/**
 * A cell of the Astrolabe Cartography Table.
 *
 * <p>Nine of these in a three-by-three square make a table. Until then each is a loose brass panel
 * that does nothing, which is deliberate: a multiblock that half-works while half-built is a
 * multiblock players cannot reason about.
 *
 * <p>Forming and unforming are driven entirely by placement and breaking rather than by a tick. The
 * last block placed is always the one that completes the square, and the first broken is always the
 * one that ruins it, so there is nothing for a poll to notice that these two moments do not.
 */
public class AstrolabeBlock extends Block implements IBE<AstrolabeBlockEntity> {

    public static final MapCodec<AstrolabeBlock> CODEC = simpleCodec(AstrolabeBlock::new);

    /** Whether this cell is part of a complete table. Kept in the state so the model can change. */
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    /** A table is waist height: something to lean over rather than a full block to walk into. */
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 13.0D, 16.0D);

    public AstrolabeBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FORMED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    // ------------------------------------------------------------- appearance

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // A formed table is one model spanning its whole footprint, drawn by the origin's renderer.
        // A loose panel is an ordinary baked model, so it still looks like something in a hand.
        return state.getValue(FORMED) ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.MODEL;
    }

    // -------------------------------------------------------------- assembly

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !oldState.is(this)) {
            tryForm(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !newState.is(this)) {
            // Any table this cell was part of is now short a block, which is not that table any more.
            for (BlockPos candidate : AstrolabeStructure.neighbourhood(pos)) {
                if (level.getBlockEntity(candidate) instanceof AstrolabeBlockEntity origin
                        && origin.isMaster() && origin.table().covers(pos)) {
                    unform(level, origin.table());
                }
            }
        }
        IBE.onRemove(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, movedByPiston);

        // What is left may still be a table, just a smaller one - break a corner off a three by
        // three and the remaining eight blocks hold a perfectly good two by two. Re-formed after the
        // removal has actually happened, so the search sees the hole rather than the block that was
        // about to leave.
        if (!level.isClientSide && !newState.is(this)) {
            for (BlockPos candidate : AstrolabeStructure.neighbourhood(pos)) {
                tryForm(level, candidate);
            }
        }
    }

    /**
     * Forms the largest table this block completes, if it completes one.
     *
     * <p>A cell may be built in when it is loose, or when it belongs to a table that would sit
     * <em>wholly inside</em> the one being formed. That second case is what lets a table grow: a
     * single block is already a working one-by-one, so without it a player could never build up to a
     * two-by-two, because every block they laid would have formed a table of its own first.
     *
     * <p>What it still refuses is stealing. A cell belonging to a table that pokes outside the
     * candidate is off limits, so laying blocks beside a finished table cannot dismantle it and leave
     * a hole where somebody's chart used to be.
     */
    private static void tryForm(Level level, BlockPos placed) {
        if (!(level.getBlockState(placed).is(AWBlocks.ASTROLABE.get()))) {
            return;
        }
        // Walked here rather than through a single predicate, because whether a cell may join
        // depends on which table is being considered - a cell belonging to a one-by-one is fair game
        // for the three-by-three around it and off limits to a two-by-two beside it.
        AstrolabeStructure.Table table = null;
        for (AstrolabeStructure.Table option : AstrolabeStructure.candidates(placed)) {
            if (AstrolabeStructure.isComplete(option, cell -> canJoin(level, cell, option))) {
                table = option;
                break;
            }
        }
        if (table == null) {
            return;
        }
        // Absorb whatever smaller tables were inside this one before claiming their cells.
        for (BlockPos cell : table.cells()) {
            if (level.getBlockEntity(cell) instanceof AstrolabeBlockEntity be && be.isFormed()
                    && !be.table().equals(table)) {
                unform(level, be.table());
            }
        }
        for (BlockPos cell : table.cells()) {
            level.setBlock(cell, level.getBlockState(cell).setValue(FORMED, true), Block.UPDATE_ALL);
            if (level.getBlockEntity(cell) instanceof AstrolabeBlockEntity be) {
                be.setMaster(table.origin(), table.size());
            }
        }
        level.playSound(null, table.origin(), net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, 1.6F);
    }

    /** Breaks a table back into loose cells. */
    private static void unform(Level level, AstrolabeStructure.Table table) {
        for (BlockPos cell : table.cells()) {
            if (!(level.getBlockEntity(cell) instanceof AstrolabeBlockEntity be)) {
                continue;
            }
            if (!table.origin().equals(be.master())) {
                continue;
            }
            be.setMaster(null, 0);
            BlockState state = level.getBlockState(cell);
            if (state.hasProperty(FORMED) && state.getValue(FORMED)) {
                level.setBlock(cell, state.setValue(FORMED, false), Block.UPDATE_ALL);
            }
        }
    }

    /** Whether a cell may be built into the table being considered. */
    private static boolean canJoin(LevelAccessor level, BlockPos pos, AstrolabeStructure.Table into) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(AWBlocks.ASTROLABE.get())) {
            return false;
        }
        if (!state.getValue(FORMED)) {
            return true;
        }
        return level.getBlockEntity(pos) instanceof AstrolabeBlockEntity be
                && be.isFormed()
                && be.table().isInside(into);
    }

    // ------------------------------------------------------------ interaction

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!state.getValue(FORMED)) {
            if (!level.isClientSide) {
                AWLang.translate("gui.astrolabe.incomplete").sendStatus(player);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            AWClientHooks.requestAstrolabeChart(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ------------------------------------------------------------ block entity

    @Override
    public Class<AstrolabeBlockEntity> getBlockEntityClass() {
        return AstrolabeBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends AstrolabeBlockEntity> getBlockEntityType() {
        return AWBlockEntities.ASTROLABE.get();
    }
}
