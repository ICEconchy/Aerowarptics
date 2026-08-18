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
 * A short-lived arc of rift energy.
 *
 * <p>Drawn on the translucent sheet with an additive-looking colour ramp: bright cyan at birth,
 * fading through violet. Cheap - no lighting, no collision, and a lifetime under a second.
 */
@OnlyIn(Dist.CLIENT)
public class RiftSparkParticle extends TextureSheetParticle {

    private final SpriteSet sprites;

    protected RiftSparkParticle(ClientLevel level, double x, double y, double z,
                                double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz);
        this.sprites = sprites;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.lifetime = 12 + this.random.nextInt(10);
        this.quadSize = 0.08F + this.random.nextFloat() * 0.06F;
        this.friction = 0.92F;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        setColor(0.29F, 0.85F, 0.78F);
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float progress = this.age / (float) this.lifetime;
        // Cyan core cooling into violet as the spark dies.
        setColor(0.29F + progress * 0.6F, 0.85F - progress * 0.55F, 0.78F + progress * 0.2F);
        setAlpha(1.0F - progress * progress);
        setSpriteFromAge(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Registered against {@code AWParticles.RIFT_SPARK}. */
    @OnlyIn(Dist.CLIENT)
    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double vx, double vy, double vz) {
            return new RiftSparkParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
