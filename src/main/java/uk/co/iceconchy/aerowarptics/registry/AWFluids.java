package uk.co.iceconchy.aerowarptics.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import uk.co.iceconchy.aerowarptics.AeroWarptics;

/**
 * Rift Essence: what a Spatial Siphon pulls out of a warp.
 *
 * <p>Registered as a proper fluid rather than a counter on a block, because everything a player will
 * reasonably try - putting a pipe on the siphon, filling a tank, scooping a bucket - only works if
 * there is a real fluid behind it. Nothing consumes it yet; it is the raw material dimensional travel
 * will be built on.
 *
 * <p>It is deliberately thin and slow. A pool of it left on the ground behaves like something that
 * does not really want to be here, which is the whole idea.
 */
public final class AWFluids {

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, AeroWarptics.MODID);

    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, AeroWarptics.MODID);

    /** How much essence one bucket holds, in millibuckets. The vanilla figure, for pipe compatibility. */
    public static final int BUCKET = 1000;

    public static final DeferredHolder<FluidType, FluidType> RIFT_ESSENCE_TYPE =
            FLUID_TYPES.register("rift_essence", () -> new FluidType(FluidType.Properties.create()
                    .density(420)
                    .viscosity(720)
                    .lightLevel(9)
                    .temperature(240)
                    .canConvertToSource(false)
                    .canSwim(true)
                    .canDrown(true)
                    .sound(SoundActions.BUCKET_FILL, net.minecraft.sounds.SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> RIFT_ESSENCE =
            FLUIDS.register("rift_essence", () -> new BaseFlowingFluid.Source(properties()));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> RIFT_ESSENCE_FLOWING =
            FLUIDS.register("flowing_rift_essence", () -> new BaseFlowingFluid.Flowing(properties()));

    public static final DeferredBlock<LiquidBlock> RIFT_ESSENCE_BLOCK =
            AWBlocks.BLOCKS.register("rift_essence", () -> new LiquidBlock(RIFT_ESSENCE.get(),
                    BlockBehaviour.Properties.ofFullCopy(Blocks.WATER)
                            .mapColor(MapColor.COLOR_PURPLE)
                            .lightLevel(state -> 9)
                            .sound(SoundType.EMPTY)
                            .pushReaction(PushReaction.DESTROY)
                            .noLootTable()));

    private AWFluids() {
    }

    /**
     * The shared properties both the still and the flowing fluid are built from.
     *
     * <p>Built fresh for each rather than shared as a field: the properties object is consumed by the
     * fluid it is handed to, and the registry entries it points at are suppliers, so both halves
     * resolve the same pair without either needing the other to exist yet.
     */
    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(RIFT_ESSENCE_TYPE, RIFT_ESSENCE, RIFT_ESSENCE_FLOWING)
                .block(RIFT_ESSENCE_BLOCK)
                .bucket(AWItems.RIFT_ESSENCE_BUCKET)
                // Slow and short-reaching: it creeps rather than floods.
                .slopeFindDistance(2)
                .levelDecreasePerBlock(2)
                .tickRate(30);
    }

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
    }
}
