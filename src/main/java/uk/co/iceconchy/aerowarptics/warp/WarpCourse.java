package uk.co.iceconchy.aerowarptics.warp;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Somewhere a Rift Drive has been told to go.
 *
 * <p>Until the Rift Probe there was only one kind of destination - an anchor - and the drive stored a
 * UUID. A sounding has no anchor at the far end and never will: the whole point of a blind jump is
 * that nobody has been there to put one down. So a course is now either an anchor to look up or a
 * fix to fly at, and everything downstream asks the course rather than the registry.
 *
 * <p>Exactly one of the two is set, which the compact constructor enforces. A course that was neither
 * would be a drive that believes it has somewhere to go and cannot say where, and that is the sort of
 * state that turns into an aborted warp halfway down a corridor.
 *
 * @param anchorId the anchor to fly to, or {@code null} for a fix
 * @param fix      the position to fly to, or {@code null} for an anchor
 * @param label    what to call this course on a console; anchors refresh theirs from the registry
 */
public record WarpCourse(@Nullable UUID anchorId, @Nullable BlockPos fix, String label) {

    public WarpCourse {
        if ((anchorId == null) == (fix == null)) {
            throw new IllegalArgumentException(
                    "a course is an anchor or a fix, never both and never neither");
        }
        if (label == null) {
            throw new IllegalArgumentException("a course needs a label");
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, WarpCourse> STREAM_CODEC =
            StreamCodec.of(WarpCourse::encode, WarpCourse::decode);

    public static WarpCourse toAnchor(UUID anchorId, String label) {
        return new WarpCourse(anchorId, null, label);
    }

    public static WarpCourse toFix(BlockPos fix, String label) {
        return new WarpCourse(null, fix, label);
    }

    public boolean isAnchor() {
        return anchorId != null;
    }

    public boolean isFix() {
        return fix != null;
    }

    /**
     * A course to the same place, renamed.
     *
     * <p>An anchor can be renamed after a course is set, and a console showing the old name would be
     * quietly wrong about where the ship is going.
     */
    public WarpCourse withLabel(String updated) {
        return new WarpCourse(anchorId, fix, updated);
    }

    // -------------------------------------------------------------------- nbt

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (anchorId != null) {
            tag.putUUID("Anchor", anchorId);
        }
        if (fix != null) {
            tag.putInt("FixX", fix.getX());
            tag.putInt("FixY", fix.getY());
            tag.putInt("FixZ", fix.getZ());
        }
        tag.putString("Label", label);
        return tag;
    }

    /** Reads a course back, returning {@code null} for anything that is not one. */
    @Nullable
    public static WarpCourse load(@Nullable CompoundTag tag) {
        if (tag == null) {
            return null;
        }
        String label = tag.getString("Label");
        if (tag.hasUUID("Anchor")) {
            return new WarpCourse(tag.getUUID("Anchor"), null, label);
        }
        if (tag.contains("FixX")) {
            return new WarpCourse(null,
                    new BlockPos(tag.getInt("FixX"), tag.getInt("FixY"), tag.getInt("FixZ")), label);
        }
        return null;
    }

    // ------------------------------------------------------------------- wire

    private static void encode(RegistryFriendlyByteBuf buf, WarpCourse course) {
        buf.writeBoolean(course.isAnchor());
        if (course.anchorId != null) {
            buf.writeUUID(course.anchorId);
        } else {
            buf.writeBlockPos(course.fix);
        }
        buf.writeUtf(course.label, 64);
    }

    private static WarpCourse decode(RegistryFriendlyByteBuf buf) {
        boolean anchor = buf.readBoolean();
        return anchor
                ? new WarpCourse(buf.readUUID(), null, buf.readUtf(64))
                : new WarpCourse(null, buf.readBlockPos(), buf.readUtf(64));
    }
}
