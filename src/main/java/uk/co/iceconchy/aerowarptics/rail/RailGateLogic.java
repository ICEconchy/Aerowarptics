package uk.co.iceconchy.aerowarptics.rail;

/**
 * The decisions a gate makes about trains, with nothing of the game in them.
 *
 * <p>Pure, so the rules can be held to a table: who is let through, when a gate dials for a train, when it shuts
 * again, and who pays. The level side - {@link RailTransit}, {@link RailGates} and the gate itself - reads the
 * facts and asks here.
 *
 * <h2>The rules</h2>
 * <ul>
 *   <li>A train is let into a corridor only through a gate that is open to the lane's other end. Anything else
 *   holds it at the pane, as a red signal would.</li>
 *   <li>Nothing that has started through is ever held - not a carriage half across a pane, not one of a train
 *   whose front is already in the corridor, not one coming out of the corridor. A gate that loses its power with
 *   a train in it lets the train finish.</li>
 *   <li>A gate dials for a train only when it is idle, not held by redstone, and has not just tried and failed.
 *   It pays exactly as a player's dial does, so the train that opens it pays and one that finds it already open
 *   rides on the upkeep being paid.</li>
 *   <li>A gate a train opened shuts once nothing has used it for a while. One a player opened is theirs, and is
 *   left alone.</li>
 *   <li>A corridor never keeps a train. One that stands in a corridor with nothing driving it is sent on the way
 *   it was going, because nobody can reach it there to drive it out.</li>
 * </ul>
 */
public final class RailGateLogic {

    private RailGateLogic() {
    }

    /** What happens to a carriage at a lane's pane. */
    public enum Admission {
        /** Through it goes. */
        PASS,
        /** It is part of the way through already; Create's own rules apply and this mod says nothing. */
        ALREADY_CROSSING,
        /** The lane's far end is not laid, so there is nowhere to come out. */
        HOLD_LANE_BUILDING,
        /** The carriage is too long for the corridor and would be stuck in it. */
        HOLD_TOO_LONG,
        /** The gate is not open to the other end yet. */
        HOLD_GATE_SHUT
    }

    /**
     * Whether a carriage reaching a lane's pane goes through.
     *
     * @param crossing      whether the carriage is already partly through this pane, or is coming out of the
     *                      corridor rather than going in
     * @param admitted      whether its train has already been let into this lane
     * @param laneReady     whether the lane has both ends laid
     * @param fits          whether the carriage fits the corridor
     * @param gateOpen      whether the gate it is at is open to the lane's other end
     */
    public static Admission admit(boolean crossing, boolean admitted, boolean laneReady, boolean fits,
                                  boolean gateOpen) {
        if (crossing) {
            return Admission.ALREADY_CROSSING;
        }
        if (admitted) {
            return Admission.PASS;
        }
        if (!laneReady) {
            return Admission.HOLD_LANE_BUILDING;
        }
        if (!fits) {
            return Admission.HOLD_TOO_LONG;
        }
        return gateOpen ? Admission.PASS : Admission.HOLD_GATE_SHUT;
    }

    /** What a gate a train is waiting at should do about it. */
    public enum Call {
        /** Nothing: it is already open, or opening, to the right gate. */
        NOTHING,
        /** Dial the lane's other end for the train. */
        DIAL,
        /** Leave it: busy with another connection, held, or too soon after a refusal. */
        WAIT,
        /** Held by redstone. */
        HELD
    }

    /**
     * What a gate does about a train waiting at it.
     *
     * @param engagedWithOther   whether it is already dialling or open to the lane's other end
     * @param engagedElsewhere   whether it is dialling or open to some other gate
     * @param redstoneHold       whether a signal on its controller is holding trains back
     * @param ticksSinceAttempt  ticks since it last tried to dial for a train and was refused
     * @param retryTicks         how long a refusal stands before it tries again
     */
    public static Call call(boolean engagedWithOther, boolean engagedElsewhere, boolean redstoneHold,
                            long ticksSinceAttempt, int retryTicks) {
        if (engagedWithOther) {
            return Call.NOTHING;
        }
        if (redstoneHold) {
            return Call.HELD;
        }
        if (engagedElsewhere || ticksSinceAttempt < retryTicks) {
            return Call.WAIT;
        }
        return Call.DIAL;
    }

    /**
     * Whether a gate shuts now.
     *
     * @param openedForTrains whether it was a train, not a player, that opened it
     * @param busy            whether a train is waiting at it or running through one of its lanes
     * @param idleTicks       how long it has been neither
     * @param lingerTicks     how long it is left open after that
     */
    public static boolean shutsNow(boolean openedForTrains, boolean busy, int idleTicks, int lingerTicks) {
        return openedForTrains && !busy && idleTicks >= lingerTicks;
    }

    /**
     * What a train pays to go through.
     *
     * @param alreadyOpen whether the gate was open to the far end when it got there
     * @param dialCost    what dialling costs at this gate
     */
    public static int toll(boolean alreadyOpen, int dialCost) {
        return alreadyOpen ? 0 : dialCost;
    }

    /**
     * Whether a player may shut a gate, or turn it elsewhere, now.
     *
     * @param trainInTransit whether a train is part of the way through any lane the change would cut
     */
    public static boolean mayChange(boolean trainInTransit) {
        return !trainInTransit;
    }

    /** What to do about a train standing still in a corridor. */
    public enum Stranded {
        /** It is going somewhere, or will be: leave it. */
        NOTHING,
        /** Create is holding it for chunks. It moves when they arrive, and the corridor's hold sees that they do. */
        WAITING,
        /** Off the track. Put it back on the rails first, then send it on. */
        LIFT_AND_SEND,
        /** Nothing is driving it and nothing will. Send it on out of the corridor. */
        SEND_ON
    }

    /**
     * What to do about a train standing in a corridor.
     *
     * <p>A corridor is a tube with a mouth at each end, no way in but a gate, and nothing a player may build in
     * it. So a train that stops in one is lost for good: nobody can reach it to drive it out, and Create will not
     * move it of its own accord - {@code Train.tickPassiveSlowdown} brings a train with no driver and no schedule
     * to a halt and then leaves it exactly where it stopped. That is a rider knocked off the controls half way
     * through, and it cost somebody their train in game (2026-10-25). A corridor keeps nobody, so it keeps no
     * train either.
     *
     * <p>Standing still is the whole test, and it is enough: a train being driven is moving, and one whose driver
     * has let go stops within a second of letting go. A train parked in a corridor on purpose is a train in a
     * place nothing should be parked.
     *
     * @param speed            how fast it is going as Create has it, signed along its own direction of travel
     * @param navigating       whether a schedule is steering it, which will carry it out without help
     * @param waitingForChunks whether Create is holding it for chunks that are not loaded yet
     * @param derailed         whether it has come off the track
     * @param stalledTicks     how long it has stood still in the corridor
     * @param graceTicks       how long it may stand still before it is sent on
     */
    public static Stranded stranded(double speed, boolean navigating, boolean waitingForChunks, boolean derailed,
                                    int stalledTicks, int graceTicks) {
        if (derailed) {
            // Not left alone any more. A derailed train is normally a player's to pick up and set down again, and
            // in a corridor there is no player who can reach it - so the corridor lifts it back on itself.
            return stalledTicks >= graceTicks ? Stranded.LIFT_AND_SEND : Stranded.NOTHING;
        }
        if (speed != 0.0D) {
            return Stranded.NOTHING;
        }
        if (waitingForChunks) {
            return Stranded.WAITING;
        }
        if (navigating) {
            return Stranded.NOTHING;
        }
        return stalledTicks >= graceTicks ? Stranded.SEND_ON : Stranded.NOTHING;
    }

    /**
     * Which way to send a stranded train, as the sign to put on its speed.
     *
     * <p>The way it was already going, so a train knocked out of somebody's hands finishes the crossing it had
     * started rather than reversing into the gate it has just come through. Failing that, the way its controls
     * were last set to, which is what its driver wanted before they were knocked off. Failing both - a train that
     * entered and stopped in the same tick - forwards, because either mouth is a way out.
     *
     * @param lastSpeed   the last speed it was seen moving at in the corridor, or 0 if it was never seen moving
     * @param targetSpeed what its controls were last set to
     */
    public static double onward(double lastSpeed, double targetSpeed) {
        if (lastSpeed != 0.0D) {
            return Math.signum(lastSpeed);
        }
        return targetSpeed != 0.0D ? Math.signum(targetSpeed) : 1.0D;
    }
}
