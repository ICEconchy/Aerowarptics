package uk.co.iceconchy.aerowarptics.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.astrolabe.DestinationSurvey;
import uk.co.iceconchy.aerowarptics.client.screen.AWLayout.Rect;
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
public class AstrolabeChartScreen extends AWWindowScreen {

    private static final int REFRESH_INTERVAL = 40;

    private static final AWLayouts.Chart LAYOUT = AWLayouts.chart();
    private static final int VISIBLE_ROWS = LAYOUT.visibleRows();

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

    /**
     * The marker beside the selected row, which slides rather than jumping.
     *
     * <p>Measured in rows from the top of the visible list, so scrolling moves it with the content.
     */
    private final AWAnim.Eased marker = new AWAnim.Eased(0.4F, -1.0F);

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
        setWindowSize(AWLayouts.CHART_WIDTH, AWLayouts.CHART_HEIGHT);
        super.init();
        placeWindow(AWLayouts.CHART_WIDTH, AWLayouts.CHART_HEIGHT);
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

    private int selectedIndex() {
        List<WarpQuote> quotes = data.quotes();
        for (int index = 0; index < quotes.size(); index++) {
            if (quotes.get(index).anchorId().equals(selected)) {
                return index;
            }
        }
        return -1;
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
        // Into window coordinates, so a click lands where the picture is when the window is scaled down.
        mouseX = unscaleX(mouseX);
        mouseY = unscaleY(mouseY);
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
        Rect list = LAYOUT.list();
        double x = mouseX - guiLeft;
        double y = mouseY - guiTop;
        if (!list.contains(x, y)) {
            return -1;
        }
        int relative = (int) ((y - list.y()) / AWLayout.ROW);
        if (relative < 0 || relative >= VISIBLE_ROWS) {
            return -1;
        }
        return scroll + relative;
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
            PacketDistributor.sendToServer(ServerboundAstrolabePacket.open(data.astrolabePos()));
        }
    }

    // ----------------------------------------------------------------- render

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        AWScreenStyle.window(graphics, guiLeft, guiTop, AWLayouts.CHART_WIDTH, AWLayouts.CHART_HEIGHT);

        RiftDriveTier tier = RiftDriveTier.byIndex(data.tierIndex());
        String subtitle = data.hasDrive()
                ? AWLang.translate(tier.translationKey()).string() + "  -  "
                        + AWLang.distance(data.maximumRange())
                : AWLang.translate("gui.astrolabe.no_drive").string();
        String ship = data.airshipName().isBlank()
                ? AWLang.translate("gui.rift_navigation.unnamed_ship").string()
                : data.airshipName();

        AWScreenStyle.header(graphics, font, guiLeft, guiTop, LAYOUT.header(),
                AWLang.translate("gui.astrolabe.title").component(),
                subtitle,
                ship,
                data.truncated()
                        ? AWLang.translate("gui.astrolabe.anchors_capped",
                                data.quotes().size(), data.totalAvailable()).component()
                        : Component.literal(data.quotes().size() + " "
                                + AWLang.translate("gui.astrolabe.anchors").string()),
                data.truncated() ? AWScreenStyle.WARN : AWScreenStyle.LABEL,
                data.hasDrive() ? AWScreenStyle.LABEL : AWScreenStyle.BAD);

        renderCourse(graphics);
        renderList(graphics, mouseX, mouseY, partialTicks);
        renderPreview(graphics, partialTicks);
        renderDetails(graphics);
    }

    /**
     * Where the ship is actually going, stated plainly and above everything else.
     *
     * <p>The list below is a list of anchors, and a Rift Probe can aim the same drive at a place no
     * anchor marks. Without this line the chart would go on highlighting whichever anchor it last
     * showed and be confidently wrong about the destination, which is the one thing a chart must
     * never be.
     */
    private void renderCourse(GuiGraphics graphics) {
        Rect strip = LAYOUT.course();
        int left = guiLeft + strip.x();
        int top = guiTop + strip.y() + 2;

        Component label = AWLang.translate("gui.astrolabe.course").component();
        graphics.drawString(font, label, left, top, AWScreenStyle.LABEL, false);
        int valueLeft = left + font.width(label.getString()) + 6;

        if (!data.hasCourse()) {
            graphics.drawString(font, AWLang.translate("gui.astrolabe.course_none").component(),
                    valueLeft, top, AWScreenStyle.LABEL, false);
            return;
        }

        String name = data.courseLabel().isBlank()
                ? AWLang.translate("gui.rift_navigation.none").string()
                : data.courseLabel();
        int room = strip.width() - (valueLeft - left) - 4;

        if (data.courseIsFix()) {
            // The coordinates matter here in a way an anchor's never do: nothing is standing at the
            // far end to be recognised by name, so the position is the only thing that identifies it.
            BlockPos fix = data.courseFix();
            String where = fix.getX() + ", " + fix.getY() + ", " + fix.getZ();
            String source = AWLang.translate("gui.astrolabe.course_sounding").string();
            graphics.drawString(font, AWScreenStyle.trim(font, name + "  " + where, room - font.width(source) - 8),
                    valueLeft, top, AWScreenStyle.ACCENT, false);
            graphics.drawString(font, source, guiLeft + strip.right() - font.width(source), top,
                    AWScreenStyle.LABEL, false);
        } else {
            graphics.drawString(font, AWScreenStyle.trim(font, name, room),
                    valueLeft, top, AWScreenStyle.OK, false);
        }
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        Rect list = LAYOUT.list();
        AWScreenStyle.panel(graphics, guiLeft, guiTop, list);

        int left = guiLeft + list.x();
        int top = guiTop + list.y();

        List<WarpQuote> quotes = data.quotes();
        if (quotes.isEmpty()) {
            graphics.drawString(font, AWLang.translate("gui.rift_navigation.no_anchors").component(),
                    left + 2, top + 4, AWScreenStyle.LABEL, false);
            return;
        }

        // The sliding selection marker, drawn under the rows so text stays crisp over it.
        float markerRow = marker.get(partialTicks);
        if (markerRow >= -0.5F && markerRow <= VISIBLE_ROWS - 0.5F) {
            int y = top + Math.round(markerRow * AWLayout.ROW);
            graphics.fill(left - AWLayout.INSET, y, left + list.width() - AWLayout.INSET,
                    y + AWLayout.ROW, 0x33_49_D9_C4);
            AWScreenStyle.selectionBar(graphics, left - AWLayout.INSET, y, AWLayout.ROW, AWScreenStyle.OK);
        }

        int hovered = rowAt(mouseX, mouseY);
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scroll + row;
            if (index >= quotes.size()) {
                break;
            }
            WarpQuote quote = quotes.get(index);
            int y = top + row * AWLayout.ROW;

            if (index == hovered && !quote.anchorId().equals(selected)) {
                graphics.fill(left - AWLayout.INSET, y, left + list.width() - AWLayout.INSET,
                        y + AWLayout.ROW, 0x22_FF_FF_FF);
            }

            int colour = quote.usable() ? AWScreenStyle.VALUE : AWScreenStyle.BAD;
            String right = quote.sameDimension() ? AWLang.distance(quote.distance()) : "--";
            int room = list.width() - font.width(right) - 16;
            graphics.drawString(font, AWScreenStyle.trim(font, quote.name(), room),
                    left + 6, y + 3, colour, false);
            graphics.drawString(font, right, left + list.width() - font.width(right) - 8,
                    y + 3, AWScreenStyle.LABEL, false);
        }

        AWScreenStyle.scrollbar(graphics, left + list.width() - AWLayout.INSET, top,
                VISIBLE_ROWS * AWLayout.ROW, quotes.size(), VISIBLE_ROWS, scroll);
    }

    /**
     * The destination from above.
     *
     * <p>A baked texture rather than a field of rectangles - see {@link SurveyTexture} - which is what
     * makes a sample per block affordable to draw at all.
     */
    private void renderPreview(GuiGraphics graphics, float partialTicks) {
        Rect panel = LAYOUT.preview();
        AWScreenStyle.panel(graphics, guiLeft, guiTop, panel);

        int left = guiLeft + panel.x();
        int top = guiTop + panel.y();

        DestinationSurvey survey = preview;
        if (survey != null && previewTexture == null) {
            previewTexture = SurveyTexture.of(survey);
        }
        SurveyTexture texture = previewTexture;
        if (survey == null || texture == null) {
            String message = AWLang.translate(selected == null
                    ? "gui.astrolabe.no_selection" : "gui.astrolabe.surveying").string();
            graphics.drawString(font, message, left + (panel.width() - font.width(message)) / 2,
                    top + panel.height() / 2 - 4, AWScreenStyle.LABEL, false);
            return;
        }

        texture.draw(graphics, left, top);

        // The anchor itself, pulsing at the middle of its own survey. Without it the picture is a
        // patch of ground with no indication of which part of it the ship is aiming at.
        int centreX = left + AWLayouts.PREVIEW / 2;
        int centreY = top + AWLayouts.PREVIEW / 2;
        int pulse = Math.round(AWAnim.pulse(ticksOpen + partialTicks, 32.0F) * 3.0F) + 3;
        graphics.fill(centreX - pulse, centreY, centreX + pulse + 1, centreY + 1, 0xFF_FF_FF_FF);
        graphics.fill(centreX, centreY - pulse, centreX + 1, centreY + pulse + 1, 0xFF_FF_FF_FF);

        // North, because a top-down picture with no orientation is a picture of nowhere.
        graphics.drawString(font, "N", centreX - 2, top + 2, 0xFF_FF_FF_FF, true);

        Rect scale = LAYOUT.scale();
        String scaleLabel = AWLang.translate("gui.astrolabe.scale", survey.radius() * 2).string();
        graphics.drawString(font, scaleLabel, guiLeft + scale.x(), guiTop + scale.y(),
                AWScreenStyle.LABEL, false);

        String contours = AWLang.translate("gui.astrolabe.contours", DestinationSurvey.CONTOUR_INTERVAL).string();
        graphics.drawString(font, contours, guiLeft + scale.right() - font.width(contours),
                guiTop + scale.y(), AWScreenStyle.LABEL, false);
    }

    private void renderDetails(GuiGraphics graphics) {
        Rect panel = LAYOUT.detail();
        AWScreenStyle.panel(graphics, guiLeft, guiTop, panel);

        int left = guiLeft + panel.x() + 2;
        int top = guiTop + panel.y() + 2;
        int width = panel.width() - 4;

        WarpQuote quote = selectedQuote();
        graphics.drawString(font, quote == null
                        ? AWLang.translate("gui.rift_navigation.none").string()
                        : AWScreenStyle.trim(font, quote.name(), width - 2),
                left, top, AWScreenStyle.TITLE, false);
        AWScreenStyle.rule(graphics, left, top + 10, width);

        int line = top + 15;
        line = AWScreenStyle.readout(graphics, font, left, line, width,
                AWLang.translate("gui.rift_navigation.distance").component(),
                quote == null ? "-" : quote.sameDimension() ? AWLang.distance(quote.distance()) : "--",
                AWScreenStyle.VALUE);
        line = AWScreenStyle.readout(graphics, font, left, line, width,
                AWLang.translate("gui.rift_navigation.cost").component(),
                quote == null ? "-" : AWLang.percent(quote.cost()),
                quote != null && quote.cost() > data.charge() ? AWScreenStyle.BAD : AWScreenStyle.VALUE);
        line = AWScreenStyle.readout(graphics, font, left, line, width,
                AWLang.translate("gui.astrolabe.ground").component(), groundLabel(), AWScreenStyle.VALUE);

        Component status;
        int statusColour;
        if (data.access().isFailure()) {
            status = AWLang.translate(data.access().translationKey()).component();
            statusColour = AWScreenStyle.BAD;
        } else if (quote == null) {
            status = AWLang.translate("gui.rift_navigation.no_selection").component();
            statusColour = AWScreenStyle.WARN;
        } else if (quote.anchorId().equals(data.selected())) {
            status = AWLang.translate("gui.astrolabe.course_set").component();
            statusColour = AWScreenStyle.OK;
        } else if (quote.usable()) {
            // Reachable, but not where the ship is pointed. Saying "course set" under every anchor
            // the drive merely could reach is how the chart used to look identical whether or not
            // you had chosen anything.
            status = AWLang.translate("gui.astrolabe.reachable").component();
            statusColour = AWScreenStyle.LABEL;
        } else {
            status = AWLang.translate(quote.failure().translationKey()).component();
            statusColour = AWScreenStyle.BAD;
        }
        AWScreenStyle.pill(graphics, font, left, line + 2, status.getString(), statusColour);
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
