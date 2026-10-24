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
import uk.co.iceconchy.aerowarptics.astrolabe.AstrolabeBlock;
import uk.co.iceconchy.aerowarptics.registry.AWItems;

/**
 * Scenes for the three machines that are simply placed: the siphon, the anchor and the chart table.
 *
 * <p>The text in here is the mod's only documentation, so it is written to be true rather than
 * enthusiastic. Where a number is quoted it is the shipped default, and the config can move it - so
 * the numbers quoted are the ones a player would notice being wrong (four buckets, three by three)
 * rather than the ones they would never check.
 */
public final class MachineScenes {

    private MachineScenes() {
    }

    // ---------------------------------------------------------------- siphon

    public static void spatialSiphon(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("spatial_siphon", "Collecting Rift Essence");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos siphon = util.grid().at(1, 1, 2);
        Selection drain = util.select().fromTo(2, 1, 2, 3, 2, 2);

        scene.world().showSection(util.select().position(siphon), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(70)
                .text("A Spatial Siphon is built into an airship, not onto the ground")
                .pointAt(util.vector().topOf(siphon))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showText(90)
                .text("Passing a hull through folded space sheds some Rift Essence. The siphons aboard catch it")
                .pointAt(util.vector().centerOf(siphon))
                .colored(PonderPalette.OUTPUT)
                .placeNearTarget()
                .attachKeyFrame();
        scene.effects().indicateSuccess(siphon);
        scene.idle(100);

        scene.overlay().showText(80)
                .text("One catch per jump, shared between every siphon aboard. More siphons hold more, but do not catch more. Longer jumps give more")
                .pointAt(util.vector().centerOf(siphon))
                .placeNearTarget();
        scene.idle(90);

        scene.world().showSection(drain, Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
                .text("Four buckets fit inside. Pipes can draw the essence off into storage")
                .pointAt(util.vector().centerOf(util.grid().at(3, 2, 2)))
                .colored(PonderPalette.BLUE)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.overlay().showText(70)
                .text("Rift Essence is what dials a Rift Gate")
                .pointAt(util.vector().centerOf(util.grid().at(3, 1, 2)))
                .colored(PonderPalette.BLUE)
                .placeNearTarget();
        scene.idle(80);
    }

    // ---------------------------------------------------------------- anchor

    public static void warpAnchor(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("warp_anchor", "Marking a Destination");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos anchor = util.grid().at(2, 1, 2);
        scene.world().showSection(util.select().position(anchor), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(70)
                .text("A Warp Anchor is somewhere an airship can be sent")
                .pointAt(util.vector().topOf(anchor))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(anchor), Pointing.DOWN, 50)
                .rightClick();
        scene.idle(10);
        scene.overlay().showText(80)
                .text("Right-click it to give it a name, and to say whether other players may travel to it")
                .pointAt(util.vector().topOf(anchor))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.overlay().showText(90)
                .text("The world keeps the record, not the block. An anchor stays a valid destination even while its chunks are unloaded")
                .pointAt(util.vector().centerOf(anchor))
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(80)
                .text("Leave room around it. An arriving hull has to fit, and the drive will refuse an anchor whose space is blocked")
                .pointAt(util.vector().topOf(anchor))
                .colored(PonderPalette.RED)
                .placeNearTarget();
        scene.idle(90);
    }

    // ----------------------------------------------------------------- probe

    public static void riftProbe(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("rift_probe", "Finding Somewhere New");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos probe = util.grid().at(1, 1, 2);
        Selection supply = util.select().fromTo(2, 1, 2, 3, 2, 2);

        scene.world().showSection(util.select().position(probe), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(90)
                .text("Every other destination is somewhere a player has placed an anchor. A Rift Probe is how you reach ground nobody has visited")
                .pointAt(util.vector().topOf(probe))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(80)
                .text("Right-click it, pick a direction on the dial and a distance on the slider, then scan ahead")
                .pointAt(util.vector().topOf(probe))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.world().showSection(supply, Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
                .text("Rift Essence pays for each scan, and scanning further costs more. A Spatial Siphon is how you collect it")
                .pointAt(util.vector().centerOf(util.grid().at(3, 1, 2)))
                .colored(PonderPalette.BLUE)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.effects().indicateSuccess(probe);
        scene.overlay().showText(90)
                .text("The wait is the world being generated. A scan aimed at unexplored ground has to generate that ground before it can map it")
                .pointAt(util.vector().centerOf(probe))
                .colored(PonderPalette.MEDIUM)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("What comes back is the same chart the Astrolabe draws for an anchor, so a blind jump is judged on the same evidence as any other")
                .pointAt(util.vector().topOf(probe))
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(80)
                .text("Send the ship there and the Rift Drive flies it. There is no anchor at the far end yet, so bring one if you want to return")
                .pointAt(util.vector().centerOf(probe))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);
    }

    // ------------------------------------------------------------- astrolabe

    public static void astrolabe(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("astrolabe", "Charting a Course");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        Selection table = util.select().fromTo(1, 1, 1, 3, 1, 3);
        BlockPos centre = util.grid().at(2, 1, 2);
        Vec3 above = util.vector().topOf(centre);

        scene.world().showSection(util.select().position(util.grid().at(1, 1, 1)), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(70)
                .text("Astrolabe blocks are laid flat, in a square up to three by three")
                .pointAt(above)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(20);
        scene.world().showSection(table, Direction.DOWN);
        scene.idle(60);

        // Forming is a server decision, so the scene performs it rather than waiting for one.
        scene.world().modifyBlocks(table, state -> state.setValue(AstrolabeBlock.FORMED, true), false);
        scene.effects().indicateSuccess(centre);
        scene.idle(10);
        scene.overlay().showText(70)
                .text("Complete the square and the table forms itself")
                .pointAt(above)
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showControls(above, Pointing.DOWN, 50)
                .rightClick()
                .withItem(new ItemStack(AWItems.ASTROLABE.get()));
        scene.idle(10);
        scene.overlay().showText(90)
                .text("Right-click for the chart: every anchor within reach, drawn over a survey of the ground around it")
                .pointAt(above)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(80)
                .text("A table can ride aboard the ship, so a course can be set without landing first")
                .pointAt(util.vector().centerOf(centre))
                .placeNearTarget();
        scene.idle(90);
    }

    /**
     * The Rift Chute: two of them, a belt into one and a belt out of the other.
     *
     * <p>Both stand on the ground. A Ponder level has no airship in it, and the two things a player
     * gets wrong about a chute - that it has to be bound, and that the sending end pays - need no
     * ship to show.
     */
    public static void riftChute(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("rift_chute", "Moving Cargo Across a Rift");
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos sender = util.grid().at(3, 1, 2);
        BlockPos receiver = util.grid().at(6, 1, 2);
        Selection intake = util.select().fromTo(1, 1, 2, 2, 1, 2);
        Selection outfeed = util.select().fromTo(7, 1, 2, 8, 1, 2);
        Selection supply = util.select().fromTo(3, 1, 0, 3, 1, 1);

        scene.world().showSection(util.select().position(sender), Direction.DOWN);
        scene.world().showSection(util.select().position(receiver), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(90)
                .text("A Rift Chute hands items to another chute, however far apart they are - including one bolted to an airship")
                .pointAt(util.vector().topOf(sender))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.world().showSection(intake, Direction.DOWN);
        scene.world().showSection(outfeed, Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
                .text("Fit a funnel to any face and feed it from a belt. A chute is an inventory, so it does not sit directly on the belt")
                .pointAt(util.vector().topOf(util.grid().at(2, 1, 2)))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.overlay().showText(90)
                .text("Right-click a chute to name it and pair it with another. Items travel both ways along a pair")
                .pointAt(util.vector().topOf(sender))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.world().showSection(supply, Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(90)
                .text("Whichever end sends pays the Rift Essence, per item and only when one crosses. A receiving chute needs no essence of its own")
                .pointAt(util.vector().topOf(util.grid().at(3, 1, 1)))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("Nothing is ever lost. A full far end, an unloaded chunk or a ship mid-warp all make items wait in the chute instead")
                .pointAt(util.vector().topOf(receiver))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);
    }
}
