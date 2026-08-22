package uk.co.iceconchy.aerowarptics.client;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import uk.co.iceconchy.aerowarptics.AeroWarptics;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Tells a client that the hull it is watching teleported, rather than moved very fast.
 *
 * <h2>The bug this exists for</h2>
 * Sable syncs a sub-level's pose as a stream of snapshots. There is no "this was a teleport" flag in
 * that stream, so a client cannot tell a jump from motion - it simply sees the ship somewhere else on
 * the next tick and carries on.
 *
 * <p>That matters because {@code SubLevelEntityCollision} sweeps an entity against a moving platform
 * by mapping the entity's box into the sub-level's frame at the <em>previous</em> pose and at the
 * current one, and unioning the two. Across a four-thousand-block warp that union is a box four
 * thousand blocks long, and once its volume passes Sable's sanity limit Sable logs
 * {@code "Enormous local sub-level collision bounds, quitting."} and abandons the collision entirely.
 *
 * <p>The consequences are what a player actually reports: with the collision abandoned there is no
 * deck under them, so they are dropped through the ship, and the client's bounds for the hull are
 * nonsense, so it stops drawing - which looks exactly like the airship having been deleted. Both
 * symptoms, one cause, and only on long jumps because a short one stays under the limit.
 *
 * <p>The server does not have this problem: {@code Airship.relocate} collapses the pose delta on the
 * server's own sub-level as soon as the teleport lands. This is the same collapse, applied to the
 * copies the clients hold.
 *
 * <h2>Why a window rather than a single message</h2>
 * The notice and Sable's own pose snapshot are separate streams with no ordering between them, so the
 * notice routinely arrives while the client still believes the ship is at the old position. Collapsing
 * then would do nothing at all. So a notice opens a short watch instead, and the collapse happens on
 * whichever tick the jump actually shows up.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class FoldCrossings {

    /**
     * How long to watch for a jump after being told one is coming.
     *
     * <p>Generous on purpose. Waiting too long costs one map lookup a tick; giving up too early costs
     * the player their footing, and the whole point of the window is that the arrival time is not
     * something this side can predict.
     */
    private static final int WATCH_TICKS = 100;

    /**
     * A one-tick move beyond this cannot be flight.
     *
     * <p>An airship under way covers a couple of blocks a tick; the warp's own passage through the
     * corridor is under two. Anything past this is a discontinuity, and the only thing in this mod
     * that produces one is the crossing itself.
     */
    private static final double JUMP_BLOCKS = 128.0D;

    private static final Map<UUID, Integer> watching = new HashMap<>();

    private FoldCrossings() {
    }

    /** A hull is about to cross, or has just crossed. Watch it until the jump shows up. */
    public static void expect(UUID shipId) {
        watching.put(shipId, WATCH_TICKS);
    }

    /** Dropped on disconnect so a watch cannot survive into a different world. */
    public static void clear() {
        watching.clear();
    }

    static int watched() {
        return watching.size();
    }

    /**
     * Checked before the client ticks as well as after.
     *
     * <p>Entity movement - and therefore the collision that blows up - happens inside the client
     * tick, so a check that only ran afterwards would be a tick late and the crew would already have
     * been dropped. Running first catches a pose that arrived between ticks, which is the common
     * case; running last catches one that arrived during the tick. Collapsing twice costs nothing,
     * because the second pass finds no delta left to collapse.
     */
    @SubscribeEvent
    public static void beforeClientTick(ClientTickEvent.Pre event) {
        sweep();
    }

    @SubscribeEvent
    public static void afterClientTick(ClientTickEvent.Post event) {
        sweep();
    }

    private static void sweep() {
        if (watching.isEmpty()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            clear();
            return;
        }

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        Iterator<Map.Entry<UUID, Integer>> entries = watching.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Integer> entry = entries.next();
            SubLevel subLevel = container == null ? null : container.getSubLevel(entry.getKey());
            if (subLevel != null && collapse(subLevel)) {
                entries.remove();
                continue;
            }
            // Not setValue: that returns the old value, so the countdown would run a tick long.
            int left = entry.getValue() - 1;
            if (left <= 0) {
                entries.remove();
            } else {
                entry.setValue(left);
            }
        }
    }

    /**
     * Brings "where it was" up to "where it is", if the two are impossibly far apart.
     *
     * @return whether a jump was found and collapsed
     */
    private static boolean collapse(SubLevel subLevel) {
        double moved = subLevel.logicalPose().position().distance(subLevel.lastPose().position());
        if (moved < JUMP_BLOCKS) {
            return false;
        }
        subLevel.updateLastPose();
        subLevel.updateBoundingBox();
        subLevel.forceUpdateGlobalBounds();
        // The client keeps a render pose and a swept bounds of its own, both interpolated from the
        // same delta, so they need the same treatment or the hull draws as a streak across the world.
        if (subLevel instanceof ClientSubLevel client) {
            client.forceUpdateBounds();
        }
        return true;
    }

    /** Whether a move of this size is a teleport rather than flight. Shared with the test. */
    public static boolean isJump(double blocks) {
        return blocks >= JUMP_BLOCKS;
    }

    public static int watchTicks() {
        return WATCH_TICKS;
    }
}
