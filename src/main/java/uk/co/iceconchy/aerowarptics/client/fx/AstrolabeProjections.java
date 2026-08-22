package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.astrolabe.AstrolabeBlockEntity;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws the relief maps Astrolabe Cartography Tables project above themselves.
 *
 * <p>Deliberately <em>not</em> drawn from the table's block entity renderer, which is where this used
 * to live and is the reason the projection kept turning up somewhere other than over its table. A
 * block entity renderer is handed a pose stack that somebody else has already transformed - the level
 * renderer moves it to the block, Sable moves it again for a table bolted to an airship, and GeckoLib
 * translates and scales it for the model without ever pushing or popping. Drawing into that pose means
 * the projection lands wherever the sum of those transforms happens to put it.
 *
 * <p>So it is drawn the same way the rifts are: as a world-space object, from a pose stack translated
 * by nothing but the camera, at a position worked out explicitly. The only thing the block entity
 * renderer contributes now is telling this class the table is still there to be drawn.
 *
 * <p>The hull's own rotation is then put back, deliberately. A chart is a thing lying on a table, and
 * a projection left level with the world hangs at an angle over a ship that is pitched, rolled, or
 * simply pointed somewhere other than south. What was wrong before was never the rotation - it was
 * the position, and the two arrive tangled together in a pose stack somebody else transformed.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class AstrolabeProjections {

    /**
     * How high above the table's own block the projection's base floats, in blocks.
     *
     * <p>The relief is drawn upwards from here, so this is the clearance over the tabletop rather
     * than the middle of the picture: a little above the 13/16 the model stands, and the whole
     * landscape then sits between chest and eye height for somebody leaning on it.
     */
    private static final double PROJECTION_HEIGHT = 1.0D;

    /** Ticks the projection takes to fade up, so forming a table is an event rather than a pop. */
    private static final float FADE_TICKS = 20.0F;

    /**
     * Ticks a table may go unseen before its projection is dropped.
     *
     * <p>The block entity renderer runs every frame for every table in range, so anything that has
     * not reported in for two ticks has been broken, unformed, or unloaded.
     */
    private static final int SEEN_GRACE_TICKS = 2;

    /**
     * One sampled landscape per table.
     *
     * <p>Weakly keyed: an unloaded table takes its map with it rather than leaving one behind per
     * chunk the player has ever visited.
     */
    private static final Map<AstrolabeBlockEntity, Projection> LIVE = new WeakHashMap<>();

    private AstrolabeProjections() {
    }

    /** Called from the table's renderer each frame, to say the table is still there and in range. */
    public static void seen(AstrolabeBlockEntity table, long gameTime) {
        LIVE.computeIfAbsent(table, key -> new Projection()).seenAt = gameTime;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // After the world's translucent blocks, which is after its block entities - so every table
        // that is going to report in this frame already has.
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || LIVE.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.level instanceof ClientLevel level)) {
            LIVE.clear();
            return;
        }

        long gameTime = level.getGameTime();
        Vec3 eye = event.getCamera().getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        // A slow sweep, one pass every eight seconds. Fast enough to notice, slow enough to ignore.
        float sweep = ((gameTime % 160L) + partialTick) / 160.0F;

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer buffer = buffers.getBuffer(AWRenderTypes.HOLOGRAM);
        boolean drew = false;

        Iterator<Map.Entry<AstrolabeBlockEntity, Projection>> tables = LIVE.entrySet().iterator();
        while (tables.hasNext()) {
            Map.Entry<AstrolabeBlockEntity, Projection> entry = tables.next();
            AstrolabeBlockEntity table = entry.getKey();
            Projection projection = entry.getValue();
            if (table.isRemoved() || gameTime - projection.seenAt > SEEN_GRACE_TICKS) {
                tables.remove();
                continue;
            }

            // Where the table actually is, which for one bolted to an airship is not where its block
            // position says: that is a reserved plot nowhere near where the hull appears to be. Taken
            // at the pose the hull is being drawn at this frame rather than the one it will tick to,
            // or the chart lags the table it is standing on by up to a tick of the ship's travel.
            Pose3dc pose = TerrainHologram.poseOf(level, table.getBlockPos(), partialTick);
            Vec3 centre = TerrainHologram.centreOf(table.getBlockPos(), pose);
            projection.hologram.refresh(level, centre, gameTime);
            float alpha = Math.min(1.0F, ++projection.age / FADE_TICKS) * 0.8F;

            poseStack.pushPose();
            poseStack.translate(centre.x - eye.x, centre.y - eye.y, centre.z - eye.z);
            // Back into the hull's frame, so the chart lies on its table rather than level with the
            // world. Position is worked out in world space and orientation is put back afterwards,
            // which is the whole distinction that was missing: where the table is and which way up it
            // is are two different questions, and only the first one was going wrong.
            if (pose != null) {
                Quaterniondc orientation = pose.orientation();
                poseStack.mulPose(new Quaternionf((float) orientation.x(), (float) orientation.y(),
                        (float) orientation.z(), (float) orientation.w()));
            }
            // Clearance above the tabletop, measured up the table rather than up the world. centre is
            // the middle of the block, so it starts half a block below the base.
            poseStack.translate(0.0D, PROJECTION_HEIGHT - 0.5D, 0.0D);
            projection.hologram.render(poseStack, buffer, sweep, alpha, centre);
            poseStack.popPose();
            drew = true;
        }

        if (drew) {
            buffers.endBatch(AWRenderTypes.HOLOGRAM);
        }
    }

    private static final class Projection {
        private final TerrainHologram hologram = new TerrainHologram();
        private long seenAt;
        private int age;
    }
}
