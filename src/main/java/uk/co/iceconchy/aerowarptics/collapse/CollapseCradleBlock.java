package uk.co.iceconchy.aerowarptics.collapse;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.registry.AWBlocks;
import uk.co.iceconchy.aerowarptics.trace.Trace;

/**
 * The Collapse Cradle: where a Singularity Core is collapsed out of a Rift Core and a Scar Heart.
 *
 * <p>A relic, rebuilt: three blocks across and three high, placed whole from one item. This is its master block,
 * in the middle of the bottom layer - the one with the block entity, turned from below or through the middle of each
 * side of its floor,
 * the one the renderer draws the whole machine from; the other twenty-six are {@link CollapseCradlePartBlock}s
 * (see {@link CradleStructure}). It is turned at the Singularity drive's own speeds - if you cannot turn the
 * cradle, you cannot fly the drive it makes. It holds a core and a heart in two slots and essence in a tank on
 * a pipe; the offerings stand on Create Depots round it, and a redstone rising edge at any of its sides starts
 * the ritual. See {@link CollapseCradleBlockEntity}.
 *
 * <p>It is too heavy to move: a piston will not push it, a contraption will not take it, a ship will not be
 * built round it, and it will not be set down on a deck. A ritual never ran aboard a ship anyway, and a machine
 * of twenty-seven blocks carried by something that moves blocks one at a time is a machine that arrives in
 * pieces - the Astrolabe's lesson, which this one does not need to learn.
 */
public class CollapseCradleBlock extends KineticBlock implements IBE<CollapseCradleBlockEntity> {

    public static final MapCodec<CollapseCradleBlock> CODEC = simpleCodec(CollapseCradleBlock::new);

    public CollapseCradleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        // One, on its underside: a line run in from below ends here, as one run into a side ends at that side's base.
        // The machine goes by the fastest of the five - see RelicFloor.
        return CradleStructure.shaftTowards(pos, pos, face);
    }

    @Override
    public IRotate.SpeedLevel getMinimumRequiredSpeedLevel() {
        return IRotate.SpeedLevel.FAST;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbour, BlockPos from,
                                   boolean moving) {
        super.neighborChanged(state, level, pos, neighbour, from, moving);
        if (!level.isClientSide) {
            readSignal(level, pos);
        }
    }

    /** Whether any block of the machine has a signal at it, told to the master: a rising edge asks for a ritual. */
    static void readSignal(Level level, BlockPos master) {
        if (!(level.getBlockEntity(master) instanceof CollapseCradleBlockEntity cradle)) {
            return;
        }
        boolean powered = false;
        for (BlockPos cell : CradleStructure.cells(master)) {
            if (level.hasNeighborSignal(cell)) {
                powered = true;
                break;
            }
        }
        cradle.signal(powered);
    }

    // ------------------------------------------------------------------ standing up

    /**
     * Where the player aims is the middle of the machine's floor; the rest of it needs to be clear, and on the
     * ground. Refused, the item says which - see {@link CollapseCradleItem}.
     */
    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (aboard(level, pos) || !CradleStructure.fits(pos, level.getMinBuildHeight(), level.getMaxBuildHeight(),
                cell -> level.getBlockState(cell).canBeReplaced())) {
            return null;
        }
        return super.getStateForPlacement(context);
    }

    /** Whether a position is on a ship: in a plot, which is where Sable keeps every hull's blocks. */
    static boolean aboard(Level level, BlockPos pos) {
        return dev.ryanhcode.sable.Sable.HELPER.getContaining(level, pos) != null;
    }

    /**
     * The machine refuses to move, whoever asks - every block of it. Create's contraptions ask this of each
     * block they would take, and Simulated's physics assembler asks it of every block it gathers, so neither a
     * contraption nor a ship takes one block of a cradle and leaves the rest. The fissure refuses the same way.
     */
    public static void onCommonSetup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(() -> com.simibubi.create.api.contraption.BlockMovementChecks.registerMovementAllowedCheck(
                (state, level, pos) -> state.getBlock() instanceof CollapseCradleBlock
                        || state.getBlock() instanceof CollapseCradlePartBlock
                        || state.getBlock() instanceof CollapseCradleBaseBlock
                        ? com.simibubi.create.api.contraption.BlockMovementChecks.CheckResult.FAIL
                        : com.simibubi.create.api.contraption.BlockMovementChecks.CheckResult.PASS));
    }

    /**
     * Raises the parts a tick after the master is set down, however it was set down - by hand, by a command, by
     * a schematic - and again whenever the master loads, which mends a machine a world edit has holed. A cell
     * something solid has taken since leaves the machine nowhere to stand, and it breaks back into its item.
     */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide && !old.is(this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        CollapseCradlePartBlock parts = AWBlocks.COLLAPSE_CRADLE_PART.get();
        CollapseCradleBaseBlock bases = AWBlocks.COLLAPSE_CRADLE_BASE.get();
        for (Vec3i offset : CradleStructure.offsets()) {
            if (offset.equals(Vec3i.ZERO)) {
                continue;
            }
            BlockPos cell = pos.offset(offset);
            int[] place = CradleStructure.placeOf(offset);
            // The floor is bases, which a pipe can join and, at the middle of each side, a shaft; the body above is inert parts.
            BlockState wanted = offset.getY() == 0 ? bases.at(place[0], place[2]) : parts.at(place[0], place[1], place[2]);
            BlockState there = level.getBlockState(cell);
            if (there == wanted) {
                continue;
            }
            // A machine set down before its floor was bases has inert parts there: those are its own, and are swapped.
            boolean ours = there.getBlock() instanceof CollapseCradlePartBlock && CollapseCradlePartBlock.master(cell, there).equals(pos);
            if (!ours && !there.canBeReplaced()) {
                Trace.log(Trace.COLLAPSE, "cradle at {} has no room to stand: {} is at {}", Trace.pos(pos),
                        there.getBlock(), Trace.pos(cell));
                level.destroyBlock(pos, true);
                return;
            }
            level.setBlock(cell, wanted, Block.UPDATE_ALL);
        }
    }

    // ------------------------------------------------------------------ hands

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        return level.getBlockEntity(pos) instanceof CollapseCradleBlockEntity cradle && cradle.putIn(player, hand)
                ? ItemInteractionResult.SUCCESS
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** An empty hand - or one whose item the cradle would not take - opens its panel. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer server
                && level.getBlockEntity(pos) instanceof CollapseCradleBlockEntity cradle) {
            server.openMenu(cradle, buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.CONSUME;
    }

    // ------------------------------------------------------------------ coming apart

    /** Going, the master empties itself and takes its parts with it, dropping nothing more. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof CollapseCradleBlockEntity cradle) {
                cradle.dropContents();
            }
            if (!level.isClientSide) {
                for (BlockPos cell : CradleStructure.cells(pos)) {
                    BlockState there = level.getBlockState(cell);
                    boolean part = there.getBlock() instanceof CollapseCradlePartBlock
                            && CollapseCradlePartBlock.master(cell, there).equals(pos);
                    boolean base = there.getBlock() instanceof CollapseCradleBaseBlock
                            && uk.co.iceconchy.aerowarptics.collapse.RelicBaseBlock.master(cell, there).equals(pos);
                    if (part || base) {
                        level.removeBlock(cell, false);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public Class<CollapseCradleBlockEntity> getBlockEntityClass() {
        return CollapseCradleBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CollapseCradleBlockEntity> getBlockEntityType() {
        return AWBlockEntities.COLLAPSE_CRADLE.get();
    }
}
