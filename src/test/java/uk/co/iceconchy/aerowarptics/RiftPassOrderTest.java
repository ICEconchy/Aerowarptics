package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The order the rift passes draw in, read off the source.
 *
 * <p>Two of those orders carry weight, and neither can be seen from a unit test that draws nothing -
 * but both can be read, the way {@link RiftVertexFormatTest} reads which methods write vertices. Get
 * either wrong and the game still draws a rift, just not safely or not well.
 */
class RiftPassOrderTest {

    private static final Path SOURCE = Path.of("src", "main", "java", "uk", "co", "iceconchy", "aerowarptics",
            "client", "fx", "RiftEffectManager.java");

    private static String source() {
        try {
            return Files.readString(SOURCE);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * The lens copies the frame only once every face and throat is in it.
     *
     * <p>The face and the throat are what hide a hull passing through. A frame copied before they are
     * drawn still has that hull in it, and the lens - which redraws the view round a rift out of the copy -
     * would draw it bent round the outside of the rift for anyone to see. Moving the call up to "before
     * any rift is drawn", which reads like the tidier order, is exactly that mistake.
     */
    @Test
    void theLensIsCopiedOnlyAfterEveryFaceAndThroat() {
        String source = source();
        int lens = source.indexOf("GravityLens.draw(");
        assertTrue(lens > 0, "nothing draws the lens any more");
        assertEquals(lens, source.lastIndexOf("GravityLens.draw("), "the lens is drawn from more than one place");
        int membrane = source.indexOf("buffers.endBatch(AWRenderTypes.RIFT_MEMBRANE)");
        int endSky = source.indexOf("buffers.endBatch(AWRenderTypes.RIFT_END_SKY)");
        assertTrue(membrane > 0 && endSky > 0, "the membrane's batches have been renamed or removed");
        assertTrue(lens > membrane && lens > endSky,
                "the lens takes its copy before the faces and throats that hide a hull are drawn");
        assertTrue(lens < source.indexOf("getBuffer(AWRenderTypes.RIFT_FIRE)"),
                "the lens has moved out of the membrane's pass, after light it would copy and bend");
    }

    /**
     * A cloud is finished before any light is drawn over it.
     *
     * <p>The cloud is dark and drawn translucent; the lightning crawling over it is additive. Drawn the
     * other way round, or in one batch, the cloud dims its own lightning.
     */
    @Test
    void theCloudIsDrawnBeforeTheLightThatPlaysOverIt() {
        String source = source();
        int cloud = source.indexOf("RiftFurniture.cloud(");
        int fire = source.indexOf("getBuffer(AWRenderTypes.RIFT_FIRE)");
        assertTrue(cloud > 0, "nothing draws the cloud any more");
        assertTrue(cloud < fire, "the cloud is drawn after the light that plays over it");
        int ended = source.indexOf("buffers.endBatch(AWRenderTypes.RIFT_SHARD)", cloud);
        assertTrue(ended > cloud && ended < fire, "the cloud's batch is still open when the light begins");
    }
}
