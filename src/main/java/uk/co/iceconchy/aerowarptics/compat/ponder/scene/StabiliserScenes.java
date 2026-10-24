package uk.co.iceconchy.aerowarptics.compat.ponder.scene;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * The Spatial Stabiliser.
 *
 * <p>The other scenes here that are about flight say plainly that they cannot show it - a warp moves a
 * Sable sub-level and a Ponder level has none. This one can show its half of it, because what a
 * stabiliser changes is not where a hull goes but how sharply it gets there, and a scene can lift the
 * machine off the plate twice: once as an unstabilised ship answers its balloons, and once as a
 * stabilised one does. Nothing in the picture is a real airship, and the text never claims one.
 *
 * <p>There is no shaft in this scene, unlike every other drive scene. A stabiliser never asks whether
 * its drive is turning - only that it is beside one - and drawing power it does not need would invite
 * exactly the wrong guess.
 */
public final class StabiliserScenes {

    /** How far the demonstration lifts the machine. Far enough to read, short enough to come back. */
    private static final double LIFT = 1.5D;

    private StabiliserScenes() {
    }

    public static void spatialStabiliser(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("spatial_stabiliser", "Steadying a Ship");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos drive = util.grid().at(2, 1, 2);
        BlockPos stabiliser = util.grid().at(1, 1, 2);
        BlockPos lever = util.grid().at(1, 2, 2);
        Vec3 top = util.vector().topOf(stabiliser);
        Vec3 leverTop = util.vector().topOf(lever);
        // Two overlapping selections of the same small build. The switch is the pair the scene throws -
        // the lever and the machine it stands on, both carrying `powered`. The machine is everything
        // that has to leave the ground together, the lever included, or the demonstration would lift a
        // stabiliser out from under its own switch.
        Selection theSwitch = util.select().fromTo(1, 1, 2, 1, 2, 2);
        Selection machine = util.select().fromTo(1, 1, 2, 2, 2, 2);

        scene.world().showSection(util.select().position(drive), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(theSwitch, Direction.WEST);
        scene.idle(15);

        scene.overlay().showText(90)
                .text("A Spatial Stabiliser is a Rift Drive module. It goes against any face of a drive that is built into an airship")
                .pointAt(util.vector().blockSurface(stabiliser, Direction.EAST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(80)
                .text("It has no screen and nothing to set. A redstone signal switches it on, and losing the signal switches it off")
                .pointAt(leverTop)
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        // Off first, so the thing being fixed is shown before the fix.
        ElementLink<WorldSectionElement> hull = scene.world().makeSectionIndependent(machine);
        scene.overlay().showText(90)
                .text("Left alone, a hull does what its machinery tells it, as fast as the physics allows. Light the burners under a balloon and the ship leaps upward")
                .pointAt(util.vector().centerOf(drive))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(20);
        scene.world().moveSection(hull, util.vector().of(0, LIFT, 0), 4);
        scene.idle(10);
        scene.world().moveSection(hull, util.vector().of(0, -LIFT, 0), 4);
        scene.idle(40);

        scene.overlay().showControls(leverTop, Pointing.DOWN, 40)
                .rightClick();
        scene.idle(10);
        scene.world().toggleRedstonePower(theSwitch);
        scene.effects().indicateRedstone(lever);
        scene.idle(15);

        scene.overlay().showText(90)
                .text("Switched on, it cancels most of each tick's change in motion. The same push now eases the ship upward instead")
                .pointAt(top)
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(20);
        scene.world().moveSection(hull, util.vector().of(0, LIFT, 0), 40);
        scene.idle(50);
        scene.world().moveSection(hull, util.vector().of(0, -LIFT, 0), 40);
        scene.idle(45);

        scene.overlay().showText(90)
                .text("The ship still goes wherever it is pushed. It just responds as though it were several times heavier")
                .pointAt(util.vector().centerOf(drive))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("A blow too hard to come from machinery, such as a hillside or a hard landing, passes through untouched, so the ship is never cushioned into the ground it just hit")
                .pointAt(top)
                .colored(PonderPalette.MEDIUM)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("While its drive is flying a warp, it stands by. The flight was planned in full before the rift opened, and resisting it would only drag the ship off course")
                .pointAt(util.vector().blockSurface(drive, Direction.SOUTH))
                .colored(PonderPalette.MEDIUM)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("A second stabiliser aboard makes no difference. One holds the ship and the rest stand by. The goggles show which is which")
                .pointAt(top)
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);
    }
}
