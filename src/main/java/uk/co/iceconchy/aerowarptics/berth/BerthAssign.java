package uk.co.iceconchy.aerowarptics.berth;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3dc;
import uk.co.iceconchy.aerowarptics.warpnet.Membership;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Which berth a ship is given, as one rule.
 *
 * <p>Of a station's berths that are switched on, free, open to whoever opened the rift and big enough for
 * the vessel as it will sit, the <em>smallest</em> - so a skiff does not take the dreadnought's slip while
 * a skiff's stands empty beside it. Berths that fit equally well go by their priority, lower first, and
 * then by how near the anchor they are.
 *
 * <p>When there is none, the reason is the first of these that is true, and it is the one a pilot can do
 * something about: no berth is open to them at all; none that is would hold the ship; or every one that
 * would is taken. It takes only facts and is tested as a table - a station that hands a ship the wrong
 * slip, or refuses it for the wrong reason, runs exactly like one that does not.
 */
public final class BerthAssign {

    private BerthAssign() {
    }

    /** Why a ship was given no berth. */
    public enum Refusal {
        NONE,
        /** Every berth that would take the ship has one in it, or one on its way. */
        NO_FREE_BERTH,
        /** No berth open to the pilot is big enough for the ship, empty or not. */
        BERTH_TOO_SMALL,
        /** No berth at the station is open to the pilot. */
        BERTH_FORBIDDEN,
        /** A berth would have taken the ship, and something stands in its slip or its lane. */
        BERTH_OBSTRUCTED
    }

    /**
     * A ship asking for a berth.
     *
     * @param ship        which ship, so that a berth already reserved for it or holding it counts as its own
     * @param sizeX       the vessel's own box, in its own frame - never a world box
     * @param orientation the ship-to-world rotation it has now
     * @param bow         the way its bow points now, in the world
     * @param who         whoever opened the rift, or {@code null} for a rift nobody can be named for
     */
    public record Vessel(UUID ship, double sizeX, double sizeY, double sizeZ, Quaterniondc orientation,
                         Vector3dc bow, @Nullable UUID who) {

        public double volume() {
            return sizeX * sizeY * sizeZ;
        }
    }

    /**
     * A ship and the berth it was given.
     *
     * @param attitude the ship-to-world rotation it will sit at
     */
    public record Choice(Vessel vessel, Berth berth, Quaterniond attitude) {
    }

    /**
     * What a station made of a fleet.
     *
     * @param choices one for every vessel asked about, in the order they were asked; empty when refused
     * @param refusal why not, and
     * @param refused which vessel could not be berthed - the whole fleet is refused for it
     */
    public record Fleet(List<Choice> choices, Refusal refusal, @Nullable Vessel refused) {

        public boolean berthed() {
            return refusal == Refusal.NONE;
        }
    }

    /**
     * The station's facts for one asking.
     *
     * @param anchorX   where the anchor is, for the last tie-break
     * @param clearance the air kept round a hull, which is part of the fit - see {@link BerthFit}
     * @param now       the overworld game time, against which a reservation has lapsed or not
     */
    public record Station(Collection<Berth> berths, double anchorX, double anchorZ, double clearance, long now,
                          Membership memberships) {
    }

    /**
     * Every berth a vessel could be given, best first.
     *
     * @param taken berths not to offer: found obstructed, or already given to another ship of the same fleet
     */
    public static List<Choice> ranked(Station station, Vessel vessel, Set<UUID> taken) {
        List<Choice> out = new ArrayList<>();
        for (Berth berth : station.berths()) {
            if (taken.contains(berth.id()) || !berth.enabled() || !available(berth, vessel, station.now())
                    || !berth.permits(vessel.who(), station.memberships())) {
                continue;
            }
            Quaterniond attitude = BerthMooring.attitude(vessel.orientation(), vessel.bow(), berth.box());
            if (BerthFit.fits(vessel.sizeX(), vessel.sizeY(), vessel.sizeZ(), attitude, berth.box(),
                    station.clearance())) {
                out.add(new Choice(vessel, berth, attitude));
            }
        }
        out.sort(Comparator
                .comparingDouble((Choice choice) -> choice.berth().box().volume())
                .thenComparingInt(choice -> choice.berth().priority())
                .thenComparingDouble(choice -> distance(choice.berth(), station))
                // Nothing above tells two identical slips the same way from the anchor apart, and a choice
                // that depends on the order a map happened to hand them over is a different berth after a restart.
                .thenComparing(choice -> choice.berth().id()));
        return out;
    }

    /**
     * Why {@link #ranked} came back with nothing. {@link Refusal#NONE} where it would not have.
     *
     * <p>{@code taken} berths count as taken, which is all this can know of them; a caller that struck a
     * berth off for an obstruction says so itself.
     */
    public static Refusal why(Station station, Vessel vessel, Set<UUID> taken) {
        boolean open = false;
        boolean fitting = false;
        for (Berth berth : station.berths()) {
            if (!berth.enabled() || !berth.permits(vessel.who(), station.memberships())) {
                continue;
            }
            open = true;
            Quaterniond attitude = BerthMooring.attitude(vessel.orientation(), vessel.bow(), berth.box());
            if (!BerthFit.fits(vessel.sizeX(), vessel.sizeY(), vessel.sizeZ(), attitude, berth.box(),
                    station.clearance())) {
                continue;
            }
            fitting = true;
            if (!taken.contains(berth.id()) && available(berth, vessel, station.now())) {
                return Refusal.NONE;
            }
        }
        if (!open) {
            return Refusal.BERTH_FORBIDDEN;
        }
        return fitting ? Refusal.NO_FREE_BERTH : Refusal.BERTH_TOO_SMALL;
    }

    /**
     * A berth for every ship of a fleet, or none for any of them.
     *
     * <p>The largest ship chooses first. Taken in the order they were asked, a small flagship takes the one
     * slip its largest escort fits, and a fleet the station has room for is refused.
     */
    public static Fleet fleet(Station station, List<Vessel> vessels) {
        List<Vessel> bySize = new ArrayList<>(vessels);
        bySize.sort(Comparator.comparingDouble(Vessel::volume).reversed());
        Set<UUID> taken = new HashSet<>();
        List<Choice> made = new ArrayList<>();
        for (Vessel vessel : bySize) {
            List<Choice> offered = ranked(station, vessel, taken);
            if (offered.isEmpty()) {
                Refusal reason = why(station, vessel, taken);
                // Everything it fits has gone to its own fleet: that is still a station with no berth free.
                return new Fleet(List.of(), reason == Refusal.NONE ? Refusal.NO_FREE_BERTH : reason, vessel);
            }
            Choice choice = offered.get(0);
            taken.add(choice.berth().id());
            made.add(choice);
        }
        List<Choice> inOrder = new ArrayList<>();
        for (Vessel vessel : vessels) {
            for (Choice choice : made) {
                if (choice.vessel() == vessel) {
                    inOrder.add(choice);
                }
            }
        }
        return new Fleet(List.copyOf(inOrder), Refusal.NONE, null);
    }

    /**
     * Whether a berth has room for this ship: free, or already this ship's own - reserved for it by a warp
     * that is being planned again, or lying in it having been cast off, which is how a ship comes back to the
     * slip it left a minute ago.
     */
    private static boolean available(Berth berth, Vessel vessel, long now) {
        if (berth.free(now)) {
            return true;
        }
        return berth.concerns(vessel.ship()) && (berth.state() == BerthState.RESERVED || berth.castOff());
    }

    private static double distance(Berth berth, Station station) {
        BerthBox box = berth.box();
        return Math.hypot(box.centreX() - station.anchorX(), box.centreZ() - station.anchorZ());
    }
}
