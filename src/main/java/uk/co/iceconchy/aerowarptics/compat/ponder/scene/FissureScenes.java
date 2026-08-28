package uk.co.iceconchy.aerowarptics.compat.ponder.scene;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import uk.co.iceconchy.aerowarptics.registry.AWItems;

/**
 * The two lessons about a Rift Fissure: finding one, and closing it.
 *
 * <h2>What these scenes deliberately do not show</h2>
 * They never draw the tear. That is not a shortcut - it is the subject. A fissure renders nothing at
 * all unless the player looking at it is wearing Rift Infused Goggles, and Ponder cannot put a pair on
 * anybody, so a scene that painted a purple rift in the ruin would be teaching the one thing that is
 * not true.
 *
 * <p>Another of this mod's scenes already takes that position - a warp moves a Sable sub-level and a
 * Ponder level has no airship - and handles it the same way: teach what the player can act on and say
 * plainly what they will see when they get there. So these point at where the tear is, in words, which
 * is exactly the experience of standing in a scar without the goggles on.
 */
public final class FissureScenes {

    /** The plane the broken ring stands in, matching {@code tools/ponder_schematics.py}. */
    private static final int RING = 2;

    private FissureScenes() {
    }

    // --------------------------------------------------------------- finding

    public static void finding(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("rift_fissure", "Tears Nobody Opened");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos tear = util.grid().at(2, 2, RING);
        Vec3 middle = util.vector().centerOf(tear);
        Selection ruin = util.select().fromTo(0, 1, 1, 4, 3, RING);

        scene.world().showSection(ruin, Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(90)
                .text("Ruins generate where somebody's gate went wrong: a broken ring, a sunken vault, a survey mast")
                .pointAt(util.vector().topOf(util.grid().at(1, 2, RING)))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("A Rift Fissure hangs in the middle of one. It is a tear that never closed, still leaking Rift Essence")
                .pointAt(middle)
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("It is invisible. No model, no outline, nothing to walk into - you can stand in the ruin and never know")
                .pointAt(middle)
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showControls(middle, Pointing.DOWN, 60)
                .withItem(new ItemStack(AWItems.RIFT_GOGGLES.get()));
        scene.idle(15);
        scene.overlay().showText(90)
                .text("Wear Rift Infused Goggles. Only then is the tear drawn, and only then can you put a crosshair on it")
                .pointAt(middle)
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(80)
                .text("They are Create's goggles with a rift-ground lens, so they read every machine the engineer's pair does")
                .pointAt(util.vector().centerOf(util.grid().at(3, 3, RING)))
                .colored(PonderPalette.MEDIUM)
                .placeNearTarget();
        scene.idle(90);

        scene.overlay().showText(80)
                .text("Nothing breaks a fissure. The only way to be rid of one is to empty it")
                .pointAt(middle)
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);
    }

    // --------------------------------------------------------------- closing

    public static void closing(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("rift_fissure_closing", "Emptying a Fissure");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos tear = util.grid().at(2, 2, RING);
        BlockPos siphon = util.grid().at(1, 1, 4);
        Vec3 middle = util.vector().centerOf(tear);
        Selection ruin = util.select().fromTo(0, 1, 1, 4, 3, RING);

        scene.world().showSection(ruin, Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(70)
                .text("The tear stands here, seen only through the goggles")
                .pointAt(middle)
                .colored(PonderPalette.WHITE)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.world().showSection(util.select().position(siphon), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(90)
                .text("Stand a Spatial Siphon within a few blocks of it. Nothing else is needed - no shaft, no redstone")
                .pointAt(util.vector().topOf(siphon))
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .attachKeyFrame();
        scene.effects().indicateSuccess(siphon);
        scene.idle(100);

        scene.overlay().showText(90)
                .text("The fissure hands its essence over steadily, and the tear shrinks as it empties")
                .pointAt(middle)
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("One holds several times what a single vessel does, so pipe it away or stand more than one nearby")
                .pointAt(util.vector().blockSurface(siphon, Direction.SOUTH))
                .colored(PonderPalette.BLUE)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(80)
                .text("A ring of siphons fills no faster. The tear is the limit, not the plumbing")
                .pointAt(middle)
                .colored(PonderPalette.MEDIUM)
                .placeNearTarget();
        scene.idle(90);

        scene.overlay().showText(80)
                .text("Spent, it seals over for good. Nothing brings that one back")
                .pointAt(middle)
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);
    }
}
