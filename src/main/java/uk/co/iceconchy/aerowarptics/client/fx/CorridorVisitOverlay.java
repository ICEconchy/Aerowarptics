package uk.co.iceconchy.aerowarptics.client.fx;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.trace.ClientTrace;
import uk.co.iceconchy.aerowarptics.util.AWLang;

/**
 * The clock on the view of somebody standing in the Rift Corridor.
 *
 * <p>A visit through a gate's own opening is not open-ended - the corridor puts everybody out in the end - so the
 * one thing a visitor needs to know is how long they have to get a stopped train moving. Said on the view, where
 * they are looking, and never in chat: a line of chat a second while somebody works is not a clock, it is a wall
 * of text with the answer buried in it.
 *
 * <p>The server is the one counting, because the clock stops while a visitor is riding a train, which is the whole
 * point of going in. It re-sends the figure each second and this counts the ticks between, so the seconds fall
 * steadily rather than in jumps - and runs out at zero rather than racing past it, in case a word is lost.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class CorridorVisitOverlay {

    /** Below this many ticks left, the clock reads as a warning rather than a figure. */
    private static final int URGENT_TICKS = 200;

    /** The rift's own violet, for a clock that belongs to the place it is counting down in. */
    private static final int CALM = 0xFFC9A9FF;

    private static final int URGENT = 0xFFFF9955;

    /** What the server last said was left, or -1 when the player is not in a corridor at all. */
    private static int told = -1;

    /** Client ticks since that was said, so the seconds fall between words. */
    private static int since;

    private CorridorVisitOverlay() {
    }

    /** What the server says is left of this player's visit; zero or less means they are out. */
    public static void set(int ticks) {
        if (ticks <= 0) {
            if (told >= 0) {
                ClientTrace.log(ClientTrace.RAIL, "the corridor says the visit is over");
            }
            told = -1;
            since = 0;
            return;
        }
        if (told < 0) {
            ClientTrace.log(ClientTrace.RAIL, "in the corridor, with {} ticks to look round", ticks);
        }
        told = ticks;
        since = 0;
    }

    /** Forgotten on the way out of a world, or a clock from one world would hang over the next. */
    public static void clear() {
        told = -1;
        since = 0;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (told >= 0 && since < told) {
            since++;
        }
    }

    private static int left() {
        return told < 0 ? -1 : Math.max(0, told - since);
    }

    /** The layer itself, registered in {@code AWClientSetup}. */
    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        int left = left();
        if (left < 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        Component line = Component.literal(AWLang.clock(left));
        Component about = AWLang.component("gate.rail.visit", line.getString());
        int colour = left <= URGENT_TICKS ? URGENT : CALM;
        int width = minecraft.font.width(about);
        // Top centre, clear of the hotbar a visitor is working out of and of the chat they may be reading.
        graphics.drawString(minecraft.font, about, (graphics.guiWidth() - width) / 2, 12, colour, true);
    }
}
