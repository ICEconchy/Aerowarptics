package uk.co.iceconchy.aerowarptics.client;

import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.collapse.CradlePanel;

/**
 * The last Collapse Cradle panel the server sent, for the screen that is showing it. One at a time: a player only
 * ever has one panel open, and a panel for another cradle is simply not the one the screen asks for.
 */
@OnlyIn(Dist.CLIENT)
public final class CradlePanels {

    @Nullable
    private static CradlePanel latest;

    private CradlePanels() {
    }

    static void accept(CradlePanel panel) {
        latest = panel;
    }

    /** The last panel sent for the cradle at {@code pos}, or {@code null} if none has come yet. */
    @Nullable
    public static CradlePanel of(BlockPos pos) {
        CradlePanel panel = latest;
        return panel != null && panel.pos().equals(pos) ? panel : null;
    }

    /** Forgets it, when the screen closes, so a later panel never opens on a stale one. */
    public static void forget() {
        latest = null;
    }
}
