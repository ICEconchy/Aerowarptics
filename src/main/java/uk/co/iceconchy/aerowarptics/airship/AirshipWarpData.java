package uk.co.iceconchy.aerowarptics.airship;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AeroWarptics;

import org.joml.Quaterniondc;
import org.joml.Vector3dc;

import java.util.UUID;

/**
 * Warp state attached to an airship rather than to a block.
 *
 * <p>Sable gives every {@link ServerSubLevel} a persistent user-data tag that is written and read
 * with the sub-level itself, so state stored here survives save/load, follows the airship if it is
 * unloaded into holding storage, and cannot be desynchronised from the ship's identity the way a
 * static {@code Map<UUID, ...>} would be.
 *
 * <p>Its job is to be the single authoritative claim on an airship: exactly one Rift Drive may hold
 * a warp at a time, and any drive can find out whether its own ship is already committed. The
 * per-drive details (charge, animation state, cooldown) stay on the drive's own block entity.
 *
 * <p>All keys are namespaced under {@value #ROOT_KEY} so other mods using the same tag are left
 * untouched.
 */
public final class AirshipWarpData {

    public static final String ROOT_KEY = AeroWarptics.MODID;

    private static final String KEY_ACTIVE = "Active";
    private static final String KEY_DRIVE = "Drive";
    private static final String KEY_ANCHOR = "Anchor";
    private static final String KEY_PHASE = "Phase";
    private static final String KEY_PROGRESS = "Progress";
    private static final String KEY_DURATION = "Duration";
    private static final String KEY_START = "StartedAt";
    private static final String KEY_TARGET = "Target";
    private static final String KEY_COOLDOWN_UNTIL = "CooldownUntil";
    private static final String KEY_CANCELLED = "Cancelled";
    private static final String KEY_ORIGIN = "Origin";
    private static final String KEY_HEARTBEAT = "Heartbeat";

    /**
     * Where the backing tag comes from.
     *
     * <p>Normally the live sub-level, but the same logic runs against a detached tag, which is how the
     * one-warp-per-airship rule is exercised without a server.
     */
    public interface Backing {
        @Nullable
        CompoundTag read();

        void write(CompoundTag userData);
    }

    private final Backing backing;

    AirshipWarpData(ServerSubLevel subLevel) {
        this(new Backing() {
            @Nullable
            @Override
            public CompoundTag read() {
                return subLevel.getUserDataTag();
            }

            @Override
            public void write(CompoundTag userData) {
                subLevel.setUserDataTag(userData);
            }
        });
    }

    public AirshipWarpData(Backing backing) {
        this.backing = backing;
    }

    /** Operates on a standalone user-data tag, e.g. one read back off disk. */
    public static AirshipWarpData of(CompoundTag userData) {
        return new AirshipWarpData(new Backing() {
            @Override
            public CompoundTag read() {
                return userData;
            }

            @Override
            public void write(CompoundTag replacement) {
                if (replacement != userData) {
                    userData.getAllKeys().stream().toList().forEach(userData::remove);
                    replacement.getAllKeys().forEach(key -> userData.put(key, replacement.get(key)));
                }
            }
        });
    }

    private CompoundTag root() {
        CompoundTag user = backing.read();
        return user == null ? new CompoundTag() : user.getCompound(ROOT_KEY);
    }

    private void store(CompoundTag section) {
        CompoundTag user = backing.read();
        if (user == null) {
            user = new CompoundTag();
        }
        if (section.isEmpty()) {
            user.remove(ROOT_KEY);
        } else {
            user.put(ROOT_KEY, section);
        }
        backing.write(user);
    }

    // ---------------------------------------------------------------- claims

    /** True while some Rift Drive on this airship owns an in-progress warp. */
    public boolean isWarping() {
        return root().getBoolean(KEY_ACTIVE);
    }

    /** Plot-space position of the drive that owns the current warp, or {@code null}. */
    @Nullable
    public BlockPos owningDrive() {
        CompoundTag tag = root();
        return tag.contains(KEY_DRIVE) ? NbtUtils.readBlockPos(tag, KEY_DRIVE).orElse(null) : null;
    }

    public boolean isOwnedBy(BlockPos drivePos) {
        return drivePos.equals(owningDrive());
    }

    /**
     * Points a claim at the drive's new position, when a crossing into another dimension has rebuilt the
     * ship and the drive with it somewhere else in the plot grid. Nothing happens when nobody holds one.
     */
    public void rebindDrive(BlockPos drivePos) {
        CompoundTag tag = root();
        if (!tag.contains(KEY_DRIVE)) {
            return;
        }
        tag.put(KEY_DRIVE, NbtUtils.writeBlockPos(drivePos));
        store(tag);
    }

    /**
     * Attempts to take the airship's warp lock for a drive.
     *
     * @return {@code false} when another drive already holds it, which is how simultaneous warp
     * requests against the same airship are rejected
     */
    public boolean claim(BlockPos drivePos, UUID anchorId, Vec3 target, int durationTicks, long gameTime) {
        CompoundTag tag = root();
        if (tag.getBoolean(KEY_ACTIVE) && !drivePos.equals(owningDrive())) {
            return false;
        }
        tag.putBoolean(KEY_ACTIVE, true);
        tag.putBoolean(KEY_CANCELLED, false);
        tag.put(KEY_DRIVE, NbtUtils.writeBlockPos(drivePos));
        tag.putUUID(KEY_ANCHOR, anchorId);
        tag.putLong(KEY_START, gameTime);
        tag.putInt(KEY_PROGRESS, 0);
        tag.putInt(KEY_DURATION, Math.max(1, durationTicks));
        tag.putLong(KEY_HEARTBEAT, gameTime);
        tag.put(KEY_TARGET, writeVec(target));
        store(tag);
        return true;
    }

    /** Records progress through the current warp so it can be resumed or reported. */
    public void advance(String phase, int progressTicks) {
        CompoundTag tag = root();
        if (!tag.getBoolean(KEY_ACTIVE)) {
            return;
        }
        tag.putString(KEY_PHASE, phase);
        tag.putInt(KEY_PROGRESS, progressTicks);
        store(tag);
    }

    /**
     * Records progress, and that the owning drive was alive to record it at {@code gameTime}.
     *
     * <p>The heartbeat is what a free cruise needs. A claim used to be judged stale by when it started,
     * which was safe while every warp finished inside a minute - but a cruise has no end, and a second
     * drive on the same hull would otherwise decide ten minutes in that the claim was debris and release
     * it out from under a ship still inside the fold.
     */
    public void advance(String phase, int progressTicks, long gameTime) {
        CompoundTag tag = root();
        if (!tag.getBoolean(KEY_ACTIVE)) {
            return;
        }
        tag.putLong(KEY_HEARTBEAT, gameTime);
        store(tag);
        advance(phase, progressTicks);
    }

    /**
     * Records where the airship was standing before the warp began.
     *
     * <p>Stored on the airship rather than on the drive so it survives independently of the machine
     * that started the journey. A hull left out in the corridor by a crash or a broken drive can
     * always be put back, because the airship itself remembers where home was.
     */
    public void rememberOrigin(Vector3dc position, Quaterniondc orientation) {
        CompoundTag tag = root();
        CompoundTag origin = new CompoundTag();
        origin.putDouble("x", position.x());
        origin.putDouble("y", position.y());
        origin.putDouble("z", position.z());
        origin.putDouble("qx", orientation.x());
        origin.putDouble("qy", orientation.y());
        origin.putDouble("qz", orientation.z());
        origin.putDouble("qw", orientation.w());
        tag.put(KEY_ORIGIN, origin);
        store(tag);
    }

    /** The pose the airship set off from, or {@code null} when none was recorded. */
    @Nullable
    public Pose3d origin() {
        CompoundTag tag = root();
        if (!tag.contains(KEY_ORIGIN)) {
            return null;
        }
        CompoundTag origin = tag.getCompound(KEY_ORIGIN);
        Pose3d pose = new Pose3d();
        pose.position().set(origin.getDouble("x"), origin.getDouble("y"), origin.getDouble("z"));
        pose.orientation().set(origin.getDouble("qx"), origin.getDouble("qy"),
                origin.getDouble("qz"), origin.getDouble("qw"));
        return pose;
    }

    /**
     * Gives up the way back.
     *
     * <p>Called the moment the hull is through the fold. Until then the origin is a rescue: a warp
     * that dies with the ship still on the near side can put it back where it started. Afterwards it
     * is a hazard, because the ship is not stranded any more - it has arrived - and "put it back
     * where it started" means dragging a hull that got where it was going four thousand blocks
     * backwards.
     *
     * <p>That is not hypothetical. An interrupted warp left the origin behind, {@code
     * releaseStaleClaim} found a claim it thought was stranded, and the ship was returned to its
     * mooring some seconds after visibly arriving at the destination.
     */
    public void forgetOrigin() {
        CompoundTag tag = root();
        tag.remove(KEY_ORIGIN);
        store(tag);
    }

    /**
     * Puts the airship back where it set off from, if that is known.
     *
     * <p>This is the safety net for an interrupted transit. It is a no-op for a warp that never left
     * the ground, so calling it on any abort is safe.
     *
     * @return whether the airship was moved
     */
    public boolean restoreOrigin(Airship airship) {
        Pose3d origin = origin();
        if (origin == null) {
            return false;
        }
        boolean moved = airship.relocate(origin.position(), origin.orientation(), 0.0D);
        CompoundTag tag = root();
        tag.remove(KEY_ORIGIN);
        store(tag);
        return moved;
    }

    /** Releases the lock, optionally recording that the warp was cancelled rather than completed. */
    public void release(boolean cancelled, long cooldownUntil) {
        CompoundTag tag = root();
        tag.putBoolean(KEY_ACTIVE, false);
        tag.putBoolean(KEY_CANCELLED, cancelled);
        tag.remove(KEY_DRIVE);
        tag.remove(KEY_ANCHOR);
        tag.remove(KEY_TARGET);
        tag.remove(KEY_PHASE);
        tag.remove(KEY_ORIGIN);
        tag.putInt(KEY_PROGRESS, 0);
        tag.putLong(KEY_COOLDOWN_UNTIL, cooldownUntil);
        store(tag);
    }

    /** Clears every trace of this mod from the airship's data bag. */
    public void clear() {
        store(new CompoundTag());
    }

    // ---------------------------------------------------------------- reads

    @Nullable
    public UUID destinationAnchor() {
        CompoundTag tag = root();
        return tag.hasUUID(KEY_ANCHOR) ? tag.getUUID(KEY_ANCHOR) : null;
    }

    @Nullable
    public Vec3 target() {
        CompoundTag tag = root();
        return tag.contains(KEY_TARGET) ? readVec(tag.getCompound(KEY_TARGET)) : null;
    }

    public String phase() {
        return root().getString(KEY_PHASE);
    }

    public int progressTicks() {
        return root().getInt(KEY_PROGRESS);
    }

    public int durationTicks() {
        return Math.max(1, root().getInt(KEY_DURATION));
    }

    public long startedAt() {
        return root().getLong(KEY_START);
    }

    public long cooldownUntil() {
        return root().getLong(KEY_COOLDOWN_UNTIL);
    }

    public boolean wasCancelled() {
        return root().getBoolean(KEY_CANCELLED);
    }

    /**
     * Drops a stale claim left behind by a server restart or a removed drive.
     *
     * <p>Called when a drive comes back with no warp of its own but finds the ship still claiming
     * one, which is the interrupted-warp recovery path.
     */
    public boolean releaseIfStale(long gameTime, int maxAgeTicks) {
        CompoundTag tag = root();
        if (!tag.getBoolean(KEY_ACTIVE)) {
            return false;
        }
        // Measured from the last sign of life rather than the start, so a cruise that has been going
        // for an hour is not mistaken for one abandoned an hour ago. A claim written before the
        // heartbeat existed has none, and falls back to its start exactly as it always did.
        long seen = Math.max(tag.getLong(KEY_START), tag.getLong(KEY_HEARTBEAT));
        long age = gameTime - seen;
        if (gameTime < tag.getLong(KEY_START) || age > maxAgeTicks) {
            release(true, gameTime);
            return true;
        }
        return false;
    }

    /**
     * The warp lock in one line, for the trace: who holds it, how far their warp has got, and how long
     * since they were last heard from.
     *
     * <p>{@code AIRSHIP_ALREADY_WARPING} means some drive holds this, and on its own does not say which
     * or why; a lock left by a drive that has since been broken or moved, and not yet stale, looks exactly
     * like a ship that is busy.
     */
    public String describe(long gameTime) {
        CompoundTag tag = root();
        if (!tag.getBoolean(KEY_ACTIVE)) {
            long cooldown = tag.getLong(KEY_COOLDOWN_UNTIL) - gameTime;
            return cooldown > 0L ? "free (hull cooling " + cooldown + "t)" : "free";
        }
        BlockPos owner = owningDrive();
        long started = tag.getLong(KEY_START);
        long heard = Math.max(started, tag.getLong(KEY_HEARTBEAT));
        return String.format(java.util.Locale.ROOT, "held by drive %s phase=%s progress=%d/%dt started %dt ago,"
                        + " last heard %dt ago%s",
                owner == null ? "?" : owner.toShortString(), tag.getString(KEY_PHASE).isEmpty() ? "-"
                        : tag.getString(KEY_PHASE),
                tag.getInt(KEY_PROGRESS), Math.max(1, tag.getInt(KEY_DURATION)), gameTime - started, gameTime - heard,
                tag.contains(KEY_ORIGIN) ? ", origin kept" : "");
    }

    private static CompoundTag writeVec(Vec3 vec) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("x", vec.x);
        tag.putDouble("y", vec.y);
        tag.putDouble("z", vec.z);
        return tag;
    }

    private static Vec3 readVec(CompoundTag tag) {
        return new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
    }
}
