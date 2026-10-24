package uk.co.iceconchy.aerowarptics.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.collapse.CollapseChoreography;
import uk.co.iceconchy.aerowarptics.collapse.CollapseCradleBlockEntity;
import uk.co.iceconchy.aerowarptics.collapse.CradleWorks;
import uk.co.iceconchy.aerowarptics.modulator.RiftModulatorBlockEntity;
import uk.co.iceconchy.aerowarptics.registry.AWParticles;

/**
 * What a Collapse Cradle does in the air round it that is not the collapse's own scene ({@link CollapseSpectacle}):
 * the small rift a fold is folded in, and a failing machine - sparks off its rings and joints, smoke out of its
 * plinth's vents, the clank of something giving and the hiss of something venting, all thicker the worse the
 * collapse is heading. Run from the cradle's own client tick.
 */
@OnlyIn(Dist.CLIENT)
public final class CradleEffects {

    /** A fold's rift is its own, apart from a collapse's aperture at the same place. */
    private static final long FOLD_KEY = 0x5F01D_0000_0000L;
    /** The fold's rift burns the Singularity's violet, unless a Rift Modulator dresses it. */
    private static final int FOLD_COLOUR = 0x9B5CFF;
    private static final int FOLD_OPEN_TICKS = 6;
    /** The fold rifts this client is holding open, so each is let go once rather than every tick after. */
    private static final java.util.Set<Long> HELD = new java.util.HashSet<>();

    private CradleEffects() {
    }

    public static void tick(CollapseCradleBlockEntity cradle) {
        if (!(cradle.getLevel() instanceof ClientLevel level)) {
            return;
        }
        fold(cradle, level);
        if (cradle.running()) {
            fail(cradle, level, CradleWorks.strain(cradle.unrest()));
        }
    }

    /** Holds the fold's small rift open over the stack while the fold wants it, and lets it go after. */
    private static void fold(CollapseCradleBlockEntity cradle, ClientLevel level) {
        long key = cradle.getBlockPos().asLong() ^ FOLD_KEY;
        double radius = cradle.folding() ? CradleWorks.foldRift(cradle.foldProgress(1.0F)) : 0.0D;
        if (radius <= 0.0D) {
            if (HELD.remove(key)) {
                RiftEffectManager.seal(key);
            }
            return;
        }
        HELD.add(key);
        Vec3 centre = Vec3.atBottomCenterOf(cradle.getBlockPos()).add(0.0D, CollapseChoreography.CENTRE, 0.0D);
        Vec3 toViewer = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(centre);
        if (toViewer.lengthSqr() < 1.0e-6D) {
            toViewer = new Vec3(0.0D, 0.0D, 1.0D);
        }
        RiftModulatorBlockEntity modulator = cradle.modulator();
        int colour = modulator == null ? FOLD_COLOUR : modulator.colour() & 0xFFFFFF;
        int accent = modulator == null ? FOLD_COLOUR : modulator.accentColour() & 0xFFFFFF;
        RiftEffectManager.hold(key, centre, toViewer.normalize(), radius, radius, colour, accent,
                modulator == null ? uk.co.iceconchy.aerowarptics.modulator.RiftModulatorTheme.STANDARD : modulator.theme(),
                FOLD_OPEN_TICKS, false);
        RiftEffectManager.aim(key, toViewer.normalize());
    }

    /** Sparks, smoke, a clank and a hiss, from a machine whose collapse is heading for trouble. */
    private static void fail(CollapseCradleBlockEntity cradle, ClientLevel level, double strain) {
        if (strain <= 0.0D) {
            return;
        }
        RandomSource random = level.random;
        double density = AWConfig.PARTICLE_DENSITY.get();
        BlockPos pos = cradle.getBlockPos();
        double cx = pos.getX() + 0.5D;
        double cz = pos.getZ() + 0.5D;
        // Sparks off the outer ring's rollers and the crown's edge.
        if (random.nextDouble() < strain * 0.9D * density) {
            double a = random.nextDouble() * Math.PI * 2.0D;
            double r = CradleWorks.OUTER_RING[1] / 16.0D;
            double y = pos.getY() + CollapseChoreography.CENTRE - 0.15D;
            if (random.nextBoolean()) {
                r = 1.35D + random.nextDouble() * 0.15D;
                y = pos.getY() + 2.7D;
            }
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, cx + Math.cos(a) * r, y, cz + Math.sin(a) * r,
                    Math.cos(a) * 0.08D, 0.05D + random.nextDouble() * 0.1D, Math.sin(a) * 0.08D);
            if (random.nextDouble() < 0.4D) {
                level.addParticle(AWParticles.RIFT_SPARK.get(), cx + Math.cos(a) * r, y, cz + Math.sin(a) * r,
                        Math.cos(a) * 0.04D, 0.03D, Math.sin(a) * 0.04D);
            }
        }
        // Smoke out of the plinth's vents, on all four sides.
        if (random.nextDouble() < strain * strain * 0.6D * density) {
            int side = random.nextInt(4);
            double along = 0.5D + random.nextDouble() * 0.4D;
            double out = 1.42D;
            double[][] faces = {{along, out}, {-along, -out}, {out, -along}, {-out, along}};
            double dx = faces[side][0];
            double dz = faces[side][1];
            level.addParticle(ParticleTypes.LARGE_SMOKE, cx + dx, pos.getY() + 0.35D, cz + dz,
                    Math.signum(dx) * (Math.abs(dx) > 1.0D ? 0.05D : 0.0D), 0.02D,
                    Math.signum(dz) * (Math.abs(dz) > 1.0D ? 0.05D : 0.0D));
        }
        // Something giving: a clank of metal, and a hiss of something venting.
        if (random.nextDouble() < strain * 0.02D) {
            level.playLocalSound(cx, pos.getY() + 1.5D, cz, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS,
                    0.18F + 0.2F * (float) strain, 0.45F + random.nextFloat() * 0.3F, false);
        }
        if (random.nextDouble() < strain * strain * 0.015D) {
            level.playLocalSound(cx, pos.getY() + 0.5D, cz, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
                    0.35F, 0.5F + random.nextFloat() * 0.3F, false);
        }
    }
}
