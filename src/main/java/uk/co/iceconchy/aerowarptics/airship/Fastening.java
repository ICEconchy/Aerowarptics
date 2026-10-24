package uk.co.iceconchy.aerowarptics.airship;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * One thing tying a vessel to something a warp would not take with it.
 *
 * <p>A player jumped with a Docking Connector locked to a connector on the ground. The connector holds
 * the hull with a Sable joint to the static world, anchored at a fixed world point, so the drive could
 * not move the ship at all: it sat still through the whole run at the rift and was then teleported, and
 * when the connector was switched off the joint - still stretched across the whole jump - dragged the
 * ship straight back. Simulated has half a dozen ways to make a joint like that, and Create's Sticker a
 * seventh, and none of them tells anything else it exists. {@link Fastenings} looks for each by name;
 * this is what it found, and the rule for whether it stops a warp.
 *
 * <p>Pure, so the rule is testable without a game. {@link #where} is a <em>world</em> position - the
 * thing a pilot is told about is somewhere on their deck, and a plot coordinate would send them to the
 * other side of the map to look for it.
 *
 * @param where the world position of the block doing the tying, or {@code null} when there is none
 */
public record Fastening(Kind kind, End end, @Nullable BlockPos where) {

    /** What is doing the tying. Appended only: these cross the wire by ordinal. */
    public enum Kind implements StringRepresentable {
        /** Simulated's Docking Connector, locking or locked to a partner: a fixed joint. */
        DOCKING_CONNECTOR("docking_connector"),
        /** A rope from a Rope Connector or Rope Winch. */
        ROPE("rope"),
        /** Create's Sticker, which Sable joins to what it has stuck to with a fixed joint. */
        STICKER("sticker"),
        /** A Swivel Bearing: the ship is the plate of a bearing it is not carrying. */
        SWIVEL_BEARING("swivel_bearing"),
        /** Locked in place with a Creative Physics Staff. */
        PHYSICS_STAFF("physics_staff"),
        /** A pair of plungers from a Plunger Launcher. */
        PLUNGER("plunger"),
        /** A pair of Springs. */
        SPRING("spring"),
        /** Merging Glue part-way through joining two sub-levels into one. */
        MERGING_GLUE("merging_glue"),
        /**
         * Sable's physics is paused for the whole level - {@code /sable} - so nothing flies. Not a joint,
         * but it stops a hull in exactly the same way, and it is said in its own sentence.
         */
        PHYSICS_PAUSED("physics_paused");

        private final String name;

        Kind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /** The thing's name, as a player would call it. */
        public String translationKey() {
            return "warp.fastening.kind." + name;
        }

        public static Kind byIndex(int index) {
            Kind[] values = values();
            if (index < 0 || index >= values.length) {
                throw new IllegalArgumentException("no fastening kind " + index);
            }
            return values[index];
        }
    }

    /** What the far end of it is fastened to. Appended only: these cross the wire by ordinal. */
    public enum End implements StringRepresentable {
        /** The world itself. Nothing moves the ground, so this always holds the ship. */
        GROUND("ground"),
        /** A sub-level that is not part of this vessel, so the warp would leave it behind. */
        OTHER_SHIP("other_ship"),
        /** Another part of the same vessel, which the warp carries along. */
        CARRIED("carried");

        private final String name;

        End(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /** The sentence that says it, with the thing's name as its one argument. */
        public String translationKey() {
            return "warp.fastening.end." + name;
        }

        public static End byIndex(int index) {
            End[] values = values();
            if (index < 0 || index >= values.length) {
                throw new IllegalArgumentException("no fastening end " + index);
            }
            return values[index];
        }
    }

    /** Whether this one stops a warp. */
    public boolean holds() {
        return holds(kind, end);
    }

    /**
     * Whether a fastening of this kind, fastened to this, stops a warp.
     *
     * <p>Anything fastened to the ground holds: the drive cannot fly the hull away from it, and a teleport
     * leaves the joint stretched across the jump. Anything fastened to another ship holds for the second of
     * those reasons - that ship is not part of this vessel, so it stays where it is.
     *
     * <p>A fastening between two parts of the same vessel is fine for everything that joins them at points
     * in their plots, because {@link Airship#relocate} moves every part by the same rigid jump and every
     * such point keeps its place. A rope's middle is not anchored like that - it is a string of points
     * simulated in world space - but {@link Airship#relocate} lifts every strand whose ends are all aboard
     * out of the physics world and puts it back on the far side, so a rope aboard is carried too. Merging
     * Glue is the one that is not: two sub-levels part-way through becoming one, which is a few ticks' wait
     * rather than something to fly through.
     */
    public static boolean holds(Kind kind, End end) {
        if (kind == Kind.PHYSICS_PAUSED) {
            return true;
        }
        return switch (end) {
            case GROUND, OTHER_SHIP -> true;
            case CARRIED -> kind == Kind.MERGING_GLUE;
        };
    }

    /**
     * The one to name, of everything found, or empty when nothing found holds.
     *
     * <p>A pilot is told one thing on the action bar, so it is the one most likely to be what they need to
     * undo: a paused world before anything else, since nothing on the deck will help; then a tie to the
     * ground; then one to another ship; then glue still setting aboard. Ties of equal
     * weight are named in the order they were found, which is stable for a given ship.
     */
    public static Optional<Fastening> worst(List<Fastening> found) {
        return found.stream()
                .filter(Fastening::holds)
                .min(Comparator.comparingInt(Fastening::rank));
    }

    private int rank() {
        if (kind == Kind.PHYSICS_PAUSED) {
            return 0;
        }
        return 1 + end.ordinal();
    }

    /** For the trace and the dry run: {@code docking_connector at (120, 64, -30) -> ground}. */
    @Override
    public String toString() {
        if (kind == Kind.PHYSICS_PAUSED) {
            return kind.getSerializedName();
        }
        return kind.getSerializedName()
                + (where == null ? "" : " at (" + where.getX() + ", " + where.getY() + ", " + where.getZ() + ")")
                + " -> " + end.getSerializedName() + (holds() ? "" : " (carried)");
    }

    /**
     * On the wire: kind and end by ordinal, then the position if there is one. A bad ordinal is refused
     * rather than read as some other thing - a decode must not turn a malformed packet into a message
     * naming the wrong block on somebody's deck.
     */
    public static final StreamCodec<ByteBuf, Fastening> STREAM_CODEC = StreamCodec.of(
            (buf, fastening) -> {
                ByteBufCodecs.VAR_INT.encode(buf, fastening.kind.ordinal());
                ByteBufCodecs.VAR_INT.encode(buf, fastening.end.ordinal());
                buf.writeBoolean(fastening.where != null);
                if (fastening.where != null) {
                    buf.writeLong(fastening.where.asLong());
                }
            },
            buf -> {
                Kind kind = Kind.byIndex(ByteBufCodecs.VAR_INT.decode(buf));
                End end = End.byIndex(ByteBufCodecs.VAR_INT.decode(buf));
                BlockPos where = buf.readBoolean() ? BlockPos.of(buf.readLong()) : null;
                return new Fastening(kind, end, where);
            });

    /** An optional one, which is how every packet carries it. */
    public static final StreamCodec<ByteBuf, Optional<Fastening>> OPTIONAL_STREAM_CODEC =
            ByteBufCodecs.optional(STREAM_CODEC);
}
