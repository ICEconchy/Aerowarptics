package uk.co.iceconchy.aerowarptics.bond;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.registry.AWDataComponents;
import uk.co.iceconchy.aerowarptics.registry.AWItems;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.UUID;

/**
 * The Rift Bond block: one on each loose section of a ship, all tuned alike, and a warp takes the lot.
 *
 * <p>Tuning is done the way a builder would expect of a pair of anything: click a bond that is already
 * placed with the bonds still in hand, and those in hand are tuned to it. A bond placed untuned starts a
 * bond of its own. See {@link RiftBonds} for what a bond does and does not do.
 */
public class RiftBondBlock extends Block implements IBE<RiftBondBlockEntity> {

    public static final MapCodec<RiftBondBlock> CODEC = simpleCodec(RiftBondBlock::new);

    public RiftBondBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RiftBondBlockEntity bond) {
            UUID tuned = stack.get(AWDataComponents.BOND.get());
            bond.tune(tuned != null ? tuned : UUID.randomUUID());
        }
    }

    /** Clicked with bonds in hand: tunes them to this one, instead of placing one against it. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(AWItems.RIFT_BOND.get()) || player.isShiftKeyDown()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RiftBondBlockEntity bond) {
            stack.set(AWDataComponents.BOND.get(), tunedOrNew(bond));
            AWLang.translate("gui.rift_bond.tuned").sendStatus(player);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Clicked with an empty hand: says how many other sections it is holding. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RiftBondBlockEntity bond) {
            tunedOrNew(bond);
            if (Airship.containing(bond) == null) {
                AWLang.translate("gui.rift_bond.not_aboard").sendStatus(player);
            } else {
                int others = RiftBonds.bondedTo(bond).size();
                AWLang.translate(others == 0 ? "gui.rift_bond.alone" : "gui.rift_bond.holding", others)
                        .sendStatus(player);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** A bond placed by something that never said who placed it has no bond yet; it is given one when asked. */
    private static UUID tunedOrNew(RiftBondBlockEntity bond) {
        if (bond.bond() == null) {
            bond.tune(UUID.randomUUID());
        }
        return bond.bond();
    }

    @Override
    public Class<RiftBondBlockEntity> getBlockEntityClass() {
        return RiftBondBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RiftBondBlockEntity> getBlockEntityType() {
        return AWBlockEntities.RIFT_BOND.get();
    }
}
