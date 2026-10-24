package uk.co.iceconchy.aerowarptics.cooling;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.drive.DriveAssembly;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveState;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.registry.AWSounds;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.List;

/**
 * One Cooling Vent: whether it is turning, which drive it answers to, and the steam it blows.
 *
 * <p>The vent decides nothing about a cooldown. The drive counts the vents round it at the moment a warp
 * completes and fixes its cooldown then - see {@link RiftDriveBlockEntity#coolers()} and
 * {@link DriveCooling} - for the reason a Rift Storm's share is fixed then: a cooldown that could be
 * shortened part-way through would make the console's countdown a guess. What a vent owes its drive is two
 * honest answers, {@link #isTurning()} and {@link #linkedDrive()}, and what it owes the pilot is a status
 * the goggles can read, since a vent doing nothing looks exactly like one doing its job.
 *
 * <p>A kinetic block entity because the vent draws stress like any other Create machine: its impact is
 * registered in {@code AWStress} and counted on whatever network turns it. Ice, which cools a drive without
 * a shaft, has no block entity at all - the drive reads it off the block.
 */
public class CoolingVentBlockEntity extends KineticBlockEntity {

    /**
     * Ticks between looks at what this vent is doing. Only the goggles and the steam read the answer, so it
     * need not be every tick; the two moments that matter are told at once - see {@link #onSpeedChanged}
     * and {@link #blow}.
     */
    private static final int STATUS_INTERVAL = 10;

    private VentStatus status = VentStatus.NOT_LINKED;

    /** The speed a vent has to be turned at, as the server has it. The goggles say it, and they are the client's. */
    private int syncedMinimumRpm;

    /** Client only: ticks since this vent began to blow, or minus one while it is not. See {@link VentPlume}. */
    private int plumeTicks = -1;

    /**
     * Client only: whether this vent has been seen at rest. One that first shows up already venting - a
     * player walking up to it, a chunk loading - joins the plume past its blast rather than at it.
     */
    private boolean seenAtRest;

    public CoolingVentBlockEntity(BlockPos pos, BlockState state) {
        super(AWBlockEntities.COOLING_VENT.get(), pos, state);
    }

    // ------------------------------------------------------------------ linking

    /**
     * The Rift Drive this vent cools, or {@code null} when it is not beside one.
     *
     * <p>Whichever neighbour is found first, as with the other modules, and a formed Drive Extender counts
     * as its drive the same way. A vent between two drives therefore cools one of them, and the drive
     * checks this before it counts the vent - see {@link RiftDriveBlockEntity#coolers()}.
     */
    @Nullable
    public RiftDriveBlockEntity linkedDrive() {
        if (level == null) {
            return null;
        }
        for (Direction direction : Direction.values()) {
            BlockPos beside = worldPosition.relative(direction);
            // Never load a chunk to answer: this is asked from a speed change as well as from the tick,
            // and a speed change can arrive while the chunk next door is still on its way in.
            RiftDriveBlockEntity drive = level.isLoaded(beside) ? DriveAssembly.driveAt(level, beside) : null;
            if (drive != null) {
                return drive;
            }
        }
        return null;
    }

    /**
     * Whether the vent is being turned hard enough to carry anything out. Server side: the speed it asks
     * for is the server's to say.
     *
     * <p>An overstressed network reads as stopped, because Create reports its speed as nought - which is
     * the right answer here too. A vent that cooled while its network was stalled would be free.
     */
    public boolean isTurning() {
        return Math.abs(getSpeed()) >= AWConfig.COOLING_VENT_MINIMUM_RPM.get();
    }

    public VentStatus status() {
        return status;
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        // Before super.tick(), not after: Create runs initialize(), lazyTick() and every behaviour from
        // there, and those touch the level too. Nothing runs on a block that is no longer there - see
        // Airship.orphaned.
        if (Airship.orphaned(this)) {
            return;
        }
        super.tick();
        if (level == null) {
            return;
        }
        if (level.isClientSide) {
            tickPlume();
            return;
        }
        // Staggered by position so a drive ringed with vents does not have them all ask on the same tick.
        if (Math.floorMod(level.getGameTime() + worldPosition.asLong(), STATUS_INTERVAL) == 0L) {
            refresh();
        }
    }

    /** A shaft starting or stopping is the one change a pilot is standing there waiting to see. */
    @Override
    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        if (level != null && !level.isClientSide) {
            refresh();
        }
    }

    private void refresh() {
        VentStatus next = work();
        int rpm = AWConfig.COOLING_VENT_MINIMUM_RPM.get();
        if (next != status || rpm != syncedMinimumRpm) {
            status = next;
            syncedMinimumRpm = rpm;
            sendData();
        }
    }

    private VentStatus work() {
        RiftDriveBlockEntity drive = linkedDrive();
        if (drive == null) {
            return VentStatus.NOT_LINKED;
        }
        if (!isTurning()) {
            return VentStatus.STOPPED;
        }
        if (!drive.ventCounts(this)) {
            return VentStatus.SURPLUS;
        }
        return drive.state() == RiftDriveState.COOLDOWN ? VentStatus.VENTING : VentStatus.READY;
    }

    /**
     * The drive has just finished a warp and counted this vent: blow.
     *
     * <p>Told rather than left for the next look, so the steam leaves on the tick the hull settles and
     * not up to half a second after it.
     */
    public void blow() {
        if (level == null || level.isClientSide) {
            return;
        }
        level.playSound(null, worldPosition, AWSounds.VENT_BLAST.get(), SoundSource.BLOCKS, 0.9F,
                0.9F + level.getRandom().nextFloat() * 0.2F);
        if (status != VentStatus.VENTING) {
            status = VentStatus.VENTING;
            sendData();
        }
    }

    // -------------------------------------------------------------------- steam

    private void tickPlume() {
        if (status != VentStatus.VENTING) {
            plumeTicks = -1;
            seenAtRest = true;
            return;
        }
        plumeTicks = plumeTicks < 0 ? (seenAtRest ? 0 : VentPlume.settled()) : plumeTicks + 1;
        AWClientHooks.tickCoolingVent(this);
    }

    /** Ticks since this vent began to blow, or minus one while it is not. Client side. */
    public int plumeTicks() {
        return plumeTicks;
    }

    // --------------------------------------------------------------- goggles

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        AWLang.translate("gui.goggles.cooling_vent").forGoggles(tooltip);
        AWLang.translate(status.translationKey())
                .style(status.isWorking() ? ChatFormatting.AQUA
                        : status.isFault() ? ChatFormatting.RED : ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
        if (status == VentStatus.STOPPED) {
            AWLang.translate("gui.cooling_vent.required_rpm", syncedMinimumRpm).style(ChatFormatting.DARK_GRAY)
                    .forGoggles(tooltip, 1);
        } else if (status == VentStatus.NOT_LINKED) {
            AWLang.translate("gui.cooling_vent.hint").style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        }
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        return true;
    }

    // ------------------------------------------------------------------- nbt

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        // Only ever sent, never saved: it is worked out again on the first look, and a status read back off
        // disk would have a vent blowing for a cooldown that ended while its chunk was unloaded.
        if (clientPacket) {
            tag.putInt("Status", status.ordinal());
            tag.putInt("MinRpm", syncedMinimumRpm);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) {
            status = VentStatus.byIndex(tag.getInt("Status"));
            syncedMinimumRpm = tag.getInt("MinRpm");
        }
    }
}
