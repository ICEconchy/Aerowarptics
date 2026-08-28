package uk.co.iceconchy.aerowarptics.compat.display;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateRegistries;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import uk.co.iceconchy.aerowarptics.AeroWarptics;

import java.util.List;

/**
 * Every Display Link source this mod adds, and the two places Create needs them.
 *
 * <p>Registration happens twice, and both are load-bearing:
 *
 * <ul>
 *   <li><b>{@code create:display_source}</b>, during {@link RegisterEvent}. Create resolves a source
 *       by its registry id when the player confirms the configuration, when the link saves, and when
 *       it loads; a source missing from the registry is silently dropped at all three. The registry
 *       is a NeoForge one, baked before common setup, so this cannot wait for
 *       {@link FMLCommonSetupEvent}.</li>
 *   <li><b>{@link DisplaySource#BY_BLOCK_ENTITY}</b>, during common setup. This is what makes the
 *       source appear in the dropdown when a link is pointed at the machine at all.</li>
 * </ul>
 *
 * <p><b>The same instance has to go into both.</b> A display link clears its configured source
 * whenever the gathered list does not contain the active instance, so two separately-created
 * instances would fail that identity check and the link would clear itself every tick. Holding the
 * instances in {@link #ALL} and iterating it twice is what guarantees that.
 *
 * <p>Only the machines are here. A Warp Anchor and an Astrolabe are mostly static configuration,
 * a Rift Fissure is deliberately invisible to anyone without the goggles, and a Rift Gate Frame has
 * no block entity to read. {@code AWDisplaySourcesTest} checks that this list covers exactly the
 * six machines, so a new one added later fails the suite until it is either wired up or written
 * off deliberately.
 */
public final class AWDisplaySources {

    /** Every source, in the order they were written. Nothing depends on the order. */
    public static final List<AWDisplaySource<?>> ALL = List.of(
            new RiftDriveDisplaySource(),
            new RiftGateDisplaySource(),
            new RiftProbeDisplaySource(),
            new SpatialSiphonDisplaySource(),
            new RiftChuteDisplaySource(),
            new RiftModulatorDisplaySource());

    private AWDisplaySources() {
    }

    /** Enters every source into Create's {@code create:display_source} registry. */
    public static void onRegister(RegisterEvent event) {
        if (!event.getRegistryKey().equals(CreateRegistries.DISPLAY_SOURCE)) {
            return;
        }
        for (AWDisplaySource<?> source : ALL) {
            event.register(CreateRegistries.DISPLAY_SOURCE,
                    AeroWarptics.id(source.sourceId()), () -> source);
        }
    }

    /** Links every source to the block entity it reads, which is what offers it in the dropdown. */
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            for (AWDisplaySource<?> source : ALL) {
                DisplaySource.BY_BLOCK_ENTITY.add(source.blockEntityType(), source);
            }
        });
    }
}
