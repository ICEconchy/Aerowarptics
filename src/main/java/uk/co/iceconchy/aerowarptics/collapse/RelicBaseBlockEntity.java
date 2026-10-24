package uk.co.iceconchy.aerowarptics.collapse;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;

import java.util.List;

/**
 * A relic machine's base: a block entity for a pipe or a funnel to find, and - in the middle of each side of the
 * floor - the end of a line of shafts, which is one way the machine is turned. See {@link RelicBaseBlock} and
 * {@link RelicFloor}. It carries the master's load while it is the input the machine goes by, and none otherwise.
 * Goggles read it as the machine it belongs to.
 */
public class RelicBaseBlockEntity extends KineticBlockEntity {

    /** Whether the network has yet to be told what this base asks of it now - see {@link RelicFloor#changed}. */
    private boolean retell = true;
    /** Whether links saved under older rules have been let go of - see {@link RelicFloor#dropStrayLinks}. */
    private boolean settled;

    public RelicBaseBlockEntity(BlockPos pos, BlockState state) {
        super(AWBlockEntities.RELIC_BASE.get(), pos, state);
    }

    /** The master's load, while this is the input turning it. */
    @Override
    public float calculateStressApplied() {
        float impact = RelicFloor.load(this, masterPos());
        this.lastStressApplied = impact;
        return impact;
    }

    /** Has the network asked again what this base costs it, on the next tick. */
    void retell() {
        retell = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }
        if (!settled) {
            settled = true;
            RelicFloor.dropStrayLinks(this);
        }
        if (retell) {
            retell = false;
            RelicFloor.tell(this);
        }
    }

    /** A line starting, stopping or changing speed can change which input the machine goes by. */
    @Override
    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        BlockPos master = masterPos();
        if (master != null) {
            RelicFloor.changed(level, master);
        }
    }

    /** Where the master is, or {@code null} if the base has come apart from it. */
    @Nullable
    public BlockPos masterPos() {
        BlockState state = getBlockState();
        if (level == null || !(state.getBlock() instanceof RelicBaseBlock base) || !base.stillValid(level, worldPosition, state)) {
            return null;
        }
        return RelicBaseBlock.master(worldPosition, state);
    }

    /** The master's block entity, or {@code null} if the base has come apart from it. */
    @Nullable
    public BlockEntity master() {
        BlockPos master = masterPos();
        return master == null ? null : level.getBlockEntity(master);
    }

    /** Reads as the machine does, so goggles on any block of the floor say what the machine is doing. */
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        return master() instanceof IHaveGoggleInformation machine
                ? machine.addToGoggleTooltip(tooltip, isPlayerSneaking)
                : super.addToGoggleTooltip(tooltip, isPlayerSneaking);
    }

    /** What Create's goggles say of this base as a kinetic block - the machine's load, where this is its feeder. */
    public boolean kineticGoggles(List<Component> tooltip, boolean isPlayerSneaking) {
        return super.addToGoggleTooltip(tooltip, isPlayerSneaking);
    }
}
