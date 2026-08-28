package uk.co.iceconchy.aerowarptics.compat.ponder.scene;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * The Rift Modulator.
 *
 * <p>Like the drive's own scene, this cannot show the thing it is actually for - a rift's colour
 * only means anything once one is torn, and a Ponder level tears none. What it teaches instead is
 * everything a pilot decides before that moment: where the module goes, that it is reading the drive
 * rather than the other way round, and what happens when the tank runs dry.
 */
public final class ModulatorScenes {

    private ModulatorScenes() {
    }

    public static void riftModulator(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("rift_modulator", "The Rift Modulator");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos drive = util.grid().at(2, 1, 2);
        BlockPos modulator = util.grid().at(1, 1, 2);
        Vec3 modulatorTop = util.vector().topOf(modulator);

        scene.world().showSection(util.select().position(drive), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(modulator), Direction.WEST);
        scene.idle(15);

        scene.overlay().showText(80)
                .text("A Rift Modulator bolted beside a Rift Drive lets its pilot choose the colour of the rift it tears, and even how it tears open")
                .pointAt(modulatorTop)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.overlay().showText(80)
                .text("It reads whichever drive it happens to be touching, and takes its own facing from that drive the moment it is placed")
                .pointAt(util.vector().blockSurface(modulator, Direction.EAST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.world().showSection(util.select().fromTo(1, 1, 0, 1, 2, 1), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(90)
                .text("It spends a trickle of Rift Essence, and only while the drive is actually running a warp - an idle drive costs it nothing")
                .pointAt(util.vector().topOf(util.grid().at(1, 2, 0)))
                .colored(PonderPalette.MEDIUM)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showControls(modulatorTop, Pointing.DOWN, 50)
                .rightClick();
        scene.idle(10);
        scene.overlay().showText(90)
                .text("Right-click for a swatch of colours - left click sets the core, right click sets the rim - a theme to match, and how strongly it all reads")
                .pointAt(modulatorTop)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("Run the tank dry, or take the module away, and the rift falls straight back to the drive's own tier colours")
                .pointAt(modulatorTop)
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);
    }
}
