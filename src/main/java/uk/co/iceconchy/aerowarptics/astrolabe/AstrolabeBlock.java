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
        // A formed table is one model spanning nine blocks, drawn by the centre's renderer. A loose
        // panel is an ordinary baked model, so it still looks like something in a player's hand.
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
            // Any table this cell was part of is now nine-minus-one blocks, which is not a table.
            for (BlockPos candidate : AstrolabeStructure.cells(pos)) {
                if (level.getBlockEntity(candidate) instanceof AstrolabeBlockEntity centre && centre.isMaster()) {
                    unform(level, candidate);
                }
            }
        }
        IBE.onRemove(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * Forms the table this block completes, if it completes one.
     *
     * <p>Only loose cells are considered eligible, so a new block laid alongside a finished table
     * cannot steal cells out of it and leave a hole where a working table used to be.
     */
    private static void tryForm(Level level, BlockPos placed) {
        BlockPos centre = AstrolabeStructure.findCentre(placed, pos -> isLooseCell(level, pos));
        if (centre == null) {
            return;
        }
        for (BlockPos cell : AstrolabeStructure.cells(centre)) {
            level.setBlock(cell, level.getBlockState(cell).setValue(FORMED, true), Block.UPDATE_ALL);
            if (level.getBlockEntity(cell) instanceof AstrolabeBlockEntity be) {
                be.setMaster(centre);
            }
        }
        level.playSound(null, centre, net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, 1.6F);
    }

    /** Breaks the table centred here back into loose cells. */
    private static void unform(Level level, BlockPos centre) {
        for (BlockPos cell : AstrolabeStructure.cells(centre)) {
            if (!(level.getBlockEntity(cell) instanceof AstrolabeBlockEntity be)) {
                continue;
            }
            if (!centre.equals(be.master())) {
                continue;
            }
            be.setMaster(null);
            BlockState state = level.getBlockState(cell);
            if (state.hasProperty(FORMED) && state.getValue(FORMED)) {
                level.setBlock(cell, state.setValue(FORMED, false), Block.UPDATE_ALL);
            }
        }
    }

    private static boolean isLooseCell(LevelAccessor level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(AWBlocks.ASTROLABE.get()) && !state.getValue(FORMED);
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
