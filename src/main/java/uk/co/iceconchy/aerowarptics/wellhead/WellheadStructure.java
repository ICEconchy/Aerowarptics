package uk.co.iceconchy.aerowarptics.wellhead;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * The shape of a Rift Wellhead: a hollow cube of casing and glass with a fissure in its exact middle.
 *
 * <p>Unlike the relics, which are set down whole, a wellhead is built a block at a time out of blocks the
 * player already has, and then <em>formed</em>: every block of the shell is swapped for a part that draws
 * nothing, and the collar draws one machine the size of the frame. Everything is measured from the fissure,
 * because the fissure is the one block of it that was there first and cannot be anywhere else.
 *
 * <h2>What goes where</h2>
 * A cell of the shell touches one, two or three of the cube's faces. One that touches two or three is on an
 * edge or a corner and is Rift Casing; one that touches a single face is Warp Glass - except the cell in the
 * very middle of each face, which is where the machine meets the world: the collar on the side the player set
 * it, the hatch on top, and a tap on each of the other four. So what a formed part used to be is a fact about
 * where it stands, and unforming puts the right block back with nothing remembered.
 *
 * <h2>How big</h2>
 * The collar looks straight back along its own facing. A fissure one block behind it makes a three-cube, two a
 * five, three a seven. There is no search and no choice between frames, so the same build always forms the
 * same machine - a lesson from the Drive Extender, whose first rule let a three-cube form as a two.
 *
 * <p>Kept apart from the blocks so the rules can be tested for what they are - arithmetic over block positions
 * - without a registry, as {@code CradleStructure} and {@code DriveStructure} are.
 */
public final class WellheadStructure {

    /** The furthest a face can stand from its fissure: a seven-cube. */
    public static final int MAX_RADIUS = 3;

    /** What a part's three state numbers are measured from, so they are never negative: 0..6 is -3..3. */
    public static final int STATE_BIAS = MAX_RADIUS;

    /** What a cell of the cube is. */
    public enum Cell {
        /** Not part of the machine at all. */
        OUTSIDE,
        /** The fissure. */
        CENTRE,
        /** Inside the shell: air, and it has to stay air. */
        INSIDE,
        /** An edge or a corner of the shell. */
        CASING,
        /** A face of the shell. */
        GLASS,
        /** The middle of the face the player set the collar in: the master. */
        COLLAR,
        /** The middle of the top face, where blocks are fed in. */
        HATCH,
        /** The middle of each remaining face, where essence is drawn off. */
        TAP;

        /** Whether this is a block of the shell. */
        public boolean isShell() {
            return this == CASING || this == GLASS || this == COLLAR || this == HATCH || this == TAP;
        }

        /** Whether this is one of the shell's two port kinds, which have block entities for pipes and funnels to find. */
        public boolean isPort() {
            return this == HATCH || this == TAP;
        }
    }

    private WellheadStructure() {
    }

    /**
     * What stands at an offset from the fissure, in a frame of this radius whose collar is in the middle of one side.
     *
     * @param collarSide which side of the frame the collar is in - the way the collar faces, out of the machine
     */
    public static Cell cell(int dx, int dy, int dz, int radius, Direction collarSide) {
        int ax = Math.abs(dx);
        int ay = Math.abs(dy);
        int az = Math.abs(dz);
        int reach = Math.max(ax, Math.max(ay, az));
        if (reach > radius) {
            return Cell.OUTSIDE;
        }
        if (reach < radius) {
            return reach == 0 ? Cell.CENTRE : Cell.INSIDE;
        }
        int faces = (ax == radius ? 1 : 0) + (ay == radius ? 1 : 0) + (az == radius ? 1 : 0);
        if (faces > 1) {
            return Cell.CASING;
        }
        // On one face only. Its middle is the cell whose other two offsets are both nought.
        if (ax + ay + az != radius) {
            return Cell.GLASS;
        }
        Direction side = Direction.fromDelta(Integer.signum(dx), Integer.signum(dy), Integer.signum(dz));
        if (side == collarSide) {
            return Cell.COLLAR;
        }
        return side == Direction.UP ? Cell.HATCH : Cell.TAP;
    }

    public static Cell cell(Vec3i offset, int radius, Direction collarSide) {
        return cell(offset.getX(), offset.getY(), offset.getZ(), radius, collarSide);
    }

    /** Every cell of the shell as an offset from the fissure, the collar's included, bottom up in a fixed order. */
    public static List<Vec3i> shell(int radius) {
        List<Vec3i> shell = new ArrayList<>();
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) == radius) {
                        shell.add(new Vec3i(dx, dy, dz));
                    }
                }
            }
        }
        return List.copyOf(shell);
    }

    /** Every cell inside the shell but the fissure's own, as an offset from the fissure. None, for a three-cube. */
    public static List<Vec3i> interior(int radius) {
        List<Vec3i> interior = new ArrayList<>();
        for (int dy = 1 - radius; dy <= radius - 1; dy++) {
            for (int dx = 1 - radius; dx <= radius - 1; dx++) {
                for (int dz = 1 - radius; dz <= radius - 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        interior.add(new Vec3i(dx, dy, dz));
                    }
                }
            }
        }
        return List.copyOf(interior);
    }

    /** How many blocks a frame of this radius is built from, the collar included: 26, 98 or 218. */
    public static int shellBlocks(int radius) {
        int size = radius * 2 + 1;
        int hollow = size - 2;
        return size * size * size - hollow * hollow * hollow;
    }

    /**
     * How far behind the collar its fissure is: the first of one, two or three blocks back that holds one, or
     * nought if none does. The nearest wins, so a second tear further back never changes which frame is meant.
     *
     * @param fissureAt whether there is a fissure this many blocks straight behind the collar
     */
    public static int radius(IntPredicate fissureAt) {
        for (int back = 1; back <= MAX_RADIUS; back++) {
            if (fissureAt.test(back)) {
                return back;
            }
        }
        return 0;
    }

    /** Where the fissure is, from where the collar stands, the side it is in and the frame's radius. */
    public static BlockPos centre(BlockPos collar, Direction collarSide, int radius) {
        return collar.relative(collarSide, -radius);
    }

    /** Where the collar is, from the fissure. */
    public static BlockPos collar(BlockPos centre, Direction collarSide, int radius) {
        return centre.relative(collarSide, radius);
    }

    /** A part's three state numbers, each 0..6, from its offset from the fissure. */
    public static int[] stateOf(Vec3i offset) {
        return new int[]{offset.getX() + STATE_BIAS, offset.getY() + STATE_BIAS, offset.getZ() + STATE_BIAS};
    }

    /** A part's offset from the fissure, from its three state numbers. */
    public static Vec3i offsetOf(int x, int y, int z) {
        return new Vec3i(x - STATE_BIAS, y - STATE_BIAS, z - STATE_BIAS);
    }

    /** Where a part's fissure is, from where the part stands and its three state numbers. */
    public static BlockPos centre(BlockPos part, int x, int y, int z) {
        return part.subtract(offsetOf(x, y, z));
    }

    /** How far out a part's frame reaches, read off the part's own offset: a shell cell is always at the radius. */
    public static int radiusOf(Vec3i offset) {
        return Math.max(Math.abs(offset.getX()), Math.max(Math.abs(offset.getY()), Math.abs(offset.getZ())));
    }

    /**
     * Whether a block of the machine has a shaft on a face.
     *
     * <p>Only the collar has one, and only on the face looking out of the machine. A Create block passes rotation
     * between any two faces it has shafts on, so a second shaft anywhere in the frame would make the wellhead a
     * gearbox - the relics' floor was one, and a shaft into one side turned whatever stood against another.
     */
    public static boolean shaftTowards(Vec3i offset, Direction face, int radius, Direction collarSide) {
        return face == collarSide && cell(offset, radius, collarSide) == Cell.COLLAR;
    }

    /**
     * The machine's box, relative to the fissure block's lowest corner: {minX, minY, minZ, maxX, maxY, maxZ}.
     * What the renderer draws in and what a blast measures cover from.
     */
    public static double[] box(int radius) {
        return new double[]{-radius, -radius, -radius, radius + 1, radius + 1, radius + 1};
    }
}
