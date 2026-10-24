package uk.co.iceconchy.aerowarptics.client.screen;

import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import uk.co.iceconchy.aerowarptics.client.screen.AWLayout.Rect;
import uk.co.iceconchy.aerowarptics.network.ClientboundNetworkPanelPacket;
import uk.co.iceconchy.aerowarptics.network.ServerboundNetworkPacket;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkNotice;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkRules;
import uk.co.iceconchy.aerowarptics.warpnet.WarpNetwork;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A network's members panel: who is in it, and - for its owner - who is let in and put out.
 *
 * <p>Opened from a machine that is on the network, and it goes back to that machine's panel when it is
 * closed. It is a view over what the server sent, like the rest: every button sends an order and waits to
 * be told what came of it, so a member looking at this panel cannot do an owner's work by pressing a
 * button the panel should not have drawn.
 *
 * <p>Nothing here is on a timer but the refresh. A network is a list of names; there is nothing to animate.
 */
@OnlyIn(Dist.CLIENT)
public class NetworkScreen extends AbstractSimiScreen {

    private static final int REFRESH_INTERVAL = 40;

    /** How long a Leave or Disband button waits for its second press before it forgets the first. */
    private static final int CONFIRM_TICKS = 60;

    /** How long the info line goes on saying what came of the last order. */
    private static final int NOTICE_TICKS = 120;

    private static final AWLayouts.Network LAYOUT = AWLayouts.network();
    private static final int VISIBLE_ROWS = LAYOUT.visibleRows();

    /** One line of the member list. */
    private record Row(UUID player, String name, boolean owner) {
    }

    private final Screen parent;
    private final UUID networkId;

    /** The network as the server last sent it, or {@code null} until it has answered. */
    @Nullable
    private WarpNetwork network;
    private List<Row> rows = List.of();
    @Nullable
    private UUID selected;
    private int scroll;
    private int refreshTimer;
    private int confirmTicks;
    private NetworkNotice notice = NetworkNotice.NONE;
    private int noticeTicks;

    private EditBox nameBox;
    private EditBox addBox;
    private Button addButton;
    private Button removeButton;
    private Button leaveButton;

    /**
     * @param parent    the panel this was opened from, shown again when this one closes
     * @param networkId the network to show
     */
    public NetworkScreen(Screen parent, UUID networkId) {
        super(AWLang.translate("gui.network.title").component());
        this.parent = parent;
        this.networkId = networkId;
    }

    /** The panel this one stands in front of. */
    public Screen parent() {
        return parent;
    }

    /** Takes the server's answer: the network as it now is, and what came of whatever was last asked. */
    public void accept(ClientboundNetworkPanelPacket packet) {
        if (!networkId.equals(packet.asked())) {
            // An answer to somebody else's question: the machine's panel behind this one asks for the
            // names of the player's networks, and that answer can still be on its way as this opens.
            return;
        }
        WarpNetwork sent = packet.viewing();
        if (sent == null) {
            // Gone, or no longer this player's to look at - which is also what a Leave or a Disband that
            // worked looks like from here. Either way there is nothing left to show.
            onClose();
            return;
        }
        network = sent;
        rows = rowsOf(sent);
        if (selected != null && rows.stream().noneMatch(row -> row.player().equals(selected))) {
            selected = null;
        }
        scroll = Math.max(0, Math.min(scroll, rows.size() - VISIBLE_ROWS));
        if (packet.notice().present()) {
            notice = packet.notice();
            noticeTicks = NOTICE_TICKS;
        }
        if (nameBox != null && !nameBox.isFocused()) {
            nameBox.setValue(sent.name());
        }
        updateWidgets();
    }

    /** The owner, then everybody else by name. */
    private static List<Row> rowsOf(WarpNetwork network) {
        List<Row> members = new ArrayList<>();
        for (Map.Entry<UUID, String> member : network.members().entrySet()) {
            members.add(new Row(member.getKey(), member.getValue(), false));
        }
        members.sort(Comparator.comparing(Row::name, String.CASE_INSENSITIVE_ORDER));
        List<Row> all = new ArrayList<>();
        all.add(new Row(network.owner(), network.ownerName(), true));
        all.addAll(members);
        return all;
    }

    @Override
    protected void init() {
        setWindowSize(AWLayouts.NETWORK_WIDTH, AWLayouts.NETWORK_HEIGHT);
        super.init();
        guiLeft = AWLayout.anchor(width, AWLayouts.NETWORK_WIDTH, guiLeft);
        guiTop = AWLayout.anchor(height, AWLayouts.NETWORK_HEIGHT, guiTop);
        clearWidgets();

        Rect name = LAYOUT.name();
        nameBox = new EditBox(font, guiLeft + name.x(), guiTop + name.y(), name.width(), name.height(),
                AWLang.translate("gui.warp_anchor.network").component());
        nameBox.setMaxLength(NetworkRules.NAME_MAX);
        nameBox.setValue(network == null ? "" : network.name());
        addRenderableWidget(nameBox);

        Rect add = LAYOUT.add();
        addBox = new EditBox(font, guiLeft + add.x(), guiTop + add.y(), add.width(), add.height(),
                AWLang.translate("gui.network.add_hint").component());
        addBox.setMaxLength(16);
        addBox.setHint(AWLang.translate("gui.network.add_hint").component()
                .copy().withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(addBox);

        Rect addRect = LAYOUT.addButton();
        addButton = Button.builder(AWLang.translate("gui.network.add").component(), b -> addMember())
                .bounds(guiLeft + addRect.x(), guiTop + addRect.y(), addRect.width(), addRect.height()).build();
        addRenderableWidget(addButton);

        Rect remove = LAYOUT.remove();
        removeButton = Button.builder(AWLang.translate("gui.network.remove").component(), b -> removeSelected())
                .bounds(guiLeft + remove.x(), guiTop + remove.y(), remove.width(), remove.height()).build();
        addRenderableWidget(removeButton);

        Rect leave = LAYOUT.leave();
        leaveButton = Button.builder(Component.empty(), b -> leaveOrDisband())
                .bounds(guiLeft + leave.x(), guiTop + leave.y(), leave.width(), leave.height()).build();
        addRenderableWidget(leaveButton);

        updateWidgets();
        ask();
    }

    private void ask() {
        refreshTimer = REFRESH_INTERVAL;
        PacketDistributor.sendToServer(ServerboundNetworkPacket.list(networkId));
    }

    /** Whether this player may manage the network: its owner, or an operator, as the server will judge it. */
    private boolean manages() {
        if (network == null || minecraft == null || minecraft.player == null) {
            return false;
        }
        return network.isOwnedBy(minecraft.player.getUUID()) || minecraft.player.hasPermissions(2);
    }

    private boolean owns() {
        return network != null && minecraft != null && minecraft.player != null
                && network.isOwnedBy(minecraft.player.getUUID());
    }

    private void updateWidgets() {
        if (leaveButton == null) {
            return;
        }
        boolean manages = manages();
        nameBox.setEditable(manages);
        addBox.setEditable(manages);
        addButton.active = manages;
        Row chosen = selectedRow();
        removeButton.active = manages && chosen != null && !chosen.owner();
        // An operator who is not in the network has nothing to leave and may still end it.
        boolean ends = owns() || (manages && network != null && minecraft != null && minecraft.player != null
                && !network.has(minecraft.player.getUUID()));
        leaveButton.active = network != null;
        leaveButton.setMessage(AWLang.translate(confirmTicks > 0
                ? (ends ? "gui.network.confirm_disband" : "gui.network.confirm_leave")
                : (ends ? "gui.network.disband" : "gui.network.leave")).component());
    }

    @Nullable
    private Row selectedRow() {
        for (Row row : rows) {
            if (row.player().equals(selected)) {
                return row;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ input

    private void addMember() {
        String name = addBox.getValue().trim();
        if (name.isEmpty()) {
            return;
        }
        PacketDistributor.sendToServer(ServerboundNetworkPacket.add(networkId, name));
        addBox.setValue("");
        playClick(1.2F);
    }

    private void removeSelected() {
        Row chosen = selectedRow();
        if (chosen == null || chosen.owner()) {
            return;
        }
        PacketDistributor.sendToServer(ServerboundNetworkPacket.remove(networkId, chosen.player()));
        playClick(0.8F);
    }

    /**
     * Leaves the network, or ends it - on the second press.
     *
     * <p>Both take machines off the network, and a network disbanded is not got back by founding another
     * of the same name. So the first press only changes what the button says, and it forgets that it was
     * pressed if the second does not follow.
     */
    private void leaveOrDisband() {
        if (network == null) {
            return;
        }
        if (confirmTicks <= 0) {
            confirmTicks = CONFIRM_TICKS;
            updateWidgets();
            playClick(0.7F);
            return;
        }
        confirmTicks = 0;
        boolean member = minecraft != null && minecraft.player != null && network.has(minecraft.player.getUUID());
        PacketDistributor.sendToServer(owns() || !member
                ? ServerboundNetworkPacket.disband(networkId) : ServerboundNetworkPacket.leave(networkId));
        updateWidgets();
        playClick(0.6F);
    }

    private void commitName() {
        if (network != null && manages() && nameBox != null
                && !NetworkRules.clean(nameBox.getValue()).equals(network.name())) {
            PacketDistributor.sendToServer(ServerboundNetworkPacket.rename(networkId, nameBox.getValue()));
        }
    }

    private void playClick(float pitch) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.25F, pitch);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (addBox != null && addBox.isFocused()) {
                addMember();
                return true;
            }
            if (nameBox != null && nameBox.isFocused()) {
                commitName();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = rowAt(mouseX, mouseY);
        if (index >= 0 && index < rows.size()) {
            selected = rows.get(index).player();
            updateWidgets();
            playClick(1.1F);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maximum = Math.max(0, rows.size() - VISIBLE_ROWS);
        scroll = Math.max(0, Math.min(maximum, scroll - (int) Math.signum(scrollY)));
        return true;
    }

    private int rowAt(double mouseX, double mouseY) {
        Rect list = LAYOUT.list();
        double x = mouseX - guiLeft;
        double y = mouseY - guiTop;
        if (!list.contains(x, y)) {
            return -1;
        }
        int relative = (int) ((y - list.y()) / AWLayout.ROW);
        return relative < 0 || relative >= VISIBLE_ROWS ? -1 : scroll + relative;
    }

    @Override
    public void tick() {
        super.tick();
        if (confirmTicks > 0 && --confirmTicks == 0) {
            updateWidgets();
        }
        if (noticeTicks > 0) {
            noticeTicks--;
        }
        if (--refreshTimer <= 0) {
            ask();
        }
    }

    /** Back to the machine's panel this was opened from, with a name typed but not entered sent first. */
    @Override
    public void onClose() {
        commitName();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    // ----------------------------------------------------------------- render

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        AWScreenStyle.window(graphics, guiLeft, guiTop, AWLayouts.NETWORK_WIDTH, AWLayouts.NETWORK_HEIGHT);

        String owner = network == null ? "" : AWLang.translate("gui.warp_anchor.owner", network.ownerName()).string();
        Component count = network == null
                ? AWLang.translate("gui.network.asking").component()
                : AWLang.translate("gui.network.member_count", network.size()).component();
        AWScreenStyle.header(graphics, font, guiLeft, guiTop, LAYOUT.header(),
                AWLang.translate("gui.network.title").component(),
                owner,
                network == null ? "" : network.name(),
                count,
                AWScreenStyle.LABEL,
                AWScreenStyle.LABEL);

        renderList(graphics, mouseX, mouseY);
        renderInfo(graphics);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        Rect list = LAYOUT.list();
        AWScreenStyle.panel(graphics, guiLeft, guiTop, list);
        int left = guiLeft + list.x();
        int top = guiTop + list.y();

        int hovered = rowAt(mouseX, mouseY);
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scroll + row;
            if (index >= rows.size()) {
                break;
            }
            Row member = rows.get(index);
            int y = top + row * AWLayout.ROW;
            boolean chosen = member.player().equals(selected);
            if (chosen) {
                graphics.fill(left - AWLayout.INSET, y, left + list.width() - AWLayout.INSET,
                        y + AWLayout.ROW, 0x33_49_D9_C4);
                AWScreenStyle.selectionBar(graphics, left - AWLayout.INSET, y, AWLayout.ROW, AWScreenStyle.OK);
            } else if (index == hovered) {
                graphics.fill(left - AWLayout.INSET, y, left + list.width() - AWLayout.INSET,
                        y + AWLayout.ROW, 0x22_FF_FF_FF);
            }
            String tag = member.owner() ? AWLang.translate("gui.network.owner_tag").string() : "";
            int room = list.width() - font.width(tag) - 18;
            // A member who has never been seen under a name - added by an operator while away - is shown
            // by the start of their id rather than as a blank row nobody could pick out.
            String name = member.name().isBlank() ? member.player().toString().substring(0, 8) : member.name();
            graphics.drawString(font, AWScreenStyle.trim(font, name, room), left + 6, y + 3,
                    AWScreenStyle.VALUE, false);
            if (!tag.isEmpty()) {
                graphics.drawString(font, tag, left + list.width() - font.width(tag) - 8, y + 3,
                        AWScreenStyle.LABEL, false);
            }
        }
        AWScreenStyle.scrollbar(graphics, left + list.width() - AWLayout.INSET, top,
                VISIBLE_ROWS * AWLayout.ROW, rows.size(), VISIBLE_ROWS, scroll);
    }

    /** The one line under the add box: what came of the last order, in red if it was refused. */
    private void renderInfo(GuiGraphics graphics) {
        if (noticeTicks <= 0 || !notice.present()) {
            return;
        }
        Rect info = LAYOUT.info();
        String said = AWLang.translate(notice.key(), notice.arg()).string();
        graphics.drawString(font, AWScreenStyle.trim(font, said, info.width()), guiLeft + info.x(),
                guiTop + info.y() + 1, notice.refused() ? AWScreenStyle.BAD : AWScreenStyle.OK, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
