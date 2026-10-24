package uk.co.iceconchy.aerowarptics.detector;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The Spatial Wave Detector: a tripwire for folded space.
 *
 * <p>Anything coming out of a rift near it - an airship finishing a warp, or a traveller or vehicle
 * stepping out of a Rift Gate - sends a pulse of redstone out of every face, graded by how close the
 * arrival was (see {@link WaveReach#strength}). It hears arrivals only, never departures: a ship
 * leaving is something its own crew already knows about, and a door that rang for both would ring
 * twice for every visitor.
 *
 * <p>How far it listens is set on the block itself, with Create's value box on any side, because two
 * detectors a few blocks apart guarding a landing pad and a whole valley are both reasonable things to
 * build. The server caps it ({@code detectorMaxRange}) and the cap is applied here, on the server, so
 * the value box a player sees is always the range the detector is actually using.
 *
 * <p>Nothing scans for arrivals. The drive and the gate announce each one through {@link #announce},
 * which walks only the detectors that are loaded in that level - so a detector costs nothing at all
 * between arrivals, and there is no reason to limit how many a server may have.
 */
public class SpatialWaveDetectorBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    /**
     * The furthest the value box will offer, in blocks.
     *
     * <p>A fixed number rather than the server's cap, because the value box is drawn by the client and
     * the client must not read server config. A setting beyond the server's cap is pulled back to it as
     * it arrives - see the behaviour built in {@link #addBehaviours}.
     */
    public static final int MAX_RANGE = 256;

    /** A fresh detector listens across a landing pad and its approaches, not a whole valley. */
    public static final int DEFAULT_RANGE = 32;

    /**
     * Every detector loaded on the server, by level.
     *
     * <p>Weakly keyed so a singleplayer world that is closed does not hold its levels in memory for the
     * rest of the session. Only ever touched on the server thread: detectors join in
     * {@link #initialize} and leave in {@link #invalidate}, which Create calls both when the block is
     * broken and when its chunk unloads.
     */
    private static final Map<Level, Set<SpatialWaveDetectorBlockEntity>> LOADED = new WeakHashMap<>();

    private ScrollValueBehaviour range;

    /** Ticks left of the current pulse. Zero while quiet. */
    private int pulseTicks;

    /** How far away the last arrival was, in blocks, or negative if nothing has arrived yet. */
    private double lastDistance = -1.0D;

    public SpatialWaveDetectorBlockEntity(BlockPos pos, BlockState state) {
        super(AWBlockEntities.SPATIAL_WAVE_DETECTOR.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        range = new ScrollValueBehaviour(AWLang.component("gui.spatial_wave_detector.range"), this,
                new CenteredSideValueBoxTransform((state, side) -> side.getAxis().isHorizontal())) {
            @Override
            public void setValue(int value) {
                // Clamped on the server only, where the cap can be read; the client is told the
                // result through the ordinary block entity sync, so its box shows what is in force.
                if (level != null && !level.isClientSide) {
                    value = Math.min(value, AWConfig.DETECTOR_MAX_RANGE.get());
                }
                super.setValue(value);
            }
        };
        range.between(1, MAX_RANGE);
        range.value = DEFAULT_RANGE;
        behaviours.add(range);
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level instanceof ServerLevel) {
            LOADED.computeIfAbsent(level, key -> new HashSet<>()).add(this);
            // A cap lowered in the config since this detector was set takes effect as it loads, not the
            // next time somebody happens to touch its value box.
            range.setValue(range.getValue());
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (level != null) {
            Set<SpatialWaveDetectorBlockEntity> detectors = LOADED.get(level);
            if (detectors != null) {
                detectors.remove(this);
            }
        }
    }

    /** The range in force, in blocks. */
    public int range() {
        return range == null ? DEFAULT_RANGE : range.getValue();
    }

    public boolean isPulsing() {
        return getBlockState().getValue(SpatialWaveDetectorBlock.POWER) > 0;
    }

    @Override
    public void tick() {
        super.tick();
        // Only a real server level. A Ponder scene's level ticks block entities as well and is not
        // client-side either, and there nothing ever starts a pulse - the scene lights the block by
        // setting its state - so the quiet-with-no-countdown branch below would put it straight out.
        if (!(level instanceof ServerLevel)) {
            return;
        }
        if (pulseTicks > 0) {
            pulseTicks--;
            if (pulseTicks == 0) {
                setPower(0);
            }
        } else if (isPulsing()) {
            // Loaded mid-pulse with the countdown lost, or left lit by a state copied in from
            // somewhere; either way there is no pulse running, so there should be no signal.
            setPower(0);
        }
    }

    /**
     * Tells every loaded detector in a level that something has just come out of a rift.
     *
     * <p>Detectors aboard airships are measured from where they are in the world now, not from their
     * plot coordinates - including those aboard the very ship arriving, which hear it at full strength.
     *
     * @param arrival world-space box of whatever arrived: a hull's bounds, or a traveller's
     */
    public static void announce(Level level, AABB arrival) {
        Set<SpatialWaveDetectorBlockEntity> detectors = LOADED.get(level);
        if (detectors == null || detectors.isEmpty()) {
            Trace.log(Trace.DETECTOR, "arrival at {} in {}: no detectors loaded there", Trace.vec(arrival.getCenter()),
                    level.dimension().location());
            return;
        }
        int heard = 0;
        // Copied, because a pulse rewrites a block state, and anything that reacts to that - a
        // contraption, a neighbour breaking - may take a detector out of the set mid-walk.
        for (SpatialWaveDetectorBlockEntity detector : new ArrayList<>(detectors)) {
            if (detector.isRemoved() || Airship.orphaned(detector)) {
                continue;
            }
            Vec3 at = detector.worldCentre();
            double distance = WaveReach.distanceToBox(at.x, at.y, at.z,
                    arrival.minX, arrival.minY, arrival.minZ, arrival.maxX, arrival.maxY, arrival.maxZ);
            int strength = WaveReach.strength(distance, detector.range());
            if (strength > 0) {
                heard++;
                detector.sense(strength, distance);
            }
        }
        Trace.log(Trace.DETECTOR, "arrival at {} in {}: heard by {} of {} loaded detector(s)",
                Trace.vec(arrival.getCenter()), level.dimension().location(), heard, detectors.size());
    }

    /** Where this detector is in the world, whether it is standing on the ground or on a deck. */
    private Vec3 worldCentre() {
        Vec3 centre = Vec3.atCenterOf(worldPosition);
        Airship airship = Airship.containing(this);
        return airship == null ? centre : airship.toWorld(centre);
    }

    /**
     * Starts, or restarts, a pulse.
     *
     * <p>Two arrivals close together - a gate letting a whole party through - make one longer pulse
     * rather than a flicker, and it holds the strongest reading of the lot, so the nearest of them is
     * what the wiring sees.
     */
    private void sense(int strength, double distance) {
        int current = pulseTicks > 0 ? getBlockState().getValue(SpatialWaveDetectorBlock.POWER) : 0;
        pulseTicks = Math.max(1, AWConfig.DETECTOR_PULSE_TICKS.get());
        lastDistance = distance;
        setPower(Math.max(current, strength));
        if (current == 0 && level != null) {
            level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
                    0.8F, 0.6F + strength * 0.06F);
        }
        sendData();
    }

    private void setPower(int power) {
        if (level == null || Airship.orphaned(this)) {
            return;
        }
        BlockState state = level.getBlockState(worldPosition);
        if (state.getBlock() instanceof SpatialWaveDetectorBlock
                && state.getValue(SpatialWaveDetectorBlock.POWER) != power) {
            level.setBlock(worldPosition, state.setValue(SpatialWaveDetectorBlock.POWER, power), Block.UPDATE_ALL);
        }
    }

    // -------------------------------------------------------------------- nbt

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("PulseTicks", pulseTicks);
        tag.putDouble("LastDistance", lastDistance);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        pulseTicks = tag.getInt("PulseTicks");
        lastDistance = tag.contains("LastDistance") ? tag.getDouble("LastDistance") : -1.0D;
    }

    // ---------------------------------------------------------------- goggles

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        AWLang.translate("gui.goggles.spatial_wave_detector").forGoggles(tooltip);
        AWLang.translate("gui.spatial_wave_detector.listening", AWLang.distance(range()))
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
        if (isPulsing() && lastDistance >= 0.0D) {
            AWLang.translate("gui.spatial_wave_detector.sensing", AWLang.distance(lastDistance))
                    .style(ChatFormatting.AQUA)
                    .forGoggles(tooltip, 1);
        } else if (lastDistance >= 0.0D) {
            AWLang.translate("gui.spatial_wave_detector.last", AWLang.distance(lastDistance))
                    .style(ChatFormatting.GRAY)
                    .forGoggles(tooltip, 1);
        } else {
            AWLang.translate("gui.spatial_wave_detector.quiet")
                    .style(ChatFormatting.DARK_GRAY)
                    .forGoggles(tooltip, 1);
        }
        return true;
    }
}
