package uk.co.iceconchy.aerowarptics.compat.ponder.scene;

import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/**
 * The Cooling Vent, and the ice that does a little of the same job.
 *
 * <p>Like the drive's own scene this cannot show the thing it is about - a cooldown follows a warp, and a
 * Ponder level has no airship to warp. What it can show is everything a player acts on: where a vent goes,
 * which way round, what turns it, and how many are worth fitting. The steam is thrown by the scene itself
 * rather than by the vents, which blow only for a drive that has really just arrived somewhere.
 *
 * <p>The build is four coolers round one drive - three vents and a block of ice - because four is the
 * number that count, and a scene with five would be showing one that did nothing without being able to say
 * which. {@code PonderSceneryTest} holds the schematic to it.
 */
public final class CoolingScenes {

    private CoolingScenes() {
    }

    public static void coolingVent(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("cooling_vent", "Cooling a Drive");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos drive = util.grid().at(2, 1, 2);
        BlockPos first = util.grid().at(1, 1, 2);
        BlockPos second = util.grid().at(3, 1, 2);
        BlockPos third = util.grid().at(2, 2, 2);
        BlockPos ice = util.grid().at(2, 1, 1);
        Vec3 louvres = util.vector().blockSurface(first, Direction.WEST);

        scene.world().showSection(util.select().position(drive), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(2, 1, 3, 2, 1, 4), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
                .text("After every warp a Rift Drive has to cool down before it can charge for the next")
                .pointAt(util.vector().blockSurface(drive, Direction.NORTH))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.world().showSection(util.select().position(first), Direction.EAST);
        scene.idle(15);

        scene.overlay().showText(90)
                .text("A Cooling Vent shortens that. It goes against any face of the drive, with its louvres facing away and its shaft at the back")
                .pointAt(louvres)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("Set straight onto a drive that is turning, it is turned by the drive. Turn it with a wrench to feed it from a slower shaft of its own instead")
                .pointAt(util.vector().blockSurface(first, Direction.UP))
                .colored(PonderPalette.FAST)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        // The blast, then the breath after it: hard out of the louvres and tailing off.
        scene.effects().emitParticles(louvres,
                scene.effects().simpleParticleEmitter(ParticleTypes.CLOUD, new Vec3(-0.22D, 0.0D, 0.0D)), 3.0F, 30);
        scene.overlay().showText(90)
                .text("When a warp completes, a turning vent halves the cooldown and blows the heat out as steam")
                .pointAt(louvres)
                .colored(PonderPalette.GREEN)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(30);
        scene.effects().emitParticles(louvres,
                scene.effects().simpleParticleEmitter(ParticleTypes.POOF, new Vec3(-0.05D, 0.0D, 0.0D)), 0.5F, 60);
        scene.idle(70);

        scene.world().showSection(util.select().position(second), Direction.WEST);
        scene.idle(8);
        scene.world().showSection(util.select().position(third), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
                .text("Each further vent halves what the last one left")
                .pointAt(util.vector().topOf(third))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.world().showSection(util.select().position(ice), Direction.SOUTH);
        scene.idle(15);

        scene.overlay().showText(90)
                .text("A block of ice against the drive cools it too, with no shaft at all, but it only takes a tenth off")
                .pointAt(util.vector().blockSurface(ice, Direction.NORTH))
                .colored(PonderPalette.BLUE)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("Four coolers count on one drive, vents before ice. Any more do nothing, and the goggles show which")
                .pointAt(util.vector().centerOf(drive))
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("The cooldown is set as the ship arrives, by whatever is cooling the drive at that moment. A vent that has stopped turning by then does not count")
                .pointAt(louvres)
                .colored(PonderPalette.MEDIUM)
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);
    }
}
