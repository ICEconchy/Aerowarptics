package uk.co.iceconchy.aerowarptics.warp;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
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
 * <p>A fix also says which dimension it is in, since a Rift Probe can sound one the ship is not in. An
 * anchor course never does: the registry knows where an anchor is, and an anchor can be carried somewhere
 * else after a course is set. A fix that names no dimension is in whichever one the drive is in when it
 * fires - every fix saved before a probe could look elsewhere, and a Rift Beacon's, which summons a ship
 * to where the player is standing.
 *
 * <p>Either kind can instead be told to hold the ship's altitude, from the Astrolabe. That is part of
 * the course rather than a setting on the drive, because a redstone edge replays the decision made at
 * the chart - and "go there, and keep this height" is one decision, not a destination plus a switch
 * somebody else might have flipped since.
 *
 * @param anchorId      the anchor to fly to, or {@code null} for a fix
 * @param fix           the position to fly to, or {@code null} for an anchor
 * @param arrivalHeight blocks above the fix to arrive at, or {@link ArrivalHeight#UNSET} for the
 *                      server default; always {@code UNSET} on an anchor course
 * @param label         what to call this course on a console; anchors refresh theirs from the registry
 * @param holdAltitude  arrive at the altitude the hull is flying at when the rift opens, in place of
 *                      the destination's own height - see {@link ArrivalHeight#planned}
 * @param dimension     the dimension a fix is in, or {@code null} for the drive's own; always
 *                      {@code null} on an anchor course
 */
public record WarpCourse(@Nullable UUID anchorId, @Nullable BlockPos fix, int arrivalHeight, String label,
                         boolean holdAltitude, @Nullable ResourceKey<Level> dimension) {

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
        if (anchorId != null) {
            dimension = null;
        }
    }

    /** A course in the drive's own dimension, which is every course but a probe's into another. */
    public WarpCourse(@Nullable UUID anchorId, @Nullable BlockPos fix, int arrivalHeight, String label,
                      boolean holdAltitude) {
        this(anchorId, fix, arrivalHeight, label, holdAltitude, null);
    }

    /** A course that comes in at the destination's own height, which is every course but the chart's. */
    public WarpCourse(@Nullable UUID anchorId, @Nullable BlockPos fix, int arrivalHeight, String label) {
        this(anchorId, fix, arrivalHeight, label, false);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, WarpCourse> STREAM_CODEC =
            StreamCodec.of(WarpCourse::encode, WarpCourse::decode);

    public static WarpCourse toAnchor(UUID anchorId, String label) {
        return new WarpCourse(anchorId, null, ArrivalHeight.UNSET, label);
    }

    public static WarpCourse toFix(BlockPos fix, int arrivalHeight, String label) {
        return new WarpCourse(null, fix, arrivalHeight, label);
    }

    /** A fix in a named dimension, which need not be the one the drive is in. */
    public static WarpCourse toFix(@Nullable ResourceKey<Level> dimension, BlockPos fix, int arrivalHeight,
                                   String label) {
        return new WarpCourse(null, fix, arrivalHeight, label, false, dimension);
    }

    public boolean isAnchor() {
        return anchorId != null;
    }

    public boolean isFix() {
        return fix != null;
    }

    /**
     * Whether a fix is in the dimension {@code here}, which is the one the drive asking is in.
     *
     * <p>Only a fix can answer. An anchor's dimension is the registry's to say, and asking the course
     * would be asking where the anchor was when the course was set.
     */
    public boolean fixIsIn(ResourceKey<Level> here) {
        return dimension == null || dimension.equals(here);
    }

    /**
     * A course to the same place, renamed.
     *
     * <p>An anchor can be renamed after a course is set, and a console showing the old name would be
     * quietly wrong about where the ship is going.
     */
    public WarpCourse withLabel(String updated) {
        return new WarpCourse(anchorId, fix, arrivalHeight, updated, holdAltitude, dimension);
    }

    /** The same course, holding altitude or not. A fix keeps its own height for when it is let go. */
    public WarpCourse withHoldAltitude(boolean hold) {
        return new WarpCourse(anchorId, fix, arrivalHeight, label, hold, dimension);
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
            if (dimension != null) {
                tag.putString("Dimension", dimension.location().toString());
            }
        }
        tag.putString("Label", label);
        if (holdAltitude) {
            tag.putBoolean("HoldAltitude", true);
        }
        return tag;
    }

    /** Reads a course back, returning {@code null} for anything that is not one. */
    @Nullable
    public static WarpCourse load(@Nullable CompoundTag tag) {
        if (tag == null) {
            return null;
        }
        String label = tag.getString("Label");
        // Absent on everything saved before the chart could hold altitude, which reads as false - the
        // destination's own height, exactly as those courses flew when they were set.
        boolean hold = tag.getBoolean("HoldAltitude");
        if (tag.hasUUID("Anchor")) {
            return new WarpCourse(tag.getUUID("Anchor"), null, ArrivalHeight.UNSET, label, hold);
        }
        if (tag.contains("FixX")) {
            // A fix saved before heights were adjustable has none, and follows the server default
            // exactly as it did when it was set.
            // And one saved before a probe could sound another dimension names none, which is the
            // drive's own - as is one whose dimension no longer reads as an id at all.
            ResourceLocation dimension = tag.contains("Dimension")
                    ? ResourceLocation.tryParse(tag.getString("Dimension")) : null;
            return new WarpCourse(null,
                    new BlockPos(tag.getInt("FixX"), tag.getInt("FixY"), tag.getInt("FixZ")),
                    tag.contains("ArrivalHeight") ? tag.getInt("ArrivalHeight") : ArrivalHeight.UNSET,
                    label, hold,
                    dimension == null ? null : ResourceKey.create(Registries.DIMENSION, dimension));
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
            buf.writeBoolean(course.dimension != null);
            if (course.dimension != null) {
                buf.writeResourceKey(course.dimension);
            }
        }
        buf.writeUtf(course.label, 64);
        buf.writeBoolean(course.holdAltitude);
    }

    private static WarpCourse decode(RegistryFriendlyByteBuf buf) {
        boolean anchor = buf.readBoolean();
        UUID anchorId = anchor ? buf.readUUID() : null;
        BlockPos fix = anchor ? null : buf.readBlockPos();
        int height = anchor ? ArrivalHeight.UNSET : buf.readVarInt() - 1;
        ResourceKey<Level> dimension = !anchor && buf.readBoolean()
                ? buf.readResourceKey(Registries.DIMENSION) : null;
        String label = buf.readUtf(64);
        return new WarpCourse(anchorId, fix, height, label, buf.readBoolean(), dimension);
    }
}
