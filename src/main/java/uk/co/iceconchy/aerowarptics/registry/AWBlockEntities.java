package uk.co.iceconchy.aerowarptics.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorBlockEntity;
import uk.co.iceconchy.aerowarptics.astrolabe.AstrolabeBlockEntity;
import uk.co.iceconchy.aerowarptics.siphon.SpatialSiphonBlockEntity;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlock;
import uk.co.iceconchy.aerowarptics.gate.RiftGateBlockEntity;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;

/** Block entity type registration. */
public final class AWBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AeroWarptics.MODID);

    /**
     * A single type covers every drive tier.
     *
     * <p>The tier is read back off the block state, so a chunk that loads a Mk III drive rebuilds a
     * Mk III block entity without a separate registry entry per tier.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RiftDriveBlockEntity>> RIFT_DRIVE =
            BLOCK_ENTITIES.register("rift_drive", () -> BlockEntityType.Builder.of(
                            (pos, state) -> new RiftDriveBlockEntity(pos, state,
                                    state.getBlock() instanceof RiftDriveBlock drive ? drive.tier() : RiftDriveTier.MK_I),
                            driveBlocks())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AstrolabeBlockEntity>> ASTROLABE =
            BLOCK_ENTITIES.register("astrolabe", () -> BlockEntityType.Builder.of(
                            AstrolabeBlockEntity::new, AWBlocks.ASTROLABE.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpatialSiphonBlockEntity>> SPATIAL_SIPHON =
            BLOCK_ENTITIES.register("spatial_siphon", () -> BlockEntityType.Builder.of(
                            SpatialSiphonBlockEntity::new, AWBlocks.SPATIAL_SIPHON.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RiftGateBlockEntity>> RIFT_GATE =
            BLOCK_ENTITIES.register("rift_gate", () -> BlockEntityType.Builder.of(
                            RiftGateBlockEntity::new, AWBlocks.RIFT_GATE.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WarpAnchorBlockEntity>> WARP_ANCHOR =
            BLOCK_ENTITIES.register("warp_anchor", () -> BlockEntityType.Builder.of(
                            WarpAnchorBlockEntity::new, AWBlocks.WARP_ANCHOR.get())
                    .build(null));

    private AWBlockEntities() {
    }

    private static Block[] driveBlocks() {
        return AWBlocks.RIFT_DRIVES.values().stream().map(holder -> (Block) holder.get()).toArray(Block[]::new);
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
