package uk.co.iceconchy.aerowarptics.registry;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Block capabilities this mod exposes.
 *
 * <p>Every machine here that holds a fluid is a fluid handler on every side. That is what makes them
 * work with Create's pipes, with a tank, and with anything else that knows how to move a fluid about,
 * without this mod having to know any of them exist.
 *
 * <p><b>A tank nobody registered is a tank nobody can reach.</b> A block entity can hold fluid
 * perfectly well, report it on its goggles, draw a readout of it on its own screen and save it to disk
 * while being completely unpipeable, because none of that goes through the capability - so the failure
 * looks like a broken pipe rather than a missing line here. {@code CapabilityCoverageTest} checks that
 * every block entity with a tank on it is named in this method.
 */
public final class AWCapabilities {

    private AWCapabilities() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, AWBlockEntities.SPATIAL_SIPHON.get(),
                (siphon, side) -> siphon.tank());
        // A Rift Gate is filled rather than drained, but it is the same capability either way - and
        // without it a gate is a machine that asks for Rift Essence and gives you no way to give it any.
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, AWBlockEntities.RIFT_GATE.get(),
                (gate, side) -> gate.tank());
        // Same story for the probe: soundings are paid for in essence, and a probe you cannot pipe
        // into is a probe fed one bucket at a time.
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, AWBlockEntities.RIFT_PROBE.get(),
                (probe, side) -> probe.tank());
        // And the chute, which spends essence per item and would otherwise have to be filled by hand
        // one bucket at a time - on a machine whose entire purpose is not being tended by hand.
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, AWBlockEntities.RIFT_CHUTE.get(),
                (chute, side) -> chute.tank());
        // And its mouth as an inventory, on every side, which is the whole of how a chute is fed:
        // a Create funnel fitted to any face fills it exactly as it would fill a chest. The chute
        // stores nothing - an insert either completes the whole journey or is refused.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, AWBlockEntities.RIFT_CHUTE.get(),
                (chute, side) -> chute.mouth());
        // The Modulator's own small tank. Without this a pipe fitted to it looks connected and moves
        // nothing, exactly the bug this whole registry exists to prevent - see the class doc.
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, AWBlockEntities.RIFT_MODULATOR.get(),
                (modulator, side) -> modulator.tank());
        // The coupler's tank, for the same reason: a pipe that looks connected and moves nothing.
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, AWBlockEntities.RIFT_COUPLER.get(),
                (coupler, side) -> coupler.tank());
        // The cradle's essence tank, on a pipe as the plan draws it, and its core and heart slots to a funnel.
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, AWBlockEntities.COLLAPSE_CRADLE.get(),
                (cradle, side) -> cradle.tank());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, AWBlockEntities.COLLAPSE_CRADLE.get(),
                (cradle, side) -> cradle.items());
        // And the same two on every other block of the machine, which is every block a pipe or a funnel can
        // reach: the master stands in the middle of the floor with parts on all its sides but the shaft's.
        event.registerBlock(Capabilities.FluidHandler.BLOCK,
                (level, pos, state, entity, side) -> cradleAt(level, pos, state) instanceof
                        uk.co.iceconchy.aerowarptics.collapse.CollapseCradleBlockEntity cradle ? cradle.tank() : null,
                AWBlocks.COLLAPSE_CRADLE_PART.get());
        event.registerBlock(Capabilities.ItemHandler.BLOCK,
                (level, pos, state, entity, side) -> cradleAt(level, pos, state) instanceof
                        uk.co.iceconchy.aerowarptics.collapse.CollapseCradleBlockEntity cradle ? cradle.items() : null,
                AWBlocks.COLLAPSE_CRADLE_PART.get());
        // The brazier burns what it is given, and a brazier fed by the bucket is a brazier that goes out.
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, AWBlockEntities.RIFT_BRAZIER.get(),
                (brazier, side) -> brazier.tank());
        // Each relic's floor: the tank to a pipe, and the cradle's slots to a funnel. Create's pipes and funnels only
        // look for a capability where there is a block entity, so these - relays with block entities of their own -
        // are where they join; the parts above have none, and are registered for anything that asks without one.
        event.registerBlock(Capabilities.FluidHandler.BLOCK,
                (level, pos, state, entity, side) -> baseMaster(level, pos, state) instanceof
                        uk.co.iceconchy.aerowarptics.collapse.CollapseCradleBlockEntity cradle ? cradle.tank() : null,
                AWBlocks.COLLAPSE_CRADLE_BASE.get());
        event.registerBlock(Capabilities.ItemHandler.BLOCK,
                (level, pos, state, entity, side) -> baseMaster(level, pos, state) instanceof
                        uk.co.iceconchy.aerowarptics.collapse.CollapseCradleBlockEntity cradle ? cradle.items() : null,
                AWBlocks.COLLAPSE_CRADLE_BASE.get());
        event.registerBlock(Capabilities.FluidHandler.BLOCK,
                (level, pos, state, entity, side) -> baseMaster(level, pos, state) instanceof
                        uk.co.iceconchy.aerowarptics.plotter.RiftPlotterBlockEntity plotter ? plotter.tank() : null,
                AWBlocks.RIFT_PLOTTER_BASE.get());
        // The ward's tank, at its master, across its floor, and for anything that asks a part without a block entity.
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, AWBlockEntities.RIFT_WARD.get(),
                (ward, side) -> ward.tank());
        event.registerBlock(Capabilities.FluidHandler.BLOCK,
                (level, pos, state, entity, side) -> baseMaster(level, pos, state) instanceof
                        uk.co.iceconchy.aerowarptics.ward.RiftWardBlockEntity ward ? ward.tank() : null,
                AWBlocks.RIFT_WARD_BASE.get());
        event.registerBlock(Capabilities.FluidHandler.BLOCK,
                (level, pos, state, entity, side) ->
                        uk.co.iceconchy.aerowarptics.ward.RiftWardPartBlock.stillValid(level, pos, state)
                                && level.getBlockEntity(uk.co.iceconchy.aerowarptics.ward.RiftWardPartBlock.master(pos, state))
                                instanceof uk.co.iceconchy.aerowarptics.ward.RiftWardBlockEntity ward ? ward.tank() : null,
                AWBlocks.RIFT_WARD_PART.get());
        // The plotter's tank, which a long plot drains faster than any bucket can fill: it wants a pipe, as the
        // cradle's does.
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, AWBlockEntities.RIFT_PLOTTER.get(),
                (plotter, side) -> plotter.tank());
        event.registerBlock(Capabilities.FluidHandler.BLOCK,
                (level, pos, state, entity, side) -> plotterAt(level, pos, state) instanceof
                        uk.co.iceconchy.aerowarptics.plotter.RiftPlotterBlockEntity plotter ? plotter.tank() : null,
                AWBlocks.RIFT_PLOTTER_PART.get());
        registerWellhead(event);
        // A Rift Sunderer's charge: Rift Essence in, and back out again while it is idle.
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, AWBlockEntities.RIFT_SUNDERER.get(),
                (sunderer, side) -> sunderer.fluids());
    }

    /**
     * A Rift Wellhead: essence out and blocks in, at the collar and at every port of its frame. A tap hands out the
     * tank and the hatch takes the feed; the collar itself answers for both, so a small frame with a pipe on its
     * collar works as one with a pipe on a tap does.
     */
    private static void registerWellhead(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, AWBlockEntities.RIFT_COLLAR.get(),
                (collar, side) -> collar.tap());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, AWBlockEntities.RIFT_COLLAR.get(),
                (collar, side) -> collar.hatch());
        // By block, not by block entity type: a port has a block entity only so a pipe will look at it, and holds
        // no tank of its own for CapabilityCoverageTest to find.
        event.registerBlock(Capabilities.FluidHandler.BLOCK,
                (level, pos, state, entity, side) -> {
                    uk.co.iceconchy.aerowarptics.wellhead.RiftCollarBlockEntity collar =
                            uk.co.iceconchy.aerowarptics.wellhead.WellheadShellBlock.collar(level, pos, state);
                    return collar != null && !state.getValue(uk.co.iceconchy.aerowarptics.wellhead.RiftWellheadPortBlock.HATCH)
                            ? collar.tap() : null;
                },
                AWBlocks.RIFT_WELLHEAD_PORT.get());
        event.registerBlock(Capabilities.ItemHandler.BLOCK,
                (level, pos, state, entity, side) -> {
                    uk.co.iceconchy.aerowarptics.wellhead.RiftCollarBlockEntity collar =
                            uk.co.iceconchy.aerowarptics.wellhead.WellheadShellBlock.collar(level, pos, state);
                    return collar != null && state.getValue(uk.co.iceconchy.aerowarptics.wellhead.RiftWellheadPortBlock.HATCH)
                            ? collar.hatch() : null;
                },
                AWBlocks.RIFT_WELLHEAD_PORT.get());
    }

    /** The block entity of the relic machine a base belongs to, or null if it has come apart. */
    private static net.minecraft.world.level.block.entity.BlockEntity baseMaster(net.minecraft.world.level.Level level,
                                                                              net.minecraft.core.BlockPos pos,
                                                                              net.minecraft.world.level.block.state.BlockState state) {
        if (!(state.getBlock() instanceof uk.co.iceconchy.aerowarptics.collapse.RelicBaseBlock base)
                || !base.stillValid(level, pos, state)) {
            return null;
        }
        return level.getBlockEntity(uk.co.iceconchy.aerowarptics.collapse.RelicBaseBlock.master(pos, state));
    }

    /** The block entity of the Rift Plotter a part belongs to, or null if it has come apart. */
    private static net.minecraft.world.level.block.entity.BlockEntity plotterAt(net.minecraft.world.level.Level level,
                                                                             net.minecraft.core.BlockPos pos,
                                                                             net.minecraft.world.level.block.state.BlockState state) {
        if (!uk.co.iceconchy.aerowarptics.plotter.RiftPlotterPartBlock.stillValid(level, pos, state)) {
            return null;
        }
        return level.getBlockEntity(uk.co.iceconchy.aerowarptics.plotter.RiftPlotterPartBlock.master(pos, state));
    }

    /** The block entity of the Collapse Cradle a part belongs to, or null if it has come apart. */
    private static net.minecraft.world.level.block.entity.BlockEntity cradleAt(net.minecraft.world.level.Level level,
                                                                            net.minecraft.core.BlockPos pos,
                                                                            net.minecraft.world.level.block.state.BlockState state) {
        if (!uk.co.iceconchy.aerowarptics.collapse.CollapseCradlePartBlock.stillValid(level, pos, state)) {
            return null;
        }
        return level.getBlockEntity(uk.co.iceconchy.aerowarptics.collapse.CollapseCradlePartBlock.master(pos, state));
    }
}
