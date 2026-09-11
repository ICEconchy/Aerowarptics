package uk.co.iceconchy.aerowarptics.client.fx;

/**
 * The shapes an improbable rift passes through on its way somewhere else.
 *
 * <p>The Infinite Improbability Drive does not move a ship so much as make it, very briefly, every
 * ship it improbably could be - and some of those are not ships. So the Improbability theme draws its
 * rift as one of these, then another, a few ticks apart.
 *
 * <p>Each form is a flat list of strokes, {@code x0, y0, x1, y1} per stroke, inside {@code [-1, 1]}
 * with {@code +y} up - the aperture's own up, which is the only up a rift has. Free of Minecraft for
 * the same reason the theme motes are: a figure drawn outside its box, or a stroke of no length that
 * draws as nothing, would not throw anywhere, and {@code ImprobableFormsTest} is what notices.
 *
 * <p>The arrays handed out are the shared originals, not copies - they are read once a frame by the
 * renderer, and nothing is allowed to write to them.
 */
public final class ImprobableForms {

    /** A sperm whale, recently called into existence several miles above the ground. */
    private static final float[] WHALE = {
            // The body: a blunt square head running back to a tapering tail.
            -0.90F, 0.05F, -0.30F, 0.30F,
            -0.30F, 0.30F, 0.55F, 0.32F,
            0.55F, 0.32F, 0.85F, 0.20F,
            0.85F, 0.20F, 0.90F, -0.05F,
            0.90F, -0.05F, 0.70F, -0.25F,
            0.70F, -0.25F, -0.10F, -0.28F,
            -0.10F, -0.28F, -0.90F, 0.05F,
            // Flukes.
            -0.90F, 0.05F, -1.00F, 0.32F,
            -0.90F, 0.05F, -1.00F, -0.22F,
            // The long underslung jaw, and an eye.
            0.90F, -0.05F, 0.35F, -0.14F,
            0.55F, 0.06F, 0.62F, 0.06F,
            // A spout, because it is still breathing and would like to go on doing so.
            0.20F, 0.32F, 0.12F, 0.62F,
            0.20F, 0.32F, 0.30F, 0.60F,
    };

    /** A bowl of petunias, thinking "oh no, not again". */
    private static final float[] PETUNIAS = {
            // The bowl.
            -0.55F, -0.20F, -0.40F, -0.70F,
            -0.40F, -0.70F, 0.40F, -0.70F,
            0.40F, -0.70F, 0.55F, -0.20F,
            0.55F, -0.20F, -0.55F, -0.20F,
            // Three stems.
            -0.25F, -0.20F, -0.35F, 0.25F,
            0.00F, -0.20F, 0.00F, 0.40F,
            0.25F, -0.20F, 0.35F, 0.25F,
            // Three flowers, each a cross of petals.
            -0.47F, 0.25F, -0.23F, 0.25F,
            -0.35F, 0.13F, -0.35F, 0.37F,
            -0.12F, 0.40F, 0.12F, 0.40F,
            0.00F, 0.28F, 0.00F, 0.52F,
            0.23F, 0.25F, 0.47F, 0.25F,
            0.35F, 0.13F, 0.35F, 0.37F,
    };

    /** A knitted doll, with the end of its yarn still trailing from one hand. */
    private static final float[] YARN_DOLL = yarnDoll();

    /** A cube, in the oblique projection every schoolchild draws one in. */
    private static final float[] CUBE = {
            // Front face.
            -0.55F, -0.55F, 0.25F, -0.55F,
            0.25F, -0.55F, 0.25F, 0.25F,
            0.25F, 0.25F, -0.55F, 0.25F,
            -0.55F, 0.25F, -0.55F, -0.55F,
            // Back face.
            -0.25F, -0.25F, 0.55F, -0.25F,
            0.55F, -0.25F, 0.55F, 0.55F,
            0.55F, 0.55F, -0.25F, 0.55F,
            -0.25F, 0.55F, -0.25F, -0.25F,
            // The edges joining them.
            -0.55F, -0.55F, -0.25F, -0.25F,
            0.25F, -0.55F, 0.55F, -0.25F,
            0.25F, 0.25F, 0.55F, 0.55F,
            -0.55F, 0.25F, -0.25F, 0.55F,
    };

    /** A cup of hot tea - the drive's own finite-improbability source, in a manner of speaking. */
    private static final float[] TEACUP = {
            // The cup.
            -0.45F, 0.25F, -0.33F, -0.35F,
            -0.33F, -0.35F, 0.33F, -0.35F,
            0.33F, -0.35F, 0.45F, 0.25F,
            -0.45F, 0.25F, 0.45F, 0.25F,
            // Its handle.
            0.42F, 0.12F, 0.66F, 0.08F,
            0.66F, 0.08F, 0.62F, -0.14F,
            0.62F, -0.14F, 0.37F, -0.16F,
            // The saucer.
            -0.72F, -0.46F, 0.72F, -0.46F,
            // Two curls of steam.
            -0.10F, 0.36F, 0.02F, 0.56F,
            0.02F, 0.56F, -0.10F, 0.76F,
            0.16F, 0.36F, 0.28F, 0.56F,
            0.28F, 0.56F, 0.16F, 0.76F,
    };

    /** A seven-pointed star, drawn in one unbroken line the way a star polygon is. */
    private static final float[] STAR = star(7, 3, 0.9F);

    private static final float[][] FORMS = {WHALE, PETUNIAS, YARN_DOLL, CUBE, TEACUP, STAR};

    private ImprobableForms() {
    }

    /** How many forms there are to pass through. */
    public static int count() {
        return FORMS.length;
    }

    /** One form, counting on round the list however far the index runs. */
    public static float[] form(int index) {
        return FORMS[Math.floorMod(index, FORMS.length)];
    }

    // ------------------------------------------------------------------- built

    private static float[] yarnDoll() {
        float[] strokes = new float[15 * 4];
        int at = 0;
        // A round head, as an octagon - fine enough to read as a circle at the size this is ever seen.
        for (int side = 0; side < 8; side++) {
            double a0 = side * (Math.PI / 4.0D);
            double a1 = (side + 1) * (Math.PI / 4.0D);
            strokes[at++] = (float) (Math.cos(a0) * 0.2D);
            strokes[at++] = (float) (0.55D + Math.sin(a0) * 0.2D);
            strokes[at++] = (float) (Math.cos(a1) * 0.2D);
            strokes[at++] = (float) (0.55D + Math.sin(a1) * 0.2D);
        }
        float[] rest = {
                // Body, arms and legs.
                0.00F, 0.35F, 0.00F, -0.25F,
                -0.45F, 0.12F, 0.45F, 0.12F,
                0.00F, -0.25F, -0.32F, -0.80F,
                0.00F, -0.25F, 0.32F, -0.80F,
                // The yarn it is made of, coming loose from one hand.
                0.45F, 0.12F, 0.62F, -0.02F,
                0.62F, -0.02F, 0.52F, -0.18F,
                0.52F, -0.18F, 0.68F, -0.34F,
        };
        System.arraycopy(rest, 0, strokes, at, rest.length);
        return strokes;
    }

    /**
     * A star polygon: {@code points} vertices round a circle, each joined to the one {@code step}
     * further on. For seven and three that closes into a single seven-pointed figure.
     */
    private static float[] star(int points, int step, float radius) {
        float[] strokes = new float[points * 4];
        for (int k = 0; k < points; k++) {
            double a0 = Math.PI / 2.0D + k * (Math.PI * 2.0D / points);
            double a1 = Math.PI / 2.0D + ((k + step) % points) * (Math.PI * 2.0D / points);
            strokes[k * 4] = (float) (Math.cos(a0) * radius);
            strokes[k * 4 + 1] = (float) (Math.sin(a0) * radius);
            strokes[k * 4 + 2] = (float) (Math.cos(a1) * radius);
            strokes[k * 4 + 3] = (float) (Math.sin(a1) * radius);
        }
        return strokes;
    }
}
