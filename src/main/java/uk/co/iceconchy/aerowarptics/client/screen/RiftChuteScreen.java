package uk.co.iceconchy.aerowarptics.client.screen;

import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.chute.RiftChute;
import uk.co.iceconchy.aerowarptics.client.screen.AWLayout.Rect;
import uk.co.iceconchy.aerowarptics.network.ClientboundChutePanelPacket;
import uk.co.iceconchy.aerowarptics.network.ServerboundChutePacket;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.List;
import java.util.UUID;

/**
 * A Rift Chute's panel: name it, see what it is doing, and bind it to another chute.
 *
 * <p>Built on the same list-and-detail arrangement as the gate's dial panel, deliberately. A player
 * who has bound a pair of gates already knows how to bind a pair of chutes, and a second idea of what
 * "pick a thing from a list" looks like would be a second thing to learn for no gain.
 *
 * <p>The one thing this panel has to be honest about is <em>why nothing is moving</em>. A chute that
 * has quietly stopped looks exactly like a chute with nothing to do, so the reason line is the most
 * load-bearing thing on the screen and is stated in plain language rather than as a status code.
 */
@OnlyIn(Dist.CLIENT)
public class RiftChuteScreen extends AbstractSimiScreen {

    private static final int REFRESH_INTERVAL = 20;

    private static final AWLayouts.Chute LAYOUT = AWLayouts.chute();
    private static final int VISIBLE_ROWS = LAYOUT.visibleRows();

    private ClientboundChutePanelPacket data;
    @Nullable
    private UUID selected;
    private int scroll;
    private int refreshTimer = REFRESH_INTERVAL;
    private int ticksOpen;

    /** The marker beside the chosen chute, which slides between rows rather than jumping. */
    private final AWAnim.Eased marker = new AWAnim.Eased(0.4F, -1.0F);

    /** The essence bar, which chases its reading so a fill reads as filling. */
    private final AWAnim.Eased fill = new AWAnim.Eased(0.25F, 0.0F);

    private EditBox nameBox;
    private Button bindButton;
    private Button accessButton;

    public RiftChuteScreen(ClientboundChutePanelPacket data) {
        super(AWLang.translate("gui.rift_chute.title").component());
        this.data = data;
        this.selected = data.partner();
        this.fill.snap(supplyFraction(data));
    }

    public boolean matches(BlockPos pos) {
        return data.chutePos().equals(pos);
    }

    public void accept(ClientboundChutePanelPacket packet) {
        boolean sameChute = packet.chutePos().equals(data.chutePos());
        this.data = packet;
        if (!sameChute) {
            selected = packet.partner();
            scroll = 0;
        }
        // A selection that is no longer in the list is a chute that has been broken or hidden since
        // the panel was opened. Fall back to whatever is actually bound rather than leaving a marker
        // pointing at a row that is not there.
        if (selected != null && packet.bindable().stream().noneMatch(c -> c.id().equals(selected))) {
            selected = packet.partner();
        }
        fill.set(supplyFraction(packet));
        updateButtons();
    }

    private static float supplyFraction(ClientboundChutePanelPacket packet) {
        return packet.capacity() <= 0 ? 0.0F : packet.essence() / (float) packet.capacity();
    }

    @Override
    protected void init() {
        setWindowSize(AWLayouts.CHUTE_WIDTH, AWLayouts.CHUTE_HEIGHT);
        super.init();
        guiLeft = AWLayout.anchor(width, AWLayouts.CHUTE_WIDTH, guiLeft);
        guiTop = AWLayout.anchor(height, AWLayouts.CHUTE_HEIGHT, guiTop);
        clearWidgets();

        Rect name = LAYOUT.name();
        nameBox = new EditBox(font, guiLeft + name.x(), guiTop + name.y(), name.width(), name.height(),
                AWLang.translate("gui.rift_chute.name").component());
        nameBox.setMaxLength(48);
        nameBox.setValue(data.name());
        nameBox.setResponder(value -> { });
        nameBox.setEditable(data.owned());
        addRenderableWidget(nameBox);

        Rect bind = LAYOUT.bind();
        bindButton = Button.builder(Component.empty(), b -> onBind())
                .bounds(guiLeft + bind.x(), guiTop + bind.y(), bind.width(), bind.height())
                .build();

        Rect access = LAYOUT.access();
        accessButton = Button.builder(Component.empty(), b -> {
            commitName();
            PacketDistributor.sendToServer(ServerboundChutePacket.cycleAccess(data.chutePos()));
            playClick(1.1F);
        }).bounds(guiLeft + access.x(), guiTop + access.y(), access.width(), access.height()).build();

        addRenderableWidget(bindButton);
        addRenderableWidget(accessButton);
        updateButtons();
    }

    private void onBind() {
        commitName();
        if (data.isBound()) {
            PacketDistributor.sendToServer(ServerboundChutePacket.unbind(data.chutePos()));
        } else if (selected != null) {
            PacketDistributor.sendToServer(ServerboundChutePacket.bind(data.chutePos(), selected));
        }
        playClick(1.2F);
    }

    /**
     * Sends the name if it has changed.
     *
     * <p>Sent on any other action rather than on every keystroke: a rename is a registry write and a
     * packet, and doing one per character typed would be an absurd amount of traffic for a label.
     */
    private void commitName() {
        if (nameBox != null && data.owned() && !nameBox.getValue().equals(data.name())) {
            PacketDistributor.sendToServer(
                    ServerboundChutePacket.rename(data.chutePos(), nameBox.getValue()));
        }
    }

    private void updateButtons() {
        if (bindButton == null || accessButton == null) {
            return;
        }
        bindButton.active = data.owned() && (data.isBound() || selected != null);
        bindButton.setMessage(AWLang.translate(
                data.isBound() ? "gui.rift_chute.unbind" : "gui.rift_chute.bind").component());
        accessButton.active = data.owned();
        accessButton.setMessage(AWLang.translate("gui.rift_chute.access",
                AWLang.translate(data.access().translationKey()).string()).component());
    }

    @Override
    public void tick() {
        super.tick();
        ticksOpen++;
        marker.tick();
        fill.tick();
        if (--refreshTimer <= 0) {
            refreshTimer = REFRESH_INTERVAL;
            PacketDistributor.sendToServer(ServerboundChutePacket.open(data.chutePos()));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Rect list = LAYOUT.list();
        double x = mouseX - guiLeft;
        double y = mouseY - guiTop;
        if (list.contains(x, y)) {
            int index = scroll + (int) ((y - list.y()) / AWLayout.ROW);
            List<RiftChute> chutes = data.bindable();
            if (index >= 0 && index < chutes.size()) {
                selected = chutes.get(index).id();
                updateButtons();
                playClick(1.0F);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maximum = Math.max(0, data.bindable().size() - VISIBLE_ROWS);
        scroll = Math.max(0, Math.min(maximum, scroll - (int) Math.signum(scrollY)));
        return true;
    }

    @Override
    public void removed() {
        commitName();
        super.removed();
    }

    // ---------------------------------------------------------------- render

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        AWScreenStyle.window(graphics, guiLeft, guiTop, AWLayouts.CHUTE_WIDTH, AWLayouts.CHUTE_HEIGHT);

        AWScreenStyle.header(graphics, font, guiLeft, guiTop, LAYOUT.header(),
                AWLang.translate("gui.rift_chute.title").component(),
                AWLang.translate(data.aboard()
                        ? "gui.rift_chute.aboard" : "gui.rift_chute.grounded").string(),
                "",
                AWLang.translate(data.riftOpen()
                        ? "gui.rift_chute.rift_open" : "gui.rift_chute.rift_collapsed").component(),
                data.riftOpen() ? AWScreenStyle.OK : AWScreenStyle.BAD,
                AWScreenStyle.LABEL);

        renderList(graphics, partialTicks);
        renderDetail(graphics, partialTicks);
    }

    private void renderList(GuiGraphics graphics, float partialTicks) {
        Rect list = LAYOUT.list();
        AWScreenStyle.panel(graphics, guiLeft, guiTop, list);

        int left = guiLeft + list.x();
        int top = guiTop + list.y();
        List<RiftChute> chutes = data.bindable();

        if (chutes.isEmpty()) {
            // Wrapped to the list's own width, not drawn as one line: "No other chutes in this world"
            // is wider than this panel, and left to run it spills across the gutter into the detail
            // panel beside it.
            int wrap = list.width() - 6;
            int y = top + 4;
            for (net.minecraft.util.FormattedCharSequence piece : font.split(
                    AWLang.translate("gui.rift_chute.no_chutes").component(), wrap)) {
                graphics.drawString(font, piece, left + 4, y, AWScreenStyle.LABEL, false);
                y += 10;
            }
            return;
        }

        int shown = Math.min(VISIBLE_ROWS, chutes.size() - scroll);
        for (int row = 0; row < shown; row++) {
            RiftChute chute = chutes.get(scroll + row);
            int y = top + row * AWLayout.ROW;
            boolean isSelected = chute.id().equals(selected);
            boolean isPartner = chute.id().equals(data.partner());

            if (isSelected) {
                graphics.fill(left - AWLayout.INSET, y, left + list.width() - AWLayout.INSET,
                        y + AWLayout.ROW, AWScreenStyle.TRACK);
            }
            // The bound partner is marked in the list itself, not only in the detail panel. Without
            // it, a player scrolling a long list has no way to see which one they already chose.
            String mark = isPartner ? "> " : "  ";
            int colour = isPartner ? AWScreenStyle.OK
                    : isSelected ? AWScreenStyle.TITLE : AWScreenStyle.VALUE;
            String room = AWScreenStyle.trim(font, mark + chute.displayName(), list.width() - 10);
            graphics.drawString(font, room, left + 2, y + 3, colour, false);
        }

        if (chutes.size() > VISIBLE_ROWS) {
            AWScreenStyle.scrollbar(graphics, left + list.width() - 3, top,
                    list.height(), scroll, VISIBLE_ROWS, chutes.size());
        }
    }

    private void renderDetail(GuiGraphics graphics, float partialTicks) {
        Rect panel = LAYOUT.detail();
        AWScreenStyle.panel(graphics, guiLeft, guiTop, panel);

        int left = guiLeft + panel.x() + 2;
        int top = guiTop + panel.y() + 2;
        int width = panel.width() - 4;

        RiftChute partner = data.bindable().stream()
                .filter(c -> c.id().equals(data.partner()))
                .findFirst()
                .orElse(null);

        graphics.drawString(font, AWScreenStyle.trim(font, data.isBound() && partner != null
                        ? partner.displayName()
                        : AWLang.translate("gui.rift_chute.unbound").string(), width - 2),
                left, top, data.isBound() ? AWScreenStyle.TITLE : AWScreenStyle.LABEL, false);
        AWScreenStyle.rule(graphics, left, top + 10, width);

        int line = top + 15;
        line = AWScreenStyle.readout(graphics, font, left, line, width,
                AWLang.translate("gui.rift_chute.cost").component(),
                AWLang.essence(data.costPerItem()), AWScreenStyle.VALUE);

        // The essence bar, captioned above it the way the console's bars are.
        graphics.drawString(font, AWLang.translate("gui.rift_chute.supply").component(),
                left, line + 4, AWScreenStyle.LABEL, false);
        AWScreenStyle.bar(graphics, left, line + 15, width, AWLayouts.BAR,
                fill.get(partialTicks), AWScreenStyle.ACCENT);
        graphics.drawString(font, AWLang.essence(data.essence(), data.capacity()),
                left, line + 23, AWScreenStyle.LABEL, false);

        // The most important line on the screen: why nothing is moving.
        String reason = AWLang.translate("gui.rift_chute.reason." + reasonKey()).string();
        AWScreenStyle.pill(graphics, font, left, top + panel.height() - 22, reason, reasonColour());

        if (data.truncated()) {
            String note = AWLang.translate("gui.rift_chute.truncated",
                    data.bindable().size(), data.total()).string();
            graphics.drawString(font, AWScreenStyle.trim(font, note, width),
                    left, top + panel.height() - 10, AWScreenStyle.WARN, false);
        }
    }

    private String reasonKey() {
        return data.reason().name().toLowerCase(java.util.Locale.ROOT);
    }

    private int reasonColour() {
        return switch (data.reason()) {
            case READY -> AWScreenStyle.OK;
            case EMPTY, WARPING -> AWScreenStyle.LABEL;
            case PARTNER_FULL, PARTNER_ABSENT -> AWScreenStyle.WARN;
            case UNBOUND, NO_ESSENCE -> AWScreenStyle.BAD;
        };
    }

    private void playClick(float pitch) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
                    .forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch));
        }
    }
}
