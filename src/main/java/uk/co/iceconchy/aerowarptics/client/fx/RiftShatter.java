package uk.co.iceconchy.aerowarptics.client.fx;

import net.minecraft.util.Mth;

/**
 * How a rift breaks space open.
 *
 * <p>An aperture does not iris open. It arrives the way a stone arrives through a window: a hard point
 * of impact, radial cracks racing out from it, concentric fracture rings crossing them, and then the
 * panes between falling away. That is the actual fracture pattern of struck glass, and it is what this
 * produces - {@link #CRACKS} radial fractures crossed by {@link #RINGS} rings, giving one shard per
 * cell.
 *
 * <p>Plain arithmetic and a hash, for the same reason {@link RiftTear} is: every viewer works the same
 * fracture out from the rift's own position, so two players watching one aperture see the same glass
 * break without a byte being sent about it.
 *
 * <h2>What this must not do</h2>
 * The face of an aperture is the thing that hides an airship going through it, and {@link RiftTear}
 * guarantees the rim never cuts inside the circle doing the hiding. Nothing here is allowed to weaken
 * that. The shatter is the <em>presentation</em> of an aperture opening, not the mechanism: it runs
 * entirely within the opening budget, which the flight planner floors at twenty ticks of approach, and
 * the hole is fully open long before a bow reaches the plane. Shards are drawn outside the occluding
 * surface and hide nothing.
 */
public final class RiftShatter {

    /** Radial fractures running out from the impact. */
    public static final int CRACKS = 13;

    /** Concentric fracture rings crossing them. */
    public static final int RINGS = 4;

    /** One shard per cell of the fracture. */
    public static final int SHARDS = CRACKS * RINGS;

    /**
     * Fraction of the opening spent cracking before anything breaks free.
     *
     * <p>Everything up to here is a lit fracture over an intact view: the world behind the rift is
     * still there to see. The hole only exists afterwards.
     */
    public static final float CRACK_PHASE = 0.5F;

    /** Ticks a shard drifts before it has faded to nothing. */
    public static final float SHARD_LIFE = 45.0F;

    /** Ticks between the middle of the pane breaking and the rim breaking. */
    public static final float BREAK_SPREAD = 6.0F;

    /**
     * How much of a seal is spent waiting, for the piece that comes home last.
     *
     * <p>Space closes from the rim inwards, which is the opposite order to the way it broke. The
     * outermost pieces are back in place while the middle is still open, so the hole shuts down to a
     * point rather than fading out evenly - and a point is something the eye can watch close.
     */
    public static final float SEAL_STAGGER = 0.35F;

    private RiftShatter() {
    }

    /**
     * One piece of broken space.
     *
     * <p>Angles and radii are the cell it was cut from - radii as fractions of the rim - and the rest
     * is how it leaves. Nothing here is in world units; the renderer scales it to the aperture.
     *
     * @param delay   ticks after the break begins before this piece detaches
     * @param outward how hard it is thrown away from the impact, as a fraction of the rim
     * @param push    how hard it is thrown along the aperture's normal, signed, so a pane bursts both ways
     * @param spin    radians per tick of travel it tumbles through
     * @param axis    which way its tumble axis lies in the aperture's plane
     */
    public record Shard(float angle0, float angle1, float innerT, float outerT,
                        float delay, float outward, float push, float spin, float axis) {

        public float midAngle() {
            return (angle0 + angle1) * 0.5F;
        }

        public float midRadius() {
            return (innerT + outerT) * 0.5F;
        }
    }

    // ------------------------------------------------------------------ seed

    /**
     * A stable seed for the rift at a position.
     *
     * <p>Quantised rather than taken from the raw doubles, so a rift is the same rift to every client
     * even if a coordinate arrives a hair different. Two apertures in the same warp are metres apart,
     * so sixteenths are far finer than they need to be to tell them apart - and the entry and exit
     * rifts of one jump breaking differently is the point.
     */
    public static int seedFor(double x, double y, double z) {
        int xi = Mth.floor(x * 16.0D);
        int yi = Mth.floor(y * 16.0D);
        int zi = Mth.floor(z * 16.0D);
        return xi * 0x9E3779B1 ^ yi * 0x85EBCA77 ^ zi * 0xC2B2AE3D;
    }

    /** A repeatable value in {@code [0, 1)} for a seed and an index. */
    public static float noise(int seed, int index) {
        int h = seed * 0x27D4EB2F + index * 0x165667B1;
        h ^= h >>> 15;
        h *= 0x2545F491;
        h ^= h >>> 13;
        h *= 0x27D4EB2F;
        h ^= h >>> 16;
        return (h >>> 8) / (float) (1 << 24);
    }

    // -------------------------------------------------------------- fracture

    /**
     * Where the radial cracks run, in ascending order.
     *
     * <p>Jitter is bounded well inside half a gap on purpose. Two cracks that swapped places would
     * make a shard of negative width, which draws as a fold rather than a piece of glass - so the
     * bound is what keeps the ordering true without a sort.
     */
    public static float[] crackAngles(int seed) {
        float[] angles = new float[CRACKS];
        float gap = (float) (Math.PI * 2.0D / CRACKS);
        for (int crack = 0; crack < CRACKS; crack++) {
            angles[crack] = crack * gap + (noise(seed, crack) - 0.5F) * gap * 0.7F;
        }
        return angles;
    }

    /**
     * Where the concentric fracture rings sit, as fractions of the rim, from the impact outwards.
     *
     * <p>Bunched towards the middle, which is where struck glass actually rings closest together.
     */
    public static float[] ringRadii(int seed) {
        float[] radii = new float[RINGS + 1];
        radii[0] = 0.0F;
        radii[RINGS] = 1.0F;
        for (int ring = 1; ring < RINGS; ring++) {
            float base = spacing(ring);
            float gap = base - spacing(ring - 1);
            radii[ring] = base + (noise(seed, 101 + ring) - 0.5F) * gap * 0.5F;
        }
        return radii;
    }

    private static float spacing(int ring) {
        return (float) Math.pow(ring / (double) RINGS, 1.35D);
    }

    /** The whole fracture: one shard per cell, ordered from the impact outwards. */
    public static Shard[] fracture(int seed) {
        float[] angles = crackAngles(seed);
        float[] radii = ringRadii(seed);
        Shard[] shards = new Shard[SHARDS];

        int index = 0;
        for (int ring = 0; ring < RINGS; ring++) {
            for (int crack = 0; crack < CRACKS; crack++) {
                float angle0 = angles[crack];
                // The last cell closes the loop onto the first crack, one turn on.
                float angle1 = crack + 1 < CRACKS
                        ? angles[crack + 1]
                        : angles[0] + (float) (Math.PI * 2.0D);
                float inner = radii[ring];
                float outer = radii[ring + 1];
                float mid = (inner + outer) * 0.5F;

                int n = 1_000 + index * 7;
                shards[index++] = new Shard(angle0, angle1, inner, outer,
                        // The break runs outwards from the impact rather than happening at once.
                        mid * BREAK_SPREAD,
                        0.30F + 0.85F * mid + 0.35F * noise(seed, n),
                        (noise(seed, n + 1) - 0.5F) * 2.0F,
                        (noise(seed, n + 2) - 0.5F) * 0.55F,
                        noise(seed, n + 3) * (float) (Math.PI * 2.0D));
            }
        }
        return shards;
    }

    // ----------------------------------------------------------------- shape

    /**
     * How open the hole is, given progress through the whole opening.
     *
     * <p>Nothing for the whole crack phase - the fracture is lit but the world behind it is intact -
     * and then the hole arrives fast. Space does not ease open.
     */
    public static float hole(float openProgress) {
        if (openProgress <= CRACK_PHASE) {
            return 0.0F;
        }
        float t = Mth.clamp((openProgress - CRACK_PHASE) / (1.0F - CRACK_PHASE), 0.0F, 1.0F);
        float remaining = 1.0F - t;
        return 1.0F - remaining * remaining * remaining;
    }

    /** How far the cracks have run, 0..1, given progress through the crack phase. */
    public static float crackReach(float crackProgress) {
        float t = Mth.clamp(crackProgress, 0.0F, 1.0F);
        float remaining = 1.0F - t;
        return 1.0F - remaining * remaining * remaining;
    }

    /**
     * How far along its arc a shard has travelled, 0..1.
     *
     * <p>Eased out, so a piece bursts away and then coasts. There is nothing in here to slow it down,
     * but a fragment that kept accelerating would read as being blown rather than as having been let
     * go of.
     */
    public static float travel(float life) {
        float t = Mth.clamp(life, 0.0F, 1.0F);
        float remaining = 1.0F - t;
        return 1.0F - remaining * remaining;
    }

    /**
     * How far through its own return a piece is, given how far through the seal the aperture is.
     *
     * <p>Negative before this piece has started coming back, past one once it is home.
     *
     * @param midRadius where the piece sits, from the middle of the pane out to the rim
     */
    public static float sealLife(float sealProgress, float midRadius) {
        float waited = (1.0F - midRadius) * SEAL_STAGGER;
        return (sealProgress - waited) / (1.0F - SEAL_STAGGER);
    }

    /**
     * How visible a returning shard is, 0..1.
     *
     * <p>The mirror of {@link #fade}: it arrives out of nothing, is brightest on the way in, and is
     * gone by the time it lands. A piece still visible when it gets home would be a shard sitting in a
     * hole that has just closed over it.
     */
    public static float sealFade(float life) {
        if (life <= 0.0F || life >= 1.0F) {
            return 0.0F;
        }
        float rise = Mth.clamp(life * 5.0F, 0.0F, 1.0F);
        float fall = 1.0F - life * life * life;
        return rise * fall;
    }

    /**
     * How visible a shard is, 0..1, given how far through its life it is.
     *
     * <p>Snaps in as it detaches and falls away over the rest, so the break is an event and the drift
     * afterwards is a long quiet tail.
     */
    public static float fade(float life) {
        if (life <= 0.0F || life >= 1.0F) {
            return 0.0F;
        }
        float rise = Mth.clamp(life * 14.0F, 0.0F, 1.0F);
        return rise * (1.0F - life * life);
    }
}
