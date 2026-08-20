package uk.co.iceconchy.aerowarptics;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import uk.co.iceconchy.aerowarptics.network.AWNetwork;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.registry.AWBlocks;
import uk.co.iceconchy.aerowarptics.registry.AWCapabilities;
import uk.co.iceconchy.aerowarptics.registry.AWCreativeTab;
import uk.co.iceconchy.aerowarptics.registry.AWFluids;
import uk.co.iceconchy.aerowarptics.registry.AWItems;
import uk.co.iceconchy.aerowarptics.registry.AWParticles;
import uk.co.iceconchy.aerowarptics.registry.AWSounds;
import uk.co.iceconchy.aerowarptics.registry.AWStress;

/**
 * Create: Aeronautics warp-travel addon.
 *
 * <p>Airships in this ecosystem are Sable {@code SubLevel}s - independent block plots that Sable's
 * physics pipeline moves through the parent {@code ServerLevel}. Everything this mod does to an
 * airship therefore goes through Sable's public API rather than through vanilla block or entity
 * teleportation.
 */
@Mod(AeroWarptics.MODID)
public class AeroWarptics {

    public static final String MODID = "aerowarptics";

    public static final Logger LOGGER = LogUtils.getLogger();

    public AeroWarptics(IEventBus modEventBus, ModContainer modContainer) {
        AWBlocks.register(modEventBus);
        AWItems.register(modEventBus);
        AWBlockEntities.register(modEventBus);
        AWFluids.register(modEventBus);
        AWSounds.register(modEventBus);
        AWParticles.register(modEventBus);
        AWCreativeTab.register(modEventBus);

        modEventBus.addListener(AWNetwork::register);
        modEventBus.addListener(AWStress::onCommonSetup);
        modEventBus.addListener(AWCapabilities::register);

        modContainer.registerConfig(ModConfig.Type.SERVER, AWConfig.SERVER_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, AWConfig.CLIENT_SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
