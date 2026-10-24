package uk.co.iceconchy.aerowarptics.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.fx.AstrolabeProjections;
import uk.co.iceconchy.aerowarptics.client.fx.FlotillaEffects;
import uk.co.iceconchy.aerowarptics.client.fx.HyperspaceStreaks;
import uk.co.iceconchy.aerowarptics.client.fx.RiftEffectManager;
import uk.co.iceconchy.aerowarptics.client.fx.RiftShimmer;
import uk.co.iceconchy.aerowarptics.client.fx.SummonBeacons;
import uk.co.iceconchy.aerowarptics.client.fx.WarpCorridorOverlay;
import uk.co.iceconchy.aerowarptics.client.fx.WarpEffects;

/**
 * Forgets everything drawn in a world the moment the client is handed a different one.
 *
 * <p>Rifts, shimmers, beacon beams, projections and fold-crossing watches are all positions in one
 * level. Each of them used to clear itself only when there was no level at all, which never happens on
 * a change of dimension - the client goes straight from one world to the next - so walking through a
 * portal mid-effect carried a rift across and stood it at the same coordinates in the Nether. That was
 * a cosmetic oddity until a warp could cross dimensions, at which point every crew arriving through the
 * fold brought the entry aperture with them.
 *
 * <p>One hook for all of them, so the rule lives in one place rather than in five classes each keeping
 * its own idea of which dimension it was in. The corridor wash is deliberately left alone: it belongs to
 * the player, not to either world, and a crossing sends it again from the far side anyway.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class LevelChangeReset {

    private LevelChangeReset() {
    }

    /** Fired when the local player is replaced, which is what a change of dimension does. */
    @SubscribeEvent
    public static void onClone(ClientPlayerNetworkEvent.Clone event) {
        reset();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
        resetSession();
    }

    public static void reset() {
        FoldCrossings.clear();
        RiftEffectManager.clear();
        RiftShimmer.clear();
        SummonBeacons.clear();
        AstrolabeProjections.clear();
        FlotillaEffects.clear();
        WarpEffects.clear();
        PlotterSurveyCache.clear();
        uk.co.iceconchy.aerowarptics.client.fx.WardEffects.clear();
        uk.co.iceconchy.aerowarptics.client.fx.WellheadSpectacle.clear();
        uk.co.iceconchy.aerowarptics.client.fx.SundererSpectacle.clear();
        PlotterCameraRig.stop();
        PlotterPreview.hide();
        // A gate's passage is within one level, so one cut off by a change of level is simply over - and
        // a player must never be left behind its veil with their keys doing nothing.
        uk.co.iceconchy.aerowarptics.client.fx.GatePassageOverlay.clear();
    }

    /**
     * Everything that belongs to the player rather than to a world, forgotten only when they leave the
     * server. The corridor wash and the starlines are deliberately carried across a change of dimension -
     * see the class doc - but a player who disconnects mid-fold must not open their next world, or next
     * server, inside a wash that nobody is ever going to send the end of.
     */
    private static void resetSession() {
        WarpCorridorOverlay.clear();
        HyperspaceStreaks.clear();
    }
}
