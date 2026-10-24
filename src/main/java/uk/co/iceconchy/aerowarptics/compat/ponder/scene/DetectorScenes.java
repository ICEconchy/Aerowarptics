package uk.co.iceconchy.aerowarptics.compat.ponder.scene;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The Spatial Wave Detector.
 *
 * <p>Like the warp scenes, this cannot show what it is about: a Ponder level has no airship to warp
 * in and no gate pair to step out of. What it can show is the whole of what a player wires up - the
 * detector's answer - so the arrivals are marked where they happen and the signal is set on the blocks
 * by hand, a strong one and then a weak one, rather than an airship being faked.
 *
 * <p>Every block's signal is written explicitly rather than with {@code toggleRedstonePower}, which
 * only knows off and full. The point of the second arrival is that it is <em>not</em> full, and the
 * dust fading along the wire is how a player sees that.
 */
public final class DetectorScenes {

    /** A reading for an arrival right beside the detector, and one near the edge of its range. */
    private static final int NEAR = 15;
    private static final int FAR = 4;

    private DetectorScenes() {
    }

    public static void spatialWaveDetector(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("spatial_wave_detector", "Hearing an Arrival");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos detector = util.grid().at(1, 1, 1);
        BlockPos anchor = util.grid().at(3, 1, 3);
        BlockPos lamp = util.grid().at(4, 1, 1);

        scene.world().showSection(util.select().position(detector), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
                .text("A Spatial Wave Detector listens for anything arriving through a rift nearby")
                .pointAt(util.vector().topOf(detector))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.overlay().showControls(util.vector().blockSurface(detector, Direction.NORTH), Pointing.DOWN, 50)
                .scroll();
        scene.idle(10);
        scene.overlay().showText(80)
                .text("Scroll on the box on any side to set how far it listens. The server may cap how far that can be")
                .pointAt(util.vector().blockSurface(detector, Direction.NORTH))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.world().showSection(util.select().fromTo(2, 1, 1, 4, 1, 1), Direction.WEST);
        scene.idle(10);
        scene.world().showSection(util.select().position(anchor), Direction.DOWN);
        scene.idle(20);

        // Near: the anchor is a couple of blocks off, well inside the default range.
        scene.effects().indicateSuccess(anchor.above());
        signal(scene, util, NEAR);
        scene.overlay().showText(90)
                .text("When a ship finishes a warp, or anyone steps out of a Rift Gate within range, it gives off a pulse of redstone")
                .pointAt(util.vector().centerOf(lamp))
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(30);
        signal(scene, util, 0);
        scene.idle(70);

        // Far: marked out at the far corner of the plate, standing in for the edge of the range.
        BlockPos far = util.grid().at(4, 2, 4);
        scene.effects().indicateSuccess(far);
        signal(scene, util, FAR);
        scene.overlay().showText(90)
                .text("The closer the arrival, the stronger the signal. Right beside it gives 15, and the very edge of its range gives 1")
                .pointAt(util.vector().centerOf(util.grid().at(2, 1, 1)))
                .colored(PonderPalette.BLUE)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(30);
        signal(scene, util, 0);
        scene.idle(70);

        scene.overlay().showText(90)
                .text("A ship is measured from its nearest part, not its middle. If a long hull's bow comes out beside the detector, the ship counts as arriving beside it")
                .pointAt(util.vector().topOf(anchor))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(80)
                .text("It only hears arrivals, never departures. Another arrival during a pulse restarts the pulse")
                .pointAt(util.vector().topOf(detector))
                .colored(PonderPalette.MEDIUM)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.overlay().showText(80)
                .text("It works aboard an airship too, and hears its own ship arrive at full strength")
                .pointAt(util.vector().topOf(detector))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);
    }

    /**
     * Puts a reading on the detector and carries it down the wire the way redstone would: one less
     * per block of dust, and the lamp lit by whatever reaches it.
     *
     * <p>Written block by block because a Ponder level does not propagate a signal - it draws the
     * states it is given. The positions are the ones {@code tools/ponder_schematics.py} lays out, which
     * {@code PonderSceneryTest} holds it to.
     */
    private static void signal(SceneBuilder scene, SceneBuildingUtil util, int power) {
        int first = Math.max(0, power - 1);
        int second = Math.max(0, power - 2);
        scene.world().modifyBlock(util.grid().at(1, 1, 1),
                state -> state.setValue(BlockStateProperties.POWER, power), false);
        scene.world().modifyBlock(util.grid().at(2, 1, 1),
                state -> state.setValue(BlockStateProperties.POWER, first), false);
        scene.world().modifyBlock(util.grid().at(3, 1, 1),
                state -> state.setValue(BlockStateProperties.POWER, second), false);
        scene.world().modifyBlock(util.grid().at(4, 1, 1),
                state -> state.setValue(BlockStateProperties.LIT, second > 0), false);
        if (power > 0) {
            scene.effects().indicateRedstone(util.grid().at(1, 1, 1));
        }
    }
}
