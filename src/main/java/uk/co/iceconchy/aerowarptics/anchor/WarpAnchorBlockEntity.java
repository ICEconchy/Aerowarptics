package uk.co.iceconchy.aerowarptics.anchor;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.warp.ArrivalHeight;

import java.util.List;
import java.util.UUID;

/**
 * A persistent warp destination.
 *
 * <p>The block entity is the anchor's presence in the world; the authoritative record lives in
 * {@link WarpAnchorRegistry}. On load the block entity re-asserts its record (creating it if the
 * registry has never seen this id), and on destruction it withdraws it. Everything a Rift Drive
 * needs to plan a warp comes from the registry, so anchors in unloaded chunks remain valid
 * destinations.
 */
public class WarpAnchorBlockEntity extends SmartBlockEntity implements GeoBlockEntity, IHaveGoggleInformation {

    private static final java.util.Map<WarpAnchorStatus, RawAnimation> ANIMATIONS =
            new java.util.EnumMap<>(WarpAnchorStatus.class);

    static {
        for (WarpAnchorStatus value : WarpAnchorStatus.values()) {
            ANIMATIONS.put(value, RawAnimation.begin().thenLoop(value.animation()));
        }
    }

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    private UUID anchorId = UUID.randomUUID();
    private String anchorName = WarpAnchor.UNNAMED;
    private WarpAnchorAccess access = WarpAnchorAccess.PUBLIC;
    private String network = "";
    private boolean enabled = true;
    /**
     * Blocks above this anchor an arriving hull's underside is set, or {@link ArrivalHeight#UNSET}
     * until the server has had a chance to say what a new anchor starts at. Not read from the config
     * here, because this constructor runs on the client too.
     */
    private int arrivalHeight = ArrivalHeight.UNSET;
    @Nullable
    private UUID owner;
    private String ownerName = "";

    /** Synced to clients purely to drive the model. */
    private WarpAnchorStatus status = WarpAnchorStatus.OFFLINE;

    /** Ticks left in the visual "an airship just arrived" state. */
    private int arrivalFlash;

    private boolean registered;

    /** The server's {@code maxArrivalHeight}, as it arrived with the last sync. Client side only. */
    private int syncedMaximumArrivalHeight = ArrivalHeight.FALLBACK_MAXIMUM;

    public WarpAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(AWBlockEntities.WARP_ANCHOR.get(), pos, state);
        setLazyTickRate(20);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // The anchor has no Create behaviours; it is a SmartBlockEntity for the sync and goggle plumbing.
    }

    // ------------------------------------------------------------------ life

    @Override
    public void initialize() {
        super.initialize();
        if (level instanceof ServerLevel serverLevel) {
            syncToRegistry(serverLevel);
        }
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!registered || isAboardAirship()) {
            if (registered || owner == null || WarpAnchorRegistry.get(serverLevel).canRegisterMore(owner)) {
                syncToRegistry(serverLevel);
            }
        }
        WarpAnchorStatus desired = computeStatus();
        if (desired != status) {
            status = desired;
            sendData();
        }
    }

    @Override
    public void tick() {
        // Before super.tick(), not after: Create runs initialize(), lazyTick() and every
        // behaviour from there, and those touch the level too. Nothing runs on a block that is
        // no longer there - see Airship.orphaned.
        if (Airship.orphaned(this)) {
            return;
        }
        super.tick();
        if (arrivalFlash > 0 && !level.isClientSide) {
            arrivalFlash--;
            if (arrivalFlash == 0) {
                status = computeStatus();
                sendData();
            }
        }
    }

    @Override
    public void remove() {
        if (level instanceof ServerLevel serverLevel && !isVirtual()) {
            WarpAnchorRegistry.get(serverLevel).remove(anchorId);
            registered = false;
        }
        super.remove();
    }

    /**
     * An anchor that has been assembled into an airship is not a fixed point in the world any more:
     * its block position is a coordinate inside Sable's plot grid, which no pilot could navigate to.
     * Such an anchor withdraws itself from the registry until it is disassembled again.
     */
    public boolean isAboardAirship() {
        return level != null && Sable.HELPER.isInPlotGrid(this);
    }

    /** Writes this anchor's current fields into the shared registry. */
    public void syncToRegistry(ServerLevel serverLevel) {
        if (arrivalHeight < 0) {
            arrivalHeight = ArrivalHeight.serverDefault();
            setChanged();
        }
        WarpAnchorRegistry registry = WarpAnchorRegistry.get(serverLevel);
        if (isAboardAirship()) {
            registry.remove(anchorId);
            registered = false;
            status = WarpAnchorStatus.ERROR;
            sendData();
            return;
        }
        WarpAnchor existing = registry.byId(anchorId);
        WarpAnchor anchor = existing == null
                ? new WarpAnchor(anchorId, anchorName, serverLevel.dimension(), worldPosition, owner, ownerName,
                access, network, enabled, arrivalHeight)
                : existing.withPosition(serverLevel.dimension(), worldPosition)
                .withName(anchorName)
                .withAccess(access)
                .withNetwork(network)
                .withEnabled(enabled)
                .withArrivalHeight(arrivalHeight);
        registry.register(anchor);
        registered = true;
        status = computeStatus();
        sendData();
    }

    private WarpAnchorStatus computeStatus() {
        if (arrivalFlash > 0) {
            return WarpAnchorStatus.WARP_ACTIVE;
        }
        if (!registered) {
            return WarpAnchorStatus.ERROR;
        }
        if (!enabled) {
            return WarpAnchorStatus.OFFLINE;
        }
        if (level instanceof ServerLevel serverLevel) {
            WarpAnchor record = WarpAnchorRegistry.get(serverLevel).byId(anchorId);
            if (record == null) {
                return WarpAnchorStatus.ERROR;
            }
        }
        return WarpAnchorStatus.ONLINE;
    }

    // --------------------------------------------------------------- mutation

    /**
     * Called by the block when a player places it, so ownership is recorded once.
     *
     * <p>A player who is already at the server's anchor cap gets an inert block rather than a silent
     * failure: it stays placed, reports a fault, and starts working the moment they free a slot.
     */
    public void onPlaced(@Nullable Player placer, @Nullable UUID preservedId) {
        if (preservedId != null) {
            anchorId = preservedId;
        }
        if (placer != null) {
            owner = placer.getUUID();
            ownerName = placer.getGameProfile().getName();
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (owner != null && !WarpAnchorRegistry.get(serverLevel).canRegisterMore(owner)) {
            registered = false;
            status = WarpAnchorStatus.ERROR;
            sendData();
            if (placer != null) {
                AWLang.translate("message.anchor_limit_reached").sendStatus(placer);
            }
            return;
        }
        syncToRegistry(serverLevel);
    }

    /**
     * Applies a player edit.
     *
     * @param newArrivalHeight clamped to what the server allows rather than refused, so a client whose
     *                         idea of the ceiling is stale still saves everything else it sent
     * @return {@code false} when the requested name collides with another anchor
     */
    public boolean applyEdit(ServerLevel serverLevel, String newName, WarpAnchorAccess newAccess,
                             String newNetwork, boolean newEnabled, int newArrivalHeight) {
        WarpAnchorRegistry registry = WarpAnchorRegistry.get(serverLevel);
        String trimmed = newName == null ? "" : newName.trim();
        if (!trimmed.isBlank() && registry.isNameTaken(trimmed, anchorId)) {
            return false;
        }
        anchorName = trimmed;
        access = newAccess.permitted() ? newAccess : WarpAnchorAccess.PUBLIC;
        network = newNetwork == null ? "" : newNetwork.trim();
        enabled = newEnabled;
        arrivalHeight = ArrivalHeight.clamp(newArrivalHeight, ArrivalHeight.maximum());
        registry.remove(anchorId);
        syncToRegistry(serverLevel);
        setChanged();
        return true;
    }

    /** Fired when an airship completes a warp to this anchor, purely for feedback. */
    public void onAirshipArrived() {
        arrivalFlash = 60;
        status = WarpAnchorStatus.WARP_ACTIVE;
        sendData();
    }

    /** Fired while a drive holds this anchor as its locked destination. */
    public void onDestinationLocked() {
        if (status != WarpAnchorStatus.WARP_ACTIVE) {
            status = WarpAnchorStatus.LOCKED;
            sendData();
        }
    }

    public void onDestinationReleased() {
        if (status == WarpAnchorStatus.LOCKED) {
            status = computeStatus();
            sendData();
        }
    }

    // ----------------------------------------------------------------- access

    public UUID anchorId() {
        return anchorId;
    }

    public String anchorName() {
        return anchorName;
    }

    public WarpAnchorAccess access() {
        return access;
    }

    public String network() {
        return network;
    }

    public boolean enabled() {
        return enabled;
    }

    /** The arrival height as last synced; resolved to the server default on a server if never set. */
    public int arrivalHeight() {
        return arrivalHeight < 0 && level != null && !level.isClientSide
                ? ArrivalHeight.serverDefault() : Math.max(0, arrivalHeight);
    }

    /**
     * The highest arrival height the configuration panel may offer.
     *
     * <p>Carried on the block entity's own sync rather than read by the screen, because the ceiling is
     * a server setting and a client does not get to read those for itself.
     */
    public int maximumArrivalHeight() {
        return syncedMaximumArrivalHeight;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public WarpAnchorStatus status() {
        return status;
    }

    public boolean mayEdit(Player player) {
        return owner == null || owner.equals(player.getUUID()) || player.hasPermissions(2);
    }

    // -------------------------------------------------------------------- nbt

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putUUID("AnchorId", anchorId);
        tag.putString("AnchorName", anchorName);
        tag.putString("Access", access.getSerializedName());
        tag.putString("Network", network);
        tag.putBoolean("Enabled", enabled);
        if (clientPacket) {
            // Resolved for the client, which cannot resolve an unset height itself: an anchor held
            // back by the per-player cap never reaches syncToRegistry, and would otherwise open its
            // panel reading zero.
            tag.putInt("ArrivalHeight", arrivalHeight());
            tag.putInt("MaxArrivalHeight", ArrivalHeight.maximum());
        } else if (arrivalHeight >= 0) {
            tag.putInt("ArrivalHeight", arrivalHeight);
        }
        tag.putString("OwnerName", ownerName);
        tag.putInt("Status", status.ordinal());
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.hasUUID("AnchorId")) {
            anchorId = tag.getUUID("AnchorId");
        }
        anchorName = tag.getString("AnchorName");
        access = WarpAnchorAccess.byName(tag.getString("Access"));
        network = tag.getString("Network");
        enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
        arrivalHeight = tag.contains("ArrivalHeight") ? tag.getInt("ArrivalHeight") : ArrivalHeight.UNSET;
        if (clientPacket && tag.contains("MaxArrivalHeight")) {
            syncedMaximumArrivalHeight = tag.getInt("MaxArrivalHeight");
        }
        ownerName = tag.getString("OwnerName");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        status = WarpAnchorStatus.byIndex(tag.getInt("Status"));
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.0D);
    }

    // ------------------------------------------------------------- tooltips

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        AWLang.translate("gui.goggles.warp_anchor").forGoggles(tooltip);
        AWLang.text(displayName()).style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
        AWLang.translate(status.translationKey()).style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        AWLang.translate(access.translationKey()).style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        if (!ownerName.isBlank()) {
            AWLang.translate("gui.warp_anchor.owner", ownerName)
                    .style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        }
        return true;
    }

    public String displayName() {
        return anchorName.isBlank() ? "Anchor-" + anchorId.toString().substring(0, 8) : anchorName;
    }

    // ------------------------------------------------------------- geckolib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "status", 6, this::statusAnimation));
    }

    private PlayState statusAnimation(AnimationState<WarpAnchorBlockEntity> state) {
        state.getController().setAnimation(ANIMATIONS.get(status));
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
