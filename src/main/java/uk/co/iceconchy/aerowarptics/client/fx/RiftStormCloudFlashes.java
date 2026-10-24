package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;
import uk.co.iceconchy.aerowarptics.AWConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Sheet lightning: patches of storm cloud lit violet from inside, flickering between the proper bolts.
 *
 * <p>Two parts to each flash. A soft glow hung in the cloud layer somewhere round the player, drawn over the
 * clouds against the depth the world had before them ({@link CloudlessDepth}), so it reads as light inside the
 * cloud rather than a lamp in front of it - and seen from a deck above the clouds it lights their tops. And a
 * little of the same light lent to the colour of the whole cloud deck ({@link #glow}), which is what makes the
 * flash belong to the clouds rather than float in front of them. Where there is no cloudless depth to draw the
 * glow against - Fabulous graphics, a shader pack - the deck's flicker carries it alone; with clouds switched off
 * there is no cloud to light, and nothing happens.
 *
 * <p>Silent: sheet lightning is too far off to be heard, and the storm's bolts already have the thunder. It is
 * lightning, so it answers to {@code riftLightning} as the bolts do, and to vanilla's "Hide Lightning Flashes"
 * as every flash in the game does.
 */
@OnlyIn(Dist.CLIENT)
final class RiftStormCloudFlashes {

    private static final int LEAST_GAP = 20;
    private static final int MOST_GAP = 80;
    private static final int LEAST_LIFE = 8;
    private static final int MOST_LIFE = 14;
    /** How much of the flash the whole cloud deck is lent. A hint of it; the glow is where it is. */
    private static final float DECK_SHARE = 0.35F;
    /** Most any one glow is ever drawn, at full storm. */
    private static final float PEAK_ALPHA = 0.5F;
    private static final int SEGMENTS = 16;
    private static final int COLOUR = 0x9A7BFF;

    private static final List<Flash> FLASHES = new ArrayList<>();
    private static int countdown = LEAST_GAP;
    private static float glow;
    private static float lastGlow;

    private RiftStormCloudFlashes() {
    }

    static void clear() {
        FLASHES.clear();
        countdown = LEAST_GAP;
        glow = 0.0F;
        lastGlow = 0.0F;
    }

    /** How much flash the whole cloud deck is lent just now, {@code 0..1}. */
    static float glow(float partialTick) {
        return Mth.lerp(partialTick, lastGlow, glow);
    }

    static void tick(Minecraft minecraft, ClientLevel level, RandomSource random, float weather) {
        FLASHES.removeIf(flash -> ++flash.age >= flash.life);
        lastGlow = glow;
        float brightest = 0.0F;
        for (Flash flash : FLASHES) {
            brightest = Math.max(brightest, flash.brightness(0.0F));
        }
        glow = brightest * DECK_SHARE * weather;

        if (weather <= 0.0F || --countdown > 0) {
            return;
        }
        int gap = LEAST_GAP + random.nextInt(MOST_GAP - LEAST_GAP + 1);
        countdown = Math.round(gap / Math.max(0.2F, weather));
        float cloudHeight = level.effects().getCloudHeight();
        if (!AWConfig.RIFT_LIGHTNING.get()
                || minecraft.options.hideLightningFlash().get()
                || minecraft.options.getCloudsType() == CloudStatus.OFF
                || Float.isNaN(cloudHeight)) {
            return;
        }

        Vec3 eye = minecraft.gameRenderer.getMainCamera().getPosition();
        double reach = Mth.clamp(minecraft.gameRenderer.getRenderDistance() * (0.25D + random.nextDouble() * 0.5D),
                48.0D, 256.0D);
        double bearing = random.nextDouble() * Math.PI * 2.0D;
        Flash flash = new Flash();
        flash.x = eye.x + Math.cos(bearing) * reach;
        flash.y = cloudHeight + 2.0D;
        flash.z = eye.z + Math.sin(bearing) * reach;
        flash.size = 16.0F + random.nextFloat() * 24.0F;
        flash.life = LEAST_LIFE + random.nextInt(MOST_LIFE - LEAST_LIFE + 1);
        flash.pulses = 2 + random.nextInt(2);
        FLASHES.add(flash);
    }

    /** Draws each glow. Only at the weather stage, over the clouds with the cloudless depth swapped in. */
    static void render(RenderLevelStageEvent event, float partialTick, float storm) {
        if (FLASHES.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 eye = event.getCamera().getPosition();
        float fade = minecraft.gameRenderer.getRenderDistance();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer buffer = buffers.getBuffer(AWRenderTypes.RIFT_AURORA);
        Vector3f toward = new Vector3f();
        Vector3f right = new Vector3f();
        Vector3f up = new Vector3f();
        for (Flash flash : FLASHES) {
            float cx = (float) (flash.x - eye.x);
            float cy = (float) (flash.y - eye.y);
            float cz = (float) (flash.z - eye.z);
            float distance = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
            // Unfogged light, so it is faded out by hand toward the edge of the view the fog would have hidden.
            float far = RiftStormShapes.clamp01(1.0F - distance / Math.max(1.0F, fade * 1.1F));
            float lit = flash.brightness(partialTick) * storm * far * PEAK_ALPHA;
            if (lit <= 0.002F || distance < 1.0F) {
                continue;
            }
            // Turned to face the camera, as every glow here is.
            toward.set(cx, cy, cz).div(distance);
            right.set(toward).cross(0.0F, 1.0F, 0.0F);
            if (right.lengthSquared() < 1.0e-6F) {
                right.set(1.0F, 0.0F, 0.0F);
            }
            right.normalize(flash.size);
            up.set(right).cross(toward).normalize(flash.size * 0.55F);
            int middle = SkyFrame.argb(COLOUR, lit);
            int edge = SkyFrame.argb(COLOUR, 0.0F);
            for (int segment = 0; segment < SEGMENTS; segment++) {
                float from = segment * Mth.TWO_PI / SEGMENTS;
                float to = (segment + 1) * Mth.TWO_PI / SEGMENTS;
                vertex(buffer, cx, cy, cz, middle);
                vertex(buffer, cx, cy, cz, middle);
                vertex(buffer, cx + right.x * Mth.cos(to) + up.x * Mth.sin(to),
                        cy + right.y * Mth.cos(to) + up.y * Mth.sin(to),
                        cz + right.z * Mth.cos(to) + up.z * Mth.sin(to), edge);
                vertex(buffer, cx + right.x * Mth.cos(from) + up.x * Mth.sin(from),
                        cy + right.y * Mth.cos(from) + up.y * Mth.sin(from),
                        cz + right.z * Mth.cos(from) + up.z * Mth.sin(from), edge);
            }
        }
        buffers.endBatch(AWRenderTypes.RIFT_AURORA);
    }

    /**
     * Position and colour: the whole of {@code RIFT_AURORA}'s format. Relative to the eye, at the weather stage,
     * where the model-view stack already carries the camera's rotation.
     */
    private static void vertex(VertexConsumer buffer, float x, float y, float z, int argb) {
        buffer.addVertex(x, y, z).setColor(argb);
    }

    private static final class Flash {
        private double x;
        private double y;
        private double z;
        private float size;
        private int pulses;
        private int life;
        private float age;

        float brightness(float partialTick) {
            return RiftStormShapes.sheetFlicker((age + partialTick) / life, pulses);
        }
    }
}
