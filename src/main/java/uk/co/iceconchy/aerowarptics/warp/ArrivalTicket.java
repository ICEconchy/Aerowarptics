package uk.co.iceconchy.aerowarptics.warp;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.hold.HoldRules;
import uk.co.iceconchy.aerowarptics.hold.Holds;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Keeps the far end of a warp - and the corridor it is flown down - loaded while a ship is on its way.
 *
 * <p>An arrival lands a hull in a place nobody may have stood for a long time, and the chunks it lands
 * in have to come off disk. Left to itself that happens at the worst possible moment - during the
 * teleport - and shows up as a hitch just as the ship comes out of the aperture. Claiming the ground
 * when the warp is planned gives it the whole flight to load quietly instead.
 *
 * <p>The corridor is claimed for the same reason turned around: the departure path is proven clear at
 * the mooring ({@link LaunchClearance}), and that proof only stays true if a block cannot stream in or
 * update along the path during the several-second flight. Holding the corridor resident is what keeps
 * the proof honest until the hull is through.
 *
 * <p>Every ticket carries its own lifespan, so there is nothing to release and no way to leak a
 * permanently loaded region if a warp ends somewhere unexpected. That matters more than it sounds:
 * forced chunks that outlive their reason are a slow, invisible drain on a server.
 */
public final class ArrivalTicket {

    /**
     * Long enough to cover a whole warp with room to spare, short enough that a warp which dies
     * badly has let go of the ground within half a minute.
     */
    private static final int LIFESPAN_TICKS = 600;

    private static final TicketType<ChunkPos> WARP_ARRIVAL =
            TicketType.create("aerowarptics:warp_arrival", Comparator.comparingLong(ChunkPos::toLong),
                    LIFESPAN_TICKS);

    /**
     * The corridor the hull is flown down, held for the duration of a flight.
     *
     * <p>The hull leaves its mooring and flies forward in real world space for the whole corridor
     * run before the single teleport carries it across. That path is proven clear before the rift
     * opens; without holding it resident, a block updating or streaming in mid-flight would pop
     * through the hull the proof said had a clear run.
     */
    private static final TicketType<ChunkPos> WARP_CORRIDOR =
            TicketType.create("aerowarptics:warp_corridor", Comparator.comparingLong(ChunkPos::toLong),
                    LIFESPAN_TICKS);

    private ArrivalTicket() {
    }

    /**
     * The far end of a warp, claimed while the drive is still winding up.
     *
     * <p>The arrival search reads every block a hull could land in, and the ground it reads has to be
     * there. Left to the search, a large hull's landing came off disk - or out of the world generator
     * - synchronously, candidate by candidate, on the server thread: hundreds of chunks at the moment
     * the rift was meant to open, which a pilot sees as the server freezing. Claimed at the start of
     * spin-up instead, the same chunks load in the background while the drive winds, and the search
     * finds them already there. Renewed while the drive spins; lapses by itself if it never fires.
     */
    private static final TicketType<ChunkPos> WARP_PRELOAD =
            TicketType.create("aerowarptics:warp_preload", Comparator.comparingLong(ChunkPos::toLong),
                    LIFESPAN_TICKS);

    /** How often a spinning drive re-asserts its preload, comfortably inside the ticket's lifespan. */
    public static final int PRELOAD_RENEW_INTERVAL = 200;

    /**
     * The ground ahead of a cruising ship, and under it.
     *
     * <p>Short-lived, unlike the others. A cruise lays these every second along wherever it is now
     * heading, so a long one leaves a wake of them behind it - and a wake held for the others' half a
     * minute would keep thousands of chunks of country the ship has already left loaded behind a fast
     * ship. Five seconds covers a missed renewal and lets the wake go almost as soon as it is flown.
     */
    private static final TicketType<ChunkPos> WARP_CRUISE =
            TicketType.create("aerowarptics:warp_cruise", Comparator.comparingLong(ChunkPos::toLong), 100);

    /**
     * Blocks between the centres of consecutive squares laid along a run.
     *
     * <p>Two chunks. Closer and the squares are nearly all overlap; further and each square has to be
     * made wider to keep the gaps between them covered, which costs more chunks than it saves tickets.
     */
    private static final double SQUARE_SPACING = 32.0D;

    /** The most squares one run is ever cut into, so a pathological run cannot mint thousands of tickets. */
    private static final int MAX_SQUARES = 256;

    /**
     * The widest square a single region ticket can hold at full status.
     *
     * <p>A ticket's level is {@code 33 - radius}, and the levels run out below one. Past this a run
     * would need several rows of squares side by side; a hull reaching over five hundred blocks from
     * its own centre is the only thing that gets there.
     */
    public static final int MAX_RADIUS = 32;

    /**
     * One square of chunks held by a single region ticket.
     *
     * <p>A region ticket of distance {@code radius} brings every chunk within that many chunks of its
     * centre - on either axis - to full status, so the square is {@code 2 x radius + 1} chunks a side.
     *
     * @param chunkX the centre chunk's X
     * @param chunkZ the centre chunk's Z
     * @param radius chunks either side of the centre, inclusive
     */
    public record Square(int chunkX, int chunkZ, int radius) {

        /** Whether a block column falls inside the square. */
        public boolean containsBlock(double blockX, double blockZ) {
            int x = (int) Math.floor(blockX) >> 4;
            int z = (int) Math.floor(blockZ) >> 4;
            return Math.abs(x - chunkX) <= radius && Math.abs(z - chunkZ) <= radius;
        }
    }

    /**
     * Squares of chunks that together cover a run and everything within {@code halfWidth} of it.
     *
     * <p>A single square centred on the run - which is what the arrival ticket used to be - has to be
     * as wide as the run is long, and was capped long before it got there. A four-hundred-block hull
     * comes out of the far aperture more than five hundred blocks behind where it comes to rest, well
     * outside the old eight-chunk square, so the ground under it at the moment of the crossing was held
     * by nothing: Sable let the hull go, the drive was read back in mid-flight, and the warp ended
     * {@code INTERRUPTED}. A chain of squares follows the run instead, and costs chunks in proportion
     * to its length rather than to its length squared.
     *
     * <p><strong>The ends come first.</strong> The returned list starts with the square at {@code from}
     * and then the one at {@code to}, and only then the ones between. A caller that has to stop early
     * to stay under a chunk budget therefore always holds the two places the hull is actually put down
     * - where it reappears and where it comes to rest - and gives up the middle of the coast between
     * them first.
     *
     * <p>Coverage is exact, not approximate, up to {@link #MAX_RADIUS}. The run is stretched by
     * {@code overhang} past both ends, because a hull put down at an end still reaches half its own
     * length beyond it. Every point within {@code halfWidth} of that stretched run lies within
     * {@code halfWidth + spacing / 2} of some square's sample point, and each square's radius is chosen
     * to reach that far from any block in its centre chunk.
     *
     * @param halfWidth how far either side of the run has to be held, in blocks
     * @param overhang  how far past each end of the run has to be held, in blocks
     */
    public static List<Square> centresAlong(double fromX, double fromZ, double toX, double toZ,
                                            double halfWidth, double overhang) {
        double width = Math.max(0.0D, halfWidth);
        double length = Math.hypot(toX - fromX, toZ - fromZ);
        // Along the run, unit length; any horizontal will do when the run has no length at all, since
        // the overhang then stretches a point into a short line either way.
        double dirX = length > 1.0e-9D ? (toX - fromX) / length : 1.0D;
        double dirZ = length > 1.0e-9D ? (toZ - fromZ) / length : 0.0D;
        double extra = Math.max(0.0D, overhang);
        double startX = fromX - dirX * extra;
        double startZ = fromZ - dirZ * extra;
        double total = length + 2.0D * extra;

        int intervals = (int) Math.min(MAX_SQUARES - 1, Math.ceil(total / SQUARE_SPACING));
        double spacing = intervals == 0 ? 0.0D : total / intervals;
        int radius = Mth.clamp((int) Math.ceil((width + spacing * 0.5D) / 16.0D), 0, MAX_RADIUS);

        // The ends first, so a budget trims the coast before it trims either place the hull is set down.
        Square from = squareAt(fromX, fromZ, radius);
        Square to = squareAt(toX, toZ, radius);
        List<Square> squares = new ArrayList<>(intervals + 3);
        squares.add(from);
        if (!to.equals(from)) {
            squares.add(to);
        }
        for (int i = 0; i <= intervals; i++) {
            double t = intervals == 0 ? 0.0D : (double) i / intervals;
            Square sample = squareAt(startX + dirX * total * t, startZ + dirZ * total * t, radius);
            if (!sample.equals(from) && !sample.equals(to)) {
                squares.add(sample);
            }
        }
        return squares;
    }

    private static Square squareAt(double blockX, double blockZ, int radius) {
        return new Square((int) Math.floor(blockX) >> 4, (int) Math.floor(blockZ) >> 4, radius);
    }

    /**
     * As many of {@code squares}, in order, as fit within {@code chunkCap} distinct chunks.
     *
     * <p>Order matters: {@link #centresAlong} puts the ends first, so trimming from the back gives up
     * the middle of a run before either end. The first square is always kept, however large, because a
     * warp that holds nothing at all is the bug this exists to prevent.
     */
    public static List<Square> withinBudget(List<Square> squares, int chunkCap) {
        List<Square> kept = new ArrayList<>(squares.size());
        LongOpenHashSet chunks = new LongOpenHashSet();
        for (Square square : squares) {
            LongOpenHashSet added = new LongOpenHashSet();
            for (int dx = -square.radius(); dx <= square.radius(); dx++) {
                for (int dz = -square.radius(); dz <= square.radius(); dz++) {
                    long chunk = pack(square.chunkX() + dx, square.chunkZ() + dz);
                    if (!chunks.contains(chunk)) {
                        added.add(chunk);
                    }
                }
            }
            if (!kept.isEmpty() && chunks.size() + added.size() > chunkCap) {
                break;
            }
            chunks.addAll(added);
            kept.add(square);
        }
        return kept;
    }

    /** Every distinct chunk the squares cover, by {@link ChunkPos#asLong} packing. */
    public static LongOpenHashSet chunksOf(List<Square> squares) {
        LongOpenHashSet chunks = new LongOpenHashSet();
        for (Square square : squares) {
            for (int dx = -square.radius(); dx <= square.radius(); dx++) {
                for (int dz = -square.radius(); dz <= square.radius(); dz++) {
                    chunks.add(pack(square.chunkX() + dx, square.chunkZ() + dz));
                }
            }
        }
        return chunks;
    }

    /** {@link ChunkPos#asLong}, without needing a {@code ChunkPos} - or Minecraft - to compute it. */
    public static long pack(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFFFFFFL) | ((chunkZ & 0xFFFFFFFFL) << 32);
    }

    /**
     * Claims the ground a hull is about to arrive on: the whole coast from where it reappears behind
     * the far aperture to where it comes to rest.
     *
     * <p>The reappearance point is the one that matters most and the one the old ticket missed. It was
     * a single square centred on the resting place, capped at eight chunks, and on a long hull the
     * teleport lands the ship hundreds of blocks outside it - over ground nothing held, which Sable
     * answers by unloading the hull. Re-asserted at the crossing as well as at launch, so the claim is
     * fresh at exactly the moment it is needed.
     *
     * @param level     the level the anchor lives in
     * @param reappears where the hull's centre is put down behind the exit aperture
     * @param rests     where the hull's centre will come to rest
     * @param halfWidth how far either side of that run to hold, from {@link #arrivalHalfWidth}
     * @param overhang  how far past either end the hull reaches, about half its length
     * @return the chunks now held, for the trace
     */
    public static int hold(ServerLevel level, Vector3dc reappears, Vector3dc rests, double halfWidth,
                           double overhang) {
        return addSquares(level, WARP_ARRIVAL, ticking(centresAlong(reappears.x(), reappears.z(),
                rests.x(), rests.z(), halfWidth, overhang)));
    }

    /**
     * The same squares, each a chunk wider, so everything they covered is block-ticking and not merely
     * loaded.
     *
     * <p>The arrival is the one claim a hull is put down on. The flight hold keeps a ship Sable's own way
     * wherever it lands; this is what covers the moment a crossing into another dimension rebuilds a hull
     * before its drive has woken to hold it, when a square's merely-loaded rim is enough for Sable to file
     * it away again. See {@link HoldRules#blockTicking}.
     */
    private static List<Square> ticking(List<Square> squares) {
        List<Square> widened = new ArrayList<>(squares.size());
        for (Square square : squares) {
            widened.add(new Square(square.chunkX(), square.chunkZ(), Math.min(MAX_RADIUS, square.radius() + 1)));
        }
        return widened;
    }

    /**
     * Claims the corridor path a hull is about to fly down, so the launch-clearance proof stays true
     * for the whole flight.
     *
     * <p>Laid along the run rather than centred on its midpoint, for the same reason as the arrival:
     * one square has to be as wide as the corridor is long, and was capped long before a large hull's
     * corridor was covered.
     *
     * @param level     the level the hull flies through, i.e. its own
     * @param departure where the hull's centre sets off from
     * @param bow       unit vector the hull flies along
     * @param reach     how far ahead the hull flies before the teleport, from
     *                  {@link LaunchClearance#launchReach}
     * @param halfWidth how far either side of the run to hold
     * @param overhang  how far past either end the hull reaches, about half its length
     */
    public static void holdCorridor(ServerLevel level, Vector3dc departure, Vector3dc bow, double reach,
                                    double halfWidth, double overhang) {
        Vector3d end = new Vector3d(bow).mul(Math.max(0.0D, reach)).add(departure);
        addSquares(level, WARP_CORRIDOR,
                centresAlong(departure.x(), departure.z(), end.x, end.z, halfWidth, overhang));
    }

    /**
     * Claims the ground a cruising ship is about to fly over, from under its hull to well ahead of it.
     *
     * <p>Asked for ahead of need, so it can load in the background while the ship is still some way off -
     * {@link CruiseSensor} will not let the ship go faster than the ground it can see is loaded, and this
     * is what gives it ground to see.
     *
     * @param centre    the hull's centre now
     * @param bow       the heading, horizontal and unit length
     * @param reach     how far ahead of the centre to hold
     * @param halfWidth how far either side of the heading to hold
     * @param overhang  how far past either end, about half the hull's length
     * @return the chunks now held
     */
    public static int holdCruise(ServerLevel level, Vector3dc centre, Vector3dc bow, double reach,
                                 double halfWidth, double overhang) {
        Vector3d end = new Vector3d(bow).mul(Math.max(0.0D, reach)).add(centre);
        return addSquares(level, WARP_CRUISE,
                centresAlong(centre.x(), centre.z(), end.x, end.z, halfWidth, overhang));
    }

    /**
     * Claims the ground the arrival search is about to read, while the drive is still spinning.
     *
     * @param squares the landing, from {@link #preloadSquares}
     * @return the chunks now held
     */
    public static int preload(ServerLevel level, List<Square> squares) {
        return addSquares(level, WARP_PRELOAD, squares);
    }

    /**
     * The squares an arrival search at {@code target} will read, worked out before there is a flight.
     *
     * <p>The search places the hull's centre over the target, climbs, and steps sideways by up to
     * {@code safeArrivalRadius} on each axis; at every candidate it reads the same
     * {@link SafeArrival#arrivalSegments segments}, moved with the hull. So what it reads is exactly
     * the segments at the target, each grown by the search radius - which is what is claimed. Not a
     * line and a width round it: on a diagonal bearing each segment is the box enclosing two
     * positions of the hull, and its corners reach further off the line than the hull ever does.
     *
     * @param hullWidth  the hull's world-aligned X extent, in blocks
     * @param hullLength its world-aligned Z extent
     */
    public static List<Square> preloadSquares(double targetX, double targetZ, Vector3dc bow,
                                              double emergeDistance, double hullWidth, double hullLength,
                                              double searchRadius, double clearance, int chunkCap) {
        // A block of slack either way, because the search measures the hull by its rotated plot box
        // and the caller hands over Sable's own world box, which can differ by a fraction of a block.
        double halfX = Math.max(0.0D, hullWidth) * 0.5D + 1.0D;
        double halfZ = Math.max(0.0D, hullLength) * 0.5D + 1.0D;
        BoundingBox3d hull = new BoundingBox3d(targetX - halfX, 0.0D, targetZ - halfZ,
                targetX + halfX, 1.0D, targetZ + halfZ);
        double reach = Math.max(0.0D, searchRadius);
        List<Square> squares = new ArrayList<>();
        for (BoundingBox3d segment : SafeArrival.arrivalSegments(hull, bow, Math.max(0.0D, emergeDistance),
                clearance)) {
            squares.addAll(tile((int) Math.floor(segment.minX() - reach) >> 4,
                    (int) Math.floor(segment.minZ() - reach) >> 4,
                    (int) Math.floor(segment.maxX() + reach) >> 4,
                    (int) Math.floor(segment.maxZ() + reach) >> 4));
        }
        return withinBudget(squares, chunkCap);
    }

    /**
     * Squares that tile a rectangle of chunks, inclusive, without gaps.
     *
     * <p>As wide as the rectangle's narrow side allows, up to {@link #MAX_RADIUS}, laid edge to edge.
     * The last row and column are pulled back to finish flush with the far edge rather than hanging
     * past it: they overlap their neighbours instead, so the tiling never claims a chunk outside the
     * rectangle - which on a big rectangle was thousands of chunks spent on nothing, and a budget
     * trimming away ground the search really would read.
     */
    public static List<Square> tile(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        return tile(minChunkX, minChunkZ, maxChunkX, maxChunkZ, MAX_RADIUS);
    }

    /**
     * Squares that make every chunk of a rectangle <em>block-ticking</em>, not merely loaded.
     *
     * <p>What holding ground for a ship needs. Sable keeps a ship - and rebuilds an unloaded one - only
     * while every column under its box is at ticket level 32 or below, and a square's outer ring is at 33:
     * loaded, and not enough ({@link HoldRules#blockTicking}). So the rectangle is tiled as {@link #tile}
     * would, one radius narrower, and every square's ticket is widened by one, which pulls each chunk the
     * tiling covered a level lower. The ring beyond the rectangle is loaded as a result, and is the price.
     */
    public static List<Square> tickingTile(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        List<Square> squares = new ArrayList<>();
        for (Square square : tile(minChunkX, minChunkZ, maxChunkX, maxChunkZ, MAX_RADIUS - 1)) {
            squares.add(new Square(square.chunkX(), square.chunkZ(), square.radius() + 1));
        }
        return squares;
    }

    private static List<Square> tile(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ, int maxRadius) {
        int width = Math.max(1, maxChunkX - minChunkX + 1);
        int length = Math.max(1, maxChunkZ - minChunkZ + 1);
        int radius = Mth.clamp((Math.min(width, length) - 1) / 2, 0, maxRadius);
        List<Square> squares = new ArrayList<>();
        for (int x : centres(minChunkX, minChunkX + width - 1, radius)) {
            for (int z : centres(minChunkZ, minChunkZ + length - 1, radius)) {
                squares.add(new Square(x, z, radius));
            }
        }
        return squares;
    }

    /** Centres along one axis for squares of {@code radius} spanning {@code min..max}, flush at both ends. */
    private static List<Integer> centres(int min, int max, int radius) {
        List<Integer> centres = new ArrayList<>();
        int side = 2 * radius + 1;
        int last = Math.max(min + radius, max - radius);
        for (int c = min + radius; ; c += side) {
            if (c >= last) {
                centres.add(last);
                return centres;
            }
            centres.add(c);
        }
    }

    /** Whether every chunk in {@code squares} has finished loading. Reads, never loads. */
    public static boolean loaded(ServerLevel level, List<Square> squares) {
        LongOpenHashSet chunks = chunksOf(squares);
        for (long chunk : chunks) {
            if (level.getChunkSource().getChunkNow(ChunkPos.getX(chunk), ChunkPos.getZ(chunk)) == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * How far either side of an arrival run to hold: half the hull's width across the bearing, and
     * the aperture's reach past it.
     *
     * <p>The aperture's reach stays in here even though it no longer gates the arrival: holding a few
     * more chunks either side is cheap, and it is not a check that can refuse anything.
     */
    public static double arrivalHalfWidth(double crossSpan, double apertureMargin) {
        return Math.max(0.0D, crossSpan) * 0.5D + Math.max(0.0D, apertureMargin);
    }

    private static int addSquares(ServerLevel level, TicketType<ChunkPos> type, List<Square> squares) {
        return Holds.groundSquares(level, type, squares);
    }

    /** The chunk budget any one claim here may spend. */
    public static int maxChunks() {
        try {
            return AWConfig.MAX_ARRIVAL_CHUNKS.get();
        } catch (IllegalStateException e) {
            return 4096; // config not loaded - unit tests
        }
    }

    /**
     * Whether a landing will stay loaded once this ticket lapses.
     *
     * <p>The pure decision, kept free of a server so it can be tested: a landing is durable when a
     * player is close enough to keep it in view, or a force-loaded region covers it. Neither is true
     * of an anchor sitting alone in unloaded wilderness, which is exactly the case a warp must not fly
     * a ship into and then let go of - the world under it unloads and Sable removes the hull.
     */
    public static boolean durableLanding(boolean hasChunkLoader, boolean withinViewDistance) {
        return hasChunkLoader || withinViewDistance;
    }

    /**
     * Whether the landing zone of a planned arrival is durable against a real level.
     *
     * <p>"Within view distance" is measured against a player actually being near enough that the
     * chunk stays ticking after the ticket goes; "has a loader" is a force-loaded chunk, the base
     * game's own way of keeping ground resident with nobody there.
     */
    public static boolean isLandingDurable(ServerLevel level, Vector3dc arrival) {
        ChunkPos landing = new ChunkPos(Mth.floor(arrival.x()) >> 4, Mth.floor(arrival.z()) >> 4);
        boolean forced = level.getForcedChunks().contains(landing.toLong());
        boolean inView = false;
        int viewChunks = level.getServer().getPlayerList().getViewDistance() + 1;
        for (ServerPlayer player : level.players()) {
            ChunkPos at = player.chunkPosition();
            if (Math.abs(at.x - landing.x) <= viewChunks && Math.abs(at.z - landing.z) <= viewChunks) {
                inView = true;
                break;
            }
        }
        return durableLanding(forced, inView);
    }
}
