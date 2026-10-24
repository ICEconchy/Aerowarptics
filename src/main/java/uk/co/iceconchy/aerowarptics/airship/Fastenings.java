package uk.co.iceconchy.aerowarptics.airship;

import com.simibubi.create.content.contraptions.chassis.StickerBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.compat.simulated.SimulatedTies;
import uk.co.iceconchy.aerowarptics.trace.Trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything tying a vessel to something a warp would not take with it - see {@link Fastening}.
 *
 * <p>There is no way to ask Sable what is joined to a sub-level: a joint's handle lives only in whatever
 * made it, and the physics engine has no call that lists them. So each thing that makes one is asked
 * about itself, by name - Simulated's machines in {@link SimulatedTies}, and here Create's Sticker, which
 * Sable joins from a mixin of its own, and the one switch that stops everything at once.
 *
 * <p>A vessel is the hull and its {@link Airship#parts() parts}, and every part is searched as well as the
 * hull: a shuttle docked alongside that is itself tied to the pier holds the ship it is docked to exactly
 * as firmly as a line from the hull would.
 *
 * <p>Read only from what is loaded. The scan for Stickers stuck to the ship from outside it looks only at
 * chunks already in memory, and never loads one - a chunk that is not loaded has no block entities ticking
 * in it and so nothing joined through it.
 */
public final class Fastenings {

    /** How far past the vessel's world box a Sticker stuck to it from outside could be standing. */
    private static final double STICKER_REACH = 2.0D;

    /** Probes that have failed against a changed dependency and are off until the game restarts. */
    private static final Set<String> BROKEN = ConcurrentHashMap.newKeySet();

    private Fastenings() {
    }

    /** Everything found on this ship's vessel, holding or not - the dry run lists the lot. */
    public static List<Fastening> of(Airship airship) {
        Vessel vessel = Vessel.of(airship);
        List<Fastening> found = new ArrayList<>();
        SubLevelPhysicsSystem physics = airship.physics();
        if (physics != null && physics.getPaused()) {
            found.add(new Fastening(Fastening.Kind.PHYSICS_PAUSED, Fastening.End.GROUND, null));
        }
        probe("sticker", () -> stickers(vessel, found));
        SimulatedTies.collect(vessel, found);
        return found;
    }

    /** The one that stops a warp, if anything does. */
    public static Optional<Fastening> holding(Airship airship) {
        return Fastening.worst(of(airship));
    }

    /**
     * Runs one probe, and switches it off for the session if a dependency no longer has what it calls.
     *
     * <p>Create and Simulated are pinned, but a pack that updates one of them ahead of this mod would
     * otherwise meet a {@link LinkageError} in the middle of a server tick - every time anybody pulled a
     * lever. One probe gone quiet is a missed warning; the whole check gone is a crash.
     */
    public static void probe(String name, Runnable probe) {
        if (BROKEN.contains(name)) {
            return;
        }
        try {
            probe.run();
        } catch (LinkageError e) {
            BROKEN.add(name);
            Trace.warn(Trace.LAUNCH, "the {} check is off for this session - its mod no longer matches: {}",
                    name, e.toString());
        }
    }

    // ------------------------------------------------------------------ Create's Sticker

    /**
     * Stickers, both ways round.
     *
     * <p>Sable's mixin gives a Sticker a fixed joint to whatever block it has stuck to, and saves which
     * block that is. A Sticker aboard is found among the vessel's machines. One stuck to the vessel from
     * outside - on the pier, or on another ship moored alongside - is only found by looking where it would
     * have to stand, just outside the vessel's box.
     */
    private static void stickers(Vessel vessel, List<Fastening> into) {
        for (Airship member : vessel.members()) {
            for (StickerBlockEntity sticker : member.machines(StickerBlockEntity.class)) {
                BlockPos attached = stuckTo(vessel.level(), sticker);
                if (attached != null) {
                    into.add(new Fastening(Fastening.Kind.STICKER, vessel.endAt(attached),
                            vessel.world(member, sticker.getBlockPos())));
                }
            }
        }

        BoundingBox3d reach = new BoundingBox3d(vessel.worldBounds()).expand(STICKER_REACH);
        // On the ground: the world's own chunks under the vessel.
        ServerLevel level = vessel.level();
        int minX = (int) Math.floor(reach.minX()) >> 4;
        int maxX = (int) Math.floor(reach.maxX()) >> 4;
        int minZ = (int) Math.floor(reach.minZ()) >> 4;
        int maxZ = (int) Math.floor(reach.maxZ()) >> 4;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk != null) {
                    stuckFromOutside(vessel, chunk.getBlockEntities().values(), null, into);
                }
            }
        }
        // On another ship alongside.
        for (SubLevel sub : Sable.HELPER.getAllIntersecting(level, reach)) {
            if (sub instanceof ServerSubLevel server && !server.isRemoved() && !vessel.contains(server)) {
                Airship other = Airship.of(server);
                stuckFromOutside(vessel, other.machines(StickerBlockEntity.class), other, into);
            }
        }
    }

    private static void stuckFromOutside(Vessel vessel, Iterable<? extends BlockEntity> candidates,
                                         @Nullable Airship on, List<Fastening> into) {
        for (BlockEntity blockEntity : candidates) {
            if (!(blockEntity instanceof StickerBlockEntity sticker) || sticker.isRemoved()) {
                continue;
            }
            BlockPos attached = stuckTo(vessel.level(), sticker);
            if (attached == null || vessel.endAt(attached) != Fastening.End.CARRIED) {
                continue;
            }
            into.add(new Fastening(Fastening.Kind.STICKER,
                    on == null ? Fastening.End.GROUND : Fastening.End.OTHER_SHIP,
                    on == null ? sticker.getBlockPos() : vessel.world(on, sticker.getBlockPos())));
        }
    }

    /**
     * The block a Sticker is joined to, or {@code null} when it has no joint.
     *
     * <p>Sable keeps the joint in fields its mixin adds and gives nothing to read them with, but it saves
     * them: a {@code SableConstraint} compound, present only while the joint is, holding the stuck block's
     * position as {@code X}, {@code Y} and {@code Z}.
     */
    @Nullable
    private static BlockPos stuckTo(ServerLevel level, StickerBlockEntity sticker) {
        CompoundTag saved = sticker.saveWithoutMetadata(level.registryAccess());
        if (!saved.contains("SableConstraint")) {
            return null;
        }
        CompoundTag joint = saved.getCompound("SableConstraint");
        return new BlockPos(joint.getInt("X"), joint.getInt("Y"), joint.getInt("Z"));
    }

    // ------------------------------------------------------------------ the vessel

    /**
     * The hull and every part a warp will carry with it, and the questions every probe asks about them:
     * whether a sub-level is one of them, what the far end of a tie is, and where on the map a block aboard
     * one of them is.
     */
    public static final class Vessel {

        private final ServerLevel level;
        private final List<Airship> members;
        private final Map<UUID, Airship> byId;
        private final Set<SubLevel> subLevels;

        private Vessel(ServerLevel level, List<Airship> members) {
            this.level = level;
            this.members = members;
            this.byId = new HashMap<>();
            this.subLevels = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            for (Airship member : members) {
                byId.put(member.uuid(), member);
                subLevels.add(member.subLevel());
            }
        }

        public static Vessel of(Airship airship) {
            return new Vessel(airship.level(), List.copyOf(airship.vessel()));
        }

        public ServerLevel level() {
            return level;
        }

        /** The hull first, then its parts. */
        public List<Airship> members() {
            return members;
        }

        public boolean contains(SubLevel subLevel) {
            return subLevels.contains(subLevel);
        }

        public boolean contains(@Nullable UUID subLevel) {
            return subLevel != null && byId.containsKey(subLevel);
        }

        /** The member with this id, or {@code null} when it is not one. */
        @Nullable
        public Airship member(@Nullable UUID subLevel) {
            return subLevel == null ? null : byId.get(subLevel);
        }

        /** What a tie whose far end is on this sub-level - {@code null} for the world - is fastened to. */
        public Fastening.End endOf(@Nullable UUID subLevel) {
            if (subLevel == null) {
                return Fastening.End.GROUND;
            }
            return byId.containsKey(subLevel) ? Fastening.End.CARRIED : Fastening.End.OTHER_SHIP;
        }

        /** What a tie whose far end is at this block - a plot position or a world one - is fastened to. */
        public Fastening.End endAt(BlockPos pos) {
            SubLevel containing = Sable.HELPER.getContaining(level, pos);
            return containing == null ? Fastening.End.GROUND : endOf(containing.getUniqueId());
        }

        /** Where a block aboard a sub-level is on the map. */
        public BlockPos world(Airship on, BlockPos plot) {
            return BlockPos.containing(on.toWorld(Vec3.atCenterOf(plot)));
        }

        /** Every member's world box, together. */
        public BoundingBox3dc worldBounds() {
            BoundingBox3d box = new BoundingBox3d(members.getFirst().worldBounds());
            for (Airship member : members) {
                box.expandTo(member.worldBounds());
            }
            return box;
        }
    }
}
