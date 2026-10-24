package uk.co.iceconchy.aerowarptics.client.fx;

/**
 * Irregular blobs spread across an aperture's face: the End bleeding through before an Ender rift's
 * hole is there, and watercolour spreading over a Sketch rift's paper once it is.
 *
 * <p>Both are the same shape doing the same thing - a lumpy disc growing out from a point until it has
 * filled its share of the face - and both go wrong the same way. A blob that reaches past the rim is,
 * for the End, an opaque patch of starfield hanging in the sky beside the aperture, and for a wash, a
 * stain on the world. So the layout is worked out once, here, where {@code BlotchesTest} can prove
 * that no blob at its largest and lumpiest ever gets out.
 *
 * <p>Free of Minecraft, like {@link RiftShatter}, and seeded off the rift's position for the same
 * reason: every viewer sees the same blobs without a byte being sent about them.
 */
public final class Blotches {

    /** Most a blob's edge strays from its own radius, either way, as a fraction of that radius. */
    public static final float WOBBLE = 0.22F;

    /** Lobes round a blob's edge. Few, so it reads as a spreading stain rather than a cog. */
    private static final int LOBES = 5;

    /** How much of the whole spread the last blob may wait before it starts to grow. */
    private static final float LATEST_START = 0.55F;

    private static final double TWO_PI = Math.PI * 2.0D;

    /**
     * One blob, in rim fractions.
     *
     * @param u      where its middle sits across the face
     * @param v      and up it
     * @param radius how far it reaches at its fullest, before the edge wobbles
     * @param start  how far through the spread it starts to grow, 0 to {@link #LATEST_START}
     * @param phase  where its lobes sit, so no two blobs are the same shape
     */
    public record Blotch(float u, float v, float radius, float start, float phase) {

        /** How far its middle is from the aperture's. */
        public float distance() {
            return (float) Math.sqrt(u * u + v * v);
        }
    }

    private Blotches() {
    }

    /**
     * A handful of blobs for one rift, all of which stay inside {@code reach} of the middle.
     *
     * <p>The first always sits dead centre and starts at once, because both users of this want the
     * middle of the face covered first - the End behind an enderman's eyes, and the wash at the
     * vanishing point a sketch is drawn towards. The rest are placed as far out as their own size
     * allows, which is the whole of the containment: a blob's centre may be no further out than
     * {@code reach} less its widest edge.
     *
     * @param salt keeps two uses of one rift's seed from laying out the same blobs
     */
    public static Blotch[] scatter(int seed, int count, float reach, int salt) {
        Blotch[] out = new Blotch[count];
        for (int index = 0; index < count; index++) {
            int n = salt + index * 5;
            float radius = reach * (0.24F + 0.18F * RiftShatter.noise(seed, n));
            float phase = RiftShatter.noise(seed, n + 1) * (float) TWO_PI;
            if (index == 0) {
                out[index] = new Blotch(0.0F, 0.0F, radius, 0.0F, phase);
                continue;
            }
            float room = Math.max(0.0F, reach - radius * (1.0F + WOBBLE));
            // Square-rooted, so the blobs spread evenly over the face rather than crowding the middle.
            float distance = room * (float) Math.sqrt(RiftShatter.noise(seed, n + 2));
            double angle = RiftShatter.noise(seed, n + 3) * TWO_PI;
            float start = LATEST_START * RiftShatter.noise(seed, n + 4);
            out[index] = new Blotch((float) Math.cos(angle) * distance, (float) Math.sin(angle) * distance,
                    radius, start, phase);
        }
        return out;
    }

    /**
     * How far a blob's edge reaches at an angle round its own middle, as a multiple of its radius.
     *
     * <p>Two sines of different lobe counts, weighted to sum to one, so the edge is lumpy without ever
     * straying more than {@link #WOBBLE} - which is the bound {@link #scatter} leaves room for.
     *
     * @param time a slow creep, so a stain is seen to be spreading rather than stamped on
     */
    public static float edge(Blotch blotch, double angle, float time) {
        double first = Math.sin(angle * LOBES + blotch.phase() + time * 0.30D);
        double second = Math.sin(angle * (LOBES + 2) - blotch.phase() * 1.7D + time * 0.17D);
        return (float) (1.0D + WOBBLE * (0.6D * first + 0.4D * second));
    }

    /**
     * How much of its full size a blob has grown to, 0..1, given how far through the spread it is.
     *
     * <p>Nothing before its own start, then eased out - a stain runs fast into dry paper and slows as it
     * soaks - and always all of it by the end of the spread, so the last blob is never caught small.
     */
    public static float grown(Blotch blotch, float progress) {
        float span = 1.0F - blotch.start();
        float k = Math.max(0.0F, Math.min(1.0F, (progress - blotch.start()) / span));
        float remaining = 1.0F - k;
        return 1.0F - remaining * remaining;
    }
}
