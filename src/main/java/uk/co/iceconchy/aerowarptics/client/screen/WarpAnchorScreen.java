package uk.co.iceconchy.aerowarptics.client.screen;

import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorAccess;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorBlockEntity;
import uk.co.iceconchy.aerowarptics.client.screen.AWLayout.Rect;
import uk.co.iceconchy.aerowarptics.network.ClientboundNetworkPanelPacket;
import uk.co.iceconchy.aerowarptics.network.ServerboundConfigureAnchorPacket;
import uk.co.iceconchy.aerowarptics.network.ServerboundNetworkPacket;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkFilter;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkOption;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkRules;

import java.util.List;
import java.util.UUID;

/**
 * Configuration panel for a Warp Anchor: name it, put it on a network, set how high ships come in over it,
 * choose who may use it, switch it off.
 *
 * <p>Edits are sent as a single payload when the player saves; the server validates ownership and
 * name uniqueness and is free to reject them.
 *
 * <p>The network is typed as a name. One of the player's own is offered in a list under the box while it
 * has the keyboard; a name no network has founds one when the anchor is saved.
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
    private Button membersButton;

    private WarpAnchorAccess access;
    private boolean enabled;
    private int arrivalHeight;
    private boolean draggingHeight;

    /** The networks this player is in, as the server last listed them; empty until it has. */
    private List<NetworkOption> mine = List.of();
    /**
     * The network named in the box, when it is one of the player's own, and the name it had when it was
     * recognised.
     *
     * <p>Kept so that a rename made in the members panel can be followed. The box holds a name, and the
     * old one, saved, would found a second network called what the first used to be.
     */
    @Nullable
    private UUID chosen;
    private String chosenName = "";
    private int listScroll;

    public WarpAnchorScreen(WarpAnchorBlockEntity anchor) {
        super(AWLang.translate("gui.warp_anchor.title").component());
        this.anchor = anchor;
        this.access = anchor.access();
        this.enabled = anchor.enabled();
        this.arrivalHeight = Math.min(anchor.arrivalHeight(), maximumHeight());
        this.chosen = anchor.networkId();
        this.chosenName = anchor.networkName();
    }

    /** The server's list of this player's networks, asked for as the panel opens. */
    public void acceptNetworks(ClientboundNetworkPanelPacket packet) {
        mine = packet.mine();
        listScroll = AWDropdown.clampScroll(listScroll, mine.size());
        String now = NetworkFilter.nameOf(mine, chosen);
        if (networkBox != null && !now.isBlank() && !now.equals(chosenName)
                && NetworkRules.key(networkBox.getValue()).equals(NetworkRules.key(chosenName))) {
            // Renamed since the box was filled, and the box still says the old name. Follow it.
            networkBox.setValue(now);
            chosenName = now;
        }
        updateMembers();
    }

    @Override
    protected void init() {
        setWindowSize(AWLayouts.ANCHOR_WIDTH, AWLayouts.ANCHOR_HEIGHT);
        super.init();
        guiLeft = AWLayout.anchor(width, AWLayouts.ANCHOR_WIDTH, guiLeft);
        guiTop = AWLayout.anchor(height, AWLayouts.ANCHOR_HEIGHT, guiTop);
        // Whatever was typed is kept across a rebuild. This runs again when the window is resized and when
        // the members panel closes back onto this one, and neither should cost a half-typed name.
        String typedName = nameBox == null ? anchor.anchorName() : nameBox.getValue();
        String typedNetwork = networkBox == null ? anchor.networkName() : networkBox.getValue();
        clearWidgets();

        Rect name = LAYOUT.name();
        nameBox = new EditBox(font, guiLeft + name.x(), guiTop + name.y(), name.width(), name.height(),
                AWLang.translate("gui.warp_anchor.name").component());
        nameBox.setMaxLength(48);
        nameBox.setValue(typedName);
        addRenderableWidget(nameBox);

        Rect network = LAYOUT.network();
        networkBox = new EditBox(font, guiLeft + network.x(), guiTop + network.y(),
                network.width(), network.height(),
                AWLang.translate("gui.warp_anchor.network").component());
        networkBox.setMaxLength(NetworkRules.NAME_MAX);
        networkBox.setValue(typedNetwork);
        addRenderableWidget(networkBox);

        Rect members = LAYOUT.members();
        membersButton = Button.builder(AWLang.translate("gui.network.members").component(), b -> openMembers())
                .bounds(guiLeft + members.x(), guiTop + members.y(), members.width(), members.height()).build();
        addRenderableWidget(membersButton);

        Rect accessRect = LAYOUT.access();
        accessButton = Button.builder(accessLabel(), b -> {
            access = access.next(!networkBox.getValue().isBlank());
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
        updateMembers();
        // Asked every time the panel is built, so the list - and a network renamed in the members panel -
        // is fresh whenever this one comes back to the front.
        PacketDistributor.sendToServer(ServerboundNetworkPacket.list(null));
    }

    /**
     * Works out which of the player's networks the box names, if any, and whether there is a members panel
     * to open for it.
     *
     * <p>Only for a network the player is already in. A name typed for the first time is no network until
     * the anchor is saved, so there is nobody in it to show.
     */
    private void updateMembers() {
        if (networkBox == null || membersButton == null) {
            return;
        }
        String typed = NetworkRules.key(networkBox.getValue());
        chosen = null;
        for (NetworkOption option : mine) {
            if (!typed.isEmpty() && NetworkRules.key(option.name()).equals(typed)) {
                chosen = option.id();
                chosenName = option.name();
            }
        }
        membersButton.active = chosen != null;
        if (access == WarpAnchorAccess.NETWORK && typed.isEmpty()) {
            // Network access with no network is what the server would save as Private. Say so now.
            access = WarpAnchorAccess.PRIVATE;
            accessButton.setMessage(accessLabel());
        }
    }

    private void openMembers() {
        if (chosen != null && minecraft != null) {
            minecraft.setScreen(new NetworkScreen(this, chosen));
        }
    }

    /** Whether the list of the player's networks is hanging under the box: while it has the keyboard. */
    private boolean listOpen() {
        return networkBox != null && networkBox.isFocused() && !mine.isEmpty();
    }

    private Rect networkList() {
        return AWDropdown.list(LAYOUT.network(), mine.size());
    }

    @Override
    public void tick() {
        super.tick();
        updateMembers();
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
        if (listOpen()) {
            // The list hangs over the slider and the buttons, so a click on it is the list's and nothing
            // underneath's. One anywhere else falls through, and takes the keyboard from the box as it goes.
            int picked = AWDropdown.optionAt(networkList(), mouseX - guiLeft, mouseY - guiTop, listScroll,
                    mine.size());
            if (picked >= 0) {
                networkBox.setValue(mine.get(picked).name());
                networkBox.setFocused(false);
                setFocused(null);
                updateMembers();
                return true;
            }
        }
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
        if (listOpen() && networkList().contains(mouseX - guiLeft, mouseY - guiTop)) {
            listScroll = AWDropdown.clampScroll(listScroll - (int) Math.signum(scrollY), mine.size());
            return true;
        }
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

    /** The player's own networks, hanging under the network box over whatever is beneath it. */
    @Override
    protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderWindowForeground(graphics, mouseX, mouseY, partialTicks);
        if (!listOpen()) {
            return;
        }
        Rect list = networkList();
        int left = guiLeft + list.x();
        int top = guiTop + list.y();
        int scroll = AWDropdown.clampScroll(listScroll, mine.size());
        int rows = AWDropdown.visibleRows(mine.size());
        int hovered = AWDropdown.optionAt(list, mouseX - guiLeft, mouseY - guiTop, scroll, mine.size());

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);
        graphics.fill(left, top, left + list.width(), top + list.height(), 0xFF_16_12_1B);
        AWScreenStyle.frame(graphics, left, top, list.width(), list.height(), AWScreenStyle.ACCENT_DIM);
        for (int row = 0; row < rows; row++) {
            NetworkOption option = mine.get(row + scroll);
            int y = top + AWLayout.INSET + row * AWDropdown.ROW;
            boolean current = option.id().equals(chosen);
            if (row + scroll == hovered) {
                graphics.fill(left + 1, y, left + list.width() - 1, y + AWDropdown.ROW,
                        AWAnim.fade(AWScreenStyle.ACCENT, 0.18F));
            }
            if (current) {
                AWScreenStyle.selectionBar(graphics, left + 1, y + 1, AWDropdown.ROW - 2, AWScreenStyle.ACCENT);
            }
            graphics.drawString(font, AWScreenStyle.trim(font, option.name(), list.width() - 12),
                    left + 5, y + 2, current ? AWScreenStyle.ACCENT : AWScreenStyle.VALUE, false);
        }
        AWScreenStyle.scrollbar(graphics, left + list.width() - 1, top + AWLayout.INSET, rows * AWDropdown.ROW,
                mine.size(), rows, scroll);
        graphics.pose().popPose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
