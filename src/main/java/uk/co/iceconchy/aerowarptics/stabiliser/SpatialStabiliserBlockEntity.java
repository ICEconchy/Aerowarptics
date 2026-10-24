package uk.co.iceconchy.aerowarptics.stabiliser;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.collapse.CollapseCradleBlockEntity;
import uk.co.iceconchy.aerowarptics.drive.DriveAssembly;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.List;
import java.util.UUID;

/**
 * The Spatial Stabiliser: a Rift Drive module that takes the edge off an airship's motion.
 *
 * <p>Left to itself, a hull does exactly what its machinery tells it to, as fast as the physics allows
 * - light the burners under a hot-air balloon and the ship leaps. With a stabiliser switched on, the
 * same ship eases upward instead: each tick, most of the change in its motion is handed back, and a
 * little of what remains is bled away. The arithmetic lives in {@link StabiliserCushion}, which is
 * where the reasons for what is and is not cushioned are written down.
 *
 * <h2>Why a module, and why beside a drive</h2>
 * It sits against a Rift Drive the way a Rift Modulator does, and is resolved the same way, by looking
 * at its six neighbours. That is more than flavour. A drive in the middle of a warp commands the hull's
 * velocity outright every tick; a stabiliser working against that would drag the ship off the course
 * the flight planned in full before the aperture opened. Being beside the drive is what lets the
 * stabiliser see it is warping and stand down.
 *
 * <h2>Why the ordinary tick, not Sable's</h2>
 * Sable offers {@code sable$physicsTick} to block entities it counts as actors, and a stabiliser would
 * seem to belong there. But actor membership depends on a block having changed since the plot was
 * built - the same trap {@link Airship#machines} exists to avoid - so a stabiliser on a ship read back
 * off disk could silently never be called. The ordinary tick always runs, sees exactly one physics
 * step between one call and the next, and adjusts velocity through the same pipeline calls a warp
 * already flies ships with.
 *
 * <h2>Its second job</h2>
 * Switched on in a Collapse Cradle's ring, it steadies a collapse - the cradle counts every switched-on
 * stabiliser within its reach, and asks nothing else of one. A cradle never stands on a ship, so the two jobs
 * never meet: with no ship to cushion, the stabiliser looks for a cradle it stands round ({@link #atACradle}),
 * and its goggles say that instead of naming a drive it was never meant to be beside.
 */
public class SpatialStabiliserBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    /** Which stabiliser has which hull this tick. Server thread only; see {@link HullClaims}. */
    private static final HullClaims CLAIMS = new HullClaims();

    private final StabiliserCushion cushion = new StabiliserCushion();

    /** Ticks between looks for a Collapse Cradle to steady, while there is no ship to cushion. */
    private static final int CRADLE_LOOK_INTERVAL = 10;

    private StabiliserStatus status = StabiliserStatus.OFF;

    /** What the last look for a cradle found - nothing, if {@code null} - and how long until the next. */
    @Nullable
    private StabiliserStatus atCradle;
    private int cradleLookIn;

    /** The hull this stabiliser last claimed, so the claim can be given back when it stops. */
    @Nullable
    private UUID claimedHull;

    public SpatialStabiliserBlockEntity(BlockPos pos, BlockState state) {
        super(AWBlockEntities.SPATIAL_STABILISER.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // Not kinetic and holds nothing: a redstone signal is its whole interface.
    }

    // ------------------------------------------------------------------ linking

    /**
     * The Rift Drive this is a module of, or {@code null} when it is not beside one.
     *
     * <p>Whichever neighbour is found first, as with the Modulator, and a formed Drive Extender counts as
     * its drive the same way. Unlike the Modulator it is not cached for the tick, because nothing but
     * this block's own tick ever asks.
     */
    @Nullable
    public RiftDriveBlockEntity linkedDrive() {
        if (level == null) {
            return null;
        }
        for (Direction direction : Direction.values()) {
            RiftDriveBlockEntity drive = DriveAssembly.driveAt(level, worldPosition.relative(direction));
            if (drive != null) {
                return drive;
            }
        }
        return null;
    }

    public StabiliserStatus status() {
        return status;
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        // Before super.tick(), not after: Create runs initialize(), lazyTick() and every
        // behaviour from there, and those touch the level too. Nothing runs on a block that is
        // no longer there - see Airship.orphaned.
        if (Airship.orphaned(this)) {
            return;
        }
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }
        StabiliserStatus next = cushionThisTick();
        if (next != StabiliserStatus.CUSHIONING) {
            stop();
        }
        if (next.isFault()) {
            next = atACradle(next);
        } else {
            // So the tick it next has no ship, it looks at once: an answer half a second late would be the very
            // line about a drive that a stabiliser beside a cradle should never show.
            cradleLookIn = 0;
        }
        if (next != status) {
            status = next;
            // Only the goggles read this, so the client is told when it changes and not otherwise.
            sendData();
        }
    }

    private StabiliserStatus cushionThisTick() {
        if (!getBlockState().getValue(SpatialStabiliserBlock.POWERED)) {
            return StabiliserStatus.OFF;
        }
        RiftDriveBlockEntity drive = linkedDrive();
        if (drive == null) {
            return StabiliserStatus.NOT_LINKED;
        }
        Airship airship = Airship.containing(this);
        if (airship == null || !airship.isActive()) {
            return StabiliserStatus.GROUNDED;
        }
        if (drive.state().isSequenceRunning()) {
            return StabiliserStatus.STANDING_DOWN;
        }
        UUID hull = airship.uuid();
        if (claimedHull != null && !claimedHull.equals(hull)) {
            // The block is on a different hull from the one it was cushioning - merged or split by
            // Simulated. Whatever it remembers about the old one means nothing here.
            stop();
        }
        if (!CLAIMS.claim(hull, level.getGameTime(), worldPosition.asLong())) {
            return StabiliserStatus.DEFERRING;
        }
        claimedHull = hull;

        StabiliserCushion.Correction correction = cushion.step(airship.velocity(), airship.angularVelocity(),
                airship.position(), settings());
        if (!correction.isNone()) {
            airship.nudgeVelocity(correction.linear(), correction.angular());
        }
        return StabiliserStatus.CUSHIONING;
    }

    /**
     * What a stabiliser with no ship to cushion is doing instead, if it stands in a Collapse Cradle's ring: being
     * switched on there is all the cradle asks of it, so it is at work, not at fault.
     *
     * <p>Looked for twice a second rather than every tick. The cradle is found among the block entities of the
     * chunks round this one, and nothing about where either stands changes faster than a player can build.
     */
    private StabiliserStatus atACradle(StabiliserStatus otherwise) {
        if (--cradleLookIn <= 0) {
            cradleLookIn = CRADLE_LOOK_INTERVAL;
            CollapseCradleBlockEntity cradle = CollapseCradleBlockEntity.steadiedFrom(level, worldPosition);
            atCradle = cradle == null ? null
                    : cradle.running() ? StabiliserStatus.STEADYING : StabiliserStatus.ATTENDING;
        }
        return atCradle == null ? otherwise : atCradle;
    }

    /** Forgets the tick being compared with and gives the hull back to any other stabiliser aboard. */
    private void stop() {
        cushion.release();
        if (claimedHull != null) {
            CLAIMS.release(claimedHull, worldPosition.asLong());
            claimedHull = null;
        }
    }

    private static StabiliserCushion.Settings settings() {
        return new StabiliserCushion.Settings(
                AWConfig.STABILISER_CUSHION.get(),
                AWConfig.STABILISER_SETTLE.get(),
                AWConfig.STABILISER_JOLT_LIMIT.get(),
                AWConfig.STABILISER_SPIN_JOLT_LIMIT.get());
    }

    /**
     * Broken, unloaded or carried off: give the hull back. {@code invalidate} rather than
     * {@code remove}, because Create only calls the latter from a block that forwards its own
     * {@code onRemove}, and this one has nothing else to forward. It touches no level, so it is safe
     * on the tick a plot is torn down under it.
     */
    @Override
    public void invalidate() {
        stop();
        super.invalidate();
    }

    // --------------------------------------------------------------- goggles

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        AWLang.translate("gui.goggles.spatial_stabiliser").forGoggles(tooltip);
        AWLang.translate(status.translationKey())
                .style(status.isWorking() ? ChatFormatting.AQUA
                        : status.isFault() ? ChatFormatting.RED : ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
        if (status == StabiliserStatus.OFF) {
            AWLang.translate("gui.spatial_stabiliser.hint").style(ChatFormatting.DARK_GRAY)
                    .forGoggles(tooltip, 1);
        }
        return true;
    }

    // ------------------------------------------------------------------- nbt

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        // Only ever sent, never saved: it is recomputed on the first tick, and a status read back off
        // disk would claim to be cushioning a hull nobody has checked yet.
        if (clientPacket) {
            tag.putInt("Status", status.ordinal());
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) {
            status = StabiliserStatus.byIndex(tag.getInt("Status"));
        }
    }
}
