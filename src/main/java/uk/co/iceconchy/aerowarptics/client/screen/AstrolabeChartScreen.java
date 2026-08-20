package uk.co.iceconchy.aerowarptics.client.screen;

import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.astrolabe.DestinationSurvey;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;
import uk.co.iceconchy.aerowarptics.network.ClientboundAstrolabeChartPacket;
import uk.co.iceconchy.aerowarptics.network.ServerboundAstrolabePacket;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.warp.WarpQuote;

import java.util.List;
import java.util.UUID;

/**
 * The Astrolabe's chart: pick where the ship is going.
 *
 * <p>Purely a view over what the server sent. Distances, costs and refusals all arrive pre-computed,
 * and the only thing the screen ever sends back is an anchor id - so it cannot offer a destination the
 * drive would turn down.
 *
 * <p>Choosing here sets the course and stops. Nothing on this screen launches anything, because the
 * table is the chart room and the jump is fired from the drive.
 */
@OnlyIn(Dist.CLIENT)
public class AstrolabeChartScreen extends AbstractSimiScreen {

    private static final int WINDOW_WIDTH = 314;
    private static final int WINDOW_HEIGHT = 262;
    private static final int LIST_WIDTH = 150;
    private static final int ROW_HEIGHT = 13;
    private static final int VISIBLE_ROWS = 16;
    private static final int REFRESH_INTERVAL = 40;

    /** Side of the preview panel, in pixels. One pixel per sampled block. */
    private static final int PREVIEW_SIZE = 2 * DestinationSurvey.RADIUS / DestinationSurvey.STEP + 1;

    private static final int CONTENT_TOP = 34;

    private ClientboundAstrolabeChartPacket data;
    @Nullable
    private UUID selected;
    @Nullable
    private UUID previewFor;
    @Nullable
    private DestinationSurvey preview;
    @Nullable
    private SurveyTexture previewTexture;
    private int scroll;
    private int refreshTimer = REFRESH_INTERVAL;
    private int ticksOpen;

    public AstrolabeChartScreen(ClientboundAstrolabeChartPacket data) {
        super(AWLang.translate("gui.astrolabe.title").component());
        this.data = data;
        this.selected = data.selected();
        if (selected == null) {
            selectFirstUsable();
        }
        requestPreview();
    }

    public boolean matches(BlockPos astrolabePos) {
        return data.astrolabePos().equals(astrolabePos);
    }

    /** Replaces the chart's contents with a fresh server snapshot, keeping the selection. */
    public void accept(ClientboundAstrolabeChartPacket packet) {
        this.data = packet;
        if (selected != null && packet.quotes().stream().noneMatch(q -> q.anchorId().equals(selected))) {
            selected = null;
            dropPreview();
            previewFor = null;
        }
        if (selected == null) {
            selectFirstUsable();
            requestPreview();
        }
    }

    /** A survey came back. Ignored unless it is a picture of what is currently being looked at. */
    public void acceptPreview(ClientboundAstrolabeChartPacket.Preview packet) {
        if (!packet.anchorId().equals(selected)) {
            return;
        }
        dropPreview();
        preview = packet.survey();
        previewFor = packet.anchorId();
        previewTexture = SurveyTexture.of(packet.survey());
    }

    private void dropPreview() {
        preview = null;
        releaseTexture();
    }

    private void releaseTexture() {
        if (previewTexture != null) {
            previewTexture.close();
            previewTexture = null;
        }
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
    }

    @Override
    public void removed() {
        // The texture is a GPU allocation this screen owns outright and nothing else will free it.
        // The survey it was baked from is kept: a screen can be closed and shown again, and re-baking
        // an image already in memory is a great deal cheaper than asking the server to survey again.
        releaseTexture();
        super.removed();
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

    /**
     * Asks the server for a picture of the current selection.
     *
     * <p>Only when the selection has actually changed. Surveying terrain is the expensive half of this
     * screen, and re-requesting the same view every time the list refreshes would make idling on a
     * chart cost more than using one.
     */
    private void requestPreview() {
        if (selected == null || selected.equals(previewFor)) {
            return;
        }
        dropPreview();
        PacketDistributor.sendToServer(ServerboundAstrolabePacket.preview(data.astrolabePos(), selected));
    }

    private void sendSelection() {
        if (selected != null) {
            PacketDistributor.sendToServer(ServerboundAstrolabePacket.select(data.astrolabePos(), selected));
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
            WarpQuote quote = data.quotes().get(index);
            boolean changed = !quote.anchorId().equals(selected);
            selected = quote.anchorId();
            if (changed) {
                requestPreview();
            }
            // Clicking a destination is the decision. There is no confirm step, because there is
            // nothing to confirm - setting a course does not move anything.
            sendSelection();
            playClick(quote.usable() ? 1.2F : 0.7F);
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
        int listTop = guiTop + CONTENT_TOP;
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
            PacketDistributor.sendToServer(ServerboundAstrolabePacket.open(data.astrolabePos()));
        }
    }

    // ----------------------------------------------------------------- render

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        AWScreenStyle.window(graphics, guiLeft, guiTop, WINDOW_WIDTH, WINDOW_HEIGHT);

        graphics.drawString(font, AWLang.translate("gui.astrolabe.title").component(),
                guiLeft + 10, guiTop + 10, AWScreenStyle.TITLE, false);

        String ship = data.airshipName().isBlank()
                ? AWLang.translate("gui.rift_navigation.unnamed_ship").string()
                : data.airshipName();
        graphics.drawString(font, ship, guiLeft + WINDOW_WIDTH - 16 - font.width(ship),
                guiTop + 10, AWScreenStyle.LABEL, false);

        RiftDriveTier tier = RiftDriveTier.byIndex(data.tierIndex());
        String subtitle = data.hasDrive()
                ? AWLang.translate(tier.translationKey()).string() + "  -  "
                        + AWLang.distance(data.maximumRange()) + " m"
                : AWLang.translate("gui.astrolabe.no_drive").string();
        graphics.drawString(font, subtitle, guiLeft + 10, guiTop + 21,
                data.hasDrive() ? AWScreenStyle.LABEL : AWScreenStyle.BAD, false);

        renderList(graphics, mouseX, mouseY);
        renderPreview(graphics);
        renderDetails(graphics);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int listLeft = guiLeft + 10;
        int listTop = guiTop + CONTENT_TOP;
        int listHeight = VISIBLE_ROWS * ROW_HEIGHT;

        AWScreenStyle.inset(graphics, listLeft - 2, listTop - 2, LIST_WIDTH, listHeight);

        List<WarpQuote> quotes = data.quotes();
        if (quotes.isEmpty()) {
            graphics.drawString(font, AWLang.translate("gui.rift_navigation.no_anchors").component(),
                    listLeft + 4, listTop + 4, AWScreenStyle.LABEL, false);
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

            int colour = quote.usable() ? AWScreenStyle.VALUE : AWScreenStyle.BAD;
            String marker = isSelected ? "> " : "  ";
            String name = font.plainSubstrByWidth(quote.name(), LIST_WIDTH - 52);
            graphics.drawString(font, marker + name, listLeft + 2, y + 2, colour, false);

            String right = quote.sameDimension() ? AWLang.distance(quote.distance()) : "--";
            graphics.drawString(font, right, listLeft + LIST_WIDTH - 8 - font.width(right),
                    y + 2, AWScreenStyle.LABEL, false);
        }

        if (quotes.size() > VISIBLE_ROWS) {
            int barHeight = Math.max(8, listHeight * VISIBLE_ROWS / quotes.size());
            int maximum = quotes.size() - VISIBLE_ROWS;
            int barY = listTop + (listHeight - barHeight) * scroll / Math.max(1, maximum);
            graphics.fill(listLeft + LIST_WIDTH - 5, barY, listLeft + LIST_WIDTH - 3, barY + barHeight, 0x88_D9_C3_92);
        }
    }

    private int previewLeft() {
        return guiLeft + LIST_WIDTH + 22;
    }

    /**
     * The destination from above.
     *
     * <p>A baked texture rather than a field of rectangles - see {@link SurveyTexture} - which is what
     * makes a sample per block affordable to draw at all.
     */
    private void renderPreview(GuiGraphics graphics) {
        int left = previewLeft();
        int top = guiTop + CONTENT_TOP;
        AWScreenStyle.inset(graphics, left - 2, top - 2, PREVIEW_SIZE + 4, PREVIEW_SIZE + 4);

        DestinationSurvey survey = preview;
        if (survey != null && previewTexture == null) {
            previewTexture = SurveyTexture.of(survey);
        }
        SurveyTexture texture = previewTexture;
        if (survey == null || texture == null) {
            String message = AWLang.translate(selected == null
                    ? "gui.astrolabe.no_selection" : "gui.astrolabe.surveying").string();
            graphics.drawString(font, message, left + (PREVIEW_SIZE - font.width(message)) / 2,
                    top + PREVIEW_SIZE / 2 - 4, AWScreenStyle.LABEL, false);
            return;
        }

        texture.draw(graphics, left, top);

        // The anchor itself, pulsing at the middle of its own survey. Without it the picture is a
        // patch of ground with no indication of which part of it the ship is aiming at.
        int centreX = left + PREVIEW_SIZE / 2;
        int centreY = top + PREVIEW_SIZE / 2;
        int pulse = (int) (Math.abs(Math.sin(ticksOpen / 16.0D)) * 3.0D) + 3;
        graphics.fill(centreX - pulse, centreY, centreX + pulse + 1, centreY + 1, 0xFF_FF_FF_FF);
        graphics.fill(centreX, centreY - pulse, centreX + 1, centreY + pulse + 1, 0xFF_FF_FF_FF);

        // North, because a top-down picture with no orientation is a picture of nowhere.
        graphics.drawString(font, "N", left + PREVIEW_SIZE / 2 - 2, top + 2, 0xFF_FF_FF_FF, true);

        String scaleLabel = AWLang.translate("gui.astrolabe.scale", survey.radius() * 2).string();
        graphics.drawString(font, scaleLabel, left, top + PREVIEW_SIZE + 5, AWScreenStyle.LABEL, false);

        String contours = AWLang.translate("gui.astrolabe.contours", DestinationSurvey.CONTOUR_INTERVAL).string();
        graphics.drawString(font, contours, left + PREVIEW_SIZE - font.width(contours),
                top + PREVIEW_SIZE + 5, AWScreenStyle.LABEL, false);
    }

    private void renderDetails(GuiGraphics graphics) {
        int left = previewLeft();
        int top = guiTop + CONTENT_TOP + PREVIEW_SIZE + 20;
        int width = PREVIEW_SIZE + 4;
        AWScreenStyle.inset(graphics, left - 2, top - 2, width, 68);

        WarpQuote quote = selectedQuote();
        graphics.drawString(font, quote == null
                        ? AWLang.translate("gui.rift_navigation.none").string()
                        : font.plainSubstrByWidth(quote.name(), width - 8),
                left + 2, top + 2, AWScreenStyle.TITLE, false);

        int line = top + 15;
        line = detail(graphics, left, line, width, "gui.rift_navigation.distance",
                quote == null ? "-" : quote.sameDimension() ? AWLang.distance(quote.distance()) + " m" : "--",
                AWScreenStyle.VALUE);
        line = detail(graphics, left, line, width, "gui.rift_navigation.cost",
                quote == null ? "-" : AWLang.percent(quote.cost()),
                quote != null && quote.cost() > data.charge() ? AWScreenStyle.BAD : AWScreenStyle.VALUE);
        line = detail(graphics, left, line, width, "gui.astrolabe.ground", groundLabel(), AWScreenStyle.VALUE);

        Component status;
        int statusColour;
        if (data.access().isFailure()) {
            status = AWLang.translate(data.access().translationKey()).component();
            statusColour = AWScreenStyle.BAD;
        } else if (quote == null) {
            status = AWLang.translate("gui.rift_navigation.no_selection").component();
            statusColour = AWScreenStyle.WARN;
        } else if (quote.usable()) {
            status = AWLang.translate("gui.astrolabe.course_set").component();
            statusColour = AWScreenStyle.OK;
        } else {
            status = AWLang.translate(quote.failure().translationKey()).component();
            statusColour = AWScreenStyle.BAD;
        }
        detail(graphics, left, line, width, "gui.rift_navigation.status", status.getString(), statusColour);
    }

    /**
     * How high the ground is at the anchor, and how much of the survey came back at all.
     *
     * <p>The second half matters: blank ground on this chart means nobody has been there, not that it
     * is flat. Saying so is the difference between an honest map and a misleading one.
     */
    private String groundLabel() {
        DestinationSurvey survey = preview;
        if (survey == null) {
            return "-";
        }
        if (survey.groundY() == DestinationSurvey.NO_GROUND) {
            return AWLang.translate("gui.astrolabe.unsurveyed").string();
        }
        int coverage = Math.round(survey.coverage() * 100.0F);
        String height = "y " + survey.groundY();
        return coverage >= 99 ? height : height + "  (" + coverage + "%)";
    }

    private int detail(GuiGraphics graphics, int left, int y, int width, String key, String value, int colour) {
        graphics.drawString(font, AWLang.translate(key).component(), left + 2, y, AWScreenStyle.LABEL, false);
        String trimmed = font.plainSubstrByWidth(value, width - 8);
        graphics.drawString(font, trimmed, left + width - 8 - font.width(trimmed), y, colour, false);
        return y + 13;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
