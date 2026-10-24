package uk.co.iceconchy.aerowarptics.plotter;

/**
 * How the Rift Plotter moves: the turntable and the dish facing the entry, the armillary ball at its focus spinning up
 * as it charges, the glow in its channels.
 *
 * <p>The machine is posed, never animated, for the cradle's reason: a keyframed loop drifts off what the machine is
 * doing, and a plotter that spins as fast charging as idle tells a player nothing. Everything is a function of the
 * state, how far through it the machine is, and how charged; the two things that are integrated - the aim and
 * the rings' spin - are integrated here, on the client's own tick, so the renderer only ever reads a pose.
 *
 * <p>Kept free of Minecraft, so the curves can be walked in a test.
 */
public final class PlotterWorks {

    /** How fast the turntable and the dish turn to a new aim, in radians a tick: a machine, not a pointer. */
    public static final double TURN_RATE = Math.toRadians(3.0D);

    /** Ticks a rift takes to tear open, and to heal. */
    public static final int OPEN_TICKS = 30;
    public static final int CLOSE_TICKS = 40;

    /** How much faster than the rings the ball at their focus tumbles, about each of its two axes. */
    public static final double RING_B_RATE = 1.4D;
    public static final double RING_C_RATE = 1.9D;

    /** The shaft's pinion to the turntable's ring: the pinion turns with the shaft, the ring does not - it aims. */
    public static final double PINION_RATIO = 1.0D;

    private static final double TAU = Math.PI * 2.0D;

    /** Kept to many whole turns, so a spin that runs all day never loses a tick to a double's precision. */
    private static final double SPIN_WRAP = TAU * 1000.0D;

    private PlotterWorks() {
    }

    // ------------------------------------------------------------------ aim

    /**
     * The dish's aim as the machine is turning it, chasing the plot's aim at {@link #TURN_RATE}. Yaw the short way
     * round; pitch straight there. First told, it is simply there - a machine loaded in is already aimed.
     */
    public static final class Aim {

        private double yaw;
        private double pitch;
        private double lastYaw;
        private double lastPitch;
        private boolean placed;

        /** @param wantedYaw degrees, Minecraft's yaw; @param wantedPitch degrees, Minecraft's pitch */
        public void tick(double wantedYaw, double wantedPitch) {
            double yawTo = Math.toRadians(wantedYaw);
            double pitchTo = Math.toRadians(wantedPitch);
            if (!placed) {
                yaw = lastYaw = yawTo;
                pitch = lastPitch = pitchTo;
                placed = true;
                return;
            }
            lastYaw = yaw;
            lastPitch = pitch;
            yaw += clamp(wrap(yawTo - yaw), -TURN_RATE, TURN_RATE);
            pitch += clamp(pitchTo - pitch, -TURN_RATE, TURN_RATE);
            if (Math.abs(yaw) > SPIN_WRAP) {
                double shift = Math.floor(yaw / TAU) * TAU;
                yaw -= shift;
                lastYaw -= shift;
            }
        }

        /** Radians, Minecraft's yaw. */
        public double yaw(float partialTick) {
            return lastYaw + (yaw - lastYaw) * partialTick;
        }

        /** Radians, Minecraft's pitch. */
        public double pitch(float partialTick) {
            return lastPitch + (pitch - lastPitch) * partialTick;
        }

        /** Whether the dish is on its aim, give or take a degree. */
        public boolean settled(double wantedYaw, double wantedPitch) {
            return placed && Math.abs(wrap(Math.toRadians(wantedYaw) - yaw)) < Math.toRadians(1.0D)
                    && Math.abs(Math.toRadians(wantedPitch) - pitch) < Math.toRadians(1.0D);
        }
    }

    /**
     * The turn the turntable's bone is given for a Minecraft yaw, in radians.
     *
     * <p>Negated. GeckoLib turns a bone right-handed about +y ({@code Axis.YP}), which carries the dish's +z towards
     * +x, east; Minecraft's yaw carries a look from +z towards -x, west. Given the yaw as it stands, the dish faced the
     * entry's mirror image across the north-south line. The elevation's pitch has the game's own sign as it is.
     */
    public static double turntableYaw(double yawRadians) {
        return -yawRadians;
    }

    // ------------------------------------------------------------------ spin

    /** The armillary ball, spun up and down smoothly rather than stepped between rates. */
    public static final class Spin {

        private double angle;
        private double last;
        private double rate;

        public void tick(double wantedRate) {
            rate += (wantedRate - rate) * 0.04D;
            last = angle;
            angle += rate;
            if (angle > SPIN_WRAP) {
                angle -= SPIN_WRAP;
                last -= SPIN_WRAP;
            }
        }

        public double angle(float partialTick) {
            return last + (angle - last) * partialTick;
        }

        public double rate() {
            return rate;
        }
    }

    /** Radians a tick the rings are wanted to turn at, in a state, how charged. */
    public static double spinRate(PlotterState state, double charged) {
        return switch (state) {
            case IDLE -> 0.004D;
            case CHARGING -> 0.02D + 0.3D * clamp(charged, 0.0D, 1.0D);
            case REACHING -> 0.34D;
            case OPEN -> 0.42D;
            case CLOSING -> 0.015D;
        };
    }

    // ------------------------------------------------------------------ glow

    /**
     * How bright the channels, the crystals and the lens burn, 0..1: a breath at rest, climbing with the charge,
     * pulsing while it waits on the far end, full while it stands open, and dying as it heals.
     */
    public static double glow(PlotterState state, double charged, int stateTicks, double time) {
        return switch (state) {
            case IDLE -> 0.14D + 0.05D * Math.sin(time * 0.06D);
            case CHARGING -> 0.2D + 0.7D * clamp(charged, 0.0D, 1.0D) + 0.05D * Math.sin(time * 0.3D);
            case REACHING -> 0.85D + 0.15D * Math.sin(time * 0.5D);
            case OPEN -> 1.0D;
            case CLOSING -> 1.0D - 0.86D * smooth(stateTicks / (double) CLOSE_TICKS);
        };
    }

    /** How far through its window an open rift is, 0..1. */
    public static double window(int stateTicks, int openTicks) {
        return openTicks <= 0 ? 1.0D : clamp(stateTicks / (double) openTicks, 0.0D, 1.0D);
    }

    // ------------------------------------------------------------------ helpers

    /** An angle folded into -pi..pi. */
    public static double wrap(double radians) {
        double wrapped = radians % TAU;
        if (wrapped > Math.PI) {
            wrapped -= TAU;
        } else if (wrapped < -Math.PI) {
            wrapped += TAU;
        }
        return wrapped;
    }

    /** Eased 0..1, flat at both ends, so nothing on the machine starts or stops with a jolt. */
    public static double smooth(double t) {
        double x = clamp(t, 0.0D, 1.0D);
        return x * x * (3.0D - 2.0D * x);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
