package uk.co.iceconchy.aerowarptics.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.network.ClientboundCorridorPacket;

/**
 * What the warp corridor does to the camera.
 *
 * <p>Only the crew of the ship in the corridor get this, and they get it because the <em>server</em>
 * told them so - it enumerates who is aboard and sends the cue to exactly those players. The client
 * does not try to work out whether it is aboard; that guess is what made the effect fail to appear
 * for players who were standing on a deck rather than riding something.
 *
 * <h2>Why there is nothing here but a lens</h2>
 * This used to tint. It drove the fog colour and pulled the fog planes in to a few blocks, and it
 * painted vignettes and a full-frame wash over the top through the GUI layer. All of that had one
 * consequence nobody wanted: <em>it coloured the blocks</em>. Fog blends world geometry toward the fog
 * colour by distance, so the deck underfoot and the hull the player was standing on went purple and
 * dark; a full-screen {@code fill} tints literally every pixel, blocks included. Between them a warp
 * read as the ship being dyed rather than as the ship travelling.
 *
 * <p>Neither was carrying the scene anyway. When they were written the ship ran a lane a thousand
 * blocks up with the whole world visible below it, and screen space was the only thing selling the
 * journey. The ship now flies down a lit bore with ribs sweeping past it, debris in the air, lightning
 * across it and a band of light running its length - real geometry, at a real distance, moving at the
 * real speed. That is a far better motion cue than anything paintable in screen space, and painting
 * over the top of it was only ever hiding the thing worth seeing.
 *
 * <p>So what is left is the one effect that changes how the world is <em>seen</em> without changing
 * what colour any of it is: the field of view stretches with speed. Nothing here writes a pixel.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class WarpCorridorOverlay {

    private static final int FADE_TICKS = 10;

    /** How much wider the view opens at full strength. A lens, not a filter. */
    private static final double FOV_STRETCH = 0.35D;

    /** Ticks left before the effect lets go on its own, in case the exit cue never arrives. */
    private static int remainingTicks;
    private static float intensity;
    private static float lastIntensity;

    /**
     * How strongly this warp should read - a Modulator's choice, or 1.0. Distinct from
     * {@link #intensity} above, which is this effect's own fade progress and has nothing to do with
     * the warp it is fading in for; this is a flat multiplier applied on top of that fade.
     */
    private static float strength = 1.0F;

    private WarpCorridorOverlay() {
    }

    /**
     * Applies a server cue.
     *
     * <p>The cue also carries the rift's colour. Nothing reads it any more - see the class note on why
     * this no longer tints anything - and it is left on the packet rather than stripped out because it
     * is a true description of the corridor being entered, and the next thing that wants to draw one
     * will want it.
     */
    public static void accept(ClientboundCorridorPacket packet) {
        if (packet.active()) {
            remainingTicks = Math.max(remainingTicks, packet.duration() + FADE_TICKS);
            strength = packet.intensity();
        } else {
            // Let it fall away rather than cutting out.
            remainingTicks = Math.min(remainingTicks, FADE_TICKS);
        }
    }

    public static void clear() {
        remainingTicks = 0;
        intensity = 0.0F;
        lastIntensity = 0.0F;
        strength = 1.0F;
    }

    private static float intensity(float partialTick) {
        return Mth.lerp(partialTick, lastIntensity, intensity) * strength;
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clear();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }

        if (remainingTicks > 0) {
            remainingTicks--;
        }
        lastIntensity = intensity;

        float target = remainingTicks > FADE_TICKS ? 1.0F : 0.0F;
        intensity += (target - intensity) / FADE_TICKS;
        if (intensity < 0.004F) {
            intensity = 0.0F;
        }
    }

    // ---------------------------------------------------------------- render

    /**
     * Speed stretches the view.
     *
     * <p>The only thing left, and the only one of the old effects that was ever honest: it changes the
     * lens rather than the picture, so the bore, the debris and the ship all stay exactly the colours
     * they are and simply arrive faster.
     */
    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        float amount = intensity((float) event.getPartialTick());
        if (amount > 0.0F) {
            event.setFOV(event.getFOV() * (1.0D + FOV_STRETCH * amount));
        }
    }
}
