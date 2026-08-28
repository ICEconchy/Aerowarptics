package uk.co.iceconchy.aerowarptics.registry;

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
import uk.co.iceconchy.aerowarptics.astrolabe.AstrolabeBlock;
import uk.co.iceconchy.aerowarptics.siphon.SpatialSiphonBlock;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlock;
import uk.co.iceconchy.aerowarptics.fissure.RiftFissureBlock;
import uk.co.iceconchy.aerowarptics.gate.RiftGateBlock;
import uk.co.iceconchy.aerowarptics.gate.RiftGateFrameBlock;
import uk.co.iceconchy.aerowarptics.gate.RiftPortalBlock;
import uk.co.iceconchy.aerowarptics.modulator.RiftModulatorBlock;
import uk.co.iceconchy.aerowarptics.probe.RiftProbeBlock;
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
