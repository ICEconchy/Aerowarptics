package uk.co.iceconchy.aerowarptics.flotilla;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Where a ship that was shaken loose comes out.
 *
 * <p>An escort that drifts out of station during the countdown still goes - the fold has it - but it
 * does not come out in its slot. It is thrown somewhere off to the side of it, a few seconds after
 * everyone else, through a smaller and rougher rift: the Wake Echo.
 *
 * <p>"Somewhere" still has to be somewhere a ship fits. So the candidates are offered in a shuffled
 * ring round the slot, each one is checked by the caller exactly as any other arrival would be, and
 * the first that passes is taken. If none does, the ship comes out in its own slot after all, which the
 * flagship's search already proved clear - a straggler is late and embarrassed, never lost.
 *
 * <p>Pure, and seeded, so the same straggler on the same fold always tries the same places in the same
 * order and a test can say which one it lands in.
 */
public final class WakeEcho {

    private WakeEcho() {
    }

    /**
     * Places to try, round the slot, as offsets from it.
     *
     * <p>Level with the slot: a straggler is thrown sideways, not dropped on the fleet from above or put
     * underneath it. Spread evenly round the circle with a little jitter, so the first few tries do not
     * all land on the same side.
     *
     * @param seed  anything stable for this straggler on this fold
     * @param count how many to offer
     * @param least the nearest a candidate may be to its slot, in blocks - at least the ship's own
     *              footprint and its tolerance, or it would still be in station
     * @param most  the furthest
     */
    public static List<FormationSlot> candidates(long seed, int count, double least, double most) {
        Random random = new Random(seed);
        double near = Math.max(0.0D, Math.min(least, most));
        double far = Math.max(near, most);
        List<FormationSlot> out = new ArrayList<>();
        int n = Math.max(0, count);
        double start = random.nextDouble() * Math.PI * 2.0D;
        for (int i = 0; i < n; i++) {
            double angle = start + (Math.PI * 2.0D * i) / Math.max(1, n)
                    + (random.nextDouble() - 0.5D) * (Math.PI / Math.max(1, n));
            double distance = near + (far - near) * random.nextDouble();
            out.add(new FormationSlot(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance));
        }
        // Shuffled after being spread, so the order tried is not simply once round the clock.
        java.util.Collections.shuffle(out, random);
        return out;
    }

    /**
     * The first candidate the caller will accept, as a slot in the formation, or the ship's own slot.
     *
     * @param slot       the slot the straggler was meant to arrive in
     * @param candidates offsets from it, from {@link #candidates}
     * @param fits       whether a ship put at that slot would be clear - of the ground and of every other
     *                   ship in the formation
     * @return where to put it, and whether that is off its slot
     */
    public static Placement place(FormationSlot slot, List<FormationSlot> candidates, Predicate<FormationSlot> fits) {
        for (FormationSlot offset : candidates) {
            FormationSlot at = new FormationSlot(slot.right() + offset.right(), slot.up() + offset.up(),
                    slot.ahead() + offset.ahead());
            if (fits.test(at)) {
                return new Placement(at, true);
            }
        }
        return new Placement(slot, false);
    }

    /**
     * Where a straggler comes out.
     *
     * @param slot    in the formation's frame
     * @param thrown  whether it is off its own slot; {@code false} is the fallback
     */
    public record Placement(FormationSlot slot, boolean thrown) {
    }
}
