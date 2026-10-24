package uk.co.iceconchy.aerowarptics.sunderer;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.advancement.AWCriteria;
import uk.co.iceconchy.aerowarptics.advancement.SundererTrigger;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.gate.RiftGateFrameBlock;
import uk.co.iceconchy.aerowarptics.gate.RiftGateShape;
import uk.co.iceconchy.aerowarptics.hold.Holds;
import uk.co.iceconchy.aerowarptics.network.ClientboundSundererCuePacket;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.registry.AWFluids;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.wellhead.WellheadMeltdown;

import java.util.ArrayList;
import java.util.List;

/**
 * A Rift Sunderer: its charge, and the one tear it is for.
 *
 * <p>Everything that decides anything is elsewhere and pure. {@link SundererRing} is the ring and what the breach
 * does to it, {@link SundererSequence} is when the tear may fail and how, and {@link SundererChoreography} is the
 * scene - which this block reads for its own light and for how hard it draws in what stands near. This is the
 * part with a world in it: it fires on a redstone edge once {@link SundererSurvey} has nothing against it, keeps
 * its ground held, tells the clients where the tear has got to, and hands the end to {@link SundererBreach}.
 *
 * <h2>What it costs</h2>
 * Its whole tank, spent at the edge. A sundering that stops before it has committed - the shaft stopped or the
 * ring broken while it is still reaching - misfires and hands half of it back; one that fails after ruptures and
 * takes the ring and the ground with it. One that tears takes the Sunderer itself: it is used up.
 *
 * <p>An unloaded Sunderer does not tick, and one read back from disk mid-tear holds for a grace before it goes on:
 * Create reports no rotation for the first ticks after a load, which a sundering must not take for a stopped shaft.
 */
public class RiftSundererBlockEntity extends KineticBlockEntity {

    /** Ticks after a load in which a sundering holds whatever the shaft says. */
    private static final int RESUME_GRACE = 60;
    /** Blocks from it within which a refusal is said. */
    private static final double TOLD = 16.0D;

    private final FluidTank tank = new FluidTank(4_000, stack -> stack.getFluid() == AWFluids.RIFT_ESSENCE.get()) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            sendData();
        }
    };

    private SundererFailure failure = SundererFailure.NO_RING;
    private boolean powered;
    private int secondTimer;
    private int holdTimer;
    private int lastSignal = -1;

    // ------------------------------------------------------------------ a tear in progress

    @Nullable
    private SundererSequence sequence;
    private long seed;
    @Nullable
    private RiftGateShape shape;
    private BlockPos centre = BlockPos.ZERO;
    private List<BlockPos> frame = List.of();
    /**
     * The third of the frame that bursts: taken out of the world as it fires, and drawn by every client lifting,
     * orbiting and being swallowed (the owner's choice, 2026-10-17). What each was is kept for a misfire, which puts
     * them back.
     */
    private List<BlockPos> shattering = List.of();
    private List<BlockState> lifted = List.of();
    @Nullable
    private BlockPos grip;
    private boolean taken;

    public RiftSundererBlockEntity(BlockPos pos, BlockState state) {
        super(AWBlockEntities.RIFT_SUNDERER.get(), pos, state);
    }

    // ------------------------------------------------------------------ state

    public FluidTank tank() {
        return tank;
    }

    /** What a pipe finds: the tank, which takes Rift Essence and gives it back while the Sunderer is idle. */
    public IFluidHandler fluids() {
        return new IFluidHandler() {
            @Override
            public int getTanks() {
                return 1;
            }

            @Override
            public FluidStack getFluidInTank(int index) {
                return tank.getFluid();
            }

            @Override
            public int getTankCapacity(int index) {
                return tank.getCapacity();
            }

            @Override
            public boolean isFluidValid(int index, FluidStack stack) {
                return tank.isFluidValid(stack);
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                return running() ? 0 : tank.fill(resource, action);
            }

            @Override
            public FluidStack drain(FluidStack resource, FluidAction action) {
                return running() ? FluidStack.EMPTY : tank.drain(resource, action);
            }

            @Override
            public FluidStack drain(int most, FluidAction action) {
                return running() ? FluidStack.EMPTY : tank.drain(most, action);
            }
        };
    }

    /** Whether it has fired and the tear is not over. */
    public boolean running() {
        return sequence != null;
    }

    public SundererFailure failure() {
        return failure;
    }

    /** Full means fifteen; a sundering under way reads fifteen too. */
    public int comparatorSignal() {
        if (running()) {
            return 15;
        }
        int charge = Math.max(1, tank.getCapacity());
        return (int) Math.floor(15.0D * Math.min(charge, tank.getFluidAmount()) / charge);
    }

    // ------------------------------------------------------------------ firing

    /** A rising edge fires it. Read on a neighbour's change, as every other machine of the mod reads its signal. */
    void readSignal() {
        if (level == null) {
            return;
        }
        boolean now = level.hasNeighborSignal(worldPosition);
        if (now && !powered && level instanceof ServerLevel server) {
            fire(server);
        }
        powered = now;
        setChanged();
    }

    /** Fires, or says why not and spends nothing. */
    void fire(ServerLevel level) {
        if (!AWConfig.SUNDERER_ENABLED.get()) {
            return;
        }
        if (running()) {
            refuse(level, SundererFailure.BUSY);
            return;
        }
        SundererSurvey.Result survey = SundererSurvey.full(level, this);
        if (!survey.ok()) {
            refuse(level, survey.failure());
            return;
        }
        SundererRing.Ring ring = survey.ring();
        tank.drain(AWConfig.SUNDERER_CHARGE.get(), IFluidHandler.FluidAction.EXECUTE);
        seed = level.random.nextLong();
        shape = ring.shape();
        centre = ring.centre();
        // The burst third leaves the world now: from here it is the clients' to draw, where it stood at first.
        shattering = WellheadMeltdown.permitted(level, centre, 4.0F,
                SundererRing.shatter(ring, AWConfig.SUNDERER_SHATTER.get(), seed));
        List<BlockState> states = new ArrayList<>(shattering.size());
        for (BlockPos pos : shattering) {
            states.add(level.getBlockState(pos));
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        lifted = states;
        List<BlockPos> standing = new ArrayList<>(ring.frame());
        standing.removeAll(shattering);
        frame = standing;
        boolean hand = level.random.nextDouble() < AWConfig.SUNDERER_HAND_CHANCE.get();
        grip = hand ? SundererRing.grip(ring, shattering, seed) : null;
        taken = false;
        sequence = new SundererSequence(hand && grip != null);
        failure = SundererFailure.NONE;
        holdTimer = 0;
        level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5F, 0.5F);
        Trace.always(Trace.RIFTING, "sunderer at {} fired: opening {}x{}, tear at {}, {} of {} frame to burst, hand {}",
                Trace.pos(worldPosition), shape.width(), shape.height(), Trace.pos(centre), shattering.size(),
                frame.size(), sequence.hand());
        tell(level, ClientboundSundererCuePacket.RUNNING);
        setChanged();
        sendData();
    }

    private void refuse(ServerLevel level, SundererFailure why) {
        failure = why;
        level.playSound(null, worldPosition, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 1.0F, 0.6F);
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(worldPosition) <= TOLD * TOLD) {
                AWLang.translate(why.translationKey()).style(ChatFormatting.RED).sendStatus(player);
            }
        }
        Trace.log(Trace.RIFTING, "sunderer at {} refused: {}", Trace.pos(worldPosition), why.getSerializedName());
        setChanged();
        sendData();
    }

    /** A click: why it would not fire, or that it would. */
    void report(Player player) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (running()) {
            AWLang.translate("sunderer.running").style(ChatFormatting.LIGHT_PURPLE).sendStatus(player);
            return;
        }
        SundererSurvey.Result survey = SundererSurvey.full(server, this);
        failure = survey.failure();
        sendData();
        if (survey.ok()) {
            AWLang.translate("sunderer.ready").style(ChatFormatting.GREEN).sendStatus(player);
        } else {
            AWLang.translate(survey.failure().translationKey()).style(ChatFormatting.RED).sendStatus(player);
        }
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void onLoad() {
        super.onLoad();
        if (AWConfig.SERVER_SPEC.isLoaded()) {
            tank.setCapacity(Math.max(1, AWConfig.SUNDERER_CHARGE.get()));
        }
        if (sequence != null) {
            sequence.resume();
        }
    }

    @Override
    public void tick() {
        // Before super.tick(): a loose Sunderer can be aboard a hull, and a block aboard one is still ticking the
        // tick after Sable has wiped its plot - see Airship.orphaned.
        if (Airship.orphaned(this)) {
            return;
        }
        super.tick();
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (sequence == null) {
            if (++secondTimer >= 20) {
                secondTimer = 0;
                idleSecond(server);
            }
            return;
        }
        run(server);
    }

    /** Once a second while it waits: what the goggles say, how brightly it is lit, and what a comparator reads. */
    private void idleSecond(ServerLevel level) {
        SundererFailure now = SundererSurvey.own(level, this).failure();
        if (now != failure) {
            failure = now;
            sendData();
        }
        int stage = tank.getFluidAmount() <= 0 ? 0
                : (int) Math.max(1L, Math.min(3L, Math.round(3.0D * tank.getFluidAmount() / Math.max(1, tank.getCapacity()))));
        light(stage);
        tellComparators();
    }

    private void run(ServerLevel level) {
        SundererSequence running = sequence;
        if (running == null || shape == null) {
            sequence = null;
            return;
        }
        if (holdTimer-- <= 0) {
            holdTimer = Holds.REACH_RENEW_INTERVAL;
            Holds.holdGround(level, centre, 1);
        }
        boolean turning = Math.abs(getSpeed()) >= AWConfig.SUNDERER_MINIMUM_SPEED.get();
        SundererSequence.Beat before = running.beat();
        SundererSequence.Step step = running.tick(new SundererSequence.Conditions(turning, ringWhole(level)), RESUME_GRACE);
        if (!turning && step == SundererSequence.Step.HELD) {
            Trace.log(Trace.RIFTING, "sunderer at {} held at tick {} for its shaft: speed {}, stalled {}",
                    Trace.pos(worldPosition), running.elapsed(), getSpeed(), running.stalled());
        }
        switch (step) {
            case MISFIRED -> {
                misfire(level);
                return;
            }
            case RUPTURED -> {
                tell(level, ClientboundSundererCuePacket.RUPTURED);
                sequence = null;
                SundererBreach.rupture(level, worldPosition, centre, frame);
                return;
            }
            default -> {
            }
        }
        boolean hand = running.hand();
        int elapsed = running.elapsed();
        light(SundererChoreography.lightStage(elapsed, hand));
        SundererBreach.strain(level, ring(), SundererChoreography.pull(elapsed, hand), level.getGameTime());

        if (hand && elapsed == SundererSequence.handStart() + 1) {
            AWCriteria.sunderer(level, centre, SundererTrigger.WAVED, SundererBreach.WITNESS);
            level.playSound(null, centre, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 1.4F, 0.5F);
        }
        if (hand && !taken && grip != null
                && SundererChoreography.handBeat(elapsed, true) >= RiftHandPose.TAKES) {
            taken = true;
            SundererBreach.take(level, grip, centre);
            List<BlockPos> left = new ArrayList<>(frame);
            left.remove(grip);
            frame = left;
            setChanged();
        }
        if (SundererChoreography.torn(elapsed, hand)) {
            // The clients are told first, while there is still a Sunderer to have told them: they play the rest
            // of the breach on their own clock.
            tell(level, ClientboundSundererCuePacket.RUNNING);
            sequence = null;
            SundererBreach.tear(level, worldPosition, centre);
            return;
        }
        if (running.beat() != before || elapsed % 20 == 0) {
            tell(level, ClientboundSundererCuePacket.RUNNING);
        }
        tellComparators();
        setChanged();
    }

    private void misfire(ServerLevel level) {
        int back = SundererSequence.refund(AWConfig.SUNDERER_CHARGE.get());
        tell(level, ClientboundSundererCuePacket.MISFIRED);
        sequence = null;
        tank.fill(new FluidStack(AWFluids.RIFT_ESSENCE.get(), back), IFluidHandler.FluidAction.EXECUTE);
        putBack(level);
        level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.5F, 0.6F);
        Trace.always(Trace.RIFTING, "sunderer at {} misfired while reaching: {} mB handed back",
                Trace.pos(worldPosition), back);
        setChanged();
        sendData();
    }

    /**
     * A misfire puts the burst third back where it stood - into air only, so a block somebody set in a gap since is
     * left alone. The clients were still drawing them in place, so nothing is seen to jump.
     */
    private void putBack(ServerLevel level) {
        for (int index = 0; index < shattering.size() && index < lifted.size(); index++) {
            BlockPos pos = shattering.get(index);
            if (level.isLoaded(pos) && level.getBlockState(pos).isAir()) {
                level.setBlock(pos, lifted.get(index), Block.UPDATE_ALL);
            }
        }
        shattering = List.of();
        lifted = List.of();
    }

    /** Whether every block of the ring it fired in that still stands is still frame. The Sunderer is this block. */
    private boolean ringWhole(ServerLevel level) {
        for (BlockPos pos : frame) {
            if (level.isLoaded(pos) && !(level.getBlockState(pos).getBlock() instanceof RiftGateFrameBlock)) {
                return false;
            }
        }
        return true;
    }

    private SundererRing.Ring ring() {
        return new SundererRing.Ring(shape, worldPosition, frame);
    }

    /** Sets its light, through Create's switch so the shaft is never taken out of its network for it. */
    private void light(int stage) {
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        if (state.getValue(RiftSundererBlock.CHARGE) != stage) {
            KineticBlockEntity.switchToBlockState(level, worldPosition, state.setValue(RiftSundererBlock.CHARGE, stage));
        }
    }

    private void tellComparators() {
        int signal = comparatorSignal();
        if (level != null && signal != lastSignal) {
            lastSignal = signal;
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    /** Tells everyone near enough where the tear has got to. */
    private void tell(ServerLevel level, int outcome) {
        SundererSequence running = sequence;
        if (running == null || shape == null) {
            return;
        }
        int range = AWConfig.SUNDERER_SPECTACLE_RANGE.get();
        Direction facing = getBlockState().getValue(RiftSundererBlock.HORIZONTAL_FACING);
        ClientboundSundererCuePacket cue = new ClientboundSundererCuePacket(worldPosition, shape, facing, centre,
                grip == null ? worldPosition : grip, shattering, running.elapsed(), seed, running.hand(), outcome, range,
                false);
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(centre) <= (double) range * range) {
                PacketDistributor.sendToPlayer(player, cue);
            }
        }
    }

    /**
     * Taken away mid-tear: broken, blown up, or pulled out by anything that could. Before it committed, that is a
     * misfire with nothing to hand back to; after, the ring was holding something that has nowhere to go.
     */
    void onRemoved() {
        SundererSequence running = sequence;
        if (running == null || !(level instanceof ServerLevel server)) {
            return;
        }
        sequence = null;
        if (running.beat().committed()) {
            tell(server, ClientboundSundererCuePacket.RUPTURED);
            SundererBreach.rupture(server, worldPosition, centre, frame);
        } else {
            sequence = running;
            tell(server, ClientboundSundererCuePacket.MISFIRED);
            sequence = null;
            putBack(server);
        }
    }

    // -------------------------------------------------------------------- nbt

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putString("Failure", failure.getSerializedName());
        tag.putBoolean("Powered", powered);
        SundererSequence running = sequence;
        if (running != null && shape != null) {
            CompoundTag tear = new CompoundTag();
            tear.putBoolean("Hand", running.hand());
            tear.putInt("Elapsed", running.elapsed());
            tear.putInt("Stalled", running.stalled());
            tear.putLong("Seed", seed);
            tear.put("Shape", shape.save());
            tear.putLong("Centre", centre.asLong());
            tear.put("Frame", positions(frame));
            tear.put("Shattering", positions(shattering));
            ListTag states = new ListTag();
            for (BlockState state : lifted) {
                states.add(NbtUtils.writeBlockState(state));
            }
            tear.put("Lifted", states);
            if (grip != null) {
                tear.putLong("Grip", grip.asLong());
            }
            tear.putBoolean("Taken", taken);
            tag.put("Tear", tear);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        tank.setCapacity(Math.max(1, AWConfig.SERVER_SPEC.isLoaded() ? AWConfig.SUNDERER_CHARGE.get() : 4_000));
        tank.readFromNBT(registries, tag.getCompound("Tank"));
        failure = SundererFailure.NO_RING;
        for (SundererFailure candidate : SundererFailure.values()) {
            if (candidate.getSerializedName().equals(tag.getString("Failure"))) {
                failure = candidate;
            }
        }
        powered = tag.getBoolean("Powered");
        if (tag.contains("Tear")) {
            CompoundTag tear = tag.getCompound("Tear");
            sequence = new SundererSequence(tear.getBoolean("Hand"), tear.getInt("Elapsed"), tear.getInt("Stalled"));
            seed = tear.getLong("Seed");
            shape = RiftGateShape.load(tear.getCompound("Shape"));
            centre = BlockPos.of(tear.getLong("Centre"));
            frame = positions(tear.getList("Frame", Tag.TAG_LONG));
            shattering = positions(tear.getList("Shattering", Tag.TAG_LONG));
            List<BlockState> states = new ArrayList<>();
            for (Tag entry : tear.getList("Lifted", Tag.TAG_COMPOUND)) {
                states.add(NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), (CompoundTag) entry));
            }
            lifted = states;
            grip = tear.contains("Grip") ? BlockPos.of(tear.getLong("Grip")) : null;
            taken = tear.getBoolean("Taken");
        } else {
            sequence = null;
        }
    }

    private static ListTag positions(List<BlockPos> positions) {
        ListTag list = new ListTag();
        for (BlockPos pos : positions) {
            list.add(LongTag.valueOf(pos.asLong()));
        }
        return list;
    }

    private static List<BlockPos> positions(ListTag list) {
        List<BlockPos> positions = new ArrayList<>(list.size());
        for (Tag entry : list) {
            if (entry instanceof LongTag value) {
                positions.add(BlockPos.of(value.getAsLong()));
            }
        }
        return positions;
    }

    // --------------------------------------------------------------- goggles

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        AWLang.translate("gui.goggles.rift_sunderer").forGoggles(tooltip);
        AWLang.translate("gui.rift_sunderer.charge", AWLang.count(tank.getFluidAmount()), AWLang.count(tank.getCapacity()))
                .style(ChatFormatting.LIGHT_PURPLE).forGoggles(tooltip, 1);
        if (running()) {
            AWLang.translate("sunderer.running").style(ChatFormatting.GOLD).forGoggles(tooltip, 1);
        } else if (failure == SundererFailure.NONE) {
            AWLang.translate("sunderer.ready").style(ChatFormatting.GREEN).forGoggles(tooltip, 1);
        } else {
            AWLang.translate(failure.translationKey()).style(ChatFormatting.RED).forGoggles(tooltip, 1);
        }
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        return true;
    }
}
