package uk.co.iceconchy.aerowarptics.compat.ponder.scene;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Rift Coupler.
 *
 * <p>A fold is several airships and a Ponder level holds none, so this cannot show a flotilla going.
 * What it can show is the order a pilot does things in - which ship leads, how the others join, what
 * holding station and harmonising mean, and what happens to a ship that drifts - with the two drives on
 * the plate standing in for the two ships, and the escort's slot and its harmonic tether drawn in.
 */
public final class CouplerScenes {

    private CouplerScenes() {
    }

    public static void riftCoupler(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("rift_coupler", "The Rift Coupler");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos flagshipDrive = util.grid().at(1, 1, 1);
        BlockPos flagship = util.grid().at(1, 2, 1);
        BlockPos escortDrive = util.grid().at(5, 1, 1);
        BlockPos escort = util.grid().at(5, 2, 1);

        scene.world().showSection(util.select().fromTo(1, 1, 1, 1, 1, 4), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(flagship), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
                .text("A Rift Coupler fitted to any face of a Rift Drive lets its ship warp together with others, as a flotilla")
                .pointAt(util.vector().topOf(flagship))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.overlay().showControls(util.vector().topOf(flagship), Pointing.DOWN, 50).rightClick();
        scene.idle(10);
        scene.overlay().showText(90)
                .text("Right-click to set its role. One ship is the Flagship: its drive holds the course and commits the jump")
                .pointAt(util.vector().topOf(flagship))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.world().showSection(util.select().fromTo(5, 1, 1, 5, 1, 4), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(escort), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(100)
                .text("The others are Escorts. At an escort's coupler, take station with a flagship within reach: its owner accepts, and your own ships join straight away")
                .pointAt(util.vector().topOf(escort))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(110);

        AABB slot = new AABB(escortDrive).inflate(1.0D, 0.0D, 1.0D).expandTowards(0.0D, 1.0D, 0.0D);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, escortDrive, slot, 100);
        scene.overlay().showText(90)
                .text("On the flagship's Fleet Board, drag each escort to a slot, then press Sound Stations. Every ship in the flotilla hears the horn")
                .pointAt(util.vector().blockSurface(escortDrive, Direction.WEST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        Vec3 from = util.vector().topOf(flagship);
        Vec3 to = util.vector().topOf(escort);
        scene.overlay().showLine(PonderPalette.GREEN, from, to, 100);
        scene.overlay().showText(100)
                .text("An escort holding its slot with a full, turning drive harmonises with the flagship. Listen for the hums settling on one note")
                .pointAt(to)
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(110);

        scene.overlay().showText(90)
                .text("Every drive still needs its own power and pays its own charge, and a fleet costs more than the same ships flying alone")
                .pointAt(util.vector().blockSurface(escortDrive, Direction.SOUTH))
                .colored(PonderPalette.MEDIUM)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(100)
                .text("When every escort is ready, press Fold. A ship that drifts out of its slot during the countdown is shaken loose, and arrives late and off to one side")
                .pointAt(to)
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(110);
    }
}
