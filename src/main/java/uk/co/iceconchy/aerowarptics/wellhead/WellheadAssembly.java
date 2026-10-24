package uk.co.iceconchy.aerowarptics.wellhead;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.fissure.RiftFissureBlock;
import uk.co.iceconchy.aerowarptics.fissure.RiftFissureBlockEntity;
import uk.co.iceconchy.aerowarptics.hold.Holds;
import uk.co.iceconchy.aerowarptics.registry.AWBlocks;
import uk.co.iceconchy.aerowarptics.trace.Trace;

/**
 * Forming a Rift Wellhead's frame out of the blocks standing round a fissure, and taking it apart again.
 *
 * <p>The level's side of {@link WellheadStructure}, as {@code DriveAssembly} is of {@code DriveStructure}: that
 * class says what a frame is, this one looks at the world and swaps the blocks. A frame is surveyed whole before
 * anything is touched, and refused with a named reason and the block at fault, so a refusal leaves every block
 * exactly as the player set it.
 *
 * <p>This is also the only code that marks a fissure harnessed. The fissure never changes a block but its own -
 * {@code RiftFissureTest} reads its source for that - so the state is flipped from here.
 */
public final class WellheadAssembly {

    private WellheadAssembly() {
    }

    /**
     * What a look at the ground round a collar found.
     *
     * @param radius  how far behind the collar its fissure is, nought if there is none
     * @param culprit the block that is wrong, for a refusal that has one
     */
    public record Survey(WellheadFailure failure, int radius, @Nullable BlockPos culprit) {

        static Survey refused(WellheadFailure failure, int radius, @Nullable BlockPos culprit) {
            return new Survey(failure, radius, culprit == null ? null : culprit.immutable());
        }
    }

    /** Looks at what stands round a collar, changing nothing. */
    public static Survey survey(Level level, RiftCollarBlockEntity collar) {
        BlockPos at = collar.getBlockPos();
        Direction side = collar.side();
        if (Airship.containing(collar) != null || level instanceof ServerLevel server && Holds.inPlot(server, at)) {
            return Survey.refused(WellheadFailure.ABOARD_SHIP, 0, null);
        }
        if (level instanceof ServerLevel server && (ScarZones.within(server, at) || Meltdowns.over(server, at))) {
            return Survey.refused(WellheadFailure.IN_SCAR_ZONE, 0, null);
        }
        int radius = WellheadStructure.radius(back ->
                level.getBlockState(at.relative(side, -back)).getBlock() instanceof RiftFissureBlock);
        if (radius == 0) {
            return Survey.refused(WellheadFailure.NO_FISSURE, 0, null);
        }
        BlockPos centre = WellheadStructure.centre(at, side, radius);
        if (!(level.getBlockEntity(centre) instanceof RiftFissureBlockEntity fissure)) {
            return Survey.refused(WellheadFailure.NO_FISSURE, 0, null);
        }
        if (fissure.isSealing()) {
            return Survey.refused(WellheadFailure.FISSURE_SEALING, radius, centre);
        }
        if (fissure.isHarnessed() && !at.equals(fissure.harnessedBy())) {
            return Survey.refused(WellheadFailure.FISSURE_TAKEN, radius, centre);
        }
        WellheadTier crystal = collar.crystalTier();
        if (crystal == null || !crystal.permits(WellheadTier.ofRadius(radius))) {
            return Survey.refused(WellheadFailure.CRYSTAL_TOO_WEAK, radius, null);
        }
        for (Vec3i offset : WellheadStructure.interior(radius)) {
            BlockPos cell = centre.offset(offset);
            BlockState state = level.getBlockState(cell);
            if (state.getBlock() instanceof RiftFissureBlock) {
                return Survey.refused(WellheadFailure.SECOND_FISSURE, radius, cell);
            }
            if (!state.isAir()) {
                return Survey.refused(WellheadFailure.INTERIOR_BLOCKED, radius, cell);
            }
        }
        for (Vec3i offset : WellheadStructure.shell(radius)) {
            BlockPos cell = centre.offset(offset);
            BlockState state = level.getBlockState(cell);
            if (state.getBlock() instanceof RiftFissureBlock) {
                return Survey.refused(WellheadFailure.SECOND_FISSURE, radius, cell);
            }
            if (!fits(state, WellheadStructure.cell(offset, radius, side))) {
                return Survey.refused(WellheadFailure.FRAME_INCOMPLETE, radius, cell);
            }
        }
        return new Survey(WellheadFailure.NONE, radius, null);
    }

    /** Whether a block is what a cell of an unformed frame has to be. */
    private static boolean fits(BlockState state, WellheadStructure.Cell cell) {
        return switch (cell) {
            case CASING -> state.is(AWBlocks.RIFT_CASING.get());
            // A face is glass right across until the frame forms: its middle becomes a port then.
            case GLASS, HATCH, TAP -> state.is(AWBlocks.WARP_GLASS.get());
            case COLLAR -> state.getBlock() instanceof RiftCollarBlock;
            default -> false;
        };
    }

    /** What a cell of a formed frame is, or {@code null} for the collar's own, which stays as it is. */
    @Nullable
    private static BlockState formed(Vec3i offset, WellheadStructure.Cell cell) {
        return switch (cell) {
            case CASING -> AWBlocks.RIFT_WELLHEAD_PART.get().at(offset, false);
            case GLASS -> AWBlocks.RIFT_WELLHEAD_PART.get().at(offset, true);
            case HATCH -> AWBlocks.RIFT_WELLHEAD_PORT.get().at(offset, true);
            case TAP -> AWBlocks.RIFT_WELLHEAD_PORT.get().at(offset, false);
            default -> null;
        };
    }

    /**
     * The collar's periodic look at its own frame: forms one that is now complete, and takes apart one that is
     * not what it was.
     *
     * <p>Skipped outright while any cell is in an unloaded chunk. Unforming a frame because half of it could
     * not be read would be wrong, and would visibly break it for the player standing beside it.
     */
    public static void check(Level level, RiftCollarBlockEntity collar) {
        if (level.isClientSide) {
            return;
        }
        if (!collar.isFormed()) {
            Survey survey = survey(level, collar);
            collar.noteSurvey(survey);
            if (!survey.failure().isFailure()) {
                form(level, collar, survey.radius());
            }
            return;
        }
        int radius = collar.radius();
        Direction side = collar.side();
        BlockPos centre = collar.centre();
        for (Vec3i offset : WellheadStructure.shell(radius)) {
            if (!level.isLoaded(centre.offset(offset))) {
                return;
            }
        }
        if (!(level.getBlockEntity(centre) instanceof RiftFissureBlockEntity fissure)
                || !collar.getBlockPos().equals(fissure.harnessedBy())) {
            Trace.log(Trace.WELLHEAD, "collar at {} lost its fissure at {}: frame let go",
                    Trace.pos(collar.getBlockPos()), Trace.pos(centre));
            unform(level, collar, null);
            return;
        }
        WellheadTier crystal = collar.crystalTier();
        if (crystal == null || !crystal.permits(WellheadTier.ofRadius(radius))) {
            Trace.log(Trace.WELLHEAD, "collar at {} has no crystal for a frame of radius {}: frame let go",
                    Trace.pos(collar.getBlockPos()), radius);
            unform(level, collar, null);
            return;
        }
        for (Vec3i offset : WellheadStructure.shell(radius)) {
            WellheadStructure.Cell cell = WellheadStructure.cell(offset, radius, side);
            BlockState expected = formed(offset, cell);
            if (expected != null && level.getBlockState(centre.offset(offset)) != expected) {
                Trace.log(Trace.WELLHEAD, "collar at {} found {} where its frame should be: frame let go",
                        Trace.pos(collar.getBlockPos()), Trace.pos(centre.offset(offset)));
                unform(level, collar, null);
                return;
            }
        }
    }

    /** Swaps a surveyed frame for its parts and takes the fissure. */
    private static void form(Level level, RiftCollarBlockEntity collar, int radius) {
        BlockPos at = collar.getBlockPos();
        Direction side = collar.side();
        BlockPos centre = WellheadStructure.centre(at, side, radius);
        if (!(level.getBlockEntity(centre) instanceof RiftFissureBlockEntity fissure)) {
            return;
        }
        // The fissure and the collar are told before any block is swapped, so every part set down finds its
        // machine at once and none takes itself for an orphan.
        fissure.harness(at);
        collar.setRadius(radius);
        level.setBlock(centre, level.getBlockState(centre).setValue(RiftFissureBlock.HARNESSED, true), Block.UPDATE_ALL);
        for (Vec3i offset : WellheadStructure.shell(radius)) {
            BlockState part = formed(offset, WellheadStructure.cell(offset, radius, side));
            if (part != null) {
                level.setBlock(centre.offset(offset), part, Block.UPDATE_ALL);
            }
        }
        level.setBlock(at, level.getBlockState(at).setValue(RiftCollarBlock.FORMED, true), Block.UPDATE_ALL);
        level.playSound(null, at, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6F, 0.9F + 0.1F * radius);
        if (level instanceof ServerLevel server) {
            uk.co.iceconchy.aerowarptics.advancement.AWCriteria.wellhead(server, at,
                    uk.co.iceconchy.aerowarptics.advancement.WellheadTrigger.FORMED, 24.0D);
            // A tear a Sunderer made, held before it guttered: a home reactor.
            if (fissure.isConjured()) {
                uk.co.iceconchy.aerowarptics.advancement.AWCriteria.sunderer(server, at,
                        uk.co.iceconchy.aerowarptics.advancement.SundererTrigger.HELD, 24.0D);
            }
        }
        Trace.log(Trace.WELLHEAD, "collar at {} formed a frame of radius {} round the fissure at {} ({} mB of {})",
                Trace.pos(at), radius, Trace.pos(centre), fissure.reservoir(), fissure.original());
    }

    /**
     * Takes a frame apart: every part goes back to the casing or glass it was, and the fissure is let go.
     *
     * @param gone a block of the frame that has just been removed and is to be left as it now is
     */
    public static void unform(Level level, RiftCollarBlockEntity collar, @Nullable BlockPos gone) {
        if (level.isClientSide || !collar.isFormed()) {
            return;
        }
        int radius = collar.radius();
        BlockPos at = collar.getBlockPos();
        BlockPos centre = collar.centre();
        // Let go first. A part being swapped back asks whether it still has a machine, and has to hear no.
        collar.setRadius(0);
        for (Vec3i offset : WellheadStructure.shell(radius)) {
            BlockPos cell = centre.offset(offset);
            if (cell.equals(gone) || !level.isLoaded(cell)) {
                continue;
            }
            BlockState state = level.getBlockState(cell);
            if (state.getBlock() instanceof WellheadShellBlock shell) {
                level.setBlock(cell, shell.original(state), Block.UPDATE_ALL);
            }
        }
        if (level.getBlockEntity(centre) instanceof RiftFissureBlockEntity fissure && at.equals(fissure.harnessedBy())) {
            fissure.release();
            BlockState tear = level.getBlockState(centre);
            if (tear.getBlock() instanceof RiftFissureBlock) {
                level.setBlock(centre, tear.setValue(RiftFissureBlock.HARNESSED, false), Block.UPDATE_ALL);
            }
        }
        BlockState state = level.getBlockState(at);
        if (state.getBlock() instanceof RiftCollarBlock && state.getValue(RiftCollarBlock.FORMED)) {
            level.setBlock(at, state.setValue(RiftCollarBlock.FORMED, false), Block.UPDATE_ALL);
        }
        level.playSound(null, at, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.6F, 0.9F);
    }

    /**
     * A block of a formed frame has been taken out from under it. The frame comes apart, and the collar is told
     * it was broken open rather than let go - which, for a well with matter in it, is not the same thing at all.
     */
    public static void breach(Level level, RiftCollarBlockEntity collar, BlockPos gone) {
        if (level.isClientSide || !collar.isFormed()) {
            return;
        }
        Trace.log(Trace.WELLHEAD, "collar at {} breached: the block at {} was taken out of its frame",
                Trace.pos(collar.getBlockPos()), Trace.pos(gone));
        collar.onBreached();
        unform(level, collar, gone);
    }
}
