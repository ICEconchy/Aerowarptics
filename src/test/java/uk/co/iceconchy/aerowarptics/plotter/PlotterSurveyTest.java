package uk.co.iceconchy.aerowarptics.plotter;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The model of the ground round a plotter's target, on the wire: it arrives whole, and a sender cannot make the
 * receiver allocate more than the widest survey the server can be configured for.
 */
class PlotterSurveyTest {

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);
    }

    private static PlotterSurvey survey(int radius) {
        int size = 2 * radius + 1;
        byte[] colours = new byte[size * size];
        short[] heights = new short[size * size];
        for (int i = 0; i < heights.length; i++) {
            colours[i] = (byte) (i % 7 == 0 ? 0 : 4 * (1 + i % 50) + 2);
            heights[i] = i % 7 == 0 ? PlotterSurvey.UNKNOWN : (short) (60 + i % 40);
        }
        return new PlotterSurvey(1234, -5678, radius, 70, colours, heights);
    }

    @Test
    void aSurveyArrivesWhole() {
        PlotterSurvey sent = survey(24);
        RegistryFriendlyByteBuf buf = buffer();
        PlotterSurvey.STREAM_CODEC.encode(buf, sent);
        PlotterSurvey received = PlotterSurvey.STREAM_CODEC.decode(buf);
        assertEquals(0, buf.readableBytes());
        assertEquals(sent.centreX(), received.centreX());
        assertEquals(sent.centreZ(), received.centreZ());
        assertEquals(sent.radius(), received.radius());
        assertEquals(sent.groundY(), received.groundY());
        assertArrayEquals(sent.colours(), received.colours());
        assertArrayEquals(sent.heights(), received.heights());
    }

    @Test
    void theWidestSurveyFitsInOnePacket() {
        PlotterSurvey widest = survey(PlotterSurvey.MAX_RADIUS);
        RegistryFriendlyByteBuf buf = buffer();
        PlotterSurvey.STREAM_CODEC.encode(buf, widest);
        assertTrue(buf.readableBytes() < 1_000_000, "a survey of " + buf.readableBytes() + " bytes will not go");
    }

    @Test
    void aDecoderRefusesARadiusPastTheWidest() {
        RegistryFriendlyByteBuf buf = buffer();
        buf.writeInt(0);
        buf.writeInt(0);
        buf.writeVarInt(PlotterSurvey.MAX_RADIUS + 1);
        buf.writeInt(64);
        assertThrows(IllegalArgumentException.class, () -> PlotterSurvey.STREAM_CODEC.decode(buf));
    }

    @Test
    void theGridIsReadFromTheNorthWest() {
        PlotterSurvey grid = survey(2);
        assertEquals(1232, grid.worldX(0));
        assertEquals(1236, grid.worldX(4));
        assertEquals(-5680, grid.worldZ(0));
        assertFalse(grid.inside(5, 0));
        assertEquals(PlotterSurvey.UNKNOWN, grid.height(0, 0), "cell 0 is unknown in the sample");
        assertFalse(grid.known(0, 0));
        assertTrue(grid.known(1, 0));
        int[] span = grid.span();
        assertTrue(span[0] <= span[1]);
    }

    @Test
    void aGridOfTheWrongSizeIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new PlotterSurvey(0, 0, 2, 64, new byte[24], new short[25]));
        short[] none = new short[9];
        Arrays.fill(none, PlotterSurvey.UNKNOWN);
        PlotterSurvey blank = new PlotterSurvey(0, 0, 1, PlotterSurvey.UNKNOWN, new byte[9], none);
        assertEquals(0.0F, blank.coverage());
        assertArrayEquals(new int[]{64, 64}, blank.span(), "an empty grid spans a guess, not nothing");
    }
}
