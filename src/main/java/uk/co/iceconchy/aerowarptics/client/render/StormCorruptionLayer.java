package uk.co.iceconchy.aerowarptics.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.client.fx.RiftStormSky;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;

/**
 * A Rift Drive during a Rift Storm: patches of void eating into the machine, edged in violet light.
 *
 * <p>The storm is the one weather in the game that changes a machine - every drive but the Creative one warps
 * less surely and recovers faster while it rages - and this is how a pilot can see that from across the hangar
 * without opening a console. The patches grow as the storm gathers, breathe while it lasts, and shrink away as
 * it clears, all from how much storm {@link RiftStormSky} is drawing; with {@code riftStormWeather} off there
 * is none, and the drive is left as it is.
 *
 * <h2>How</h2>
 * The drive is drawn twice more over itself, rim then void, each through vanilla's dissolve render type
 * ({@link RenderType#dragonExplosionAlpha}, the ender dragon's death). Its shader keeps a texel only where the
 * texture's alpha is at least the vertex alpha, and draws it unlit. The two sheets carry the order the void
 * takes each texel in as their alpha, so the vertex alpha is a threshold, and lowering it grows the patches -
 * see {@link StormCorruption}. The obvious alternative, a cutout render type with a faded vertex colour, cannot
 * do this: in 1.21.1 the cutout shaders test the texture's alpha before the vertex colour is multiplied in.
 *
 * <p>Drawn at exactly the depth of the metal it covers, and after it, so it wins the depth test the way
 * GeckoLib's own glow layers do. The void is drawn after its rim for the same reason, which covers all but the
 * band of rim outside it. Scale, facing, an extended drive's frame and a ship's pose all come with it, because
 * GeckoLib runs a layer inside the same render as the model it belongs to.
 *
 * <p>The core and the needle are skipped - by {@code RiftDriveRenderer}, which draws the bones: the rift is not
 * what is being eaten, and the needle is a reading a pilot still needs.
 */
@OnlyIn(Dist.CLIENT)
public final class StormCorruptionLayer extends GeoRenderLayer<RiftDriveBlockEntity> {

    private static final ResourceLocation VOID_TEXTURE = AeroWarptics.id("textures/block/rift_drive_void.png");
    private static final ResourceLocation RIM_TEXTURE = AeroWarptics.id("textures/block/rift_drive_void_rim.png");
    private static final RenderType VOID = RenderType.dragonExplosionAlpha(VOID_TEXTURE);
    private static final RenderType RIM = RenderType.dragonExplosionAlpha(RIM_TEXTURE);

    public StormCorruptionLayer(GeoRenderer<RiftDriveBlockEntity> renderer) {
        super(renderer);
    }

    /** Whether a bone is being drawn for this layer rather than for the drive itself. */
    static boolean isCorruption(RenderType type) {
        return type == VOID || type == RIM;
    }

    @Override
    public void render(PoseStack poseStack, RiftDriveBlockEntity drive, BakedGeoModel model, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                       int packedLight, int packedOverlay) {
        float storm = RiftStormSky.weather(partialTick);
        if (storm <= 0.0F || !StormCorruption.affects(drive.tier())) {
            return;
        }
        float time = drive.getLevel() == null ? 0.0F : drive.getLevel().getGameTime() + partialTick;
        float growth = StormCorruption.growth(storm, StormCorruption.breath(time, phase(drive.getBlockPos())));

        getRenderer().reRender(model, poseStack, bufferSource, drive, RIM, bufferSource.getBuffer(RIM),
                partialTick, packedLight, OverlayTexture.NO_OVERLAY,
                StormCorruption.colour(StormCorruption.rimThreshold(growth)));
        getRenderer().reRender(model, poseStack, bufferSource, drive, VOID, bufferSource.getBuffer(VOID),
                partialTick, packedLight, OverlayTexture.NO_OVERLAY,
                StormCorruption.colour(StormCorruption.voidThreshold(growth)));
    }

    /** Where in its breathing this drive starts, in radians, from where it stands. */
    private static float phase(BlockPos pos) {
        return (pos.hashCode() & 0xFFFF) / 65535.0F * (float) (Math.PI * 2.0D);
    }
}
