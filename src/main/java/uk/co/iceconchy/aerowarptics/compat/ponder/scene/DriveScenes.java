package uk.co.iceconchy.aerowarptics.compat.ponder.scene;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import uk.co.iceconchy.aerowarptics.drive.DriveExtenderBlock;
import uk.co.iceconchy.aerowarptics.drive.DriveExtenderBlockEntity;
import uk.co.iceconchy.aerowarptics.drive.DriveStructure;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;

/**
 * The Rift Drive, and the cube of Drive Extenders it can be built out into.
 *
 * <p>The first of them is the one scene that cannot show the thing it is about. A warp moves a Sable
 * sub-level, and a Ponder scene has no airship in it - so it teaches what a player can act on before
 * they ever reach the console: where the drive goes, what it eats, and what decides whether a jump is
 * affordable. The flight itself is left to be seen from the deck.
 *
 * <p>The second has the opposite problem and the opposite answer: a frame is entirely a thing on the
 * ground, and every part of it can be shown honestly, including the moment it forms.
 */
public final class DriveScenes {

    /** The frame the extender scene builds, matching {@code tools/ponder_schematics.py}. */
    private static final int FRAME_SIZE = 3;

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

    // ------------------------------------------------------------- extenders

    /**
     * Building a drive out into a cube of Drive Extenders.
     *
     * <p>The scene forms the frame itself, as the Astrolabe's does and for the same reason: forming is
     * a server decision and a Ponder level makes none. It does it in the order a real level does -
     * every cell told which drive it belongs to, the drive told the shape of its frame, and only then
     * the states flipped - because anybody reading this to learn how a frame comes together should
     * read the right order.
     *
     * <p>The drive sits in the middle of the cube, touching neither the shaft nor the outside. That is
     * the arrangement players assume cannot work, and it is the one this scene exists to show working.
     */
    public static void driveExtender(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("drive_extender", "Building a Drive Out");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos drive = util.grid().at(2, 2, 2);
        DriveStructure.Frame frame = new DriveStructure.Frame(util.grid().at(1, 1, 1), FRAME_SIZE);
        Selection cube = util.select().fromTo(1, 1, 1, 3, 3, 3);
        Selection casing = cube.substract(util.select().position(drive));
        Selection power = util.select().fromTo(2, 1, 4, 2, 1, 5);
        BlockPos corner = util.grid().at(1, 3, 1);

        scene.world().showSection(util.select().position(drive), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
                .text("A Rift Drive works on its own. Drive Extenders are an optional upgrade, never a requirement")
                .pointAt(util.vector().centerOf(drive))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.world().showSection(casing, Direction.DOWN);
        scene.idle(20);

        scene.overlay().showOutline(PonderPalette.WHITE, "cube", cube, 80);
        scene.idle(10);
        scene.overlay().showText(90)
                .text("Build a complete cube of them, from two blocks along an edge up to seven, with the drive anywhere inside it - buried in the middle included")
                .pointAt(util.vector().topOf(corner))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(80)
                .text("Until the cube is finished, the extenders are inert casing. An incomplete frame does nothing and carries nothing")
                .pointAt(util.vector().blockSurface(util.grid().at(1, 1, 1), Direction.WEST))
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        form(scene, frame, casing, drive);
        scene.effects().indicateSuccess(drive);
        scene.idle(15);

        scene.overlay().showText(80)
                .text("Place the last block and the drive forms. What stands there is now one machine, drawn the size of its frame")
                .pointAt(util.vector().topOf(corner))
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.world().showSection(power, Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(90)
                .text("A shaft against any face of the cube turns it. Every extender passes the rotation through, so nothing needs to reach the drive itself")
                .pointAt(util.vector().blockSurface(util.grid().at(2, 1, 4), Direction.WEST))
                .colored(PonderPalette.FAST)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showControls(util.vector().topOf(corner), Pointing.DOWN, 50)
                .rightClick();
        scene.idle(10);
        scene.overlay().showText(90)
                .text("Every block of the frame acts as the drive: right-click any of them for the console, and a module against any face links to it")
                .pointAt(util.vector().topOf(corner))
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(80)
                .text("It does not fly any further for being bigger. What a larger frame gives you is room and faces to attach things to, not range")
                .pointAt(util.vector().centerOf(drive))
                .colored(PonderPalette.MEDIUM)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.overlay().showText(80)
                .text("Remove one block and the cube is no longer complete. The frame stops working, and the drive goes back to being a single block")
                .pointAt(util.vector().blockSurface(corner, Direction.WEST))
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);
    }

    /** Forms the frame by hand, the way the level would: pointers first, then the drive, then the states. */
    private static void form(SceneBuilder scene, DriveStructure.Frame frame, Selection casing,
                             BlockPos drive) {
        for (BlockPos cell : frame.cells()) {
            if (!cell.equals(drive)) {
                scene.world().modifyBlockEntity(cell, DriveExtenderBlockEntity.class,
                        extender -> extender.setDrive(drive));
            }
        }
        scene.world().modifyBlockEntity(drive, RiftDriveBlockEntity.class, be -> be.setFrame(frame));
        scene.world().modifyBlocks(casing, state -> state.hasProperty(DriveExtenderBlock.FORMED)
                ? state.setValue(DriveExtenderBlock.FORMED, true)
                : state, false);
    }
}
