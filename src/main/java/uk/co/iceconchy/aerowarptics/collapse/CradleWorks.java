package uk.co.iceconchy.aerowarptics.collapse;

import static uk.co.iceconchy.aerowarptics.collapse.CollapseChoreography.lerp;
import static uk.co.iceconchy.aerowarptics.collapse.CollapseChoreography.smooth;
import static uk.co.iceconchy.aerowarptics.collapse.CollapseChoreography.span;

/**
 * The Collapse Cradle as a machine: where its rings, its lock ring, its pistons and its sun gear are at any moment
 * of a ritual, as arithmetic. {@link CollapseChoreography} says what the collapse does; this says what the machine
 * round it does about it, on the same clock.
 *
 * <ol>
 *   <li><b>At rest</b> the Rift Core hangs over the Scar Heart; the rings lie flat and drift round with the
 *       shaft; the pistons tick over.</li>
 *   <li><b>Spin-up.</b> The lock ring on the roof turns. The core rises to just under the rings, and as the rift
 *       tears it is drawn up into the middle of them.</li>
 *   <li><b>From the fold on</b> the rings tumble inside one another, faster as it winds; the pistons pump
 *       harder.</li>
 *   <li><b>The hush.</b> The rings brake and lock flat into one plane; the pistons fall still.</li>
 *   <li><b>After.</b> A settled core comes back down out of the point to hang over the heart again, and the lock
 *       turns back.</li>
 * </ol>
 *
 * <p>The machine is the artist's model (2026-10-21), which has no arms, iris or gears; the earlier, generated
 * cradle had all three, and their arithmetic went with it. Model pixels throughout, as the model is built:
 * sixteen to a block, from the middle of the machine's floor. {@code CradleRigTest} reads the rig numbers back
 * out of the shipped geometry. Angles are as GeckoLib's {@code setRotX} takes them.
 *
 * <p>Free of Minecraft, for {@code CradleWorksTest}: a ring that stops short of flat or a piston out of its
 * cylinder are neither of them things that throw.
 */
public final class CradleWorks {

    // ------------------------------------------------------------------ the rig

    /** The middle of the rings, and of the hole: where the artist's rings turn. */
    public static final double CENTRE = 31.0;
    /** The plinth's top. */
    public static final double DECK = 13.0;
    /** The Rift Core's half-width as the renderer draws it, in pixels: it hangs inside the inner ring's hole. */
    public static final double CORE_HALF = 3.6;

    /**
     * Each ring's inner and outer radius, and its height, in pixels: the Relic Armillary, as the artist built it -
     * eight straight segments a ring, the nearest any inner face comes to the axis and the furthest any outer face
     * goes. {@code CradleRigTest} measures them off the shipped model, and holds each ring, turned any way, clear
     * of the one outside it.
     */
    public static final double[] OUTER_RING = {14.58D, 19.35D, 4.0D};
    public static final double[] MIDDLE_RING = {8.42D, 12.08D, 4.0D};
    public static final double[] INNER_RING = {5.04D, 7.26D, 4.0D};

    /** How far the lock ring on the roof turns as a ritual starts. */
    public static final double LOCK_TURN = Math.toRadians(30.0D);
    /** How far a piston's rod sinks at the bottom of its stroke. */
    public static final double PISTON_STROKE = 3.0D;

    // ------------------------------------------------------------------ the beats

    /** The lock ring has turned by here. */
    public static final double UNLOCKED = 0.03D;
    /** The rift has the core by here: it is drawn up out of its place over the heart just as the rift tears. */
    public static final double RELEASED = 0.105D;
    /** The rings start to tumble once the core is in among them, and are at the winding's pace by the fold. */
    public static final double TUMBLE_FROM = 0.20D;
    public static final double TUMBLE_FULL = CollapseChoreography.FOLD;

    /** Ticks after a ritual for a settled core to come back down out of the point: the spectacle's settle. */
    public static final int RETURN_TICKS = 40;

    // ------------------------------------------------------------------ the gearing

    /** Turns of each part per turn of the shaft. */
    public static final double SUN_RATIO = 1.0D;
    public static final double PISTON_RATIO = 0.5D;
    /** The outer ring drifts at rest and quickens through the winding - the other way to the sun gear. */
    public static final double YAW_IDLE = -0.02D;
    public static final double YAW_PEAK = -0.12D;
    /** The middle ring's pitch and the inner ring's roll, per turn of the shaft, at the winding's start. */
    public static final double PITCH_RATIO = 0.075D;
    public static final double ROLL_RATIO = -0.11D;
    /** How much faster than that they are tumbling by the hush. */
    public static final double TUMBLE_WIND = 1.5D;
    /** The hardest the rings brake, radians per tick per tick. */
    public static final double BRAKE = 0.04D;
    /** How fast a ring that was barely turning swings round to lie flat, radians per tick. */
    public static final double SWING = 0.1D;
    /**
     * The fastest the inner rings are ever driven, radians per tick - a little over three turns a second. A
     * config that turns shafts far faster than Create's own would otherwise spin them into a blur that no brake
     * could bring flat before the next ritual.
     */
    public static final double FASTEST_TUMBLE = 0.4D;

    private CradleWorks() {
    }

    // ------------------------------------------------------------------ the core

    /** How high a settled core is as it comes down out of the point onto the cradle, in pixels. */
    public static double settledCore(double after) {
        return lerp(smooth(after), CENTRE, px(CollapseChoreography.HOVER));
    }

    // ------------------------------------------------------------------ the roof


    /** How far the lock ring has turned: unlocked as the ritual starts, locked again once it is over. */
    public static double lock(double progress, boolean running, double after) {
        if (running) {
            return LOCK_TURN * smooth(span(progress, 0.0D, UNLOCKED));
        }
        return LOCK_TURN * (1.0D - smooth(span(after, 0.0D, 0.25D)));
    }

    // ------------------------------------------------------------------ the plinth

    /**
     * How hard the pistons work, as a share of their full stroke: ticking over at rest, pumping harder into
     * the winding, and falling still in the hush.
     */
    public static double work(double progress, boolean running) {
        if (!running) {
            return 0.35D;
        }
        if (progress < CollapseChoreography.HUSH) {
            return lerp(smooth(span(progress, 0.0D, CollapseChoreography.WINDING_FROM)), 0.35D, 1.0D);
        }
        return 1.0D - smooth(span(progress, CollapseChoreography.HUSH, CollapseChoreography.HUSH + 0.01D));
    }

    /**
     * How far a piston's rod has sunk, in pixels (0 at the top of its stroke): each on its own quarter of the
     * crank, so the four take turns.
     *
     * @param shaft how far the shaft has turned, radians
     */
    public static double piston(int index, double shaft, double work) {
        double phase = shaft * PISTON_RATIO + index * Math.PI / 2.0D;
        return -PISTON_STROKE * work * (0.5D + 0.5D * Math.sin(phase));
    }

    /**
     * How bright the machine's carved channels burn, 0..1: faint at rest, waking with the spin-up, full through
     * the winding, dimming in the hush and flaring at the collapse, then fading back.
     */
    public static double glow(double progress, boolean running, double after, double beat) {
        if (!running) {
            return lerp(smooth(Math.min(1.0D, after)), 1.0D, 0.12D);
        }
        double base = progress < CollapseChoreography.HUSH
                ? lerp(smooth(span(progress, 0.0D, CollapseChoreography.WINDING_FROM)), 0.25D, 0.85D)
                : lerp(CollapseChoreography.hush(progress), 0.85D, 0.3D);
        return CollapseChoreography.clamp01(base + 0.15D * beat);
    }

    // ------------------------------------------------------------------ a fold
    //
    // A fold is the cradle's lesser work and has a smaller scene, on its own clock, 0..1 through the run: the stack
    // rises to just under the rings and a small rift draws it up into their middle; the rift folds it with a flash;
    // what comes out is lowered back down to hang over the heart. The rings whirl flat round it and never tumble.

    /** The stack is up under the rings; the rift has drawn it into their middle; it is folded. */
    public static final double FOLD_LIFTED = 0.20D;
    public static final double FOLD_RISEN = 0.35D;
    public static final double FOLD_FLASH = 0.50D;
    /** What was made starts down; it is back under the rings. */
    public static final double FOLD_DESCENT = 0.60D;
    public static final double FOLD_DOWN = 0.72D;
    /** The rift a fold opens, at its widest, in blocks: small, a lens rather than a hole. */
    public static final double FOLD_RIFT = 0.28D;

    /** How high what is being folded is, in pixels, through a fold. */
    public static double foldHeight(double f) {
        double hover = px(CollapseChoreography.HOVER);
        double handOff = px(CollapseChoreography.HAND_OFF);
        if (f < FOLD_LIFTED) {
            return lerp(smooth(span(f, 0.0D, FOLD_LIFTED)), hover, handOff);
        }
        if (f < FOLD_RISEN) {
            return lerp(smooth(span(f, FOLD_LIFTED, FOLD_RISEN)), handOff, CENTRE);
        }
        if (f < FOLD_DESCENT) {
            return CENTRE;
        }
        if (f < FOLD_DOWN) {
            return lerp(smooth(span(f, FOLD_DESCENT, FOLD_DOWN)), CENTRE, handOff);
        }
        return lerp(smooth(span(f, FOLD_DOWN, 1.0D)), handOff, hover);
    }


    /** How large what is being folded is drawn: shrinking into the rift, then what it made growing out of it. */
    public static double foldScale(double f) {
        if (f < FOLD_FLASH) {
            return 1.0D - smooth(span(f, FOLD_FLASH - 0.12D, FOLD_FLASH));
        }
        return smooth(span(f, FOLD_FLASH, FOLD_DESCENT));
    }

    /** Whether it has been folded yet - from the flash on, what is drawn is what it made. */
    public static boolean folded(double f) {
        return f >= FOLD_FLASH;
    }

    /** The fold's rift, in blocks: opening as the stack rises into it, and closed again once it has let go. */
    public static double foldRift(double f) {
        if (f < 0.28D || f >= 0.64D) {
            return 0.0D;
        }
        if (f < 0.36D) {
            return FOLD_RIFT * smooth(span(f, 0.28D, 0.36D));
        }
        return FOLD_RIFT * (1.0D - smooth(span(f, 0.56D, 0.64D)));
    }

    /** How bright the carvings burn through a fold: warming, with a flash at the fold itself. */
    public static double foldGlow(double f) {
        double warm = 0.2D + 0.4D * Math.sin(Math.PI * CollapseChoreography.clamp01(f));
        double flash = Math.max(0.0D, 1.0D - Math.abs(f - FOLD_FLASH) / 0.04D);
        return CollapseChoreography.clamp01(warm + 0.5D * flash);
    }

    /** How much faster than its drift the outer ring whirls through a fold: most as the fold is made. */
    public static double foldWhirl(double f) {
        double s = Math.sin(Math.PI * CollapseChoreography.clamp01(f));
        return 1.0D + 5.0D * s * s;
    }

    // ------------------------------------------------------------------ failing
    //
    // An unsteady collapse shows itself on the machine, not only in the rift: from where its instability is
    // heading (CollapseRitual.heading), so it looks as bad as it is going to be, from the moment it is. The
    // machine shudders, the outer ring rocks on its rollers, the sun gear slips, the pistons misfire, and the
    // carvings flicker and run hot. All of it is small against the clearances the machine was built to - the ring
    // rocks a couple of degrees.

    /** The outer ring's rocking at its worst, radians: a little under two degrees. */
    public static final double MOST_ROCK = Math.toRadians(1.8D);
    /** The whole machine's shudder at its worst, in pixels. */
    public static final double MOST_SHUDDER = 0.45D;

    /** How badly the machine is failing, 0..1, from where the instability is heading: nothing below a third. */
    public static double strain(double heading) {
        return smooth(span(heading, 0.3D, 0.9D));
    }

    /** The whole machine's shudder this tick, {x, y, z} in pixels: a fresh jolt each tick, larger the worse. */
    public static double[] shudder(double strain, long tick) {
        double amount = MOST_SHUDDER * strain * strain;
        return new double[]{amount * jitter(tick, 1), amount * 0.4D * jitter(tick, 2), amount * jitter(tick, 3)};
    }

    /** The outer ring rocking on its rollers, {about x, about z} in radians. */
    public static double[] rock(double strain, double time) {
        double amount = MOST_ROCK * strain;
        return new double[]{amount * Math.sin(time * 0.9D + Math.sin(time * 2.3D)),
                amount * Math.cos(time * 1.1D + Math.sin(time * 1.7D))};
    }

    /** How far the sun gear has slipped, radians: jerks back and forth that come in steps, not a smooth turn. */
    public static double slip(double strain, double time) {
        return 0.35D * strain * jitter((long) Math.floor(time / 6.0D), 4);
    }

    /** An extra share of a piston's stroke from a misfire, 0..1: nothing steady, a kick now and then when not. */
    public static double misfire(double strain, long tick, int piston) {
        double kick = jitter(tick / 3L, 10 + piston);
        return strain * Math.max(0.0D, kick);
    }



    /** How much of the carvings' light gets through a flicker, 0..1. */
    public static double flicker(double strain, long tick) {
        return 1.0D - 0.6D * strain * Math.max(0.0D, jitter(tick, 30));
    }

    /** How the carvings' light is tinted as it runs hot, {red, green, blue} multipliers: violet to angry red. */
    public static double[] hot(double strain) {
        return new double[]{1.0D, 1.0D - 0.65D * strain, 1.0D - 0.45D * strain};
    }

    /** A repeatable value in -1..1 for a moment and a stream, so a client redrawing a tick draws the same jolt. */
    static double jitter(long tick, int stream) {
        long h = tick * 0x9E3779B97F4A7C15L + stream * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return ((h >>> 11) * 0x1.0p-53) * 2.0D - 1.0D;
    }

    // ------------------------------------------------------------------ the rings

    /** The outer ring's turns per turn of the shaft: a drift at rest, quickening through the winding. */
    public static double yawRatio(double progress, boolean running) {
        if (!running) {
            return YAW_IDLE;
        }
        return lerp(smooth(span(progress, TUMBLE_FROM, CollapseChoreography.HUSH)), YAW_IDLE, YAW_PEAK);
    }

    /**
     * How hard the inner rings are driven to tumble, as a multiple of {@link #PITCH_RATIO} and {@link #ROLL_RATIO}:
     * nothing until the core is among them, spinning up to the fold, quickening through the winding - and
     * nothing at all in the hush, which is when they brake and lock flat.
     */
    public static double tumble(double progress, boolean running) {
        if (!running || progress >= CollapseChoreography.HUSH) {
            return 0.0D;
        }
        return smooth(span(progress, TUMBLE_FROM, TUMBLE_FULL))
                * (1.0D + TUMBLE_WIND * span(progress, TUMBLE_FULL, CollapseChoreography.HUSH));
    }

    /**
     * The three rings' angles, run on a client from tick to tick: the outer's yaw and the inner two's pitch and
     * roll. Integrated rather than worked out from the clock, because their speed changes as the ritual winds
     * and an angle read off a changing speed jumps.
     *
     * <p>Driven, each turns at its share of the shaft. Undriven - in the hush, after a ritual, at rest - the
     * inner two brake to the next flat position ahead of them, never faster than {@link #BRAKE} allows and
     * never past it, and stop there: flat, all three lie in one plane.
     */
    public static final class Rings {

        private final Ring yaw = new Ring();
        private final Ring pitch = new Ring();
        private final Ring roll = new Ring();

        /**
         * One tick.
         *
         * @param rpm the shaft's speed, signed as Create signs it
         */
        public void tick(double rpm, double progress, boolean running) {
            tick(rpm, progress, running, -1.0D);
        }

        /**
         * One tick, with a fold under way if {@code fold} is 0..1: the outer ring whirls, the inner two lie flat.
         *
         * @param rpm the shaft's speed, signed as Create signs it
         */
        public void tick(double rpm, double progress, boolean running, double fold) {
            double shaft = Math.PI * 2.0D * rpm / 1200.0D;
            double wanted = fold >= 0.0D ? shaft * YAW_IDLE * foldWhirl(fold)
                    : running && progress >= CollapseChoreography.HUSH ? 0.0D : shaft * yawRatio(progress, running);
            yaw.ease(wanted);
            double drive = tumble(progress, running);
            if (drive > 0.0D) {
                pitch.drive(shaft * PITCH_RATIO * drive);
                roll.drive(shaft * ROLL_RATIO * drive);
            } else {
                pitch.settle();
                roll.settle();
            }
        }

        public double yaw(float partialTick) {
            return yaw.at(partialTick);
        }

        public double pitch(float partialTick) {
            return pitch.at(partialTick);
        }

        public double roll(float partialTick) {
            return roll.at(partialTick);
        }

        /** Whether the inner two are lying flat and still. */
        public boolean parked() {
            return pitch.parked() && roll.parked();
        }
    }

    /** One ring's turn: driven at a rate, eased towards one, or braking to lie flat. */
    static final class Ring {

        double angle;
        double last;
        double rate;
        /** Where a braking ring will stop, a multiple of a half turn; NaN while it is driven. */
        double target = Double.NaN;

        /** Turns at the rate it is driven at, radians per tick. */
        void drive(double driven) {
            last = angle;
            target = Double.NaN;
            rate = Math.max(-FASTEST_TUMBLE, Math.min(FASTEST_TUMBLE, driven));
            angle += rate;
            keepSmall();
        }

        /** Turns towards the rate it is asked for, never changing speed faster than {@link #BRAKE}. */
        void ease(double wanted) {
            last = angle;
            rate += Math.max(-BRAKE, Math.min(BRAKE, wanted - rate));
            angle += rate;
            keepSmall();
        }

        /** Brakes to the next flat position ahead, and stops there. */
        void settle() {
            last = angle;
            if (Double.isNaN(target)) {
                target = flatAhead(angle, rate);
            }
            double left = Math.abs(target - angle);
            if (left < 1.0e-9D) {
                angle = target;
                rate = 0.0D;
                return;
            }
            // Keep going as fast as it was until it has to brake to stop on the mark, then follow the braking
            // curve down to it. A ring turning slower than it swings flat at picks up to that, gently.
            double going = Math.abs(rate) < SWING ? Math.abs(rate) + BRAKE * 0.5D : Math.abs(rate);
            double speed = Math.min(going, Math.sqrt(2.0D * BRAKE * left));
            if (speed >= left) {
                angle = target;
                rate = 0.0D;
                return;
            }
            rate = Math.signum(target - angle) * speed;
            angle += rate;
        }

        double at(float partialTick) {
            return lerp(partialTick, last, angle);
        }

        boolean parked() {
            return rate == 0.0D && Math.abs(Math.IEEEremainder(angle, Math.PI)) < 1.0e-9D;
        }

        /**
         * Keeps a spinning ring within a few dozen turns of zero, so a float between ticks never loses the
         * fraction of a turn that is the picture. Whole turns only, taken off everything it remembers at once.
         */
        private void keepSmall() {
            double turn = Math.PI * 2.0D;
            if (Math.abs(angle) > turn * 32.0D) {
                double shift = Math.floor(angle / turn) * turn;
                angle -= shift;
                last -= shift;
                if (!Double.isNaN(target)) {
                    target -= shift;
                }
            }
        }

        /**
         * The next flat position ahead of a ring turning at a rate, far enough on that braking at
         * {@link #BRAKE} reaches it without having to stop short; the nearest one either way if it is still.
         */
        static double flatAhead(double angle, double rate) {
            if (Math.abs(rate) < 1.0e-9D) {
                return Math.round(angle / Math.PI) * Math.PI;
            }
            double stopping = rate * rate / (2.0D * BRAKE);
            return rate > 0.0D
                    ? Math.ceil((angle + stopping) / Math.PI) * Math.PI
                    : Math.floor((angle - stopping) / Math.PI) * Math.PI;
        }
    }

    // ------------------------------------------------------------------ arithmetic

    /** Blocks to model pixels. */
    public static double px(double blocks) {
        return blocks * 16.0D;
    }

    private static double clamp(double value) {
        return Math.max(-1.0D, Math.min(1.0D, value));
    }
}
