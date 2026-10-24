package uk.co.iceconchy.aerowarptics.wellhead;

/**
 * The ground a meltdown left unsettled: where, how far, and until when.
 *
 * <p>Pure arithmetic, so how a zone fades can be tested without a world. Times are the level's game time, so a
 * zone neither lingers nor lapses for a server having been switched off.
 *
 * @param centre  the fissure that was, as a packed block position
 * @param radius  blocks from it the zone reaches
 * @param began   game time it was left
 * @param expires game time it is gone
 */
public record ScarZone(long centre, int radius, long began, long expires) {

    /** How much of the zone is left, 1 as it is made down to 0 as it goes. Nothing once it has gone. */
    public double strength(long now) {
        if (now >= expires || expires <= began) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, (expires - now) / (double) (expires - began)));
    }

    public boolean over(long now) {
        return now >= expires;
    }

    /** How deep in the zone a point this far from its middle is: 1 at the middle, 0 at the edge and beyond. */
    public double depth(double distance) {
        return radius <= 0 ? 0.0D : Math.max(0.0D, 1.0D - distance / radius);
    }

    /** How hard the zone bears on something this far from its middle, now: its strength by how deep it stands. */
    public double bears(double distance, long now) {
        double depth = depth(distance);
        // Never nothing inside the edge while the zone lasts: the rim of a fresh one is not safe ground.
        return depth <= 0.0D ? 0.0D : strength(now) * (0.3D + 0.7D * depth);
    }
}
