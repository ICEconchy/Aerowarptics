package uk.co.iceconchy.aerowarptics.client.screen;

import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorAccess;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorBlockEntity;
import uk.co.iceconchy.aerowarptics.network.ServerboundConfigureAnchorPacket;
import uk.co.iceconchy.aerowarptics.util.AWLang;

/**
 * Configuration panel for a Warp Anchor: name it, group it, choose who may use it, switch it off.
 *
 * <p>Edits are sent as a single payload when the player saves; the server validates ownership and
 * name uniqueness and is free to reject them.
 */
@OnlyIn(Dist.CLIENT)
public class WarpAnchorScreen extends AbstractSimiScreen {

    private static final int WINDOW_WIDTH = 220;
    private static final int WINDOW_HEIGHT = 148;

    private static final Color PANEL = new Color(0xDD_16_12_1B, true);
    private static final Color BORDER_TOP = new Color(0xFF_5C_4A_36, true);
    private static final Color BORDER_BOTTOM = new Color(0xFF_2E_25_1B, true);

    private static final int COLOUR_TITLE = 0xFF_D9_C3_92;
    private static final int COLOUR_LABEL = 0xFF_7C_6C_57;
    private static final int COLOUR_VALUE = 0xFF_E6_E1_D6;

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
        setWindowSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        super.init();
        clearWidgets();

        nameBox = new EditBox(font, guiLeft + 12, guiTop + 38, WINDOW_WIDTH - 40, 16,
                AWLang.translate("gui.warp_anchor.name").component());
        nameBox.setMaxLength(48);
        nameBox.setValue(anchor.anchorName());
        addRenderableWidget(nameBox);

        networkBox = new EditBox(font, guiLeft + 12, guiTop + 70, WINDOW_WIDTH - 40, 16,
                AWLang.translate("gui.warp_anchor.network").component());
        networkBox.setMaxLength(24);
        networkBox.setValue(anchor.network());
        addRenderableWidget(networkBox);

        accessButton = Button.builder(accessLabel(), b -> {
            access = access.next();
            accessButton.setMessage(accessLabel());
        }).bounds(guiLeft + 12, guiTop + 94, 92, 18).build();
        addRenderableWidget(accessButton);

        enabledButton = Button.builder(enabledLabel(), b -> {
            enabled = !enabled;
            enabledButton.setMessage(enabledLabel());
        }).bounds(guiLeft + 110, guiTop + 94, 92, 18).build();
        addRenderableWidget(enabledButton);

        addRenderableWidget(Button.builder(AWLang.translate("gui.warp_anchor.save").component(), b -> {
            save();
            onClose();
        }).bounds(guiLeft + 12, guiTop + 118, 190, 18).build());

        setInitialFocus(nameBox);
    }

    private net.minecraft.network.chat.Component accessLabel() {
        return AWLang.translate(access.translationKey()).component();
    }

    private net.minecraft.network.chat.Component enabledLabel() {
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
        new BoxElement()
                .withBackground(PANEL)
                .gradientBorder(BORDER_TOP, BORDER_BOTTOM)
                .at(guiLeft, guiTop)
                .withBounds(WINDOW_WIDTH - 8, WINDOW_HEIGHT - 8)
                .render(graphics);

        graphics.drawString(font, AWLang.translate("gui.warp_anchor.title").component(),
                guiLeft + 12, guiTop + 10, COLOUR_TITLE, false);

        String coords = anchor.getBlockPos().getX() + " / " + anchor.getBlockPos().getY()
                + " / " + anchor.getBlockPos().getZ();
        graphics.drawString(font, coords, guiLeft + 12, guiTop + 22, COLOUR_LABEL, false);

        String owner = anchor.ownerName().isBlank()
                ? AWLang.translate("gui.warp_anchor.unowned").string()
                : anchor.ownerName();
        graphics.drawString(font, owner,
                guiLeft + WINDOW_WIDTH - 20 - font.width(owner), guiTop + 22, COLOUR_LABEL, false);

        graphics.drawString(font, AWLang.translate("gui.warp_anchor.name").component(),
                guiLeft + 12, guiTop + 29, COLOUR_LABEL, false);
        graphics.drawString(font, AWLang.translate("gui.warp_anchor.network").component(),
                guiLeft + 12, guiTop + 61, COLOUR_LABEL, false);

        graphics.drawString(font, AWLang.translate(anchor.status().translationKey()).component(),
                guiLeft + WINDOW_WIDTH - 20 - font.width(
                        AWLang.translate(anchor.status().translationKey()).string()),
                guiTop + 10, COLOUR_VALUE, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
