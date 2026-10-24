package uk.co.iceconchy.aerowarptics.compat.ponder.scene;

import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import uk.co.iceconchy.aerowarptics.stabiliser.SpatialStabiliserBlock;

/**
 * The Rift Wellhead: how its frame is built, what goes in and comes out, and what holds it steady.
 *
 * <p>Like the fissure's own scenes, this teaches what a player can act on and says plainly what they will see
 * when they get there. A Ponder level draws no rift, so the tear the frame is built round is told in words; and
 * it runs no machine, so the frame shown is the one a player builds, a moment before it forms.
 */
public final class WellheadScenes {

    private WellheadScenes() {
    }

    public static void wellhead(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("rift_wellhead", "Farming a Fissure");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos tear = util.grid().at(3, 2, 3);
        BlockPos collar = util.grid().at(3, 2, 4);
        BlockPos hatch = util.grid().at(3, 3, 3);
        BlockPos tap = util.grid().at(2, 2, 3);
        BlockPos west = util.grid().at(0, 1, 3);
        BlockPos east = util.grid().at(6, 1, 3);

        // The frame goes up a layer at a time, as it is built, with the collar's place left open to the last.
        scene.world().showSection(util.select().fromTo(2, 1, 2, 4, 1, 4), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(2, 2, 2, 4, 2, 3), Direction.DOWN);
        scene.world().showSection(util.select().position(2, 2, 4), Direction.DOWN);
        scene.world().showSection(util.select().position(4, 2, 4), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(2, 3, 2, 4, 3, 4), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(90)
                .text("Build a hollow cube round a Rift Fissure, with the fissure in its exact middle: Rift Casing for the edges, Warp Glass for the faces")
                .pointAt(util.vector().centerOf(tear))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.world().showSection(util.select().position(collar), Direction.NORTH);
        scene.idle(15);
        scene.overlay().showText(90)
                .text("Set a Rift Collar in the middle of one side and put a Warp Crystal in it. A five or seven block frame needs a better crystal")
                .pointAt(util.vector().blockSurface(collar, Direction.SOUTH))
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("Feed blocks in through the middle of the top. Rougher blocks strain the fissure more")
                .pointAt(util.vector().topOf(hatch))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);
        scene.overlay().showText(80)
                .text("Pipe Rift Essence out of the middle of any other face")
                .pointAt(util.vector().blockSurface(tap, Direction.WEST))
                .placeNearTarget();
        scene.idle(90);

        scene.world().showSection(util.select().position(west), Direction.DOWN);
        scene.world().showSection(util.select().position(east), Direction.DOWN);
        scene.idle(15);
        scene.world().modifyBlock(west, state -> state.setValue(SpatialStabiliserBlock.POWERED, true), false);
        scene.world().modifyBlock(east, state -> state.setValue(SpatialStabiliserBlock.POWERED, true), false);
        scene.effects().indicateRedstone(west);
        scene.effects().indicateRedstone(east);
        scene.overlay().showText(90)
                .text("Turn the collar with a shaft, and switch on Spatial Stabilisers round it, evenly spaced, to hold the strain down")
                .pointAt(util.vector().topOf(east))
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("More strain digests faster, and strains it further. A comparator reads it, and a redstone pulse scrams it")
                .pointAt(util.vector().blockSurface(collar, Direction.SOUTH))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);
        scene.overlay().showText(100)
                .text("Let the strain reach the top and it melts down: the fissure, the frame and the ground round them are gone")
                .pointAt(util.vector().centerOf(tear))
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(110);
    }
}
