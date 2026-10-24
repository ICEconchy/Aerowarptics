package uk.co.iceconchy.aerowarptics.drive;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.List;

/**
 * A Drive Extender: one block of the cube a Rift Drive can be built out into.
 *
 * <p>Build a complete cube of these, two to seven blocks along an edge, with the drive anywhere inside
 * it, and the drive is drawn the size of the cube. Every extender in it is then the drive as far as
 * anything outside is concerned - a shaft against it turns the drive, a module bolted to it serves the
 * drive, and right-clicking it opens the drive's console. See {@link DriveStructure} for the shape.
 *
 * <p>Until the cube is complete each one is an inert casing block that does nothing and serves nobody,
 * deliberately, for the Astrolabe's reason: a machine that half-works while half-built is a machine
 * players cannot reason about. That includes rotation - a loose extender has no shaft on any face.
 *
 * <h2>Why a kinetic block</h2>
 * Power has to reach a drive buried in the middle of its frame, and the only honest way to carry
 * rotation in Create is to be part of the network: a formed extender is a relay with a shaft on every
 * face and no stress of its own, so the drive's load is counted on whatever turns the frame, exactly as
 * if the shaft ran to the drive itself. Reading a nearby shaft's speed and charging from it instead would
 * be free power as far as Create's stress was concerned.
 *
 * <p>Not see-through in its looks, but declared so, because a formed one is not drawn at all - the
 * enlarged drive is - and the model is a cage you can see into. Were these occluding, every block face
 * pressed against the frame from outside would be culled and the cage would show holes into the void.
 */
public class DriveExtenderBlock extends KineticBlock implements IBE<DriveExtenderBlockEntity> {

    public static final MapCodec<DriveExtenderBlock> CODEC = simpleCodec(DriveExtenderBlock::new);

    /** Whether this extender is part of a complete frame. Kept in the state so it can stop being drawn. */
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public DriveExtenderBlock(Properties properties) {
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

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // A formed frame is one model spanning the whole cube, drawn by the drive's renderer. A loose
        // extender is an ordinary casing block, so it still looks like something in the world and a hand.
        return state.getValue(FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    // -------------------------------------------------------------- kinetics

    /**
     * Nominal. A relay with a shaft on every face turns about no axis in particular; Create only reads
     * this to decide whether two states of the block are the same kinetically, which FORMED decides here.
     */
    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }

    /**
     * A shaft on every face of a formed extender, and none on a loose one - except against a block that
     * belongs to a different drive, so two frames built side by side stay two machines.
     */
    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        if (!state.getValue(FORMED)) {
            return false;
        }
        BlockPos drivePos = level.getBlockEntity(pos) instanceof DriveExtenderBlockEntity extender
                ? extender.drivePos() : null;
        return drivePos != null && DriveAssembly.mayShareShaft(level, drivePos, pos.relative(face));
    }

    /**
     * Forming and unforming change which faces have shafts, so Create has to treat them as a change to
     * the network. By default it only counts a change of axis, and a frame would form and sit there
     * connected to nothing until something else nudged it.
     */
    @Override
    protected boolean areStatesKineticallyEquivalent(BlockState oldState, BlockState newState) {
        return super.areStatesKineticallyEquivalent(oldState, newState)
                && oldState.getValue(FORMED) == newState.getValue(FORMED);
    }

    // --------------------------------------------------------------- redstone

    /**
     * A comparator against a formed extender reads the drive, as everything else outside the frame does.
     * A drive built out to seven blocks is otherwise a drive nothing can be put beside.
     */
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return state.getValue(FORMED);
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        RiftDriveBlockEntity drive = state.getValue(FORMED) ? DriveAssembly.driveAt(level, pos) : null;
        return drive == null ? DriveSignal.IDLE : drive.comparatorSignal();
    }

    // -------------------------------------------------------------- assembly

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        // Only a real arrival. Forming and unforming flip FORMED with a setBlock of this same block, and
        // that must not set the search off again from inside itself.
        if (!level.isClientSide && !oldState.is(this)) {
            DriveAssembly.offerTo(level, List.of(pos));
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        RiftDriveBlockEntity drive = null;
        if (!level.isClientSide && !newState.is(this)
                && level.getBlockEntity(pos) instanceof DriveExtenderBlockEntity extender) {
            drive = extender.drive();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);

        // The frame this was part of is short a block, which is not that frame any more. Taken apart
        // after the removal has really happened, so that forming again sees the hole: what is left may
        // still be a smaller drive, or exactly the cells a neighbouring drive was waiting for.
        if (drive != null) {
            BlockPos drivePos = drive.getBlockPos();
            List<BlockPos> freed = DriveAssembly.unform(level, drive);
            DriveAssembly.tryForm(level, drivePos);
            DriveAssembly.offerTo(level, freed);
        }
    }

    // ------------------------------------------------------------ interaction

    /**
     * Opens the drive's console, exactly as clicking the drive would.
     *
     * <p>The console itself is addressed to the drive's position, so from here on nothing downstream
     * knows or cares that the click landed on an extender - permission, reach and everything the panel
     * shows are asked of the drive.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        // Somebody holding extenders is building, and a click that opened a console or complained about
        // an unfinished cube would eat the placement - a cube is laid almost entirely against itself.
        if (isBuilding(player)) {
            return InteractionResult.PASS;
        }
        RiftDriveBlockEntity drive = level.getBlockEntity(pos) instanceof DriveExtenderBlockEntity extender
                ? extender.drive() : null;
        if (drive == null) {
            // A loose extender is not a machine, so a click with anything in hand goes to that thing.
            if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide) {
                AWLang.translate("gui.drive_extender.incomplete").sendStatus(player);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            AWClientHooks.requestRiftNavigation(drive);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Whether a player has Drive Extenders in either hand. */
    public static boolean isBuilding(Player player) {
        return holdsExtender(player.getMainHandItem()) || holdsExtender(player.getOffhandItem());
    }

    private static boolean holdsExtender(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof DriveExtenderBlock;
    }

    // ------------------------------------------------------------ block entity

    @Override
    public Class<DriveExtenderBlockEntity> getBlockEntityClass() {
        return DriveExtenderBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends DriveExtenderBlockEntity> getBlockEntityType() {
        return AWBlockEntities.DRIVE_EXTENDER.get();
    }
}
