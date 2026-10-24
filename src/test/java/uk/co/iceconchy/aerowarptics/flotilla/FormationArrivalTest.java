package uk.co.iceconchy.aerowarptics.flotilla;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.SafeArrival;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A formation at the far end: where each escort's hull is put, that no two of them overlap, and what a
 * drive is told about the fold it has joined.
 */
class FormationArrivalTest {

    private static final double EPSILON = 1.0e-9D;

    @Test
    void anEscortComesOutWhereItsSlotPutsItWithItsOwnShape() {
        BoundingBox3d hull = new BoundingBox3d(100, 60, 200, 110, 64, 230);
        SafeArrival.Companion escort = new SafeArrival.Companion(null, new Vector3d(40, 5, -20), hull,
                new Vector3d(0, 0, -1), 12.0D);
        BoundingBox3d box = SafeArrival.companionBox(new Vector3d(1_000, 90, 5_000), escort);
        Vector3d centre = box.center(new Vector3d());
        assertEquals(1_040.0D, centre.x, EPSILON);
        assertEquals(95.0D, centre.y, EPSILON);
        assertEquals(4_980.0D, centre.z, EPSILON);
        assertEquals(hull.width(), box.width(), EPSILON, "a fold moves a hull without turning it");
        assertEquals(hull.height(), box.height(), EPSILON);
        assertEquals(hull.length(), box.length(), EPSILON);
    }

    @Test
    void theSearchReadsEveryEscortsRunOutAlongItsOwnBearing() {
        BoundingBox3d flagship = new BoundingBox3d(-10, 80, -10, 10, 90, 10);
        SafeArrival.Companion east = new SafeArrival.Companion(null, new Vector3d(50, 0, 0),
                new BoundingBox3d(0, 0, 0, 6, 4, 6), new Vector3d(1, 0, 0), 30.0D);
        List<List<BoundingBox3d>> volumes = SafeArrival.companionVolumes(flagship, List.of(east), 2.0D);
        assertEquals(1, volumes.size());
        double minX = Double.MAX_VALUE;
        for (BoundingBox3d segment : volumes.get(0)) {
            minX = Math.min(minX, segment.minX());
        }
        assertTrue(minX < 50 - 3 - 20, "the run-out reaches back along the escort's bearing, west of its slot");
        assertTrue(SafeArrival.companionVolumes(flagship, List.of(), 2.0D).isEmpty());
    }

    @Test
    void slotsKeptApartOnTheBoardAreApartAtTheFarEnd() {
        // Two escorts whose slots the Fleet Board let through: their boxes at arrival do not overlap, for a
        // flagship coming out anywhere at all.
        Formation.Footprint footprint = Formation.Footprint.ofBox(8, 6, 8);
        FormationSlot a = new FormationSlot(20, 0, 0);
        FormationSlot b = new FormationSlot(50, 0, 0);
        assertFalse(Formation.clashes(a, footprint, b, footprint, 12.0D));
        BoundingBox3d hull = new BoundingBox3d(0, 0, 0, 8, 6, 8);
        for (Vector3d flagship : List.of(new Vector3d(0, 100, 0), new Vector3d(-7_000, 64, 12_345))) {
            BoundingBox3d boxA = SafeArrival.companionBox(flagship, new SafeArrival.Companion(null,
                    new Vector3d(a.right(), a.up(), -a.ahead()), hull, new Vector3d(0, 0, -1), 0));
            BoundingBox3d boxB = SafeArrival.companionBox(flagship, new SafeArrival.Companion(null,
                    new Vector3d(b.right(), b.up(), -b.ahead()), hull, new Vector3d(0, 0, -1), 0));
            assertFalse(boxA.intersects(boxB), "two escorts would come out inside one another");
        }
    }

    @Test
    void aFoldOrderSurvivesTheSaveACrossingIsMadeFrom() {
        ResourceKey<Level> nether = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:the_nether"));
        FoldOrder order = new FoldOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), false,
                UUID.randomUUID(), nether, new BlockPos(12, 64, -900), 1_840.0D, 0.62F, "Ironreach",
                0x9B6BFF, 0x49D9C4, 3, 1.25F);
        assertEquals(order, FoldOrder.load(order.save()));
        FoldOrder anonymous = new FoldOrder(order.fold(), order.flagship(), order.coupler(), true, null, nether,
                BlockPos.ZERO, 0, 0, null, 0, 0, 0, 1);
        assertEquals(anonymous, FoldOrder.load(anonymous.save()), "a circuit's fold has nobody to thank");
        assertEquals(null, FoldOrder.load(null));
    }

    @Test
    void aCalledOffFoldCostsAnEscortNothing() {
        assertFalse(WarpFailure.FOLD_CALLED_OFF.penalised(),
                "the flagship called it off; the escort's pilot changed nothing");
        assertFalse(WarpFailure.FLOTILLA_NOT_READY.penalised());
        assertTrue(WarpFailure.CANCELLED.penalised(), "a pilot's own cancel still costs what it did");
    }

    @Test
    void theNewRefusalsCameAtTheEndSoOldOrdinalsStillMeanWhatTheyDid() {
        WarpFailure[] values = WarpFailure.values();
        // The beacon's reach refusal has since been appended after them, a tied-down ship's after that, and a
        // warded destination's after that - see RiftBeaconTest.
        // And the four a station refuses with came after all of those, with the Berth; then a rope that
        // cannot cross into another dimension.
        assertEquals(WarpFailure.DIMENSION_ROPED, values[values.length - 1]);
        assertEquals(WarpFailure.BERTH_OBSTRUCTED, values[values.length - 2]);
        assertEquals(WarpFailure.BERTH_FORBIDDEN, values[values.length - 3]);
        assertEquals(WarpFailure.BERTH_TOO_SMALL, values[values.length - 4]);
        assertEquals(WarpFailure.NO_FREE_BERTH, values[values.length - 5]);
        assertEquals(WarpFailure.DESTINATION_WARDED, values[values.length - 6]);
        assertEquals(WarpFailure.SHIP_FASTENED, values[values.length - 7]);
        assertEquals(WarpFailure.SHIP_OUT_OF_REACH, values[values.length - 8]);
        assertEquals(WarpFailure.FLOTILLA_NOT_READY, values[values.length - 9]);
        assertEquals(WarpFailure.FOLD_CALLED_OFF, values[values.length - 10]);
        assertEquals(WarpFailure.DIMENSION_TOO_TALL, values[values.length - 11]);
    }
}
