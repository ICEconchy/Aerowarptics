package uk.co.iceconchy.aerowarptics.client.fx;

import java.util.ArrayList;
import java.util.List;

/**
 * The pencil a Sketch rift is drawn with: a hexagonal painted body, a sharpened wooden cone, the lead,
 * a crimped metal ferrule and an eraser - as a list of flat faces in its own unit space.
 *
 * <p>The tip sits at the origin and the pencil runs up the Z axis to its eraser at {@code z = 1}, so a
 * renderer places it by the tip - which is the one point that has to be exactly where the line is being
 * drawn - and scales it by its length. Every face carries the outward normal it is lit by.
 *
 * <p>Free of Minecraft, like {@link RiftShatter}, and for the usual reason: the ways a hand-built mesh
 * goes wrong - a face lit from inside, a tip that is not the tip, a ferrule the body pokes through - all
 * draw something, and {@code PencilModelTest} is what notices.
 */
public final class PencilModel {

    /** What a face is part of, so a renderer can paint a coloured pencil without rebuilding one. */
    public enum Part {
        /** The exposed point, in whatever it draws with. */
        LEAD,
        /** The sharpened cone of bare wood. */
        WOOD,
        /** The painted hexagonal barrel. */
        BODY,
        /** A thin painted band round the barrel near its end. */
        STRIPE,
        /** The crimped metal sleeve holding the eraser. */
        FERRULE,
        /** The eraser. */
        ERASER
    }

    /**
     * One flat face: four corners, wound as a quad is drawn, and the way it faces.
     *
     * <p>A face with two coincident corners is a triangle, the same trick the rest of the rift drawing
     * uses to put a triangle into a quad buffer.
     *
     * @param corners {@code x, y, z} four times, in the pencil's unit space
     * @param tone    how much darker than its part's own colour this face is painted - a ferrule's ridges
     */
    public record Face(Part part, float[] corners, float nx, float ny, float nz, float tone) {
    }

    /** Where the lead gives way to wood, as a fraction of the length from the tip. */
    public static final float LEAD_END = 0.035F;
    /** Where the sharpened cone meets the painted barrel. */
    public static final float WOOD_END = 0.14F;
    /** Where the barrel disappears into the ferrule. */
    public static final float BODY_END = 0.86F;
    /** Where the ferrule gives way to the eraser. */
    public static final float FERRULE_END = 0.935F;
    /** Where the painted band round the barrel sits. */
    public static final float STRIPE_FROM = 0.80F;
    public static final float STRIPE_TO = 0.815F;

    /**
     * The barrel's radius to its corners, as a fraction of the length.
     *
     * <p>Fatter than a real pencil, which is closer to one in twenty-seven: at the size it is seen, a
     * true-to-life pencil is a line, and a line with a point on it does not read as anything held.
     */
    public static final float RADIUS = 0.036F;
    /** How thick the lead is where the wood gives way to it. */
    public static final float LEAD_RADIUS = RADIUS * 0.3F;
    /** The ferrule's radius to its corners. Round, so it has more sides than the barrel. */
    public static final float FERRULE_RADIUS = RADIUS * 1.04F;
    /** How far a ferrule's crimped ridges stand proud of it. */
    public static final float RIDGE_RADIUS = RADIUS * 1.10F;
    public static final float ERASER_RADIUS = RADIUS * 0.96F;

    /** Sides of the barrel. A pencil is hexagonal, which is most of why it reads as a pencil. */
    public static final int BARREL_SIDES = 6;
    /** Sides of the round parts. Twice the barrel's, so its corners always sit on theirs. */
    public static final int ROUND_SIDES = 12;

    /** The painted barrel: pencil yellow. */
    public static final int BODY = 0xF4B62B;
    /** The band round it. */
    public static final int STRIPE = 0x24302A;
    /** Bare cedar where it has been sharpened. */
    public static final int WOOD = 0xE8C48C;
    /** Graphite. */
    public static final int LEAD = 0x3A3A42;
    public static final int FERRULE = 0xC6C9CF;
    public static final int ERASER = 0xF08DA2;

    private static final List<Face> FACES = build();

    private PencilModel() {
    }

    /** Every face of the pencil. The same list every time; do not change the arrays in it. */
    public static List<Face> faces() {
        return FACES;
    }

    private static List<Face> build() {
        List<Face> faces = new ArrayList<>();

        // The lead: a small cone from the point out to where the wood starts.
        for (int side = 0; side < BARREL_SIDES; side++) {
            float[] a = ring(BARREL_SIDES, LEAD_RADIUS, LEAD_END, side);
            float[] b = ring(BARREL_SIDES, LEAD_RADIUS, LEAD_END, side + 1);
            add(faces, Part.LEAD, 1.0F, new float[]{0.0F, 0.0F, 0.0F}, a, b, new float[]{0.0F, 0.0F, 0.0F});
        }
        // The sharpened wood, widening from the lead to the barrel.
        for (int side = 0; side < BARREL_SIDES; side++) {
            add(faces, Part.WOOD, 1.0F,
                    ring(BARREL_SIDES, LEAD_RADIUS, LEAD_END, side),
                    ring(BARREL_SIDES, LEAD_RADIUS, LEAD_END, side + 1),
                    ring(BARREL_SIDES, RADIUS, WOOD_END, side + 1),
                    ring(BARREL_SIDES, RADIUS, WOOD_END, side));
        }
        // The barrel, in three lengths either side of its band.
        barrel(faces, Part.BODY, RADIUS, WOOD_END, STRIPE_FROM);
        barrel(faces, Part.STRIPE, RADIUS * 1.006F, STRIPE_FROM, STRIPE_TO);
        barrel(faces, Part.BODY, RADIUS, STRIPE_TO, BODY_END);

        // The ferrule: a lip where it meets the barrel, then plain metal and crimped ridges in turn.
        annulus(faces, Part.FERRULE, 1.0F, RADIUS * 0.86F, FERRULE_RADIUS, BODY_END, false);
        float[] steps = {BODY_END, 0.875F, 0.885F, 0.905F, 0.915F, FERRULE_END};
        for (int band = 0; band + 1 < steps.length; band++) {
            boolean ridge = band % 2 == 1;
            float radius = ridge ? RIDGE_RADIUS : FERRULE_RADIUS;
            round(faces, Part.FERRULE, ridge ? 0.82F : 1.0F, radius, steps[band], steps[band + 1]);
            if (ridge) {
                // The two shoulders of a ridge, so it is a solid bead and not a sleeve floating off the metal.
                annulus(faces, Part.FERRULE, 0.82F, FERRULE_RADIUS, RIDGE_RADIUS, steps[band], false);
                annulus(faces, Part.FERRULE, 0.82F, FERRULE_RADIUS, RIDGE_RADIUS, steps[band + 1], true);
            }
        }
        annulus(faces, Part.FERRULE, 1.0F, ERASER_RADIUS, FERRULE_RADIUS, FERRULE_END, true);

        // The eraser, and its flat end - a ring with no hole in it.
        round(faces, Part.ERASER, 1.0F, ERASER_RADIUS, FERRULE_END, 1.0F);
        annulus(faces, Part.ERASER, 1.0F, 0.0F, ERASER_RADIUS, 1.0F, true);
        return List.copyOf(faces);
    }

    private static void barrel(List<Face> faces, Part part, float radius, float from, float to) {
        for (int side = 0; side < BARREL_SIDES; side++) {
            add(faces, part, 1.0F,
                    ring(BARREL_SIDES, radius, from, side), ring(BARREL_SIDES, radius, from, side + 1),
                    ring(BARREL_SIDES, radius, to, side + 1), ring(BARREL_SIDES, radius, to, side));
        }
    }

    private static void round(List<Face> faces, Part part, float tone, float radius, float from, float to) {
        for (int side = 0; side < ROUND_SIDES; side++) {
            add(faces, part, tone,
                    ring(ROUND_SIDES, radius, from, side), ring(ROUND_SIDES, radius, from, side + 1),
                    ring(ROUND_SIDES, radius, to, side + 1), ring(ROUND_SIDES, radius, to, side));
        }
    }

    /**
     * A flat ring across the axis at one height.
     *
     * @param up whether it faces the eraser end rather than the tip
     */
    private static void annulus(List<Face> faces, Part part, float tone, float inner, float outer, float z,
                                boolean up) {
        for (int side = 0; side < ROUND_SIDES; side++) {
            float[] a = ring(ROUND_SIDES, inner, z, side);
            float[] b = ring(ROUND_SIDES, inner, z, side + 1);
            float[] c = ring(ROUND_SIDES, outer, z, side + 1);
            float[] d = ring(ROUND_SIDES, outer, z, side);
            faces.add(new Face(part, join(a, b, c, d), 0.0F, 0.0F, up ? 1.0F : -1.0F, tone));
        }
    }

    /** A corner of a regular polygon round the axis at a height. */
    private static float[] ring(int sides, float radius, float z, int corner) {
        double angle = corner * (Math.PI * 2.0D / sides);
        return new float[]{(float) (Math.cos(angle) * radius), (float) (Math.sin(angle) * radius), z};
    }

    /**
     * A face round the side of the pencil, lit by the normal its own corners give it - turned to face away
     * from the axis, whichever way round the corners happened to be listed. The flat ends are not made
     * here; {@link #annulus} knows which way those face.
     */
    private static void add(List<Face> faces, Part part, float tone, float[] a, float[] b, float[] c, float[] d) {
        // Two diagonals rather than two edges, so a quad with a doubled corner still has a normal.
        double[] across = {c[0] - a[0], c[1] - a[1], c[2] - a[2]};
        double[] along = {d[0] - b[0], d[1] - b[1], d[2] - b[2]};
        double nx = across[1] * along[2] - across[2] * along[1];
        double ny = across[2] * along[0] - across[0] * along[2];
        double nz = across[0] * along[1] - across[1] * along[0];
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= length;
        ny /= length;
        nz /= length;
        double midX = (a[0] + b[0] + c[0] + d[0]) * 0.25D;
        double midY = (a[1] + b[1] + c[1] + d[1]) * 0.25D;
        // Every side face leans outward from the axis, the cones included, so that is the way to face.
        if (nx * midX + ny * midY < 0.0D) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        faces.add(new Face(part, join(a, b, c, d), (float) nx, (float) ny, (float) nz, tone));
    }

    private static float[] join(float[] a, float[] b, float[] c, float[] d) {
        return new float[]{a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2]};
    }
}
