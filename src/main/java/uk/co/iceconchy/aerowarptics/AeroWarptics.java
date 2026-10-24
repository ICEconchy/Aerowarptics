package uk.co.iceconchy.aerowarptics;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import uk.co.iceconchy.aerowarptics.advancement.AWCriteria;
import uk.co.iceconchy.aerowarptics.astrolabe.AstrolabeBlock;
import uk.co.iceconchy.aerowarptics.compat.display.AWDisplaySources;
import uk.co.iceconchy.aerowarptics.fissure.RiftGogglesItem;
import uk.co.iceconchy.aerowarptics.hold.AirshipResidency;
import uk.co.iceconchy.aerowarptics.hold.Holds;
import uk.co.iceconchy.aerowarptics.network.AWNetwork;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.registry.AWBlocks;
import uk.co.iceconchy.aerowarptics.registry.AWCapabilities;
import uk.co.iceconchy.aerowarptics.registry.AWCreativeTab;
import uk.co.iceconchy.aerowarptics.registry.AWFluids;
import uk.co.iceconchy.aerowarptics.registry.AWDataComponents;
import uk.co.iceconchy.aerowarptics.registry.AWEntities;
import uk.co.iceconchy.aerowarptics.registry.AWRecipeTypes;
import uk.co.iceconchy.aerowarptics.pack.ClassicRecipes;
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
        AWDataComponents.register(modEventBus);
        AWEntities.register(modEventBus);
        AWRecipeTypes.register(modEventBus);
        uk.co.iceconchy.aerowarptics.registry.AWMenus.register(modEventBus);
        uk.co.iceconchy.aerowarptics.registry.AWFanTypes.register(modEventBus);
        AWCreativeTab.register(modEventBus);
        AWCriteria.register(modEventBus);
        // Before any level loads: Sable reads saved ship tickets by type name as each one does.
        Holds.bootstrap();

        modEventBus.addListener(AirshipResidency::onRegisterControllers);
        modEventBus.addListener(uk.co.iceconchy.aerowarptics.hold.RailWake::onRegisterControllers);
        modEventBus.addListener(AWNetwork::register);
        modEventBus.addListener(AWDisplaySources::onRegister);
        modEventBus.addListener(AWDisplaySources::onCommonSetup);
        modEventBus.addListener(AWStress::onCommonSetup);
        modEventBus.addListener(RiftGogglesItem::onCommonSetup);
        modEventBus.addListener(AstrolabeBlock::onCommonSetup);
        modEventBus.addListener(uk.co.iceconchy.aerowarptics.rail.RiftPortalTracks::onCommonSetup);
        modEventBus.addListener(uk.co.iceconchy.aerowarptics.fissure.RiftFissureBlock::onCommonSetup);
        modEventBus.addListener(uk.co.iceconchy.aerowarptics.collapse.CollapseCradleBlock::onCommonSetup);
        modEventBus.addListener(uk.co.iceconchy.aerowarptics.plotter.RiftPlotterBlock::onCommonSetup);
        modEventBus.addListener(uk.co.iceconchy.aerowarptics.ward.RiftWardBlock::onCommonSetup);
        modEventBus.addListener(uk.co.iceconchy.aerowarptics.berth.BerthBlock::onCommonSetup);
        modEventBus.addListener(uk.co.iceconchy.aerowarptics.wellhead.RiftCollarBlock::onCommonSetup);
        modEventBus.addListener(uk.co.iceconchy.aerowarptics.sunderer.RiftSundererBlock::onCommonSetup);
        modEventBus.addListener(AWCapabilities::register);
        modEventBus.addListener(ClassicRecipes::onAddPackFinders);

        modContainer.registerConfig(ModConfig.Type.SERVER, AWConfig.SERVER_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, AWConfig.CLIENT_SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
