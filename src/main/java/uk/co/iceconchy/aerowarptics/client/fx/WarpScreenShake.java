package uk.co.iceconchy.aerowarptics.client.fx;

import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;

import java.util.Random;

/**
 * Camera shake for rift formation and exit.
 *
 * <p>Implemented as a decaying magnitude applied to the camera angles rather than by faking damage,
 * so it never tints the screen or interferes with anything the player is doing. Respects the
 * {@code screenShake} client setting.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class WarpScreenShake {

    private static final float DECAY = 0.86F;
    private static final float MAX_DEGREES = 1.8F;

    /**
     * {@link Random} lives in {@code java.base}, so it is present on every runtime. The
     * {@link java.util.random.RandomGenerator} SPI relies on the {@code jdk.random} provider module,
     * which stripped-down JREs omit — resolving the default there throws and crashed the client.
     */
    private static final Random RANDOM = new Random();

    private static float magnitude;

    private WarpScreenShake() {
    }

    /** Adds a shake impulse. Values are roughly 0..1. */
    public static void add(float strength) {
        if (!AWConfig.SCREEN_SHAKE.get()) {
            return;
        }
        magnitude = Math.min(1.5F, magnitude + strength);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide) {
            magnitude *= DECAY;
            if (magnitude < 0.001F) {
                magnitude = 0.0F;
            }
        }
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (magnitude <= 0.0F) {
            return;
        }
        float amount = magnitude * MAX_DEGREES;
        event.setPitch(event.getPitch() + Mth.clamp((float) RANDOM.nextGaussian() * amount, -amount, amount));
        event.setYaw(event.getYaw() + Mth.clamp((float) RANDOM.nextGaussian() * amount, -amount, amount));
        event.setRoll(event.getRoll() + Mth.clamp((float) RANDOM.nextGaussian() * amount * 0.5F, -amount, amount));
    }
}
