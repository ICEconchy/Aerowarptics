package uk.co.iceconchy.aerowarptics.compat.ponder.scene;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import uk.co.iceconchy.aerowarptics.gate.RiftPortalBlock;
import uk.co.iceconchy.aerowarptics.gate.RiftPortalStage;
import uk.co.iceconchy.aerowarptics.registry.AWBlocks;

/**
 * The two halves of a Rift Gate: building the ring, and getting something through it.
 *
 * <p>Both play on the same schematic - a five by five ring standing across X with a three by three
 * opening, and the controller sitting in the bottom row where a shaft can reach it.
 *
 * <p>The dialling scene puts the pane in by hand, at the beat where a real gate would. It can, now
 * that the hole in space is a block: a Ponder level draws blocks like any other, and for as long as
 * the opening was a client-side effect read off live gate state there was nothing here for it to read
 * and the scene had to explain the doorway without ever showing one open. The assembly scene still
 * shows none, because a gate that has not been dialled has none.
 */
public final class GateScenes {

    private GateScenes() {
    }

    /** Where the ring is, in the shared schematic. */
    private static final int PLANE = 2;

    private static Selection ring(SceneBuildingUtil util) {
        return util.select().fromTo(0, 1, PLANE, 4, 5, PLANE)
                .substract(util.select().fromTo(1, 2, PLANE, 3, 4, PLANE));
    }

    private static Selection opening(SceneBuildingUtil util) {
        return util.select().fromTo(1, 2, PLANE, 3, 4, PLANE);
    }

    /**
     * Settles every pane in the opening from {@code OPENING} to {@code OPEN}, leaving anything that
     * is not a Rift Portal block alone.
     *
     * <p>{@code modifyBlocks} rather than {@code setBlocks}: the pane is already standing by the time
     * this runs, and re-placing it would restart its animation rather than settle it, which is the
     * opposite of the beat being played here.
     */
    private static void settlePane(SceneBuilder scene, SceneBuildingUtil util) {
        scene.world().modifyBlocks(opening(util), state ->
                state.hasProperty(RiftPortalBlock.STAGE)
                        ? state.setValue(RiftPortalBlock.STAGE, RiftPortalStage.OPEN)
                        : state, false);
    }

    // -------------------------------------------------------------- assembly

    public static void assembly(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("rift_gate", "Building a Rift Gate");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos controller = util.grid().at(2, 1, PLANE);
        Vec3 middle = util.vector().centerOf(util.grid().at(2, 3, PLANE));

        scene.world().showSection(ring(util), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(80)
                .text("Rift Gate Frame builds a ring standing upright, enclosing an empty opening")
                .pointAt(middle)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.overlay().showOutline(PonderPalette.GREEN, "opening", opening(util), 80);
        scene.idle(10);
        scene.overlay().showText(90)
                .text("The opening is the size limit. Whatever fits through it can travel - and nothing larger can")
                .pointAt(middle)
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(80)
                .text("Anything from four blocks of opening up to two hundred and twenty-five will form")
                .pointAt(middle)
                .placeNearTarget();
        scene.idle(90);

        scene.overlay().showOutline(PonderPalette.OUTPUT, "controller", util.select().position(controller), 70);
        scene.idle(10);
        scene.overlay().showText(80)
                .text("One block of the ring is a Rift Gate controller. It counts as frame, so it may sit anywhere in the circle")
                .pointAt(util.vector().blockSurface(controller, Direction.SOUTH))
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.overlay().showText(80)
                .text("A second gate has to be built somewhere else in the same world. A gate on its own goes nowhere")
                .pointAt(middle)
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);
    }

    // -------------------------------------------------------------- dialling

    public static void dialling(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("rift_gate_dialling", "Travelling by Rift Gate");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos controller = util.grid().at(2, 1, PLANE);
        Vec3 face = util.vector().blockSurface(controller, Direction.SOUTH);
        Vec3 middle = util.vector().centerOf(util.grid().at(2, 3, PLANE));

        scene.world().showSection(ring(util), Direction.DOWN);
        scene.idle(15);
        scene.world().showSection(util.select().fromTo(2, 1, PLANE + 1, 2, 1, PLANE + 2), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(90)
                .text("Rotational force is what holds an aperture open. Let the speed drop and the gate hangs up")
                .pointAt(util.vector().blockSurface(util.grid().at(2, 1, PLANE + 1), Direction.EAST))
                .colored(PonderPalette.FAST)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("A wider opening is heavier to hold and dearer to dial, so a gate costs what it is worth")
                .pointAt(middle)
                .colored(PonderPalette.MEDIUM)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("Rift Essence pays to dial, and keeps paying every second the doorway stands")
                .pointAt(face)
                .colored(PonderPalette.BLUE)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showControls(face, Pointing.RIGHT, 50)
                .rightClick();
        scene.idle(10);
        scene.overlay().showText(80)
                .text("Right-click the controller and choose which gate to reach for")
                .pointAt(face)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        // The pane, at the beat a real gate would fill its own opening. The ring here stands across
        // X, so the portal blocks do too. Shown as well as set: the opening is the one part of the
        // schematic `ring` deliberately leaves out, so these cells have never been revealed. It comes
        // in on its own OPENING stage - the block's own default - and settles to OPEN a moment later,
        // the same two beats a real gate plays out over its dial.
        scene.world().setBlocks(opening(util), AWBlocks.RIFT_PORTAL.get().defaultBlockState()
                .setValue(RiftPortalBlock.AXIS, Direction.Axis.X), true);
        scene.world().showSection(opening(util), Direction.DOWN);
        scene.idle(25);
        settlePane(scene, util);
        scene.idle(20);

        scene.overlay().showText(90)
                .text("Only the dialling gate pays. The far end needs no rotation and no essence of its own")
                .pointAt(middle)
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("Walk in, or drive in. A vehicle leaves the far gate the way that gate faces, carrying its speed with it")
                .pointAt(middle)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(80)
                .text("Both ends must lie in the same world. A gate cannot reach into another dimension")
                .pointAt(middle)
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);
    }
}
