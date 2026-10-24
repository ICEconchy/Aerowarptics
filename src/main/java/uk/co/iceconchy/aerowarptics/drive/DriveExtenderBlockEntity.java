package uk.co.iceconchy.aerowarptics.drive;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.List;
import java.util.Objects;

/**
 * One Drive Extender: which drive it belongs to, and the rotation it is carrying through to that drive.
 *
 * <p>A kinetic block entity because the frame carries power - see {@link DriveExtenderBlock} for why that
 * is done through Create's network rather than around it. It adds no stress and has no behaviours, so
 * all Create does with it each tick is keep it linked; everything else is the drive's business, and an
 * extender only has to be able to answer "whose are you" when a player clicks it, a module looks for its
 * drive, a shaft asks whether it may connect, or goggles are pointed at it.
 *
 * <p>The answer is checked both ways. An extender that remembers a drive only counts as that drive's
 * while the drive agrees its frame covers it, so a pointer left behind by anything that took a frame
 * apart without asking - a hull torn down, a block swapped by a command - reads as "not part of a
 * drive" rather than steering a module or a console at a machine that has let it go.
 */
public class DriveExtenderBlockEntity extends KineticBlockEntity {

    /** The step from this extender to its drive, or {@code null} while it is loose. See {@link DriveStructure#step}. */
    @Nullable
    private BlockPos driveStep;

    public DriveExtenderBlockEntity(BlockPos pos, BlockState state) {
        super(AWBlockEntities.DRIVE_EXTENDER.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
    }

    @Override
    public void tick() {
        // Before super.tick(), not after: Create runs initialize(), lazyTick() and every behaviour from
        // there, and those touch the level too - see Airship.orphaned. A frame aboard a hull is exactly
        // the kind of block that is still ticking the tick after Sable has wiped its plot.
        if (Airship.orphaned(this)) {
            return;
        }
        super.tick();
    }

    /** A relay: the load belongs to the drive, which counts it wherever the frame is turned from. */
    @Override
    public float calculateStressApplied() {
        this.lastStressApplied = 0.0F;
        return 0.0F;
    }

    /**
     * The drive this extender is part of, or {@code null} when it is loose or its drive has let it go.
     *
     * <p>Works on both sides - the pointer is synced - which is what lets the client open the drive's
     * console and draw the drive's goggle readout from an extender.
     */
    @Nullable
    public RiftDriveBlockEntity drive() {
        BlockPos at = drivePos();
        if (level == null || at == null) {
            return null;
        }
        // Never load a chunk to answer. A frame is small enough that its drive is loaded whenever a
        // player is near enough to be asking, and a module ticking at the edge of the loaded world should
        // hear "no drive", not drag the next chunk in.
        if (!level.isLoaded(at)) {
            return null;
        }
        return level.getBlockEntity(at) instanceof RiftDriveBlockEntity drive && !drive.isRemoved()
                && drive.frame().isExtended() && drive.frame().covers(worldPosition)
                ? drive
                : null;
    }

    /**
     * Where this extender's pointer leads, whether or not the drive there agrees - {@code null} while loose.
     *
     * <p>Cheap and needs no drive to be loaded, which is what the shaft check wants: it is asked about
     * every face of every cell while Create links a frame, and only has to tell one drive's cells from
     * another's.
     */
    @Nullable
    public BlockPos drivePos() {
        return driveStep == null ? null : DriveStructure.fromStep(worldPosition, driveStep);
    }

    /** Whether this extender points at the drive standing at {@code drivePos}, whether or not the drive agrees. */
    public boolean pointsAt(BlockPos drivePos) {
        return drivePos.equals(drivePos());
    }

    /**
     * Tells this extender which drive it belongs to, or with {@code null} that it is loose again.
     *
     * <p>Sends itself to the client, whose console and goggles need the pointer too. When the block
     * state is changing in the same breath this costs nothing extra - both only mark the position for
     * the next broadcast - but a drive that grows keeps its existing cells formed, and for a cell whose
     * state does not change this is the only update there is.
     */
    public void setDrive(@Nullable BlockPos drivePos) {
        BlockPos next = drivePos == null ? null : DriveStructure.step(worldPosition, drivePos);
        if (Objects.equals(driveStep, next)) {
            return;
        }
        driveStep = next;
        setChanged();
        if (level != null && !level.isClientSide) {
            sendData();
        }
    }

    // -------------------------------------------------------------------- nbt

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (driveStep != null) {
            tag.put("DriveStep", NbtUtils.writeBlockPos(driveStep));
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        driveStep = tag.contains("DriveStep") ? NbtUtils.readBlockPos(tag, "DriveStep").orElse(null) : null;
    }

    // --------------------------------------------------------------- goggles

    /** A formed extender reads exactly as the drive does; a loose one says what it is waiting for. */
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        RiftDriveBlockEntity drive = drive();
        if (drive != null) {
            return drive.addToGoggleTooltip(tooltip, isPlayerSneaking);
        }
        AWLang.translate("gui.goggles.drive_extender").forGoggles(tooltip);
        AWLang.translate("gui.drive_extender.incomplete").style(ChatFormatting.RED).forGoggles(tooltip, 1);
        AWLang.translate("gui.drive_extender.hint").style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        return true;
    }
}
