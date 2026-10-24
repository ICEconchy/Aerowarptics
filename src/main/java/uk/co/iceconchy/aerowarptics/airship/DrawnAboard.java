package uk.co.iceconchy.aerowarptics.airship;

import dev.ryanhcode.sable.Sable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import uk.co.iceconchy.aerowarptics.trace.ClientTrace;
import uk.co.iceconchy.aerowarptics.trace.Trace;

/**
 * Keeps this mod's machines drawn on a ship that has just warped.
 *
 * <p>A client draws a block entity only if a build of its chunk section filed it under a renderer, and
 * nothing but a section build ever files one. There are two lists it can be filed in: the section's own,
 * or - for a renderer whose {@code shouldRenderOffScreen} says so - the level renderer's off-screen set.
 * Every machine here is drawn by a GeckoLib renderer, so if neither list holds it, it draws nothing at all
 * while it goes on ticking, syncing and answering the goggles. That is what a warp was doing to them: a Rift
 * Modulator vanished off the top of a drive that stayed in plain sight. Create's own machines never do,
 * because Flywheel draws them from the block entity itself rather than from any section.
 *
 * <h2>Off-screen is the wrong list aboard a ship</h2>
 * The off-screen set exists for a model that reaches out of its own section, so that a culled section does
 * not take it with it. Sable never culls a ship's sections: it draws every block entity in every one of them,
 * every frame. What the set does bring with it is fragility. A section build that was cancelled because a
 * newer one was asked for still writes its off-screen list before it checks, so if it finishes last it puts
 * back whatever it saw, which may be from before the block entity arrived. The section's own list is only
 * ever replaced by a build that was not cancelled. And the cancel's request to build the section again is
 * made while the newer build is being scheduled and cleared straight after, by vanilla and by Sable alike, so
 * the stale list stands until something else in that section changes. A warp is exactly when a hull's
 * sections are rebuilt over and over - chunks sent, lit, relit - so a race that is rare anywhere else is run
 * dozens of times at once, and a one-block Modulator on a section of its own has nothing to change it back.
 *
 * <p>So {@link #offScreen} answers yes in the world, where a drive seven blocks across does need it, and no
 * on a ship, where the section's own list is enough.
 * Marking every renderer {@code shouldRenderOffScreen} to fix a machine missing after a crossing was a
 * mistake worth recording: it moved them onto the fragile list, and could not have helped anyway - the set
 * is filled by the same section build, so a block entity the build never saw is in neither place.
 *
 * <h2>A block entity that arrives after its section</h2>
 * The other way to be missing is to arrive late. A client told about a block entity it has no instance of -
 * an update packet for a block whose chunk arrived without it - makes one on the spot, and that path marks
 * nothing to be rebuilt. {@link #redraw}, called from every rendered block entity's {@code onLoad}, marks its
 * section instead. One that came with its chunk costs one extra build of one section; one that came late is
 * built in at last. Sable routes the mark to the ship's own sections when the block is aboard one.
 */
public final class DrawnAboard {

    private DrawnAboard() {
    }

    /** Call from a rendered block entity's {@code onLoad}. Does nothing on the server, which has nothing to draw. */
    public static void redraw(BlockEntity blockEntity) {
        Level level = blockEntity.getLevel();
        if (level == null || !level.isClientSide()) {
            return;
        }
        BlockState state = blockEntity.getBlockState();
        if (ClientTrace.enabled()) {
            ClientTrace.log(ClientTrace.DRAW, "{} loaded at {}{}: asking for its section to be rebuilt so it is drawn",
                    BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()), Trace.pos(blockEntity.getBlockPos()),
                    Sable.HELPER.isInPlotGrid(blockEntity) ? " aboard a ship" : "");
        }
        // The same state on both sides: nothing about the block changed, only whether the renderer knows there
        // is an entity in it. On a client level this is only ever a request to rebuild.
        level.sendBlockUpdated(blockEntity.getBlockPos(), state, state, Block.UPDATE_CLIENTS);
    }

    /**
     * What a renderer whose model can reach out of its section should answer to {@code shouldRenderOffScreen}:
     * yes in the world, no aboard a ship. Read when the section is built, so a machine carried onto a ship by
     * assembly, or off one by disassembly, is refiled by the rebuild that moving it causes anyway.
     */
    public static boolean offScreen(BlockEntity blockEntity) {
        return blockEntity.getLevel() == null || !Sable.HELPER.isInPlotGrid(blockEntity);
    }
}
