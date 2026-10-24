package uk.co.iceconchy.aerowarptics.client;

import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.client.fx.PlotterHologram;
import uk.co.iceconchy.aerowarptics.network.ClientboundPlotterSurveyPacket;

import java.util.HashMap;
import java.util.Map;

/**
 * The models of the ground this client has been sent for Rift Plotters: one for each plotter's table this player
 * has plotted on. Each is a vertex buffer, so a replaced or forgotten one is closed rather than dropped.
 */
@OnlyIn(Dist.CLIENT)
public final class PlotterSurveyCache {

    private static final Map<BlockPos, PlotterHologram> TABLE = new HashMap<>();

    /** Bumped whenever a table's model arrives, so an open table knows its picture has changed. */
    private static int arrivals;

    private PlotterSurveyCache() {
    }

    static void accept(ClientboundPlotterSurveyPacket packet) {
        PlotterHologram old = TABLE.put(packet.plotter().immutable(), PlotterHologram.of(packet.survey()));
        if (old != null) {
            old.close();
        }
        arrivals++;
    }

    /** The model on a plotter's table, if this player has plotted on it. */
    @Nullable
    public static PlotterHologram table(BlockPos plotter) {
        return TABLE.get(plotter);
    }

    public static int arrivals() {
        return arrivals;
    }

    public static void clear() {
        TABLE.values().forEach(PlotterHologram::close);
        TABLE.clear();
    }
}
