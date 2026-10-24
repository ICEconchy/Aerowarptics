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
                .text("A Rift Modulator bolted beside a Rift Drive lets its pilot choose the colour of the rift it opens, and even how the rift opens")
                .pointAt(modulatorTop)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.overlay().showText(80)
                .text("It reads whichever drive it touches, and takes its facing from that drive when it is placed")
                .pointAt(util.vector().blockSurface(modulator, Direction.EAST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.world().showSection(util.select().fromTo(1, 1, 0, 1, 2, 1), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(90)
                .text("It uses a trickle of Rift Essence, and only while the drive is warping. An idle drive costs it nothing")
                .pointAt(util.vector().topOf(util.grid().at(1, 2, 0)))
                .colored(PonderPalette.MEDIUM)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showControls(modulatorTop, Pointing.DOWN, 50)
                .rightClick();
        scene.idle(10);
        scene.overlay().showText(90)
                .text("Right-click for a choice of colours (left-click sets the core, right-click sets the rim), a matching theme, and a slider for how strong the effect is")
                .pointAt(modulatorTop)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("If it runs out of essence or is removed, the rift goes back to the drive's own tier colours")
                .pointAt(modulatorTop)
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);
    }
}
