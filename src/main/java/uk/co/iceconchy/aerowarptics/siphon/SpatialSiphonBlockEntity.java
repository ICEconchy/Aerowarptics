package uk.co.iceconchy.aerowarptics.siphon;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.registry.AWFluids;
import uk.co.iceconchy.aerowarptics.registry.AWSounds;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.List;

/**
 * The Spatial Siphon: a trap for whatever a rift leaves behind.
 *
 * <p>Passing a hull through folded space apparently sheds something, and this catches it. What it
 * catches is Rift Essence, and at present nothing consumes it - the siphon exists to accumulate the
 * raw material dimensional travel will be built on, so that by the time there is somewhere else to go,
 * a ship that has been going places already has some.
 *
 * <p>The yield is a lottery rather than a rate. A jump either shakes something loose or it does not,
 * and a longer throw widens the odds without guaranteeing anything - which keeps it a by-product of
 * travelling rather than a reason to bounce a ship between two anchors.
 */
public class SpatialSiphonBlockEntity extends SmartBlockEntity
        implements GeoBlockEntity, IHaveGoggleInformation {

    /** How much the vessel holds, in millibuckets. Four buckets: enough to be worth piping out. */
    public static final int CAPACITY = 4 * AWFluids.BUCKET;

    /** Millibuckets every completed jump is worth before distance is considered. */
    private static final int BASE_YIELD = 40;
    private static final int BASE_SPREAD = 160;

    /** Extra millibuckets a jump at {@link #REACH_REFERENCE} blocks can add, on top of the base. */
    private static final int REACH_BONUS = 300;
    private static final double REACH_REFERENCE = 4_000.0D;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.spatial_siphon.idle");
    private static final RawAnimation DRAWING = RawAnimation.begin().thenLoop("animation.spatial_siphon.drawing");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    /**
     * What the siphon is holding.
     *
     * <p>Only Rift Essence is accepted, so a player cannot use it as a general tank - and, more to the
     * point, so a pipe network cannot push water into the thing and dilute the point of it.
     */
    private final FluidTank tank = new FluidTank(CAPACITY,
            stack -> stack.getFluid() == AWFluids.RIFT_ESSENCE.get()) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide) {
                sendData();
            }
        }
    };

    /** Ticks left of the drawing-in animation, so a capture reads as an event rather than a number. */
    private int drawTicks;

    public SpatialSiphonBlockEntity(BlockPos pos, BlockState state) {
        super(AWBlockEntities.SPATIAL_SIPHON.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public IFluidHandler tank() {
        return tank;
    }

    public FluidStack contents() {
        return tank.getFluid();
    }

    /** 0..1 how full the vessel is, for the renderer and the tooltip. */
    public float fillLevel() {
        return tank.getFluidAmount() / (float) CAPACITY;
    }

    public boolean isDrawing() {
        return drawTicks > 0;
    }

    @Override
    public void tick() {
        super.tick();
        // A steady stream of motes pulled in from the rift for as long as a draw is running. Emitted
        // before the countdown ticks down so the last tick of a draw still shows, and only client-side
        // where there is a level to spawn particles into.
        if (drawTicks > 0 && level != null && level.isClientSide) {
            AWClientHooks.animateSiphonDraw(level, worldPosition, level.getRandom());
        }
        if (drawTicks > 0) {
            drawTicks--;
        }
    }

    /**
     * Takes a draught out of a completed warp.
     *
     * @param distance blocks the hull crossed, used only to widen the odds
     * @param random   the level's own generator, so the roll is part of world randomness
     * @return millibuckets actually captured, which is zero once the vessel is full
     */
    public int capture(double distance, RandomSource random) {
        double reach = Mth.clamp(distance / REACH_REFERENCE, 0.0D, 1.0D);
        int offered = BASE_YIELD + random.nextInt(BASE_SPREAD) + random.nextInt(1 + (int) (reach * REACH_BONUS));
        int accepted = tank.fill(new FluidStack(AWFluids.RIFT_ESSENCE.get(), offered), IFluidHandler.FluidAction.EXECUTE);
        if (accepted > 0) {
            drawTicks = 60;
            if (level != null && !level.isClientSide) {
                level.playSound(null, worldPosition, AWSounds.RIFT_OPEN.get(), SoundSource.BLOCKS,
                        0.35F, 1.7F);
            }
            sendData();
        }
        return accepted;
    }

    /** Millibuckets the vessel could still take. */
    public int room() {
        return Math.max(0, CAPACITY - tank.getFluidAmount());
    }

    /**
     * Takes what a Rift Fissure is handing over.
     *
     * <p>Separate from {@link #capture} because the two are different events wearing the same
     * plumbing. A capture is a lottery paid out at the end of a journey; this is a steady draw off a
     * tear that was already open, metered by the fissure rather than rolled for here - so this method
     * decides nothing and only reports what it managed to hold.
     *
     * @return millibuckets actually taken, which is less than offered once the vessel is full
     */
    public int acceptFromFissure(int millibuckets) {
        if (millibuckets <= 0) {
            return 0;
        }
        int accepted = tank.fill(new FluidStack(AWFluids.RIFT_ESSENCE.get(), millibuckets),
                IFluidHandler.FluidAction.EXECUTE);
        if (accepted <= 0) {
            return 0;
        }
        // Only at the moment it starts, not every tick of a draw that runs for half a minute.
        if (drawTicks <= 0 && level != null && !level.isClientSide) {
            level.playSound(null, worldPosition, AWSounds.RIFT_OPEN.get(), SoundSource.BLOCKS,
                    0.3F, 1.4F);
        }
        drawTicks = 10;
        return accepted;
    }

    /**
     * Runs every siphon aboard an airship after a completed jump.
     *
     * <p>Each rolls separately, so a ship carrying several fills faster - which is the obvious thing
     * to try and deserves to work, and costs nothing to allow given there is no rate to balance yet.
     *
     * @return total millibuckets captured across the hull
     */
    public static int harvest(Airship airship, double distance, RandomSource random) {
        int total = 0;
        for (SpatialSiphonBlockEntity siphon : airship.machines(SpatialSiphonBlockEntity.class)) {
            total += siphon.capture(distance, random);
        }
        return total;
    }

    // -------------------------------------------------------------------- nbt

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putInt("DrawTicks", drawTicks);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        tank.readFromNBT(registries, tag.getCompound("Tank"));
        drawTicks = tag.getInt("DrawTicks");
    }

    // --------------------------------------------------------------- goggles

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        AWLang.translate("gui.goggles.spatial_siphon").forGoggles(tooltip);
        AWLang.translate("gui.spatial_siphon.contents", AWLang.count(tank.getFluidAmount()), AWLang.count(CAPACITY))
                .style(tank.getFluidAmount() > 0 ? ChatFormatting.AQUA : ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
        return true;
    }

    // -------------------------------------------------------------- geckolib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "siphon", 6, state -> {
            state.getController().setAnimation(isDrawing() ? DRAWING : IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
