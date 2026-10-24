package uk.co.iceconchy.aerowarptics.flotilla;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchor;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorRegistry;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.warp.ArrivalHeight;
import uk.co.iceconchy.aerowarptics.warp.WarpCourse;
import uk.co.iceconchy.aerowarptics.warp.WarpValidator;

/**
 * Where a flagship is set to go, read fresh off its drive the way the drive's own console reads it.
 *
 * <p>Not remembered anywhere of its own. The course lives on the flagship's drive, set at an Astrolabe
 * where permission was checked, and an anchor can be renamed, moved or taken down while a flotilla is
 * mustering - so every question about the destination goes back to the drive and the anchor registry
 * each time it is asked, and a Fleet Board never shows a destination its flagship is no longer going to.
 *
 * @param level         the level the course ends in
 * @param target        the anchor's block, or the fix
 * @param arrivalHeight blocks above the target to come in at, resolved as the rift would resolve it
 * @param label         what to call it
 * @param distance      how long the jump is, as priced; across dimensions the configured length
 * @param crossing      whether it ends in another dimension
 */
public record FleetCourse(ServerLevel level, BlockPos target, int arrivalHeight, String label, double distance,
                          boolean crossing) {

    /**
     * The course a flagship's drive is set to, or {@code null} with none, an anchor that has gone, or a
     * dimension that is not there.
     */
    @Nullable
    public static FleetCourse of(ServerLevel here, RiftDriveBlockEntity drive, Airship flagship) {
        WarpCourse course = drive.standingCourse();
        if (course == null) {
            return null;
        }
        if (course.isAnchor()) {
            WarpAnchor anchor = WarpAnchorRegistry.get(here).byId(course.anchorId());
            if (anchor == null) {
                return null;
            }
            ServerLevel destination = here.getServer().getLevel(anchor.dimension());
            if (destination == null) {
                return null;
            }
            boolean crossing = !destination.dimension().equals(here.dimension());
            int height = ArrivalHeight.planned(anchor.arrivalHeight(), course.holdAltitude(), !crossing,
                    flagship.worldBounds().minY(), anchor.pos().getY());
            return new FleetCourse(destination, anchor.pos(), height, anchor.displayName(),
                    WarpValidator.warpDistance(flagship, anchor), crossing);
        }
        // A fix a Rift Probe took in another dimension names it; every other fix is in this one.
        BlockPos fix = course.fix();
        ServerLevel destination = course.dimension() == null ? here : here.getServer().getLevel(course.dimension());
        if (destination == null) {
            return null;
        }
        boolean crossing = !destination.dimension().equals(here.dimension());
        int height = ArrivalHeight.planned(course.arrivalHeight(), course.holdAltitude(), !crossing,
                flagship.worldBounds().minY(), fix.getY());
        return new FleetCourse(destination, fix, height, course.label(),
                WarpValidator.fixDistance(flagship, course), crossing);
    }

    /**
     * How far an escort would be thrown to reach its slot at the far end: its own distance from where
     * the slot will be, or the configured crossing length when the course leaves the dimension.
     */
    public double distanceFor(Airship escort, org.joml.Vector3dc slotAtArrival) {
        if (crossing) {
            return AWConfig.CROSS_DIMENSION_DISTANCE.get();
        }
        return escort.centre(new org.joml.Vector3d()).distance(slotAtArrival);
    }
}
