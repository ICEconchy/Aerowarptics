package uk.co.iceconchy.aerowarptics.rifting;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
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
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.airship.DrawnAboard;
import uk.co.iceconchy.aerowarptics.client.AWClientHooks;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.registry.AWFluids;
import uk.co.iceconchy.aerowarptics.registry.AWSounds;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.List;

/**
 * A Rift Brazier's essence, and whether it is burning.
 *
 * <p>It burns by the second while it has a signal, not by the item a fan blows past it: Create's fan tells
 * a processing type what it is processing and never which catalyst it passed, so there is no way to charge
 * the brazier that did the work. Paid up front, a second at a time - a brazier with too little left for the
 * next second goes out, and one given more lights again at the next.
 */
public class RiftBrazierBlockEntity extends SmartBlockEntity implements GeoBlockEntity, IHaveGoggleInformation {

    /** One bucket. At the default burn that is over eight minutes of rift. */
    public static final int CAPACITY = AWFluids.BUCKET;

    private static final int BURN_INTERVAL = 20;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.rift_brazier.idle");
    private static final RawAnimation LIT = RawAnimation.begin().thenLoop("animation.rift_brazier.lit");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

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

    private int burnTimer;

    /** How far caught its fire is on a client, 0..1 - it catches quickly and dies down, rather than blinking. */
    private float flame;
    private float lastFlame;

    public RiftBrazierBlockEntity(BlockPos pos, BlockState state) {
        super(AWBlockEntities.RIFT_BRAZIER.get(), pos, state);
    }

    /** Asks a client to draw this machine, in case it arrived after the blocks round it - see {@link DrawnAboard}. */
    @Override
    public void onLoad() {
        super.onLoad();
        DrawnAboard.redraw(this);
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

    public boolean isLit() {
        return getBlockState().getValue(RiftBrazierBlock.LIT);
    }

    public boolean isPowered() {
        return getBlockState().getValue(RiftBrazierBlock.POWERED);
    }

    /** How far caught the rift fire is, 0..1 between ticks, for the renderer. Only a client keeps it. */
    public float flame(float partialTick) {
        return lastFlame + (flame - lastFlame) * partialTick;
    }

    /** Whether the fire caught this very tick, for the burst of sparks it catches with. */
    public boolean justCaught() {
        return lastFlame <= 0.0F && flame > 0.0F;
    }

    /** Tall enough for the flames over it, and a little past its sides for their haze - see {@link RiftFlame}. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).expandTowards(0.0D, RiftFlame.REACH_UP, 0.0D).inflate(RiftFlame.REACH_OUT);
    }

    /** 0..1 how full the firebox is, for the renderer. */
    public float fillLevel() {
        return tank.getFluidAmount() / (float) CAPACITY;
    }

    @Override
    public void tick() {
        // Before super.tick(): nothing runs on a block that is no longer there - see Airship.orphaned.
        if (Airship.orphaned(this)) {
            return;
        }
        super.tick();
        if (level == null) {
            return;
        }
        if (level.isClientSide) {
            lastFlame = flame;
            flame = (float) RiftFlame.step(flame, isLit());
            AWClientHooks.tickBrazier(this);
            return;
        }
        if (!isPowered()) {
            burnTimer = 0;
            if (isLit()) {
                setLit(false, "switched off");
            }
            return;
        }
        if (--burnTimer > 0) {
            return;
        }
        burnTimer = BURN_INTERVAL;
        int cost = AWConfig.BRAZIER_BURN_RATE.get();
        boolean paid = cost <= 0 || tank.drain(cost, IFluidHandler.FluidAction.SIMULATE).getAmount() >= cost;
        if (paid && cost > 0) {
            tank.drain(cost, IFluidHandler.FluidAction.EXECUTE);
        }
        if (paid != isLit()) {
            setLit(paid, paid ? "burning " + cost + " mB/s" : "out of essence (" + tank.getFluidAmount() + " mB, needs " + cost + ")");
        }
    }

    private void setLit(boolean lit, String why) {
        if (level == null) {
            return;
        }
        level.setBlock(worldPosition, getBlockState().setValue(RiftBrazierBlock.LIT, lit), Block.UPDATE_ALL);
        Trace.log(Trace.RIFTING, "brazier at {} {}: {}", Trace.pos(worldPosition), lit ? "lit" : "went out", why);
        level.playSound(null, worldPosition, lit ? AWSounds.RIFT_OPEN.get() : AWSounds.WARP_EXIT.get(),
                SoundSource.BLOCKS, 0.4F, lit ? 1.5F : 1.8F);
    }

    // -------------------------------------------------------------------- nbt

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putInt("BurnTimer", burnTimer);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        tank.readFromNBT(registries, tag.getCompound("Tank"));
        burnTimer = tag.getInt("BurnTimer");
    }

    // --------------------------------------------------------------- goggles

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        AWLang.translate("gui.goggles.rift_brazier").forGoggles(tooltip);
        AWLang.translate("gui.rift_brazier.contents", AWLang.count(tank.getFluidAmount()), AWLang.count(CAPACITY))
                .style(tank.getFluidAmount() > 0 ? ChatFormatting.AQUA : ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
        String state = isLit() ? "gui.rift_brazier.lit" : isPowered() ? "gui.rift_brazier.dry" : "gui.rift_brazier.unlit";
        AWLang.translate(state)
                .style(isLit() ? ChatFormatting.LIGHT_PURPLE : isPowered() ? ChatFormatting.RED : ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
        return true;
    }

    // -------------------------------------------------------------- geckolib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "brazier", 8, state -> {
            state.getController().setAnimation(isLit() ? LIT : IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
