package uk.co.iceconchy.aerowarptics.compat.ponder.scene;

import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import uk.co.iceconchy.aerowarptics.sunderer.RiftSundererBlock;

/**
 * The Rift Sunderer: the ring it stands in, what it is fed, and what it leaves.
 *
 * <p>A Ponder level draws no rift and runs no machine, so the tear is told in words and its end is shown by what
 * the world is left as: the Sunderer gone, a third of the ring burst, and a fissure nobody without goggles can see
 * hanging where the opening was - the ruin a rift scar is, built on purpose.
 */
public final class SundererScenes {

    private SundererScenes() {
    }

    public static void sunderer(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("rift_sunderer", "Tearing a Fissure Open");
        scene.configureBasePlate(0, 0, 6);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos sunderer = util.grid().at(2, 1, 2);
        BlockPos centre = util.grid().at(2, 3, 2);

        scene.world().showSection(util.select().fromTo(0, 1, 2, 4, 5, 2).substract(util.select().position(sunderer)),
                Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(90)
                .text("Build a closed ring of Rift Gate Frame standing on end, as for a Rift Gate. The opening may be three to seven blocks across")
                .pointAt(util.vector().centerOf(centre))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.world().showSection(util.select().position(sunderer), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(90)
                .text("Set a Rift Sunderer in the ring where a gate's controller would go, facing out. Its one shaft is on its back")
                .pointAt(util.vector().blockSurface(sunderer, Direction.SOUTH))
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.world().showSection(util.select().fromTo(2, 1, 0, 2, 1, 1), Direction.SOUTH);
        scene.world().modifyBlock(sunderer, state -> state.setValue(RiftSundererBlock.CHARGE, 3), false);
        scene.idle(10);
        scene.overlay().showText(90)
                .text("Fill it with four buckets of Rift Essence and turn it at 128 RPM or more. It lights as it fills")
                .pointAt(util.vector().blockSurface(sunderer, Direction.SOUTH))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.effects().indicateRedstone(sunderer);
        scene.overlay().showText(100)
                .text("A redstone signal fires it. A point searches the ring for a far side and finds none. A third of the frame lifts out and orbits a black seed, and is swallowed. Keep it turning and stand clear")
                .pointAt(util.vector().centerOf(centre))
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(110);

        scene.world().destroyBlock(sunderer);
        scene.world().destroyBlock(util.grid().at(0, 4, 2));
        scene.world().destroyBlock(util.grid().at(0, 5, 2));
        scene.world().destroyBlock(util.grid().at(1, 5, 2));
        scene.world().destroyBlock(util.grid().at(4, 2, 2));
        scene.idle(20);
        scene.overlay().showText(100)
                .text("The seed implodes. The Sunderer is used up, and a Rift Fissure hangs where the opening was. Everyone sees it born; after that, only Rift Goggles show it")
                .pointAt(util.vector().centerOf(centre))
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(110);
        scene.overlay().showText(100)
                .text("A torn fissure gives a siphon nothing and seals with no Scar Heart. Build a Rift Wellhead round it within twenty minutes or it gutters shut")
                .pointAt(util.vector().centerOf(centre))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(110);
        scene.overlay().showText(90)
                .text("Stopped while it is still reaching, it misfires and gives half the essence back. Stopped later, or broken, it ruptures and takes the ground")
                .pointAt(util.vector().blockSurface(sunderer, Direction.SOUTH))
                .colored(PonderPalette.RED)
                .placeNearTarget();
        scene.idle(100);
    }
}
