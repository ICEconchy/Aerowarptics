package uk.co.iceconchy.aerowarptics.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import uk.co.iceconchy.aerowarptics.flotilla.CouplerRole;
import uk.co.iceconchy.aerowarptics.flotilla.FleetNames;
import uk.co.iceconchy.aerowarptics.network.ClientboundCouplerPanelPacket;
import uk.co.iceconchy.aerowarptics.network.ServerboundCouplerPacket;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.UUID;

/**
 * What the Fleet Board and the Station screen share: one coupler's panel, refreshed twice a second, a box
 * for renaming the ship, and the drawing both of them do.
 *
 * <p>Two screens rather than one with two modes, because they are about different things - a fleet and
 * one ship's place in it - and share almost no layout. The coupler's role picks which opens, and a role
 * changed while one is open swaps it for the other on the next refresh.
 */
@OnlyIn(Dist.CLIENT)
public abstract class CouplerScreen extends AWWindowScreen {

    private static final int REFRESH_INTERVAL = 10;

    protected ClientboundCouplerPanelPacket data;
    protected int ticksOpen;
    private int refreshTimer = REFRESH_INTERVAL;

    /** The rename box, while a rename is being typed; otherwise {@code null}. */
    protected EditBox renameBox;

    /** How long a notice stands on the info line: three seconds, long enough to read one sentence twice. */
    private static final int NOTICE_TICKS = 60;

    /** What the server last said about something asked from this screen, and how long it has left. */
    @Nullable
    private Component notice;
    private int noticeTicks;

    protected CouplerScreen(ClientboundCouplerPanelPacket data) {
        super(AWLang.translate("gui.rift_coupler.title").component());
        this.data = data;
    }

    public boolean matches(BlockPos pos) {
        return data.couplerPos().equals(pos);
    }

    /** Whether this screen is the one for a coupler in this role. */
    public abstract boolean shows(CouplerRole role);

    public void accept(ClientboundCouplerPanelPacket packet) {
        data = packet;
        if (!packet.notice().isEmpty()) {
            // Keyed and named as the rest of the panel is: the name raw, and an unnamed ship named here.
            notice = AWLang.translate(packet.notice(), shipName(packet.noticeArg())).component();
            noticeTicks = NOTICE_TICKS;
        }
        refresh();
    }

    /**
     * Draws the notice on the info line, if one is standing, and says whether it did - in which case the
     * line's own contents wait until it has gone.
     */
    protected boolean renderNotice(GuiGraphics graphics, AWLayout.Rect info) {
        if (notice == null || noticeTicks <= 0 || renameBox != null) {
            return false;
        }
        graphics.drawString(font, AWScreenStyle.trim(font, notice.getString(), info.width()),
                guiLeft + info.x(), guiTop + info.y(), AWScreenStyle.BAD, false);
        return true;
    }

    /** Brings buttons into line with a new panel. */
    protected abstract void refresh();

    // ------------------------------------------------------------------ sending

    protected void send(ServerboundCouplerPacket.Action action, UUID target, boolean flag) {
        PacketDistributor.sendToServer(ServerboundCouplerPacket.of(data.couplerPos(), action, target, flag));
    }

    protected void send(ServerboundCouplerPacket.Action action) {
        send(action, ClientboundCouplerPanelPacket.NIL, false);
    }

    protected void click(float pitch) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.25F, pitch);
        }
    }

    // ------------------------------------------------------------------ rename

    /** Opens the rename box over the info line, filled with the ship's current name. */
    protected void startRename(AWLayout.Rect info) {
        if (renameBox != null) {
            finishRename(false);
            return;
        }
        renameBox = new EditBox(font, guiLeft + info.x(), guiTop + info.y() - 2, info.width(), info.height() + 4,
                AWLang.translate("gui.rift_coupler.rename").component());
        renameBox.setMaxLength(FleetNames.MAX_LENGTH);
        renameBox.setValue(data.shipName());
        renameBox.setHint(AWLang.translate("gui.rift_coupler.rename_hint").component());
        addRenderableWidget(renameBox);
        setFocused(renameBox);
        renameBox.setFocused(true);
    }

    protected void finishRename(boolean send) {
        if (renameBox == null) {
            return;
        }
        if (send) {
            PacketDistributor.sendToServer(ServerboundCouplerPacket.rename(data.couplerPos(), renameBox.getValue()));
            click(1.2F);
        }
        removeWidget(renameBox);
        renameBox = null;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (renameBox != null) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                finishRename(true);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                finishRename(false);
                return true;
            }
            return renameBox.keyPressed(key, scanCode, modifiers) || renameBox.canConsumeInput()
                    || super.keyPressed(key, scanCode, modifiers);
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    // ---------------------------------------------------------------- ticking

    @Override
    public void tick() {
        super.tick();
        ticksOpen++;
        if (noticeTicks > 0) {
            noticeTicks--;
        }
        if (--refreshTimer <= 0) {
            refreshTimer = REFRESH_INTERVAL;
            PacketDistributor.sendToServer(ServerboundCouplerPacket.open(data.couplerPos()));
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return windowClicked(unscaleX(mouseX), unscaleY(mouseY), button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return windowReleased(unscaleX(mouseX), unscaleY(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return windowDragged(unscaleX(mouseX), unscaleY(mouseY), button, dragX / fitScale, dragY / fitScale);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return windowScrolled(unscaleX(mouseX), unscaleY(mouseY), scrollY);
    }

    /** The pointer, already in window coordinates. */
    protected boolean windowClicked(double x, double y, int button) {
        return super.mouseClicked(x, y, button);
    }

    protected boolean windowReleased(double x, double y, int button) {
        return super.mouseReleased(x, y, button);
    }

    protected boolean windowDragged(double x, double y, int button, double dx, double dy) {
        return super.mouseDragged(x, y, button, dx, dy);
    }

    protected boolean windowScrolled(double x, double y, double amount) {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ----------------------------------------------------------------- drawing

    /** A ship's name, or "Unnamed vessel" in the player's own language. */
    protected static String shipName(String raw) {
        return raw == null || raw.isBlank() ? AWLang.translate("gui.rift_navigation.unnamed_ship").string() : raw;
    }

    /**
     * The harmony glyph: a short wave that settles as a drive finds the flagship's note.
     *
     * <p>Drawn rather than written - a tilde-wave character is not in every font Minecraft might be using
     * - and ragged in proportion to how far the drive still has to go: each column is jittered off the
     * clean sine by a noise that shrinks to nothing at full harmony. A steady wave is a harmonised drive,
     * which is the whole of what a pilot needs to see at a glance.
     */
    protected static void wave(GuiGraphics graphics, int x, int y, int width, float harmony, int colour, float ticks) {
        float rough = 1.0F - AWAnim.clamp(harmony);
        int middle = y + 3;
        int previous = Integer.MIN_VALUE;
        for (int column = 0; column < width; column++) {
            double phase = column * 0.9D + ticks * 0.35D;
            double clean = Math.sin(phase) * 2.2D;
            double jitter = Math.sin(column * 12.9898D + Math.floor(ticks / 2.0D) * 78.233D) * 43758.5453D;
            jitter = (jitter - Math.floor(jitter)) * 2.0D - 1.0D;
            int offset = (int) Math.round(clean + jitter * 2.5D * rough);
            int row = middle + Math.max(-3, Math.min(3, offset));
            int from = previous == Integer.MIN_VALUE ? row : Math.min(previous, row);
            int to = previous == Integer.MIN_VALUE ? row : Math.max(previous, row);
            graphics.fill(x + column, from, x + column + 1, to + 1, colour);
            previous = row;
        }
    }

    /** The colour a station status is drawn in. */
    protected static int statusColour(uk.co.iceconchy.aerowarptics.flotilla.StationStatus status) {
        return switch (status) {
            case READY -> AWScreenStyle.OK;
            case HARMONISING, SURVEYING, UNCHARGED -> AWScreenStyle.WARN;
            default -> AWScreenStyle.BAD;
        };
    }
}
