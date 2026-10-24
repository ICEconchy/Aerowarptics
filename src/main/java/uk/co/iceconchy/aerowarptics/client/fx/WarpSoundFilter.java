package uk.co.iceconchy.aerowarptics.client.fx;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;

/**
 * Drops a warp's sounds for a player who has turned {@code warpSounds} off, and turns every other sound of
 * the mod's down to what {@code effectVolume} allows.
 *
 * <p>Here, on the way into the sound engine, because it is the one place both kinds pass: the ones the
 * server plays at a drive and the ones this client plays for itself at a rift. Which sounds are a warp's is
 * {@link WarpSoundSwitch}'s to say, and how loud is {@link SoundMix}'s.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class WarpSoundFilter {

    private WarpSoundFilter() {
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null) {
            return;
        }
        ResourceLocation name = sound.getLocation();
        // The name first, and the settings only for a sound they cover: the game plays sounds before the
        // client config has been read, and asking a config that early throws.
        if (!SoundMix.covers(name.getNamespace())) {
            return;
        }
        if (WarpSoundSwitch.covers(name.getNamespace(), name.getPath()) && !AWConfig.WARP_SOUNDS.get()) {
            event.setSound(null);
            return;
        }
        // A loop is turned as it plays and trims itself; wrapped, the engine would stop ticking it.
        if (!(sound instanceof TickableSoundInstance) && !(sound instanceof TrimmedSound)) {
            event.setSound(new TrimmedSound(sound));
        }
    }
}
