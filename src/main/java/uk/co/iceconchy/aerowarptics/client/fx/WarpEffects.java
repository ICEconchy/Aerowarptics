package uk.co.iceconchy.aerowarptics.client.fx;

import dev.ryanhcode.sable.Sable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveState;
import uk.co.iceconchy.aerowarptics.network.ClientboundWarpEffectPacket;
import uk.co.iceconchy.aerowarptics.registry.AWParticles;
import uk.co.iceconchy.aerowarptics.registry.AWSounds;

/**
 * Everything the warp looks and sounds like on the client.
 *
 * <p>None of this decides anything. Ambient effects follow the drive's synced state, and one-shot
 * bursts follow the server's stage cues, so the client can never show a warp that is not happening.
 *
 * <p>A Rift Drive usually sits on an airship, which means its block position is a coordinate inside
 * Sable's plot grid rather than a place in the world. Every emission point is therefore pushed
 * through {@code Sable.HELPER.projectOutOfSubLevel} first, which applies the sub-level's current
 * render pose - without it the particles would appear in the plot region far away from the ship.
 */
@OnlyIn(Dist.CLIENT)
public final class WarpEffects {

    private WarpEffects() {
    }

    private static double density() {
        return AWConfig.PARTICLE_DENSITY.get();
    }

    private static float volume() {
        return (float) (double) AWConfig.EFFECT_VOLUME.get();
    }

    /** Converts a position inside an airship's plot into the world position the player sees. */
    public static Vec3 toWorld(ClientLevel level, Vec3 local) {
        return Sable.HELPER.projectOutOfSubLevel(level, local);
    }

    // -------------------------------------------------------------- ambient

    /** Called every client tick by the drive, scaled to whatever state the server put it in. */
    public static void tickDrive(RiftDriveBlockEntity drive) {
        if (!(drive.getLevel() instanceof ClientLevel level)) {
            return;
        }
        double scale = density();
        if (scale <= 0.0D) {
            return;
        }

        RandomSource random = level.random;
        Vec3 centre = toWorld(level, Vec3.atCenterOf(drive.getBlockPos()));
        RiftDriveState state = drive.state();
        float charge = drive.chargePartial(1.0F);

        switch (state) {
            case CHARGING -> {
                if (random.nextFloat() < 0.35F * charge * scale) {
                    orbit(level, centre, random, 0.55D, AWParticles.RIFT_SPARK.get(), 0.02D);
                }
            }
            case CHARGED -> {
                if (random.nextFloat() < 0.5F * scale) {
                    orbit(level, centre, random, 0.65D, AWParticles.RIFT_SPARK.get(), 0.01D);
                }
            }
            case DESTINATION_SELECTED, STABILIZING -> {
                float progress = drive.sequenceProgress();
                int count = (int) Math.max(1, 3 * scale);
                for (int i = 0; i < count; i++) {
                    // Particles converge on the core as the destination locks in.
                    double radius = Mth.lerp(progress, 1.6D, 0.4D);
                    inward(level, centre, random, radius);
                }
            }
            case WARPING -> {
                // Heavier while the hull is actually in the corridor than during the run at the rift.
                boolean inCorridor = "CORRIDOR".equals(drive.flightStage());
                int count = (int) Math.max(1, (inCorridor ? 10 : 4) * scale);
                for (int i = 0; i < count; i++) {
                    streak(level, centre, random);
                }
                if (random.nextFloat() < (inCorridor ? 0.12F : 0.05F)) {
                    level.playLocalSound(centre.x, centre.y, centre.z, AWSounds.WARP_TRAVEL.get(),
                            SoundSource.BLOCKS, 0.6F * volume(), 0.8F + random.nextFloat() * 0.4F, false);
                }
            }
            case ARRIVING -> {
                if (random.nextFloat() < 0.6F * scale) {
                    level.addParticle(ParticleTypes.REVERSE_PORTAL,
                            centre.x + jitter(random, 1.2D), centre.y + jitter(random, 1.2D), centre.z + jitter(random, 1.2D),
                            0.0D, 0.02D, 0.0D);
                }
            }
            case COOLDOWN -> {
                if (random.nextFloat() < 0.25F * scale) {
                    // Residual heat venting.
                    level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            centre.x + jitter(random, 0.4D), centre.y + 0.6D, centre.z + jitter(random, 0.4D),
                            0.0D, 0.04D, 0.0D);
                }
            }
            case ERROR -> {
                if (random.nextFloat() < 0.2F * scale) {
                    level.addParticle(ParticleTypes.SMOKE,
                            centre.x + jitter(random, 0.4D), centre.y + 0.5D, centre.z + jitter(random, 0.4D),
                            0.0D, 0.03D, 0.0D);
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------- one-shots

    /** Tier accent colours, matching the drive's core and the rift it tears open. */
    private static final int[] TIER_COLOURS = {0x2FA8B8, 0x49D9C4, 0xE0B04A, 0xE45CFF};

    private static int tierColour(int tierIndex) {
        return TIER_COLOURS[Math.max(0, Math.min(TIER_COLOURS.length - 1, tierIndex))];
    }

    /**
     * Handles a stage cue from the server.
     *
     * <p>Cues anchored on the drive are drawn through the airship's pose; cues carrying an aperture
     * are already in world space, because a rift is a hole in the world rather than a part of the
     * ship.
     */
    public static void onStage(ClientboundWarpEffectPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        RandomSource random = level.random;
        double scale = density();
        float tierScale = 1.0F + packet.tierIndex() * 0.25F;
        int colour = tierColour(packet.tierIndex());

        Vec3 driveOrigin = toWorld(level, Vec3.atCenterOf(packet.drivePos()));
        Vec3 riftCentre = packet.hasRift() ? packet.centre() : driveOrigin;

        switch (packet.stage()) {
            case DESTINATION_LOCK -> ring(level, driveOrigin, random, 1.2D * tierScale, (int) (24 * scale),
                    AWParticles.RIFT_SPARK.get());
            case STABILIZING -> ring(level, driveOrigin, random, 2.0D * tierScale, (int) (40 * scale),
                    AWParticles.RIFT_SPARK.get());
            case RIFT_OPEN -> {
                // The aperture holds open long enough for the hull to fly through it, then collapses.
                RiftEffectManager.open(packet, colour, 20, 140, 25);
                riftDisc(level, riftCentre, random, Math.max(3.0D, packet.radius() * 0.6D), (int) (140 * scale));
                WarpScreenShake.add(0.6F);
            }
            case CORRIDOR -> {
                // The corridor overlay is driven by its own packet, sent to the crew by name. This cue
                // is only here for anyone watching the hull streak overhead.
                for (int i = 0; i < 30 * scale; i++) {
                    streak(level, driveOrigin, random);
                }
            }
            case WARP_EXIT -> {
                // The aperture is already open - it tore itself a few seconds ago, when the hull
                // entered the corridor. This is the hull coming through it.
                shockwave(level, riftCentre, random, Math.max(4.0D, packet.radius() * 0.7D), (int) (180 * scale));
                WarpScreenShake.add(1.0F);
            }
            case FAILED -> {
                for (int i = 0; i < 30 * scale; i++) {
                    level.addParticle(ParticleTypes.LARGE_SMOKE,
                            driveOrigin.x + jitter(random, 1.0D), driveOrigin.y + jitter(random, 1.0D),
                            driveOrigin.z + jitter(random, 1.0D),
                            jitter(random, 0.08D), 0.05D, jitter(random, 0.08D));
                }
                level.playLocalSound(driveOrigin.x, driveOrigin.y, driveOrigin.z, AWSounds.WARP_FAILED.get(),
                        SoundSource.BLOCKS, 0.9F * volume(), 0.7F, false);
            }
        }
    }

    // --------------------------------------------------------------- shapes

    private static void orbit(ClientLevel level, Vec3 centre, RandomSource random, double radius,
                              ParticleOptions particle, double rise) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double height = jitter(random, 0.5D);
        level.addParticle(particle,
                centre.x + Math.cos(angle) * radius,
                centre.y + height,
                centre.z + Math.sin(angle) * radius,
                -Math.sin(angle) * 0.06D, rise, Math.cos(angle) * 0.06D);
    }

    private static void inward(ClientLevel level, Vec3 centre, RandomSource random, double radius) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double pitch = (random.nextDouble() - 0.5D) * Math.PI;
        double x = Math.cos(angle) * Math.cos(pitch) * radius;
        double y = Math.sin(pitch) * radius;
        double z = Math.sin(angle) * Math.cos(pitch) * radius;
        level.addParticle(AWParticles.RIFT_SPARK.get(),
                centre.x + x, centre.y + y, centre.z + z,
                -x * 0.12D, -y * 0.12D, -z * 0.12D);
    }

    private static void ring(ClientLevel level, Vec3 centre, RandomSource random, double radius, int count,
                             ParticleOptions particle) {
        for (int i = 0; i < count; i++) {
            double angle = (i / (double) Math.max(1, count)) * Math.PI * 2.0D;
            level.addParticle(particle,
                    centre.x + Math.cos(angle) * radius,
                    centre.y + jitter(random, 0.25D),
                    centre.z + Math.sin(angle) * radius,
                    -Math.cos(angle) * 0.05D, 0.01D, -Math.sin(angle) * 0.05D);
        }
    }

    /** The rift itself: a disc of inward-falling energy with arcing at its edge. */
    private static void riftDisc(ClientLevel level, Vec3 centre, RandomSource random, double radius, int count) {
        boolean distortion = AWConfig.RIFT_DISTORTION.get();
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double r = radius * Math.sqrt(random.nextDouble());
            double x = Math.cos(angle) * r;
            double z = Math.sin(angle) * r;
            level.addParticle(distortion ? ParticleTypes.PORTAL : AWParticles.RIFT_SPARK.get(),
                    centre.x + x, centre.y + jitter(random, 0.4D), centre.z + z,
                    -x * 0.15D, 0.0D, -z * 0.15D);
            if (random.nextFloat() < 0.15F) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        centre.x + x, centre.y + jitter(random, 0.5D), centre.z + z,
                        jitter(random, 0.1D), jitter(random, 0.1D), jitter(random, 0.1D));
            }
        }
        level.playLocalSound(centre.x, centre.y, centre.z, AWSounds.RIFT_OPEN.get(),
                SoundSource.BLOCKS, 1.1F * volume(), 0.9F, false);
    }

    private static void streak(ClientLevel level, Vec3 centre, RandomSource random) {
        if (!AWConfig.WARP_CORRIDOR.get()) {
            return;
        }
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double radius = 1.0D + random.nextDouble() * 4.0D;
        double x = Math.cos(angle) * radius;
        double z = Math.sin(angle) * radius;
        level.addParticle(AWParticles.WARP_STREAK.get(),
                centre.x + x, centre.y + jitter(random, 3.0D), centre.z + z,
                -x * 0.25D, jitter(random, 0.05D), -z * 0.25D);
    }

    /** Exit burst: an outward shell plus a flash. */
    private static void shockwave(ClientLevel level, Vec3 centre, RandomSource random, double radius, int count) {
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double pitch = (random.nextDouble() - 0.5D) * Math.PI;
            double x = Math.cos(angle) * Math.cos(pitch);
            double y = Math.sin(pitch);
            double z = Math.sin(angle) * Math.cos(pitch);
            level.addParticle(AWParticles.RIFT_SPARK.get(),
                    centre.x + x * 0.5D, centre.y + y * 0.5D, centre.z + z * 0.5D,
                    x * radius * 0.08D, y * radius * 0.08D, z * radius * 0.08D);
        }
        level.addParticle(ParticleTypes.FLASH, centre.x, centre.y, centre.z, 0.0D, 0.0D, 0.0D);
        level.playLocalSound(centre.x, centre.y, centre.z, AWSounds.WARP_EXIT.get(),
                SoundSource.BLOCKS, 1.2F * volume(), 1.0F, false);
    }

    private static double jitter(RandomSource random, double magnitude) {
        return (random.nextDouble() - 0.5D) * 2.0D * magnitude;
    }
}
