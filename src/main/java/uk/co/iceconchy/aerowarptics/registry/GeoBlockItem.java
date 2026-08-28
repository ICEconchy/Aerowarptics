package uk.co.iceconchy.aerowarptics.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import uk.co.iceconchy.aerowarptics.AeroWarptics;

import java.util.List;

/**
 * A block item that is drawn in the inventory by the same GeckoLib model as the block in the world.
 *
 * <p>Four of this mod's machines have no baked model at all - the world render is entirely GeckoLib -
 * so the item form of each, eight of them once the drive tiers are counted, had a flat sixteen-pixel
 * sprite standing in for it. A sprite of a machine is a drawing of a machine, and it stops agreeing
 * with the machine the moment either one changes.
 *
 * <p>So the item carries the same three resources the block's renderer uses, and the client draws it
 * with them. There is now one description of what a Rift Drive looks like rather than two, and it is
 * the one that already had to be right.
 *
 * <p>This class stays free of client code deliberately. Nothing here knows how to draw anything; it
 * only says where the model is. What draws it is registered as a client item extension, so a
 * dedicated server never loads a renderer to hold an item it cannot see.
 */
public class GeoBlockItem extends BlockItem implements GeoItem {

    private final ResourceLocation model;
    private final ResourceLocation texture;
    private final ResourceLocation animations;
    private final RawAnimation idle;
    private final List<String> hiddenBones;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /**
     * @param model      the {@code geo/} file, without the folder or the extension
     * @param texture    the {@code textures/block/} file, likewise - a tier's own skin, so five drives
     *                   share one geometry and differ only here
     * @param animations the {@code animations/} file
     * @param idle       the animation an item of this thing plays: what the block does at rest. An
     *                   item in a slot is not doing anything, and showing it mid-warp would be a lie
     *                   about a machine that is sitting in a chest.
     * @param hiddenBones parts of the model that exist only once the block is doing its job. A
     *                   Spatial Siphon's fill is the whole list at present: the bone is the essence
     *                   inside the vessel, and an unplaced siphon that appears to be full of the
     *                   stuff you have not collected yet is a small lie told in every slot it sits in.
     */
    public GeoBlockItem(Block block, Properties properties,
                        String model, String texture, String animations, String idle,
                        String... hiddenBones) {
        super(block, properties);
        this.hiddenBones = List.of(hiddenBones);
        this.model = AeroWarptics.id("geo/" + model + ".geo.json");
        this.texture = AeroWarptics.id("textures/block/" + texture + ".png");
        this.animations = AeroWarptics.id("animations/" + animations + ".animation.json");
        this.idle = RawAnimation.begin().thenLoop(idle);
    }

    public ResourceLocation modelResource() {
        return model;
    }

    public ResourceLocation textureResource() {
        return texture;
    }

    public ResourceLocation animationResource() {
        return animations;
    }

    /** Bones the item render leaves out. Empty for most of them. */
    public List<String> hiddenBones() {
        return hiddenBones;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, state -> {
            state.getController().setAnimation(idle);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
