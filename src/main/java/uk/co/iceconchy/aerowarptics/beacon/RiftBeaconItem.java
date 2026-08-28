package uk.co.iceconchy.aerowarptics.beacon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.advancement.AWCriteria;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.network.AWNetwork;
import uk.co.iceconchy.aerowarptics.network.ClientboundRiftBeaconPacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundWarpFeedbackPacket;
import uk.co.iceconchy.aerowarptics.registry.AWDataComponents;
import uk.co.iceconchy.aerowarptics.registry.AWSounds;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;
import uk.co.iceconchy.aerowarptics.warp.WarpValidator;

import java.util.List;

/**
 * Calls a bound airship down to wherever the holder is pointing.
 *
 * <p>Two actions on one item. <strong>Sneak</strong> and right-click a Rift Drive to <em>bind</em>,
 * and that is where every permission question is asked - the player is standing on the deck with the
 * machine in front of them, so the ordinary rules about reach and presence all apply and are enforced
 * by the ordinary code. Then <em>aim</em> and right-click to summon: the beacon casts a ray from the
 * eye out to {@code beaconAimRange} blocks and calls the ship to whatever the crosshair is on, by
 * which time the permission answer has already been given.
 *
 * <p>Binding is behind sneak because it has to be. A plain right-click on a drive never reaches an
 * item at all: the block's own interaction runs first and opens the drive's console, and a block that
 * consumes the click ends the matter. Sneaking is the vanilla way of saying "not the block, the thing
 * in my hand", and using anything else here would mean reaching into the drive's block class to make
 * it hand its clicks to an item it should know nothing about.
 *
 * <p>The split between binding and summoning is the whole of what makes this item possible without
 * weakening anything. A summon still has to satisfy the drive: rotation, charge, range, cost, and a
 * destination the hull actually fits in. What it no longer has to satisfy is standing on a ship that
 * is nowhere near you, which is the one condition the item exists to be an exception to.
 */
public class RiftBeaconItem extends Item {

    public RiftBeaconItem(Properties properties) {
        super(properties);
    }

    /**
     * Binding only. Summoning is deliberately not done from here.
     *
     * <p>This fires when the crosshair happens to be on a block within arm's reach, which is a
     * different question from where the player is aiming. Letting it summon would give the beacon
     * two ranges - four and a half blocks when something happened to be under the cursor, and the
     * aiming range otherwise - and the same click would mean two different places depending on
     * whether a fence post was in the way. So a summon is always the raycast, and this hands over to
     * it rather than reading the block it was given.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        // Everything real happens on the server. The client is told what came of it by the same
        // feedback channel every other warp command uses, so a beacon's refusals read like a drive's.
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer commander) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        ItemStack held = context.getItemInHand();
        if (player.isSecondaryUseActive()
                && level.getBlockEntity(context.getClickedPos()) instanceof RiftDriveBlockEntity drive) {
            return bind(commander, held, drive);
        }
        // A click onto a block is not gated on the item cooldown the way an air click is, so without
        // this a held button asks the drive again every tick.
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.CONSUME;
        }
        return aim(serverLevel, commander, held);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer commander)
                || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
        }
        aim(serverLevel, commander, held);
        return InteractionResultHolder.consume(held);
    }

    /**
     * Finds what the holder is looking at, and calls the ship there.
     *
     * <p>A ray from the eye rather than the block the game handed us, because a beacon is aimed. The
     * gesture worth having is standing on a hill and pointing at a clearing across the valley, and
     * an interaction range measured in arm's lengths cannot express that. The ray reaches
     * {@code beaconAimRange} blocks and stops at the first thing the crosshair would have
     * highlighted, so what the player is looking at and what the beacon picked are the same thing.
     *
     * <p>Fluids are clipped on their source blocks, so aiming at open water calls the ship to the
     * surface rather than to the sea floor under it - which is where a ray that ignored water would
     * land, and is not the place anybody was pointing at.
     */
    private InteractionResult aim(ServerLevel level, ServerPlayer player, ItemStack held) {
        double range = AWConfig.BEACON_AIM_RANGE.get();
        Vec3 eye = player.getEyePosition();
        Vec3 reach = eye.add(player.getViewVector(1.0F).scale(range));
        BlockHitResult hit = level.clip(new ClipContext(eye, reach,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player));

        if (hit.getType() == HitResult.Type.MISS) {
            // Pointed at the sky, or past everything within range. Saying so beats silence, which
            // reads as a broken item rather than as a miss.
            player.displayClientMessage(AWLang.translate("beacon.aim").component(), true);
            return InteractionResult.CONSUME;
        }
        // The open block against the face that was hit: the ground under the crosshair rather than
        // the inside of it.
        return summon(level, player, held, hit.getBlockPos().relative(hit.getDirection()));
    }

    /**
     * Ties this beacon to a drive.
     *
     * <p>Straight through {@link WarpValidator#validatePlayer}, deliberately: this is the moment the
     * authority is granted, so it is checked by exactly the code that checks it everywhere else. A
     * beacon cannot be bound to a ship you would not have been allowed to command by hand.
     */
    private InteractionResult bind(ServerPlayer player, ItemStack held, RiftDriveBlockEntity drive) {
        WarpFailure permission = WarpValidator.validatePlayer(player, drive);
        if (permission.isFailure()) {
            AWNetwork.sendTo(player, new ClientboundWarpFeedbackPacket(permission));
            return InteractionResult.CONSUME;
        }
        Airship airship = drive.airship();
        String ship = airship == null || airship.name() == null ? "" : airship.name();
        held.set(AWDataComponents.BEACON_BINDING.get(),
                new RiftBeaconBinding(player.level().dimension(), drive.getBlockPos(), ship));

        player.displayClientMessage(AWLang.translate("beacon.bound", shipName(ship)).component(), true);
        player.level().playSound(null, player.blockPosition(), AWSounds.DESTINATION_LOCK.get(),
                SoundSource.PLAYERS, 0.7F, 1.4F);
        return InteractionResult.CONSUME;
    }

    /** Calls the bound ship to a position, or says exactly why it is not coming. */
    private InteractionResult summon(ServerLevel level, ServerPlayer player, ItemStack held, BlockPos target) {
        RiftBeaconBinding binding = held.get(AWDataComponents.BEACON_BINDING.get());

        // Resolved before the rules are asked, so the rules get facts rather than lookups. A drive
        // in an unloaded plot reads the same as one that has been broken, and it should: from here
        // there is no ship to call either way.
        RiftDriveBlockEntity drive = binding == null || !binding.isIn(level) ? null
                : level.getBlockEntity(binding.drivePos()) instanceof RiftDriveBlockEntity found ? found : null;
        Airship airship = drive == null ? null : drive.airship();

        WarpFailure verdict = RiftBeaconRules.check(binding != null,
                binding != null && binding.isIn(level), drive != null,
                airship != null && airship.isActive());
        if (verdict.isFailure()) {
            AWNetwork.sendTo(player, new ClientboundWarpFeedbackPacket(verdict));
            return InteractionResult.CONSUME;
        }

        WarpFailure result = drive.summonTo(player, target,
                AWLang.translate("beacon.course", target.getX(), target.getY(), target.getZ()).string());
        if (result.isFailure()) {
            // startWarp has already told the player through the drive's own channel; the cooldown is
            // still applied so a refused summon cannot be hammered once a tick.
            player.getCooldowns().addCooldown(this, AWConfig.BEACON_COOLDOWN_TICKS.get());
            return InteractionResult.CONSUME;
        }

        player.getCooldowns().addCooldown(this, AWConfig.BEACON_COOLDOWN_TICKS.get());
        ClientboundRiftBeaconPacket.broadcast(level, target, drive);
        // The drive has agreed to everything it agrees to for any warp, so the summon has happened
        // as far as the beacon is concerned. The flight itself is the crew's advancement, not the
        // summoner's - they are, by definition, not aboard.
        AWCriteria.shipSummoned(player);
        return InteractionResult.CONSUME;
    }

    private static String shipName(String ship) {
        return ship.isEmpty() ? AWLang.translate("gui.rift_navigation.unnamed_ship").string() : ship;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        RiftBeaconBinding binding = stack.get(AWDataComponents.BEACON_BINDING.get());
        if (binding == null) {
            tooltip.add(AWLang.translate("beacon.tip.unbound").component()
                    .copy().withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(AWLang.translate("beacon.tip.bound", shipName(binding.shipLabel())).component()
                .copy().withStyle(ChatFormatting.AQUA));
        tooltip.add(AWLang.translate("beacon.tip.rebind").component()
                .copy().withStyle(ChatFormatting.DARK_GRAY));
    }
}
