package uk.co.iceconchy.aerowarptics.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;

/** Creative tab holding the drives, the anchor and the components. */
public final class AWCreativeTab {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AeroWarptics.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.aerowarptics"))
                    .icon(() -> AWItems.RIFT_DRIVES.get(RiftDriveTier.MK_I).get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(AWItems.HANDBOOK.get());
                        for (RiftDriveTier tier : RiftDriveTier.values()) {
                            output.accept(AWItems.RIFT_DRIVES.get(tier).get());
                        }
                        output.accept(AWItems.WARP_ANCHOR.get());
                        output.accept(AWItems.ASTROLABE.get());
                        output.accept(AWItems.RIFT_PROBE.get());
                        output.accept(AWItems.RIFT_GOGGLES.get());
                        output.accept(AWItems.RIFT_BEACON.get());
                        output.accept(AWItems.RIFT_GATE.get());
                        output.accept(AWItems.RIFT_GATE_FRAME.get());
                        output.accept(AWItems.RIFT_CHUTE.get());
                        output.accept(AWItems.RIFT_MODULATOR.get());
                        output.accept(AWItems.SPATIAL_SIPHON.get());
                        output.accept(AWItems.RIFT_ESSENCE_BUCKET.get());
                        output.accept(AWItems.RIFT_CORE.get());
                        output.accept(AWItems.RIFT_LENS.get());
                        output.accept(AWItems.STABILISER_RING.get());
                        output.accept(AWItems.SINGULARITY_CORE.get());
                        output.accept(AWItems.WARP_CRYSTAL_ORE.get());
                        output.accept(AWItems.RAW_WARP_CRYSTAL.get());
                        output.accept(AWItems.WARP_DUST.get());
                        output.accept(AWItems.WARP_SHARD.get());
                        output.accept(AWItems.WARP_CRYSTAL.get());
                        output.accept(AWItems.PEARLESCENT_WARP_CRYSTAL.get());
                    })
                    .build());

    private AWCreativeTab() {
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
