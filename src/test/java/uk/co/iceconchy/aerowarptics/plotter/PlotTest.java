package uk.co.iceconchy.aerowarptics.plotter;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A plot is clamped once, on the way in, from whatever sent it - a packet can carry a NaN as easily as a number -
 * and survives the save and the wire unchanged.
 */
class PlotTest {

    private static final Plot SAMPLE = Plot.NONE.withTarget(-12_345, 88, 67_890).withExit(70.5D, 225.0D, 33.0D)
            .withAim(false, -20.0D, 35.0D).withInput(135.0D, -40.0D, 20.0D, 7.5D).withFacing(true, -60.0D, 15.0D);

    @Test
    void aNewPlotterHasNowhereToSendAnything() {
        assertFalse(Plot.NONE.set());
        assertTrue(Plot.NONE.withTarget(1, 2, 3).set(), "setting a target sets the plot");
    }

    @Test
    void everythingIsClampedIntoItsLimits() {
        Plot wild = new Plot(0, 99_999, 0, 9_999.0D, -450.0D, -5.0D, false, 999.0D, 400.0D, 725.0D, -300.0D,
                0.5D, true, 545.0D, 200.0D, 200.0D, true).clamped(16.0D);
        assertEquals(4096, wild.targetY());
        assertEquals(Plot.MAX_DISTANCE, wild.distance());
        assertEquals(270.0D, wild.bearing(), 1.0e-9D, "-450 is 270 round the compass");
        assertEquals(0.0D, wild.height());
        assertEquals(-81.0D, wild.aimYaw(), 1.0e-9D, "999 wraps");
        assertEquals(Plot.MAX_AIM_PITCH, wild.aimPitch());
        assertEquals(5.0D, wild.inputYaw(), 1.0e-9D, "725 wraps");
        assertEquals(-90.0D, wild.inputPitch());
        assertEquals(Plot.MIN_REACH, wild.inputReach());
        assertEquals(-175.0D, wild.faceYaw(), 1.0e-9D, "545 wraps");
        assertEquals(90.0D, wild.facePitch(), "an entry tips no further than straight down");
        assertEquals(16.0D, wild.radius(), "no wider than the server allows");
    }

    @Test
    void aNaNIsNeverKept() {
        Plot nan = new Plot(0, 0, 0, Double.NaN, Double.NaN, Double.NaN, false, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, true, Double.NaN, Double.NaN, Double.NaN, true).clamped(16.0D);
        for (double value : new double[]{nan.distance(), nan.bearing(), nan.height(), nan.aimYaw(), nan.aimPitch(),
                nan.inputYaw(), nan.inputPitch(), nan.inputReach(), nan.faceYaw(), nan.facePitch(), nan.radius()}) {
            assertTrue(Double.isFinite(value), "a NaN got through: " + nan);
        }
    }

    @Test
    void aPlotSurvivesTheSave() {
        CompoundTag tag = SAMPLE.save();
        assertEquals(SAMPLE, Plot.load(tag));
        assertEquals(Plot.NONE, Plot.load(new CompoundTag()), "nothing saved is no plot");
    }

    /**
     * An entry faces straight out from the machine until it is turned, and turning it moves nothing: where it is thrown
     * is one thing and which way it faces another.
     */
    @Test
    void anEntryFacesStraightOutUntilItIsTurned() {
        Plot thrown = Plot.NONE.withInput(135.0D, -40.0D, 20.0D, 7.5D);
        assertFalse(thrown.inputTurned(), "a new plot's entry is not turned");
        assertEquals(135.0D, thrown.facingYaw());
        assertEquals(-40.0D, thrown.facingPitch());
        Plot turned = thrown.withFacing(true, -60.0D, 15.0D);
        assertEquals(-60.0D, turned.facingYaw());
        assertEquals(15.0D, turned.facingPitch());
        assertEquals(thrown.inputYaw(), turned.inputYaw(), "turning it moved it");
        assertEquals(thrown.inputPitch(), turned.inputPitch(), "turning it moved it");
        assertEquals(thrown.inputReach(), turned.inputReach(), "turning it moved it");
        // Thrown somewhere else, a turned entry still faces the way it was turned to.
        Plot moved = turned.withInput(10.0D, 5.0D, 30.0D, 7.5D);
        assertEquals(-60.0D, moved.facingYaw());
        assertEquals(15.0D, moved.facingPitch());
        // Let go of, it faces straight out again, from wherever it now is.
        Plot back = moved.withFacing(false, moved.faceYaw(), moved.facePitch());
        assertEquals(10.0D, back.facingYaw());
        assertEquals(5.0D, back.facingPitch());
    }

    /** A plot saved before an entry could be turned has no facing in it, and has to load facing as it did. */
    @Test
    void aPlotSavedBeforeEntriesTurnedLoadsFacingStraightOut() {
        CompoundTag old = SAMPLE.save();
        old.remove("InputTurned");
        old.remove("FaceYaw");
        old.remove("FacePitch");
        Plot loaded = Plot.load(old);
        assertFalse(loaded.inputTurned());
        assertEquals(SAMPLE.inputYaw(), loaded.facingYaw());
        assertEquals(SAMPLE.inputPitch(), loaded.facingPitch());
    }

    @Test
    void aPlotSurvivesTheWire() {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY,
                ConnectionType.OTHER);
        Plot.STREAM_CODEC.encode(buf, SAMPLE);
        assertEquals(SAMPLE, Plot.STREAM_CODEC.decode(buf));
        assertEquals(0, buf.readableBytes(), "the decoder read exactly what was written");
    }
}
