package uk.co.iceconchy.aerowarptics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.collapse.CollapseCradleBlock;
import uk.co.iceconchy.aerowarptics.collapse.CollapseCradlePartBlock;
import uk.co.iceconchy.aerowarptics.collapse.CradleStructure;

/**
 * The Collapse Cradle's outlines: one round the whole machine rather than round the block the crosshair is on,
 * and the room it needed, in red, where it could not be set down.
 *
 * <p>Twenty-seven blocks each drawing their own box would draw a cube floating in the middle of an open cage
 * wherever the player looked into it, which reads as a bug. The machine is one thing, so it is outlined as one.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class CradleOutline {

    private static final int REFUSED = 0xFF5D6C;

    private CradleOutline() {
    }

    /** The machine's box in the world, from its master. */
    static AABB machine(BlockPos master) {
        double[] box = CradleStructure.box();
        return new AABB(master.getX() + box[0], master.getY() + box[1], master.getZ() + box[2],
                master.getX() + box[3], master.getY() + box[4], master.getZ() + box[5]);
    }

    /** Where the player tried to set a cradle down and could not: the whole of the room it needed. */
    static void showSpace(BlockPos master) {
        Outliner.getInstance().showAABB("aerowarptics_cradle_space", machine(master).deflate(1.0D / 128.0D))
                .colored(REFUSED)
                .lineWidth(1.0F / 16.0F);
    }

    @SubscribeEvent
    public static void onHighlight(RenderHighlightEvent.Block event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        BlockPos pos = event.getTarget().getBlockPos();
        BlockState state = minecraft.level.getBlockState(pos);
        BlockPos master;
        if (state.getBlock() instanceof CollapseCradleBlock) {
            master = pos;
        } else if (state.getBlock() instanceof CollapseCradlePartBlock
                && CollapseCradlePartBlock.stillValid(minecraft.level, pos, state)) {
            master = CollapseCradlePartBlock.master(pos, state);
        } else {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        LevelRenderer.renderLineBox(poseStack, event.getMultiBufferSource().getBuffer(RenderType.lines()),
                machine(master).inflate(0.002D).move(-eye.x, -eye.y, -eye.z), 0.0F, 0.0F, 0.0F, 0.4F);
        event.setCanceled(true);
    }
}
