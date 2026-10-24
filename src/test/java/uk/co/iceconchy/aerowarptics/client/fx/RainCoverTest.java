package uk.co.iceconchy.aerowarptics.client.fx;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The storm's rain stops on a ship.
 *
 * <p>The report was the storm's rain falling through airships that vanilla's rain stopped on. Vanilla's
 * stops only because Sable wraps the heightmap reads inside vanilla's own two rain methods; the storm's rain
 * reads the heightmap itself, which has no ship in it. {@link RainCover} is the question Sable asks, asked
 * for ours, and what is pinned here is its answer: the deck's own height, for a hull at any height and any
 * attitude, and nothing at all for a column the hull is not under.
 *
 * <p>Whether the rain is then <em>drawn</em> ending there cannot be tested without a game. What can is that
 * the drawing never asks the ground how high it is without asking the ships too, which is read off the source.
 */
class RainCoverTest {

    /** As near as the walk closes on a surface: half a block, halved five times. */
    private static final double CLOSE = 1.0D / 32.0D + 1.0e-6D;

    /** Plot coordinates - the kind of numbers a hull's blocks really have. */
    private static final int OX = 20_480_000;
    private static final int OY = 64;
    private static final int OZ = 20_480_000;

    /** The deck's layer in the plot, so its top face is one above. */
    private static final int DECK = OY + 6;

    /** A hull: blocks in a plot, a pose, and a count of how often the plot was asked. */
    private static final class Hull implements RainCover.Plot {
        private record Block(int x, int y, int z) {
        }

        private final Set<Block> blocks = new HashSet<>();
        private final Pose3d pose = new Pose3d();
        private int reads;

        /** Fills a box of the plot, corners included, measured from the plot's origin. */
        Hull fill(int x0, int y0, int z0, int x1, int y1, int z1) {
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        blocks.add(new Block(OX + x, y, OZ + z));
                    }
                }
            }
            return this;
        }

        /** Puts the plot's {@code pivot} at {@code position} in the world, turned by {@code orientation}. */
        Hull at(Vector3d pivot, Vector3d position, Quaterniond orientation) {
            pose.rotationPoint().set(pivot);
            pose.position().set(position);
            pose.orientation().set(orientation);
            return this;
        }

        @Override
        public boolean stops(int x, int y, int z) {
            reads++;
            return blocks.contains(new Block(x, y, z));
        }

        /** The box round every corner of every block where the pose puts it: what Sable keeps for a hull. */
        BoundingBox3d bounds() {
            double[] low = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
            double[] high = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            Vector3d corner = new Vector3d();
            for (Block block : blocks) {
                for (int index = 0; index < 8; index++) {
                    pose.transformPosition(corner.set(
                            block.x + (index & 1), block.y + (index >> 1 & 1), block.z + (index >> 2 & 1)));
                    double[] at = {corner.x, corner.y, corner.z};
                    for (int axis = 0; axis < 3; axis++) {
                        low[axis] = Math.min(low[axis], at[axis]);
                        high[axis] = Math.max(high[axis], at[axis]);
                    }
                }
            }
            return new BoundingBox3d(low[0], low[1], low[2], high[0], high[1], high[2]);
        }

        double over(int x, int z) {
            return RainCover.height(pose, bounds(), x, z, this);
        }

        /** Where the middle of a world column meets a level of the plot - the height a flat deck is at there. */
        double levelAt(int x, int z, double plotY) {
            Vector3d low = pose.transformPositionInverse(new Vector3d(x + 0.5D, 0.0D, z + 0.5D));
            Vector3d high = pose.transformPositionInverse(new Vector3d(x + 0.5D, 1.0D, z + 0.5D));
            return (plotY - low.y) / (high.y - low.y);
        }
    }

    /**
     * A five by five deck with a mast at one corner, level, with the middle of the deck over world
     * (100.5, 200.5) and the deck's top face at {@code deckTop}. Plot block (i, k) of the deck is world column
     * (98 + i, 198 + k).
     */
    private static Hull raft(double deckTop) {
        return new Hull()
                .fill(0, DECK, 0, 4, DECK, 4)
                .fill(0, DECK + 1, 0, 0, DECK + 6, 0)
                .at(new Vector3d(OX + 2.5D, DECK + 1.0D, OZ + 2.5D), new Vector3d(100.5D, deckTop, 200.5D),
                        new Quaterniond());
    }

    @Test
    void rainStopsOnTheDeck() {
        Hull raft = raft(150.8D);
        assertEquals(150.8D, raft.over(100, 200), CLOSE, "the middle of the deck");
        assertEquals(150.8D, raft.over(102, 198), CLOSE, "the far corner of the deck");
        assertEquals(156.8D, raft.over(98, 198), CLOSE, "the top of the mast, not the deck under it");
    }

    /**
     * The whole reason this is not Sable's answer rounded: a ship rides at any height, and to a whole block the
     * rain ends up to a block over the deck or under it. Swept through a block of heights in sixty-fourths,
     * which takes in the one where every block face sits exactly on a whole number - where a walk that read
     * the face between two blocks would find the deck or miss it by the rounding.
     */
    @Test
    void aShipRidesAtAnyHeightAndTheRainEndsOnItsDeck() {
        for (int step = 0; step <= 64; step++) {
            double deckTop = 150.0D + step / 64.0D;
            double height = raft(deckTop).over(100, 200);
            assertEquals(deckTop, height, CLOSE, "deck at " + deckTop);
            assertTrue(height >= deckTop - 1.0e-6D,
                    "at " + deckTop + " the rain ends inside the deck (" + height + "), where a splash is hidden");
        }
    }

    @Test
    void aHullBelowZeroIsNoDifferent() {
        assertEquals(-40.3D, raft(-40.3D).over(100, 200), CLOSE);
    }

    @Test
    void aColumnBesideTheHullFallsThroughAndReadsNothing() {
        Hull raft = raft(150.8D);
        for (int[] column : new int[][]{{97, 200}, {103, 200}, {100, 197}, {100, 203}, {5000, -5000}}) {
            assertEquals(RainCover.NONE, raft.over(column[0], column[1]), column[0] + ", " + column[1]);
        }
        assertEquals(0, raft.reads, "a column outside a hull's box was looked up in its plot - far enough"
                + " outside, that is another ship's plot");
    }

    /** A deck with a hatch in it: inside the hull's box, and nothing in the way. */
    @Test
    void aColumnOverAHoleInTheDeckFallsThrough() {
        Hull raft = raft(150.8D);
        raft.blocks.remove(new Hull.Block(OX + 2, DECK, OZ + 2));
        assertEquals(RainCover.NONE, raft.over(100, 200), "rain stopped on nothing");
        assertTrue(raft.reads <= 7, "a seven block box was walked in " + raft.reads + " reads");
        assertEquals(150.8D, raft.over(101, 200), CLOSE, "the deck beside the hatch");
    }

    /** An envelope over the deck is a roof: the deck under it is dry, the deck beside it is not. */
    @Test
    void whateverIsHighestInTheColumnIsWhatStopsTheRain() {
        Hull ship = new Hull()
                .fill(0, DECK, 0, 9, DECK, 4)
                .fill(0, DECK + 8, 0, 4, DECK + 11, 4)
                .at(new Vector3d(OX, DECK + 1.0D, OZ), new Vector3d(98.0D, 150.4D, 198.0D), new Quaterniond());
        assertEquals(150.4D + 11.0D, ship.over(100, 200), CLOSE, "under the envelope");
        assertEquals(150.4D, ship.over(105, 200), CLOSE, "on the open deck");
    }

    /**
     * A hull long one way, turned a quarter. The world columns it is over are the plot's turned with it: a
     * walk that came down the world's column in the plot's own axes would find deck where there is none.
     */
    @Test
    void aTurnedHullIsWalkedInItsOwnPlot() {
        Hull barge = new Hull()
                .fill(0, DECK, 0, 11, DECK, 1)
                .at(new Vector3d(OX + 6.0D, DECK + 1.0D, OZ + 1.0D), new Vector3d(100.0D, 150.4D, 200.0D),
                        new Quaterniond().rotateY(Math.toRadians(90.0D)));
        // Twelve long in the plot's x, so twelve long in the world's z now, and two wide in its x.
        assertEquals(150.4D, barge.over(100, 195), CLOSE, "the bow");
        assertEquals(150.4D, barge.over(99, 205), CLOSE, "the stern");
        assertEquals(RainCover.NONE, barge.over(105, 200), "where the hull would be if it had not turned");
    }

    /**
     * A deck that is not level is met where the column meets its slope. Whatever the tilt, the rain neither
     * stops short of the deck nor is carried through it.
     */
    @Test
    void aTiltedDeckStopsTheRainOnItsSlope() {
        for (double pitch : new double[]{-35.0D, -12.0D, 7.5D, 20.0D, 40.0D}) {
            for (double roll : new double[]{-25.0D, 0.0D, 15.0D}) {
                Hull ship = new Hull()
                        .fill(0, DECK, 0, 20, DECK, 20)
                        .at(new Vector3d(OX + 10.5D, DECK + 1.0D, OZ + 10.5D), new Vector3d(100.5D, 150.4D, 200.5D),
                                new Quaterniond().rotateY(Math.toRadians(30.0D))
                                        .rotateX(Math.toRadians(pitch)).rotateZ(Math.toRadians(roll)));
                for (int[] column : new int[][]{{100, 200}, {96, 203}, {104, 198}, {101, 195}}) {
                    assertEquals(ship.levelAt(column[0], column[1], DECK + 1.0D), ship.over(column[0], column[1]),
                            CLOSE, "pitch " + pitch + ", roll " + roll + ", column " + column[0] + ", " + column[1]);
                }
            }
        }
    }

    // ---------------------------------------------------------------- wiring

    private static final Path RAIN = Path.of("src", "main", "java", "uk", "co", "iceconchy", "aerowarptics",
            "client", "fx", "RiftStormRain.java");

    /**
     * The fault itself: a read of the heightmap with no read of the ships beside it. The drops, the splashes
     * and the muffled sound under a roof each read it, and each has to ask.
     */
    @Test
    void theStormsRainNeverAsksTheGroundWithoutAskingTheShips() {
        String source;
        try {
            source = Files.readString(RAIN);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        int ground = count(source, "Heightmap.Types.MOTION_BLOCKING");
        assertTrue(ground >= 3,
                "only " + ground + " reads of the heightmap found in the storm's rain - have they moved?");
        assertEquals(ground, count(source, "ships.over("), "the storm's rain reads the heightmap somewhere it"
                + " does not ask the ships: there it falls through a deck");

        int render = source.indexOf("static void render(");
        int drawn = source.indexOf("RainCovers.drawn(");
        int ticked = source.indexOf("RainCovers.ticked(");
        assertTrue(render > 0, "the storm's rain is no longer drawn by render");
        assertTrue(drawn > render, "the drops are not stopped by the hull as it is drawn");
        assertTrue(ticked > 0 && ticked < render, "the splashes are not stopped by the hull as the tick has it");
    }

    private static int count(String source, String needle) {
        int found = 0;
        for (int at = source.indexOf(needle); at >= 0; at = source.indexOf(needle, at + needle.length())) {
            found++;
        }
        return found;
    }
}
