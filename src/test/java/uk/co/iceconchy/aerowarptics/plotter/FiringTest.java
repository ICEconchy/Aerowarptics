package uk.co.iceconchy.aerowarptics.plotter;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a Rift Plotter fires: kept inside the server's limits, carried across the wire and the save whole, and an exit
 * speed that means exactly what the table says - kept, at rest, or so fast out of the front.
 */
class FiringTest {

    private static final Firing SAMPLE = Firing.DEFAULT.withFireOnDock(true).withLane(true).withWindow(45)
            .withExitSpeed(37.5D).withDockDelay(4).withFireOnRedstone(false).withDockSize(9.0D, 40.0D);

    @Test
    void aNewPlotterKeepsSpeedAndCatchesWhatWaits() {
        assertTrue(Firing.DEFAULT.keepsSpeed());
        assertTrue(Firing.DEFAULT.dock());
        assertFalse(Firing.DEFAULT.lane());
    }

    @Test
    void everythingIsHeldToTheServersLimits() {
        Firing wild = new Firing(true, true, true, false, 999, 5000.0D, 99, 900.0D, 900.0D)
                .clamped(60, 100.0D, 48.0D, 96.0D);
        assertEquals(60, wild.window());
        assertEquals(100.0D, wild.exitSpeed());
        assertEquals(Firing.MAX_DOCK_DELAY, wild.dockDelay());
        assertEquals(48.0D, wild.dockRadius(), "no wider than the server allows");
        assertEquals(96.0D, wild.dockDepth(), "no deeper than the server allows");
        assertEquals(1, new Firing(true, true, true, false, -5, 0.0D, -3, Firing.AUTO, Firing.AUTO)
                .clamped(60, 100.0D, 48.0D, 96.0D).window());
        assertTrue(new Firing(true, true, true, false, 5, Double.NaN, 0, Firing.AUTO, Firing.AUTO)
                        .clamped(60, 100.0D, 48.0D, 96.0D).keepsSpeed(),
                "a NaN speed keeps its own rather than flinging");
        assertTrue(new Firing(true, true, true, false, 5, -40.0D, 0, Firing.AUTO, Firing.AUTO)
                .clamped(60, 100.0D, 48.0D, 96.0D).keepsSpeed());
        assertEquals(0.0D, new Firing(true, true, true, false, 5, 0.0D, 0, Firing.AUTO, Firing.AUTO)
                        .clamped(60, 100.0D, 48.0D, 96.0D).exitSpeed(),
                "nought is a delivery, not keep");
    }

    /**
     * A dock is the size it was set, inside the server's limits and never too small to hold anything - and one nobody
     * has sized is the dock every plotter had before: as wide as suits its rift, as deep as the server says.
     */
    @Test
    void aDockIsTheSizeItWasSetOrTheSizeThatSuitsItsRift() {
        assertTrue(Firing.DEFAULT.autoDockRadius());
        assertTrue(Firing.DEFAULT.autoDockDepth());
        assertEquals(DockAlign.zoneRadius(6.0D), Firing.DEFAULT.dockRadius(6.0D), "left alone, it follows the rift");
        assertEquals(DockAlign.zoneRadius(12.0D), Firing.DEFAULT.dockRadius(12.0D), "and goes on following it");
        assertEquals(32.0D, Firing.DEFAULT.dockDepth(32.0D), "left alone, it is as deep as the server says");

        Firing sized = Firing.DEFAULT.withDockSize(9.0D, 40.0D).clamped(60, 100.0D, 48.0D, 96.0D);
        assertEquals(9.0D, sized.dockRadius(6.0D));
        assertEquals(9.0D, sized.dockRadius(12.0D), "set, it no longer follows the rift");
        assertEquals(40.0D, sized.dockDepth(32.0D));

        Firing tiny = Firing.DEFAULT.withDockSize(0.25D, 0.5D).clamped(60, 100.0D, 48.0D, 96.0D);
        assertEquals(Firing.MIN_DOCK_RADIUS, tiny.dockRadius());
        assertEquals(Firing.MIN_DOCK_DEPTH, tiny.dockDepth());

        // Only one of the two may be set, and what is not a size is no size.
        Firing half = Firing.DEFAULT.withDockSize(Firing.AUTO, 20.0D).clamped(60, 100.0D, 48.0D, 96.0D);
        assertTrue(half.autoDockRadius());
        assertEquals(20.0D, half.dockDepth(32.0D));
        Firing junk = Firing.DEFAULT.withDockSize(Double.NaN, Double.NEGATIVE_INFINITY).clamped(60, 100.0D, 48.0D, 96.0D);
        assertTrue(junk.autoDockRadius(), "a NaN radius is no radius");
        assertTrue(junk.autoDockDepth());
        // A server whose limit is under the smallest dock still leaves a dock that can hold something.
        assertEquals(Firing.MIN_DOCK_RADIUS, Firing.DEFAULT.withDockSize(30.0D, 30.0D).clamped(60, 100.0D, 0.0D, 0.0D)
                .dockRadius());
    }

    /**
     * A plotter saved before its dock could be sized has no size in it. A number that is not there reads as nought,
     * which clamps to the smallest dock there is - on every plotter already standing in every world.
     */
    @Test
    void aPlotterSavedBeforeDocksWereSizedKeepsTheDockItHad() {
        CompoundTag old = SAMPLE.save();
        old.remove("DockRadius");
        old.remove("DockDepth");
        Firing loaded = Firing.load(old).clamped(60, 100.0D, 48.0D, 96.0D);
        assertTrue(loaded.autoDockRadius(), "an old plotter's dock shrank to " + loaded.dockRadius());
        assertTrue(loaded.autoDockDepth(), "an old plotter's dock shrank to " + loaded.dockDepth());
        assertEquals(SAMPLE.dockDelay(), loaded.dockDelay(), "and the rest of it loaded");
    }

    @Test
    void firingSurvivesTheSaveAndTheWire() {
        CompoundTag tag = SAMPLE.save();
        assertEquals(SAMPLE, Firing.load(tag));
        assertEquals(Firing.DEFAULT, Firing.load(new CompoundTag()));
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY,
                ConnectionType.OTHER);
        Firing.STREAM_CODEC.encode(buf, SAMPLE);
        assertEquals(SAMPLE, Firing.STREAM_CODEC.decode(buf));
        assertEquals(0, buf.readableBytes());
    }

    /** Kept, a thing leaves at the speed it went in, turned; set, it leaves straight out of the front at that speed. */
    @Test
    void theExitSpeedIsWhatTheTableSays() {
        PlotGeometry.Frame entry = PlotGeometry.frame(new Vec3(0, 70, 0), 0, 0);
        PlotGeometry.Frame exit = PlotGeometry.frame(new Vec3(500, 90, 500), 120, 30);
        PlotGeometry.Crossing crossing = PlotGeometry.crossing(entry, 4.0D, new Vec3(0, 70, -1), new Vec3(0.3, 70, 1.5));
        PlotGeometry.Arrival arrival = PlotGeometry.map(entry, exit, crossing, new Vec3(0.3, 0, 2.5));
        assertEquals(arrival.motion(), PlotGeometry.exitMotion(arrival, exit, Firing.KEEP_SPEED));
        Vec3 set = PlotGeometry.exitMotion(arrival, exit, 40.0D);
        assertEquals(2.0D, set.length(), 1.0e-9D, "forty blocks a second is two a tick");
        assertEquals(1.0D, set.normalize().dot(exit.normal()), 1.0e-9D, "straight out of the front");
        assertEquals(0.0D, PlotGeometry.exitMotion(arrival, exit, 0.0D).length(), 1.0e-9D, "at rest");
    }

    /** A lane costs a share of its plot a second - far lanes cost more to hold - and never nothing. */
    @Test
    void aLaneCostsAShareOfItsPlotASecond() {
        assertEquals(10L, PlotCost.upkeep(1000L, 1.0D));
        assertEquals(1L, PlotCost.upkeep(10L, 1.0D), "never less than a millibucket");
        assertEquals(1L, PlotCost.upkeep(5000L, 0.0D));
        assertTrue(PlotCost.upkeep(100_000L, 1.0D) > PlotCost.upkeep(1_000L, 1.0D));
    }
}
