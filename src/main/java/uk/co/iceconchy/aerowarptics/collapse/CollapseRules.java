package uk.co.iceconchy.aerowarptics.collapse;

/**
 * The Collapse's rules that are not instability: when it refuses, how long it runs, what shape it takes, and
 * how it ends. Free of Minecraft, so the whole ritual can be walked through in a test.
 */
public final class CollapseRules {

    /** How it ended. The names are what the {@code collapse_finished} trigger and the trace say. */
    public enum Outcome implements net.minecraft.util.StringRepresentable {
        /** The Singularity Core, on the cradle. */
        SETTLED,
        /** The core, thrown clear of the cradle - it declines to be precise, the Singularity's vice at birth. */
        FLUNG,
        /** Nothing made. The Rift Core and every offering are thrown back out whole; the heart and essence are spent. */
        SPAT;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        @Override
        public String getSerializedName() {
            return id();
        }
    }

    /** The ritual's four movements, by how far through it is. */
    public enum Phase {
        /** The core lifts, the stabilisers light. */
        SPIN_UP,
        /** A rift opens over the cradle; the offerings are drawn in and the essence begins to burn. */
        OPENING,
        /** Instability builds from whatever is pushing and holding. */
        WINDING,
        /** The rift falls in on itself. */
        COLLAPSE
    }

    public static final double SPIN_UP_END = 0.10D;
    public static final double OPENING_END = 0.30D;
    public static final double WINDING_END = 0.95D;

    /** A storm is space already coming apart: a collapse in one runs this much of its usual length. */
    public static final double STORM_LENGTH = 0.6D;

    /** However short a recipe asks for, a ritual long enough to watch. */
    public static final int SHORTEST_RITUAL = 100;

    private CollapseRules() {
    }

    /** What a cradle's pre-flight sees. Only what the refusals need, so a test can ask for any of them. */
    public record Preflight(boolean running, boolean aboardShip, boolean hasCore, boolean hasHeart,
                            boolean recipeMatched, int essenceHeld, int essenceNeeded, double speed,
                            double minimumSpeed) {
    }

    /**
     * The first reason not to begin, or {@link CollapseFailure#NONE}. Asked before anything is spent, and
     * nothing is spent unless it answers {@code NONE}.
     */
    public static CollapseFailure preflight(Preflight check) {
        if (check.running()) {
            return CollapseFailure.BUSY;
        }
        if (check.aboardShip()) {
            return CollapseFailure.ABOARD_SHIP;
        }
        if (!check.hasCore()) {
            return CollapseFailure.NO_CORE;
        }
        if (!check.hasHeart()) {
            return CollapseFailure.NO_HEART;
        }
        if (!check.recipeMatched()) {
            return CollapseFailure.NO_RECIPE;
        }
        if (check.essenceHeld() < check.essenceNeeded()) {
            return CollapseFailure.NOT_ENOUGH_ESSENCE;
        }
        if (Math.abs(check.speed()) < check.minimumSpeed()) {
            return CollapseFailure.TOO_SLOW;
        }
        return CollapseFailure.NONE;
    }

    /** How long a ritual runs, in ticks - shortened by a storm, cutting both ways as it does for warps. */
    public static int ritualTicks(int recipeTicks, boolean storm) {
        double ticks = Math.max(SHORTEST_RITUAL, recipeTicks) * (storm ? STORM_LENGTH : 1.0D);
        return Math.max(SHORTEST_RITUAL / 2, (int) Math.round(ticks));
    }

    public static Phase phase(double progress) {
        if (progress < SPIN_UP_END) {
            return Phase.SPIN_UP;
        }
        if (progress < OPENING_END) {
            return Phase.OPENING;
        }
        if (progress < WINDING_END) {
            return Phase.WINDING;
        }
        return Phase.COLLAPSE;
    }

    /** How much of the ritual's essence should have been burnt by now, 0..1: none at spin-up, all by the end of winding. */
    public static double essenceDue(double progress) {
        return CollapseInstability.clamp01((progress - SPIN_UP_END) / (WINDING_END - SPIN_UP_END));
    }

    /** How a ritual ends, from its final instability and the bands in config. */
    public static Outcome outcome(double instability, double settleBelow, double spitAbove) {
        if (instability < settleBelow) {
            return Outcome.SETTLED;
        }
        return instability < Math.max(settleBelow, spitAbove) ? Outcome.FLUNG : Outcome.SPAT;
    }
}
