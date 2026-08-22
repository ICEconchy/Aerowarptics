package uk.co.iceconchy.aerowarptics.chute;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;

/**
 * The Rift Chute block.
 *
 * <p>Open on all four sides and with no facing at all. The aperture inside is a billboard that turns
 * to the viewer, so there is no front to get wrong, and a funnel may be fitted to whichever face
 * happens to be convenient.
 *
 * <p>Right-clicking opens its panel the same way the probe's and the gate's do - the client asks and
 * the server answers. Binding is a decision about what a player may reach and may see, so none of it
 * is worked out on the client.
 */
public class RiftChuteBlock extends Block implements IBE<RiftChuteBlockEntity> {

    public static final MapCodec<RiftChuteBlock> CODEC = simpleCodec(RiftChuteBlock::new);

    /**
     * A plinth, four corner posts and a lid - the shape of the cage rather than a solid block.
     *
     * <p>Open on all four sides, so the aperture inside is visible from wherever a player happens to
     * be standing and so a funnel can be fitted to any face.
     */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1.0D, 0.0D, 1.0D, 15.0D, 4.0D, 15.0D),
            Block.box(1.0D, 12.0D, 1.0D, 15.0D, 16.0D, 15.0D),
            Block.box(1.0D, 4.0D, 1.0D, 3.0D, 12.0D, 3.0D),
            Block.box(13.0D, 4.0D, 1.0D, 15.0D, 12.0D, 3.0D),
            Block.box(1.0D, 4.0D, 13.0D, 3.0D, 12.0D, 15.0D),
            Block.box(13.0D, 4.0D, 13.0D, 15.0D, 12.0D, 15.0D));

    public RiftChuteBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * Registers the chute the moment it is placed.
     *
     * <p>Before it is in the registry it is not a thing another chute can be bound to, and a player
     * who places two chutes and immediately opens one would find the other missing from the list.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof RiftChuteBlockEntity chute) {
            chute.ensureRegistered(serverLevel, placer instanceof Player player ? player : null);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            AWClientHooks.requestChutePanel(pos);
        } else if (level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof RiftChuteBlockEntity chute
                && player instanceof ServerPlayer serverPlayer) {
            // A chute placed before this version, or by a command block, may have no identity yet.
            chute.ensureRegistered(serverLevel, serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Drops whatever had arrived and not been collected, and lets the block entity know it has
     * actually been destroyed.
     *
     * <p>A chute holds nothing on the way out, but an arrival waiting in its tray for a funnel is
     * real cargo, and breaking the block must not void it.
     *
     * <p>{@code IBE.onRemove} is <b>static</b>, so it is not inherited - it has to be called by hand.
     * Create's own {@code KineticBlock} calls it, which is why the Rift Gate deregisters itself
     * correctly; this block extends a plain {@code Block}, so without the call below
     * {@code SmartBlockEntity.destroy()} never runs and a broken chute stays in the registry forever,
     * haunting every other chute's binding list.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof RiftChuteBlockEntity chute
                && !chute.held().isEmpty()) {
            Block.popResource(level, pos, chute.held());
        }
        IBE.onRemove(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public Class<RiftChuteBlockEntity> getBlockEntityClass() {
        return RiftChuteBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RiftChuteBlockEntity> getBlockEntityType() {
        return AWBlockEntities.RIFT_CHUTE.get();
    }
}
