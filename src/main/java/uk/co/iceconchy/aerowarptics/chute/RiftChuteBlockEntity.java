package uk.co.iceconchy.aerowarptics.chute;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.registry.AWFluids;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.List;
import java.util.UUID;

/**
 * A Rift Chute: a hole in space with an inventory face, wired to exactly one other chute.
 *
 * <h2>One slot, and it is an arrival tray</h2>
 * A chute stores nothing on the way <em>out</em>: an item pushed into one is carried across in the
 * same call or refused outright, so an unpaired chute takes nothing and the funnel feeding it sits
 * visibly full with the belt backing up behind it.
 *
 * <p>What it does have is a single slot on the way <em>in</em>. That is not a buffer in the sense of
 * somewhere things queue - it is where an arrival lands so that a Create funnel on the far chute has
 * something to take. A funnel <em>pulls</em>; it does not receive. With nothing to pull from, a pair
 * wired funnel-and-belt at the far end delivers nothing at all and gives no clue why.
 *
 * <p>The tray also drains into any inventory the chute is touching, so a chest against the far chute
 * works with no funnel fitted. Between the two, both of Create's idioms are covered by one
 * mechanism: neighbours first, tray as the fallback.
 *
 * <p>It holds exactly one stack. A full tray refuses the next arrival, backing the sending end up
 * rather than letting items pile into a hidden store a player would have to break the block to
 * find.
 *
 * <h2>Why this is not a dimensional operation</h2>
 * A Sable sub-level's blocks live in reserved chunks of the same {@code ServerLevel}, so a chute
 * bolted to an airship and a chute on the ground are both ordinary block entities in one level. The
 * transfer is an inventory move; the rift is presentation over it.
 *
 * <h2>How it is fed</h2>
 * As an inventory, on every side. Fit a Create funnel to any face and it fills the chute exactly as
 * it would fill a chest. The far end delivers into whatever inventory it is touching, so a working
 * pair is funnel &rarr; chute &rarr; chute &rarr; chest.
 *
 * <h2>What it costs</h2>
 * Rift Essence, per item, charged to whichever end sent it, and only when an item actually crosses.
 * An idle chute spends nothing, and a chute that only receives needs no supply at all.
 */
public class RiftChuteBlockEntity extends SmartBlockEntity
        implements GeoBlockEntity, IHaveGoggleInformation {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.rift_chute.idle");

    /** How much essence a chute holds. Small: this is a fitting, not a reservoir. */
    public static final int CAPACITY = 2 * AWFluids.BUCKET;

    /** Ticks between refreshes of the link state. The rift is not re-evaluated every tick. */
    private static final int LINK_INTERVAL = 10;

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    private UUID chuteId;
    private int linkTimer;

    /**
     * The partner, resolved at most once a tick.
     *
     * <p>An insert used to re-resolve it from the registry every single time, and Create's funnel
     * asks several times per item - so a belt running at speed was paying for a saved-data lookup, a
     * map lookup and a block-entity fetch per query, on the server thread. Caching it for the
     * duration of one tick makes a chute cost about what a hopper does.
     */
    @Nullable
    private RiftChuteBlockEntity cachedPartner;
    private long cachedPartnerTick = Long.MIN_VALUE;

    /** Whether this chute has re-asserted its registration since loading. */
    private boolean registered;

    /** What the last decision was, so the panel and the goggles can say why nothing is moving. */
    private ChuteTransfer.Reason lastReason = ChuteTransfer.Reason.UNBOUND;

    /**
     * Whether this chute is showing a rift.
     *
     * <p>This is the state of the <em>pair</em>, not of this end. A live link shows an aperture at
     * both ends even when only one of them holds essence, because what a rift means here is "these
     * two are connected and something could cross". A dark chute opposite a lit one would read as a
     * broken pairing rather than as an unfuelled but perfectly good receiver.
     */
    private boolean riftOpen;

    private final FluidTank tank = new FluidTank(CAPACITY,
            stack -> stack.getFluid() == AWFluids.RIFT_ESSENCE.get()) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide) {
                sendData();
            }
        }
    };

    /**
     * Where an arrival lands, waiting for a funnel to take it or for the tick to push it onward.
     *
     * <p>One slot on purpose. It is a landing pad, not a store.
     */
    private final ItemStackHandler tray = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) {
                sendData();
            }
        }
    };

    /**
     * The chute's inventory face.
     *
     * <p>Asymmetric, deliberately. Inserting sends an item across the rift and never touches the
     * tray; extracting takes whatever has <em>arrived</em> in it. So a funnel feeding a chute pushes
     * items through the link and a funnel on the far chute pulls them out of it, which is how anyone
     * would expect to wire the two ends.
     */
    private final IItemHandler mouth = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @NotNull
        @Override
        public ItemStack getStackInSlot(int slot) {
            // What has arrived, so a funnel can see there is something worth pulling.
            return tray.getStackInSlot(0);
        }

        @NotNull
        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return offer(stack, simulate);
        }

        @NotNull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            // Arrivals only. A funnel on the receiving chute pulls from here; nothing can reach back
            // through the rift and take something off the sending end.
            return tray.extractItem(0, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            // Deliberately cheap. This is asked constantly, and it is only ever a hint - the real
            // decision is made in insertItem, which refuses properly if anything has changed since.
            // Running the whole journey in simulation here is what made a chute slower than the belt
            // feeding it.
            return couldSend();
        }
    };

    public RiftChuteBlockEntity(BlockPos pos, BlockState state) {
        super(AWBlockEntities.RIFT_CHUTE.get(), pos, state);
    }

    /**
     * Lets a belt hand items straight to this chute, with no funnel in between.
     *
     * <p>This is a throughput decision, not a convenience one. Create's funnels run on an eight-tick
     * extraction timer, so a pair wired belt-funnel-chute-chute-funnel-belt pays that toll twice and
     * tops out at about 2.2 items a second however fast the belts are turning. Talking to the belt
     * directly - through the same public behaviour Create's own funnel is built on - removes one of
     * those, and {@link #pushToNeighbours} removes the other at the far end.
     *
     * <p>The inventory face stays as well, so a funnel, hopper or chest still works for anyone who
     * wants one. This is an additional way in, not a replacement.
     */
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new DirectBeltInputBehaviour(this)
                .setInsertionHandler((transported, side, simulate) -> offer(transported.stack, simulate))
                .onlyInsertWhen(side -> couldSend())
                .allowingBeltFunnels());
    }

    public IItemHandler mouth() {
        return mouth;
    }

    /** Whatever has arrived and not been collected, so breaking the block can drop it. */
    public ItemStack held() {
        return tray.getStackInSlot(0);
    }

    // -------------------------------------------------------------- transfer

    /**
     * Takes an item, carries it across and delivers it - or refuses the lot.
     *
     * <p>The order matters and is the one thing this block must never get wrong. The far side is
     * asked in simulation first; only what it actually accepted is delivered for real, and only that
     * much essence is charged. Committing first and asking afterwards is how cargo goes missing, and
     * with no buffer anywhere there would be nothing left to recover it from.
     *
     * @return whatever could not be taken, which the inserter keeps
     */
    private ItemStack offer(ItemStack incoming, boolean simulate) {
        if (incoming.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return incoming;
        }
        RiftChuteRegistry registry = RiftChuteRegistry.get(serverLevel);
        RiftChute self = registry.byId(chuteId);
        if (self == null || !self.enabled()) {
            lastReason = ChuteTransfer.Reason.UNBOUND;
            return incoming;
        }

        RiftChuteBlockEntity partner = partner(serverLevel, self);
        int cost = AWConfig.CHUTE_COST_PER_ITEM.get();
        // Not how much room the far chute has - it has none - but how much the inventories it is
        // touching will take.
        int room = partner == null ? 0 : partner.deliverable(incoming);

        ChuteTransfer.Plan plan = ChuteTransfer.plan(
                incoming.getCount(),
                room,
                tank.getFluidAmount(),
                cost,
                AWConfig.CHUTE_BATCH_SIZE.get(),
                self.isBound(),
                partner != null,
                isWarping() || partner != null && partner.isWarping());

        lastReason = plan.reason();
        if (!plan.moves()) {
            return incoming;
        }
        if (simulate) {
            return incoming.copyWithCount(incoming.getCount() - plan.count());
        }

        ItemStack rejected = partner.deliver(incoming.copyWithCount(plan.count()), false);
        int crossed = plan.count() - rejected.getCount();
        if (crossed <= 0) {
            lastReason = ChuteTransfer.Reason.PARTNER_FULL;
            return incoming;
        }
        if (cost > 0) {
            tank.drain(crossed * cost, IFluidHandler.FluidAction.EXECUTE);
        }
        return incoming.copyWithCount(incoming.getCount() - crossed);
    }

    /**
     * A quick, honest-enough answer to "is this chute worth offering anything to".
     *
     * <p>Paired, loaded and funded. It does not ask whether the far tray has room for this
     * particular stack, because that is the expensive half and {@code insertItem} checks it anyway.
     */
    private boolean couldSend() {
        if (!(level instanceof ServerLevel serverLevel) || chuteId == null) {
            return false;
        }
        RiftChute self = RiftChuteRegistry.get(serverLevel).byId(chuteId);
        if (self == null || !self.enabled() || !self.isBound()) {
            return false;
        }
        return partner(serverLevel, self) != null
                && ChuteTransfer.riftOpen(tank.getFluidAmount(), AWConfig.CHUTE_COST_PER_ITEM.get());
    }

    /** How many of this stack the far end would accept. */
    private int deliverable(ItemStack stack) {
        return stack.getCount() - deliver(stack, true).getCount();
    }

    /**
     * Lands a stack at this end, in the tray.
     *
     * <p>The tray and nothing else, because this runs on the hot path - once per item arriving, and
     * again for every simulation Create's funnel runs before committing. Scanning six neighbours and
     * every slot of whatever inventories they hold, per query, is what made a chute unable to keep up
     * with a belt.
     *
     * <p>Getting the item onward is {@link #tick()}'s job instead: it drains the tray into any
     * adjacent inventory every tick. So a chest against a chute still works with nothing else fitted,
     * at a cost of at most one tick of latency, and the per-item cost here is a single insert into a
     * one-slot handler.
     */
    private ItemStack deliver(ItemStack stack, boolean simulate) {
        if (level == null || stack.isEmpty()) {
            return stack;
        }
        return tray.insertItem(0, stack, simulate);
    }

    /**
     * Offers a stack to everything around this chute, skipping other chutes.
     *
     * <p>Belts first, and that ordering is the whole point. Handing an item straight to a belt
     * through {@link DirectBeltInputBehaviour} is what Create's own funnel does internally, and it
     * costs nothing - whereas letting a funnel pull the same item out of the tray costs Create's
     * eight-tick extraction timer, which is what capped a chute at 2.2 items a second. Inventories
     * are tried afterwards so a chest or a barrel still works with nothing else fitted.
     *
     * <p>Two chutes touching would pass a stack back and forth and charge essence for every lap, so
     * a chute is never a valid destination.
     */
    private ItemStack pushToNeighbours(ItemStack stack, boolean simulate) {
        if (level == null || stack.isEmpty()) {
            return stack;
        }
        ItemStack remainder = stack.copy();
        for (Direction side : Direction.values()) {
            if (remainder.isEmpty()) {
                break;
            }
            BlockPos neighbour = worldPosition.relative(side);
            if (level.getBlockEntity(neighbour) instanceof RiftChuteBlockEntity) {
                continue;
            }

            DirectBeltInputBehaviour belt =
                    BlockEntityBehaviour.get(level, neighbour, DirectBeltInputBehaviour.TYPE);
            if (belt != null && belt.canInsertFromSide(side.getOpposite())) {
                TransportedItemStack parcel = new TransportedItemStack(remainder);
                parcel.insertedFrom = side.getOpposite();
                ItemStack left = belt.handleInsertion(parcel, side.getOpposite(), simulate);
                if (left.getCount() != remainder.getCount()) {
                    remainder = left;
                    continue;
                }
            }

            IItemHandler target =
                    level.getCapability(Capabilities.ItemHandler.BLOCK, neighbour, side.getOpposite());
            if (target == null) {
                continue;
            }
            for (int slot = 0; slot < target.getSlots() && !remainder.isEmpty(); slot++) {
                remainder = target.insertItem(slot, remainder, simulate);
            }
        }
        return remainder;
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }
        if (level.isClientSide) {
            // The aperture is renewed from here rather than from a renderer, so a chute that opened
            // before the player arrived is still showing one when they walk up to it - the same rule
            // a Rift Gate follows.
            uk.co.iceconchy.aerowarptics.client.AWClientHooks.tickChuteAperture(this);
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // A chute re-asserts itself on load. Its registry entry is the only thing that makes it
        // bindable, and one lost to a crash or a world edit would otherwise never come back.
        if (!registered) {
            registered = true;
            ensureRegistered(serverLevel, null);
        }
        // An arrival that landed in the tray because nothing was touching the chute at the time
        // still leaves as soon as something is. Without this, putting a chest against a working pair
        // would leave whatever was already sitting there stranded.
        ItemStack waiting = tray.getStackInSlot(0);
        if (!waiting.isEmpty()) {
            ItemStack left = pushToNeighbours(waiting, false);
            if (left.getCount() != waiting.getCount()) {
                tray.setStackInSlot(0, left);
            }
        }
        if (++linkTimer < LINK_INTERVAL) {
            return;
        }
        linkTimer = 0;
        updateLink(serverLevel);
    }

    /**
     * Works out whether this pair has a live link, and shows a rift at both ends if it does.
     *
     * <p>A link is live when the two are paired and <em>either</em> end could pay for an item. That
     * is what makes the aperture mean "connected" rather than "fuelled": a receiving chute with an
     * empty tank is a perfectly good half of a working pair, and drawing it dark would send a player
     * off to pipe essence somewhere that never needed any.
     */
    private void updateLink(ServerLevel serverLevel) {
        RiftChute self = RiftChuteRegistry.get(serverLevel).byId(chuteId);
        RiftChuteBlockEntity partner = self == null ? null : partner(serverLevel, self);
        int cost = AWConfig.CHUTE_COST_PER_ITEM.get();

        boolean live = partner != null
                && (ChuteTransfer.riftOpen(tank.getFluidAmount(), cost)
                || ChuteTransfer.riftOpen(partner.tank.getFluidAmount(), cost));

        if (self == null || !self.isBound()) {
            lastReason = ChuteTransfer.Reason.UNBOUND;
        } else if (partner == null) {
            lastReason = ChuteTransfer.Reason.PARTNER_ABSENT;
        } else if (!live) {
            lastReason = ChuteTransfer.Reason.NO_ESSENCE;
        } else if (lastReason == ChuteTransfer.Reason.UNBOUND
                || lastReason == ChuteTransfer.Reason.PARTNER_ABSENT
                || lastReason == ChuteTransfer.Reason.NO_ESSENCE) {
            lastReason = ChuteTransfer.Reason.READY;
        }

        if (live != riftOpen) {
            riftOpen = live;
            setChanged();
            sendData();
        }
    }

    /**
     * Finds the partner's block entity, or {@code null} when it is not reachable.
     *
     * <p>Deliberately no chunk ticket. A chute that held its partner's chunks open would be a chunk
     * loader built from two blocks and a bucket, which is a far larger decision than a logistics
     * fitting should be making.
     */
    /** The partner, from this tick's cache where possible. */
    @Nullable
    private RiftChuteBlockEntity partner(ServerLevel serverLevel, RiftChute self) {
        long now = serverLevel.getGameTime();
        if (cachedPartnerTick == now) {
            return cachedPartner != null && !cachedPartner.isRemoved() ? cachedPartner : null;
        }
        cachedPartnerTick = now;
        cachedPartner = resolvePartner(serverLevel, self);
        return cachedPartner;
    }

    @Nullable
    private RiftChuteBlockEntity resolvePartner(ServerLevel serverLevel, RiftChute self) {
        if (!self.isBound()) {
            return null;
        }
        RiftChute record = RiftChuteRegistry.get(serverLevel).byId(self.partner());
        if (record == null || !record.enabled() || !record.dimension().equals(serverLevel.dimension())) {
            return null;
        }
        if (!serverLevel.isLoaded(record.pos())) {
            return null;
        }
        return serverLevel.getBlockEntity(record.pos()) instanceof RiftChuteBlockEntity chute
                && !chute.isRemoved() ? chute : null;
    }

    private boolean isWarping() {
        if (level == null || level.isClientSide) {
            return false;
        }
        Airship airship = airship();
        return airship != null && airship.warpData().isWarping();
    }

    @Nullable
    private Airship airship() {
        if (level == null) {
            return null;
        }
        return Sable.HELPER.getContaining(level, worldPosition) instanceof ServerSubLevel sub
                ? Airship.of(sub)
                : null;
    }

    /**
     * Whether this chute is bolted to a ship rather than standing on the ground.
     *
     * <p>Used by the packet handler to decide whether a plain distance check against the player
     * means anything: a chute aboard a hull has plot-grid coordinates nowhere near where its owner
     * is standing, so measuring the two against each other would refuse every legitimate click.
     */
    public boolean aboard() {
        return airship() != null;
    }

    // ---------------------------------------------------------------- access

    @Nullable
    public UUID chuteId() {
        return chuteId;
    }

    public void setChuteId(UUID id) {
        this.chuteId = id;
        setChanged();
    }

    public IFluidHandler tank() {
        return tank;
    }

    public FluidStack contents() {
        return tank.getFluid();
    }

    public boolean isRiftOpen() {
        return riftOpen;
    }

    public ChuteTransfer.Reason lastReason() {
        return lastReason;
    }

    // --------------------------------------------------------------- registry

    /**
     * Makes sure this chute is in the registry, registering it the first time it is needed.
     *
     * <p>Reuses the id it already had wherever there is one, so a chute that outlived its registry
     * entry comes back as itself rather than as a stranger - which is what keeps a partner's binding
     * pointing at something real.
     */
    public RiftChute ensureRegistered(ServerLevel serverLevel, @Nullable Player placer) {
        RiftChuteRegistry registry = RiftChuteRegistry.get(serverLevel);
        if (chuteId != null) {
            RiftChute existing = registry.byId(chuteId);
            if (existing != null) {
                return existing;
            }
        }
        UUID id = chuteId == null ? UUID.randomUUID() : chuteId;
        RiftChute chute = RiftChute.create(id, serverLevel.dimension(), worldPosition, placer);
        registry.register(chute);
        setChuteId(id);
        return chute;
    }

    // ------------------------------------------------------------- animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "chute", 5, state -> {
            state.setAnimation(IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    // --------------------------------------------------------------- goggles

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        AWLang.translate("gui.goggles.rift_chute").forGoggles(tooltip);
        AWLang.translate(riftOpen ? "gui.rift_chute.rift_open" : "gui.rift_chute.rift_collapsed")
                .style(riftOpen ? ChatFormatting.GRAY : ChatFormatting.RED).forGoggles(tooltip, 1);
        AWLang.translate("gui.rift_chute.essence", AWLang.count(tank.getFluidAmount()), AWLang.count(CAPACITY))
                .style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        return true;
    }

    // ------------------------------------------------------------------- nbt

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (chuteId != null) {
            tag.putUUID("ChuteId", chuteId);
        }
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.put("Tray", tray.serializeNBT(registries));
        tag.putBoolean("RiftOpen", riftOpen);
        tag.putString("Reason", lastReason.name());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        chuteId = tag.hasUUID("ChuteId") ? tag.getUUID("ChuteId") : null;
        tank.readFromNBT(registries, tag.getCompound("Tank"));
        if (tag.contains("Tray")) {
            tray.deserializeNBT(registries, tag.getCompound("Tray"));
        }
        riftOpen = tag.getBoolean("RiftOpen");
        lastReason = reasonByName(tag.getString("Reason"));
    }

    private static ChuteTransfer.Reason reasonByName(String name) {
        for (ChuteTransfer.Reason value : ChuteTransfer.Reason.values()) {
            if (value.name().equals(name)) {
                return value;
            }
        }
        return ChuteTransfer.Reason.UNBOUND;
    }

    // -------------------------------------------------------------- lifecycle

    /**
     * The block has actually been broken.
     *
     * <p>Only here, never in {@code remove()}. Chunk unload and block break are different events,
     * and a chute that deregistered itself every time nobody was standing near it would break every
     * binding pointing at it.
     *
     * <p>This runs only because {@code RiftChuteBlock.onRemove} calls the static
     * {@code IBE.onRemove}. Without that call it never fires at all, which is exactly how broken
     * chutes came to haunt every other chute's list.
     */
    @Override
    public void destroy() {
        if (level instanceof ServerLevel serverLevel && chuteId != null) {
            RiftChuteRegistry.get(serverLevel).remove(chuteId);
        }
        super.destroy();
    }
}
