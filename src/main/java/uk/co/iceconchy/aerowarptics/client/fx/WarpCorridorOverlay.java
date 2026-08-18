package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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
 * single frame, so the tunnel is drawn in screen space: the world outside drowns in the rift's colour,
 * the view closes in, the field of view stretches, and streaks rush outward past the edges.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class WarpCorridorOverlay {

    private static final int FADE_TICKS = 10;
    private static final int STREAKS = 56;

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
        event.setNearPlaneDistance(Mth.lerp(amount, event.getNearPlaneDistance(), 6.0F));
        event.setFarPlaneDistance(Mth.lerp(amount, event.getFarPlaneDistance(), 72.0F));
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
     * Streaks rushing outward from the centre of the screen.
     *
     * <p>Each streak is a bar drawn in a rotated coordinate frame, so it points along its own radius
     * instead of being an axis-aligned rectangle. Rotating the matrix and filling a plain bar is far
     * cheaper than building geometry, and it is the only way {@code GuiGraphics} will draw a shape
     * that is not square-on.
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
        float reach = (float) Math.sqrt(width * width + height * height) * 0.5F;

        // A wash that is strongest at the edges, so the middle of the view stays readable.
        int washAlpha = (int) (amount * 90);
        graphics.fillGradient(0, 0, width, height / 3, (washAlpha << 24) | (colour & 0xFFFFFF), 0);
        graphics.fillGradient(0, height - height / 3, width, height,
                0, (washAlpha << 24) | (colour & 0xFFFFFF));

        float travel = Mth.lerp(partialTick, lastSpin, spin);
        double density = AWConfig.PARTICLE_DENSITY.get();
        int count = (int) (STREAKS * amount * density);

        PoseStack pose = graphics.pose();
        for (int i = 0; i < count; i++) {
            // Evenly spread around the circle, jittered so they do not form a visible wheel.
            float angle = (i / (float) Math.max(1, count)) * Mth.TWO_PI + (i % 5) * 0.37F;
            float progress = ((i * 0.1379F) + travel * 0.25F) % 1.0F;
            float eased = progress * progress;

            float start = reach * (0.06F + eased * 1.05F);
            float length = reach * (0.05F + eased * 0.22F);
            float alpha = amount * (1.0F - eased) * 0.9F;
            if (alpha <= 0.02F || start > reach * 1.15F) {
                continue;
            }

            int thickness = 1 + (int) (eased * 3.0F);
            int packed = ((int) (alpha * 255) << 24) | (colour & 0xFFFFFF);

            pose.pushPose();
            pose.translate(width / 2.0F, height / 2.0F, 0.0F);
            pose.mulPose(Axis.ZP.rotation(angle));
            graphics.fill((int) start, -thickness / 2 - 1, (int) (start + length), thickness / 2 + 1, packed);
            pose.popPose();
        }
    }
}
