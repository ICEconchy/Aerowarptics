package uk.co.iceconchy.aerowarptics.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveState;
import uk.co.iceconchy.aerowarptics.registry.AWSounds;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * One drive's wind-up, from the moment its rings start to align until the rift opens or the warp is
 * called off.
 *
 * <p>A loop that follows its drive, like a flotilla's hum, and for the same reason: the point is that the
 * pitch moves while it plays, and a sound sent once by the server cannot be turned afterwards. What it
 * plays is {@link SpinUpVoice}'s to say; this only keeps it on the drive and lets it go.
 *
 * <p>It reads nothing but what the drive already syncs - its state and how far wound it is - so it can
 * never be heard for a wind-up that is not happening.
 */
@OnlyIn(Dist.CLIENT)
final class DriveSpinUpSound extends AbstractTickableSoundInstance {

    /**
     * The sound each winding drive has playing, by the drive itself. Entries are taken out as their sound
     * stops, and all of them when the client is handed another world - each holds its drive, and through
     * it the level that drive was in.
     */
    private static final Map<RiftDriveBlockEntity, DriveSpinUpSound> PLAYING = new IdentityHashMap<>();

    private final RiftDriveBlockEntity drive;
    private int ticks;

    private DriveSpinUpSound(RiftDriveBlockEntity drive) {
        super(AWSounds.DRIVE_SPIN_UP.get(), SoundSource.BLOCKS, RandomSource.create());
        this.drive = drive;
        this.looping = true;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        // Started all but silent and eased in, so a wind-up opens on a swell and not a click.
        this.volume = 0.01F;
        this.pitch = SpinUpVoice.pitch(drive.sequenceProgress(), 0.0F);
        place();
    }

    /**
     * Starts a drive's wind-up if it is winding and has none playing. Asked every client tick by every
     * drive in view; all but the first few ticks of a spin-up it is one map lookup.
     */
    static void follow(RiftDriveBlockEntity drive) {
        if (drive.state() != RiftDriveState.STABILIZING || !AWConfig.WARP_SOUNDS.get()) {
            return;
        }
        DriveSpinUpSound playing = PLAYING.get(drive);
        if (playing != null && !playing.isStopped()) {
            return;
        }
        // Whatever has stopped since the last wind-up began, or was left by a drive that has gone.
        PLAYING.values().removeIf(sound -> sound.isStopped() || sound.drive.isRemoved());
        DriveSpinUpSound sound = new DriveSpinUpSound(drive);
        PLAYING.put(drive, sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    /** Stops and forgets every wind-up: the client has been handed another world. */
    static void clear() {
        for (DriveSpinUpSound sound : PLAYING.values()) {
            sound.stop();
        }
        PLAYING.clear();
    }

    @Override
    public void tick() {
        ticks++;
        boolean winding = !drive.isRemoved() && drive.state() == RiftDriveState.STABILIZING
                && drive.getLevel() == Minecraft.getInstance().level;
        if (!winding) {
            // Let go rather than cut: the rift's own sound is arriving on top of it, or the drive's fault.
            volume -= volume * SpinUpVoice.EASE * 2.0F;
            if (volume < 0.02F || drive.isRemoved()) {
                stop();
            }
            return;
        }
        place();
        float progress = drive.sequenceProgress();
        pitch = SpinUpVoice.pitch(progress, ticks);
        float wanted = SpinUpVoice.volume(progress) * TrimmedSound.level();
        volume += (wanted - volume) * SpinUpVoice.EASE;
    }

    /**
     * Puts the sound where the drive is seen to be. Aboard a ship the drive's own position is a place in
     * the plot grid, millions of blocks from anybody's ears.
     */
    private void place() {
        if (drive.getLevel() instanceof ClientLevel level) {
            Vec3 at = WarpEffects.toWorld(level, Vec3.atCenterOf(drive.getBlockPos()));
            x = at.x;
            y = at.y;
            z = at.z;
        }
    }
}
