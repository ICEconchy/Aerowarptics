package uk.co.iceconchy.aerowarptics.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.network.ClientboundCorridorPacket;

/**
 * What the warp corridor looks like from the deck.
 *
 * <p>Only the crew of the ship in the corridor get this, and they get it because the <em>server</em>
 * told them so - it enumerates who is aboard and sends the cue to exactly those players. The client
 * does not try to work out whether it is aboard; that guess is what made the effect fail to appear
 * for players who were standing on a deck rather than riding something.
 *
 * <p>It is deliberately not a particle effect. At corridor speed a particle crosses the view in a
 * single frame, so the fold is drawn in screen space: the world outside drowns in the rift's colour,
 * the view closes in, and the field of view stretches.
 *
 * <p>Nothing here points anywhere. It used to: streaks raced outwards from the centre of the screen,
 * which reads as "you are travelling that way" - except the centre of the screen is wherever the
 * player happens to be looking, so turning your head turned the direction of travel with it. An effect
 * that lies about which way the ship is going is worse than no effect at all.
 *
 * <p>It is also deliberately restrained now, because it is no longer carrying the scene. When this was
 * written the ship ran a lane a thousand blocks up with the whole world visible below it, and the
 * overlay was the only thing selling the journey. The ship now flies down a lit bore with ribs sweeping
 * past it and a band of light running its length - real geometry, at a real distance, moving at the
 * real speed - and that is a far better motion cue than anything paintable in screen space. The
 * overlay's job is to tint and close in the view so the tunnel reads as an atmosphere rather than a
 * corridor of blocks. Painting over the top of it would be hiding the thing worth seeing.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class WarpCorridorOverlay {

    private static final int FADE_TICKS = 10;

    /** Ticks left before the overlay lets go on its own, in case the exit cue never arrives. */
    private static int remainingTicks;
    private static float intensity;
    private static float lastIntensity;
    private static float spin;
    private static float lastSpin;
    private static int colour = 0xE45CFF;

    private WarpCorridorOverlay() {
    }

    /** Applies a server cue. */
    public static void accept(ClientboundCorridorPacket packet) {
        if (packet.active()) {
            remainingTicks = Math.max(remainingTicks, packet.duration() + FADE_TICKS);
            colour = packet.colour();
        } else {
            // Let it fall away rather than cutting out.
            remainingTicks = Math.min(remainingTicks, FADE_TICKS);
        }
    }

    public static void clear() {
        remainingTicks = 0;
        intensity = 0.0F;
        lastIntensity = 0.0F;
    }

    private static float intensity(float partialTick) {
        return Mth.lerp(partialTick, lastIntensity, intensity);
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
        lastSpin = spin;

        float target = remainingTicks > FADE_TICKS ? 1.0F : 0.0F;
        intensity += (target - intensity) / FADE_TICKS;
        if (intensity < 0.004F) {
            intensity = 0.0F;
        }
        spin += 0.06F + 0.18F * intensity;
    }

    // ---------------------------------------------------------------- render

    /** The corridor has no sky, so the fog becomes the rift. */
    @SubscribeEvent
    public static void onFogColour(ViewportEvent.ComputeFogColor event) {
        float amount = intensity((float) event.getPartialTick());
        if (amount <= 0.0F) {
            return;
        }
        event.setRed(Mth.lerp(amount, event.getRed(), ((colour >> 16) & 0xFF) / 255.0F * 0.35F));
        event.setGreen(Mth.lerp(amount, event.getGreen(), ((colour >> 8) & 0xFF) / 255.0F * 0.2F));
        event.setBlue(Mth.lerp(amount, event.getBlue(), (colour & 0xFF) / 255.0F * 0.5F));
    }

    /** Closes the world in around the ship so the corridor feels like a tunnel. */
    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog event) {
        float amount = intensity((float) event.getPartialTick());
        if (amount <= 0.0F) {
            return;
        }
        // Pulled in from seventy-two. The bore is about thirty blocks across and its far end is well
        // under a hundred away, so fog at this range fills the tunnel with atmosphere instead of
        // sitting somewhere past the end of it doing nothing.
        event.setNearPlaneDistance(Mth.lerp(amount, event.getNearPlaneDistance(), 3.0F));
        event.setFarPlaneDistance(Mth.lerp(amount, event.getFarPlaneDistance(), 44.0F));
        event.setCanceled(true);
    }

    /** Speed stretches the view. */
    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        float amount = intensity((float) event.getPartialTick());
        if (amount > 0.0F) {
            event.setFOV(event.getFOV() * (1.0D + 0.35D * amount));
        }
    }

    /**
     * The frame drowning in the rift's colour, from every edge equally.
     *
     * <p>Two gradients in from the top and bottom, a fainter pair from the sides, and a set of slow
     * horizontal bands drifting across the middle. None of it has a focus, an origin or a heading, so
     * it says the same thing whichever way the player is facing - which is the point.
     *
     * <p>Held well below the old effect's strength. This is meant to sit underneath the fog and the
     * sound rather than compete with them.
     */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float amount = intensity(partialTick);
        if (amount <= 0.0F || !AWConfig.WARP_CORRIDOR.get()) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int tint = colour & 0xFFFFFF;

        // A vignette rather than a wash. It draws the eye down the bore and darkens the corners where
        // the tunnel wall is closest and least interesting, without putting colour over the middle of
        // the frame where the ribs and the running band actually are.
        int corner = (int) (amount * 55);
        graphics.fillGradient(0, 0, width, height / 4, (corner << 24) | tint, 0);
        graphics.fillGradient(0, height - height / 4, width, height, 0, (corner << 24) | tint);
        int side = (int) (amount * 34);
        graphics.fillGradient(0, 0, width / 5, height, (side << 24) | tint, 0);
        graphics.fillGradient(width - width / 5, 0, width, height, 0, (side << 24) | tint);

        // One slow breath across the whole frame, well under the old shimmer. Enough that the light
        // in here feels unsteady; not enough to compete with the tunnel for attention.
        float time = Mth.lerp(partialTick, lastSpin, spin);
        int breath = (int) (amount * (5.0F + 5.0F * Mth.abs(Mth.sin(time * 0.08F))));
        if (breath > 1) {
            graphics.fill(0, 0, width, height, (breath << 24) | tint);
        }
    }
}
