package uk.co.iceconchy.aerowarptics.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.astrolabe.DestinationSurvey;
import uk.co.iceconchy.aerowarptics.astrolabe.TerrainPalette;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A surveyed destination, baked into a texture once and then simply drawn.
 *
 * <p>At one sample a block a survey is sixteen thousand cells, and painting those as sixteen thousand
 * filled rectangles every frame is an absurd amount of work to redraw a picture that only changes when
 * the player clicks a different anchor. So it is built into an image the same way a filled map is, and
 * the screen blits it.
 *
 * <p>The contour lines are baked in here rather than drawn over the top, because they are a property
 * of the ground rather than of the panel: a line appears wherever the surface crosses a multiple of
 * {@link DestinationSurvey#CONTOUR_INTERVAL} blocks above or below the anchor. They are what turn a
 * shaded map into something you can read a gradient off - which is the question a pilot actually has,
 * namely whether they are about to arrive over a valley or into the side of a hill.
 */
@OnlyIn(Dist.CLIENT)
public final class SurveyTexture implements AutoCloseable {

    /** How much a contour line darkens the ground it crosses. */
    private static final float CONTOUR_SHADE = 0.62F;

    /** Every open chart needs its own texture name, or two charts would fight over one image. */
    private static final AtomicInteger SERIAL = new AtomicInteger();

    private final ResourceLocation location;
    private final int size;
    private boolean closed;

    private SurveyTexture(ResourceLocation location, int size) {
        this.location = location;
        this.size = size;
    }

    /** Bakes a survey into a texture, or returns {@code null} if the survey is empty. */
    @Nullable
    public static SurveyTexture of(DestinationSurvey survey) {
        int size = survey.size();
        if (size <= 0 || survey.colours().length < size * size) {
            return null;
        }
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, size, size, false);
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int argb = survey.colour(x, z);
                if ((argb >>> 24) == 0) {
                    image.setPixelRGBA(x, z, 0);
                    continue;
                }
                if (isContour(survey, x, z)) {
                    argb = TerrainPalette.scale(argb, CONTOUR_SHADE);
                }
                image.setPixelRGBA(x, z, toNativeOrder(argb));
            }
        }

        ResourceLocation location = AeroWarptics.id("survey/" + SERIAL.incrementAndGet());
        Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(image));
        return new SurveyTexture(location, size);
    }

    /**
     * Whether a contour line passes through this cell.
     *
     * <p>A cell is on a line when it sits in a different height band from its western or northern
     * neighbour. Testing two sides rather than four draws each line once instead of twice, which
     * keeps it a line rather than a smear.
     */
    private static boolean isContour(DestinationSurvey survey, int x, int z) {
        int band = band(survey.relief(x, z));
        if (x > 0 && survey.known(x - 1, z) && band(survey.relief(x - 1, z)) != band) {
            return true;
        }
        return z > 0 && survey.known(x, z - 1) && band(survey.relief(x, z - 1)) != band;
    }

    private static int band(int relief) {
        return Math.floorDiv(relief, DestinationSurvey.CONTOUR_INTERVAL);
    }

    /**
     * ARGB to the byte order {@link NativeImage} stores.
     *
     * <p>A native image is laid out as it will be uploaded to the GPU, which is red first - so the
     * integer it wants is 0xAABBGGRR, with red and blue the other way round from every colour in this
     * mod. Getting this wrong is not subtle: grass comes out blue.
     */
    private static int toNativeOrder(int argb) {
        return (argb & 0xFF00FF00) | ((argb & 0x00FF0000) >>> 16) | ((argb & 0x000000FF) << 16);
    }

    /** Draws the survey at one screen pixel per sampled block. */
    public void draw(GuiGraphics graphics, int x, int y) {
        if (!closed) {
            graphics.blit(location, x, y, 0, 0, size, size, size, size);
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            Minecraft.getInstance().getTextureManager().release(location);
        }
    }
}
