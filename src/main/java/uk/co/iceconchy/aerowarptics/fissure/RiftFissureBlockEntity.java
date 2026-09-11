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
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.advancement.AWCriteria;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.registry.AWBlocks;
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

    /** How many blocks of Warp Crystal Ore this fissure has crystallised so far, capped by config. */
    private int seeded;

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

    /** How open the tear should be drawn, 0 to 1. */
    public float openness() {
        if (isSealing()) {
            return 0.0F;
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
        // more tick each. Everything below touches the level: the sync announces a block change, the
        // ore seeding sets one, and close() removes one. All three throw out of Sable if the plot has
        // gone, and it is the server thread they throw on. This is the crash that made
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

        maybeSeedOre();

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
        if (siphons.isEmpty() || reservoir <= 0 || level == null) {
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

    /**
     * Now and then, crystallises one nearby block of natural stone into Warp Crystal Ore.
     *
     * <p>This is the world's only source of the raw material, and it grows outward from the tear
     * rather than being scattered through the ground by ordinary worldgen - so the ore is always found
     * clustered around a fissure, which is the point. Only natural stone and deepslate (and their
     * variants) are eligible, so nothing a player placed is ever turned to ore, and a per-fissure cap
     * keeps a long-loaded tear from converting a whole cavern.
     */
    private void maybeSeedOre() {
        if (level == null || !AWConfig.FISSURE_SEEDS_ORE.get() || seeded >= AWConfig.FISSURE_ORE_MAX.get()) {
            return;
        }
        if (level.random.nextInt(Math.max(1, AWConfig.FISSURE_ORE_CHANCE.get())) != 0) {
            return;
        }
        int radius = AWConfig.FISSURE_ORE_RADIUS.get();
        int dx = level.random.nextInt(radius * 2 + 1) - radius;
        int dy = level.random.nextInt(radius * 2 + 1) - radius;
        int dz = level.random.nextInt(radius * 2 + 1) - radius;
        if (dx == 0 && dy == 0 && dz == 0) {
            return;
        }
        BlockPos target = worldPosition.offset(dx, dy, dz);
        if (!level.isLoaded(target)) {
            return;
        }
        BlockState existing = level.getBlockState(target);
        if (!existing.is(BlockTags.STONE_ORE_REPLACEABLES) && !existing.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)) {
            return;
        }
        level.setBlock(target, AWBlocks.WARP_CRYSTAL_ORE.get().defaultBlockState(), 3);
        seeded++;
        setChanged();
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

    /** The tear finishes closing and there is simply nothing there any more. */
    private void close() {
        if (level == null) {
            return;
        }
        level.playSound(null, worldPosition, AWSounds.WARP_EXIT.get(), SoundSource.BLOCKS, 0.5F, 1.4F);
        if (level instanceof ServerLevel server) {
            // Told before the block goes, while there is still something at this position to have
            // been standing near.
            AWCriteria.fissureClosed(server, worldPosition, Math.max(original, 0));
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
        tag.putInt("Seeded", seeded);
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
        seeded = tag.getInt("Seeded");
    }

    // --------------------------------------------------------------- goggles

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        // A fissure is invisible to anything but a rift-infused lens, and that has to include what it
        // is willing to say about itself. Create offers this tooltip to anyone wearing any registered
        // goggles, so an engineer's plain pair reached it - and got the full readout for a tear it
        // could not see, which gives away both that one is there and exactly how much it is holding.
        // The tear, its sparks and its hit box already ask this question; this was the gap.
        if (!AWClientHooks.seesFissures()) {
            return false;
        }
        AWLang.translate("gui.goggles.rift_fissure").forGoggles(tooltip);
        AWLang.translate("gui.rift_fissure.held", AWLang.count(reservoir()), AWLang.count(Math.max(reservoir(), original)))
                .style(ChatFormatting.LIGHT_PURPLE).forGoggles(tooltip, 1);
        AWLang.translate(drawing ? "gui.rift_fissure.draining" : "gui.rift_fissure.hint")
                .style(drawing ? ChatFormatting.AQUA : ChatFormatting.GRAY).forGoggles(tooltip, 1);
        return true;
    }
}
