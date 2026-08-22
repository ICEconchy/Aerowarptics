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
 * Configuration panel for a Warp Anchor: name it, group it, choose who may use it, switch it off.
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

    public WarpAnchorScreen(WarpAnchorBlockEntity anchor) {
        super(AWLang.translate("gui.warp_anchor.title").component());
        this.anchor = anchor;
        this.access = anchor.access();
        this.enabled = anchor.enabled();
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
                enabled));
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
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
