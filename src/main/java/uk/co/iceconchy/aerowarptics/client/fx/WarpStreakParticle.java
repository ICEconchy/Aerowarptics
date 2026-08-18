package uk.co.iceconchy.aerowarptics.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/**
 * An elongated streak drawn along the warp corridor.
 *
 * <p>The quad is stretched along the direction of travel and stays roll-aligned with its own
 * velocity, which reads as motion blur rather than as a cloud of dots.
 */
@OnlyIn(Dist.CLIENT)
public class WarpStreakParticle extends TextureSheetParticle {

    private final SpriteSet sprites;

    protected WarpStreakParticle(ClientLevel level, double x, double y, double z,
                                 double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz);
        this.sprites = sprites;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.lifetime = 8 + this.random.nextInt(8);
        this.quadSize = 0.12F + this.random.nextFloat() * 0.1F;
        this.friction = 0.98F;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        setColor(0.89F, 0.36F, 1.0F);
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        this.oRoll = this.roll;
        super.tick();
        // Point the streak along its own motion.
        this.roll = (float) Math.atan2(this.zd, this.xd);
        float progress = this.age / (float) this.lifetime;
        setAlpha(1.0F - progress);
        setSpriteFromAge(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Registered against {@code AWParticles.WARP_STREAK}. */
    @OnlyIn(Dist.CLIENT)
    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double vx, double vy, double vz) {
            return new WarpStreakParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
