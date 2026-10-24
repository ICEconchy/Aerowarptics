package uk.co.iceconchy.aerowarptics.client.fx;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.gate.GatePassage;
import uk.co.iceconchy.aerowarptics.gate.GatePassageStyle;
import uk.co.iceconchy.aerowarptics.gate.PassageFrame;
import uk.co.iceconchy.aerowarptics.network.ClientboundGatePassagePacket;
import uk.co.iceconchy.aerowarptics.registry.AWSounds;
import uk.co.iceconchy.aerowarptics.trace.ClientTrace;

import java.util.HashMap;
import java.util.Map;

/**
 * What a passage through a Rift Gate looks like to the one going through it - and, to everybody else,
 * who is not to be drawn while they are.
 *
 * <p>The server has already put this player at the far gate by the time any of this starts. So the first
 * job is not decoration: for as long as the passage lasts the whole view is covered, hand and HUD with
 * it, and the player's own keys do nothing. They are being held somewhere they cannot yet see, and a
 * client that let them walk would be walking them blind. What the cover is made of is the player's own
 * choice ({@code gatePassage}); that there is one is not.
 *
 * <p>Drawn as a GUI layer above every other. The GUI goes straight onto the screen after the world, so a
 * shader pack or Fabulous graphics - both of which rearrange how the world is drawn - have nothing to
 * say about it, and a layer a mod registers is drawn whether or not the HUD is hidden.
 *
 * <p>The timing is all {@link GatePassage}'s. This keeps three numbers - how long ago the passage began,
 * how long the server said it would last, and when it said it was over - and asks.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class GatePassageOverlay {

    private static boolean active;
    private static int age;
    private static int forecast;
    /** The age this client heard the passage was over at, or -1 while it has not. */
    private static int arrived = -1;
    private static double seed;
    private static GatePassageStyle style = GatePassageStyle.KAWOOSH;

    /** Travellers held at a far gate near here, by entity id, and the ticks before each is drawn regardless. */
    private static final Map<Integer, Integer> HIDDEN = new HashMap<>();

    private GatePassageOverlay() {
    }

    // ------------------------------------------------------------------ cues

    /** Takes the server's word that somebody has gone into a passage, or come out of one. */
    public static void accept(ClientboundGatePassagePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean own = minecraft.player != null && minecraft.player.getId() == packet.entityId();
        switch (packet.phase()) {
            case ENTER -> {
                if (own) {
                    enter(packet.ticks());
                } else {
                    // With the same patience this client would have for its own: a traveller whose
                    // arrival never reaches here is drawn again anyway, rather than never.
                    HIDDEN.put(packet.entityId(), packet.ticks() + GatePassage.LOST_TICKS);
                }
            }
            case ARRIVE -> {
                if (own) {
                    arrive();
                } else {
                    HIDDEN.remove(packet.entityId());
                }
            }
        }
    }

    private static void enter(int ticks) {
        active = true;
        age = 0;
        forecast = Math.max(1, ticks);
        arrived = -1;
        seed = Math.random() * Math.PI * 2.0D;
        GatePassageStyle chosen = AWConfig.GATE_PASSAGE.get();
        // A style whose shader did not load is the plain passage, not no passage.
        style = GatePassagePainter.drawable(chosen);
        ClientTrace.log(ClientTrace.CUE, "gate passage begun: {}t forecast, drawn as {}{}", forecast, style,
                chosen == style ? "" : " - " + chosen + " was chosen, and its shader did not load");
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(AWSounds.GATE_PASSAGE.get(),
                1.0F, 0.9F));
    }

    private static void arrive() {
        if (!active || arrived >= 0) {
            return;
        }
        arrived = age;
        ClientTrace.log(ClientTrace.CUE, "gate passage over at {}t of a forecast {}t", age, forecast);
        WarpScreenShake.add(0.35F);
    }

    public static void clear() {
        active = false;
        arrived = -1;
        HIDDEN.clear();
    }

    /** Whether this client's own player is in a passage. */
    public static boolean active() {
        return active;
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clear();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        if (active) {
            age++;
            if (GatePassage.over(age, forecast, arrived)) {
                if (arrived < 0) {
                    ClientTrace.log(ClientTrace.CUE, "gate passage cleared by itself after {}t: the server never"
                            + " said it was over", age);
                }
                active = false;
            }
        }
        if (!HIDDEN.isEmpty()) {
            HIDDEN.replaceAll((id, ticks) -> ticks - 1);
            HIDDEN.values().removeIf(ticks -> ticks <= 0);
        }
    }

    // ------------------------------------------------------------------ held

    /** A held traveller's keys do nothing: they have nothing to steer by. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!active || !GatePassage.held(age, forecast, arrived)) {
            return;
        }
        Input input = event.getInput();
        input.leftImpulse = 0.0F;
        input.forwardImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    /** Nor do their hands: no striking, breaking or placing at whatever happens to be in front of them. */
    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (active && GatePassage.held(age, forecast, arrived)) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    /** Somebody else's traveller, held at a gate near here, is not drawn until they are through. */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (!HIDDEN.isEmpty() && HIDDEN.containsKey(event.getEntity().getId())) {
            event.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- render

    /**
     * The lens, wide as the far side appears and easing back as the light thins.
     *
     * <p>Wide all through the passage too, where it cannot be seen, so that there is no moment at which
     * it visibly opens.
     */
    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (!active || !AWConfig.WARP_FOV.get()) {
            return;
        }
        double widen = GatePassage.fov(age + event.getPartialTick(), forecast, arrived);
        if (widen > 0.0D) {
            event.setFOV(event.getFOV() * (1.0D + widen));
        }
    }

    /** The layer itself, registered above every other in {@code AWClientSetup}. */
    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (!active) {
            return;
        }
        double now = age + delta.getGameTimeDeltaPartialTick(true);
        GatePassagePainter.draw(graphics, style, PassageFrame.at(now, forecast, arrived, seed));
    }
}
