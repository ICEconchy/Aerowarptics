package uk.co.iceconchy.aerowarptics.collapse;

import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * How a relic machine - a Collapse Cradle, a Rift Plotter or a Rift Ward - is turned: through the block in the middle
 * of each side of its floor, or from under its middle, and by nothing else, and so that no rotation ever comes out of it.
 *
 * <p>A Create block passes rotation on whenever it has shafts on two faces, whatever else it is; a consumer does it
 * as readily as a gearbox. The floor was once one relay, shafts on every face, and a shaft run into one side of a
 * machine turned whatever stood against any other. So nothing here is linked to anything else of the machine now.
 * Each of the five inputs has the one shaft - the four sides' facing out, the master's facing down
 * ({@link CradleStructure#shaftTowards}) - and is the end of whatever line is run into it. The machine goes by the
 * fastest: the master's speed is that input's, read each time it is asked, and its load is counted on that input and
 * no other ({@link RelicFeed}). Two lines at two speeds into two inputs are simply two lines.
 */
public final class RelicFloor {

    private RelicFloor() {
    }

    /** The input turning a machine - its master or one of its four side bases - or {@code null} if nothing is. */
    @Nullable
    public static KineticBlockEntity feeder(@Nullable Level level, BlockPos master) {
        if (level == null) {
            return null;
        }
        List<BlockPos> cells = CradleStructure.inputs(master);
        KineticBlockEntity[] inputs = new KineticBlockEntity[cells.size()];
        float[] speeds = new float[cells.size()];
        for (int i = 0; i < inputs.length; i++) {
            BlockPos cell = cells.get(i);
            if (!level.isLoaded(cell)) {
                continue;
            }
            KineticBlockEntity input = null;
            if (cell.equals(master)) {
                if (level.getBlockEntity(cell) instanceof KineticBlockEntity self && self instanceof RelicMaster) {
                    input = self;
                }
            } else if (level.getBlockEntity(cell) instanceof RelicBaseBlockEntity base && master.equals(base.masterPos())) {
                input = base;
            }
            if (input != null) {
                inputs[i] = input;
                speeds[i] = input.getTheoreticalSpeed();
            }
        }
        int feeder = RelicFeed.feeder(speeds);
        return feeder == RelicFeed.NONE ? null : inputs[feeder];
    }

    /** The speed a machine is turned at: its feeder's, which is nothing while that line is overstressed. */
    public static float speed(@Nullable Level level, BlockPos master) {
        KineticBlockEntity feeder = feeder(level, master);
        if (feeder instanceof RelicMaster self) {
            return self.lineSpeed();
        }
        return feeder == null ? 0.0F : feeder.getSpeed();
    }

    /** The load an input puts on its line: the machine's, if it is the one turning it, and none otherwise. */
    public static float load(KineticBlockEntity input, @Nullable BlockPos master) {
        if (master == null || input.getLevel() == null
                || !(input.getLevel().getBlockEntity(master) instanceof RelicMaster machine)) {
            return 0.0F;
        }
        return feeder(input.getLevel(), master) == input ? machine.relicImpact() : 0.0F;
    }

    /** Tells an input's line what the input asks of it now. */
    public static void tell(KineticBlockEntity input) {
        if (input.hasNetwork()) {
            input.getOrCreateNetwork().updateStressFor(input, input.calculateStressApplied());
        }
    }

    /** Create's goggle lines for a machine - the load and the stress on the line turning it, which is its feeder's. */
    public static void goggles(@Nullable Level level, BlockPos master, List<Component> tooltip, boolean isPlayerSneaking) {
        KineticBlockEntity feeder = feeder(level, master);
        if (feeder instanceof RelicMaster self) {
            self.lineGoggles(tooltip, isPlayerSneaking);
        } else if (feeder instanceof RelicBaseBlockEntity base) {
            base.kineticGoggles(tooltip, isPlayerSneaking);
        }
    }

    /**
     * Something that decides which input carries the load, or how much it is, has changed: every input tells its
     * network again on its next tick. Create caches what a block asks for, and asks again only when told.
     */
    public static void changed(@Nullable Level level, BlockPos master) {
        if (level == null || level.isClientSide) {
            return;
        }
        for (BlockPos cell : CradleStructure.inputs(master)) {
            if (!level.isLoaded(cell)) {
                continue;
            }
            if (level.getBlockEntity(cell) instanceof RelicBaseBlockEntity base) {
                base.retell();
            } else if (level.getBlockEntity(cell) instanceof RelicMaster self) {
                self.retell();
            }
        }
    }

    /**
     * Lets go of every link a block of the machine has across a face it has no shaft on - once, as it loads.
     *
     * <p>A machine set down while the floor was a relay was saved linked through faces that have no shaft now, and
     * Create does not look at a link again until something disturbs it: the machine would go on turning from below,
     * and go on turning its neighbours, for as long as nobody touched the line. Whatever is let go looks for a
     * source again by the rules as they stand.
     */
    public static void dropStrayLinks(KineticBlockEntity cell) {
        Level level = cell.getLevel();
        BlockState state = cell.getBlockState();
        if (level == null || level.isClientSide || !(state.getBlock() instanceof IRotate block)) {
            return;
        }
        BlockPos pos = cell.getBlockPos();
        for (Direction face : Direction.values()) {
            if (block.hasShaftTowards(level, pos, state, face)) {
                continue;
            }
            BlockPos next = pos.relative(face);
            if (next.equals(cell.source)) {
                letGo(cell);
            }
            if (level.isLoaded(next) && level.getBlockEntity(next) instanceof KineticBlockEntity neighbour
                    && pos.equals(neighbour.source)) {
                letGo(neighbour);
            }
        }
    }

    private static void letGo(KineticBlockEntity cell) {
        cell.detachKinetics();
        cell.removeSource();
        cell.attachKinetics();
    }
}
