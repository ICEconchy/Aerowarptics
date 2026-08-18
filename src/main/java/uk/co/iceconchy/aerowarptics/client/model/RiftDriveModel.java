package uk.co.iceconchy.aerowarptics.client.model;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveState;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;

/**
 * GeckoLib model binding for the Rift Drive.
 *
 * <p>All four tiers share one geometry and one animation file and differ only by texture, so a new
 * tier is a texture plus a config block.
 */
@OnlyIn(Dist.CLIENT)
public class RiftDriveModel extends GeoModel<RiftDriveBlockEntity> {

    private static final ResourceLocation MODEL = AeroWarptics.id("geo/rift_drive.geo.json");
    private static final ResourceLocation ANIMATION = AeroWarptics.id("animations/rift_drive.animation.json");

    @Override
    public ResourceLocation getModelResource(RiftDriveBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(RiftDriveBlockEntity animatable) {
        RiftDriveTier tier = animatable == null ? RiftDriveTier.MK_I : animatable.tier();
        return AeroWarptics.id("textures/block/" + tier.blockName() + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(RiftDriveBlockEntity animatable) {
        return ANIMATION;
    }

    @Override
    public RenderType getRenderType(RiftDriveBlockEntity animatable, ResourceLocation texture) {
        // The core glows and the housing has cut-out grilles, so both need alpha.
        return RenderType.entityTranslucent(texture);
    }

    /** Emissive-ish tint applied by the renderer, driven by the authoritative state. */
    public static int coreColour(RiftDriveState state) {
        return switch (state) {
            case IDLE -> 0x30_6B_78;
            case CHARGING -> 0x2F_A8_B8;
            case CHARGED -> 0x49_D9_C4;
            case DESTINATION_SELECTED, STABILIZING -> 0xE0_B0_4A;
            case WARPING -> 0xE4_5C_FF;
            case ARRIVING -> 0xC8_6C_FF;
            case COOLDOWN -> 0x8A_6B_FF;
            case ERROR -> 0xE0_40_2F;
        };
    }
}
