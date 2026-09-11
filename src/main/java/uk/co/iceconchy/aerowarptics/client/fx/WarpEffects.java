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
import org.joml.Vector3f;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveState;
import uk.co.iceconchy.aerowarptics.modulator.RiftModulatorTheme;
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

    /**
     * The particle a theme reaches for in place of the standard spark.
     *
     * <p>This is the whole of what a Rift Modulator's theme changes about the one-shot bursts below -
     * a different mote, at a different pace. {@link RiftModulatorTheme#STANDARD} keeps every particle
     * exactly as an undecorated drive already draws it. {@link RiftModulatorTheme#CLOCKWORK} has no
     * dedicated mote of its own - {@link ParticleTypes#CRIT} is vanilla's own metallic glint, and
     * reaching for it here is cheaper than a whole new particle type for one theme.
     */
    private static ParticleOptions themeMote(RiftModulatorTheme theme) {
        return switch (theme) {
            case EMBER -> ParticleTypes.FLAME;
            case STARLIGHT -> ParticleTypes.END_ROD;
            case CLOCKWORK -> ParticleTypes.CRIT;
            // The mod's own streak, which exists for the corridor and is exactly the right mote for a
            // theme whose whole idea is that everything has been drawn out into lines.
            case STARBLOCKS -> AWParticles.WARP_STREAK.get();
            // Dark, and it falls away - the ripple a jump leaves where the ship was.
            case BEDROCK -> ParticleTypes.SQUID_INK;
            case BOLDLY_GONE -> ParticleTypes.GLOW; // soft and blue-white, with no crackle in it
            case LUDICROUS -> ParticleTypes.NOTE; // vanilla's only mote that arrives in every colour
            case EVENTFUL_HORIZON -> ParticleTypes.SOUL_FIRE_FLAME; // cold blue fire off the core
            case VWORP -> ParticleTypes.ENCHANT;
            case IMPROBABILITY -> ParticleTypes.TOTEM_OF_UNDYING; // confetti, essentially
            default -> AWParticles.RIFT_SPARK.get();
        };
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
        // Folded into one multiplier: the player's own particleDensity setting, and this warp's own
        // intensity - a Modulator's choice, or 1.0. Every count below reads scale and nothing else, so
        // there is exactly one place either of those two settings can drift out of a burst.
        double scale = density() * packet.intensityOverride();
        float tierScale = 1.0F + packet.tierIndex() * 0.25F;
        float shakeScale = packet.intensityOverride();
        // Already resolved server-side: a linked, fuelled Modulator's choice, or the tier's own
        // colour and standard look. Nothing here needs to know which of the two it got.
        int colour = packet.colourOverride();
        int accentColour = packet.accentColourOverride();
        RiftModulatorTheme theme = RiftModulatorTheme.byIndex(packet.themeOverride());

        Vec3 driveOrigin = toWorld(level, Vec3.atCenterOf(packet.drivePos()));
        Vec3 riftCentre = packet.hasRift() ? packet.centre() : driveOrigin;

        switch (packet.stage()) {
            case DESTINATION_LOCK -> ring(level, driveOrigin, random, 1.2D * tierScale, (int) (24 * scale),
                    themeMote(theme));
            case STABILIZING -> ring(level, driveOrigin, random, 2.0D * tierScale, (int) (40 * scale),
                    themeMote(theme));
            case RIFT_OPEN -> {
                // How long it holds is the server's business: it knows how long the run in takes and
                // how long the hull spends inside the aperture. It is closed explicitly when the hull
                // is done with it, so this is only a backstop against a cue going missing.
                RiftEffectManager.open(packet, colour, accentColour, theme,
                        20, Math.max(60, packet.duration() + 40), 25);
                aperture(level, riftCentre, packet.normal(), random,
                        Math.max(3.0D, packet.radius() * 0.6D), (int) (140 * scale), theme);
                WarpScreenShake.add(0.6F * shakeScale);
            }
            case RIFT_TRANSIT -> {
                // A hull is going through. The aperture keeps the fire going off its own clock from
                // here, so this is one packet rather than one per tick for three seconds.
                RiftEffectManager.transit(riftCentre, packet.duration());
                WarpScreenShake.add(0.5F * shakeScale);
            }
            case RIFT_CLOSE -> RiftEffectManager.collapse(riftCentre);
            case CORRIDOR -> {
                // The corridor overlay is driven by its own packet, sent to the crew by name. This cue
                // is only here for anyone watching the hull streak overhead.
                for (int i = 0; i < 30 * scale; i++) {
                    streak(level, driveOrigin, random);
                }
            }
            case WARP_EXIT -> {
                // The hull is all the way out of the far aperture, so the aperture is finished with.
                shockwave(level, riftCentre, random, Math.max(4.0D, packet.radius() * 0.7D),
                        (int) (180 * scale), theme);
                RiftEffectManager.collapse(riftCentre);
                WarpScreenShake.add(1.0F * shakeScale);
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
            case SCATTER -> {
                // Outward burst of sparks — the opposite of the converging sparks during
                // stabilisation, selling the "something went sideways" feel.
                int count = (int) Math.max(1, 30 * scale);
                for (int i = 0; i < count; i++) {
                    double angle = random.nextDouble() * Math.PI * 2.0D;
                    double pitch = (random.nextDouble() - 0.5D) * Math.PI;
                    double x = Math.cos(angle) * Math.cos(pitch);
                    double y = Math.sin(pitch);
                    double z = Math.sin(angle) * Math.cos(pitch);
                    level.addParticle(AWParticles.RIFT_SPARK.get(),
                            driveOrigin.x + x * 0.5D, driveOrigin.y + y * 0.5D, driveOrigin.z + z * 0.5D,
                            x * 0.15D, y * 0.15D, z * 0.15D);
                    if (random.nextFloat() < 0.2F) {
                        level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                                driveOrigin.x + x * 0.5D, driveOrigin.y + y * 0.5D, driveOrigin.z + z * 0.5D,
                                jitter(random, 0.1D), jitter(random, 0.1D), jitter(random, 0.1D));
                    }
                }
                WarpScreenShake.add(0.3F * shakeScale);
                level.playLocalSound(driveOrigin.x, driveOrigin.y, driveOrigin.z,
                        AWSounds.WARP_SCATTER.get(), SoundSource.BLOCKS, 0.9F * volume(), 1.0F, false);
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

    /**
     * The rift tearing open: energy falling inwards across the face of the aperture.
     *
     * <p>Laid out in the aperture's own plane rather than the world's. A rift stands upright - a ship
     * flies through it - so scattering these across the ground plane put them somewhere the aperture
     * simply is not.
     */
    private static void aperture(ClientLevel level, Vec3 centre, Vec3 normal, RandomSource random,
                                 double radius, int count, RiftModulatorTheme theme) {
        // The distortion disc is the standard look's own texture, not a theme - a Modulator dressing
        // the rift in something else replaces it rather than layering underneath it.
        boolean distortion = theme == RiftModulatorTheme.STANDARD && AWConfig.RIFT_DISTORTION.get();
        ParticleOptions mote = distortion ? ParticleTypes.PORTAL : themeMote(theme);
        // ARCANE crackles harder than the standard look; EMBER and STARLIGHT are both quieter takes on
        // the same tear, so the electric arcs that sell "standard" would fight their own point.
        // CLOCKWORK has none at all - a gear grinds, it does not spark.
        // Of the borrowed themes only three crackle, the same three that throw lightning - see
        // ThemeLook.crackles: a gravity drive tearing a hole is violent, the vortex is full of it, and an
        // improbable rift is allowed anything. The rest are each making a quieter claim than "this is
        // tearing", and arcs would talk over all of them.
        float arcChance = switch (theme) {
            case ARCANE -> 0.3F;
            case IMPROBABILITY -> 0.4F;
            case EVENTFUL_HORIZON -> 0.35F;
            case VWORP -> 0.25F;
            case EMBER, STARLIGHT, CLOCKWORK, STARBLOCKS, BEDROCK, BOLDLY_GONE, LUDICROUS -> 0.0F;
            default -> 0.15F;
        };
        Vector3f[] basis = planeOf(normal);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double r = radius * Math.sqrt(random.nextDouble());
            Vec3 offset = inPlane(basis, angle, r);
            Vec3 inward = offset.scale(-0.15D / Math.max(0.001D, r));
            level.addParticle(mote,
                    centre.x + offset.x, centre.y + offset.y, centre.z + offset.z,
                    inward.x, inward.y, inward.z);
            if (arcChance > 0.0F && random.nextFloat() < arcChance) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        centre.x + offset.x, centre.y + offset.y, centre.z + offset.z,
                        jitter(random, 0.1D), jitter(random, 0.1D), jitter(random, 0.1D));
            }
        }
        level.playLocalSound(centre.x, centre.y, centre.z, AWSounds.RIFT_OPEN.get(),
                SoundSource.BLOCKS, 1.1F * volume(), 0.9F, false);
    }

    /**
     * Fire around the rim while something is passing through, called once a tick by the aperture.
     *
     * <p>Deliberately on the rim rather than across the face: the face is opaque, so anything drawn
     * on it would only be hiding the very thing that is meant to be doing the hiding. What sells the
     * hull going through is the edge reacting to it.
     */
    public static void tearFire(ClientLevel level, Vec3 centre, Vector3f right, Vector3f up,
                                double radius, int colour) {
        double scale = density();
        if (scale <= 0.0D || !AWConfig.RIFT_DISTORTION.get()) {
            return;
        }
        RandomSource random = level.random;
        Vector3f[] basis = {right, up};
        int count = (int) Math.max(1, 6 * scale);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            Vec3 offset = inPlane(basis, angle, radius * (0.95D + random.nextDouble() * 0.25D));
            // Thrown outwards along the rim, as if something is forcing the tear wider.
            Vec3 outward = offset.normalize().scale(0.12D + random.nextDouble() * 0.1D);
            level.addParticle(AWParticles.RIFT_SPARK.get(),
                    centre.x + offset.x, centre.y + offset.y, centre.z + offset.z,
                    outward.x, outward.y, outward.z);
            if (random.nextFloat() < 0.25F) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        centre.x + offset.x, centre.y + offset.y, centre.z + offset.z,
                        jitter(random, 0.2D), jitter(random, 0.2D), jitter(random, 0.2D));
            }
        }
    }

    /** Two perpendicular unit vectors spanning the plane a normal stands against. */
    private static Vector3f[] planeOf(Vec3 normal) {
        Vector3f forward = new Vector3f((float) normal.x, (float) normal.y, (float) normal.z);
        if (forward.lengthSquared() < 1.0e-6F) {
            forward.set(0.0F, 0.0F, 1.0F);
        }
        forward.normalize();
        Vector3f seed = Math.abs(forward.y) > 0.9F
                ? new Vector3f(1.0F, 0.0F, 0.0F)
                : new Vector3f(0.0F, 1.0F, 0.0F);
        Vector3f right = new Vector3f(forward).cross(seed).normalize();
        Vector3f up = new Vector3f(forward).cross(right).normalize();
        return new Vector3f[]{right, up};
    }

    private static Vec3 inPlane(Vector3f[] basis, double angle, double radius) {
        double cos = Math.cos(angle) * radius;
        double sin = Math.sin(angle) * radius;
        return new Vec3(basis[0].x * cos + basis[1].x * sin,
                basis[0].y * cos + basis[1].y * sin,
                basis[0].z * cos + basis[1].z * sin);
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
    private static void shockwave(ClientLevel level, Vec3 centre, RandomSource random, double radius,
                                  int count, RiftModulatorTheme theme) {
        ParticleOptions mote = themeMote(theme);
        // EMBER drifts rather than shoots - it is meant to smoulder, not crack - and STARLIGHT keeps
        // its usual sparser count from the caller rather than being thinned again here. The exit is
        // the one moment a theme gets to say how hard the arrival was: a jump to lightspeed snaps out, a
        // warp drive's ring blows outward fast, a gravity drive's is thrown violently, and a fold leaves
        // only a slow dark ripple where the ship was.
        double speed = switch (theme) {
            case EMBER -> 0.03D;
            case BEDROCK -> 0.04D;
            case EVENTFUL_HORIZON -> 0.16D;
            case BOLDLY_GONE -> 0.18D;
            case STARBLOCKS -> 0.20D;
            default -> 0.08D;
        };
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double pitch = (random.nextDouble() - 0.5D) * Math.PI;
            double x = Math.cos(angle) * Math.cos(pitch);
            double y = Math.sin(pitch);
            double z = Math.sin(angle) * Math.cos(pitch);
            level.addParticle(mote,
                    centre.x + x * 0.5D, centre.y + y * 0.5D, centre.z + z * 0.5D,
                    x * radius * speed, y * radius * speed, z * radius * speed);
        }
        level.addParticle(ParticleTypes.FLASH, centre.x, centre.y, centre.z, 0.0D, 0.0D, 0.0D);
        level.playLocalSound(centre.x, centre.y, centre.z, AWSounds.WARP_EXIT.get(),
                SoundSource.BLOCKS, 1.2F * volume(), 1.0F, false);
    }

    private static double jitter(RandomSource random, double magnitude) {
        return (random.nextDouble() - 0.5D) * 2.0D * magnitude;
    }
}
