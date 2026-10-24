package uk.co.iceconchy.aerowarptics.ward;

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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.registry.AWBlocks;
import uk.co.iceconchy.aerowarptics.trace.Trace;

/**
 * The Rift Ward: a relic that holds a field over the ground round it, and turns aside a rift a stranger aims into
 * that field - a ship's arrival, a Rift Plotter's far end - so that it comes out at the field's rim instead.
 *
 * <p>The third of the relics, and built as the other two are: three blocks across and three high, placed whole
 * from one item. This is its master block, in the middle of the bottom layer - the block entity, the renderer; the
 * other twenty-six are {@link RiftWardBaseBlock}s and {@link RiftWardPartBlock}s (see {@link WardStructure}).
 * Essence goes in at any of its blocks by pipe or bucket, and an empty hand opens its panel. It takes no orders
 * from a circuit, and gives one two readings: a comparator against any block of it reads how full its charge is,
 * and every block of it gives a pulse when it turns something aside. See {@link RiftWardBlockEntity}.
 *
 * <p>It will not move: not by piston, contraption or ship. A field is held over a place.
 */
public class RiftWardBlock extends KineticBlock implements IBE<RiftWardBlockEntity> {

    public static final MapCodec<RiftWardBlock> CODEC = simpleCodec(RiftWardBlock::new);

    /**
     * Whether the field stands. The machine gives off light only while it does - see
     * {@link RiftWardBlockEntity} - so a ward that is working can be told from one that is not from across a base.
     */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty LIT =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT;

    /** How brightly a ward whose field stands lights the ground round it. */
    public static final int LIGHT = 10;

    public RiftWardBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(
            net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
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
        return uk.co.iceconchy.aerowarptics.collapse.CradleStructure.shaftTowards(pos, pos, face);
    }

    @Override
    public IRotate.SpeedLevel getMinimumRequiredSpeedLevel() {
        return IRotate.SpeedLevel.SLOW;
    }

    // ------------------------------------------------------------------ what it tells a circuit

    /** The signal every block of a ward gives while it is turning something aside, read from its master. */
    static int pulse(BlockGetter level, BlockPos master) {
        return level.getBlockEntity(master) instanceof RiftWardBlockEntity ward && ward.pulsing() ? 15 : 0;
    }

    /** What a comparator reads off any block of a ward: how full its charge is. */
    static int reading(BlockGetter level, BlockPos master) {
        return level.getBlockEntity(master) instanceof RiftWardBlockEntity ward ? ward.comparator() : 0;
    }

    /**
     * Tells every circuit touching the machine that one of its two readings has changed. All twenty-seven blocks
     * give them, so all twenty-seven have neighbours to tell.
     */
    static void tellCircuits(Level level, BlockPos master) {
        for (BlockPos cell : WardStructure.cells(master)) {
            Block block = level.getBlockState(cell).getBlock();
            level.updateNeighborsAt(cell, block);
            level.updateNeighbourForOutputSignal(cell, block);
        }
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return pulse(level, pos);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return reading(level, pos);
    }

    // ------------------------------------------------------------------ standing up

    /**
     * Where the player aims is the middle of the machine's floor; the rest of it needs to be clear, and on the
     * ground. Refused, the item says which - see {@link RiftWardItem}.
     */
    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (aboard(level, pos) || !WardStructure.fits(pos, level.getMinBuildHeight(), level.getMaxBuildHeight(),
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
     * The machine refuses to move, whoever asks - every block of it, as the cradle's does. Create's contraptions and
     * Simulated's assembler both ask this of each block they would take.
     */
    public static void onCommonSetup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(() -> com.simibubi.create.api.contraption.BlockMovementChecks.registerMovementAllowedCheck(
                (state, level, pos) -> state.getBlock() instanceof RiftWardBlock
                        || state.getBlock() instanceof RiftWardPartBlock
                        || state.getBlock() instanceof RiftWardBaseBlock
                        ? com.simibubi.create.api.contraption.BlockMovementChecks.CheckResult.FAIL
                        : com.simibubi.create.api.contraption.BlockMovementChecks.CheckResult.PASS));
    }

    /** Whoever sets a ward down owns it: its field lets them through, and its panel is theirs. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof RiftWardBlockEntity ward) {
            ward.placedBy(player);
        }
    }

    /** Raises the parts a tick after the master is set down, and again whenever it loads - see the cradle's. */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!level.isClientSide && !old.is(this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        RiftWardPartBlock parts = AWBlocks.RIFT_WARD_PART.get();
        RiftWardBaseBlock bases = AWBlocks.RIFT_WARD_BASE.get();
        for (Vec3i offset : WardStructure.offsets()) {
            if (offset.equals(Vec3i.ZERO)) {
                continue;
            }
            BlockPos cell = pos.offset(offset);
            int[] place = WardStructure.placeOf(offset);
            // The floor is bases, which a pipe can join and, at the middle of each side, a shaft; the body above is inert parts.
            BlockState wanted = offset.getY() == 0 ? bases.at(place[0], place[2]) : parts.at(place[0], place[1], place[2]);
            BlockState there = level.getBlockState(cell);
            if (there == wanted) {
                continue;
            }
            if (!there.canBeReplaced()) {
                Trace.log(Trace.WARD, "ward at {} has no room to stand: {} is at {}", Trace.pos(pos),
                        there.getBlock(), Trace.pos(cell));
                level.destroyBlock(pos, true);
                return;
            }
            level.setBlock(cell, wanted, Block.UPDATE_ALL);
        }
    }

    // ------------------------------------------------------------------ hands

    /** A bucket fills or empties the tank; anything else opens the panel, as an empty hand does. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.isEmpty() && FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Opens the panel, off the machine as this client has it; an edit is the server's to accept. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide && level.getBlockEntity(pos) instanceof RiftWardBlockEntity ward) {
            AWClientHooks.openWardPanel(ward);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ------------------------------------------------------------------ coming apart

    /** Going, the master lets its field go and takes its parts with it. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof RiftWardBlockEntity ward) {
                ward.broken();
            }
            if (!level.isClientSide) {
                for (BlockPos cell : WardStructure.cells(pos)) {
                    BlockState there = level.getBlockState(cell);
                    boolean part = there.getBlock() instanceof RiftWardPartBlock
                            && RiftWardPartBlock.master(cell, there).equals(pos);
                    boolean base = there.getBlock() instanceof RiftWardBaseBlock
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
    public Class<RiftWardBlockEntity> getBlockEntityClass() {
        return RiftWardBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RiftWardBlockEntity> getBlockEntityType() {
        return AWBlockEntities.RIFT_WARD.get();
    }
}
