package uk.co.iceconchy.aerowarptics.modulator;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.registry.AWFluids;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.List;

/**
 * The Rift Modulator: a module for a Rift Drive that lets its pilot dress the rift the drive tears.
 *
 * <p>It does nothing on its own. Everything it offers - a chosen colour, a chosen look - is read by
 * the drive standing beside it and used in place of the drive's own tier palette wherever that palette
 * would otherwise show up: the aperture, the corridor wash, the exit shockwave. Break the link, or run
 * the tank dry, and the drive falls straight back to its own colours - a Modulator is a filter over a
 * warp, never a second thing the warp depends on to happen at all.
 *
 * <h2>Finding its drive</h2>
 * Resolved by looking, exactly the way {@code sable$tick} answers "which airship am I on" for a
 * drive: the Modulator scans its six neighbours for a {@link RiftDriveBlockEntity} and uses whichever
 * one it finds first. Cached for the length of one tick, the same shape as
 * {@code RiftChuteBlockEntity}'s partner cache, since the goggles, the screen and the drive's own
 * lookup of this block can all ask in the same tick.
 *
 * <h2>What it costs</h2>
 * Rift Essence, spent in small charges at a fixed interval, and only while the linked drive is
 * actually running a warp sequence - an idle drive costs the Modulator nothing, the same rule the Rift
 * Gate applies to its own upkeep. {@link #active()} is what the drive actually reads: true only while
 * the last charge succeeded, so an empty tank falls back to the tier's own colours rather than holding
 * the last colour it could afford.
 */
public class RiftModulatorBlockEntity extends SmartBlockEntity implements GeoBlockEntity, IHaveGoggleInformation {

    /** How much essence a Modulator holds. Small: this is a filter, not a reservoir. */
    public static final int CAPACITY = AWFluids.BUCKET / 2;

    /** What an unconfigured Modulator is set to - the mod's own stock purple, so it changes nothing. */
    public static final int DEFAULT_COLOUR = 0x9B_6B_FF;

    /** Effect intensity is a dial, not a toggle - it never turns a rift's flourish off outright. */
    public static final float MIN_INTENSITY = 0.25F;
    public static final float MAX_INTENSITY = 2.0F;
    public static final float DEFAULT_INTENSITY = 1.0F;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.rift_modulator.idle");

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

    private int colour = DEFAULT_COLOUR;
    /**
     * The rift's rim colour. Defaults to {@link #colour}'s own default so an unconfigured Modulator
     * draws a flat, single-tone rift exactly as an undecorated drive would - the gradient only appears
     * once a player has actually chosen a second swatch.
     */
    private int accentColour = DEFAULT_COLOUR;
    private RiftModulatorTheme theme = RiftModulatorTheme.STANDARD;
    private float intensity = DEFAULT_INTENSITY;

    /** Ticks until the next upkeep charge is due. Charged the moment the drive starts warping. */
    private int upkeepTimer;

    /** Whether the last charge attempt succeeded - what {@link #activeColour()} actually reads. */
    private boolean active;

    /** The linked drive, resolved at most once a tick. See the class doc for why this is cached. */
    @Nullable
    private RiftDriveBlockEntity cachedDrive;
    private long cachedDriveTick = Long.MIN_VALUE;

    public RiftModulatorBlockEntity(BlockPos pos, BlockState state) {
        super(AWBlockEntities.RIFT_MODULATOR.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // No Create behaviours: this is not a kinetic block and nothing feeds it items or fluid
        // through a face - the tank is filled by a pipe against the capability, same as the probe's.
    }

    // ------------------------------------------------------------------ linking

    /**
     * The Rift Drive this module is dressing, or {@code null} when it is not sitting beside one.
     *
     * <p>Whichever neighbour is found first. Two drives either side of one Modulator is not a
     * configuration this mod tries to arbitrate more cleverly than that - it is an odd thing to build
     * on purpose, and "the module answers to one of them, consistently" is a perfectly good answer to
     * it.
     */
    @Nullable
    public RiftDriveBlockEntity linkedDrive() {
        if (level == null) {
            return null;
        }
        long now = level.getGameTime();
        if (cachedDriveTick == now) {
            return cachedDrive != null && !cachedDrive.isRemoved() ? cachedDrive : null;
        }
        cachedDriveTick = now;
        cachedDrive = findAdjacentDrive();
        return cachedDrive;
    }

    @Nullable
    private RiftDriveBlockEntity findAdjacentDrive() {
        if (level == null) {
            return null;
        }
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(direction)) instanceof RiftDriveBlockEntity drive) {
                return drive;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        // Before super.tick(), not after: Create runs initialize(), lazyTick() and every
        // behaviour from there, and those touch the level too. Nothing runs on a block that is
        // no longer there - see Airship.orphaned.
        if (Airship.orphaned(this)) {
            return;
        }
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }
        RiftDriveBlockEntity drive = linkedDrive();
        boolean operating = drive != null && drive.state().isSequenceRunning();
        if (!operating) {
            // Reset rather than merely pause: a drive that stopped warping and starts again gets a
            // fresh charge attempt straight away instead of waiting out whatever was left of the
            // interval when it stopped - the essence a Modulator spends is for the warp it dresses,
            // not a subscription that runs whether one is happening or not.
            upkeepTimer = 0;
            if (active) {
                active = false;
                setChanged();
            }
            return;
        }
        if (--upkeepTimer > 0) {
            return;
        }
        upkeepTimer = Math.max(1, AWConfig.MODULATOR_UPKEEP_INTERVAL.get());
        int cost = AWConfig.MODULATOR_UPKEEP_COST.get();
        boolean afforded = cost <= 0 || tank.drain(cost, IFluidHandler.FluidAction.SIMULATE).getAmount() >= cost;
        if (afforded && cost > 0) {
            tank.drain(cost, IFluidHandler.FluidAction.EXECUTE);
        }
        if (afforded != active) {
            active = afforded;
            setChanged();
            sendData();
        }
    }

    // -------------------------------------------------------------- what the drive reads

    public boolean active() {
        return active;
    }

    /** The configured colour if the Modulator is linked and currently affording its upkeep, else -1. */
    public int activeColour() {
        return linkedDrive() != null && active ? colour : -1;
    }

    /** The configured rim colour under the same condition as {@link #activeColour()}, else -1. */
    public int activeAccentColour() {
        return linkedDrive() != null && active ? accentColour : -1;
    }

    /** The configured theme's ordinal under the same condition as {@link #activeColour()}, else -1. */
    public int activeThemeOrdinal() {
        return linkedDrive() != null && active ? theme.ordinal() : -1;
    }

    /**
     * The configured intensity under the same condition as {@link #activeColour()}, else the neutral
     * {@link #DEFAULT_INTENSITY} - unlike the colour and theme overrides there is no "off" value to
     * signal with a sentinel, since 1.0 already <em>is</em> off.
     */
    public float activeIntensity() {
        return linkedDrive() != null && active ? intensity : DEFAULT_INTENSITY;
    }

    // ---------------------------------------------------------------- configuration

    public int colour() {
        return colour;
    }

    public int accentColour() {
        return accentColour;
    }

    public RiftModulatorTheme theme() {
        return theme;
    }

    public float intensity() {
        return intensity;
    }

    public IFluidHandler tank() {
        return tank;
    }

    public FluidStack contents() {
        return tank.getFluid();
    }

    public void setColour(int newColour) {
        if (colour == newColour) {
            return;
        }
        colour = newColour;
        setChanged();
        sendData();
    }

    public void setAccentColour(int newColour) {
        if (accentColour == newColour) {
            return;
        }
        accentColour = newColour;
        setChanged();
        sendData();
    }

    public void setTheme(RiftModulatorTheme newTheme) {
        if (theme == newTheme) {
            return;
        }
        theme = newTheme;
        setChanged();
        sendData();
    }

    public void setIntensity(float newIntensity) {
        float clamped = Math.max(MIN_INTENSITY, Math.min(MAX_INTENSITY, newIntensity));
        if (intensity == clamped) {
            return;
        }
        intensity = clamped;
        setChanged();
        sendData();
    }

    // ------------------------------------------------------------- animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "modulator", 5, state -> {
            state.setAnimation(IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    // --------------------------------------------------------------- goggles

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        AWLang.translate("gui.goggles.rift_modulator").forGoggles(tooltip);
        RiftDriveBlockEntity drive = linkedDrive();
        if (drive == null) {
            AWLang.translate("gui.rift_modulator.not_linked")
                    .style(ChatFormatting.RED).forGoggles(tooltip, 1);
        } else {
            AWLang.translate(active ? "gui.rift_modulator.active" : "gui.rift_modulator.idle")
                    .style(active ? ChatFormatting.GRAY : ChatFormatting.RED).forGoggles(tooltip, 1);
        }
        AWLang.translate("gui.rift_modulator.essence", AWLang.count(tank.getFluidAmount()), AWLang.count(CAPACITY))
                .style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        AWLang.translate(theme.translationKey()).style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        AWLang.translate("gui.rift_modulator.intensity_reading", AWLang.percent(intensity))
                .style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        return true;
    }

    // ------------------------------------------------------------------- nbt

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putInt("Colour", colour);
        tag.putInt("AccentColour", accentColour);
        tag.putInt("Theme", theme.ordinal());
        tag.putFloat("Intensity", intensity);
        tag.putBoolean("Active", active);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        tank.readFromNBT(registries, tag.getCompound("Tank"));
        colour = tag.contains("Colour") ? tag.getInt("Colour") : DEFAULT_COLOUR;
        accentColour = tag.contains("AccentColour") ? tag.getInt("AccentColour") : colour;
        theme = RiftModulatorTheme.byIndex(tag.getInt("Theme"));
        intensity = tag.contains("Intensity")
                ? Math.max(MIN_INTENSITY, Math.min(MAX_INTENSITY, tag.getFloat("Intensity")))
                : DEFAULT_INTENSITY;
        active = tag.getBoolean("Active");
    }

    /** Whether this block is sitting on the ground rather than bolted to an airship's hull. */
    public boolean aboard() {
        RiftDriveBlockEntity drive = linkedDrive();
        return drive != null && drive.airship() != null;
    }

    /** Used by the packet handler's reach check when this Modulator is standing on the ground. */
    public ServerLevel serverLevelOrNull() {
        return level instanceof ServerLevel serverLevel ? serverLevel : null;
    }
}
