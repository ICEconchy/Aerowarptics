package uk.co.iceconchy.aerowarptics.hold;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3dc;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.warp.ArrivalTicket;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * <strong>The only code in this mod that makes the game load a chunk.</strong>
 *
 * <p>Everything else asks here, which is what lets the rules that keep a world safe be kept in one place
 * rather than remembered at every call site ({@code ChunkLoadingChokePointTest} holds the rest of the
 * codebase to that):
 *
 * <ul>
 *   <li><strong>Never plot space.</strong> A ticket at a ship's plot coordinates is what hung "Saving
 *   worlds" and left empty {@code r.40001} region files - see {@link AirshipResidency}. Ground asked for
 *   in plot space is refused, and traced, never silently dropped: a machine aboard a ship that wants its
 *   far end loaded is reaching for a <em>ship</em>, and {@link #reachShip} is how.</li>
 *   <li><strong>A ship is held with Sable's own ticket</strong> ({@link #holdShip}), never by forcing the
 *   ground under it. Sable keeps a ship only while every column under its box is in block-ticking range,
 *   and its own ticket holds exactly that, wherever the ship goes - through a warp's teleport included.
 *   Forcing ground a ship was over is what lost ships at the far end of a warp.</li>
 *   <li><strong>Every ship hold is on a lease</strong> in {@link HoldLedger}, renewed by its owner and let
 *   go by {@link HoldUpkeep} when it is not. Sable saves its tickets with the world, so a hold nobody
 *   renews would otherwise keep its ship loaded forever.</li>
 *   <li><strong>Everything else is an expiring region ticket.</strong> A preview, a reach, a chute's
 *   partner, a gate's far end: each is asked for by something happening now, is renewed while it goes on
 *   happening, and lapses by itself when it stops. Nothing to release, so nothing to leak - and none of
 *   it survives a restart, because nothing that asked for it does either.</li>
 *   <li><strong>No bulk synchronous loads.</strong> A ticket asks for chunks to load in the background;
 *   whoever needs them waits until they are there ({@code getChunkNow}) rather than making the server
 *   produce them in one tick.</li>
 * </ul>
 *
 * <p>Nothing here holds anything on its own initiative. A chunk is loaded because a pilot is warping, a
 * player is looking at a map, a beacon has been used, items are flowing through a chute, or a gate is
 * connected - and for no longer than that goes on.
 */
public final class Holds {

    /**
     * This mod's Sable force-load ticket. Created when the mod is constructed, because Sable looks saved
     * tickets up by name as each level loads, and one whose type is not registered by then is dropped.
     *
     * <p>That drop is also why removing the mod cannot break a world: Sable logs a ticket of a type it does
     * not know and skips it, and the ship it held loads like any other.
     */
    public static final SubLevelLoadingTicketType<HoldKey> SHIP_TICKET =
            SubLevelLoadingTicketType.create(AeroWarptics.id("hold"), HoldKey.CODEC);

    /** How long a reach for an unloaded ship holds its ground before it has to be asked for again. */
    public static final int REACH_LIFESPAN = 300;

    /** How often something still reaching for a ship asks again. Comfortably inside the lifespan. */
    public static final int REACH_RENEW_INTERVAL = 100;

    private static final TicketType<ChunkPos> REACH =
            TicketType.create("aerowarptics:reach", Comparator.comparingLong(ChunkPos::toLong), REACH_LIFESPAN);

    /** Sable reads the columns under a ship's box grown by a block; this reaches a block past that. */
    private static final double REACH_MARGIN_BLOCKS = 2.0D;

    /** Set once, if the installed Sable's ticket API is not the one this was built against. */
    private static volatile boolean incompatible;

    private Holds() {
    }

    /**
     * Stands ship holds down for the rest of the session, and says why, once.
     *
     * <p>Sable moves quickly and its ticket API has changed shape before (2.0.5 renamed the ticket's getters).
     * A call that no longer links throws on the server thread, inside a drive's tick or the upkeep, and a
     * server that throws there stops. Warps, gates and chutes all work without ship holds - as they did
     * before holds existed - so losing them is the right price for a mismatch, not the whole world.
     */
    static void incompatible(String doing, LinkageError error) {
        if (!incompatible) {
            incompatible = true;
            AeroWarptics.LOGGER.error("Sable's sub-level ticket API is not the one this version of AeroWarptics was"
                    + " built for (failed {}). Ships will not be held loaded until AeroWarptics is updated for this"
                    + " Sable; everything else carries on.", doing, error);
        }
    }

    /** Whether ship holds have been stood down for a Sable this version cannot talk to. */
    static boolean shipHoldsUnavailable() {
        return incompatible;
    }

    /** Called from the mod's constructor so {@link #SHIP_TICKET} is registered before any level loads. */
    public static void bootstrap() {
        AeroWarptics.LOGGER.debug("Registered the {} sub-level ticket", SHIP_TICKET.name());
    }

    // ------------------------------------------------------------------ ground

    /**
     * Places one expiring region ticket, unless it is in plot space.
     *
     * @return {@code false} when refused because the centre is aboard a ship
     */
    public static boolean ground(ServerLevel level, TicketType<ChunkPos> type, ChunkPos centre, int radius) {
        if (AirshipResidency.plotSpace(level).test(centre.toLong())) {
            if (Trace.enabled()) {
                Trace.log(Trace.HOLD, "refused a {} ticket at {} in {}: that is inside a ship's plot, where a ticket"
                        + " is what once stopped worlds saving - the ship has to be reached instead", type,
                        centre, level.dimension().location());
            }
            return false;
        }
        level.getChunkSource().addRegionTicket(type, centre, radius, centre);
        return true;
    }

    /**
     * Holds the chunk a block stands in for {@link #REACH_LIFESPAN} ticks more, on the expiring reach ticket.
     *
     * <p>For a Collapse Cradle's ritual, which renews it while it runs and simply lets it lapse when it ends,
     * pauses or is broken - nothing to release, so nothing to forget to.
     *
     * @return {@code false} when refused because the block is aboard a ship
     */
    public static boolean holdGround(ServerLevel level, BlockPos pos) {
        return ground(level, REACH, new ChunkPos(pos), 1);
    }

    /**
     * {@link #holdGround(ServerLevel, BlockPos)}, out to a number of chunks round the block.
     *
     * <p>For a Rift Wellhead's meltdown, which takes ground several chunks from where it stands: held by the one
     * chunk, a large crater simply skipped everything past it. Renewed while the meltdown runs and left to lapse
     * when it ends, as the cradle's is.
     */
    public static boolean holdGround(ServerLevel level, BlockPos pos, int chunkRadius) {
        return ground(level, REACH, new ChunkPos(pos), Math.max(1, Math.min(8, chunkRadius)));
    }

    /**
     * Places a ticket for each square, as many as fit the arrival chunk budget, ends first.
     *
     * @return how many distinct chunks are now held
     */
    public static int groundSquares(ServerLevel level, TicketType<ChunkPos> type, List<ArrivalTicket.Square> squares) {
        List<ArrivalTicket.Square> held = ArrivalTicket.withinBudget(squares, ArrivalTicket.maxChunks());
        for (ArrivalTicket.Square square : held) {
            ground(level, type, new ChunkPos(square.chunkX(), square.chunkZ()), square.radius());
        }
        return ArrivalTicket.chunksOf(held).size();
    }

    /** Whether a block position is inside a ship's plot rather than out in the world. */
    public static boolean inPlot(ServerLevel level, BlockPos pos) {
        return AirshipResidency.plotSpace(level).test(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
    }

    // ------------------------------------------------------------------- ships

    /**
     * Holds a ship loaded, or renews the hold, for {@code leaseTicks} from now.
     *
     * <p>Sable's call is made every time, not only the first: it does nothing to a ticket already there,
     * and it means a ticket taken away behind this mod's back - an operator's {@code /sable} command - comes
     * back at the next renewal instead of leaving a ledger that believes in a hold that is not there.
     *
     * @param reanchor whether to count any further wandering from where the ship is now (see
     *                 {@link HoldRules#residency})
     * @return {@code false} when the ship is not there to hold
     */
    public static boolean holdShip(Airship airship, HoldPurpose purpose, UUID owner, int leaseTicks, boolean reanchor) {
        if (incompatible || !airship.isActive()) {
            return false;
        }
        ServerLevel level = airship.level();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return false;
        }
        MinecraftServer server = level.getServer();
        long now = server.overworld().getGameTime();
        HoldLedger ledger = HoldLedger.get(server);
        HoldKey key = new HoldKey(owner, purpose);
        HoldLedger.ShipHold existing = ledger.hold(key, airship.uuid());
        boolean added;
        try {
            added = container.addForceLoadTicket(airship.subLevel(), SHIP_TICKET, key);
        } catch (LinkageError mismatch) {
            incompatible("adding a ticket", mismatch);
            return false;
        }

        Vector3dc at = airship.position();
        boolean moveOrigin = existing == null
                || reanchor && HoldRules.roamed(existing.originX(), existing.originZ(), at.x(), at.z()) > 1.0D;
        if (moveOrigin || added || HoldRules.dueForRenewal(existing.leaseUntil(), now, leaseTicks)) {
            ledger.putHold(new HoldLedger.ShipHold(key, airship.uuid(), level.dimension(),
                    HoldRules.leaseUntil(now, leaseTicks),
                    moveOrigin ? at.x() : existing.originX(), moveOrigin ? at.z() : existing.originZ()));
        }
        noteShip(airship);
        if (existing == null || added) {
            Trace.log(Trace.HOLD, "{} holds ship {} ({}) for {} at {} in {}, lease {}t{}", owner, airship.uuid(),
                    shipName(airship), purpose.id(), Trace.vec(at), level.dimension().location(), leaseTicks,
                    existing != null ? " - its Sable ticket had gone and was put back" : "");
        }
        return true;
    }

    /**
     * Lets go of every ship {@code owner} holds for {@code purpose}, or only {@code ship}.
     *
     * <p>A ship that is not loaded cannot have its ticket taken off - Sable's call needs the ship - but a
     * ship with this mod's ticket is by definition loaded, so that only happens to one that failed to load.
     * Its ledger entry goes anyway, and {@link HoldUpkeep}'s sweep takes the orphaned ticket off whenever
     * the ship does turn up.
     */
    public static void releaseShip(MinecraftServer server, UUID owner, HoldPurpose purpose, @Nullable UUID ship) {
        HoldLedger ledger = HoldLedger.get(server);
        HoldKey key = new HoldKey(owner, purpose);
        for (HoldLedger.ShipHold hold : ledger.holdsBy(key)) {
            if (ship != null && !ship.equals(hold.ship())) {
                continue;
            }
            boolean dropped = dropTicket(server, hold);
            ledger.removeHold(key, hold.ship());
            Trace.log(Trace.HOLD, "{} lets go of ship {} ({}){}", owner, hold.ship(), purpose.id(),
                    dropped ? "" : " - the ship is not loaded; its ticket goes when it next is");
        }
    }

    /** Whether {@code owner} is holding {@code ship} for {@code purpose}. */
    @Nullable
    public static HoldLedger.ShipHold heldBy(MinecraftServer server, UUID owner, HoldPurpose purpose, UUID ship) {
        return HoldLedger.get(server).hold(new HoldKey(owner, purpose), ship);
    }

    /** Takes a hold's ticket off its ship, if the ship is there. */
    static boolean dropTicket(MinecraftServer server, HoldLedger.ShipHold hold) {
        ServerLevel level = server.getLevel(hold.dimension());
        ServerSubLevel ship = level == null ? null : present(level, hold.ship());
        if (ship == null) {
            return false;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || incompatible) {
            return false;
        }
        try {
            return container.removeForceLoadTicket(ship, SHIP_TICKET, hold.key());
        } catch (LinkageError mismatch) {
            incompatible("removing a ticket", mismatch);
            return false;
        }
    }

    /** A ship, if it is loaded in this level right now. */
    @Nullable
    public static ServerSubLevel present(ServerLevel level, UUID ship) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevel found = container == null ? null : container.getSubLevel(ship);
        return found instanceof ServerSubLevel server && !server.isRemoved() ? server : null;
    }

    // ----------------------------------------------------------------- locator

    /**
     * Notes where a ship is, so it can be found again once it has unloaded.
     *
     * <p>Cheap to call often: the ledger is only marked for saving when the ship has moved or changed.
     */
    public static void noteShip(Airship airship) {
        if (!airship.isActive()) {
            return;
        }
        MinecraftServer server = airship.level().getServer();
        HoldLedger ledger = HoldLedger.get(server);
        ledger.noteShip(record(airship.subLevel(), ledger.ship(airship.uuid()), server.overworld().getGameTime()));
    }

    /**
     * What to remember about a ship now. The plot range is carried over from {@code previous} when the
     * plot is already being torn down, as it is while Sable unloads a ship.
     */
    static HoldLedger.ShipRecord record(ServerSubLevel ship, @Nullable HoldLedger.ShipRecord previous, long now) {
        Airship airship = Airship.of(ship);
        ChunkPos plotMin = ship.getPlot() == null ? null : airship.plotChunkMin();
        ChunkPos plotMax = ship.getPlot() == null ? null : airship.plotChunkMax();
        long min = plotMin != null ? plotMin.toLong() : previous != null ? previous.plotMin() : 0L;
        long max = plotMax != null ? plotMax.toLong() : previous != null ? previous.plotMax() : 0L;
        BoundingBox3dc box = ship.boundingBox();
        String name = ship.getName() == null ? "" : ship.getName();
        ResourceKey<Level> dimension = ship.getLevel().dimension();
        return new HoldLedger.ShipRecord(ship.getUniqueId(), dimension, min, max,
                box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ(), name, now);
    }

    /** The ship a plot position is aboard, loaded or not, if that ship was ever noted. */
    @Nullable
    public static HoldLedger.ShipRecord shipAt(ServerLevel level, BlockPos plotPos) {
        return HoldLedger.get(level.getServer()).shipAt(level.dimension(), plotPos);
    }

    // ------------------------------------------------------------------- reach

    /** Where a ship stands when something reaches for it. */
    public enum Reach {
        /** Loaded. Nothing was held; go ahead. */
        PRESENT,
        /** Not loaded; the ground it was last seen over is now loading. Ask again shortly. */
        REACHING,
        /** Not loaded, and never noted anywhere - there is nowhere to look. */
        UNKNOWN
    }

    /**
     * Reaches for whatever ship a plot position is aboard.
     *
     * @param keepHeld see {@link #reachShip}
     */
    public static Reach reachShipAt(ServerLevel level, BlockPos plotPos, String why, boolean keepHeld) {
        ServerSubLevel loaded = containing(level, plotPos);
        if (loaded != null) {
            if (keepHeld) {
                Airship airship = Airship.of(loaded);
                noteShip(airship);
                holdUnder(level, Holds.record(loaded, null, 0L));
            }
            return Reach.PRESENT;
        }
        HoldLedger.ShipRecord record = shipAt(level, plotPos);
        if (record == null) {
            Trace.changed("reach:" + level.dimension().location() + "@" + plotPos.asLong(), Trace.HOLD,
                    "{} reached for the ship at {} in {}, but no ship there was ever noted", why,
                    Trace.pos(plotPos), level.dimension().location());
            return Reach.UNKNOWN;
        }
        return reachShip(level, record, why, keepHeld);
    }

    /** The loaded ship a plot position is aboard, or {@code null}. */
    @Nullable
    private static ServerSubLevel containing(ServerLevel level, BlockPos plotPos) {
        Airship airship = Airship.containing(level, plotPos);
        return airship == null || !airship.isActive() ? null : airship.subLevel();
    }

    /**
     * Loads the ground a ship was last seen over, so Sable brings it back.
     *
     * <p>Sable files an unloaded ship under a ground chunk inside its box and rebuilds it once every column
     * under the box is in block-ticking range again. So the squares laid here make the whole box - and a
     * block round it - block-ticking, not merely loaded: an ordinary region ticket's outer ring is not
     * ({@link HoldRules#blockTicking}). Nothing is held for longer than {@link #REACH_LIFESPAN} unless it is
     * asked for again.
     *
     * @param keepHeld whether a ship that is already there should go on being held for as long as this is
     *                 asked again - a chute's partner or a gate's far end, which are in use for as long as
     *                 items flow or the gate stays connected. Without it, a ship brought in by the reach
     *                 would be let go the moment it arrived, and unload again as the reach lapsed. A beacon
     *                 does not want it: once the ship is there, its own warp holds it.
     */
    public static Reach reachShip(ServerLevel level, HoldLedger.ShipRecord record, String why, boolean keepHeld) {
        ServerSubLevel loaded = present(level, record.ship());
        if (loaded != null) {
            if (keepHeld) {
                noteShip(Airship.of(loaded));
                holdUnder(level, record(loaded, record, 0L));
            }
            return Reach.PRESENT;
        }
        int held = holdUnder(level, record);
        Trace.changed("reach:" + record.ship(), Trace.HOLD,
                "{} reaching for ship {} ('{}') in {}: holding {} chunk(s) round where it was last seen, ({}, {}) to ({}, {})",
                why, record.ship(), record.name(), level.dimension().location(), held,
                Math.round(record.minX()), Math.round(record.minZ()), Math.round(record.maxX()), Math.round(record.maxZ()));
        return Reach.REACHING;
    }

    /**
     * Holds squares of ground with the same short-lived ticket a reach uses. For an operator bringing back
     * a ship lost before any of this existed - see {@code /aerowarptics holds reach}.
     *
     * @return how many distinct chunks are now held
     */
    public static int reachGround(ServerLevel level, List<ArrivalTicket.Square> squares) {
        return groundSquares(level, REACH, squares);
    }

    /** Makes the ground under a ship's box, and a block round it, block-ticking for a while. */
    private static int holdUnder(ServerLevel level, HoldLedger.ShipRecord record) {
        int[] rect = HoldRules.chunksUnder(record.minX(), record.minZ(), record.maxX(), record.maxZ(),
                REACH_MARGIN_BLOCKS);
        return groundSquares(level, REACH, ArrivalTicket.tickingTile(rect[0], rect[1], rect[2], rect[3]));
    }

    private static String shipName(Airship airship) {
        String name = airship.name();
        return name == null || name.isBlank() ? "unnamed" : name;
    }
}
