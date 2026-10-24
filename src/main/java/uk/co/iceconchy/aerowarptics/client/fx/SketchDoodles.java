package uk.co.iceconchy.aerowarptics.client.fx;

import java.util.ArrayList;
import java.util.List;

/**
 * The little drawings a Sketch rift's tunnel is scribbled with: the sort of thing anybody doodles in
 * the margin of a page, sailing past the crew on their way through.
 *
 * <p>Each one is a handful of polylines in page space and nothing more. They do not animate and they do
 * not have to: the tunnel carries them past at speed, and a drawing that moves as well would be a lot
 * to take in through a porthole. What matters is that each is recognisable in the moment it is in view,
 * which is why they are simple closed shapes rather than detailed ones.
 *
 * <p>Free of Minecraft, and a pure function of which drawing it is, so every crew member sees the same
 * doodles in the same places.
 */
public final class SketchDoodles {

    /** How many different drawings there are. */
    public static final int COUNT = 6;

    /** How far out any of them reaches, so none of them is drawn over the edge of its own patch. */
    private static final float EDGE = 0.92F;

    private SketchDoodles() {
    }

    /**
     * One drawing, as polylines of {@code u, v} pairs in page space, which runs from -1 to 1 both ways.
     *
     * <p>The index wraps, so a caller can hand it a doodle's id and not care how many there are.
     */
    public static List<float[]> strokes(int which) {
        return switch (Math.floorMod(which, COUNT)) {
            case 0 -> airship();
            case 1 -> cloud();
            case 2 -> star();
            case 3 -> birds();
            case 4 -> sun();
            default -> spiral();
        };
    }

    /** The obvious thing for a crew of airshipmen to have doodled. */
    private static List<float[]> airship() {
        float balloonY = 0.22F;
        float wide = 0.52F;
        float tall = 0.27F;
        List<float[]> strokes = new ArrayList<>(6);
        strokes.add(ellipse(0.0F, balloonY, wide, tall, 16));
        // A fin at the back, so it is pointing somewhere rather than just floating.
        strokes.add(new float[]{-wide * 0.9F, balloonY, -wide * 1.35F, balloonY + tall * 0.95F,
                -wide * 1.35F, balloonY - tall * 0.55F, -wide * 0.9F, balloonY});
        float deckY = balloonY - tall - 0.18F;
        float deck = 0.20F;
        strokes.add(new float[]{-deck, deckY + 0.09F, deck, deckY + 0.09F, deck * 0.78F, deckY - 0.09F,
                -deck * 0.78F, deckY - 0.09F, -deck, deckY + 0.09F});
        strokes.add(new float[]{-deck * 0.8F, deckY + 0.09F, -wide * 0.42F, balloonY - tall * 0.88F});
        strokes.add(new float[]{deck * 0.8F, deckY + 0.09F, wide * 0.42F, balloonY - tall * 0.88F});
        // The propeller, stopped: two blades and the shaft it is on.
        strokes.add(new float[]{wide * 0.9F, balloonY, wide * 1.2F, balloonY});
        strokes.add(new float[]{wide * 1.2F, balloonY - 0.16F, wide * 1.2F, balloonY + 0.16F});
        return List.copyOf(strokes);
    }

    /** Bumps along a line, which is how everybody draws a cloud. */
    private static List<float[]> cloud() {
        int bumps = 4;
        float span = EDGE * 2.0F / bumps;
        float[] points = new float[(bumps * 5 + 1) * 2];
        int at = 0;
        for (int bump = 0; bump < bumps; bump++) {
            float from = -EDGE + bump * span;
            float rise = bump == 1 || bump == 2 ? 0.46F : 0.32F;
            for (int step = 0; step < 5; step++) {
                double angle = Math.PI * (1.0D - step / 5.0D);
                points[at++] = from + span * 0.5F + (float) Math.cos(angle) * span * 0.5F;
                points[at++] = -0.22F + (float) Math.sin(angle) * rise;
            }
        }
        points[at++] = EDGE;
        points[at] = -0.22F;
        return List.of(points);
    }

    /** Five points, drawn without lifting the pencil, the way a star is doodled. */
    private static List<float[]> star() {
        float[] points = new float[11 * 2];
        for (int point = 0; point <= 10; point++) {
            double angle = Math.PI * 0.5D + point * Math.PI / 5.0D;
            float reach = point % 2 == 0 ? EDGE : EDGE * 0.42F;
            points[point * 2] = (float) Math.cos(angle) * reach;
            points[point * 2 + 1] = (float) Math.sin(angle) * reach;
        }
        return List.of(points);
    }

    /** Two gulls: the pair of arcs everybody puts in the sky of a drawing. */
    private static List<float[]> birds() {
        return List.of(gull(-0.30F, 0.18F, 0.55F), gull(0.38F, -0.28F, 0.38F));
    }

    /** One bird: both wings in a single stroke, tips up, a dip in each, and the body between them. */
    private static float[] gull(float centreX, float centreY, float size) {
        int steps = 16;
        float[] points = new float[(steps + 1) * 2];
        for (int step = 0; step <= steps; step++) {
            float along = step / (float) steps * 2.0F - 1.0F;
            points[step * 2] = centreX + along * size;
            points[step * 2 + 1] = centreY + (float) Math.cos(along * Math.PI * 2.0D) * size * 0.24F;
        }
        return points;
    }

    /** A ring with rays, which is a sun in every drawing anybody has ever made. */
    private static List<float[]> sun() {
        List<float[]> strokes = new ArrayList<>(9);
        strokes.add(ellipse(0.0F, 0.0F, 0.40F, 0.40F, 16));
        for (int ray = 0; ray < 8; ray++) {
            double angle = ray * Math.PI * 0.25D;
            strokes.add(new float[]{
                    (float) Math.cos(angle) * 0.54F, (float) Math.sin(angle) * 0.54F,
                    (float) Math.cos(angle) * EDGE, (float) Math.sin(angle) * EDGE});
        }
        return List.copyOf(strokes);
    }

    /** The thing a pencil draws while its owner is thinking about something else. */
    private static List<float[]> spiral() {
        int steps = 44;
        float turns = 2.75F;
        float[] points = new float[(steps + 1) * 2];
        for (int step = 0; step <= steps; step++) {
            double angle = step / (double) steps * turns * Math.PI * 2.0D;
            float reach = 0.06F + (EDGE - 0.06F) * step / (float) steps;
            points[step * 2] = (float) Math.cos(angle) * reach;
            points[step * 2 + 1] = (float) Math.sin(angle) * reach;
        }
        return List.of(points);
    }

    /** A closed ring of points: the balloon, and the sun's face. */
    private static float[] ellipse(float centreX, float centreY, float wide, float tall, int steps) {
        float[] points = new float[(steps + 1) * 2];
        for (int point = 0; point <= steps; point++) {
            double angle = Math.PI * 2.0D * point / steps;
            points[point * 2] = centreX + (float) Math.cos(angle) * wide;
            points[point * 2 + 1] = centreY + (float) Math.sin(angle) * tall;
        }
        return points;
    }
}
