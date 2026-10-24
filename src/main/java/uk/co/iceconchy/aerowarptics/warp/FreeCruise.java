package uk.co.iceconchy.aerowarptics.warp;

/**
 * The arithmetic of a free cruise: how hard the drive pushes, how fast it may go, and what it costs.
 *
 * <p>A cruise is supercruise, not a jump. Nothing is folded and nothing is teleported: the drive takes
 * over the ship's horizontal thrust and flies it through the real world, along wherever the bow points,
 * at a speed the pilot sets with a redstone signal and the drive builds up to rather than snaps to. The
 * ship's own lift and turning are left alone, which is what makes it steerable - so the pilot can see
 * where they are going because they are going there.
 *
 * <p>What stops it being an elytra with a bigger number is what this class works out. The speed is held
 * to what the ship could stop from before the nearest thing ahead of it, or the nearest ground the
 * server has not loaded yet, so a cruise slows for a mountain and for new country rather than flying
 * into either. And the charge drains by the same formula a waypoint jump is priced with, per block
 * actually flown, so crossing a thousand blocks costs what jumping them would.
 *
 * <p>Free of Minecraft on purpose, so all of that can be tested without a server.
 */
public final class FreeCruise {

    private FreeCruise() {
    }

    /** Game ticks in one second, which is what the configured cruise speed is quoted per. */
    public static final double TICKS_PER_SECOND = 20.0D;

    /** The strongest redstone signal, which is full throttle. */
    public static final int FULL_SIGNAL = 15;

    /**
     * Ticks between looks ahead.
     *
     * <p>Every tick would be exact and expensive; this is often enough that at a Singularity's cruise
     * speed the ship covers less than a hull's standoff between two looks, which {@link #safeSpeed}
     * accounts for anyway.
     */
    public static final int SENSOR_INTERVAL = 4;

    /** Ticks between laying chunk tickets ahead of the ship. Well inside their lifespan. */
    public static final int TICKET_INTERVAL = 20;

    /** Ticks between telling the crew how fast they are going, for the streaks. */
    public static final int CREW_INTERVAL = 5;

    /**
     * Blocks the ship keeps between its bow and anything it is slowing for.
     *
     * <p>A cruise that braked to a stop exactly at a cliff face would stop with its bow in the rock after
     * a tick of lag or a gust of physics. This is the room for that.
     */
    public static final double STANDOFF = 6.0D;

    /** Below this, in blocks per tick, a ship letting go of a cruise has stopped. */
    public static final double STOPPED = 0.02D;

    /**
     * How far ahead to start the world loading, in ticks of travel at the speed being asked for.
     *
     * <p>Chunks take a few seconds to come off disk and longer to generate. Laid only as far ahead as the
     * ship can stop in, a cruise into new country would spend its whole life braking for ground that had
     * been asked for a moment too late.
     */
    public static final int LOAD_AHEAD_TICKS = 100;

    /** A redstone signal as a throttle, 0 to 1. */
    public static double throttle(int signal) {
        return Math.max(0, Math.min(FULL_SIGNAL, signal)) / (double) FULL_SIGNAL;
    }

    /** A speed quoted in blocks per second, in the blocks per tick everything else here thinks in. */
    public static double perTick(double blocksPerSecond) {
        return Math.max(0.0D, blocksPerSecond) / TICKS_PER_SECOND;
    }

    /**
     * How much speed changes in one tick, for a change from nothing to {@code top} taking {@code ticks}.
     *
     * <p>Used for both getting going and stopping, which are configured separately: supercruise builds
     * up slowly and a pilot should feel it, but a ship braking for a hillside has to do it briskly.
     */
    public static double rate(double top, int ticks) {
        return Math.max(0.0D, top) / Math.max(1, ticks);
    }

    /**
     * One tick of the speed chasing where the throttle and the sensor say it should be.
     *
     * <p>Linear, not eased: a pilot learns a ramp they can count, and a ship that has to stop before a
     * wall is only safe if it slows at exactly the rate {@link #safeSpeed} assumed it would.
     */
    public static double nextSpeed(double current, double target, double accelerate, double brake) {
        double now = Math.max(0.0D, current);
        double want = Math.max(0.0D, target);
        return want > now ? Math.min(want, now + accelerate) : Math.max(want, now - brake);
    }

    /** How far a ship doing {@code speed} travels before braking brings it to a stop. */
    public static double stoppingDistance(double speed, double brake) {
        if (!(brake > 0.0D)) {
            return speed > 0.0D ? Double.POSITIVE_INFINITY : 0.0D;
        }
        double v = Math.max(0.0D, speed);
        return v * v / (2.0D * brake);
    }

    /**
     * The fastest a ship can be going and still stop in {@code clear} blocks, having not noticed for
     * {@code reactionTicks}.
     *
     * <p>The reaction is the gap between two looks ahead: whatever the ship is doing when the sensor
     * reads, it keeps doing until the next read. Solving {@code v * t + v^2 / 2a = d} for {@code v}.
     */
    public static double safeSpeed(double clear, double brake, int reactionTicks) {
        if (!(clear > 0.0D) || !(brake > 0.0D)) {
            return 0.0D;
        }
        double t = Math.max(0, reactionTicks);
        return brake * (-t + Math.sqrt(t * t + 2.0D * clear / brake));
    }

    /**
     * How far ahead the sensor has to read for a ship that may go {@code speed}: enough to stop in, plus
     * what is covered before the next read, plus the standoff.
     */
    public static double sensorReach(double speed, double brake, int reactionTicks) {
        double v = Math.max(0.0D, speed);
        return stoppingDistance(v, brake) + v * Math.max(0, reactionTicks) + STANDOFF;
    }

    /**
     * The furthest a cruise can fly before it has to let go: whichever comes first of the tier's range
     * and the distance at which the flight would cost the whole of the charge it set off with.
     *
     * <p>Worked from the raw cost rather than {@link WarpCost.Formula#cost}, which clamps at a full
     * charge and would call a flight costing twice the drive's capacity affordable to a full drive.
     * Solving {@code (base + d * perBlock + massTerm) / efficiency = charge} for {@code d} gives the
     * exact point {@link WarpRules#checkDestination} would start refusing a waypoint jump at.
     *
     * @return the limit in blocks, or a negative number when even setting off costs more than the charge
     */
    public static double limit(WarpCost.Formula formula, double mass, double charge) {
        double efficiency = Math.max(0.01D, formula.efficiency());
        double fixed = formula.baseCost()
                + (Math.max(0.0D, mass) / Math.max(1.0D, formula.massReference())) * formula.sizeMultiplier();
        // The same hair of tolerance the destination check allows.
        double budget = Math.min(1.0D, Math.max(0.0D, charge) + 1.0e-4D) * efficiency - fixed;
        if (budget < 0.0D) {
            return -1.0D;
        }
        if (formula.distanceMultiplier() <= 0.0D) {
            return formula.maximumDistance();
        }
        return Math.min(formula.maximumDistance(), budget / formula.distanceMultiplier());
    }

    /**
     * Whether a drive holding this charge may engage a cruise at all.
     *
     * <p>Only whether it can pay to set off. There is no minimum distance, unlike a waypoint jump: that
     * floor exists so nobody spends a rift on a hop they could have walked, and a cruise is not a rift -
     * it is flying, and a short flight is only ever a short flight.
     */
    public static WarpFailure canEngage(WarpCost.Formula formula, double mass, double charge) {
        if (limit(formula, mass, charge) > 0.0D) {
            return WarpFailure.NONE;
        }
        // Charging will not help a hull too heavy for even a full drive, so do not say it would.
        return formula.exceedsFullCharge(0.0D, mass)
                ? WarpFailure.DESTINATION_TOO_FAR : WarpFailure.INSUFFICIENT_CHARGE;
    }

    /**
     * Whether the cruise has to start letting go now to stop by the limit.
     *
     * <p>Counting the braking, not just the distance flown, so a ship forced out by its charge does not
     * then spend a hundred blocks of charge it does not have coming to a halt.
     */
    public static boolean mustDropOut(double travelled, double speed, double brake, double limit) {
        return travelled + stoppingDistance(speed, brake) >= limit;
    }

    /** The charge left after flying {@code travelled} blocks on a cruise that set off holding {@code start}. */
    public static float chargeAfter(WarpCost.Formula formula, double mass, double start, double travelled) {
        return (float) Math.max(0.0D, start - formula.cost(travelled, mass));
    }

    /**
     * How much of a tick's movement counts towards the distance flown.
     *
     * <p>Measured from where the hull actually got to, so a ship pinned against something is not charged
     * for going nowhere. Capped at a few times the commanded speed, so a hull carried through a Rift Gate
     * mid-cruise is not charged for the length of the gate's jump.
     *
     * @param moved     horizontal blocks the hull's centre moved since the last tick
     * @param commanded the speed the drive asked for last tick, in blocks per tick
     */
    public static double countedTravel(double moved, double commanded) {
        if (!Double.isFinite(moved) || moved <= 0.0D) {
            return 0.0D;
        }
        return Math.min(moved, Math.max(0.5D, Math.max(0.0D, commanded) * 3.0D));
    }
}
