package uk.co.iceconchy.aerowarptics.bond;

import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.airship.Airship;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The sections of a ship that Rift Bonds hold together for a warp.
 *
 * <p>Some ships are built as several sub-levels that only fly in formation: nothing joins them, so
 * {@link Airship#parts()} found one section and a warp took that section and left the rest. A Rift Bond on
 * each, tuned to the same bond, says they are one vessel. That is all it says - it is not a joint, and it
 * holds nothing together in ordinary flight.
 *
 * <p>Every Rift Bond on the server reports here as it loads and as it goes, and {@link Airship} asks this
 * for each bond it finds aboard a hull. Server thread only.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID)
public final class RiftBonds {

    private static final BondLedger<RiftBondBlockEntity> LEDGER = new BondLedger<>();

    private RiftBonds() {
    }

    static void join(UUID bond, RiftBondBlockEntity block) {
        LEDGER.join(bond, block);
    }

    static void leave(UUID bond, RiftBondBlockEntity block) {
        LEDGER.leave(bond, block);
    }

    /** A server has stopped: its block entities are gone, and the next world's must not be answered with them. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LEDGER.clear();
    }

    /**
     * Every other sub-level this bond holds to the one it stands on.
     *
     * <p>Only what is loaded, in the same level, aboard a ship, and within {@code bonds.bondReach} of this
     * block. The reach is there so that a bond left behind on a wreck a continent away does not have that
     * wreck brought along by every warp its old ship makes; it is measured block to block, in the world.
     * A bond on the ground is nobody's section and is passed over - the ground is not carried.
     */
    public static List<SubLevel> bondedTo(RiftBondBlockEntity block) {
        UUID bond = block.bond();
        Level level = block.getLevel();
        if (bond == null || level == null || level.isClientSide) {
            return List.of();
        }
        Airship aboard = Airship.containing(block);
        if (aboard == null) {
            return List.of();
        }
        // Asked for by a walk that found this block in its chunk, so it is certainly standing - which makes
        // this the place to mend a block that was never told it had loaded.
        LEDGER.join(bond, block);
        Vec3 here = aboard.toWorld(Vec3.atCenterOf(block.getBlockPos()));
        double reach = reach();
        List<SubLevel> found = new ArrayList<>();
        for (RiftBondBlockEntity other : LEDGER.members(bond)) {
            if (other == block || other.getLevel() != level) {
                continue;
            }
            if (other.isRemoved() || Airship.orphaned(other)) {
                LEDGER.leave(bond, other);
                continue;
            }
            Airship theirs = Airship.containing(other);
            if (theirs == null || theirs.subLevel() == aboard.subLevel() || !theirs.isActive()) {
                continue;
            }
            Vec3 there = theirs.toWorld(Vec3.atCenterOf(other.getBlockPos()));
            if (within(here.distanceTo(there), reach) && !found.contains(theirs.subLevel())) {
                found.add(theirs.subLevel());
            }
        }
        return found;
    }

    /** Whether two bonds this far apart still hold. Pure, so the rule can be tested without a ship. */
    public static boolean within(double distance, double reach) {
        return Double.isFinite(distance) && distance <= reach;
    }

    private static double reach() {
        try {
            return AWConfig.BOND_REACH.get();
        } catch (IllegalStateException e) {
            // Config not loaded yet - happens in unit tests.
            return 256.0D;
        }
    }
}
