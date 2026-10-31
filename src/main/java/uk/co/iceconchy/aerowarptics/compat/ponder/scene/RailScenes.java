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
 * Trains through a Rift Gate: the third gate lesson, and the one a player cannot work out by looking.
 *
 * <p>Create's own train scenes show no train, and neither does this one. A Ponder level has no train
 * in it - a carriage is a contraption entity bound to a track graph, and there is none here to bind
 * to - so what the scene shows is everything a player can act on: the route set before any track is
 * laid, the row the track has to run into, the pane the gate puts up for a train, and the gate
 * closing itself afterwards. Faking a carriage with a minecart would teach a vehicle this mod does
 * not carry.
 *
 * <p>It has its own schematic rather than the ring the assembly and dialling scenes share, because a
 * lane is scenery those two have no use for: the bottom row of a gate's opening stands a block above
 * the ground the ring sits on, so the track into it runs along a low bed. The ring itself is laid out
 * the same way - standing across X, controller in the bottom row - so a player who has watched the
 * other two recognises it.
 *
 * <p>The pane goes in by hand at the beat the gate dials for a train, and is taken away again when
 * the gate shuts behind it. Both are the same trick {@link GateScenes} plays, for the same reason: a
 * Ponder level has no live gate state to read, and the doorway is a block like any other.
 */
public final class RailScenes {

    private RailScenes() {
    }

    /** Where the ring stands in the rail schematic - four cells of approach in front of it. */
    private static final int PLANE = 4;

    /** The middle of the opening's bottom row, where the lane crosses. */
    private static final int LANE_X = 3;

    private static Selection ring(SceneBuildingUtil util) {
        return util.select().fromTo(1, 1, PLANE, 5, 5, PLANE)
                .substract(util.select().fromTo(2, 2, PLANE, 4, 4, PLANE));
    }

    private static Selection opening(SceneBuildingUtil util) {
        return util.select().fromTo(2, 2, PLANE, 4, 4, PLANE);
    }

    /** The row a lane has to run into, and the only row of the opening that will take track. */
    private static Selection bottomRow(SceneBuildingUtil util) {
        return util.select().fromTo(2, 2, PLANE, 4, 2, PLANE);
    }

    /** The player's own railway: straight track on its bed, running up to the opening. */
    private static Selection lane(SceneBuildingUtil util) {
        return util.select().fromTo(LANE_X, 1, 0, LANE_X, 2, PLANE - 1);
    }

    private static Selection power(SceneBuildingUtil util) {
        return util.select().fromTo(LANE_X, 1, PLANE + 1, LANE_X, 1, PLANE + 2);
    }

    /** Moves every pane in the opening to one stage, leaving anything that is not a pane alone. */
    private static void stage(SceneBuilder scene, SceneBuildingUtil util, RiftPortalStage to) {
        scene.world().modifyBlocks(opening(util), state ->
                state.hasProperty(RiftPortalBlock.STAGE)
                        ? state.setValue(RiftPortalBlock.STAGE, to)
                        : state, false);
    }

    public static void trains(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("rift_gate_rail", "Trains through a Rift Gate");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos controller = util.grid().at(LANE_X, 1, PLANE);
        Vec3 face = util.vector().blockSurface(controller, Direction.SOUTH);
        Vec3 middle = util.vector().centerOf(util.grid().at(LANE_X, 3, PLANE));
        Vec3 laneCell = util.vector().centerOf(util.grid().at(LANE_X, 2, PLANE));
        Vec3 rails = util.vector().topOf(util.grid().at(LANE_X, 2, PLANE - 1));

        scene.world().showSection(ring(util), Direction.DOWN);
        scene.idle(15);
        scene.world().showSection(power(util), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(lane(util), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(90)
                .text("Create track run into a Rift Gate becomes a lane, and a train that goes in comes out of the gate it is dialled to")
                .pointAt(rails)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showControls(face, Pointing.RIGHT, 50)
                .rightClick();
        scene.idle(10);
        scene.overlay().showText(90)
                .text("Set the gate's route at its dial before laying any track. Track run into a gate with no route is refused")
                .pointAt(face)
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("A route may cross into another dimension. Trains go there, though anyone on foot is turned back")
                .pointAt(middle)
                .colored(PonderPalette.BLUE)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showOutline(PonderPalette.GREEN, "bottom_row", bottomRow(util), 90);
        scene.idle(10);
        scene.overlay().showText(90)
                .text("The track has to run straight into the bottom row of the opening. Laid along the gate, or higher up, it is refused")
                .pointAt(laneCell)
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("At the far gate, track and a short apron are laid for you. Build on from the apron, since re-dialling moves them")
                .pointAt(middle)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        // The gate opening for a train, on the two beats a real one plays: the pane arrives on its own
        // OPENING stage and settles to OPEN. Shown as well as set - the opening is the one part of the
        // schematic nothing has revealed, because a gate waiting for a train has no doorway.
        scene.world().setBlocks(opening(util), AWBlocks.RIFT_PORTAL.get().defaultBlockState()
                .setValue(RiftPortalBlock.AXIS, Direction.Axis.X), true);
        scene.world().showSection(opening(util), Direction.DOWN);
        scene.idle(25);
        stage(scene, util, RiftPortalStage.OPEN);
        scene.idle(20);

        scene.overlay().showText(90)
                .text("A train that reaches a shut gate waits at the pane while the gate dials for it, needing rotation and essence")
                .pointAt(laneCell)
                .colored(PonderPalette.FAST)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("That train pays the dial. One that finds the gate already open pays nothing, since only the gate's opener pays")
                .pointAt(face)
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("A scheduled train asks the gate to dial while it is still on its way, so it rarely has to stop at all")
                .pointAt(rails)
                .colored(PonderPalette.MEDIUM)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        // And shutting again behind it. CLOSING first, then the panes go: the stage is what a player
        // sees of a gate winding down, and hiding the section without it would be a doorway blinking out.
        stage(scene, util, RiftPortalStage.CLOSING);
        scene.idle(20);
        scene.world().hideSection(opening(util), Direction.UP);
        scene.idle(15);

        scene.overlay().showText(90)
                .text("The gate shuts itself once the last carriage is through, unless a player opened it. Its track stays where it is")
                .pointAt(middle)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(80)
                .text("A redstone signal on the controller holds trains back, so a gate hung up by hand is not opened by the next train")
                .pointAt(face)
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);
    }
}
