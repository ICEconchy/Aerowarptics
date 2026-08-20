package uk.co.iceconchy.aerowarptics.registry;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorBlock;
import uk.co.iceconchy.aerowarptics.astrolabe.AstrolabeBlock;
import uk.co.iceconchy.aerowarptics.siphon.SpatialSiphonBlock;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlock;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;

import java.util.EnumMap;
import java.util.Map;

/** Block registration. Every drive tier shares one block class and one block entity type. */
public final class AWBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AeroWarptics.MODID);

    /** One block per {@link RiftDriveTier}, keyed so new tiers need no extra wiring. */
    public static final Map<RiftDriveTier, DeferredBlock<RiftDriveBlock>> RIFT_DRIVES =
            new EnumMap<>(RiftDriveTier.class);

    /**
     * One cell of the chart table. Nine of them make one.
     *
     * <p>Registered as a single block rather than a frame-and-core pair because every cell is
     * physically the same object; which one is the middle is a fact about the arrangement, not about
     * the block, and baking it into the registry would mean a table could be built wrong.
     */
    public static final DeferredBlock<AstrolabeBlock> ASTROLABE = BLOCKS.register("astrolabe",
            () -> new AstrolabeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_LIGHT_BLUE)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.COPPER)
                    .lightLevel(state -> state.getValue(AstrolabeBlock.FORMED) ? 6 : 0)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<SpatialSiphonBlock> SPATIAL_SIPHON = BLOCKS.register("spatial_siphon",
            () -> new SpatialSiphonBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.5F, 8.0F)
                    .sound(SoundType.COPPER)
                    .lightLevel(state -> 4)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<WarpAnchorBlock> WARP_ANCHOR = BLOCKS.register("warp_anchor",
            () -> new WarpAnchorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_LIGHT_BLUE)
                    .strength(3.5F, 8.0F)
                    .sound(SoundType.COPPER)
                    .lightLevel(state -> 7)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    static {
        for (RiftDriveTier tier : RiftDriveTier.values()) {
            RIFT_DRIVES.put(tier, BLOCKS.register(tier.blockName(),
                    () -> new RiftDriveBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_ORANGE)
                            .strength(4.0F, 10.0F)
                            .sound(SoundType.COPPER)
                            .lightLevel(state -> 5)
                            .noOcclusion()
                            .requiresCorrectToolForDrops(), tier)));
        }
    }

    private AWBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
