package uk.co.iceconchy.aerowarptics.compat.ponder;

import net.createmod.ponder.api.registration.MultiTagBuilder;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;
import uk.co.iceconchy.aerowarptics.registry.AWItems;

/**
 * The mod's chapters of the Ponder index.
 *
 * <p>Two tags, because the mod does two separable things. Warp travel is the airship half - build a
 * drive, mark somewhere to go, chart it, catch what falls out. Rift gates are the land half, and a
 * player who only ever wanted a doorway between two bases should not have to read about airships to
 * find it.
 *
 * <p>Components appear under a tag whether or not they have a scene of their own, which is how the
 * crafting parts get a place to be listed.
 */
public final class AWPonderTags {

    public static final ResourceLocation WARP_TRAVEL = AeroWarptics.id("warp_travel");
    public static final ResourceLocation RIFT_GATES = AeroWarptics.id("rift_gates");

    private AWPonderTags() {
    }

    public static void register(PonderTagRegistrationHelper<ResourceLocation> helper) {
        PonderTagRegistrationHelper<ItemLike> tags =
                helper.withKeyFunction(item -> BuiltInRegistries.ITEM.getKey(item.asItem()));

        tags.registerTag(WARP_TRAVEL)
                .addToIndex()
                .item(AWItems.RIFT_DRIVES.get(RiftDriveTier.MK_II).get())
                .title("Warp Travel")
                .description("Moving a whole airship across the world, and the machinery that makes it possible")
                .register();

        tags.registerTag(RIFT_GATES)
                .addToIndex()
                .item(AWItems.RIFT_GATE.get())
                .title("Rift Gates")
                .description("Standing doorways joining two places on the ground, for travellers on foot and vehicles alike")
                .register();

        MultiTagBuilder.Tag<ItemLike> warp = tags.addToTag(WARP_TRAVEL)
                .add(AWItems.WARP_ANCHOR.get())
                .add(AWItems.ASTROLABE.get())
                .add(AWItems.SPATIAL_SIPHON.get())
                .add(AWItems.RIFT_PROBE.get())
                .add(AWItems.RIFT_ESSENCE_BUCKET.get())
                .add(AWItems.RIFT_CORE.get())
                .add(AWItems.RIFT_LENS.get())
                .add(AWItems.STABILISER_RING.get())
                .add(AWItems.SINGULARITY_CORE.get());
        for (RiftDriveTier tier : RiftDriveTier.values()) {
            // The creative drive is left out: nothing makes one, so listing it in an index of things
            // to build would only be a dead end.
            if (!tier.creative()) {
                warp.add(AWItems.RIFT_DRIVES.get(tier).get());
            }
        }

        tags.addToTag(RIFT_GATES)
                .add(AWItems.RIFT_GATE.get())
                .add(AWItems.RIFT_GATE_FRAME.get())
                .add(AWItems.RIFT_ESSENCE_BUCKET.get());
    }
}
