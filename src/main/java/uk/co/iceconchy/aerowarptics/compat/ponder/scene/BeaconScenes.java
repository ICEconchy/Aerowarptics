package uk.co.iceconchy.aerowarptics.compat.ponder.scene;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import uk.co.iceconchy.aerowarptics.registry.AWItems;

/**
 * The Rift Beacon: binding it to a drive, and calling the ship down.
 *
 * <p>Like the warp scenes, this one has no airship in it, because a Ponder level cannot hold a Sable
 * sub-level. What it can teach is the part that is pure gesture and is explained nowhere else: that
 * there are two clicks, that one of them is behind sneak and why, and that the far one is a ray from
 * the eye rather than a reach.
 *
 * <p>The order the scene puts them in is the order they have to happen in. Binding is where the
 * permission question is asked - standing on the deck, with the machine in front of you - and the
 * summon later asks nothing of the sort, which is exactly what the item is for.
 */
public final class BeaconScenes {

    private BeaconScenes() {
    }

    public static void summoning(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("rift_beacon", "Calling a Ship Down");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos drive = util.grid().at(1, 1, 1);
        Vec3 face = util.vector().blockSurface(drive, Direction.EAST);
        Vec3 landing = util.vector().topOf(util.grid().at(3, 0, 3));
        ItemStack beacon = new ItemStack(AWItems.RIFT_BEACON.get());

        scene.world().showSection(util.select().position(drive), Direction.DOWN);
        scene.idle(15);
        scene.world().showSection(util.select().fromTo(1, 1, 2, 1, 1, 5), Direction.NORTH);
        scene.idle(20);

        scene.overlay().showText(80)
                .text("A Rift Beacon calls a ship you are not standing on, which is the one thing a course cannot do")
                .pointAt(face)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.overlay().showControls(face, Pointing.RIGHT, 60)
                .rightClick()
                .whileSneaking()
                .withItem(beacon);
        scene.idle(15);
        scene.overlay().showText(90)
                .text("Sneak and right-click a Rift Drive to bind the beacon to it. A plain click would only open the console")
                .pointAt(face)
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("This is where you are checked. A beacon cannot be bound to a ship you could not have commanded by hand")
                .pointAt(util.vector().topOf(drive))
                .colored(PonderPalette.MEDIUM)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showControls(landing, Pointing.DOWN, 60)
                .rightClick()
                .withItem(beacon);
        scene.idle(15);
        scene.overlay().showText(90)
                .text("Then aim anywhere in reach of the eye and right-click. The ship is called to whatever the crosshair is on")
                .pointAt(landing)
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget()
                .attachKeyFrame();
        scene.effects().indicateSuccess(util.grid().at(3, 1, 3));
        scene.idle(100);

        scene.overlay().showText(90)
                .text("The drive still has to agree: turning fast enough, charged, in range, and with room for the hull to arrive")
                .pointAt(util.vector().blockSurface(util.grid().at(1, 1, 3), Direction.EAST))
                .colored(PonderPalette.FAST)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(80)
                .text("Refused, it says which of those failed, in the same words the drive's own console uses")
                .pointAt(face)
                .colored(PonderPalette.RED)
                .placeNearTarget();
        scene.idle(90);

        scene.overlay().showText(80)
                .text("Take the drive apart and the beacon is pointing at nothing. Bind it again to whatever you build next")
                .pointAt(util.vector().topOf(drive))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);
    }
}
