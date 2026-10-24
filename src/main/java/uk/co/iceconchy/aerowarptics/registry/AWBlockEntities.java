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
import uk.co.iceconchy.aerowarptics.chute.RiftChuteBlockEntity;
import uk.co.iceconchy.aerowarptics.detector.SpatialWaveDetectorBlockEntity;
import uk.co.iceconchy.aerowarptics.probe.RiftProbeBlockEntity;
import uk.co.iceconchy.aerowarptics.siphon.SpatialSiphonBlockEntity;
import uk.co.iceconchy.aerowarptics.drive.DriveExtenderBlockEntity;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlock;
import uk.co.iceconchy.aerowarptics.fissure.RiftFissureBlockEntity;
import uk.co.iceconchy.aerowarptics.gate.RiftGateBlockEntity;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;
import uk.co.iceconchy.aerowarptics.flotilla.RiftCouplerBlockEntity;
import uk.co.iceconchy.aerowarptics.modulator.RiftModulatorBlockEntity;
import uk.co.iceconchy.aerowarptics.stabiliser.SpatialStabiliserBlockEntity;

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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DriveExtenderBlockEntity>> DRIVE_EXTENDER =
            BLOCK_ENTITIES.register("drive_extender", () -> BlockEntityType.Builder.of(
                            DriveExtenderBlockEntity::new, AWBlocks.DRIVE_EXTENDER.get())
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RiftProbeBlockEntity>> RIFT_PROBE =
            BLOCK_ENTITIES.register("rift_probe", () -> BlockEntityType.Builder.of(
                            RiftProbeBlockEntity::new, AWBlocks.RIFT_PROBE.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RiftChuteBlockEntity>> RIFT_CHUTE =
            BLOCK_ENTITIES.register("rift_chute", () -> BlockEntityType.Builder.of(
                            RiftChuteBlockEntity::new, AWBlocks.RIFT_CHUTE.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RiftFissureBlockEntity>> RIFT_FISSURE =
            BLOCK_ENTITIES.register("rift_fissure", () -> BlockEntityType.Builder.of(
                            RiftFissureBlockEntity::new, AWBlocks.RIFT_FISSURE.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RiftModulatorBlockEntity>> RIFT_MODULATOR =
            BLOCK_ENTITIES.register("rift_modulator", () -> BlockEntityType.Builder.of(
                            RiftModulatorBlockEntity::new, AWBlocks.RIFT_MODULATOR.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RiftCouplerBlockEntity>> RIFT_COUPLER =
            BLOCK_ENTITIES.register("rift_coupler", () -> BlockEntityType.Builder.of(
                            RiftCouplerBlockEntity::new, AWBlocks.RIFT_COUPLER.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpatialStabiliserBlockEntity>> SPATIAL_STABILISER =
            BLOCK_ENTITIES.register("spatial_stabiliser", () -> BlockEntityType.Builder.of(
                            SpatialStabiliserBlockEntity::new, AWBlocks.SPATIAL_STABILISER.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<uk.co.iceconchy.aerowarptics.cooling.CoolingVentBlockEntity>> COOLING_VENT =
            BLOCK_ENTITIES.register("cooling_vent", () -> BlockEntityType.Builder.<uk.co.iceconchy.aerowarptics.cooling.CoolingVentBlockEntity>of(
                            uk.co.iceconchy.aerowarptics.cooling.CoolingVentBlockEntity::new, AWBlocks.COOLING_VENT.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<uk.co.iceconchy.aerowarptics.bond.RiftBondBlockEntity>> RIFT_BOND =
            BLOCK_ENTITIES.register("rift_bond", () -> BlockEntityType.Builder.<uk.co.iceconchy.aerowarptics.bond.RiftBondBlockEntity>of(
                            uk.co.iceconchy.aerowarptics.bond.RiftBondBlockEntity::new, AWBlocks.RIFT_BOND.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<uk.co.iceconchy.aerowarptics.berth.BerthBlockEntity>> BERTH =
            BLOCK_ENTITIES.register("berth", () -> BlockEntityType.Builder.<uk.co.iceconchy.aerowarptics.berth.BerthBlockEntity>of(
                            uk.co.iceconchy.aerowarptics.berth.BerthBlockEntity::new, AWBlocks.BERTH.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpatialWaveDetectorBlockEntity>> SPATIAL_WAVE_DETECTOR =
            BLOCK_ENTITIES.register("spatial_wave_detector", () -> BlockEntityType.Builder.of(
                            SpatialWaveDetectorBlockEntity::new, AWBlocks.SPATIAL_WAVE_DETECTOR.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WarpAnchorBlockEntity>> WARP_ANCHOR =
            BLOCK_ENTITIES.register("warp_anchor", () -> BlockEntityType.Builder.of(
                            WarpAnchorBlockEntity::new, AWBlocks.WARP_ANCHOR.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<uk.co.iceconchy.aerowarptics.rifting.RiftBrazierBlockEntity>> RIFT_BRAZIER =
            BLOCK_ENTITIES.register("rift_brazier", () -> BlockEntityType.Builder.of(
                            uk.co.iceconchy.aerowarptics.rifting.RiftBrazierBlockEntity::new, AWBlocks.RIFT_BRAZIER.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<uk.co.iceconchy.aerowarptics.collapse.CollapseCradleBlockEntity>> COLLAPSE_CRADLE =
            BLOCK_ENTITIES.register("collapse_cradle", () -> BlockEntityType.Builder.<uk.co.iceconchy.aerowarptics.collapse.CollapseCradleBlockEntity>of(
                            uk.co.iceconchy.aerowarptics.collapse.CollapseCradleBlockEntity::new, AWBlocks.COLLAPSE_CRADLE.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<uk.co.iceconchy.aerowarptics.plotter.RiftPlotterBlockEntity>> RIFT_PLOTTER =
            BLOCK_ENTITIES.register("rift_plotter", () -> BlockEntityType.Builder.<uk.co.iceconchy.aerowarptics.plotter.RiftPlotterBlockEntity>of(
                            uk.co.iceconchy.aerowarptics.plotter.RiftPlotterBlockEntity::new, AWBlocks.RIFT_PLOTTER.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<uk.co.iceconchy.aerowarptics.ward.RiftWardBlockEntity>> RIFT_WARD =
            BLOCK_ENTITIES.register("rift_ward", () -> BlockEntityType.Builder.<uk.co.iceconchy.aerowarptics.ward.RiftWardBlockEntity>of(
                            uk.co.iceconchy.aerowarptics.ward.RiftWardBlockEntity::new, AWBlocks.RIFT_WARD.get())
                    .build(null));

    /** Every relic machine's floor: one relay type for all three, since a base is the same machine whichever it is. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<uk.co.iceconchy.aerowarptics.collapse.RelicBaseBlockEntity>> RELIC_BASE =
            BLOCK_ENTITIES.register("relic_base", () -> BlockEntityType.Builder.<uk.co.iceconchy.aerowarptics.collapse.RelicBaseBlockEntity>of(
                            uk.co.iceconchy.aerowarptics.collapse.RelicBaseBlockEntity::new, AWBlocks.COLLAPSE_CRADLE_BASE.get(),
                            AWBlocks.RIFT_PLOTTER_BASE.get(), AWBlocks.RIFT_WARD_BASE.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<uk.co.iceconchy.aerowarptics.wellhead.RiftCollarBlockEntity>> RIFT_COLLAR =
            BLOCK_ENTITIES.register("rift_collar", () -> BlockEntityType.Builder.<uk.co.iceconchy.aerowarptics.wellhead.RiftCollarBlockEntity>of(
                            uk.co.iceconchy.aerowarptics.wellhead.RiftCollarBlockEntity::new, AWBlocks.RIFT_COLLAR.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<uk.co.iceconchy.aerowarptics.sunderer.RiftSundererBlockEntity>> RIFT_SUNDERER =
            BLOCK_ENTITIES.register("rift_sunderer", () -> BlockEntityType.Builder.<uk.co.iceconchy.aerowarptics.sunderer.RiftSundererBlockEntity>of(
                            uk.co.iceconchy.aerowarptics.sunderer.RiftSundererBlockEntity::new, AWBlocks.RIFT_SUNDERER.get())
                    .build(null));

    /** A Rift Wellhead's hatch and taps: there so a pipe or a funnel has a block entity to find, and for nothing else. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<uk.co.iceconchy.aerowarptics.wellhead.RiftWellheadPortBlockEntity>> RIFT_WELLHEAD_PORT =
            BLOCK_ENTITIES.register("rift_wellhead_port", () -> BlockEntityType.Builder.<uk.co.iceconchy.aerowarptics.wellhead.RiftWellheadPortBlockEntity>of(
                            uk.co.iceconchy.aerowarptics.wellhead.RiftWellheadPortBlockEntity::new, AWBlocks.RIFT_WELLHEAD_PORT.get())
                    .build(null));

    /** A Rift Wound: there so a client has something to tick, and for nothing else. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<uk.co.iceconchy.aerowarptics.wellhead.RiftWoundBlockEntity>> RIFT_WOUND =
            BLOCK_ENTITIES.register("rift_wound", () -> BlockEntityType.Builder.<uk.co.iceconchy.aerowarptics.wellhead.RiftWoundBlockEntity>of(
                            uk.co.iceconchy.aerowarptics.wellhead.RiftWoundBlockEntity::new, AWBlocks.RIFT_WOUND.get())
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
