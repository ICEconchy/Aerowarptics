package uk.co.iceconchy.aerowarptics.client;

import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.network.client.ClientSableInterpolationState;
import dev.ryanhcode.sable.network.client.SubLevelSnapshotInterpolator;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.airship.BorrowedMotion;
import uk.co.iceconchy.aerowarptics.airship.HullJump;
import uk.co.iceconchy.aerowarptics.compat.simulated.ClientRopes;
import uk.co.iceconchy.aerowarptics.network.ClientboundFoldCrossedPacket;
import uk.co.iceconchy.aerowarptics.trace.ClientTrace;
import uk.co.iceconchy.aerowarptics.trace.Trace;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Makes a client see a hull's teleport as a teleport, and carries its player across with it.
 *
 * <h2>The bug this exists for</h2>
 * Sable does not draw a sub-level where the server last put it. It draws it a tick and a half in the
 * past, interpolated between the poses it has buffered - and a teleport arrives in that buffer as just
 * another pose. So for a tick the client puts the hull part of the way between the two ends (half-way,
 * with the default delay), where it has no chunks and draws nothing; with only the far pose to hand it
 * dead-reckons past it. That is the airship "vanishing" at a warp.
 *
 * <p>The crew see something worse. A player standing on a deck is carried by the change in the hull's
 * pose from one tick to the next, and Sable keeps that change as the player's borrowed momentum (see
 * {@link BorrowedMotion}). Interpolated across a teleport, the change is a couple of thousand blocks:
 * the player is carried that far in a tick, is past the deck by the time it arrives, and goes on at
 * that speed. And because the server had already teleported them to the far side a tick before the
 * hull got there in their own view, even a short warp dropped them onto nothing. Only a player in a
 * seat escaped both, because a seat is fixed to the plot rather than carried by pose changes.
 *
 * <p>The fix before this one only collapsed the last pose onto the current one - which does nothing
 * about the interpolator drawing the half-way pose, and was then spent on that half-way step, leaving
 * the second half of the jump to be read as motion after all.
 *
 * <h2>What happens now</h2>
 * The server says exactly where the hull went (a {@link HullJump}), and everything this client holds
 * for that hull that is still on the near side - every buffered pose, the interpolator's running pose,
 * the current and last poses - goes through the same transform. There is then no jump left anywhere in
 * the client's copy for anything to interpolate or be carried by: as far as this client is concerned
 * the hull was always on the far side. The player, if the server carried them, goes through the same
 * transform in the same moment, so they land on the same plank of the deck they were standing on.
 *
 * <p>The notice and Sable's own pose stream travel separately with no ordering between them, so the
 * near-side poses are looked for again every tick for a short while - a snapshot sent before the jump
 * can still arrive after the notice.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class FoldCrossings {

    /**
     * How long near-side poses are still carried across after the notice.
     *
     * <p>Long enough for any snapshot that was in flight at the jump to land; short enough that a hull
     * cannot plausibly fly half-way back towards where it jumped from in the meantime, which is the one
     * thing that could make a genuine pose read as a straggler.
     */
    private static final int REALIGN_TICKS = 20;

    /**
     * How long to keep a hull under watch for a jump that got past the realignment anyway.
     *
     * <p>Generous on purpose: a watch costs one map lookup a tick, and it is the last line between a
     * missed straggler and a crew thrown off the deck.
     */
    private static final int WATCH_TICKS = 200;

    /** A one-tick move beyond this cannot be flight. Shared with the server; see the packet. */
    private static final double JUMP_BLOCKS = ClientboundFoldCrossedPacket.JUMP_BLOCKS;

    private static final class Watch {
        private final HullJump jump;
        private int realignLeft = REALIGN_TICKS;
        private int ticksLeft = WATCH_TICKS;
        /** Whether this client has held the hull at any point in the watch, for the trace. */
        private boolean seen;

        private Watch(HullJump jump) {
            this.jump = jump;
        }
    }

    private static final Map<UUID, Watch> watching = new HashMap<>();

    private FoldCrossings() {
    }

    /**
     * A hull this client may be holding has jumped.
     *
     * @param carry whether the server carried this client's own player across with it
     */
    public static void crossed(UUID shipId, HullJump jump, boolean carry) {
        watch(shipId, jump);
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        ClientSubLevel ship = ship(level, shipId);
        boolean moved = false;
        if (ship != null) {
            watching.get(shipId).seen = true;
            moved = realign(ship, jump);
            ClientRopes.carry(level, ship, jump);
        }
        // Whether this client had the hull when told it jumped, and whether anything it held was still on
        // the near side. A hull the client does not hold here, and never finds in the watch after, is one
        // this player will not see arrive.
        ClientTrace.log(ClientTrace.FOLD, "ship {} jumped {} blocks {} -> {}: {}{}", shipId,
                Trace.num(jump.distance()), Trace.vec(jump.from()), Trace.vec(jump.to()),
                ship == null ? "NOT HELD by this client yet" : moved ? "held, near-side poses carried across"
                        : "held, already on the far side", carry ? "; carrying this player with it" : "");
        if (carry) {
            carry(minecraft.player, jump);
        }
    }

    /**
     * The server has put this client's player down: take back what the hull lent them, bar what the
     * hull is still doing.
     */
    public static void settle(Vector3dc hullVelocity) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            BorrowedMotion.settle(player, hullVelocity);
        }
    }

    /**
     * Starts, or restarts, the watch on a hull. A newer jump replaces an older one outright: its near
     * side is wherever the hull was last, which is the older jump's far side.
     */
    public static void watch(UUID shipId, HullJump jump) {
        watching.put(shipId, new Watch(jump));
    }

    /** Dropped on disconnect so a watch cannot survive into a different world. */
    public static void clear() {
        watching.clear();
    }

    public static int watched() {
        return watching.size();
    }

    /**
     * Checked before the client ticks as well as after.
     *
     * <p>Entity movement - where a bad jump turns into a thrown player - happens inside the client tick,
     * so a check that only ran afterwards would be a tick late. Running first catches a snapshot that
     * arrived between ticks, which is the common case; running last catches one that arrived during the
     * tick. Doing it twice costs nothing, because the second pass finds nothing left behind.
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
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            clear();
            return;
        }
        refuseImpossibleMomentum(minecraft.player);
        if (watching.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, Watch>> entries = watching.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Watch> entry = entries.next();
            Watch watch = entry.getValue();
            ClientSubLevel ship = ship(level, entry.getKey());
            if (ship != null) {
                if (!watch.seen) {
                    watch.seen = true;
                    ClientTrace.log(ClientTrace.FOLD, "ship {} arrived on this client {} ticks after its jump",
                            entry.getKey(), WATCH_TICKS - watch.ticksLeft);
                }
                if (watch.realignLeft > 0) {
                    realign(ship, watch.jump);
                }
                collapse(ship);
            }
            watch.realignLeft--;
            if (--watch.ticksLeft <= 0) {
                if (!watch.seen) {
                    ClientTrace.log(ClientTrace.FOLD, "ship {} was never held by this client in the {} ticks after"
                            + " its jump - the player will not have seen it arrive", entry.getKey(), WATCH_TICKS);
                }
                entries.remove();
            }
        }
    }

    @Nullable
    private static ClientSubLevel ship(ClientLevel level, UUID shipId) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevel subLevel = container == null ? null : container.getSubLevel(shipId);
        return subLevel instanceof ClientSubLevel client && !client.isRemoved() ? client : null;
    }

    /**
     * Carries everything this client holds for a hull that is still on the near side across the jump.
     *
     * <p>Idempotent: a pose already on the far side is nearer that end and is left alone, which is
     * what lets this run every tick of the window without ever moving anything twice.
     *
     * @return whether anything had to be moved
     */
    private static boolean realign(ClientSubLevel ship, HullJump jump) {
        boolean moved = false;
        boolean resampled = false;
        SubLevelSnapshotInterpolator interpolator = ship.getInterpolator();
        // The interpolator's own lock: snapshots can be delivered off the main thread.
        synchronized (interpolator.buffer) {
            for (int i = 0; i < interpolator.buffer.size(); i++) {
                SubLevelSnapshotInterpolator.Snapshot snapshot = interpolator.buffer.get(i);
                if (jump.behind(snapshot.pose().position())) {
                    // Replaced rather than edited: a buffered pose is the decoded packet's own object.
                    interpolator.buffer.set(i,
                            new SubLevelSnapshotInterpolator.Snapshot(snapshot.gameTick(), jump.moved(snapshot.pose())));
                    moved = true;
                }
            }
            if (moved) {
                resampled = resample(ship, interpolator);
            }
        }
        if (!resampled) {
            // Nothing to sample from - Sable is holding the hull still - so the poses it is holding are
            // real ones, each plainly on one side or the other, and can be carried across like snapshots.
            moved |= carryPose(interpolator.getInterpolatedPose(), jump);
            moved |= carryPose(ship.logicalPose(), jump);
            moved |= carryPose(ship.lastPose(), jump);
        }
        if (moved) {
            // The hull's bounds - and the swept bounds Sable collides entities against - are derived from
            // the poses, and would otherwise still reach back to the near side for a tick.
            ship.forceUpdateBounds();
        }
        return moved;
    }

    /**
     * Re-reads the hull's current and last poses from the corrected buffer, at the same two moments
     * Sable last read them.
     *
     * <p>Not simply carried across like a snapshot, because they are not real poses: they are
     * interpolated, and one taken across the jump sits part-way between the ends, on neither side.
     * Moving that by the jump overshoots the far end; leaving it undershoots. Asking the corrected
     * buffer the same question Sable asked gives exactly what it would have said had the jump never
     * been in it - Sable's own {@code ClientSubLevel.setInitialPosesFrom} does the same when a hull is
     * split.
     *
     * @return whether there was anything to sample from
     */
    private static boolean resample(ClientSubLevel ship, SubLevelSnapshotInterpolator interpolator) {
        if (interpolator.buffer.isEmpty()
                || !(SubLevelContainer.getContainer(ship.getLevel()) instanceof ClientSubLevelContainer container)) {
            return false;
        }
        ClientSableInterpolationState state = container.getInterpolation();
        if (state.isStopped()) {
            return false;
        }
        if (interpolator.getInterpolatedPose() instanceof Pose3d running) {
            interpolator.getSampleAt(state.mostRecentInterpolationTick, running);
        }
        interpolator.getSampleAt(state.mostRecentInterpolationTick, ship.logicalPose());
        if (ship.lastPose() instanceof Pose3d last) {
            interpolator.getSampleAt(state.lastInterpolationTick, last);
        }
        return true;
    }

    private static boolean carryPose(Pose3dc pose, HullJump jump) {
        if (pose instanceof Pose3d mutable && jump.behind(mutable.position())) {
            jump.applyTo(mutable);
            return true;
        }
        return false;
    }

    /**
     * Puts this client's player where the server put them: on the same spot of the same deck.
     *
     * <p>Moved by the jump rather than to a coordinate, because the server and this client disagree
     * about where the deck is by a tick and a half of interpolation. A world-space teleport - what this
     * used to be - lands the player where the deck is on the server, which on a moving hull is a block
     * or two from where it is drawn here, and before this existed it landed them where the hull had not
     * yet arrived at all. The same transform as the hull's own poses cannot disagree with them.
     *
     * <p>Their motion is turned with the hull and kept: they were carried, not stopped.
     */
    private static void carry(@Nullable LocalPlayer player, HullJump jump) {
        if (player == null || player.isPassenger()) {
            // A rider goes wherever its seat goes, and the seat is part of the hull.
            return;
        }
        Vector3d at = jump.apply(new Vector3d(player.getX(), player.getY(), player.getZ()), new Vector3d());
        // moveTo also resets the previous position, so the camera does not sweep across the world.
        player.moveTo(at.x, at.y, at.z);

        Vec3 motion = player.getDeltaMovement();
        Vector3d turned = jump.applyToDirection(new Vector3d(motion.x, motion.y, motion.z), new Vector3d());
        player.setDeltaMovement(turned.x, turned.y, turned.z);
        Vector3d borrowed = BorrowedMotion.of(player);
        if (borrowed != null) {
            BorrowedMotion.lend(player, jump.applyToDirection(borrowed, new Vector3d()));
        }
        player.fallDistance = 0.0F;
    }

    /**
     * The last line: a borrowed speed no hull could have lent is dropped.
     *
     * <p>Nothing flies a hundred and twenty-eight blocks a tick, so a player carrying that much was
     * handed it by a teleport misread as motion - a jump this client was never told about, or one a
     * notice reached too late. Keeping it is the fling.
     */
    private static void refuseImpossibleMomentum(@Nullable LocalPlayer player) {
        if (player == null) {
            return;
        }
        Vector3d borrowed = BorrowedMotion.of(player);
        if (borrowed != null && BorrowedMotion.impossible(borrowed.length())) {
            borrowed.zero();
        }
    }

    /**
     * Brings "where it was" up to "where it is", if the two are impossibly far apart.
     *
     * <p>The fallback for a jump the realignment did not cover. It no longer ends the watch: a hull
     * interpolated across a teleport comes out in two steps, near side to half-way and half-way to far,
     * and the old one-shot collapse spent itself on the first and let the second through.
     */
    private static void collapse(SubLevel subLevel) {
        double moved = subLevel.logicalPose().position().distance(subLevel.lastPose().position());
        if (!isJump(moved)) {
            return;
        }
        subLevel.updateLastPose();
        subLevel.updateBoundingBox();
        subLevel.forceUpdateGlobalBounds();
        // The client keeps a render pose and a swept bounds of its own, both interpolated from the
        // same delta, so they need the same treatment or the hull draws as a streak across the world.
        if (subLevel instanceof ClientSubLevel client) {
            client.forceUpdateBounds();
        }
    }

    /** Whether a move of this size is a teleport rather than flight. Shared with the test. */
    public static boolean isJump(double blocks) {
        return blocks >= JUMP_BLOCKS;
    }

    public static int watchTicks() {
        return WATCH_TICKS;
    }

    public static int realignTicks() {
        return REALIGN_TICKS;
    }
}
