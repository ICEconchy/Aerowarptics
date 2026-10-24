package uk.co.iceconchy.aerowarptics.drive;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Builds and takes apart extended Rift Drives in a level.
 *
 * <p>The rules - what shape a frame is, where its drive sits, which frame wins - are
 * {@link DriveStructure}'s. This is the half that reads blocks and writes them.
 *
 * <p>Forming is driven by placing and breaking, as the Astrolabe's is: the block laid last completes a
 * frame and the block broken first ruins it. The drive also re-checks its own frame every so often, see
 * {@link #check}, because some things put blocks down or take them away without placing or breaking
 * anything in the ordinary sense - Sable assembling a hull is the one that matters.
 */
public final class DriveAssembly {

    private DriveAssembly() {
    }

    /**
     * The drive a position is part of: the drive standing there, or the drive of the formed extender
     * standing there. {@code null} for anything else, a loose extender included.
     *
     * <p>This is the whole of how a module finds its drive and how a click finds a console, which is
     * why a module bolted to any face of a finished frame works exactly as one bolted to the drive.
     */
    @Nullable
    public static RiftDriveBlockEntity driveAt(BlockGetter level, BlockPos pos) {
        return switch (level.getBlockEntity(pos)) {
            case RiftDriveBlockEntity drive -> drive.isRemoved() ? null : drive;
            case DriveExtenderBlockEntity extender -> extender.drive();
            case null, default -> null;
        };
    }

    /**
     * Forms the largest frame a drive now completes, and lets go of any cells it no longer covers.
     *
     * <p>Called when a drive is placed, when an extender appears beside a drive's extenders, and when a
     * frame loses a cell. Doing nothing is the common outcome and is cheap - see
     * {@link DriveStructure#findBest} for how the search stays bounded however much casing is about.
     */
    public static void tryForm(Level level, BlockPos drivePos) {
        if (level.isClientSide || !(level.getBlockEntity(drivePos) instanceof RiftDriveBlockEntity drive)
                || drive.isRemoved()) {
            return;
        }
        DriveStructure.Frame current = drive.frame();
        DriveStructure.Frame best = DriveStructure.findBest(drivePos, cell -> usable(level, cell, drivePos));
        if (best.equals(current) && (!current.isExtended() || intact(level, drive))) {
            return;
        }

        // Anything the old frame held that the new one does not goes back to being loose - a frame that
        // re-forms more centred on its drive after a rebuild may leave a slab of the old one behind.
        for (BlockPos cell : current.cells()) {
            if (!best.covers(cell)) {
                release(level, cell, drivePos);
            }
        }
        // Every pointer is written before any state flips. Create re-links a flipped cell on its next
        // tick, asking its neighbours whether they belong to the same drive, and by then they all say.
        List<BlockPos> joining = new ArrayList<>();
        for (BlockPos cell : best.cells()) {
            if (cell.equals(drivePos) || !(level.getBlockEntity(cell) instanceof DriveExtenderBlockEntity extender)) {
                continue;
            }
            extender.setDrive(drivePos);
            if (!level.getBlockState(cell).getValue(DriveExtenderBlock.FORMED)) {
                joining.add(cell);
            }
        }
        for (BlockPos cell : joining) {
            setFormed(level, cell, true);
        }
        drive.setFrame(best);
        if (best.isExtended() && best.size() > current.size()) {
            level.playSound(null, drivePos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6F, 1.2F);
        }
    }

    /**
     * Takes a drive's frame apart, leaving the drive standing on its own.
     *
     * @return the extenders that were let go of, for {@link #offerTo} to see whether another drive wants them
     */
    public static List<BlockPos> unform(Level level, RiftDriveBlockEntity drive) {
        DriveStructure.Frame frame = drive.frame();
        List<BlockPos> freed = new ArrayList<>();
        if (level.isClientSide || !frame.isExtended()) {
            return freed;
        }
        BlockPos drivePos = drive.getBlockPos();
        for (BlockPos cell : frame.cells()) {
            if (!cell.equals(drivePos) && release(level, cell, drivePos)) {
                freed.add(cell);
            }
        }
        drive.setFrame(DriveStructure.Frame.single(drivePos));
        return freed;
    }

    /**
     * Gives every drive near these extenders the chance to build them into a frame.
     *
     * <p>Called with a newly placed extender, and with the cells a broken frame let go of - which may be
     * exactly what a neighbouring drive was one block short of.
     */
    public static void offerTo(Level level, Collection<BlockPos> extenders) {
        if (level.isClientSide || extenders.isEmpty()) {
            return;
        }
        List<BlockPos> drives = DriveStructure.nearbyDrives(extenders,
                pos -> level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof DriveExtenderBlock,
                pos -> level.isLoaded(pos) && level.getBlockEntity(pos) instanceof RiftDriveBlockEntity);
        for (BlockPos drive : drives) {
            tryForm(level, drive);
        }
    }

    /**
     * The drive's periodic look at its own frame.
     *
     * <p>A formed frame that has lost a cell, gained a stray pointer, or no longer suits the way the
     * drive faces is taken apart and formed again from what is really there. A lone drive tries to form,
     * in case its extenders arrived some way that placed nothing: assembling a hull copies every block
     * into the plot, and whether that fires the ordinary placement hooks, and in what order relative to
     * the block entities' data, is Sable's business rather than something this should have to trust.
     *
     * <p>Skipped outright while any cell is in an unloaded chunk. Unforming a frame because half of it
     * could not be read would be wrong, and would visibly break it for the player standing beside it.
     */
    public static void check(Level level, RiftDriveBlockEntity drive) {
        if (level.isClientSide) {
            return;
        }
        DriveStructure.Frame frame = drive.frame();
        BlockPos drivePos = drive.getBlockPos();
        if (!frame.isExtended()) {
            tryForm(level, drivePos);
            return;
        }
        for (BlockPos cell : frame.cells()) {
            if (!level.isLoaded(cell)) {
                return;
            }
        }
        if (!intact(level, drive)) {
            List<BlockPos> freed = unform(level, drive);
            tryForm(level, drivePos);
            offerTo(level, freed);
        }
    }

    /** Whether a cell may be built into the frame of the drive at {@code drivePos}. */
    static boolean usable(Level level, BlockPos cell, BlockPos drivePos) {
        if (!level.isLoaded(cell)) {
            return false;
        }
        BlockState state = level.getBlockState(cell);
        if (!(state.getBlock() instanceof DriveExtenderBlock)) {
            return false;
        }
        if (!state.getValue(DriveExtenderBlock.FORMED)) {
            return true;
        }
        // A formed extender is fair game only to the drive it already belongs to, so building beside a
        // finished drive cannot take its frame apart - and so a drive growing keeps its own cells.
        if (!(level.getBlockEntity(cell) instanceof DriveExtenderBlockEntity extender)) {
            return false;
        }
        return extender.pointsAt(drivePos) || extender.drive() == null;
    }

    /**
     * Whether two neighbouring blocks may share a shaft as far as frames are concerned: never when one
     * belongs to a frame and the other to a different drive's.
     *
     * <p>Without this, two extended drives built against each other would weld their kinetic networks
     * together through the touching faces - one shaft would turn both, and a stall on one would be a
     * stall on the other, with nothing on either machine to say why.
     *
     * @param drivePos the drive the asking block belongs to, or {@code null} for a loose one
     */
    public static boolean mayShareShaft(net.minecraft.world.level.BlockGetter level, @Nullable BlockPos drivePos,
                                        BlockPos neighbour) {
        return switch (level.getBlockEntity(neighbour)) {
            case RiftDriveBlockEntity other -> drivePos == null || other.getBlockPos().equals(drivePos);
            case DriveExtenderBlockEntity other -> {
                BlockPos theirs = other.drivePos();
                yield drivePos == null || theirs == null || theirs.equals(drivePos);
            }
            case null, default -> true;
        };
    }

    /** Whether every cell of a drive's frame is an extender that is formed and belongs to it. */
    static boolean intact(Level level, RiftDriveBlockEntity drive) {
        DriveStructure.Frame frame = drive.frame();
        BlockPos drivePos = drive.getBlockPos();
        if (!DriveStructure.fits(frame, drivePos)) {
            return false;
        }
        for (BlockPos cell : frame.cells()) {
            if (cell.equals(drivePos)) {
                continue;
            }
            BlockState state = level.getBlockState(cell);
            if (!(state.getBlock() instanceof DriveExtenderBlock) || !state.getValue(DriveExtenderBlock.FORMED)
                    || !(level.getBlockEntity(cell) instanceof DriveExtenderBlockEntity extender)
                    || !extender.pointsAt(drivePos)) {
                return false;
            }
        }
        return true;
    }

    /** Lets one extender go, if it was this drive's. Returns whether it was. */
    private static boolean release(Level level, BlockPos cell, BlockPos drivePos) {
        if (!level.isLoaded(cell) || !(level.getBlockEntity(cell) instanceof DriveExtenderBlockEntity extender)
                || !extender.pointsAt(drivePos)) {
            return false;
        }
        extender.setDrive(null);
        setFormed(level, cell, false);
        return true;
    }

    /**
     * Flips an extender between loose and formed through Create rather than a bare setBlock.
     *
     * <p>Whether an extender carries rotation is a property of its state, and Create only re-links a
     * kinetic block whose state changed in a way it counts as kinetically different - see
     * {@link DriveExtenderBlock#areStatesKineticallyEquivalent}. Going through
     * {@code switchToBlockState} also takes the cell out of its old network first, so a frame coming
     * apart stops turning at once instead of spinning on a source it is no longer joined to.
     */
    private static void setFormed(Level level, BlockPos cell, boolean formed) {
        BlockState state = level.getBlockState(cell);
        if (state.getBlock() instanceof DriveExtenderBlock && state.getValue(DriveExtenderBlock.FORMED) != formed) {
            KineticBlockEntity.switchToBlockState(level, cell, state.setValue(DriveExtenderBlock.FORMED, formed));
        }
    }
}
