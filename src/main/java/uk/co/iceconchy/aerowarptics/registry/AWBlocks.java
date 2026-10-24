package uk.co.iceconchy.aerowarptics.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorBlock;
import uk.co.iceconchy.aerowarptics.chute.RiftChuteBlock;
import uk.co.iceconchy.aerowarptics.detector.SpatialWaveDetectorBlock;
import uk.co.iceconchy.aerowarptics.astrolabe.AstrolabeBlock;
import uk.co.iceconchy.aerowarptics.siphon.SpatialSiphonBlock;
import uk.co.iceconchy.aerowarptics.drive.DriveExtenderBlock;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlock;
import uk.co.iceconchy.aerowarptics.fissure.RiftFissureBlock;
import uk.co.iceconchy.aerowarptics.gate.RiftGateBlock;
import uk.co.iceconchy.aerowarptics.gate.RiftGateFrameBlock;
import uk.co.iceconchy.aerowarptics.gate.RiftPortalBlock;
import uk.co.iceconchy.aerowarptics.gate.RiftSocketBlock;
import uk.co.iceconchy.aerowarptics.rail.CorridorMouthBlock;
import uk.co.iceconchy.aerowarptics.flotilla.RiftCouplerBlock;
import uk.co.iceconchy.aerowarptics.modulator.RiftModulatorBlock;
import uk.co.iceconchy.aerowarptics.probe.RiftProbeBlock;
import uk.co.iceconchy.aerowarptics.stabiliser.SpatialStabiliserBlock;
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

    /**
     * A Rift Fissure: a tear that generates in the world rather than being opened by a machine.
     *
     * <p>Strong as bedrock and drops nothing, because there is no sensible thing for a pickaxe to do
     * to a hole in space. The only way to be rid of one in survival is to empty it with a Spatial
     * Siphon, which is the point of it. No light, either - a glow in an empty room would say exactly
     * where the invisible thing is, to a player with no goggles on.
     */
    public static final DeferredBlock<RiftFissureBlock> RIFT_FISSURE = BLOCKS.register("rift_fissure",
            () -> new RiftFissureBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .strength(-1.0F, 3_600_000.0F)
                    .sound(SoundType.EMPTY)
                    .noCollission()
                    .noOcclusion()
                    .noLootTable()
                    .pushReaction(PushReaction.BLOCK)));

    /**
     * A block of gate frame.
     *
     * <p>Inert on purpose. The frame of a gate is a wall, and forty blocks of wall do not need forty
     * block entities in them - the controller finds its ring by looking rather than by being told.
     */
    public static final DeferredBlock<RiftGateFrameBlock> RIFT_GATE_FRAME = BLOCKS.register("rift_gate_frame",
            () -> new RiftGateFrameBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(4.0F, 12.0F)
                    .sound(SoundType.COPPER)
                    .lightLevel(state -> 3)
                    .requiresCorrectToolForDrops()));

    /**
     * The pane that stands in a gate's opening while it is connected.
     *
     * <p>Placed by the gate and by nothing else - no item, no recipe, nothing that drops - and as
     * hard as bedrock for the same reason a Nether portal is: there is nothing sensible for a pickaxe
     * to do to a hole in space, and the way to be rid of one is to shut the gate holding it. It lights
     * itself brightly, because a doorway across a dark hangar should be the thing you can see; and it
     * has no collision, because walking into it is the entire point. It dims a little while it is
     * opening or closing - see {@link RiftPortalBlock#STAGE} - because it is not fully there yet.
     *
     * <p>Random ticks are on so a pane orphaned by a ring broken in an unloaded chunk can notice and
     * take itself away. See {@link RiftPortalBlock}.
     */
    public static final DeferredBlock<RiftPortalBlock> RIFT_PORTAL = BLOCKS.register("rift_portal",
            () -> new RiftPortalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(-1.0F, 3_600_000.0F)
                    .sound(SoundType.GLASS)
                    .lightLevel(RiftPortalBlock::stageLight)
                    .noCollission()
                    .noOcclusion()
                    .noLootTable()
                    .randomTicks()
                    .pushReaction(PushReaction.BLOCK)));

    /**
     * What stands in a rail lane's cell of a gate while the gate is shut, so the track run into it is not eaten.
     *
     * <p>Placed by the gate and by nothing else, like the pane: no item, no recipe, nothing that drops, and as
     * hard as the pane. Invisible and without collision, because it is still a hole in the doorway - it only
     * has to be a portal Create recognises. See {@link RiftSocketBlock}.
     */
    public static final DeferredBlock<RiftSocketBlock> RIFT_SOCKET = BLOCKS.register("rift_socket",
            () -> new RiftSocketBlock(BlockBehaviour.Properties.of()
                    .strength(-1.0F, 3_600_000.0F)
                    .noCollission()
                    .noOcclusion()
                    .noLootTable()
                    .randomTicks()
                    .pushReaction(PushReaction.BLOCK)));

    /**
     * The doorway at each end of a rail corridor, in the Rift Corridor dimension.
     *
     * <p>Placed by a lane and by nothing else. Drawn as the gate's own shimmer, lit as an open pane is, and
     * unbreakable: nobody is meant to be able to build or dig in the corridor. See {@link CorridorMouthBlock}.
     */
    public static final DeferredBlock<CorridorMouthBlock> CORRIDOR_MOUTH = BLOCKS.register("corridor_mouth",
            () -> new CorridorMouthBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(-1.0F, 3_600_000.0F)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 11)
                    .noCollission()
                    .noOcclusion()
                    .noLootTable()
                    .pushReaction(PushReaction.BLOCK)));

    /** The one piece of the ring that thinks. Counts as frame, so it may sit anywhere in the circle. */
    public static final DeferredBlock<RiftGateBlock> RIFT_GATE = BLOCKS.register("rift_gate",
            () -> new RiftGateBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(4.0F, 12.0F)
                    .sound(SoundType.COPPER)
                    .lightLevel(state -> 7)
                    .requiresCorrectToolForDrops()));

    /**
     * The Rift Probe: the one machine here that finds a destination rather than remembering one.
     *
     * <p>Lit a little brighter than the siphon because it is doing something rather than collecting
     * something, and no-occlusion because its dish is narrower than a full block.
     */
    public static final DeferredBlock<RiftProbeBlock> RIFT_PROBE = BLOCKS.register("rift_probe",
            () -> new RiftProbeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.5F, 8.0F)
                    .sound(SoundType.COPPER)
                    .lightLevel(state -> 6)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    /**
     * The Rift Chute: a belt-fed hole in space.
     *
     * <p>Lit only faintly - the light in the block is the rift it holds, drawn by the renderer, and a
     * block that glowed just as brightly with its rift collapsed would be telling the player it was
     * working when it was not.
     */
    public static final DeferredBlock<RiftChuteBlock> RIFT_CHUTE = BLOCKS.register("rift_chute",
            () -> new RiftChuteBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.0F, 8.0F)
                    .sound(SoundType.COPPER)
                    .lightLevel(state -> 3)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    /**
     * The Rift Modulator: a cosmetic module a pilot bolts beside a Rift Drive to dress the rift it
     * tears. No light of its own - like the chute, the light in the block is the lens the renderer
     * draws, and a block that glowed the same whether or not it was actually linked and fuelled would
     * be lying about which state it was in.
     */
    public static final DeferredBlock<RiftModulatorBlock> RIFT_MODULATOR = BLOCKS.register("rift_modulator",
            () -> new RiftModulatorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(2.5F, 6.0F)
                    .sound(SoundType.COPPER)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    /**
     * The Rift Coupler: a module bolted beside a Rift Drive that lets its ship fold as one of a flotilla.
     * No light of its own, for the Modulator's reason - the crystal the renderer draws is what lights up,
     * and only while a muster is on.
     */
    public static final DeferredBlock<RiftCouplerBlock> RIFT_COUPLER = BLOCKS.register("rift_coupler",
            () -> new RiftCouplerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(2.5F, 6.0F)
                    .sound(SoundType.COPPER)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    /**
     * The Spatial Stabiliser: a module a pilot bolts beside a Rift Drive to cushion the ship against
     * sharp changes in its motion. Lit only while it has a signal - the light is the switch's position,
     * readable from across a deck, and goes out with the switch rather than glowing regardless.
     */
    public static final DeferredBlock<SpatialStabiliserBlock> SPATIAL_STABILISER = BLOCKS.register("spatial_stabiliser",
            () -> new SpatialStabiliserBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.0F, 8.0F)
                    .sound(SoundType.COPPER)
                    .lightLevel(state -> state.getValue(SpatialStabiliserBlock.POWERED) ? 5 : 0)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    /**
     * The Cooling Vent: a module a pilot bolts beside a Rift Drive, and turns with a shaft, to carry the heat
     * of a warp out as steam - see {@link uk.co.iceconchy.aerowarptics.cooling.CoolingVentBlock}. No light of
     * its own: what shows it working is the steam. Declared see-through because its louvres are set back
     * into the casing, and a block pressed against them would otherwise have its face culled away behind
     * the slats.
     */
    public static final DeferredBlock<uk.co.iceconchy.aerowarptics.cooling.CoolingVentBlock> COOLING_VENT =
            BLOCKS.register("cooling_vent", () -> new uk.co.iceconchy.aerowarptics.cooling.CoolingVentBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PURPLE)
                            .strength(3.0F, 8.0F)
                            .sound(SoundType.COPPER)
                            .noOcclusion()
                            .requiresCorrectToolForDrops()));

    /**
     * The Spatial Wave Detector: a redstone source that pulses when something arrives through a rift
     * nearby. Lit only while it is pulsing, and by how hard - the light is the signal, so a detector
     * across a yard says both that something came and roughly how close.
     */
    public static final DeferredBlock<SpatialWaveDetectorBlock> SPATIAL_WAVE_DETECTOR = BLOCKS.register("spatial_wave_detector",
            () -> new SpatialWaveDetectorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.0F, 8.0F)
                    .sound(SoundType.COPPER)
                    .lightLevel(state -> state.getValue(SpatialWaveDetectorBlock.POWER) / 2)
                    .requiresCorrectToolForDrops()));

    /**
     * A Rift Bond: one on each loose section of a ship, tuned alike, and a warp takes every section. It does
     * nothing anybody can see, so it gives no light and makes no claim to be a machine.
     */
    public static final DeferredBlock<uk.co.iceconchy.aerowarptics.bond.RiftBondBlock> RIFT_BOND = BLOCKS.register("rift_bond",
            () -> new uk.co.iceconchy.aerowarptics.bond.RiftBondBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.0F, 8.0F)
                    .sound(SoundType.COPPER)
                    .requiresCorrectToolForDrops()));

    /**
     * A Berth: a bollard that marks out a slip at a Warp Anchor's station. Its lamp is what is in the slip -
     * dark empty, dim with a ship on its way, bright with one held - so a harbour can be read from the air.
     * Never pushed: its record says where its slip is.
     */
    public static final DeferredBlock<uk.co.iceconchy.aerowarptics.berth.BerthBlock> BERTH = BLOCKS.register("berth",
            () -> new uk.co.iceconchy.aerowarptics.berth.BerthBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.0F, 8.0F)
                    .sound(SoundType.COPPER)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                    .lightLevel(state -> state.getValue(uk.co.iceconchy.aerowarptics.berth.BerthBlock.STATE).signal() * 2 / 3)
                    .requiresCorrectToolForDrops()));

    /**
     * A Drive Extender: casing a Rift Drive is built out into, a cube of up to seven blocks an edge.
     *
     * <p>No light of its own, for the Modulator's reason - a formed frame is not drawn as extenders at
     * all, the enlarged drive is, and a loose one glowing would suggest it was doing something. Declared
     * see-through for the reason given on {@link uk.co.iceconchy.aerowarptics.drive.DriveExtenderBlock}.
     */
    public static final DeferredBlock<DriveExtenderBlock> DRIVE_EXTENDER = BLOCKS.register("drive_extender",
            () -> new DriveExtenderBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.5F, 8.0F)
                    .sound(SoundType.COPPER)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()));

    /**
     * Rift Casing: a Brass Casing with warp dust worked into it, by hand or by a Deployer.
     *
     * <p>The addon's own casing, and what every machine here that used to take a Brass Casing is built from.
     * Inert - a building block and a crafting part, like the casing it is made from - and cheap on purpose:
     * it costs one dust more than brass, which is the whole point of it. It says a machine is this mod's
     * without asking anything of the player the Mk I does not already ask.
     */
    public static final DeferredBlock<Block> RIFT_CASING = BLOCKS.register("rift_casing",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.WOOD)
                    .instrument(net.minecraft.world.level.block.state.properties.NoteBlockInstrument.BASS)));

    /**
     * Glass that has been blown through a standing rift: the crafting layer's first building block, and the
     * first thing a player makes by rifting. Violet, clear enough to see through, and it drops itself - it
     * was made by a process, not found, and breaking it should not throw the work away.
     */
    public static final DeferredBlock<net.minecraft.world.level.block.TransparentBlock> WARP_GLASS =
            BLOCKS.register("warp_glass", () -> new net.minecraft.world.level.block.TransparentBlock(
                    BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GLASS)
                            .mapColor(MapColor.COLOR_PURPLE)
                            .lightLevel(state -> 3)));

    /**
     * The Rift Brazier: a bowl of essence holding a small rift open while it has a signal - see
     * {@link uk.co.iceconchy.aerowarptics.rifting.RiftBrazierBlock}. Lit by what it is burning.
     */
    public static final DeferredBlock<uk.co.iceconchy.aerowarptics.rifting.RiftBrazierBlock> RIFT_BRAZIER =
            BLOCKS.register("rift_brazier", () -> new uk.co.iceconchy.aerowarptics.rifting.RiftBrazierBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PURPLE)
                            .strength(3.0F, 8.0F)
                            .sound(SoundType.COPPER)
                            .lightLevel(state -> state.getValue(uk.co.iceconchy.aerowarptics.rifting.RiftBrazierBlock.LIT) ? 11 : 0)
                            .noOcclusion()
                            .requiresCorrectToolForDrops()));

    /**
     * The Collapse Cradle, where a Singularity Core is collapsed out of a scar - see
     * {@link uk.co.iceconchy.aerowarptics.collapse.CollapseCradleBlockEntity}. Heavy, and kinetic: it takes a
     * shaft into any side of its floor and turns at the Singularity's own speeds. This is the machine's master block, in the
     * middle of its floor; the other twenty-six are {@link #COLLAPSE_CRADLE_PART}. Nothing pushes either, and
     * neither passes redstone on - a signal at the machine's side is for the machine.
     */
    public static final DeferredBlock<uk.co.iceconchy.aerowarptics.collapse.CollapseCradleBlock> COLLAPSE_CRADLE =
            BLOCKS.register("collapse_cradle", () -> new uk.co.iceconchy.aerowarptics.collapse.CollapseCradleBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PURPLE)
                            .strength(5.0F, 12.0F)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .lightLevel(state -> 6)
                            .noOcclusion()
                            .isRedstoneConductor((state, level, pos) -> false)
                            .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                            .requiresCorrectToolForDrops()));

    /**
     * The rest of a Collapse Cradle: see {@link uk.co.iceconchy.aerowarptics.collapse.CollapseCradlePartBlock}.
     * No item and no loot of its own - breaking one breaks the machine, and the master drops the cradle.
     */
    public static final DeferredBlock<uk.co.iceconchy.aerowarptics.collapse.CollapseCradlePartBlock> COLLAPSE_CRADLE_PART =
            BLOCKS.register("collapse_cradle_part", () -> new uk.co.iceconchy.aerowarptics.collapse.CollapseCradlePartBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PURPLE)
                            .strength(5.0F, 12.0F)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .noOcclusion()
                            .isRedstoneConductor((state, level, pos) -> false)
                            .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                            .requiresCorrectToolForDrops()
                            .noLootTable()));

    /**
     * The Rift Plotter, which tears a rift beside itself and opens the other end of it round a target anywhere in
     * the world - see {@link uk.co.iceconchy.aerowarptics.plotter.RiftPlotterBlockEntity}. The cradle's sibling:
     * the same relic stone and bronze, turned from its sides, and the master block of twenty-seven. Nothing pushes it,
     * and a signal at its side is for the machine.
     */
    public static final DeferredBlock<uk.co.iceconchy.aerowarptics.plotter.RiftPlotterBlock> RIFT_PLOTTER =
            BLOCKS.register("rift_plotter", () -> new uk.co.iceconchy.aerowarptics.plotter.RiftPlotterBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PURPLE)
                            .strength(5.0F, 12.0F)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .lightLevel(state -> 6)
                            .noOcclusion()
                            .isRedstoneConductor((state, level, pos) -> false)
                            .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                            .requiresCorrectToolForDrops()));

    /**
     * The rest of a Rift Plotter: see {@link uk.co.iceconchy.aerowarptics.plotter.RiftPlotterPartBlock}. No item and
     * no loot of its own - breaking one breaks the machine, and the master drops the plotter.
     */
    public static final DeferredBlock<uk.co.iceconchy.aerowarptics.plotter.RiftPlotterPartBlock> RIFT_PLOTTER_PART =
            BLOCKS.register("rift_plotter_part", () -> new uk.co.iceconchy.aerowarptics.plotter.RiftPlotterPartBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PURPLE)
                            .strength(5.0F, 12.0F)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .noOcclusion()
                            .isRedstoneConductor((state, level, pos) -> false)
                            .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                            .requiresCorrectToolForDrops()
                            .noLootTable()));

    /**
     * The floor of a Collapse Cradle round its master: where a shaft turns it and pipes and funnels join it - see
     * {@link uk.co.iceconchy.aerowarptics.collapse.RelicBaseBlock}. No item and no loot, as a part.
     */
    public static final DeferredBlock<uk.co.iceconchy.aerowarptics.collapse.CollapseCradleBaseBlock> COLLAPSE_CRADLE_BASE =
            BLOCKS.register("collapse_cradle_base", () -> new uk.co.iceconchy.aerowarptics.collapse.CollapseCradleBaseBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PURPLE)
                            .strength(5.0F, 12.0F)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .noOcclusion()
                            .isRedstoneConductor((state, level, pos) -> false)
                            .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                            .requiresCorrectToolForDrops()
                            .noLootTable()));

    /**
     * The Rift Ward, which holds a field over the ground round it and turns aside a rift a stranger aims into it -
     * see {@link uk.co.iceconchy.aerowarptics.ward.RiftWardBlockEntity}. The third relic: the same stone and bronze,
     * turned from its sides, and the master block of twenty-seven. Nothing pushes it.
     */
    public static final DeferredBlock<uk.co.iceconchy.aerowarptics.ward.RiftWardBlock> RIFT_WARD =
            BLOCKS.register("rift_ward", () -> new uk.co.iceconchy.aerowarptics.ward.RiftWardBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PURPLE)
                            .strength(5.0F, 12.0F)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .lightLevel(state -> state.getValue(uk.co.iceconchy.aerowarptics.ward.RiftWardBlock.LIT)
                                    ? uk.co.iceconchy.aerowarptics.ward.RiftWardBlock.LIGHT : 0)
                            .noOcclusion()
                            .isRedstoneConductor((state, level, pos) -> false)
                            .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                            .requiresCorrectToolForDrops()));

    /** The body of a Rift Ward: see {@link uk.co.iceconchy.aerowarptics.ward.RiftWardPartBlock}. No item, no loot. */
    public static final DeferredBlock<uk.co.iceconchy.aerowarptics.ward.RiftWardPartBlock> RIFT_WARD_PART =
            BLOCKS.register("rift_ward_part", () -> new uk.co.iceconchy.aerowarptics.ward.RiftWardPartBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PURPLE)
                            .strength(5.0F, 12.0F)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .noOcclusion()
                            .isRedstoneConductor((state, level, pos) -> false)
                            .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                            .requiresCorrectToolForDrops()
                            .noLootTable()));

    /** The floor of a Rift Ward round its master, as the cradle's - see {@link #COLLAPSE_CRADLE_BASE}. */
    public static final DeferredBlock<uk.co.iceconchy.aerowarptics.ward.RiftWardBaseBlock> RIFT_WARD_BASE =
            BLOCKS.register("rift_ward_base", () -> new uk.co.iceconchy.aerowarptics.ward.RiftWardBaseBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PURPLE)
                            .strength(5.0F, 12.0F)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .noOcclusion()
                            .isRedstoneConductor((state, level, pos) -> false)
                            .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                            .requiresCorrectToolForDrops()
                            .noLootTable()));

    /** The floor of a Rift Plotter round its master, as the cradle's - see {@link #COLLAPSE_CRADLE_BASE}. */
    public static final DeferredBlock<uk.co.iceconchy.aerowarptics.plotter.RiftPlotterBaseBlock> RIFT_PLOTTER_BASE =
            BLOCKS.register("rift_plotter_base", () -> new uk.co.iceconchy.aerowarptics.plotter.RiftPlotterBaseBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PURPLE)
                            .strength(5.0F, 12.0F)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .noOcclusion()
                            .isRedstoneConductor((state, level, pos) -> false)
                            .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                            .requiresCorrectToolForDrops()
                            .noLootTable()));

    /**
     * The Rift Collar: the one block of a Rift Wellhead that thinks - see
     * {@link uk.co.iceconchy.aerowarptics.wellhead.RiftCollarBlockEntity}. Set in the middle of one side of a frame of
     * Rift Casing and Warp Glass built round a fissure, it forms the frame and runs the well. Kinetic, with its one
     * shaft on the face looking out. A loose one is a block like any other; a formed one nothing pushes.
     */
    public static final DeferredBlock<uk.co.iceconchy.aerowarptics.wellhead.RiftCollarBlock> RIFT_COLLAR =
            BLOCKS.register("rift_collar", () -> new uk.co.iceconchy.aerowarptics.wellhead.RiftCollarBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PURPLE)
                            .strength(4.0F, 10.0F)
                            .sound(SoundType.COPPER)
                            .lightLevel(state -> state.getValue(uk.co.iceconchy.aerowarptics.wellhead.RiftCollarBlock.FORMED) ? 7 : 0)
                            .noOcclusion()
                            .isRedstoneConductor((state, level, pos) -> false)
                            .requiresCorrectToolForDrops()));

    /**
     * The Rift Sunderer: a single-use heart for a ring of Rift Gate Frame that dials nowhere and tears a fissure
     * where the aperture should have been - see {@link uk.co.iceconchy.aerowarptics.sunderer.RiftSundererBlockEntity}.
     * Kinetic, with its one shaft on its back; lit by its own charge.
     */
    public static final DeferredBlock<uk.co.iceconchy.aerowarptics.sunderer.RiftSundererBlock> RIFT_SUNDERER =
            BLOCKS.register("rift_sunderer", () -> new uk.co.iceconchy.aerowarptics.sunderer.RiftSundererBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PURPLE)
                            .strength(4.0F, 10.0F)
                            .sound(SoundType.COPPER)
                            .lightLevel(uk.co.iceconchy.aerowarptics.sunderer.RiftSundererBlock::light)
                            .noOcclusion()
                            .isRedstoneConductor((state, level, pos) -> false)
                            .requiresCorrectToolForDrops()));

    /**
     * The casing and glass of a formed Rift Wellhead: see
     * {@link uk.co.iceconchy.aerowarptics.wellhead.RiftWellheadPartBlock}. No item and no loot of its own - broken, it
     * drops the block it was.
     */
    public static final DeferredBlock<uk.co.iceconchy.aerowarptics.wellhead.RiftWellheadPartBlock> RIFT_WELLHEAD_PART =
            BLOCKS.register("rift_wellhead_part", () -> new uk.co.iceconchy.aerowarptics.wellhead.RiftWellheadPartBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PURPLE)
                            .strength(2.0F, 6.0F)
                            .sound(SoundType.COPPER)
                            .noOcclusion()
                            .isRedstoneConductor((state, level, pos) -> false)
                            .pushReaction(PushReaction.BLOCK)
                            .noLootTable()));

    /** A formed Rift Wellhead's hatch and taps: see {@link uk.co.iceconchy.aerowarptics.wellhead.RiftWellheadPortBlock}. */
    public static final DeferredBlock<uk.co.iceconchy.aerowarptics.wellhead.RiftWellheadPortBlock> RIFT_WELLHEAD_PORT =
            BLOCKS.register("rift_wellhead_port", () -> new uk.co.iceconchy.aerowarptics.wellhead.RiftWellheadPortBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PURPLE)
                            .strength(2.0F, 6.0F)
                            .sound(SoundType.COPPER)
                            .noOcclusion()
                            .isRedstoneConductor((state, level, pos) -> false)
                            .pushReaction(PushReaction.BLOCK)
                            .noLootTable()));

    /**
     * A Rift Wound: what stands where a fissure was after a meltdown took it - see
     * {@link uk.co.iceconchy.aerowarptics.wellhead.RiftWoundBlock}. A landmark and nothing else. Hard as the fissure it
     * replaces and placed by a meltdown alone: no item, no recipe, nothing that drops. It gives no light: what
     * shows it is that it is black where nothing is, and the red that bleeds off it.
     */
    public static final DeferredBlock<uk.co.iceconchy.aerowarptics.wellhead.RiftWoundBlock> RIFT_WOUND =
            BLOCKS.register("rift_wound", () -> new uk.co.iceconchy.aerowarptics.wellhead.RiftWoundBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.NONE)
                            .strength(-1.0F, 3_600_000.0F)
                            .sound(SoundType.EMPTY)
                            .noCollission()
                            .noOcclusion()
                            .noLootTable()
                            .pushReaction(PushReaction.BLOCK)));

    /**
     * Scarred Stone: what a meltdown leaves of the ground it did not take. Stone that has been somewhere else and
     * come back. A building block and nothing more - it does nothing, and it drops itself.
     */
    public static final DeferredBlock<Block> SCARRED_STONE = BLOCKS.register("scarred_stone",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(2.5F, 9.0F)
                    .sound(SoundType.DEEPSLATE)
                    .lightLevel(state -> 2)
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
                            // Purple with the rest of them now the drive is a cage rather than a
                            // brass box; orange was the colour of the housing it no longer has.
                            .mapColor(MapColor.COLOR_PURPLE)
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
