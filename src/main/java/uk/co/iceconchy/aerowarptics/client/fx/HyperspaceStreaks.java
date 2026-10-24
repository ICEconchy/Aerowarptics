package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.network.ClientboundSupercruisePacket;

/**
 * What a cruising crew sees: starlines.
 *
 * <p>The jump to lightspeed, done the way this mod does light. When the drive throws the ship into
 * cruise, points of light around it pull out into long lines rushing past; while it cruises, the lines
 * run faster and longer the faster it goes; when it lets go, they draw back into points and wink out.
 * All of it is additive light in the world around the camera - nothing here fogs, tints or fills the
 * screen, because every one of those colours the blocks, and a cruise has to leave the country ahead
 * looking like the country ahead. See {@link WarpCorridorOverlay} for how that was learnt.
 *
 * <p>And none of it is drawn straight ahead. {@link HyperspaceField} keeps a window open down the
 * heading, so however hard the lines are streaking the pilot is still looking at where the ship is
 * going. That window is what makes this a supercruise rather than a hyperspace jump: the destination is
 * something you fly at, not somewhere you arrive.
 *
 * <p>Only the crew get it, because only the crew are told - by name, from the server.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class HyperspaceStreaks {

    /** Ticks without word from the server before the effect lets go on its own. */
    private static final int SILENCE_TICKS = 40;

    /** Ticks the lines take to stretch out on engaging, and to draw back on letting go. */
    private static final int STRETCH_TICKS = 14;
    private static final int RELAX_TICKS = 18;

    /** Blocks the field scrolls per tick at full speed, before each line's own pace. Visual, not real. */
    private static final double SCROLL_AT_FULL = 7.0D;

    /** How much wider the view opens at full speed, and the extra kick the moment a jump engages. */
    private static final double FOV_AT_FULL = 0.16D;
    private static final double FOV_KICK = 0.22D;

    /** Width of a line at its bright end, in blocks, before speed thickens it. */
    private static final float WIDTH = 0.07F;

    /** Sky and block light written on every vertex. Starlight lights itself. */
    private static final int GLOW_LIGHT = 240;

    private static boolean active;
    private static boolean dropping;
    private static int silence;
    private static int colour = 0xBFE8FF;
    private static float strength = 1.0F;

    private static double targetSpeed;
    private static double speed;
    private static double lastSpeed;
    private static double stretch;
    private static double lastStretch;
    private static double kick;
    private static double lastKick;
    private static double scroll;
    private static double lastScroll;
    private static final Vector3d heading = new Vector3d(0.0D, 0.0D, 1.0D);
    private static final Vector3d targetHeading = new Vector3d(0.0D, 0.0D, 1.0D);

    private HyperspaceStreaks() {
    }

    /** Takes the server's word for how the cruise is going. */
    public static void accept(ClientboundSupercruisePacket packet) {
        if (packet.phase() == ClientboundSupercruisePacket.Phase.LEAVE) {
            active = false;
            dropping = true;
            return;
        }
        silence = SILENCE_TICKS;
        colour = packet.colour() & 0xFFFFFF;
        strength = Float.isFinite(packet.intensity()) ? Mth.clamp(packet.intensity(), 0.0F, 2.0F) : 1.0F;
        targetSpeed = Float.isFinite(packet.speed()) ? Mth.clamp(packet.speed(), 0.0F, 1.0F) : 0.0D;
        double hx = Float.isFinite(packet.headingX()) ? packet.headingX() : 0.0D;
        double hz = Float.isFinite(packet.headingZ()) ? packet.headingZ() : 0.0D;
        if (hx * hx + hz * hz > 1.0e-6D) {
            targetHeading.set(hx, 0.0D, hz).normalize();
        }
        switch (packet.phase()) {
            case ENGAGE -> {
                if (!active) {
                    // A fresh jump starts from points, not from wherever the last one left the lines.
                    heading.set(targetHeading);
                    stretch = 0.0D;
                    lastStretch = 0.0D;
                }
                active = true;
                dropping = false;
                kick = 1.0D;
                if (AWConfig.SCREEN_SHAKE.get()) {
                    WarpScreenShake.add(0.55F * strength);
                }
            }
            case CRUISE -> {
                active = true;
                dropping = false;
            }
            case DROP -> {
                active = true;
                dropping = true;
            }
            default -> {
            }
        }
    }

    public static void clear() {
        active = false;
        dropping = false;
        speed = lastSpeed = targetSpeed = 0.0D;
        stretch = lastStretch = 0.0D;
        kick = lastKick = 0.0D;
        scroll = lastScroll = 0.0D;
    }

    /** Whether anything is being drawn, for tests of the client's wiring and for the lens. */
    public static boolean visible() {
        return stretch > 0.001D;
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.level instanceof ClientLevel)) {
            clear();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        if (active && --silence <= 0) {
            // The server stopped saying anything - a lost packet, a dimension change, a ship that went
            // away. Let the lines draw back rather than streak on forever.
            active = false;
            dropping = true;
        }

        lastSpeed = speed;
        lastStretch = stretch;
        lastKick = kick;
        lastScroll = scroll;

        double wantSpeed = active ? targetSpeed : 0.0D;
        speed += (wantSpeed - speed) * 0.2D;
        double wantStretch = active && !dropping ? 1.0D : 0.0D;
        stretch = wantStretch > stretch
                ? Math.min(wantStretch, stretch + 1.0D / STRETCH_TICKS)
                : Math.max(wantStretch, stretch - 1.0D / RELAX_TICKS);
        kick = Math.max(0.0D, kick - 1.0D / 12.0D);
        scroll += SCROLL_AT_FULL * Math.max(speed, 0.15D * stretch);
        // Turned towards the new heading over a few ticks, so a ship coming about swings the tube round
        // rather than snapping it.
        heading.lerp(targetHeading, 0.25D);
        if (heading.lengthSquared() < 1.0e-6D) {
            heading.set(targetHeading);
        }
        heading.normalize();
        if (!active && stretch <= 0.0D) {
            dropping = false;
            scroll = lastScroll = 0.0D;
        }
    }

    // ---------------------------------------------------------------- render

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // Under a shader pack this is drawn once the pack has finished the frame - see AfterShaders.
        if (AfterShaders.defer(event)) {
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !visible()
                || !AWConfig.WARP_CORRIDOR.get()) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double drawSpeed = Mth.lerp(partialTick, lastSpeed, speed);
        double drawStretch = Mth.lerp(partialTick, lastStretch, stretch);
        double drawScroll = Mth.lerp(partialTick, lastScroll, scroll);
        // On engaging, the lines are pulled out further than they will settle at: the lurch of the jump.
        double drawKick = Mth.lerp(partialTick, lastKick, kick);
        double lineSpeed = Math.max(drawSpeed, drawStretch * 0.35D);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        // No translation to the camera: the field is already in camera-relative coordinates.
        Matrix4f matrix = poseStack.last().pose();
        MultiBufferSource.BufferSource buffers = AfterShaders.buffers();
        VertexConsumer buffer = buffers.getBuffer(AWRenderTypes.RIFT_FIRE);

        float[] tint = {((colour >> 16) & 0xFF) / 255.0F, ((colour >> 8) & 0xFF) / 255.0F,
                (colour & 0xFF) / 255.0F};
        float fade = Mth.clamp(strength, 0.0F, 1.5F);
        for (int index = 0; index < HyperspaceField.STREAKS; index++) {
            HyperspaceField.Streak streak = HyperspaceField.streak(index, heading, drawScroll,
                    Math.min(1.0D, lineSpeed * (1.0D + drawKick * 0.6D)), drawStretch);
            if (streak.alpha() <= 0.004F) {
                continue;
            }
            line(buffer, matrix, streak, tint, streak.alpha() * fade, (float) (WIDTH * (1.0D + drawSpeed)));
        }
        buffers.endBatch(AWRenderTypes.RIFT_FIRE);
        poseStack.popPose();
    }

    /**
     * One starline: a flat ribbon turned to face the viewer, white-hot at its leading end and fading into
     * the drive's colour, then to nothing, along its length.
     */
    private static void line(VertexConsumer buffer, Matrix4f matrix, HyperspaceField.Streak streak,
                             float[] tint, float alpha, float width) {
        Vector3d head = streak.head();
        Vector3d tail = streak.tail();
        Vector3d along = new Vector3d(tail).sub(head);
        if (along.lengthSquared() < 1.0e-6D) {
            return;
        }
        // Across the line and across the view: the camera is at the origin, so the midpoint is the view.
        Vector3d mid = new Vector3d(head).add(tail).mul(0.5D);
        Vector3d side = new Vector3d(along).cross(mid);
        if (side.lengthSquared() < 1.0e-9D) {
            return;
        }
        side.normalize().mul(width);
        float headAlpha = Math.min(1.0F, alpha);
        float tailAlpha = 0.0F;
        // White at the bright end, the drive's tint behind it - starlight with the drive's colour in its wake.
        float hr = 0.85F + 0.15F * tint[0];
        float hg = 0.9F + 0.1F * tint[1];
        float hb = 1.0F;
        vertex(buffer, matrix, head.x - side.x, head.y - side.y, head.z - side.z, hr, hg, hb, headAlpha);
        vertex(buffer, matrix, head.x + side.x, head.y + side.y, head.z + side.z, hr, hg, hb, headAlpha);
        vertex(buffer, matrix, tail.x + side.x * 0.3D, tail.y + side.y * 0.3D, tail.z + side.z * 0.3D,
                tint[0], tint[1], tint[2], tailAlpha);
        vertex(buffer, matrix, tail.x - side.x * 0.3D, tail.y - side.y * 0.3D, tail.z - side.z * 0.3D,
                tint[0], tint[1], tint[2], tailAlpha);
    }

    /**
     * The one place this class puts a vertex into a buffer.
     *
     * <p>{@code RIFT_FIRE} carries the beacon beam's vertex format, so every vertex owes a texture
     * coordinate, a light level and a normal on top of its colour. Leaving any off does not draw badly; it
     * throws {@code Missing elements in vertex} mid-frame - see {@code RiftVertexFormatTest}.
     */
    private static void vertex(VertexConsumer buffer, Matrix4f matrix, double x, double y, double z,
                               float red, float green, float blue, float alpha) {
        buffer.addVertex(matrix, (float) x, (float) y, (float) z)
                .setColor(Math.min(1.0F, red), Math.min(1.0F, green), Math.min(1.0F, blue), alpha)
                .setUv(0.5F, 0.5F)
                .setUv2(GLOW_LIGHT, GLOW_LIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    /** The lens: a touch wider the faster the ship goes, and a jolt wider the moment it jumps. */
    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (!visible() || !AWConfig.WARP_FOV.get()) {
            return;
        }
        float partialTick = (float) event.getPartialTick();
        double drawSpeed = Mth.lerp(partialTick, lastSpeed, speed);
        double drawKick = Mth.lerp(partialTick, lastKick, kick);
        double drawStretch = Mth.lerp(partialTick, lastStretch, stretch);
        double widen = (FOV_AT_FULL * drawSpeed + FOV_KICK * drawKick * drawKick) * drawStretch;
        event.setFOV(event.getFOV() * (1.0D + widen * Mth.clamp(strength, 0.0F, 1.0F)));
    }
}
