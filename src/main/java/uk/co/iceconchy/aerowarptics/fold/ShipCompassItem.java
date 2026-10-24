package uk.co.iceconchy.aerowarptics.fold;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.beacon.RiftBeaconBinding;
import uk.co.iceconchy.aerowarptics.drive.DriveAssembly;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.hold.HoldLedger;
import uk.co.iceconchy.aerowarptics.hold.Holds;
import uk.co.iceconchy.aerowarptics.network.AWNetwork;
import uk.co.iceconchy.aerowarptics.network.ClientboundWarpFeedbackPacket;
import uk.co.iceconchy.aerowarptics.registry.AWDataComponents;
import uk.co.iceconchy.aerowarptics.registry.AWSounds;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;
import uk.co.iceconchy.aerowarptics.warp.WarpValidator;

import java.util.List;

/**
 * A compass folded at a Collapse Cradle, which, bound to a ship, ever after points at it.
 *
 * <p>It comes out of the fold unbound - a cradle stands on the ground and has no ship to give it - and is bound
 * the way a Rift Beacon is: sneak and use it on the ship's Rift Drive, or any face of the drive's frame. That
 * is the moment permission is asked, through the same check a beacon's binding makes, and the binding is the
 * drive's plot position, never the ship's UUID - which shares the beacon's one limit: a cross-dimension crossing
 * rebuilds a ship at a new plot position, and the compass is left pointing at where it was. A bound compass can
 * be bound again, to another ship, the same way.
 *
 * <p>The needle is the vanilla compass's, fed a position the server keeps up to date. A ship that is loaded
 * is read where it is; one that is not is read where it was last seen, which the hold ledger remembers.
 */
public class ShipCompassItem extends Item {

    /** How often a carried compass asks where its ship is, in ticks. */
    private static final int UPDATE_INTERVAL = 20;

    public ShipCompassItem(Properties properties) {
        super(properties);
    }

    /** Sneaking, on a Rift Drive: bind to its ship. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() == null || !context.getPlayer().isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        RiftDriveBlockEntity drive = DriveAssembly.driveAt(level, context.getClickedPos());
        if (drive == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        WarpFailure permission = WarpValidator.validatePlayer(player, drive);
        Trace.log(Trace.FOLD, "{} binding a Ship's Compass to drive {} -> {}", Trace.who(player),
                Trace.pos(drive.getBlockPos()), permission.isFailure() ? permission.getSerializedName() : "bound");
        if (permission.isFailure()) {
            AWNetwork.sendTo(player, new ClientboundWarpFeedbackPacket(permission));
            return InteractionResult.CONSUME;
        }
        Airship airship = drive.airship();
        String ship = airship == null || airship.name() == null ? "" : airship.name();
        ItemStack held = context.getItemInHand();
        held.set(AWDataComponents.SHIP_COMPASS.get(), new RiftBeaconBinding(level.dimension(), drive.getBlockPos(), ship));
        held.remove(AWDataComponents.SHIP_COMPASS_TARGET.get());
        String name = ship.isBlank() ? AWLang.translate("item.ship_compass.unnamed").string() : ship;
        player.displayClientMessage(AWLang.translate("item.ship_compass.bound_now", name).component(), true);
        level.playSound(null, player.blockPosition(), AWSounds.DESTINATION_LOCK.get(), SoundSource.PLAYERS, 0.6F, 1.6F);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(AWDataComponents.SHIP_COMPASS.get());
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel server) || server.getGameTime() % UPDATE_INTERVAL != slot % UPDATE_INTERVAL) {
            return;
        }
        RiftBeaconBinding binding = stack.get(AWDataComponents.SHIP_COMPASS.get());
        if (binding == null) {
            return;
        }
        GlobalPos target = locate(server, binding);
        if (target != null && !target.equals(stack.get(AWDataComponents.SHIP_COMPASS_TARGET.get()))) {
            stack.set(AWDataComponents.SHIP_COMPASS_TARGET.get(), target);
        }
    }

    /** Where the bound ship is, or was last seen; {@code null} if it has never been noted anywhere. */
    @Nullable
    private static GlobalPos locate(ServerLevel any, RiftBeaconBinding binding) {
        ServerLevel level = any.getServer().getLevel(binding.dimension());
        if (level == null) {
            return null;
        }
        Airship ship = Airship.containing(level, binding.drivePos());
        if (ship != null && ship.isActive()) {
            Vec3 at = ship.toWorld(Vec3.atCenterOf(binding.drivePos()));
            return GlobalPos.of(ship.level().dimension(), BlockPos.containing(at));
        }
        HoldLedger.ShipRecord record = Holds.shipAt(level, binding.drivePos());
        if (record == null) {
            return null;
        }
        return GlobalPos.of(record.dimension(), BlockPos.containing((record.minX() + record.maxX()) / 2.0D,
                (record.minY() + record.maxY()) / 2.0D, (record.minZ() + record.maxZ()) / 2.0D));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        RiftBeaconBinding binding = stack.get(AWDataComponents.SHIP_COMPASS.get());
        if (binding == null) {
            tooltip.add(AWLang.translate("item.ship_compass.unbound").component().copy().withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        String name = binding.shipLabel().isBlank() ? AWLang.translate("item.ship_compass.unnamed").string() : binding.shipLabel();
        tooltip.add(AWLang.translate("item.ship_compass.bound", name).component().copy().withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
