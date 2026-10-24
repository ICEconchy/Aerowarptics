package uk.co.iceconchy.aerowarptics.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.network.ClientboundSupercruisePacket;
import uk.co.iceconchy.aerowarptics.rail.RailCorridor;

/**
 * What a rider sees in the Rift Corridor.
 *
 * <p>A violet void with no sky, no sun and no clouds, and - while they are riding a train through it - the same
 * starlines a ship's crew sees in a free cruise, streaming past along the line of the track. The corridor is a
 * few seconds of track in an empty dimension, and without something moving past, a train in it looks as if it
 * has stopped: there is nothing to see go by.
 *
 * <p>Driven from the client alone. The server sends nothing: the player is in the corridor and on a carriage, or
 * they are not, and the train's own motion is the heading and the pace.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class CorridorView {

    /** The corridor's sky effects, named by its dimension type. */
    public static final net.minecraft.resources.ResourceLocation EFFECTS = AeroWarptics.id("rift_corridor");

    /** The void's colour, which is also the fog's: the darkest of the gate pane's violets. */
    private static final Vec3 VOID = new Vec3(0.07D, 0.03D, 0.12D);

    /** The starlines' colour, the pane's own. */
    private static final int STREAK_COLOUR = 0xC86CFF;

    /** Blocks a tick that count as full speed for the lines - about Create's fastest train. */
    private static final double FULL_PACE = 2.0D;

    private static boolean riding;
    private static Vec3 lastPosition;

    private CorridorView() {
    }

    /** A mod-bus event; the subscriber puts it there by its type, as it does the shaders in AWClientSetup. */
    @SubscribeEvent
    public static void registerEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(EFFECTS, effects());
    }

    static DimensionSpecialEffects effects() {
        return new DimensionSpecialEffects(Float.NaN, false, DimensionSpecialEffects.SkyType.NONE, false, false) {
            @Override
            public Vec3 getBrightnessDependentFogColor(Vec3 fog, float brightness) {
                return VOID;
            }

            @Override
            public boolean isFoggyAt(int x, int y) {
                return false;
            }
        };
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()) {
            return;
        }
        ClientLevel level = minecraft.level;
        Entity vehicle = minecraft.player == null ? null : minecraft.player.getRootVehicle();
        boolean inCorridor = level != null && RailCorridor.isCorridor(level) && vehicle != null
                && vehicle != minecraft.player;
        if (!inCorridor) {
            if (riding) {
                HyperspaceStreaks.accept(new ClientboundSupercruisePacket(ClientboundSupercruisePacket.Phase.LEAVE,
                        0.0F, 0.0F, 0.0F, STREAK_COLOUR, 1.0F));
            }
            riding = false;
            lastPosition = null;
            return;
        }
        Vec3 now = vehicle.position();
        Vec3 moved = lastPosition == null ? Vec3.ZERO : now.subtract(lastPosition);
        lastPosition = now;
        // The track runs along X, so the heading is east or west, whichever the train is going; standing still it
        // keeps facing the way the rider looks.
        double along = Math.abs(moved.x) > 1.0e-3D ? Math.signum(moved.x)
                : Math.signum(minecraft.player.getLookAngle().x == 0.0D ? 1.0D : minecraft.player.getLookAngle().x);
        float pace = (float) Math.max(0.25D, Math.min(1.0D, Math.abs(moved.x) / FULL_PACE));
        HyperspaceStreaks.accept(new ClientboundSupercruisePacket(
                riding ? ClientboundSupercruisePacket.Phase.CRUISE : ClientboundSupercruisePacket.Phase.ENGAGE,
                pace, (float) along, 0.0F, STREAK_COLOUR, 0.8F));
        riding = true;
    }
}
