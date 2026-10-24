package uk.co.iceconchy.aerowarptics.client.fx;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import uk.co.iceconchy.aerowarptics.registry.AWSounds;

import java.util.function.Supplier;

/**
 * One drive's hum while its flotilla musters, sliding onto the flagship's note as it harmonises.
 *
 * <p>A looping sound that follows its coupler, rather than one played once: the whole point is that the
 * pitch moves, audibly, while the ship holds station. When every hum has arrived on the flagship's note
 * the fleet is ready - the chord says so, but the pilots should already have heard it.
 *
 * <p>Asks {@link FlotillaEffects} each tick where it is and what it is at; when the answer is nothing -
 * the muster over, the ship gone - it stops itself.
 */
@OnlyIn(Dist.CLIENT)
final class FlotillaHum extends AbstractTickableSoundInstance {

    /** Where the hum is now, and at what pitch; {@code null} to stop. */
    record Voice(Vec3 at, float pitch, float volume) {
    }

    private final Supplier<Voice> voice;

    FlotillaHum(int tier, Supplier<Voice> voice) {
        super(AWSounds.hum(tier), SoundSource.BLOCKS, RandomSource.create());
        this.voice = voice;
        this.looping = true;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.volume = 0.01F;
        Voice first = voice.get();
        if (first != null) {
            x = first.at().x;
            y = first.at().y;
            z = first.at().z;
            pitch = first.pitch();
        }
    }

    @Override
    public void tick() {
        Voice now = voice.get();
        if (now == null) {
            stop();
            return;
        }
        x = now.at().x;
        y = now.at().y;
        z = now.at().z;
        pitch = Math.max(0.5F, Math.min(2.0F, now.pitch()));
        // Eased in rather than started at full, so a muster opens on a swell and not a click.
        volume += (now.volume() * TrimmedSound.level() - volume) * 0.1F;
    }

    void end() {
        stop();
    }
}
