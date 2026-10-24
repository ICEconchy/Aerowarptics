package uk.co.iceconchy.aerowarptics.client.fx;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import uk.co.iceconchy.aerowarptics.AWConfig;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

/**
 * One of the mod's sounds, played at the volume {@link SoundMix} allows it.
 *
 * <p>It stands in for the sound it was given on the way into the sound engine ({@link WarpSoundFilter}),
 * which is the one place a sound the server sent and one this client played for itself both pass. Not for
 * a loop that changes as it plays: the engine only ticks what is itself tickable, so those multiply their
 * own volume by {@link #level()} instead.
 */
final class TrimmedSound implements SoundInstance {

    private final SoundInstance sound;
    @Nullable
    private Sound carried;

    TrimmedSound(SoundInstance sound) {
        this.sound = sound;
    }

    /** What a loop that sets its own volume each tick multiplies it by. */
    static float level() {
        return SoundMix.level(AWConfig.EFFECT_VOLUME.get());
    }

    @Override
    public float getVolume() {
        return SoundMix.gain(sound.getVolume(), AWConfig.EFFECT_VOLUME.get());
    }

    @Override
    public Sound getSound() {
        Sound chosen = sound.getSound();
        float asked = sound.getVolume();
        // The engine knows a sound with nothing to play by identity, so those are never copied.
        if (!(asked > 1.0F) || chosen == SoundManager.EMPTY_SOUND || chosen == SoundManager.INTENTIONALLY_EMPTY_SOUND) {
            return chosen;
        }
        if (carried == null) {
            carried = new Sound(chosen.getLocation(), chosen.getVolume(), chosen.getPitch(), chosen.getWeight(),
                    chosen.getType(), chosen.shouldStream(), chosen.shouldPreload(),
                    SoundMix.reach(chosen.getAttenuationDistance(), asked));
        }
        return carried;
    }

    @Override
    public ResourceLocation getLocation() {
        return sound.getLocation();
    }

    @Nullable
    @Override
    public WeighedSoundEvents resolve(SoundManager manager) {
        carried = null;
        return sound.resolve(manager);
    }

    @Override
    public SoundSource getSource() {
        return sound.getSource();
    }

    @Override
    public boolean isLooping() {
        return sound.isLooping();
    }

    @Override
    public boolean isRelative() {
        return sound.isRelative();
    }

    @Override
    public int getDelay() {
        return sound.getDelay();
    }

    @Override
    public float getPitch() {
        return sound.getPitch();
    }

    @Override
    public double getX() {
        return sound.getX();
    }

    @Override
    public double getY() {
        return sound.getY();
    }

    @Override
    public double getZ() {
        return sound.getZ();
    }

    @Override
    public Attenuation getAttenuation() {
        return sound.getAttenuation();
    }

    @Override
    public boolean canStartSilent() {
        return sound.canStartSilent();
    }

    @Override
    public boolean canPlaySound() {
        return sound.canPlaySound();
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound played, boolean looping) {
        return sound.getStream(soundBuffers, played, looping);
    }
}
