package uk.co.iceconchy.aerowarptics.plotter;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Rift Plotter's poses: the lens turns to its aim at a machine's pace and the short way round, the rings spin
 * up rather than jump, and nothing on it snaps at a change of state.
 */
class PlotterWorksTest {

    @Test
    void theLensIsAimedAtOnceWhenFirstToldAndTurnsAtItsPaceAfter() {
        PlotterWorks.Aim aim = new PlotterWorks.Aim();
        aim.tick(90.0D, -30.0D);
        assertEquals(Math.toRadians(90.0D), aim.yaw(1.0F), 1.0e-9D, "a machine loaded in is already aimed");
        aim.tick(0.0D, 0.0D);
        assertEquals(Math.toRadians(90.0D) - PlotterWorks.TURN_RATE, aim.yaw(1.0F), 1.0e-9D);
        for (int i = 0; i < 200; i++) {
            aim.tick(0.0D, 0.0D);
        }
        assertTrue(aim.settled(0.0D, 0.0D));
    }

    /** From 170 to -170 is twenty degrees east, not three hundred and forty west. */
    @Test
    void theTurntableTurnsTheShortWayRound() {
        PlotterWorks.Aim aim = new PlotterWorks.Aim();
        aim.tick(170.0D, 0.0D);
        double start = aim.yaw(1.0F);
        int ticks = 0;
        while (!aim.settled(-170.0D, 0.0D) && ticks < 1000) {
            aim.tick(-170.0D, 0.0D);
            ticks++;
            assertTrue(Math.abs(aim.yaw(1.0F) - start) <= Math.toRadians(20.0D) + 1.0e-9D,
                    "swung the long way: " + Math.toDegrees(aim.yaw(1.0F)));
        }
        assertTrue(ticks <= Math.ceil(Math.toRadians(20.0D) / PlotterWorks.TURN_RATE) + 1, "took " + ticks + " ticks");
    }

    @Test
    void theRingsSpinUpRatherThanJump() {
        PlotterWorks.Spin spin = new PlotterWorks.Spin();
        double wanted = PlotterWorks.spinRate(PlotterState.OPEN, 1.0D);
        spin.tick(wanted);
        assertTrue(spin.rate() < wanted * 0.1D, "jumped to speed");
        for (int i = 0; i < 400; i++) {
            spin.tick(wanted);
        }
        assertEquals(wanted, spin.rate(), wanted * 0.01D, "never got there");
    }

    @Test
    void theRingsSpinFasterTheMoreItIsCharged() {
        double last = -1.0D;
        for (double charged = 0.0D; charged <= 1.0D; charged += 0.1D) {
            double rate = PlotterWorks.spinRate(PlotterState.CHARGING, charged);
            assertTrue(rate > last);
            last = rate;
        }
        assertTrue(PlotterWorks.spinRate(PlotterState.OPEN, 1.0D) >= PlotterWorks.spinRate(PlotterState.CHARGING, 1.0D));
        assertTrue(PlotterWorks.spinRate(PlotterState.IDLE, 0.0D) < PlotterWorks.spinRate(PlotterState.CHARGING, 0.0D));
    }

    /** Every curve lands where the next state picks it up, so nothing on the machine teleports at a change. */
    @Test
    void nothingJumpsAtAChangeOfState() {
        double charged = PlotterWorks.glow(PlotterState.CHARGING, 1.0D, 400, 0.0D);
        double open = PlotterWorks.glow(PlotterState.OPEN, 1.0D, 0, 0.0D);
        assertTrue(Math.abs(open - charged) < 0.12D, "the glow jumps from " + charged + " to " + open + " at the tear");
        assertEquals(1.0D, PlotterWorks.glow(PlotterState.CLOSING, 1.0D, 0, 0.0D), 1.0e-9D);
    }

    /**
     * The dish, posed as GeckoLib poses it - the turntable turned about +y, the elevation tipped about +x under it, each
     * right-handed ({@code Axis.YP}, {@code Axis.XP}) - faces the way the plot's yaw and pitch look, the way the entry is
     * thrown. With the yaw given as it stands the dish faced the entry's mirror image, right only north and south.
     */
    @Test
    void theDishFacesTheEntryItIsAimedAt() {
        for (double yaw : new double[]{0.0D, 30.0D, 90.0D, 135.0D, 180.0D, 250.0D, -60.0D}) {
            for (double pitch : new double[]{-70.0D, -20.0D, 0.0D, 45.0D, 90.0D}) {
                Vector3f facing = new Quaternionf()
                        .rotateY((float) PlotterWorks.turntableYaw(Math.toRadians(yaw)))
                        .rotateX((float) Math.toRadians(pitch))
                        .transform(new Vector3f(0.0F, 0.0F, 1.0F));
                Vec3 wanted = PlotGeometry.direction(yaw, pitch);
                assertEquals(wanted.x, facing.x, 1.0e-5D, "yaw " + yaw + " pitch " + pitch + " east-west");
                assertEquals(wanted.y, facing.y, 1.0e-5D, "yaw " + yaw + " pitch " + pitch + " up-down");
                assertEquals(wanted.z, facing.z, 1.0e-5D, "yaw " + yaw + " pitch " + pitch + " north-south");
            }
        }
    }

    @Test
    void anglesWrapTheShortWay() {
        assertEquals(-Math.toRadians(20.0D), PlotterWorks.wrap(Math.toRadians(340.0D)), 1.0e-9D);
        assertEquals(Math.toRadians(20.0D), PlotterWorks.wrap(Math.toRadians(-340.0D)), 1.0e-9D);
        assertEquals(0.0D, PlotterWorks.smooth(-1.0D));
        assertEquals(1.0D, PlotterWorks.smooth(2.0D));
        assertEquals(0.5D, PlotterWorks.smooth(0.5D), 1.0e-9D);
    }
}
