package uk.co.iceconchy.aerowarptics.compat.ponder.scene;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.phys.Vec3;
import uk.co.iceconchy.aerowarptics.registry.AWItems;
import uk.co.iceconchy.aerowarptics.rifting.RiftBrazierBlock;
import uk.co.iceconchy.aerowarptics.stabiliser.SpatialStabiliserBlock;

/**
 * The crafting layer's machines: rifting at a brazier, and folding and the collapse at the cradle.
 *
 * <p>Like the drive and fissure scenes, these teach what a player can act on and say plainly what they will
 * see when they get there. A Ponder level draws no rift - the brazier's and the cradle's are drawn by the
 * client in the world - so those are told in words rather than shown as something they are not.
 */
public final class CraftScenes {

    private CraftScenes() {
    }

    // --------------------------------------------------------------- rifting

    public static void rifting(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("rift_brazier", "Rifting at an Open Rift");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos brazier = util.grid().at(2, 1, 2);
        BlockPos lever = util.grid().at(2, 1, 1);
        BlockPos fan = util.grid().at(0, 1, 2);
        BlockPos depot = util.grid().at(4, 1, 2);

        scene.world().showSection(util.select().position(brazier), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(80)
                .text("A Rift Brazier burns with rift fire while it has a redstone signal and essence")
                .pointAt(util.vector().topOf(brazier))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.world().showSection(util.select().position(lever), Direction.DOWN);
        scene.idle(10);
        scene.world().modifyBlock(lever, state -> state.setValue(LeverBlock.POWERED, true), false);
        scene.world().modifyBlock(brazier, state -> state.setValue(RiftBrazierBlock.POWERED, true)
                .setValue(RiftBrazierBlock.LIT, true), false);
        scene.effects().indicateRedstone(lever);
        scene.overlay().showText(80)
                .text("Give it a redstone signal. Lit, it burns two millibuckets of Rift Essence a second, whatever is passing")
                .pointAt(util.vector().blockSurface(brazier, Direction.WEST))
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.world().showSection(util.select().position(fan), Direction.DOWN);
        scene.world().showSection(util.select().position(depot), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(80)
                .text("Blow items through it with an Encased Fan, the same way you would wash them")
                .pointAt(util.vector().topOf(fan))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(30);
        scene.world().createItemEntity(util.vector().centerOf(1, 2, 2), new Vec3(0.12D, 0.02D, 0.0D),
                new ItemStack(AWItems.RIFT_LENS.get()));
        scene.idle(40);
        scene.world().modifyEntities(ItemEntity.class, item -> item.setItem(new ItemStack(AWItems.OPENED_RIFT_LENS.get())));
        scene.effects().indicateSuccess(brazier);
        scene.idle(30);

        scene.overlay().showText(90)
                .text("A Rift Lens comes out as an Opened Rift Lens. Every drive from the Mk II up is built with them")
                .pointAt(util.vector().topOf(depot))
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("Any open rift will do: a Rift Fissure is free for as long as it stays open, and so is an open Rift Gate's pane")
                .pointAt(util.vector().centerOf(brazier))
                .colored(PonderPalette.MEDIUM)
                .placeNearTarget();
        scene.idle(100);
    }

    // --------------------------------------------------------------- folding

    public static void folding(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("cradle_folding", "Folding at the Cradle");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos cradle = util.grid().at(3, 1, 3);
        Selection machine = util.select().fromTo(2, 1, 2, 4, 3, 4);
        BlockPos side = util.grid().at(3, 1, 2);

        scene.world().showSection(machine, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(90)
                .text("A Collapse Cradle can also fold items: it is simpler than a collapse and takes a few seconds")
                .pointAt(util.vector().centerOf(cradle).add(0.0D, 1.1D, 0.0D))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showControls(util.vector().blockSurface(side, Direction.NORTH), Pointing.DOWN, 60).rightClick()
                .withItem(new ItemStack(AWItems.WARP_CRYSTAL.get(), 16));
        scene.idle(10);
        scene.overlay().showText(90)
                .text("Put a stack of up to sixteen in its arms. The folding ingredient, such as a Shulker Shell, goes in its cup")
                .pointAt(util.vector().blockSurface(side, Direction.NORTH))
                .placeNearTarget();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("Every craft uses Rift Essence from its tank, and the shaft has to be turning")
                .pointAt(util.vector().blockSurface(side, Direction.NORTH))
                .colored(PonderPalette.BLUE)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.effects().indicateRedstone(side);
        scene.overlay().showText(100)
                .text("Press Begin on its panel, or give it a redstone pulse. The arms lift the stack up to a small rift, which folds it")
                .pointAt(util.vector().centerOf(cradle).add(0.0D, 1.3D, 0.0D))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(110);

        scene.overlay().showText(90)
                .text("The result comes back down into the arms, and waits in the tray for you or a funnel to collect it")
                .pointAt(util.vector().centerOf(cradle))
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("A Warp Crystal folds into a Folded Warp Crystal. That plus a Shulker Shell folds into a Pearlescent Warp Crystal. A Compass folds into a Ship's Compass")
                .pointAt(util.vector().centerOf(cradle))
                .colored(PonderPalette.MEDIUM)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(110);

        scene.overlay().showText(90)
                .text("A Ship's Compass comes out unbound. Sneak and use it on a Rift Drive to bind it to that ship")
                .pointAt(util.vector().centerOf(cradle))
                .colored(PonderPalette.GREEN)
                .placeNearTarget();
        scene.idle(100);
    }

    // -------------------------------------------------------------- collapse

    public static void collapse(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("collapse_cradle", "The Collapse");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos cradle = util.grid().at(3, 1, 3);
        // The machine is three blocks each way round its master; the crown is what a pointer points at.
        Selection machine = util.select().fromTo(2, 1, 2, 4, 3, 4);
        BlockPos crown = util.grid().at(3, 3, 3);
        BlockPos side = util.grid().at(3, 1, 2);
        Selection depots = util.select().fromTo(1, 1, 1, 5, 1, 5).substract(util.select().fromTo(2, 1, 2, 4, 1, 4));
        BlockPos[] stabilisers = {util.grid().at(0, 1, 3), util.grid().at(6, 1, 3), util.grid().at(3, 1, 0),
                util.grid().at(3, 1, 6)};

        scene.world().showSection(machine, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(90)
                .text("A Collapse Cradle collapses a Singularity Core out of a Rift Core and a Scar Heart")
                .pointAt(util.vector().topOf(crown))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(100)
                .text("It is an old machine, rebuilt: three blocks each way, set down whole where you aim, and too heavy to move")
                .pointAt(util.vector().blockSurface(side, Direction.NORTH))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(110);

        scene.overlay().showText(90)
                .text("Its rings, the Relic Armillary, can only be found: look in the barrels in rift scar ruins")
                .pointAt(util.vector().centerOf(cradle).add(0.0D, 1.1D, 0.0D))
                .colored(PonderPalette.MEDIUM)
                .placeNearTarget();
        scene.idle(100);

        scene.overlay().showControls(util.vector().blockSurface(side, Direction.NORTH), Pointing.DOWN, 50).rightClick()
                .withItem(new ItemStack(AWItems.SCAR_HEART.get()));
        scene.idle(10);
        scene.overlay().showText(80)
                .text("The Scar Heart comes only from a sealed Rift Fissure. Without one, the core has nothing to collapse into")
                .pointAt(util.vector().blockSurface(side, Direction.NORTH))
                .colored(PonderPalette.RED)
                .placeNearTarget();
        scene.idle(90);

        scene.world().showSection(depots, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(90)
                .text("The offerings stand on Create Depots round it. A Mechanical Arm can load them")
                .pointAt(util.vector().topOf(util.grid().at(1, 1, 3)))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("Pipe Rift Essence into its bottom layer, and turn it with a shaft into the middle of any side of its floor or up under its middle, at a Singularity drive's own speed")
                .pointAt(util.vector().blockSurface(side, Direction.NORTH))
                .colored(PonderPalette.BLUE)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        for (BlockPos stabiliser : stabilisers) {
            scene.world().showSection(util.select().position(stabiliser), Direction.DOWN);
        }
        scene.idle(15);
        for (BlockPos stabiliser : stabilisers) {
            scene.world().modifyBlock(stabiliser, state -> state.setValue(SpatialStabiliserBlock.POWERED, true), false);
        }
        scene.overlay().showText(100)
                .text("Switched-on Spatial Stabilisers steady the collapse. Space them evenly: a lopsided cage wobbles")
                .pointAt(util.vector().topOf(stabilisers[0]))
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(110);

        scene.effects().indicateRedstone(side);
        scene.overlay().showText(100)
                .text("A redstone pulse at any side begins it. Anything missing refuses it, before anything is spent")
                .pointAt(util.vector().blockSurface(side, Direction.NORTH))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(110);

        scene.overlay().showText(100)
                .text("The arms lift the core into the rings, the iris opens and the rift folds into a hole. Stand back: it drags loose objects in as it winds up")
                .pointAt(util.vector().centerOf(cradle).add(0.0D, 1.3D, 0.0D))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(110);

        scene.overlay().showText(110)
                .text("Steady, the core settles back into the arms. Unsteady, it is flung clear. Very unsteady, it spits the core and offerings back out")
                .pointAt(util.vector().topOf(crown))
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget()
                .attachKeyFrame();
        scene.effects().indicateSuccess(crown);
        scene.idle(120);

        scene.overlay().showText(90)
                .text("The collapse knocks back everything near it, and an unsteady one hurts whoever stands too close")
                .pointAt(util.vector().topOf(crown))
                .colored(PonderPalette.RED)
                .placeNearTarget();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("However it ends, only the Scar Heart and the essence are used up")
                .pointAt(util.vector().centerOf(cradle))
                .colored(PonderPalette.MEDIUM)
                .placeNearTarget();
        scene.idle(100);
    }
}
