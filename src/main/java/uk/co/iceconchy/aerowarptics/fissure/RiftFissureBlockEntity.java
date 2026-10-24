package uk.co.iceconchy.aerowarptics.fissure;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.advancement.AWCriteria;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.registry.AWItems;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.registry.AWSounds;
import uk.co.iceconchy.aerowarptics.siphon.SpatialSiphonBlockEntity;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.ArrayList;
import java.util.List;

/**
 * A Rift Fissure, and the essence still coming out of it.
 *
 * <p>The fissure is the one that does the work, not the siphon. That is on purpose and it is the only
 * arrangement that behaves properly with more than one vessel around a tear: the reservoir is the
 * thing there is a limited amount of, so whatever owns it has to be the thing handing it out, or two
 * siphons each politely taking "what is left" between them would take it twice.
 *
 * <p>Nothing here decides how much it holds. {@link FissureDrain} does, from the fissure's own
 * position, which means the answer exists before the first tick and is the same for anybody who asks.
 */
public class RiftFissureBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    /** Ticks between sweeps for vessels. A siphon is placed by hand; once a second is soon enough. */
    private static final int SCAN_INTERVAL = 20;

    /** Ticks the tear spends closing once it is spent, so the closing is something you can watch. */
    private static final int SEAL_TICKS = 40;

    /** What is left, in millibuckets. Negative until the fissure has worked out its own size. */
    private int reservoir = -1;

    /** What it held to begin with, so the tear can be drawn closing as it empties. */
    private int original;

    private int sealTicks;
    private int scanTimer;

    /** Whether anything is drawing from it right now. Synced, because the tear reacts to it. */
    private boolean drawing;

    /** Where the vessels were at the last sweep. Positions rather than block entities, so an unloaded
     * or broken siphon simply fails to resolve rather than being held on to. */
    private List<BlockPos> siphons = List.of();

    /** What the client was last told, so a slow drain does not send a packet twenty times a second. */
    private int syncedReservoir = -1;

    /**
     * The Rift Collar holding this tear, or {@code null} for a tear nobody has built round. Kept here because a
     * block of the frame knows where its fissure is and nothing else: this is how it finds its machine.
     */
    @org.jetbrains.annotations.Nullable
    private BlockPos harness;

    /** How open a harnessed tear is drawn, 0 to 1, by the well's strain rather than by what is left in it. */
    private float harnessedOpenness = 1.0F;

    /**
     * Whether a Rift Sunderer tore this one rather than the world leaving it. A conjured tear holds what it was
     * given rather than what its position rolls, hands nothing to a siphon, seals with no heart, and gutters
     * shut unless a wellhead holds it.
     */
    private boolean conjured;

    /** When an unheld conjured tear gutters, in game time; -1 while a wellhead holds it, or for a found one. */
    private long unsettledUntil = -1L;

    /** How long a conjured tear lasts unheld, so a frame that lets go of one can start its clock again. */
    private long unsettledWindow;

    /**
     * Until when, in game time, the tear is drawn for everybody and not only through Rift Goggles: a newborn tear,
     * for the end of the scene that made it. -1 for none.
     */
    private long revealedUntil = -1L;

    public RiftFissureBlockEntity(BlockPos pos, BlockState state) {
        super(AWBlockEntities.RIFT_FISSURE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    // ------------------------------------------------------------------ state

    public int reservoir() {
        return Math.max(0, reservoir);
    }

    public int original() {
        return original;
    }

    public boolean isDrawing() {
        return drawing;
    }

    public boolean isSealing() {
        return sealTicks > 0;
    }

    /** The Rift Collar holding this tear, or {@code null}. */
    @org.jetbrains.annotations.Nullable
    public BlockPos harnessedBy() {
        return harness;
    }

    public boolean isHarnessed() {
        return harness != null;
    }

    public boolean isConjured() {
        return conjured;
    }

    /** Until when the tear is drawn for everybody, in game time, or -1 when it is not. */
    public long revealedUntil() {
        return revealedUntil;
    }

    /** Draws the tear for everybody for {@code ticks}, as a Rift Sunderer's newborn one is. */
    public void reveal(long ticks) {
        revealedUntil = level == null ? -1L : level.getGameTime() + Math.max(0L, ticks);
        setChanged();
        sync();
    }

    /** When an unheld conjured tear gutters, in game time, or -1 when it will not. */
    public long unsettledUntil() {
        return unsettledUntil;
    }

    /**
     * Makes this a conjured tear: torn by a Rift Sunderer, holding {@code reservoir} mB, and guttering after
     * {@code unsettledTicks} unless a wellhead harnesses it first.
     *
     * <p>Called by {@code SundererBreach} and nothing else, in the same tick as the block is set: a fissure that
     * reaches its first tick with no reservoir rolls one from its position, and a conjured tear's is what it was
     * given. A found fissure is never made one.
     */
    public void conjure(int reservoir, long unsettledTicks) {
        this.reservoir = Math.max(1, reservoir);
        this.original = this.reservoir;
        this.conjured = true;
        this.unsettledWindow = Math.max(1L, unsettledTicks);
        this.unsettledUntil = level == null ? -1L : level.getGameTime() + unsettledWindow;
        setChanged();
        sync();
    }

    /**
     * A Rift Wellhead takes this tear. From here it hands nothing to a siphon - what comes out of it comes out
     * through the well - and the well says how wide it gapes.
     */
    public void harness(BlockPos collar) {
        harness = collar.immutable();
        harnessedOpenness = 1.0F;
        drawing = false;
        // A held tear is settled: the frame is what keeps it open now.
        if (conjured) {
            unsettledUntil = -1L;
        }
        setChanged();
        sync();
    }

    /** The well lets go: the tear is as it was found, less whatever it bled. */
    public void release() {
        if (harness == null) {
            return;
        }
        harness = null;
        // A conjured tear let go of is unsettled again, for a whole window: a frame broken to be rebuilt bigger
        // has the time it would have had when it was first torn.
        if (conjured && level != null) {
            unsettledUntil = level.getGameTime() + Math.max(1L, unsettledWindow);
        }
        setChanged();
        sync();
    }

    /** How wide a harnessed tear gapes, from the well holding it. Told to clients only when it would show. */
    public void gape(float openness) {
        float clamped = Math.max(0.0F, Math.min(1.0F, openness));
        if (Math.abs(clamped - harnessedOpenness) >= 0.05F) {
            harnessedOpenness = clamped;
            sync();
        }
    }

    /**
     * A torn well bleeds its fissure. What is lost is lost - it fills nothing - and a tear bled dry seals as one
     * drained dry does, heart and all.
     *
     * @return whether the tear is now sealing
     */
    public boolean bleed(int millibuckets) {
        if (millibuckets <= 0 || reservoir <= 0 || sealTicks > 0) {
            return sealTicks > 0;
        }
        reservoir = Math.max(0, reservoir - millibuckets);
        setChanged();
        if (reservoir <= 0) {
            beginSealing();
            return true;
        }
        if (Math.abs(syncedReservoir - reservoir) >= Math.max(1, original / 20)) {
            sync();
        }
        return false;
    }

    /**
     * A meltdown takes the tear with it. No heart: a heart is what a wound leaves when it closes, and this one
     * did not close.
     */
    public void consume() {
        if (level == null) {
            return;
        }
        Trace.log(Trace.WELLHEAD, "fissure at {} consumed by a meltdown, with {} mB of {} left: no heart",
                Trace.pos(worldPosition), reservoir(), Math.max(original, 0));
        level.removeBlock(worldPosition, false);
    }

    /** How open the tear should be drawn, 0 to 1. */
    public float openness() {
        if (isSealing()) {
            return 0.0F;
        }
        if (harness != null) {
            return harnessedOpenness;
        }
        // A fissure straight out of worldgen has not worked out its own size yet, and a client that
        // asks before the first tick would otherwise be told "empty" and draw a tear the size of a
        // pea for the one tick before the truth arrives.
        if (reservoir < 0 || original <= 0) {
            return 1.0F;
        }
        return FissureDrain.openness(reservoir, original);
    }

    // ------------------------------------------------------------------- tick

    @Override
    public void tick() {
        // A fissure is a feature of the world, not a machine, so unlike every other block entity here
        // it has no airship to resolve and therefore nothing that would otherwise tell it to stop. It
        // can still end up inside a plot - a hull assembled around a tear takes the tear with it - and
        // when that plot is released the blocks are wiped to void_air while the block entities get one
        // more tick each. Everything below touches the level: the sync announces a block change and
        // close() removes one. Both throw out of Sable if the plot has gone, and it is the server thread
        // they throw on. This is the crash that made
        // Airship.orphaned exist; it goes before super.tick() for the reason given there.
        if (Airship.orphaned(this)) {
            return;
        }
        super.tick();
        if (level == null) {
            return;
        }
        if (level.isClientSide) {
            AWClientHooks.tickFissure(this);
            return;
        }
        serverTick();
    }

    private void serverTick() {
        if (reservoir < 0) {
            reservoir = FissureDrain.reservoir(worldPosition.asLong(),
                    AWConfig.FISSURE_RESERVOIR_LEAST.get(), AWConfig.FISSURE_RESERVOIR_MOST.get());
            original = reservoir;
            setChanged();
            sync();
        }

        if (sealTicks > 0) {
            if (--sealTicks <= 0) {
                close();
            }
            return;
        }

        // A conjured tear nobody has built round gutters shut when its time is up. Sealing it is the same closing
        // a drained tear does, without the heart.
        if (conjured && harness == null && unsettledUntil >= 0L && level.getGameTime() >= unsettledUntil) {
            Trace.log(Trace.RIFTING, "conjured fissure at {} guttered, unheld", Trace.pos(worldPosition));
            beginSealing();
            return;
        }

        if (--scanTimer <= 0) {
            scanTimer = SCAN_INTERVAL;
            siphons = sweepForSiphons();
        }

        int moved = drainInto();
        if (moved > 0) {
            reservoir -= moved;
            setChanged();
            if (!drawing) {
                drawing = true;
                sync();
            } else if (Math.abs(syncedReservoir - reservoir) >= Math.max(1, original / 20)) {
                // Only when the tear would visibly have changed size. A packet a tick for the whole
                // of a drain is a lot of wire for an animation nobody could tell apart from this.
                sync();
            }
            if (reservoir <= 0) {
                beginSealing();
            }
        } else if (drawing) {
            drawing = false;
            sync();
        }
    }

    /**
     * Hands essence to every vessel in range, in turn, until the tear or the tick runs out.
     *
     * <p>Each gets its own share of the rate rather than the whole of it, so a ring of siphons empties
     * a fissure no faster than one does - the tear is the limit, not the plumbing. What several
     * vessels buy you is somewhere to put it all.
     */
    private int drainInto() {
        // A harnessed tear is the well's. A siphon left beside it would otherwise empty it in a minute. A conjured
        // tear's essence is the Sunderer's own charge held under tension, and only a frame draws on it: drained
        // into a siphon it would make tearing one and emptying it a loop that pays.
        if (siphons.isEmpty() || reservoir <= 0 || level == null || harness != null || conjured) {
            return 0;
        }
        int rate = AWConfig.FISSURE_DRAIN_RATE.get();
        int moved = 0;
        for (BlockPos pos : siphons) {
            int left = reservoir - moved;
            if (left <= 0) {
                break;
            }
            if (!(level.getBlockEntity(pos) instanceof SpatialSiphonBlockEntity siphon)) {
                continue;
            }
            int amount = FissureDrain.transfer(left, siphon.room(), rate - moved);
            if (amount > 0) {
                moved += siphon.acceptFromFissure(amount);
            }
        }
        return moved;
    }

    private List<BlockPos> sweepForSiphons() {
        if (level == null) {
            return List.of();
        }
        int radius = AWConfig.FISSURE_SIPHON_RADIUS.get();
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(worldPosition.offset(-radius, -radius, -radius),
                worldPosition.offset(radius, radius, radius))) {
            if (level.getBlockEntity(pos) instanceof SpatialSiphonBlockEntity) {
                // Immutable: the iterator hands out the same cursor every time round, and keeping it
                // would leave a list of one position repeated however many siphons were found.
                found.add(pos.immutable());
            }
        }
        return List.copyOf(found);
    }

    private void beginSealing() {
        reservoir = 0;
        sealTicks = SEAL_TICKS;
        drawing = false;
        setChanged();
        sync();
        if (level != null) {
            level.playSound(null, worldPosition, AWSounds.RIFT_OPEN.get(), SoundSource.BLOCKS,
                    0.7F, 0.6F);
        }
    }

    /**
     * The tear finishes closing, and what is left of it is a Scar Heart.
     *
     * <p>One heart, whatever the fissure held: the heart is the adventure, not the yield. Spawned as an item
     * entity rather than dropped, because {@code Block.popResource} drops nothing with {@code doTileDrops}
     * off and a fissure has no loot table - which {@code ResourceIntegrityTest} forbids it, a fissure being
     * nothing a player collects. And with an unlimited lifetime, because a tear often seals with nobody
     * watching: a siphon left beside it does the work, and the heart has to be there when somebody comes
     * back. On the mast ruin it lands on the mast head, eight blocks up. A small climb, kept.
     */
    private void close() {
        if (level == null) {
            return;
        }
        level.playSound(null, worldPosition, AWSounds.WARP_EXIT.get(), SoundSource.BLOCKS, 0.5F, 1.4F);
        if (level instanceof ServerLevel server) {
            // Told before the block goes, while there is still something at this position to have
            // been standing near.
            AWCriteria.fissureClosed(server, worldPosition, Math.max(original, 0));
            // A conjured tear leaves nothing: the Scar Heart is what a found wound leaves as it closes, and the
            // Singularity is gated on having found one.
            if (conjured) {
                Trace.log(Trace.RIFTING, "conjured fissure at {} sealed: no heart", Trace.pos(worldPosition));
            } else {
                ItemEntity heart = new ItemEntity(server, worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                        worldPosition.getZ() + 0.5D, new ItemStack(AWItems.SCAR_HEART.get()));
                heart.setDeltaMovement(0.0D, 0.05D, 0.0D);
                heart.setUnlimitedLifetime();
                server.addFreshEntity(heart);
                Trace.log(Trace.RIFTING, "fissure at {} sealed, having held {} mB: a scar heart left behind",
                        Trace.pos(worldPosition), Math.max(original, 0));
            }
        }
        level.removeBlock(worldPosition, false);
    }

    private void sync() {
        syncedReservoir = reservoir;
        if (level != null && !level.isClientSide) {
            sendData();
        }
    }

    // -------------------------------------------------------------------- nbt

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Reservoir", reservoir);
        tag.putInt("Original", original);
        tag.putInt("SealTicks", sealTicks);
        tag.putBoolean("Drawing", drawing);
        if (harness != null) {
            tag.putLong("Harness", harness.asLong());
            tag.putFloat("Gape", harnessedOpenness);
        }
        if (revealedUntil >= 0L) {
            tag.putLong("Revealed", revealedUntil);
        }
        if (conjured) {
            tag.putBoolean("Conjured", true);
            tag.putLong("Unsettled", unsettledUntil);
            tag.putLong("UnsettledWindow", unsettledWindow);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        // A fissure written by worldgen carries none of this, and a missing tag reads as zero - which
        // for the reservoir would mean "spent" rather than "not yet decided". Hence the sentinel.
        reservoir = tag.contains("Reservoir") ? tag.getInt("Reservoir") : -1;
        original = tag.getInt("Original");
        sealTicks = tag.getInt("SealTicks");
        drawing = tag.getBoolean("Drawing");
        harness = tag.contains("Harness") ? BlockPos.of(tag.getLong("Harness")) : null;
        harnessedOpenness = tag.contains("Gape") ? tag.getFloat("Gape") : 1.0F;
        conjured = tag.getBoolean("Conjured");
        unsettledUntil = conjured && tag.contains("Unsettled") ? tag.getLong("Unsettled") : -1L;
        unsettledWindow = tag.getLong("UnsettledWindow");
        revealedUntil = tag.contains("Revealed") ? tag.getLong("Revealed") : -1L;
    }

    // --------------------------------------------------------------- goggles

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        // A fissure is invisible to anything but a rift-infused lens, and that has to include what it
        // is willing to say about itself. Create offers this tooltip to anyone wearing any registered
        // goggles, so an engineer's plain pair reached it - and got the full readout for a tear it
        // could not see, which gives away both that one is there and exactly how much it is holding.
        // The tear, its sparks and its hit box already ask this question; this was the gap.
        // A harnessed tear says nothing for itself: the well round it does the talking, on every block of its frame.
        if (harness != null || !AWClientHooks.seesFissures()) {
            return false;
        }
        AWLang.translate("gui.goggles.rift_fissure").forGoggles(tooltip);
        AWLang.translate("gui.rift_fissure.held", AWLang.count(reservoir()), AWLang.count(Math.max(reservoir(), original)))
                .style(ChatFormatting.LIGHT_PURPLE).forGoggles(tooltip, 1);
        if (conjured) {
            long left = level == null || unsettledUntil < 0L ? 0L : Math.max(0L, unsettledUntil - level.getGameTime());
            long seconds = left / 20L;
            AWLang.translate("gui.rift_fissure.conjured", String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L))
                    .style(ChatFormatting.RED).forGoggles(tooltip, 1);
            AWLang.translate("gui.rift_fissure.conjured_hint").style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
            return true;
        }
        AWLang.translate(drawing ? "gui.rift_fissure.draining" : "gui.rift_fissure.hint")
                .style(drawing ? ChatFormatting.AQUA : ChatFormatting.GRAY).forGoggles(tooltip, 1);
        return true;
    }
}
