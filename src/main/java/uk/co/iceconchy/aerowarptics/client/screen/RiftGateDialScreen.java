package uk.co.iceconchy.aerowarptics.client.screen;

import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.gate.GatePassageStyle;
import uk.co.iceconchy.aerowarptics.gate.RiftGate;
import uk.co.iceconchy.aerowarptics.gate.RiftGateState;
import uk.co.iceconchy.aerowarptics.network.ClientboundGateDialPacket;
import uk.co.iceconchy.aerowarptics.network.ServerboundGatePacket;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkFilter;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkNotice;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkRules;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A Rift Gate's dial panel.
 *
 * <p>A view over what the server sent, like the Astrolabe's chart: the list of gates has already been
 * filtered to the ones this player may dial in this dimension, so the screen cannot offer a connection
 * that would be refused for a reason it does not know about.
 *
 * <p>Unlike the drive's console, this one <em>does</em> act. A gate is a door, and a door with no
 * handle is an odd thing - the dial is the deliberate act, and the redstone-only rule that governs a
 * Rift Drive is about committing a whole ship to a journey, which is not what opening a doorway is.
 */
@OnlyIn(Dist.CLIENT)
public class RiftGateDialScreen extends AbstractSimiScreen {

    private static final int REFRESH_INTERVAL = 20;

    private static final AWLayouts.Dial LAYOUT = AWLayouts.dial();
    private static final int VISIBLE_ROWS = LAYOUT.visibleRows();

    private ClientboundGateDialPacket data;
    @Nullable
    private UUID selected;
    private int scroll;
    private int refreshTimer = REFRESH_INTERVAL;
    private int ticksOpen;

    /** The marker beside the chosen gate, which slides between rows rather than jumping. */
    private final AWAnim.Eased marker = new AWAnim.Eased(0.4F, -1.0F);

    private EditBox nameBox;
    private EditBox networkBox;
    private Button dialButton;
    private Button accessButton;
    private Button membersButton;
    private Button passageButton;

    /** How long the list ignores answers about a filter other than the one just asked for. */
    private static final int PENDING_FILTER_TICKS = 20;

    /** How long the panel goes on saying what the gate's network made of the last thing asked of it. */
    private static final int NOTICE_TICKS = 120;

    /** The least room worth drawing a row's network in. */
    private static final int MINIMUM_TAG = 24;

    /**
     * The network the list is filtered to, or {@code null} for every gate - as the Astrolabe's chart keeps
     * it, and for the same reason: the server applies it, and a refresh already on its way when the button
     * is pressed must not put the old list back.
     */
    @Nullable
    private UUID filter;
    private int pendingFilterTicks;

    /**
     * What the gate's network last had to say, kept for a while after the packet that said it. The panel
     * refreshes every second, and the refresh that follows a refusal carries nothing.
     */
    private NetworkNotice notice = NetworkNotice.NONE;
    private int noticeTicks;

    public RiftGateDialScreen(ClientboundGateDialPacket data) {
        super(AWLang.translate("gui.rift_gate.title").component());
        this.data = data;
        this.selected = data.selected();
        this.filter = data.filter();
        hear(data.notice());
    }

    private void hear(NetworkNotice said) {
        if (said.present()) {
            notice = said;
            noticeTicks = NOTICE_TICKS;
        }
    }

    public boolean matches(BlockPos gatePos) {
        return data.gatePos().equals(gatePos);
    }

    /** Replaces the panel with a fresh server snapshot, keeping what the player was looking at. */
    public void accept(ClientboundGateDialPacket packet) {
        hear(packet.notice());
        if (!Objects.equals(packet.filter(), filter)) {
            if (pendingFilterTicks > 0) {
                return;
            }
            // The server fell back to every gate: the network asked for has gone, or has no gate in reach.
            filter = packet.filter();
            scroll = 0;
        }
        pendingFilterTicks = 0;
        boolean sameGate = packet.gatePos().equals(data.gatePos());
        this.data = packet;
        scroll = Math.max(0, Math.min(scroll, packet.reachable().size() - VISIBLE_ROWS));
        if (!sameGate || packet.reachable().stream().noneMatch(g -> g.id().equals(selected))) {
            selected = packet.selected();
        }
        if (nameBox != null && !nameBox.isFocused()) {
            nameBox.setValue(packet.name());
        }
        if (networkBox != null && !networkBox.isFocused()) {
            networkBox.setValue(packet.networkName());
        }
        updateButtons();
    }

    @Override
    protected void init() {
        setWindowSize(AWLayouts.DIAL_WIDTH, AWLayouts.DIAL_HEIGHT);
        super.init();
        guiLeft = AWLayout.anchor(width, AWLayouts.DIAL_WIDTH, guiLeft);
        guiTop = AWLayout.anchor(height, AWLayouts.DIAL_HEIGHT, guiTop);
        clearWidgets();

        AWLayout.Rect name = LAYOUT.name();
        nameBox = new EditBox(font, guiLeft + name.x(), guiTop + name.y(), name.width(), name.height(),
                AWLang.translate("gui.rift_gate.name").component());
        nameBox.setMaxLength(48);
        nameBox.setValue(data.name());
        nameBox.setResponder(value -> {
        });
        nameBox.setEditable(data.owned());
        // The two boxes share a line with no room for a caption over either, so each says what it is for
        // while it is empty.
        nameBox.setHint(AWLang.translate("gui.rift_gate.name").component().copy().withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(nameBox);

        AWLayout.Rect network = LAYOUT.network();
        networkBox = new EditBox(font, guiLeft + network.x(), guiTop + network.y(), network.width(),
                network.height(), AWLang.translate("gui.warp_anchor.network").component());
        networkBox.setMaxLength(NetworkRules.NAME_MAX);
        networkBox.setValue(data.networkName());
        networkBox.setEditable(data.owned());
        networkBox.setHint(AWLang.translate("gui.warp_anchor.network").component()
                .copy().withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(networkBox);

        AWLayout.Rect dial = LAYOUT.dial();
        AWLayout.Rect access = LAYOUT.access();
        AWLayout.Rect members = LAYOUT.members();
        dialButton = Button.builder(Component.empty(), b -> dialOrHangUp())
                .bounds(guiLeft + dial.x(), guiTop + dial.y(), dial.width(), dial.height())
                .build();
        accessButton = Button.builder(Component.empty(), b -> {
            // The network first: whether Network is among the modes the button steps through depends on
            // the gate being on one, and the server reads these two in the order they are sent.
            commitNetwork();
            PacketDistributor.sendToServer(ServerboundGatePacket.cycleAccess(data.gatePos(), filter));
            playClick(0.9F);
        }).bounds(guiLeft + access.x(), guiTop + access.y(), access.width(), access.height()).build();
        membersButton = Button.builder(AWLang.translate("gui.network.members").component(), b -> openMembers())
                .bounds(guiLeft + members.x(), guiTop + members.y(), members.width(), members.height()).build();

        // The one control here that is the player's and not the gate's: it is never greyed out, whoever
        // owns the gate, and it asks the server nothing.
        AWLayout.Rect passage = LAYOUT.passage();
        passageButton = Button.builder(Component.empty(), b -> stepPassage())
                .bounds(guiLeft + passage.x(), guiTop + passage.y(), passage.width(), passage.height()).build();

        addRenderableWidget(dialButton);
        addRenderableWidget(accessButton);
        addRenderableWidget(membersButton);
        addRenderableWidget(passageButton);
        updateButtons();
        updatePassage();
    }

    /**
     * Steps this player's own passage style on, or back with shift held.
     *
     * <p>Written straight into the client config, where a passage reads it from as it begins. Nothing is
     * sent: what a player sees going through a gate is nobody else's business, the gate's included. And
     * nothing is played: the button says which style is chosen, its tooltip says what that one is, and the
     * place to see it is the gate.
     */
    private void stepPassage() {
        GatePassageStyle now = AWConfig.GATE_PASSAGE.get();
        AWConfig.GATE_PASSAGE.set(hasShiftDown() ? now.previous() : now.next());
        AWConfig.GATE_PASSAGE.save();
        updatePassage();
        playClick(1.0F);
    }

    private void updatePassage() {
        if (passageButton == null) {
            return;
        }
        GatePassageStyle style = AWConfig.GATE_PASSAGE.get();
        passageButton.setMessage(AWLang.translate("gui.rift_gate.passage",
                AWLang.translate(style.translationKey()).string()).component());
        // Vanilla wraps a widget's own tooltip, and breaks it at the blank line.
        passageButton.setTooltip(Tooltip.create(AWLang.translate(style.aboutKey()).component().copy()
                .append("\n\n")
                .append(AWLang.translate("gui.rift_gate.passage.hint").component().copy()
                        .withStyle(ChatFormatting.GRAY))));
    }

    @Override
    public void removed() {
        commitName();
        commitNetwork();
        super.removed();
    }

    private void updateButtons() {
        if (dialButton == null || accessButton == null || membersButton == null) {
            return;
        }
        RiftGateState state = data.state();
        dialButton.setMessage(state.engaged()
                ? AWLang.translate("gui.rift_gate.hang_up").component()
                : routeOnly() ? AWLang.translate("gui.rift_gate.set_route").component()
                : AWLang.translate("gui.rift_gate.dial").component());
        dialButton.active = state.engaged() || (data.formed() && selected != null);
        // Said on the button itself, because a route is the one thing here a player might want without paying to
        // open the gate: it is where every track run into the gate comes out.
        dialButton.setTooltip(state.engaged() ? null
                : Tooltip.create(AWLang.translate(routeOnly() ? "gui.rift_gate.set_route.hint"
                        : "gui.rift_gate.dial.hint").component()));
        accessButton.setMessage(AWLang.translate("gui.rift_gate.access",
                AWLang.translate(data.access().translationKey()).string()).component());
        accessButton.active = data.owned();
        // Only for whoever may edit the gate. A gate's owner is always in its network; anybody else at the
        // dial may well not be, and the server shows a network's members to nobody outside it.
        membersButton.active = data.network() != null && data.owned();
    }

    // ------------------------------------------------------------------ input

    /**
     * Whether the chosen gate is one this gate can be routed to but not dialled - another dimension, which trains go
     * to and nothing else can follow them.
     */
    private boolean routeOnly() {
        RiftGate chosen = chosen();
        return chosen != null && !sameDimension(chosen);
    }

    @Nullable
    private RiftGate chosen() {
        int index = selectedIndex();
        return index < 0 ? null : data.reachable().get(index);
    }

    private boolean sameDimension(RiftGate gate) {
        return minecraft == null || minecraft.level == null || gate.dimension().equals(minecraft.level.dimension());
    }

    private void dialOrHangUp() {
        commitName();
        if (data.state().engaged()) {
            PacketDistributor.sendToServer(ServerboundGatePacket.hangUp(data.gatePos(), filter));
            playClick(0.7F);
        } else if (selected != null && (routeOnly() || hasShiftDown())) {
            PacketDistributor.sendToServer(ServerboundGatePacket.setRoute(data.gatePos(), selected, filter));
            playClick(1.1F);
        } else if (selected != null) {
            PacketDistributor.sendToServer(ServerboundGatePacket.dial(data.gatePos(), selected, filter));
            playClick(1.3F);
        }
    }

    private void commitName() {
        if (nameBox != null && data.owned() && !nameBox.getValue().equals(data.name())) {
            PacketDistributor.sendToServer(
                    ServerboundGatePacket.rename(data.gatePos(), nameBox.getValue(), filter));
        }
    }

    /**
     * Sends the network typed into the box, if it is not the one the gate is already on.
     *
     * <p>Compared as networks are - spaces and case aside - so that tidying how a name is typed is not sent
     * as a change of network.
     */
    private void commitNetwork() {
        if (networkBox != null && data.owned()
                && !NetworkRules.key(networkBox.getValue()).equals(NetworkRules.key(data.networkName()))) {
            PacketDistributor.sendToServer(
                    ServerboundGatePacket.setNetwork(data.gatePos(), networkBox.getValue(), filter));
        }
    }

    /** Opens the members panel of the network this gate is on, in front of this one. */
    private void openMembers() {
        if (data.network() != null && minecraft != null) {
            minecraft.setScreen(new NetworkScreen(this, data.network()));
        }
    }

    /**
     * Steps the list on to the next network, or back to the one before, and asks for it at once.
     *
     * @param by {@code 1} for the next, {@code -1} for the one before
     */
    private void stepFilter(int by) {
        if (data.networks().isEmpty()) {
            playClick(0.7F);
            return;
        }
        filter = by > 0 ? NetworkFilter.next(data.networks(), filter) : NetworkFilter.previous(data.networks(), filter);
        scroll = 0;
        pendingFilterTicks = PENDING_FILTER_TICKS;
        refreshTimer = REFRESH_INTERVAL;
        PacketDistributor.sendToServer(ServerboundGatePacket.open(data.gatePos(), filter));
        playClick(1.0F);
    }

    /** Enter in either box sends it, rather than waiting for the panel to close. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (networkBox != null && networkBox.isFocused()) {
                commitNetwork();
                return true;
            }
            if (nameBox != null && nameBox.isFocused()) {
                commitName();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void playClick(float pitch) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.25F, pitch);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int side = NetworkFilterStrip.side(LAYOUT.filter(), mouseX - guiLeft, mouseY - guiTop);
        if (side != 0) {
            stepFilter(side);
            return true;
        }
        int index = rowAt(mouseX, mouseY);
        if (index >= 0 && index < data.reachable().size()) {
            selected = data.reachable().get(index).id();
            updateButtons();
            playClick(1.1F);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0D && NetworkFilterStrip.side(LAYOUT.filter(), mouseX - guiLeft, mouseY - guiTop) != 0) {
            stepFilter(scrollY < 0.0D ? 1 : -1);
            return true;
        }
        int maximum = Math.max(0, data.reachable().size() - VISIBLE_ROWS);
        scroll = Math.max(0, Math.min(maximum, scroll - (int) Math.signum(scrollY)));
        return true;
    }

    private int rowAt(double mouseX, double mouseY) {
        AWLayout.Rect list = LAYOUT.list();
        double x = mouseX - guiLeft;
        double y = mouseY - guiTop;
        if (!list.contains(x, y)) {
            return -1;
        }
        int relative = (int) ((y - list.y()) / AWLayout.ROW);
        return relative < 0 || relative >= VISIBLE_ROWS ? -1 : scroll + relative;
    }

    private int selectedIndex() {
        List<RiftGate> gates = data.reachable();
        for (int index = 0; index < gates.size(); index++) {
            if (gates.get(index).id().equals(selected)) {
                return index;
            }
        }
        return -1;
    }

    @Override
    public void tick() {
        super.tick();
        ticksOpen++;
        int index = selectedIndex();
        marker.set(index < 0 ? -1.0F : index - scroll);
        marker.tick();
        if (pendingFilterTicks > 0) {
            pendingFilterTicks--;
        }
        if (noticeTicks > 0) {
            noticeTicks--;
        }
        if (--refreshTimer <= 0) {
            refreshTimer = REFRESH_INTERVAL;
            PacketDistributor.sendToServer(ServerboundGatePacket.open(data.gatePos(), filter));
        }
    }

    // ----------------------------------------------------------------- render

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        AWScreenStyle.window(graphics, guiLeft, guiTop, AWLayouts.DIAL_WIDTH, AWLayouts.DIAL_HEIGHT);

        RiftGateState state = data.state();
        String opening = data.formed()
                ? AWLang.translate("gui.rift_gate.opening", data.width(), data.height()).string()
                : AWLang.translate("gui.rift_gate.unformed_hint").string();

        // The subtitle line is otherwise unused on this panel, which makes it the place to own up to a
        // list that did not all fit rather than quietly showing a shorter one.
        String capped = data.truncated()
                ? AWLang.translate("gui.rift_gate.gates_capped",
                        data.reachable().size(), data.totalReachable()).string()
                : "";

        AWScreenStyle.header(graphics, font, guiLeft, guiTop, LAYOUT.header(),
                AWLang.translate("gui.rift_gate.title").component(),
                capped,
                opening,
                AWLang.translate(state.translationKey()).component(),
                stateColour(state),
                data.truncated() ? AWScreenStyle.WARN : AWScreenStyle.LABEL);

        NetworkFilterStrip.draw(graphics, font, guiLeft, guiTop, LAYOUT.filter(), data.networks(), filter,
                mouseX, mouseY);
        renderList(graphics, mouseX, mouseY, partialTicks);
        renderDetails(graphics, partialTicks);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        AWLayout.Rect list = LAYOUT.list();
        AWScreenStyle.panel(graphics, guiLeft, guiTop, list);

        int left = guiLeft + list.x();
        int top = guiTop + list.y();

        List<RiftGate> gates = data.reachable();
        if (gates.isEmpty()) {
            graphics.drawString(font, AWLang.translate("gui.rift_gate.no_gates").component(),
                    left + 2, top + 4, AWScreenStyle.LABEL, false);
            return;
        }

        float markerRow = marker.get(partialTicks);
        if (markerRow >= -0.5F && markerRow <= VISIBLE_ROWS - 0.5F) {
            int y = top + Math.round(markerRow * AWLayout.ROW);
            graphics.fill(left - AWLayout.INSET, y, left + list.width() - AWLayout.INSET,
                    y + AWLayout.ROW, 0x30_49_D9_C4);
            AWScreenStyle.selectionBar(graphics, left - AWLayout.INSET, y, AWLayout.ROW, AWScreenStyle.OK);
        }

        int hovered = rowAt(mouseX, mouseY);
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scroll + row;
            if (index >= gates.size()) {
                break;
            }
            RiftGate gate = gates.get(index);
            int y = top + row * AWLayout.ROW;
            boolean isConnected = gate.id().equals(data.connected());

            if (isConnected) {
                // A live connection is the one fact here worth seeing without reading, so it breathes
                // rather than sitting still.
                float glow = 0.35F + 0.25F * AWAnim.pulse(ticksOpen + partialTicks, 40.0F);
                graphics.fill(left - AWLayout.INSET, y, left + list.width() - AWLayout.INSET,
                        y + AWLayout.ROW, AWAnim.fade(0xFF_49_D9_C4, glow * 0.4F));
            } else if (index == hovered && !gate.id().equals(selected)) {
                graphics.fill(left - AWLayout.INSET, y, left + list.width() - AWLayout.INSET,
                        y + AWLayout.ROW, 0x22_FF_FF_FF);
            }

            // A gate in another dimension says so where its size would be: a route is all it can be, and its size is
            // no business of a train's. Squeezed in beside the name instead, it vanished whenever the name was long.
            boolean elsewhere = !sameDimension(gate);
            String size = elsewhere ? AWLang.translate("gui.rift_gate.trains_only").string()
                    : gate.shape().width() + "x" + gate.shape().height();
            int room = list.width() - font.width(size) - 18;
            String name = AWScreenStyle.trim(font, gate.displayName(), room);
            boolean isRoute = gate.id().equals(data.selected());
            graphics.drawString(font, name, left + 6, y + 3,
                    isConnected ? AWScreenStyle.OK : isRoute ? AWScreenStyle.ACCENT : AWScreenStyle.VALUE, false);
            int sizeLeft = left + list.width() - font.width(size) - 8;
            graphics.drawString(font, size, sizeLeft, y + 3, elsewhere ? AWScreenStyle.ACCENT : AWScreenStyle.LABEL,
                    false);

            // The gate's network, in whatever room its name left, and only while the list is unfiltered -
            // the same rule as the Astrolabe's chart.
            String network = filter == null ? NetworkFilter.nameOf(data.networks(), gate.network()) : "";
            int tagRoom = room - font.width(name) - 8;
            if (!network.isBlank() && tagRoom >= MINIMUM_TAG) {
                String tag = AWScreenStyle.trim(font, network, tagRoom);
                graphics.drawString(font, tag, sizeLeft - 6 - font.width(tag), y + 3,
                        AWScreenStyle.ACCENT_DIM, false);
            }
        }

        AWScreenStyle.scrollbar(graphics, left + list.width() - AWLayout.INSET, top,
                VISIBLE_ROWS * AWLayout.ROW, gates.size(), VISIBLE_ROWS, scroll);
    }

    private void renderDetails(GuiGraphics graphics, float partialTicks) {
        AWLayout.Rect panel = LAYOUT.detail();
        AWScreenStyle.panel(graphics, guiLeft, guiTop, panel);

        int left = guiLeft + panel.x() + 2;
        int top = guiTop + panel.y() + 2;
        int width = panel.width() - 4;

        boolean paid = data.essence() >= data.cost();
        boolean turning = Math.abs(data.speed()) >= data.requiredSpeed();

        int line = AWScreenStyle.readout(graphics, font, left, top, width,
                AWLang.translate("gui.rift_gate.cost").component(),
                AWLang.essence(data.cost()), paid ? AWScreenStyle.VALUE : AWScreenStyle.BAD);
        AWScreenStyle.bar(graphics, left, line + 1, width, 4,
                data.capacity() <= 0 ? 0.0F : data.essence() / (float) data.capacity(),
                paid ? AWScreenStyle.OK : AWScreenStyle.WARN);
        String held = AWLang.essence(data.essence(), data.capacity());
        graphics.drawString(font, held, left, line + 8, AWScreenStyle.LABEL, false);
        line += 20;

        line = AWScreenStyle.readout(graphics, font, left, line, width,
                AWLang.translate("gui.rift_drive.speed").component(),
                Math.round(data.speed()) + " / " + data.requiredSpeed(),
                turning ? AWScreenStyle.VALUE : AWScreenStyle.BAD);

        line = AWScreenStyle.readout(graphics, font, left, line, width,
                AWLang.translate("gui.rift_gate.rails").component(), railReading(),
                data.rails().held() ? AWScreenStyle.WARN
                        : data.rails().ready() < data.rails().lanes() ? AWScreenStyle.WARN
                        : data.rails().any() ? AWScreenStyle.VALUE : AWScreenStyle.LABEL);

        AWScreenStyle.rule(graphics, left, line + 2, width);
        line += 7;

        Component status;
        int colour;
        if (noticeTicks > 0 && notice.refused()) {
            // What the network would not do, ahead of anything the gate has to say for itself: it is the
            // answer to what the player has just typed, and it is only here for a few seconds.
            status = AWLang.translate(notice.key(), notice.arg()).component();
            colour = AWScreenStyle.BAD;
        } else if (data.failure().isFailure()) {
            status = AWLang.translate(data.failure().translationKey()).component();
            colour = AWScreenStyle.BAD;
        } else if (data.state().passable()) {
            status = AWLang.translate("gui.rift_gate.ready").component();
            colour = AWScreenStyle.OK;
        } else if (selected == null) {
            status = AWLang.translate("gui.rift_gate.pick").component();
            colour = AWScreenStyle.WARN;
        } else {
            status = AWLang.translate("gui.rift_gate.standing_by").component();
            colour = AWScreenStyle.LABEL;
        }
        // Wrapped rather than cut: a refusal is the one thing on this panel a player has to read in
        // full, and the reasons are sentences.
        for (net.minecraft.util.FormattedCharSequence piece : font.split(status, width - 4)) {
            graphics.drawString(font, piece, left, line, colour, false);
            line += 10;
        }
    }

    /** The rails reading: lanes, then what is waiting or running, then whether redstone is holding them. */
    private String railReading() {
        var rails = data.rails();
        if (!rails.any()) {
            return AWLang.translate("gui.rift_gate.rails.none").string();
        }
        if (rails.held()) {
            return AWLang.translate("gui.rift_gate.rails.held", rails.lanes()).string();
        }
        if (rails.running() > 0) {
            return AWLang.translate("gui.rift_gate.rails.running", rails.lanes(), rails.running()).string();
        }
        if (rails.waiting() > 0) {
            return AWLang.translate("gui.rift_gate.rails.waiting", rails.lanes(), rails.waiting()).string();
        }
        if (rails.ready() < rails.lanes()) {
            return AWLang.translate("gui.rift_gate.rails.laying", rails.ready(), rails.lanes()).string();
        }
        return AWLang.translate("gui.rift_gate.rails.lanes", rails.lanes(), rails.incoming()).string();
    }

    private static int stateColour(RiftGateState state) {
        return switch (state) {
            case OPEN -> AWScreenStyle.OK;
            case DIALLING, CLOSING -> AWScreenStyle.WARN;
            case UNFORMED -> AWScreenStyle.BAD;
            default -> AWScreenStyle.LABEL;
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
