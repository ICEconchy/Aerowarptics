package uk.co.iceconchy.aerowarptics.compat.ponder.scene;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * The two halves of a Rift Gate: building the ring, and getting something through it.
 *
 * <p>Both play on the same schematic - a five by five ring standing across X with a three by three
 * opening, and the controller sitting in the bottom row where a shaft can reach it.
 *
 * <p>Neither scene shows an aperture. The hole in space is drawn by the client from live gate state,
 * and a Ponder level has none, so these scenes explain the doorway rather than pretending to open
 * one. That is the honest version anyway: what a player gets wrong about a gate is the ring, the
 * power and the pairing, none of which the animation would have told them.
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
                .text("Dialling is paid for in Rift Essence, piped or poured into the controller")
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
