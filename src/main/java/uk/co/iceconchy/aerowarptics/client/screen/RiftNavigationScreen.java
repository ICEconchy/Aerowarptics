package uk.co.iceconchy.aerowarptics.client.screen;

import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.drive.DriveHeading;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveState;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;
import uk.co.iceconchy.aerowarptics.network.ClientboundNavigationDataPacket;
import uk.co.iceconchy.aerowarptics.network.ServerboundNavigationRequestPacket;
import uk.co.iceconchy.aerowarptics.network.ServerboundWarpCommandPacket;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.warp.WarpQuote;

import java.util.List;
import java.util.UUID;

/**
 * The Rift Navigation console.
 *
 * <p>Purely a view over a {@link ClientboundNavigationDataPacket}: destination names, distances,
 * costs and statuses all arrive pre-computed from the server, and the only thing the screen ever
 * sends back is an anchor id or a cancel. It refreshes itself once a second so charge and drive state
 * stay live while it is open.
 */
@OnlyIn(Dist.CLIENT)
public class RiftNavigationScreen extends AbstractSimiScreen {

    private static final int WINDOW_WIDTH = 292;
    private static final int WINDOW_HEIGHT = 196;
    private static final int LIST_WIDTH = 148;
    private static final int ROW_HEIGHT = 13;
    private static final int VISIBLE_ROWS = 9;
    private static final int REFRESH_INTERVAL = 20;

    private static final Color PANEL = new Color(0xDD_16_12_1B, true);
    private static final Color BORDER_TOP = new Color(0xFF_5C_4A_36, true);
    private static final Color BORDER_BOTTOM = new Color(0xFF_2E_25_1B, true);
    private static final Color LIST_PANEL = new Color(0xCC_0D_0B_12, true);

    private static final int COLOUR_TITLE = 0xFF_D9_C3_92;
    private static final int COLOUR_LABEL = 0xFF_7C_6C_57;
    private static final int COLOUR_VALUE = 0xFF_E6_E1_D6;
    private static final int COLOUR_OK = 0xFF_49_D9_C4;
    private static final int COLOUR_BAD = 0xFF_D9_5C_4A;
    private static final int COLOUR_WARN = 0xFF_E0_B0_4A;

    private ClientboundNavigationDataPacket data;
    @Nullable
    private UUID selected;
    private int scroll;
    private int refreshTimer = REFRESH_INTERVAL;
    private int ticksOpen;

    private Button initiateButton;
    private Button cancelButton;
    private Button headingButton;

    public RiftNavigationScreen(ClientboundNavigationDataPacket data) {
        super(AWLang.translate("gui.rift_navigation.title").component());
        this.data = data;
        selectFirstUsable();
    }

    public boolean matches(BlockPos drivePos) {
        return data.drivePos().equals(drivePos);
    }

    /** Replaces the console's contents with a fresh server snapshot, keeping the selection. */
    public void accept(ClientboundNavigationDataPacket packet) {
        this.data = packet;
        if (selected != null && packet.quotes().stream().noneMatch(q -> q.anchorId().equals(selected))) {
            selected = null;
        }
        if (selected == null) {
            selectFirstUsable();
        }
        updateButtons();
    }

    private void selectFirstUsable() {
        selected = data.quotes().stream()
                .filter(WarpQuote::usable)
                .map(WarpQuote::anchorId)
                .findFirst()
                .orElse(data.quotes().isEmpty() ? null : data.quotes().getFirst().anchorId());
    }

    @Override
    protected void init() {
        setWindowSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        super.init();
        clearWidgets();

        int buttonY = guiTop + WINDOW_HEIGHT - 26;
        initiateButton = Button.builder(AWLang.translate("gui.rift_navigation.initiate").component(),
                        b -> sendInitiate())
                .bounds(guiLeft + LIST_WIDTH + 14, buttonY, 118, 18)
                .build();
        cancelButton = Button.builder(AWLang.translate("gui.rift_navigation.abort").component(),
                        b -> sendCancel())
                .bounds(guiLeft + 10, buttonY, 118, 18)
                .build();
        headingButton = Button.builder(headingLabel(), b -> {
            PacketDistributor.sendToServer(ServerboundWarpCommandPacket.cycleHeading(data.drivePos()));
            playClick(1.0F);
        }).bounds(guiLeft + WINDOW_WIDTH - 92, guiTop + 18, 76, 14).build();
        headingButton.setTooltip(Tooltip.create(AWLang.translate("gui.rift_navigation.bow_hint").component()));

        addRenderableWidget(initiateButton);
        addRenderableWidget(cancelButton);
        addRenderableWidget(headingButton);
        updateButtons();
    }

    private void updateButtons() {
        RiftDriveState state = RiftDriveState.byIndex(data.stateIndex());
        WarpQuote quote = selectedQuote();
        initiateButton.active = !data.access().isFailure()
                && state.acceptsDestination()
                && quote != null
                && quote.usable();
        cancelButton.active = state.isCancellable();
        cancelButton.setMessage(state.isCancellable()
                ? AWLang.translate("gui.rift_navigation.abort").component()
                : AWLang.translate("gui.rift_navigation.close").component());
        if (headingButton != null) {
            headingButton.active = !state.isSequenceRunning();
            headingButton.setMessage(headingLabel());
        }
    }

    /**
      * "Bow: Right (N)" - the setting, which is relative to the drive, plus the compass point it
      * happens to be pointing at from where the ship is lying now.
      */
    private Component headingLabel() {
        DriveHeading setting = DriveHeading.byIndex(data.heading());
        String resolved = data.bearing().isBlank() ? "-" : data.bearing().substring(0, 1).toUpperCase();
        return AWLang.translate("gui.rift_navigation.bow",
                AWLang.translate(setting.translationKey()).string(), resolved).component();
    }

    @Nullable
    private WarpQuote selectedQuote() {
        if (selected == null) {
            return null;
        }
        for (WarpQuote quote : data.quotes()) {
            if (quote.anchorId().equals(selected)) {
                return quote;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ input

    private void sendInitiate() {
        if (selected == null) {
            return;
        }
        PacketDistributor.sendToServer(ServerboundWarpCommandPacket.initiate(data.drivePos(), selected));
        playClick(1.2F);
    }

    private void sendCancel() {
        if (RiftDriveState.byIndex(data.stateIndex()).isCancellable()) {
            PacketDistributor.sendToServer(ServerboundWarpCommandPacket.cancel(data.drivePos()));
            playClick(0.8F);
        } else {
            onClose();
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
        if (index >= 0 && index < data.quotes().size()) {
            selected = data.quotes().get(index).anchorId();
            updateButtons();
            playClick(1.0F);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maximum = Math.max(0, data.quotes().size() - VISIBLE_ROWS);
        scroll = Math.max(0, Math.min(maximum, scroll - (int) Math.signum(scrollY)));
        return true;
    }

    private int rowAt(double mouseX, double mouseY) {
        int listLeft = guiLeft + 10;
        int listTop = guiTop + 34;
        if (mouseX < listLeft || mouseX > listLeft + LIST_WIDTH) {
            return -1;
        }
        int relative = (int) ((mouseY - listTop) / ROW_HEIGHT);
        if (relative < 0 || relative >= VISIBLE_ROWS) {
            return -1;
        }
        return scroll + relative;
    }

    @Override
    public void tick() {
        super.tick();
        ticksOpen++;
        if (--refreshTimer <= 0) {
            refreshTimer = REFRESH_INTERVAL;
            PacketDistributor.sendToServer(new ServerboundNavigationRequestPacket(data.drivePos()));
        }
    }

    // ----------------------------------------------------------------- render

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        new BoxElement()
                .withBackground(PANEL)
                .gradientBorder(BORDER_TOP, BORDER_BOTTOM)
                .at(guiLeft, guiTop)
                .withBounds(WINDOW_WIDTH - 8, WINDOW_HEIGHT - 8)
                .render(graphics);

        RiftDriveTier tier = RiftDriveTier.byIndex(data.tierIndex());
        RiftDriveState state = RiftDriveState.byIndex(data.stateIndex());

        graphics.drawString(font, AWLang.translate("gui.rift_navigation.title").component(),
                guiLeft + 10, guiTop + 10, COLOUR_TITLE, false);
        graphics.drawString(font, AWLang.translate(tier.translationKey()).component(),
                guiLeft + 10, guiTop + 21, COLOUR_LABEL, false);

        String shipLabel = data.airshipName().isBlank()
                ? AWLang.translate("gui.rift_navigation.unnamed_ship").string()
                : data.airshipName();
        int shipWidth = font.width(shipLabel);
        graphics.drawString(font, shipLabel, guiLeft + WINDOW_WIDTH - 16 - shipWidth, guiTop + 10, COLOUR_LABEL, false);

        renderList(graphics, mouseX, mouseY);
        renderDetails(graphics, tier, state);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int listLeft = guiLeft + 10;
        int listTop = guiTop + 34;
        int listHeight = VISIBLE_ROWS * ROW_HEIGHT;

        new BoxElement()
                .withBackground(LIST_PANEL)
                .flatBorder(new Color(0x66_3B_31_24, true))
                .at(listLeft - 2, listTop - 2)
                .withBounds(LIST_WIDTH, listHeight)
                .render(graphics);

        List<WarpQuote> quotes = data.quotes();
        if (quotes.isEmpty()) {
            graphics.drawString(font, AWLang.translate("gui.rift_navigation.no_anchors").component(),
                    listLeft + 4, listTop + 4, COLOUR_LABEL, false);
            return;
        }

        int hovered = rowAt(mouseX, mouseY);
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scroll + row;
            if (index >= quotes.size()) {
                break;
            }
            WarpQuote quote = quotes.get(index);
            int y = listTop + row * ROW_HEIGHT;
            boolean isSelected = quote.anchorId().equals(selected);

            if (isSelected) {
                graphics.fill(listLeft - 2, y - 1, listLeft + LIST_WIDTH - 2, y + ROW_HEIGHT - 2, 0x40_49_D9_C4);
            } else if (index == hovered) {
                graphics.fill(listLeft - 2, y - 1, listLeft + LIST_WIDTH - 2, y + ROW_HEIGHT - 2, 0x22_FF_FF_FF);
            }

            int colour = quote.usable() ? COLOUR_VALUE : COLOUR_BAD;
            String marker = isSelected ? "> " : "  ";
            String name = font.plainSubstrByWidth(quote.name(), LIST_WIDTH - 46);
            graphics.drawString(font, marker + name, listLeft + 2, y + 2, colour, false);

            String right = quote.sameDimension() ? AWLang.distance(quote.distance()) : "--";
            graphics.drawString(font, right, listLeft + LIST_WIDTH - 8 - font.width(right), y + 2, COLOUR_LABEL, false);
        }

        if (quotes.size() > VISIBLE_ROWS) {
            int barHeight = Math.max(8, listHeight * VISIBLE_ROWS / quotes.size());
            int maximum = quotes.size() - VISIBLE_ROWS;
            int barY = listTop + (listHeight - barHeight) * scroll / Math.max(1, maximum);
            graphics.fill(listLeft + LIST_WIDTH - 5, barY, listLeft + LIST_WIDTH - 3, barY + barHeight, 0x88_D9_C3_92);
        }
    }

    private void renderDetails(GuiGraphics graphics, RiftDriveTier tier, RiftDriveState state) {
        int panelLeft = guiLeft + LIST_WIDTH + 14;
        int y = guiTop + 34;
        WarpQuote quote = selectedQuote();

        new BoxElement()
                .withBackground(LIST_PANEL)
                .flatBorder(new Color(0x66_3B_31_24, true))
                .at(panelLeft - 2, y - 2)
                .withBounds(WINDOW_WIDTH - LIST_WIDTH - 30, VISIBLE_ROWS * ROW_HEIGHT)
                .render(graphics);

        graphics.drawString(font, AWLang.translate("gui.rift_navigation.destination").component(),
                panelLeft + 2, y + 2, COLOUR_LABEL, false);
        graphics.drawString(font, quote == null
                        ? AWLang.translate("gui.rift_navigation.none").string()
                        : font.plainSubstrByWidth(quote.name(), WINDOW_WIDTH - LIST_WIDTH - 38),
                panelLeft + 2, y + 13, COLOUR_TITLE, false);

        int line = y + 28;
        line = detail(graphics, panelLeft, line, "gui.rift_navigation.distance",
                quote == null ? "-" : quote.sameDimension() ? AWLang.distance(quote.distance()) + " m" : "--",
                COLOUR_VALUE);
        line = detail(graphics, panelLeft, line, "gui.rift_navigation.cost",
                quote == null ? "-" : AWLang.percent(quote.cost()),
                quote != null && quote.cost() > data.charge() ? COLOUR_BAD : COLOUR_VALUE);
        line = detail(graphics, panelLeft, line, "gui.rift_navigation.charge",
                AWLang.percent(data.charge()),
                data.charge() >= 1.0F ? COLOUR_OK : COLOUR_WARN);
        line = detail(graphics, panelLeft, line, "gui.rift_navigation.range",
                AWLang.distance(data.maximumRange()) + " m", COLOUR_VALUE);
        line = detail(graphics, panelLeft, line, "gui.rift_navigation.dimension",
                quote == null ? "-" : quote.dimension().location().getPath(), COLOUR_VALUE);

        Component status;
        int statusColour;
        if (data.access().isFailure()) {
            status = AWLang.translate(data.access().translationKey()).component();
            statusColour = COLOUR_BAD;
        } else if (quote == null) {
            status = AWLang.translate("gui.rift_navigation.no_selection").component();
            statusColour = COLOUR_WARN;
        } else if (quote.usable()) {
            status = AWLang.translate("gui.rift_navigation.safe").component();
            statusColour = COLOUR_OK;
        } else {
            status = AWLang.translate(quote.failure().translationKey()).component();
            statusColour = COLOUR_BAD;
        }
        detail(graphics, panelLeft, line, "gui.rift_navigation.status", status.getString(), statusColour);

        // Drive state and charge bar along the bottom of the details panel.
        int barTop = guiTop + WINDOW_HEIGHT - 44;
        graphics.drawString(font, AWLang.translate(state.translationKey()).component(),
                guiLeft + 10, barTop, stateColour(state), false);
        renderChargeBar(graphics, guiLeft + 10, barTop + 11, WINDOW_WIDTH - 28);

        if (data.cooldown() > 0) {
            String cooldown = AWLang.translate("gui.rift_navigation.cooldown", data.cooldown() / 20).string();
            graphics.drawString(font, cooldown,
                    guiLeft + WINDOW_WIDTH - 18 - font.width(cooldown), barTop, COLOUR_WARN, false);
        }
    }

    private int detail(GuiGraphics graphics, int left, int y, String key, String value, int colour) {
        graphics.drawString(font, AWLang.translate(key).component(), left + 2, y, COLOUR_LABEL, false);
        int width = WINDOW_WIDTH - LIST_WIDTH - 34;
        String trimmed = font.plainSubstrByWidth(value, width);
        graphics.drawString(font, trimmed, left + width - font.width(trimmed) + 2, y, colour, false);
        return y + 11;
    }

    private void renderChargeBar(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 5, 0xFF_1A_16_12);
        int filled = (int) (width * Math.max(0.0F, Math.min(1.0F, data.charge())));
        // A slow shimmer while charging so the bar reads as "working", not "stuck".
        int pulse = (int) (Math.sin((ticksOpen % 80) / 80.0D * Math.PI * 2.0D) * 24.0D);
        int colour = data.charge() >= 1.0F
                ? 0xFF_49_D9_C4
                : 0xFF_00_00_00 | (Math.min(255, 0xC0 + pulse) << 16) | (0x90 << 8) | 0x3A;
        graphics.fill(x, y, x + filled, y + 5, colour);
    }

    private static int stateColour(RiftDriveState state) {
        return switch (state) {
            case ERROR -> COLOUR_BAD;
            case COOLDOWN -> COLOUR_WARN;
            case WARPING, ARRIVING, STABILIZING, DESTINATION_SELECTED -> 0xFF_C8_6C_FF;
            case CHARGED -> COLOUR_OK;
            default -> COLOUR_LABEL;
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
