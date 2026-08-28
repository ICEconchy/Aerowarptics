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
import uk.co.iceconchy.aerowarptics.gate.RiftGate;
import uk.co.iceconchy.aerowarptics.gate.RiftGateState;
import uk.co.iceconchy.aerowarptics.network.ClientboundGateDialPacket;
import uk.co.iceconchy.aerowarptics.network.ServerboundGatePacket;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.List;
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
    private Button dialButton;
    private Button accessButton;

    public RiftGateDialScreen(ClientboundGateDialPacket data) {
        super(AWLang.translate("gui.rift_gate.title").component());
        this.data = data;
        this.selected = data.selected();
    }

    public boolean matches(BlockPos gatePos) {
        return data.gatePos().equals(gatePos);
    }

    /** Replaces the panel with a fresh server snapshot, keeping what the player was looking at. */
    public void accept(ClientboundGateDialPacket packet) {
        boolean sameGate = packet.gatePos().equals(data.gatePos());
        this.data = packet;
        if (!sameGate || packet.reachable().stream().noneMatch(g -> g.id().equals(selected))) {
            selected = packet.selected();
        }
        if (nameBox != null && !nameBox.isFocused()) {
            nameBox.setValue(packet.name());
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
        addRenderableWidget(nameBox);

        AWLayout.Rect dial = LAYOUT.dial();
        AWLayout.Rect access = LAYOUT.access();
        dialButton = Button.builder(Component.empty(), b -> dialOrHangUp())
                .bounds(guiLeft + dial.x(), guiTop + dial.y(), dial.width(), dial.height())
                .build();
        accessButton = Button.builder(Component.empty(), b -> {
            PacketDistributor.sendToServer(ServerboundGatePacket.cycleAccess(data.gatePos()));
            playClick(0.9F);
        }).bounds(guiLeft + access.x(), guiTop + access.y(), access.width(), access.height()).build();

        addRenderableWidget(dialButton);
        addRenderableWidget(accessButton);
        updateButtons();
    }

    @Override
    public void removed() {
        commitName();
        super.removed();
    }

    private void updateButtons() {
        if (dialButton == null || accessButton == null) {
            return;
        }
        RiftGateState state = data.state();
        dialButton.setMessage(state.engaged()
                ? AWLang.translate("gui.rift_gate.hang_up").component()
                : AWLang.translate("gui.rift_gate.dial").component());
        dialButton.active = state.engaged() || (data.formed() && selected != null);
        accessButton.setMessage(AWLang.translate("gui.rift_gate.access",
                AWLang.translate(data.access().translationKey()).string()).component());
        accessButton.active = data.owned();
    }

    // ------------------------------------------------------------------ input

    private void dialOrHangUp() {
        commitName();
        if (data.state().engaged()) {
            PacketDistributor.sendToServer(ServerboundGatePacket.hangUp(data.gatePos()));
            playClick(0.7F);
        } else if (selected != null) {
            PacketDistributor.sendToServer(ServerboundGatePacket.dial(data.gatePos(), selected));
            playClick(1.3F);
        }
    }

    private void commitName() {
        if (nameBox != null && data.owned() && !nameBox.getValue().equals(data.name())) {
            PacketDistributor.sendToServer(ServerboundGatePacket.rename(data.gatePos(), nameBox.getValue()));
        }
    }

    private void playClick(float pitch) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.25F, pitch);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
        if (--refreshTimer <= 0) {
            refreshTimer = REFRESH_INTERVAL;
            PacketDistributor.sendToServer(ServerboundGatePacket.open(data.gatePos()));
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

            String size = gate.shape().width() + "x" + gate.shape().height();
            int room = list.width() - font.width(size) - 18;
            graphics.drawString(font, AWScreenStyle.trim(font, gate.displayName(), room),
                    left + 6, y + 3, isConnected ? AWScreenStyle.OK : AWScreenStyle.VALUE, false);
            graphics.drawString(font, size, left + list.width() - font.width(size) - 8,
                    y + 3, AWScreenStyle.LABEL, false);
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

        AWScreenStyle.rule(graphics, left, line + 2, width);
        line += 7;

        Component status;
        int colour;
        if (data.failure().isFailure()) {
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
