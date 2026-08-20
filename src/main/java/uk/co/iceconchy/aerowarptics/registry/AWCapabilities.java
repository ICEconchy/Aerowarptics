package uk.co.iceconchy.aerowarptics.registry;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import uk.co.iceconchy.aerowarptics.siphon.SpatialSiphonBlockEntity;

/**
 * Block capabilities this mod exposes.
 *
 * <p>Only one so far: a Spatial Siphon is a fluid handler on every side. That is what makes it work
 * with Create's pipes, with a tank, and with anything else that knows how to drain something, without
 * this mod having to know any of them exist.
 */
public final class AWCapabilities {

    private AWCapabilities() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, AWBlockEntities.SPATIAL_SIPHON.get(),
                (siphon, side) -> siphon.tank());
    }
}
