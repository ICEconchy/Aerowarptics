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

    private static final int WINDOW_WIDTH = 268;
    private static final int WINDOW_HEIGHT = 226;
    private static final int LIST_WIDTH = 150;
    private static final int ROW_HEIGHT = 13;
    private static final int VISIBLE_ROWS = 10;
    private static final int REFRESH_INTERVAL = 20;
    private static final int CONTENT_TOP = 48;

    private ClientboundGateDialPacket data;
    @Nullable
    private UUID selected;
    private int scroll;
    private int refreshTimer = REFRESH_INTERVAL;

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
        setWindowSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        super.init();
        clearWidgets();

        nameBox = new EditBox(font, guiLeft + 12, guiTop + 28, LIST_WIDTH - 6, 14,
                AWLang.translate("gui.rift_gate.name").component());
        nameBox.setMaxLength(48);
        nameBox.setValue(data.name());
        nameBox.setResponder(value -> {
        });
        nameBox.setEditable(data.owned());
        addRenderableWidget(nameBox);

        int buttonY = guiTop + WINDOW_HEIGHT - 26;
        dialButton = Button.builder(Component.empty(), b -> dialOrHangUp())
                .bounds(guiLeft + 12, buttonY, 110, 18)
                .build();
        accessButton = Button.builder(Component.empty(), b -> {
            PacketDistributor.sendToServer(ServerboundGatePacket.cycleAccess(data.gatePos()));
            playClick(0.9F);
        }).bounds(guiLeft + WINDOW_WIDTH - 122, buttonY, 110, 18).build();

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
        int listLeft = guiLeft + 12;
        int listTop = guiTop + CONTENT_TOP;
        if (mouseX < listLeft || mouseX > listLeft + LIST_WIDTH) {
            return -1;
        }
        int relative = (int) ((mouseY - listTop) / ROW_HEIGHT);
        return relative < 0 || relative >= VISIBLE_ROWS ? -1 : scroll + relative;
    }

    @Override
    public void tick() {
        super.tick();
        if (--refreshTimer <= 0) {
            refreshTimer = REFRESH_INTERVAL;
            PacketDistributor.sendToServer(ServerboundGatePacket.open(data.gatePos()));
        }
    }

    // ----------------------------------------------------------------- render

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        AWScreenStyle.window(graphics, guiLeft, guiTop, WINDOW_WIDTH, WINDOW_HEIGHT);

        graphics.drawString(font, AWLang.translate("gui.rift_gate.title").component(),
                guiLeft + 12, guiTop + 12, AWScreenStyle.TITLE, false);

        RiftGateState state = data.state();
        Component stateLabel = AWLang.translate(state.translationKey()).component();
        graphics.drawString(font, stateLabel, guiLeft + WINDOW_WIDTH - 16 - font.width(stateLabel),
                guiTop + 12, stateColour(state), false);

        String opening = data.formed()
                ? AWLang.translate("gui.rift_gate.opening", data.width(), data.height()).string()
                : AWLang.translate("gui.rift_gate.unformed_hint").string();
        graphics.drawString(font, opening, guiLeft + WINDOW_WIDTH - 16 - font.width(opening),
                guiTop + 30, data.formed() ? AWScreenStyle.LABEL : AWScreenStyle.BAD, false);

        renderList(graphics, mouseX, mouseY);
        renderDetails(graphics);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int listLeft = guiLeft + 12;
        int listTop = guiTop + CONTENT_TOP;
        int listHeight = VISIBLE_ROWS * ROW_HEIGHT;
        AWScreenStyle.inset(graphics, listLeft - 2, listTop - 2, LIST_WIDTH, listHeight);

        List<RiftGate> gates = data.reachable();
        if (gates.isEmpty()) {
            graphics.drawString(font, AWLang.translate("gui.rift_gate.no_gates").component(),
                    listLeft + 4, listTop + 4, AWScreenStyle.LABEL, false);
            return;
        }

        int hovered = rowAt(mouseX, mouseY);
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scroll + row;
            if (index >= gates.size()) {
                break;
            }
            RiftGate gate = gates.get(index);
            int y = listTop + row * ROW_HEIGHT;
            boolean isSelected = gate.id().equals(selected);
            boolean isConnected = gate.id().equals(data.connected());

            if (isConnected) {
                graphics.fill(listLeft - 2, y - 1, listLeft + LIST_WIDTH - 2, y + ROW_HEIGHT - 2, 0x50_49_D9_C4);
            } else if (isSelected) {
                graphics.fill(listLeft - 2, y - 1, listLeft + LIST_WIDTH - 2, y + ROW_HEIGHT - 2, 0x30_49_D9_C4);
            } else if (index == hovered) {
                graphics.fill(listLeft - 2, y - 1, listLeft + LIST_WIDTH - 2, y + ROW_HEIGHT - 2, 0x22_FF_FF_FF);
            }

            String marker = isConnected ? "* " : isSelected ? "> " : "  ";
            String name = font.plainSubstrByWidth(gate.displayName(), LIST_WIDTH - 60);
            graphics.drawString(font, marker + name, listLeft + 2, y + 2,
                    isConnected ? AWScreenStyle.OK : AWScreenStyle.VALUE, false);

            String size = gate.shape().width() + "x" + gate.shape().height();
            graphics.drawString(font, size, listLeft + LIST_WIDTH - 8 - font.width(size),
                    y + 2, AWScreenStyle.LABEL, false);
        }

        if (gates.size() > VISIBLE_ROWS) {
            int barHeight = Math.max(8, listHeight * VISIBLE_ROWS / gates.size());
            int maximum = gates.size() - VISIBLE_ROWS;
            int barY = listTop + (listHeight - barHeight) * scroll / Math.max(1, maximum);
            graphics.fill(listLeft + LIST_WIDTH - 5, barY, listLeft + LIST_WIDTH - 3, barY + barHeight, 0x88_D9_C3_92);
        }
    }

    private void renderDetails(GuiGraphics graphics) {
        int left = guiLeft + LIST_WIDTH + 20;
        int top = guiTop + CONTENT_TOP;
        int width = WINDOW_WIDTH - LIST_WIDTH - 34;
        AWScreenStyle.inset(graphics, left - 2, top - 2, width, VISIBLE_ROWS * ROW_HEIGHT);

        int line = top + 2;
        line = detail(graphics, left, line, width, "gui.rift_gate.essence_short",
                data.essence() + " / " + data.capacity(),
                data.essence() >= data.cost() ? AWScreenStyle.VALUE : AWScreenStyle.BAD);
        line = detail(graphics, left, line, width, "gui.rift_gate.cost", String.valueOf(data.cost()),
                data.essence() >= data.cost() ? AWScreenStyle.VALUE : AWScreenStyle.BAD);
        line = detail(graphics, left, line, width, "gui.rift_drive.speed",
                Math.round(data.speed()) + " / " + data.requiredSpeed(),
                Math.abs(data.speed()) >= data.requiredSpeed() ? AWScreenStyle.VALUE : AWScreenStyle.BAD);

        line += 4;
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
        for (String piece : font.plainSubstrByWidth(status.getString(), width - 8).split("\n")) {
            graphics.drawString(font, piece, left + 2, line, colour, false);
            line += 11;
        }
    }

    private int detail(GuiGraphics graphics, int left, int y, int width, String key, String value, int colour) {
        graphics.drawString(font, AWLang.translate(key).component(), left + 2, y, AWScreenStyle.LABEL, false);
        String trimmed = font.plainSubstrByWidth(value, width - 8);
        graphics.drawString(font, trimmed, left + width - 8 - font.width(trimmed), y, colour, false);
        return y + 13;
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
