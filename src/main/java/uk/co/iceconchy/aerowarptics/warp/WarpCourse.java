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
 * <p>A fix also carries the height to come in at, because once the course is set there is nothing
 * else to ask: the probe that chose it may have been moved, re-aimed or broken by the time the warp
 * fires. An anchor course carries none. The anchor is still there to be asked, and its owner can
 * change its height after a course is set, so the anchor's own figure is read when the rift opens -
 * the same way its position is.
 *
 * @param anchorId      the anchor to fly to, or {@code null} for a fix
 * @param fix           the position to fly to, or {@code null} for an anchor
 * @param arrivalHeight blocks above the fix to arrive at, or {@link ArrivalHeight#UNSET} for the
 *                      server default; always {@code UNSET} on an anchor course
 * @param label         what to call this course on a console; anchors refresh theirs from the registry
 */
public record WarpCourse(@Nullable UUID anchorId, @Nullable BlockPos fix, int arrivalHeight, String label) {

    public WarpCourse {
        if ((anchorId == null) == (fix == null)) {
            throw new IllegalArgumentException(
                    "a course is an anchor or a fix, never both and never neither");
        }
        if (label == null) {
            throw new IllegalArgumentException("a course needs a label");
        }
        // Normalised rather than refused, so two courses to the same place compare equal whatever
        // was passed for a height that does not apply - the probe's "is this already the course"
        // test is an equality check.
        if (anchorId != null || arrivalHeight < 0) {
            arrivalHeight = ArrivalHeight.UNSET;
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, WarpCourse> STREAM_CODEC =
            StreamCodec.of(WarpCourse::encode, WarpCourse::decode);

    public static WarpCourse toAnchor(UUID anchorId, String label) {
        return new WarpCourse(anchorId, null, ArrivalHeight.UNSET, label);
    }

    public static WarpCourse toFix(BlockPos fix, int arrivalHeight, String label) {
        return new WarpCourse(null, fix, arrivalHeight, label);
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
        return new WarpCourse(anchorId, fix, arrivalHeight, updated);
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
            tag.putInt("ArrivalHeight", arrivalHeight);
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
            return new WarpCourse(tag.getUUID("Anchor"), null, ArrivalHeight.UNSET, label);
        }
        if (tag.contains("FixX")) {
            // A fix saved before heights were adjustable has none, and follows the server default
            // exactly as it did when it was set.
            return new WarpCourse(null,
                    new BlockPos(tag.getInt("FixX"), tag.getInt("FixY"), tag.getInt("FixZ")),
                    tag.contains("ArrivalHeight") ? tag.getInt("ArrivalHeight") : ArrivalHeight.UNSET,
                    label);
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
            // Shifted by one so UNSET travels as zero rather than as a five-byte negative varint.
            buf.writeVarInt(course.arrivalHeight + 1);
        }
        buf.writeUtf(course.label, 64);
    }

    private static WarpCourse decode(RegistryFriendlyByteBuf buf) {
        boolean anchor = buf.readBoolean();
        return anchor
                ? new WarpCourse(buf.readUUID(), null, ArrivalHeight.UNSET, buf.readUtf(64))
                : new WarpCourse(null, buf.readBlockPos(), buf.readVarInt() - 1, buf.readUtf(64));
    }
}
