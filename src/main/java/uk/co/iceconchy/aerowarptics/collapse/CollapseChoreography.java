package uk.co.iceconchy.aerowarptics.collapse;

import java.util.Arrays;
import java.util.Comparator;

/**
 * What a collapse looks like, and what it does to the ground round it, as arithmetic.
 *
 * <p>A collapse is a set piece in five beats, each a stretch of the ritual's own progress, so a storm that
 * shortens a ritual plays the same scene faster:
 * <ol>
 *   <li><b>Spin-up</b> (to {@link CollapseRules#SPIN_UP_END}). The shaft screams up, the lock ring on the roof
 *       turns, the Rift Core rises into the middle of the cradle's rings where the hole will be, and the
 *       offerings lift off their depots.</li>
 *   <li><b>Opening</b> (to {@link CollapseRules#OPENING_END}). A rift tears open over the cradle, dressed by
 *       a Rift Modulator beside it; the offerings fly to it and fall into orbit; a column of light climbs out
 *       of it into the sky, and the day round the site starts to dim.</li>
 *   <li><b>The fold</b> (at {@link #FOLD}). The rift closes down onto a ball of darkness growing in its middle
 *       - a rift folded shut on itself, a hole with no far side. From here on there is no aperture, only the
 *       hole, a disc of light turning round it and space bending past its edge.</li>
 *   <li><b>Winding</b> (to {@link #HUSH}). The orbit tightens and quickens, loose things round the cradle are
 *       drawn in, lightning reaches out to the stabilisers, and everything shows how unsteady it is.</li>
 *   <li><b>The hush</b> (the last twentieth). Silence; the column falls into the hole, the offerings spiral
 *       in, the heart is drawn up out of its cup, and the hole shrinks to a point - then the flash, the boom
 *       and the ring of air, and how it ended.</li>
 * </ol>
 *
 * <p>Heights are in blocks above the cradle's base and positions relative to the middle of its master block,
 * so the renderer that draws the offerings and the client that draws the hole agree on where everything is
 * without either asking the other. The server reads the pull and the blast from here too, so the forces a
 * player feels are the ones the client draws. What the machine itself does meanwhile - its rings, lock ring
 * and pistons - is {@link CradleWorks}, on the same clock.
 *
 * <p>The machine is three blocks across and three high, and the hole forms inside it, in the middle of the
 * Relic Armillary's rings: so the rift and the hole are sized to the innermost ring, and the offerings orbit
 * outside the pillars.
 *
 * <p>Free of Minecraft, for {@code CollapseChoreographyTest}: a piece that jumps at a beat, a hole that
 * shrinks early or a pull that pushes are none of them things that throw.
 */
public final class CollapseChoreography {

    /** Where the Scar Heart sits, on the cradle's sun gear: its middle, drawn standing on the gear's top. */
    public static final double CUP = 17.5D / 16.0D;
    /** Where the Rift Core hangs over the heart while the cradle is idle, under the rings. */
    public static final double HOVER = 23.0D / 16.0D;
    /** The middle of the hole, of the rings round it, and of the orbit: {@link CradleWorks#CENTRE} in blocks. */
    public static final double CENTRE = CradleWorks.CENTRE / 16.0D;
    /** Where whatever a collapse throws out comes from: out of the opening in the roof, just over it. */
    public static final double SPOUT = 3.2D;
    /** Where a Create Depot shows its item, above the depot's own base. */
    public static final double DEPOT_REST = 0.85D;
    /** How high an offering lifts over its depot before it flies. */
    public static final double LIFT = 1.6D;

    /**
     * When the Rift Core starts to rise; when it is up under the rings; and by when the rift tearing round it has
     * drawn it the rest of the way, into the middle of where the hole will be.
     */
    public static final double CORE_LIFT_FROM = 0.02D;
    public static final double CORE_HANDED_OFF = 0.09D;
    public static final double CORE_RISEN = 0.12D;
    /** How high the core rises before the rift takes it: its top just under the rings, in blocks. */
    public static final double HAND_OFF = 25.5D / 16.0D;
    /** By when every offering has reached its orbit. */
    public static final double ORBIT_FROM = 0.22D;
    /** When the column starts to climb. */
    public static final double COLUMN_FROM = 0.12D;
    /** When the hole starts to grow inside the rift. */
    public static final double HOLE_FROM = 0.24D;
    /** When the rift has closed down onto the hole, and seals. */
    public static final double FOLD = 0.40D;
    /** When the disc of light round the hole begins, and when it is whole. */
    public static final double DISC_FROM = 0.26D;
    public static final double DISC_WHOLE = 0.42D;
    /** When the winding starts: the rift is open and the offerings are in orbit. */
    public static final double WINDING_FROM = CollapseRules.OPENING_END;
    /** The hush: the last stretch, when everything falls to a point. */
    public static final double HUSH = CollapseRules.WINDING_END;

    /** The rift at its widest, before it folds: inside the innermost ring, which it is torn open within. */
    public static final double APERTURE_WIDE = 0.55D;
    /** The rift's first size, the moment it tears. */
    public static final double APERTURE_FIRST = 0.12D;
    /** How much wider than the hole the rift is when it seals onto it. */
    public static final double APERTURE_OVER_HOLE = 1.05D;
    /** The hole's radius at the fold, and at the hush: kept clear of the innermost ring as it shakes. */
    public static final double HOLE_FORMED = 0.3D;
    public static final double HOLE_FULL = 0.45D;
    /** The orbit's radius as the offerings arrive, and as the hush begins: outside the pillars, at 2.12. */
    public static final double ORBIT_WIDE = 3.0D;
    public static final double ORBIT_TIGHT = 2.4D;
    /** How far an offering's flight to its orbit bows upward, at its middle. */
    public static final double FLIGHT_BOW = 0.8D;
    /** How far the column reaches above the hole, at full height. A landmark: it clears any hill. */
    public static final double COLUMN_HEIGHT = 320.0D;

    /**
     * How many turns the orbit has made by a progress past {@link #ORBIT_FROM}: {@code A s + B s^2}, so it
     * starts at about a turn every twelve seconds of a minute-long ritual and ends at about two turns every
     * three. Slower than it once turned, because it is wider - it goes round the machine now, not over it - and
     * what an offering must never do is outrun the half a block a tick {@code CollapseChoreographyTest} allows.
     */
    private static final double TURNS_A = 5.0D;
    private static final double TURNS_B = 22.0D;

    /** The pull at the start of the winding, at its end, and in the hush - blocks per tick, per tick. */
    public static final double PULL_FIRST = 0.004D;
    public static final double PULL_LAST = 0.014D;
    public static final double PULL_HUSH = 0.05D;
    /** The fastest the pull will draw anything in, winding and in the hush - blocks per tick. */
    public static final double PULL_CAP = 0.12D;
    public static final double PULL_CAP_HUSH = 0.30D;
    /** Nothing is pulled this close to the cradle's axis, so what arrives there stays put. */
    public static final double PULL_DEAD_ZONE = 0.8D;
    public static final double PULL_FULL_FROM = 2.0D;

    /** How far the dimming reaches at full strength, and where it is gone. */
    public static final double DIM_FULL_WITHIN = 32.0D;
    public static final double DIM_GONE_BEYOND = 160.0D;

    /** How far the collapse's jolt is felt through the camera. */
    public static final double SHAKE_FULL_WITHIN = 4.0D;
    public static final double SHAKE_GONE_BEYOND = 64.0D;

    /** Slowest and quickest the heart beats, in ticks between beats; and the least it will ever wait. */
    public static final int BEAT_SLOW = 40;
    public static final int BEAT_WINDING_FIRST = 34;
    public static final int BEAT_WINDING_LAST = 12;
    public static final int BEAT_FASTEST = 6;

    /** Slowest and quickest the lightning reaches for each stabiliser, in ticks. */
    public static final int ARC_SLOW = 50;
    public static final int ARC_QUICK = 14;
    public static final int ARC_FASTEST = 6;
    /** How long each reach lasts, in ticks. */
    public static final int ARC_LIFE = 3;

    private CollapseChoreography() {
    }

    // ------------------------------------------------------------------ the hole

    /** How far through the hush, 0 before it and 1 at the collapse. */
    public static double hush(double progress) {
        return span(progress, HUSH, 1.0D);
    }

    /** The hole's radius: nothing until it forms inside the rift, growing through the winding, gone at the end. */
    public static double holeRadius(double progress) {
        if (progress < HOLE_FROM) {
            return 0.0D;
        }
        if (progress < FOLD) {
            return HOLE_FORMED * smooth(span(progress, HOLE_FROM, FOLD));
        }
        if (progress < HUSH) {
            return lerp(span(progress, FOLD, HUSH), HOLE_FORMED, HOLE_FULL);
        }
        // Falling to a point, faster and faster: the last of it goes in a blink.
        double hush = hush(progress);
        return HOLE_FULL * (1.0D - hush * hush * hush);
    }

    /**
     * The rift's radius while it stands, or 0 when there is none: it tears at the end of the spin-up, opens
     * wide, then closes down onto the hole growing inside it and seals at the fold.
     */
    public static double apertureRadius(double progress) {
        if (progress < CollapseRules.SPIN_UP_END || progress >= FOLD) {
            return 0.0D;
        }
        if (progress < HOLE_FROM) {
            return lerp(smooth(span(progress, CollapseRules.SPIN_UP_END, HOLE_FROM)), APERTURE_FIRST, APERTURE_WIDE);
        }
        return lerp(smooth(span(progress, HOLE_FROM, FOLD)), APERTURE_WIDE,
                holeRadius(progress) * APERTURE_OVER_HOLE);
    }

    /** How whole the disc of light round the hole is, 0..1. */
    public static double disc(double progress) {
        return smooth(span(progress, DISC_FROM, DISC_WHOLE));
    }

    /**
     * How hard the view is bent round the hole, 0..1: building through the winding, and at its strongest as
     * everything falls in.
     */
    public static double lens(double progress) {
        if (progress < WINDING_FROM) {
            return 0.0D;
        }
        if (progress < HUSH) {
            return 0.55D * smooth(span(progress, WINDING_FROM, HUSH));
        }
        return lerp(hush(progress), 0.55D, 1.0D);
    }

    /** How much of the column stands, 0..1 of {@link #COLUMN_HEIGHT}: it shoots up, stands, and falls into the hole. */
    public static double column(double progress) {
        if (progress < COLUMN_FROM) {
            return 0.0D;
        }
        if (progress < HUSH) {
            double rise = 1.0D - span(progress, COLUMN_FROM, WINDING_FROM);
            return 1.0D - rise * rise * rise;
        }
        return 1.0D - smooth(hush(progress));
    }

    // ------------------------------------------------------------ what is on the altar

    /**
     * How high the Rift Core is drawn: rising from over the heart to just under the rings, held there a moment,
     * then drawn up by the rift into the middle of the hole to be.
     */
    public static double coreHeight(double progress) {
        if (progress < CORE_HANDED_OFF) {
            return lerp(smooth(span(progress, CORE_LIFT_FROM, CORE_HANDED_OFF)), HOVER, HAND_OFF);
        }
        return lerp(smooth(span(progress, CORE_HANDED_OFF, CORE_RISEN)), HAND_OFF, CENTRE);
    }

    /** How large the Rift Core is drawn: whole, until the hush crushes it to nothing. */
    public static double coreScale(double progress) {
        return 1.0D - smooth(hush(progress));
    }

    /** How high the Scar Heart is drawn: in its cup until the hush, then drawn up into the point. */
    public static double heartHeight(double progress) {
        double hush = hush(progress);
        return lerp(hush * hush, CUP, CENTRE);
    }

    /** How large the Scar Heart is drawn: whole, until it is swallowed. */
    public static double heartScale(double progress) {
        return 1.0D - smooth(span(progress, HUSH + (1.0D - HUSH) * 0.5D, 1.0D));
    }

    // ---------------------------------------------------------------- the offerings

    /** The orbit's radius: tightening through the winding, and spiralling into the point in the hush. */
    public static double orbitRadius(double progress) {
        if (progress < HUSH) {
            return lerp(smooth(span(progress, ORBIT_FROM, HUSH)), ORBIT_WIDE, ORBIT_TIGHT);
        }
        double hush = hush(progress);
        return ORBIT_TIGHT * (1.0D - hush * hush);
    }

    /** Turns the orbit has made by now, speeding up as it goes; nothing before the offerings arrive. */
    public static double orbitTurns(double progress) {
        double s = Math.max(0.0D, progress - ORBIT_FROM);
        return TURNS_A * s + TURNS_B * s * s;
    }

    /** How fast the orbit turns at a moment, in turns per whole ritual. */
    public static double orbitSpeed(double progress) {
        double s = Math.max(0.0D, progress - ORBIT_FROM);
        return TURNS_A + 2.0D * TURNS_B * s;
    }

    /** How large an offering is drawn, as a share of its size: whole until the hush swallows it. */
    public static double offeringScale(double progress) {
        return 1.0D - smooth(hush(progress));
    }

    /**
     * Where each offering's place in the orbit is, as an angle - evenly round the ring, and handed out in the
     * order the depots stand round the cradle so no two paths to the ring cross.
     *
     * @param offsets each offering's depot, {x, z} from the middle of the cradle's block
     */
    public static double[] slotAngles(double[][] offsets) {
        int count = offsets.length;
        double[] angles = new double[count];
        if (count == 0) {
            return angles;
        }
        Integer[] order = new Integer[count];
        for (int i = 0; i < count; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingDouble(i -> Math.atan2(offsets[i][1], offsets[i][0])));
        double first = Math.atan2(offsets[order[0]][1], offsets[order[0]][0]);
        for (int slot = 0; slot < count; slot++) {
            angles[order[slot]] = first + Math.PI * 2.0D * slot / count;
        }
        return angles;
    }

    /**
     * Where an offering is, {x, y, z} from the middle of the cradle's base.
     *
     * <p>Lifted off its depot through the spin-up, flown along a bowed path to its place in the orbit while the
     * rift opens, and turned round the hole from then on - continuous at every hand-over, so a piece never
     * jumps.
     *
     * @param progress how far through the ritual
     * @param depot    the depot the offering came off, {x, z} from the middle of the cradle's block
     * @param slot     its place in the orbit, from {@link #slotAngles}
     * @param tiltX    how the orbit's plane is tipped, as a slope along x - see {@link #orbitPoint}
     * @param tiltZ    and along z
     */
    public static double[] offering(double progress, double[] depot, double slot, double tiltX, double tiltZ) {
        if (progress < CollapseRules.SPIN_UP_END) {
            double rise = smooth(span(progress, 0.0D, CollapseRules.SPIN_UP_END));
            return new double[]{depot[0], lerp(rise, DEPOT_REST, LIFT), depot[1]};
        }
        if (progress < ORBIT_FROM) {
            double flight = smooth(span(progress, CollapseRules.SPIN_UP_END, ORBIT_FROM));
            double[] to = orbitPoint(orbitRadius(ORBIT_FROM), slot, tiltX, tiltZ);
            return new double[]{
                    lerp(flight, depot[0], to[0]),
                    lerp(flight, LIFT, to[1]) + FLIGHT_BOW * Math.sin(Math.PI * flight),
                    lerp(flight, depot[1], to[2])};
        }
        double angle = slot + Math.PI * 2.0D * orbitTurns(progress);
        return orbitPoint(orbitRadius(progress), angle, tiltX, tiltZ);
    }

    /**
     * A point on the orbit, {x, y, z} from the middle of the cradle's base: round the hole's middle, in a plane
     * tipped by a slope along each axis. Sheared rather than turned - at the few tenths of slope an unsteady
     * collapse tips it by, the two cannot be told apart, and the disc of light is drawn in the same plane.
     */
    public static double[] orbitPoint(double radius, double angle, double tiltX, double tiltZ) {
        double x = Math.cos(angle) * radius;
        double z = Math.sin(angle) * radius;
        return new double[]{x, CENTRE - (tiltX * x + tiltZ * z), z};
    }

    // ------------------------------------------------------------------- the ground

    /** How dark the day is drawn over the site, 0..1: dimming as the rift opens, darkest in the hush. */
    public static double darkness(double progress) {
        if (progress < CollapseRules.SPIN_UP_END) {
            return 0.15D * span(progress, 0.0D, CollapseRules.SPIN_UP_END);
        }
        if (progress < WINDING_FROM) {
            return lerp(span(progress, CollapseRules.SPIN_UP_END, WINDING_FROM), 0.15D, 0.45D);
        }
        if (progress < HUSH) {
            return lerp(span(progress, WINDING_FROM, HUSH), 0.45D, 0.7D);
        }
        return lerp(hush(progress), 0.7D, 0.9D);
    }

    /** How much of the dimming reaches a viewer this far from the cradle, 0..1. */
    public static double darknessReach(double distance) {
        return 1.0D - smooth(span(distance, DIM_FULL_WITHIN, DIM_GONE_BEYOND));
    }

    /** The pull's strength at a moment, in blocks per tick per tick: none until the winding. */
    public static double pull(double progress) {
        if (progress < WINDING_FROM) {
            return 0.0D;
        }
        if (progress < HUSH) {
            return lerp(span(progress, WINDING_FROM, HUSH), PULL_FIRST, PULL_LAST);
        }
        return PULL_HUSH;
    }

    /** The fastest the pull draws anything in at a moment, in blocks per tick. */
    public static double pullCap(double progress) {
        return progress < HUSH ? PULL_CAP : PULL_CAP_HUSH;
    }

    /**
     * How much of the pull reaches this far from the cradle's axis, 0..1: nothing at the axis itself, so what
     * arrives there stays put, the most just outside it, and nothing at the edge of its range or past it.
     */
    public static double pullReach(double distance, double range) {
        if (range <= 0.0D) {
            return 0.0D;
        }
        return smooth(span(distance, PULL_DEAD_ZONE, PULL_FULL_FROM)) * clamp01(1.0D - distance / range);
    }

    /**
     * Something's horizontal velocity, pulled towards the cradle's axis.
     *
     * <p>The pull adds to what it is already doing, and only while it is drifting inward slower than the cap:
     * a creature walking away still walks away, one already falling in faster is left alone, and nothing is
     * ever pushed outward.
     *
     * @param vx     its velocity along x, blocks per tick
     * @param vz     along z
     * @param toX    the way to the axis along x, any length
     * @param toZ    along z
     * @param strength the pull, from {@link #pull} times {@link #pullReach}
     * @param cap    from {@link #pullCap}
     * @return {vx, vz} afterwards
     */
    public static double[] pulled(double vx, double vz, double toX, double toZ, double strength, double cap) {
        double length = Math.sqrt(toX * toX + toZ * toZ);
        if (length < 1.0e-6D || strength <= 0.0D) {
            return new double[]{vx, vz};
        }
        double ux = toX / length;
        double uz = toZ / length;
        double inward = vx * ux + vz * uz;
        double room = cap - inward;
        if (room <= 0.0D) {
            return new double[]{vx, vz};
        }
        double added = Math.min(strength, room);
        return new double[]{vx + ux * added, vz + uz * added};
    }

    // -------------------------------------------------------------------- the blast

    /** How hard each ending throws what stands round it, at the cradle itself - blocks per tick. */
    public static double blastForce(CollapseRules.Outcome outcome) {
        return switch (outcome) {
            case SETTLED -> 0.6D;
            case FLUNG -> 0.9D;
            case SPAT -> 1.3D;
        };
    }

    /** How much of the blast's harm each ending deals: a settled collapse hurts nobody. */
    public static double harmShare(CollapseRules.Outcome outcome) {
        return switch (outcome) {
            case SETTLED -> 0.0D;
            case FLUNG -> 0.5D;
            case SPAT -> 1.0D;
        };
    }

    /** How hard the blast throws something this far from the cradle outward - nothing past its range. */
    public static double knockback(double distance, double range, CollapseRules.Outcome outcome) {
        if (range <= 0.0D || distance >= range) {
            return 0.0D;
        }
        return blastForce(outcome) * (1.0D - distance / range);
    }

    /** How hard it throws that same thing upward, so it lifts off the ground rather than sliding along it. */
    public static double knockUp(double distance, double range) {
        if (range <= 0.0D || distance >= range) {
            return 0.0D;
        }
        return 0.2D + 0.25D * (1.0D - distance / range);
    }

    /**
     * The harm the blast deals something this far from the cradle, in half-hearts, before cover: the most at
     * the cradle, nothing at the edge of its range, and nothing at all from a settled collapse.
     */
    public static double damage(double distance, double range, double most, CollapseRules.Outcome outcome) {
        if (range <= 0.0D || distance >= range || most <= 0.0D) {
            return 0.0D;
        }
        return most * harmShare(outcome) * (1.0D - distance / range);
    }

    /** How hard the collapse jolts a viewer's camera this far away, 0..1.5. */
    public static double shake(double distance, CollapseRules.Outcome outcome) {
        return blastForce(outcome) * (1.0D - smooth(span(distance, SHAKE_FULL_WITHIN, SHAKE_GONE_BEYOND)));
    }

    // ---------------------------------------------------------------- the rhythm

    /** Ticks between the heart's beats: slow as it begins, quickening through the winding, quicker when unsteady. */
    public static int heartbeat(double progress, double unrest) {
        double base = progress < WINDING_FROM ? BEAT_SLOW
                : lerp(span(progress, WINDING_FROM, HUSH), BEAT_WINDING_FIRST, BEAT_WINDING_LAST);
        return Math.max(BEAT_FASTEST, (int) Math.round(base * (1.0D - 0.4D * clamp01(unrest))));
    }

    /** Ticks between the lightning's reaches for each stabiliser: quickening through the winding, and when unsteady. */
    public static int arcPeriod(double progress, double unrest) {
        double base = lerp(span(progress, WINDING_FROM, HUSH), ARC_SLOW, ARC_QUICK);
        return Math.max(ARC_FASTEST, (int) Math.round(base * (1.0D - 0.3D * clamp01(unrest))));
    }

    // ---------------------------------------------------------------- arithmetic

    /** How far {@code value} is from {@code from} to {@code to}, clamped to 0..1. */
    public static double span(double value, double from, double to) {
        return to <= from ? (value >= to ? 1.0D : 0.0D) : clamp01((value - from) / (to - from));
    }

    public static double smooth(double t) {
        double c = clamp01(t);
        return c * c * (3.0D - 2.0D * c);
    }

    public static double lerp(double t, double from, double to) {
        return from + (to - from) * t;
    }

    static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
