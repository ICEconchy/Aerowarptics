package uk.co.iceconchy.aerowarptics.client.screen;

import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorAccess;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorBlockEntity;
import uk.co.iceconchy.aerowarptics.client.screen.AWLayout.Rect;
import uk.co.iceconchy.aerowarptics.network.ServerboundConfigureAnchorPacket;
import uk.co.iceconchy.aerowarptics.util.AWLang;

/**
 * Configuration panel for a Warp Anchor: name it, group it, set how high ships come in over it, choose
 * who may use it, switch it off.
 *
 * <p>Edits are sent as a single payload when the player saves; the server validates ownership and
 * name uniqueness and is free to reject them.
 *
 * <p>The only screen here with nothing live on it, so it is the only one with nothing moving. An
 * anchor is a label, not a machine, and a panel that pulsed while somebody typed a name into it would
 * be animation for its own sake.
 */
@OnlyIn(Dist.CLIENT)
public class WarpAnchorScreen extends AbstractSimiScreen {

    private static final AWLayouts.Anchor LAYOUT = AWLayouts.anchor();

    private final WarpAnchorBlockEntity anchor;

    private EditBox nameBox;
    private EditBox networkBox;
    private Button accessButton;
    private Button enabledButton;

    private WarpAnchorAccess access;
    private boolean enabled;
    private int arrivalHeight;
    private boolean draggingHeight;

    public WarpAnchorScreen(WarpAnchorBlockEntity anchor) {
        super(AWLang.translate("gui.warp_anchor.title").component());
        this.anchor = anchor;
        this.access = anchor.access();
        this.enabled = anchor.enabled();
        this.arrivalHeight = Math.min(anchor.arrivalHeight(), maximumHeight());
    }

    @Override
    protected void init() {
        setWindowSize(AWLayouts.ANCHOR_WIDTH, AWLayouts.ANCHOR_HEIGHT);
        super.init();
        guiLeft = AWLayout.anchor(width, AWLayouts.ANCHOR_WIDTH, guiLeft);
        guiTop = AWLayout.anchor(height, AWLayouts.ANCHOR_HEIGHT, guiTop);
        clearWidgets();

        Rect name = LAYOUT.name();
        nameBox = new EditBox(font, guiLeft + name.x(), guiTop + name.y(), name.width(), name.height(),
                AWLang.translate("gui.warp_anchor.name").component());
        nameBox.setMaxLength(48);
        nameBox.setValue(anchor.anchorName());
        addRenderableWidget(nameBox);

        Rect network = LAYOUT.network();
        networkBox = new EditBox(font, guiLeft + network.x(), guiTop + network.y(),
                network.width(), network.height(),
                AWLang.translate("gui.warp_anchor.network").component());
        networkBox.setMaxLength(24);
        networkBox.setValue(anchor.network());
        addRenderableWidget(networkBox);

        Rect accessRect = LAYOUT.access();
        accessButton = Button.builder(accessLabel(), b -> {
            access = access.next();
            accessButton.setMessage(accessLabel());
        }).bounds(guiLeft + accessRect.x(), guiTop + accessRect.y(),
                accessRect.width(), accessRect.height()).build();
        addRenderableWidget(accessButton);

        Rect enabledRect = LAYOUT.enabled();
        enabledButton = Button.builder(enabledLabel(), b -> {
            enabled = !enabled;
            enabledButton.setMessage(enabledLabel());
        }).bounds(guiLeft + enabledRect.x(), guiTop + enabledRect.y(),
                enabledRect.width(), enabledRect.height()).build();
        addRenderableWidget(enabledButton);

        Rect save = LAYOUT.save();
        addRenderableWidget(Button.builder(AWLang.translate("gui.warp_anchor.save").component(), b -> {
            save();
            onClose();
        }).bounds(guiLeft + save.x(), guiTop + save.y(), save.width(), save.height()).build());

        setInitialFocus(nameBox);
    }

    private Component accessLabel() {
        return AWLang.translate(access.translationKey()).component();
    }

    private Component enabledLabel() {
        return AWLang.translate(enabled ? "gui.warp_anchor.enabled" : "gui.warp_anchor.disabled").component();
    }

    private void save() {
        PacketDistributor.sendToServer(new ServerboundConfigureAnchorPacket(
                anchor.getBlockPos(),
                nameBox.getValue().trim(),
                access,
                networkBox.getValue().trim(),
                enabled,
                arrivalHeight));
    }

    private int maximumHeight() {
        return Math.max(0, anchor.maximumArrivalHeight());
    }

    // ------------------------------------------------------------------ input

    // Nothing here is sent until Save, like every other field on this form: the slider only moves the
    // number that Save will send, so dragging it about and then closing the panel changes nothing.

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && LAYOUT.height().contains(mouseX - guiLeft, mouseY - guiTop)) {
            draggingHeight = true;
            dragHeight(mouseX);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingHeight) {
            dragHeight(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingHeight) {
            draggingHeight = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * A block at a time under the wheel.
     *
     * <p>A slider across a hundred-odd blocks moves one and a half of them per pixel, which is fine for
     * "high" or "low" and hopeless for "exactly level with the dock". The wheel is the fine adjustment.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0D && LAYOUT.height().contains(mouseX - guiLeft, mouseY - guiTop)) {
            arrivalHeight = Math.max(0, Math.min(maximumHeight(),
                    arrivalHeight + (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void dragHeight(double mouseX) {
        Rect track = AWLayouts.sliderBar(LAYOUT.height());
        double fraction = (mouseX - guiLeft - track.x()) / Math.max(1, track.width());
        fraction = Math.max(0.0D, Math.min(1.0D, fraction));
        arrivalHeight = (int) Math.round(fraction * maximumHeight());
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        AWScreenStyle.window(graphics, guiLeft, guiTop, AWLayouts.ANCHOR_WIDTH, AWLayouts.ANCHOR_HEIGHT);

        String coords = anchor.getBlockPos().getX() + " / " + anchor.getBlockPos().getY()
                + " / " + anchor.getBlockPos().getZ();
        String owner = anchor.ownerName().isBlank()
                ? AWLang.translate("gui.warp_anchor.unowned").string()
                : anchor.ownerName();

        AWScreenStyle.header(graphics, font, guiLeft, guiTop, LAYOUT.header(),
                AWLang.translate("gui.warp_anchor.title").component(),
                coords,
                owner,
                AWLang.translate(anchor.status().translationKey()).component(),
                AWScreenStyle.VALUE,
                AWScreenStyle.LABEL);

        // Each field's label sits directly above its box, which is what the layout's taller bands
        // leave room for.
        graphics.drawString(font, AWLang.translate("gui.warp_anchor.name").component(),
                guiLeft + LAYOUT.name().x(), guiTop + LAYOUT.name().y() - 10,
                AWScreenStyle.LABEL, false);
        graphics.drawString(font, AWLang.translate("gui.warp_anchor.network").component(),
                guiLeft + LAYOUT.network().x(), guiTop + LAYOUT.network().y() - 10,
                AWScreenStyle.LABEL, false);

        renderHeight(graphics);
    }

    /** The arrival height: caption and value on one line, the bar under them. */
    private void renderHeight(GuiGraphics graphics) {
        Rect band = LAYOUT.height();
        int left = guiLeft + band.x();
        int top = guiTop + band.y();

        graphics.drawString(font, AWLang.translate("gui.warp_anchor.arrival_height").component(),
                left, top, AWScreenStyle.LABEL, false);
        String value = AWLang.distance(arrivalHeight);
        graphics.drawString(font, value, left + band.width() - font.width(value), top,
                AWScreenStyle.VALUE, false);

        Rect track = AWLayouts.sliderBar(band);
        float fraction = maximumHeight() <= 0 ? 0.0F : arrivalHeight / (float) maximumHeight();
        AWScreenStyle.bar(graphics, guiLeft + track.x(), guiTop + track.y(),
                track.width(), track.height(), fraction, AWScreenStyle.ACCENT_DIM);

        int knob = guiLeft + track.x() + Math.round(track.width() * AWAnim.clamp(fraction));
        graphics.fill(knob - 2, guiTop + track.y() - 2, knob + 2, guiTop + track.y() + track.height() + 2,
                AWScreenStyle.ACCENT);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
