package uk.co.iceconchy.aerowarptics.client;

import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.plotter.Firing;
import uk.co.iceconchy.aerowarptics.plotter.Plot;

/**
 * The entry a player is setting at a Rift Plotter's table, before the server has it: what the hologram in the world
 * draws, so it moves under the slider rather than a round trip later. One at a time - there is one table open.
 */
@OnlyIn(Dist.CLIENT)
public final class PlotterPreview {

    @Nullable
    private static BlockPos plotter;
    private static Plot plot = Plot.NONE;
    private static Firing firing = Firing.DEFAULT;

    private PlotterPreview() {
    }

    public static void show(BlockPos at, Plot shown, Firing fired) {
        plotter = at.immutable();
        plot = shown;
        firing = fired;
    }

    public static void hide() {
        plotter = null;
    }

    @Nullable
    public static BlockPos plotter() {
        return plotter;
    }

    public static Plot plot() {
        return plot;
    }

    public static Firing firing() {
        return firing;
    }
}
