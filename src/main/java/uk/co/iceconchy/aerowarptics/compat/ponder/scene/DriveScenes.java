package uk.co.iceconchy.aerowarptics.compat.ponder.scene;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * The Rift Drive.
 *
 * <p>The one scene that cannot show the thing it is about. A warp moves a Sable sub-level, and a
 * Ponder scene has no airship in it - so this teaches what a player can act on before they ever
 * reach the console: where the drive goes, what it eats, and what decides whether a jump is
 * affordable. The flight itself is left to be seen from the deck.
 */
public final class DriveScenes {

    private DriveScenes() {
    }

    public static void riftDrive(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("rift_drive", "The Rift Drive");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos drive = util.grid().at(2, 1, 2);
        Vec3 top = util.vector().topOf(drive);

        scene.world().showSection(util.select().position(drive), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
                .text("A Rift Drive moves an entire airship to a Warp Anchor, hull and cargo and crew together")
                .pointAt(top)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.overlay().showText(70)
                .text("It has to be built into the ship. A drive standing on the ground has nothing to move")
                .pointAt(util.vector().centerOf(drive))
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.world().showSection(util.select().fromTo(2, 1, 3, 2, 1, 4), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(90)
                .text("Rotational force spins it up. Below the drive's minimum speed it will not charge at all")
                .pointAt(util.vector().blockSurface(util.grid().at(2, 1, 3), Direction.WEST))
                .colored(PonderPalette.FAST)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("Charge builds while it turns. A jump spends a share of it - more for distance, more for a heavy ship")
                .pointAt(top)
                .colored(PonderPalette.MEDIUM)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showControls(top, Pointing.DOWN, 50)
                .rightClick();
        scene.idle(10);
        scene.overlay().showText(80)
                .text("Right-click for the console: pick an anchor, and the drive quotes the cost before committing")
                .pointAt(top)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.overlay().showText(90)
                .text("Mk I to Singularity buy reach, speed and efficiency. The Singularity pays for its range with an unsteady arrival")
                .pointAt(util.vector().centerOf(drive))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);
    }
}
