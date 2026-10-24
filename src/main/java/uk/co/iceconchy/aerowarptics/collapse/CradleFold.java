package uk.co.iceconchy.aerowarptics.collapse;

/**
 * A fold at the Collapse Cradle, as rules: what refuses one, how many crafts one run makes, and how long it
 * takes.
 *
 * <p>Folding was the Fold Crucible's, aboard a ship, on a jump long enough to count. It is the cradle's now: what
 * the arms hold is folded - with what the cup holds, where the recipe asks for a second thing - for Rift Essence
 * from the tank, a whole stack at a time. It is the cradle's lesser work, a few seconds long, with none of a
 * collapse's instability: the machine hands the stack up to a small rift, the rift folds it, and the arms take
 * back what comes down.
 *
 * <p>As with a collapse, nothing is spent until every refusal has been asked, and the essence for the whole run is
 * paid as it begins. Free of Minecraft, for {@code CradleFoldTest}.
 */
public final class CradleFold {

    /** The most crafts one run folds: a stack of sixteen. */
    public static final int MOST = 16;

    /** However short a recipe asks for, a fold long enough to watch the machine do it. */
    public static final int SHORTEST = 100;

    private CradleFold() {
    }

    /**
     * What a fold's pre-flight sees.
     *
     * @param busy          a collapse or a fold is already under way
     * @param aboardShip    the cradle is on a ship, which it never should be
     * @param holding       the arms hold anything at all
     * @param crafts        how many crafts what the arms and cup hold makes, by the recipe that matched, or 0
     * @param room          how many crafts' results the output has room for
     * @param essenceHeld   millibuckets in the tank
     * @param essencePer    millibuckets one craft burns
     * @param speed         the shaft's speed, signed as Create signs it
     * @param minimumSpeed  the slowest a fold will turn at, from config
     */
    public record Preflight(boolean busy, boolean aboardShip, boolean holding, int crafts, int room,
                            int essenceHeld, int essencePer, double speed, double minimumSpeed) {
    }

    /** The first reason not to fold, or {@link CollapseFailure#NONE}. In the order a player fixes things in. */
    public static CollapseFailure preflight(Preflight check) {
        if (check.busy()) {
            return CollapseFailure.BUSY;
        }
        if (check.aboardShip()) {
            return CollapseFailure.ABOARD_SHIP;
        }
        if (!check.holding()) {
            return CollapseFailure.NO_CORE;
        }
        if (check.crafts() <= 0) {
            return CollapseFailure.NO_FOLD;
        }
        if (check.room() <= 0) {
            return CollapseFailure.NO_ROOM;
        }
        if (check.essencePer() > 0 && check.essenceHeld() < check.essencePer()) {
            return CollapseFailure.NOT_ENOUGH_ESSENCE;
        }
        if (Math.abs(check.speed()) < check.minimumSpeed()) {
            return CollapseFailure.NOT_TURNING;
        }
        return CollapseFailure.NONE;
    }

    /**
     * How many crafts one run folds: as many as the arms and cup hold, the output has room for and the tank can
     * pay for - and never more than {@link #MOST}.
     */
    public static int count(Preflight check) {
        int count = Math.min(MOST, Math.min(check.crafts(), check.room()));
        if (check.essencePer() > 0) {
            count = Math.min(count, check.essenceHeld() / check.essencePer());
        }
        return Math.max(0, count);
    }

    /** How long a fold runs, in ticks, from the recipe's own processing time. */
    public static int ticks(int recipeTicks) {
        return Math.max(SHORTEST, recipeTicks);
    }

    /**
     * How many more crafts' worth of a result fit on top of what is already in the output.
     *
     * @param inOutput    how many are already there - 0 if it is empty
     * @param sameItem    whether what is there is the same as what would be made (true if it is empty)
     * @param perCraft    how many one craft makes
     * @param stackLimit  the most the output holds of it
     */
    public static int room(int inOutput, boolean sameItem, int perCraft, int stackLimit) {
        if (perCraft <= 0) {
            return MOST;
        }
        if (inOutput > 0 && !sameItem) {
            return 0;
        }
        return Math.max(0, (stackLimit - inOutput) / perCraft);
    }
}
