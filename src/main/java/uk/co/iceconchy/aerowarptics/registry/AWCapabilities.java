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
    }
}
