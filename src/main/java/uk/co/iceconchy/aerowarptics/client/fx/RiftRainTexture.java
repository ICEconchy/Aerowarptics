package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import uk.co.iceconchy.aerowarptics.AeroWarptics;

import java.io.IOException;

/**
 * The Rift Storm's rain texture: vanilla's own rain, recoloured violet as it is loaded.
 *
 * <p>The storm's rain used to have a texture of its own, drawn by a script in the rift's colours - bright
 * lilac drops, many more of them than vanilla's and glowing at night. It was the most distracting thing on
 * the screen in a storm. Vanilla's drops are what a player's eye has long since learned to look through, so
 * the storm now uses those, and changes only their colour ({@link RiftRainTint} says how, and why it has
 * to be a recolour rather than a tint).
 *
 * <p>Read from whatever rain texture the game has, not from a copy: a resource pack that redraws the rain
 * gets its own drops back in violet, and no file of Mojang's is shipped in this mod. A {@link SimpleTexture}
 * rather than a {@code DynamicTexture} for exactly that reason - the texture manager re-registers every
 * texture it holds on a resource reload, which for a simple texture means loading it again, so F3+T or a
 * change of resource pack rebuilds this from the new rain. A dynamic texture would keep the old one.
 *
 * <p>Registered under a name with no file behind it, and on first use rather than at start-up: whatever
 * asks the texture manager for a name it has never heard of is handed a plain texture read from that path,
 * so this must be in place before the rain's render type is first set up.
 */
@OnlyIn(Dist.CLIENT)
final class RiftRainTexture extends SimpleTexture {

    /** Where the rain's render type finds it. Deliberately not under {@code textures/}: there is no such file. */
    static final ResourceLocation LOCATION = AeroWarptics.id("dynamic/rift_rain");

    /** Vanilla's rain, as the current resource packs have it. */
    private static final ResourceLocation VANILLA_RAIN =
            ResourceLocation.withDefaultNamespace("textures/environment/rain.png");

    private RiftRainTexture() {
        super(VANILLA_RAIN);
    }

    /**
     * Puts the texture in place if it is not there. Cheap enough to ask every frame the rain is drawn.
     *
     * <p>A load that failed leaves the missing texture registered under the name, which the next resource
     * reload clears; asking again every frame in between would only log the same failure every frame.
     */
    static void ensureRegistered() {
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        AbstractTexture current = textures.getTexture(LOCATION, null);
        if (current == null) {
            textures.register(LOCATION, new RiftRainTexture());
        } else if (current != MissingTextureAtlasSprite.getTexture() && !(current instanceof RiftRainTexture)) {
            // Something drew with the name before this was registered and was handed a plain texture.
            textures.register(LOCATION, new RiftRainTexture());
        }
    }

    @Override
    protected TextureImage getTextureImage(ResourceManager resources) {
        TextureImage source = TextureImage.load(resources, VANILLA_RAIN);
        try {
            NativeImage image = source.getImage();
            // Pixels come as ABGR, the order the image holds them in memory.
            image.applyToAllPixels(abgr -> {
                int argb = RiftRainTint.recolour(abgr & 0xFF, (abgr >> 8) & 0xFF, (abgr >> 16) & 0xFF, abgr >>> 24);
                return argb & 0xFF00FF00 | (argb >> 16) & 0xFF | (argb & 0xFF) << 16;
            });
            return new TextureImage(source.getTextureMetadata(), image);
        } catch (IOException failure) {
            return new TextureImage(failure);
        }
    }
}
