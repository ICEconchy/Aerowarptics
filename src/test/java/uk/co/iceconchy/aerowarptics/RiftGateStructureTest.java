package uk.co.iceconchy.aerowarptics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.gate.RiftGateStructure;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Finding the opening inside a ring of gate frame.
 *
 * <p>The failures worth guarding are the ones that would form something rather than refusing: a ring
 * with a gap in it, a gate buried in a hillside, a letterbox. All three would produce an aperture that
 * hides nothing properly, and the aperture is what stops a vehicle being visible at both ends of the
 * journey at once.
 */
class RiftGateStructureTest {

    /** Builds a rectangular ring of frame in the X/Y plane at a fixed z. */
    private static Set<BlockPos> ringAcrossX(int x0, int y0, int x1, int y1, int z) {
        Set<BlockPos> frame = new HashSet<>();
        for (int x = x0; x <= x1; x++) {
            frame.add(new BlockPos(x, y0, z));
            frame.add(new BlockPos(x, y1, z));
        }
        for (int y = y0; y <= y1; y++) {
            frame.add(new BlockPos(x0, y, z));
            frame.add(new BlockPos(x1, y, z));
        }
        return frame;
    }

    private static Predicate<BlockPos> frameAt(Set<BlockPos> frame) {
        return pos -> frame.contains(pos.immutable());
    }

    /** Everything that is not frame is open, which is the ordinary case of a gate standing in air. */
    private static Predicate<BlockPos> openExcept(Set<BlockPos> frame) {
        return pos -> !frame.contains(pos.immutable());
    }

    // ---------------------------------------------------------------- finding

    @Test
    void aClosedRingFormsTheOpeningInsideIt() {
        Set<BlockPos> frame = ringAcrossX(0, 64, 6, 70, 20);
        RiftGateStructure.Opening opening =
                RiftGateStructure.find(new BlockPos(0, 64, 20), frameAt(frame), openExcept(frame));

        assertNotNull(opening, "a closed ring did not form");
        assertEquals(Direction.Axis.X, opening.span(), "the gate formed in the wrong plane");
        assertEquals(Direction.Axis.Z, opening.normal(), "you would pass through the wrong way");
        assertEquals(5, opening.width());
        assertEquals(5, opening.height());
        assertEquals(25, opening.area());
        assertTrue(opening.covers(new BlockPos(3, 67, 20)), "the middle was not inside the opening");
    }

    @Test
    void theOpeningNeverIncludesTheFrameHoldingIt() {
        Set<BlockPos> frame = ringAcrossX(0, 64, 6, 70, 20);
        RiftGateStructure.Opening opening =
                RiftGateStructure.find(new BlockPos(0, 64, 20), frameAt(frame), openExcept(frame));
        assertNotNull(opening);
        for (BlockPos block : frame) {
            assertFalse(opening.covers(block), "the opening swallowed its own frame at " + block);
        }
    }

    /**
     * The gap case, and the reason the area cap is a correctness check rather than a speed one.
     *
     * <p>A ring with a block missing does not enclose anything. The fill walks out through the hole
     * into open world and keeps going until it runs past the cap, which is exactly how it should fail.
     */
    @Test
    void aRingWithAGapFormsNothing() {
        Set<BlockPos> frame = ringAcrossX(0, 64, 6, 70, 20);
        frame.remove(new BlockPos(3, 70, 20));
        assertNull(RiftGateStructure.find(new BlockPos(0, 64, 20), frameAt(frame), openExcept(frame)));
    }

    /**
     * A gate cut into a hillside is not a gate with a smaller opening.
     *
     * <p>Refusing outright is kinder than forming something the player cannot then drive through.
     */
    @Test
    void anOpeningWithSomethingInItFormsNothing() {
        Set<BlockPos> frame = ringAcrossX(0, 64, 6, 70, 20);
        BlockPos blocked = new BlockPos(3, 67, 20);
        Predicate<BlockPos> open = pos -> !frame.contains(pos.immutable()) && !pos.equals(blocked);
        assertNull(RiftGateStructure.find(new BlockPos(0, 64, 20), frameAt(frame), open));
    }

    @Test
    void aGateInTheOtherPlaneFormsToo() {
        Set<BlockPos> frame = new HashSet<>();
        for (int z = 0; z <= 6; z++) {
            frame.add(new BlockPos(9, 64, z));
            frame.add(new BlockPos(9, 70, z));
        }
        for (int y = 64; y <= 70; y++) {
            frame.add(new BlockPos(9, y, 0));
            frame.add(new BlockPos(9, y, 6));
        }
        RiftGateStructure.Opening opening =
                RiftGateStructure.find(new BlockPos(9, 64, 0), frameAt(frame), openExcept(frame));
        assertNotNull(opening, "a ring across Z did not form");
        assertEquals(Direction.Axis.Z, opening.span());
        assertEquals(Direction.Axis.X, opening.normal());
    }

    @Test
    void aControllerInTheMiddleOfASideFindsTheInsideRatherThanTheWorld() {
        // This controller has open air on both sides of it - the opening on one, the whole world on
        // the other. Seeding from the wrong one has to fail on its own rather than forming a gate
        // out of the countryside.
        Set<BlockPos> frame = ringAcrossX(0, 64, 6, 70, 20);
        RiftGateStructure.Opening opening =
                RiftGateStructure.find(new BlockPos(3, 64, 20), frameAt(frame), openExcept(frame));
        assertNotNull(opening);
        assertEquals(25, opening.area());
    }

    // ------------------------------------------------------------------ caps

    @Test
    void anOpeningTooSmallToDriveThroughFormsNothing() {
        Set<BlockPos> frame = ringAcrossX(0, 64, 2, 66, 20);
        assertEquals(1, ringOpeningArea(frame, new BlockPos(0, 64, 20)));
    }

    private static int ringOpeningArea(Set<BlockPos> frame, BlockPos controller) {
        RiftGateStructure.Opening opening =
                RiftGateStructure.find(controller, frameAt(frame), openExcept(frame));
        // A one-block hole is under MIN_AREA, so nothing should form at all.
        assertNull(opening, "a one-block hole formed a gate");
        return 1;
    }

    @Test
    void anOpeningPastTheCapFormsNothing() {
        // Twenty by twenty is well past MAX_AREA, and has to be refused rather than quietly clipped.
        Set<BlockPos> frame = ringAcrossX(0, 64, 21, 85, 20);
        assertNull(RiftGateStructure.find(new BlockPos(0, 64, 20), frameAt(frame), openExcept(frame)));
    }

    @Test
    void aLetterboxFormsNothing() {
        // Two high and twenty across passes the area cap and is still not a gate.
        Set<BlockPos> frame = ringAcrossX(0, 64, 21, 67, 20);
        assertNull(RiftGateStructure.find(new BlockPos(0, 64, 20), frameAt(frame), openExcept(frame)));
    }

    @Test
    void anOpeningExactlyAtTheCapStillForms() {
        // Fifteen by fifteen is MAX_AREA on the nose. The boundary has to be inclusive or the biggest
        // legal gate is one the player cannot actually build.
        Set<BlockPos> frame = ringAcrossX(0, 64, 16, 80, 20);
        RiftGateStructure.Opening opening =
                RiftGateStructure.find(new BlockPos(0, 64, 20), frameAt(frame), openExcept(frame));
        assertNotNull(opening, "the largest legal gate was refused");
        assertEquals(RiftGateStructure.MAX_AREA, opening.area());
    }

    // ------------------------------------------------------------- geometry

    @Test
    void theMiddleOfTheOpeningIsTheMiddleOfTheHole() {
        Set<BlockPos> frame = ringAcrossX(0, 64, 6, 70, 20);
        RiftGateStructure.Opening opening =
                RiftGateStructure.find(new BlockPos(0, 64, 20), frameAt(frame), openExcept(frame));
        assertNotNull(opening);
        Vec3 centre = opening.centre();
        assertEquals(3.5D, centre.x, 1.0e-9D);
        assertEquals(67.5D, centre.y, 1.0e-9D);
        assertEquals(20.5D, centre.z, 1.0e-9D);
    }

    /**
     * A crossing is carried over as a fraction of the opening, not as a distance.
     *
     * <p>Two gates are rarely the same size. Carrying a raw offset would put somebody who entered a
     * large gate near its edge outside a small one entirely, which is a traveller deposited in a wall.
     */
    @Test
    void aCrossingIsCarriedOverAsAFractionOfTheOpening() {
        Set<BlockPos> big = ringAcrossX(0, 64, 10, 74, 20);
        RiftGateStructure.Opening from =
                RiftGateStructure.find(new BlockPos(0, 64, 20), frameAt(big), openExcept(big));
        Set<BlockPos> small = ringAcrossX(100, 64, 104, 68, 50);
        RiftGateStructure.Opening to =
                RiftGateStructure.find(new BlockPos(100, 64, 50), frameAt(small), openExcept(small));
        assertNotNull(from);
        assertNotNull(to);

        // Hard against the top left of the big gate.
        Vec3 corner = new Vec3(from.centre().x - from.halfWidth(), from.centre().y + from.halfHeight(),
                from.centre().z);
        double across = from.acrossFraction(corner);
        double up = from.upFraction(corner);
        assertEquals(-1.0D, across, 1.0e-9D);
        assertEquals(1.0D, up, 1.0e-9D);

        // Which lands hard against the top left of the small one, not somewhere past its frame.
        Vec3 arrival = to.pointAt(across, up, 0.0D);
        assertEquals(to.centre().x - to.halfWidth(), arrival.x, 1.0e-9D);
        assertEquals(to.centre().y + to.halfHeight(), arrival.y, 1.0e-9D);
    }

    @Test
    void aFractionNeverLeavesTheOpeningHoweverFarOutTheTravellerWas() {
        Set<BlockPos> frame = ringAcrossX(0, 64, 6, 70, 20);
        RiftGateStructure.Opening opening =
                RiftGateStructure.find(new BlockPos(0, 64, 20), frameAt(frame), openExcept(frame));
        assertNotNull(opening);
        Vec3 wayOut = new Vec3(-400.0D, 900.0D, 20.0D);
        assertTrue(opening.acrossFraction(wayOut) >= -1.0D);
        assertTrue(opening.upFraction(wayOut) <= 1.0D);
    }

    @Test
    void whichSideOfThePlaneATravellerIsOnIsSigned() {
        Set<BlockPos> frame = ringAcrossX(0, 64, 6, 70, 20);
        RiftGateStructure.Opening opening =
                RiftGateStructure.find(new BlockPos(0, 64, 20), frameAt(frame), openExcept(frame));
        assertNotNull(opening);
        // Centre sits at z = 20.5, so either side of it has to come back with opposite signs or a
        // crossing can never be detected at all.
        assertTrue(opening.distanceToPlane(new Vec3(3.5D, 67.5D, 19.0D)) < 0.0D);
        assertTrue(opening.distanceToPlane(new Vec3(3.5D, 67.5D, 22.0D)) > 0.0D);
    }

    @Test
    void theSameRingAlwaysFormsTheSameGate() {
        Set<BlockPos> frame = ringAcrossX(0, 64, 6, 70, 20);
        RiftGateStructure.Opening first =
                RiftGateStructure.find(new BlockPos(0, 64, 20), frameAt(frame), openExcept(frame));
        for (int repeat = 0; repeat < 8; repeat++) {
            RiftGateStructure.Opening again =
                    RiftGateStructure.find(new BlockPos(0, 64, 20), frameAt(frame), openExcept(frame));
            assertNotNull(again);
            assertEquals(first.span(), again.span());
            assertEquals(first.area(), again.area());
            assertEquals(first.centre(), again.centre());
        }
    }
}
