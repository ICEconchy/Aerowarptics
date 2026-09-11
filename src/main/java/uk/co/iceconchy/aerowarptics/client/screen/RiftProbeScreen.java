package uk.co.iceconchy.aerowarptics.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.astrolabe.DestinationSurvey;
import uk.co.iceconchy.aerowarptics.client.screen.AWLayout.Rect;
import uk.co.iceconchy.aerowarptics.network.ClientboundProbePacket;
import uk.co.iceconchy.aerowarptics.network.ServerboundProbePacket;
import uk.co.iceconchy.aerowarptics.probe.ProbeBearing;
import uk.co.iceconchy.aerowarptics.probe.ProbeSounding;
import uk.co.iceconchy.aerowarptics.probe.ProbeState;
import uk.co.iceconchy.aerowarptics.probe.ProbeVerdict;
import uk.co.iceconchy.aerowarptics.util.AWLang;

/**
 * The Rift Probe's panel: aim a sounding, throw it, and look at what comes back.
 *
 * <p>Two controls and one picture. The dial says which way, the slider says how far, and the map is
 * the answer - which is the same map the Astrolabe draws of an anchor, on purpose. A pilot deciding
 * whether to commit a hull to somewhere nobody has been should be reading the same kind of evidence
 * they read everywhere else, not a score out of ten.
 *
 * <p>Nothing here decides anything. The bearing, the range and the sounding are all the server's; the
 * screen sends the request and draws the reply, because a sounding is a request to generate terrain
 * and that is not something a client gets to do on its own say-so.
 */
@OnlyIn(Dist.CLIENT)
public class RiftProbeScreen extends AWWindowScreen {

    private static final int REFRESH_INTERVAL = 10;

    private static final AWLayouts.Probe LAYOUT = AWLayouts.probe();

    private ClientboundProbePacket data;

    @Nullable
    private ProbeSounding reading;
    @Nullable
    private SurveyTexture texture;
    /** Whether the reading currently on screen is the one the server says the probe holds. */
    private boolean readingCurrent;

    private int refreshTimer = REFRESH_INTERVAL;
    private int ticksOpen;

    /** The needle chases the dial rather than snapping, so a change of bearing reads as a turn. */
    private final AWAnim.Eased needle = new AWAnim.Eased(0.35F);
    private final AWAnim.Eased fill = new AWAnim.Eased(0.25F);
    private final AWAnim.Eased reach = new AWAnim.Eased(0.3F);

    private Button soundButton;
    private Button courseButton;

    private boolean draggingRange;

    public RiftProbeScreen(ClientboundProbePacket data) {
        super(AWLang.translate("gui.rift_probe.title").component());
        this.data = data;
        this.needle.snap(data.bearing().degrees());
        this.fill.snap(supplyFraction(data));
        this.reach.snap(data.reachProgress());
    }

    public boolean matches(BlockPos probePos) {
        return data.probePos().equals(probePos);
    }

    public void accept(ClientboundProbePacket packet) {
        boolean sameProbe = packet.probePos().equals(data.probePos());
        this.data = packet;
        if (!sameProbe) {
            dropReading();
        }
        // A reading the server no longer has is a picture of a bearing the dial has since moved off.
        if (!packet.hasReading()) {
            dropReading();
        } else if (reading == null && !readingCurrent) {
            requestReading();
        }
        aimNeedle(packet.bearing());
        fill.set(supplyFraction(packet));
        reach.set(packet.reachProgress());
        updateButtons();
    }

    public void acceptReading(ProbeSounding sounding) {
        dropReading();
        reading = sounding;
        readingCurrent = true;
        texture = SurveyTexture.of(sounding.survey());
    }

    private void dropReading() {
        reading = null;
        readingCurrent = false;
        releaseTexture();
    }

    private void releaseTexture() {
        if (texture != null) {
            texture.close();
            texture = null;
        }
    }

    private static float supplyFraction(ClientboundProbePacket packet) {
        return packet.capacity() <= 0 ? 0.0F : packet.essence() / (float) packet.capacity();
    }

    /**
     * Turns the needle the short way round.
     *
     * <p>Without this a sounding aimed from north-west to north swings three hundred and fifteen
     * degrees the wrong way, which reads as the instrument being broken rather than being adjusted.
     */
    private void aimNeedle(ProbeBearing bearing) {
        float target = bearing.degrees();
        float current = needle.target();
        float delta = ((target - current) % 360.0F + 540.0F) % 360.0F - 180.0F;
        needle.set(current + delta);
    }

    @Override
    protected void init() {
        setWindowSize(AWLayouts.PROBE_WIDTH, AWLayouts.PROBE_HEIGHT);
        super.init();
        placeWindow(AWLayouts.PROBE_WIDTH, AWLayouts.PROBE_HEIGHT);
        clearWidgets();

        soundButton = Button.builder(AWLang.translate("gui.rift_probe.sound").component(), b -> {
            PacketDistributor.sendToServer(ServerboundProbePacket.sound(data.probePos()));
            playClick(1.0F);
        }).bounds(guiLeft + LAYOUT.sound().x(), guiTop + LAYOUT.sound().y(),
                LAYOUT.sound().width(), LAYOUT.sound().height()).build();

        courseButton = Button.builder(AWLang.translate("gui.rift_probe.set_course").component(), b -> {
            PacketDistributor.sendToServer(ServerboundProbePacket.setCourse(data.probePos()));
            playClick(1.2F);
        }).bounds(guiLeft + LAYOUT.course().x(), guiTop + LAYOUT.course().y(),
                LAYOUT.course().width(), LAYOUT.course().height()).build();

        addRenderableWidget(soundButton);
        addRenderableWidget(courseButton);
        updateButtons();

        if (data.hasReading() && reading == null) {
            requestReading();
        }
    }

    @Override
    public void removed() {
        releaseTexture();
        super.removed();
    }

    private void updateButtons() {
        if (soundButton == null || courseButton == null) {
            return;
        }
        boolean busy = data.state().busy();
        soundButton.active = !busy && data.affordable();
        soundButton.setMessage(busy
                ? AWLang.translate("gui.rift_probe.sounding").component()
                : AWLang.translate("gui.rift_probe.sound").component());
        courseButton.active = data.hasReading() && data.verdict().usable()
                && data.hasDrive() && !data.isCourse();
        courseButton.setMessage(data.isCourse()
                ? AWLang.translate("gui.rift_probe.course_set").component()
                : AWLang.translate("gui.rift_probe.set_course").component());
    }

    private void requestReading() {
        PacketDistributor.sendToServer(ServerboundProbePacket.fetchReading(data.probePos()));
    }

    private void playClick(float pitch) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.25F, pitch);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Into window coordinates, so the dial and slider are hit where they are drawn when the window
        // is scaled down to fit. An identity when it fits, which is the common case.
        mouseX = unscaleX(mouseX);
        mouseY = unscaleY(mouseY);
        Rect compass = LAYOUT.compass();
        if (!data.state().busy() && compass.contains(mouseX - guiLeft, mouseY - guiTop)) {
            ProbeBearing picked = bearingAt(mouseX, mouseY);
            if (picked != null && picked != data.bearing()) {
                PacketDistributor.sendToServer(ServerboundProbePacket.bearing(data.probePos(), picked));
                aimNeedle(picked);
                playClick(1.1F);
            }
            return true;
        }
        if (!data.state().busy() && sliderTrack().contains(mouseX - guiLeft, mouseY - guiTop)) {
            draggingRange = true;
            dragRange(mouseX);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        mouseX = unscaleX(mouseX);
        mouseY = unscaleY(mouseY);
        if (draggingRange) {
            dragRange(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        mouseX = unscaleX(mouseX);
        mouseY = unscaleY(mouseY);
        if (draggingRange) {
            draggingRange = false;
            // Sent on release rather than on every pixel of the drag: the server clamps and echoes
            // back, and a packet per mouse-move would be a packet per frame.
            PacketDistributor.sendToServer(ServerboundProbePacket.range(data.probePos(), pendingRange));
            playClick(0.9F);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** What the slider is showing while it is being dragged, before the server has agreed. */
    private int pendingRange;

    private void dragRange(double mouseX) {
        Rect track = sliderTrack();
        double fraction = (mouseX - guiLeft - track.x()) / Math.max(1, track.width());
        fraction = Math.max(0.0D, Math.min(1.0D, fraction));
        int span = data.maximumRange() - data.minimumRange();
        // Snapped to a round number, because nobody wants a sounding at 3,847 blocks.
        int step = Math.max(1, span / 96);
        pendingRange = data.minimumRange() + (int) Math.round(fraction * span / step) * step;
        pendingRange = Math.min(data.maximumRange(), Math.max(data.minimumRange(), pendingRange));
    }

    private int shownRange() {
        return draggingRange ? pendingRange : data.range();
    }

    private Rect sliderTrack() {
        Rect panel = LAYOUT.range();
        return new Rect(panel.x() + 4, panel.y() + panel.height() - 16, panel.width() - 8, 10);
    }

    @Nullable
    private ProbeBearing bearingAt(double mouseX, double mouseY) {
        Rect compass = LAYOUT.compass();
        double dx = mouseX - guiLeft - compass.centreX();
        double dy = mouseY - guiTop - compass.centreY();
        if (dx * dx + dy * dy < 36.0D) {
            return null; // the hub is not a direction
        }
        double degrees = Math.toDegrees(Math.atan2(dx, -dy));
        if (degrees < 0.0D) {
            degrees += 360.0D;
        }
        return ProbeBearing.byIndex((int) Math.round(degrees / 45.0D));
    }

    @Override
    public void tick() {
        super.tick();
        ticksOpen++;
        needle.tick();
        fill.tick();
        reach.tick();
        if (--refreshTimer <= 0) {
            refreshTimer = REFRESH_INTERVAL;
            PacketDistributor.sendToServer(ServerboundProbePacket.open(data.probePos()));
        }
    }

    // ----------------------------------------------------------------- render

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        AWScreenStyle.window(graphics, guiLeft, guiTop, AWLayouts.PROBE_WIDTH, AWLayouts.PROBE_HEIGHT);

        ProbeState state = data.state();
        int statusColour = switch (state) {
            case FAILED -> AWScreenStyle.BAD;
            case REACHING -> AWScreenStyle.ACCENT;
            case COMPLETE -> AWScreenStyle.OK;
            default -> AWScreenStyle.LABEL;
        };
        AWScreenStyle.header(graphics, font, guiLeft, guiTop, LAYOUT.header(),
                AWLang.translate("gui.rift_probe.title").component(),
                AWLang.translate(data.aboard() ? "gui.rift_probe.aboard" : "gui.rift_probe.grounded").string(),
                "",
                AWLang.translate(state.translationKey()).component(),
                statusColour,
                data.aboard() ? AWScreenStyle.LABEL : AWScreenStyle.WARN);

        renderCompass(graphics, partialTicks);
        renderRange(graphics);
        renderSupply(graphics, partialTicks);
        renderReading(graphics, partialTicks);
        renderVerdict(graphics);
    }

    /**
     * The bearing dial.
     *
     * <p>Eight points around a ring, the chosen one lit, and a needle that turns to it. While a
     * sounding is out, a sweep runs round the ring - which is the only honest thing to show, because
     * what the probe is actually doing is waiting for ground to exist and there is no progress to
     * report beyond "still going".
     */
    private void renderCompass(GuiGraphics graphics, float partialTicks) {
        Rect panel = LAYOUT.compass();
        AWScreenStyle.panel(graphics, guiLeft, guiTop, panel);

        int cx = guiLeft + panel.centreX();
        int cy = guiTop + panel.centreY() + 4;
        int radius = Math.min(panel.width(), panel.height() - 12) / 2 - 12;

        graphics.drawString(font, AWLang.translate("gui.rift_probe.bearing").component(),
                guiLeft + panel.x() + 2, guiTop + panel.y() + 2, AWScreenStyle.LABEL, false);

        float sweepPhase = data.state().busy() ? AWAnim.sweep(ticksOpen + partialTicks, 40.0F) : -1.0F;

        for (ProbeBearing point : ProbeBearing.values()) {
            double radians = Math.toRadians(point.degrees());
            int tx = cx + (int) Math.round(Math.sin(radians) * radius);
            int ty = cy - (int) Math.round(Math.cos(radians) * radius);
            boolean chosen = point == data.bearing();

            int colour = chosen ? AWScreenStyle.ACCENT : AWScreenStyle.LABEL;
            if (sweepPhase >= 0.0F) {
                // The sweep brightens each point as it passes, so the ring reads as being scanned.
                float distance = Math.abs(AWAnim.sweep(ticksOpen + partialTicks, 40.0F)
                        - point.ordinal() / (float) ProbeBearing.values().length);
                float near = Math.max(0.0F, 1.0F - Math.min(distance, 1.0F - distance) * 6.0F);
                colour = AWAnim.blend(colour, AWScreenStyle.ACCENT, near);
            }

            String label = point.abbreviation();
            graphics.drawString(font, label, tx - font.width(label) / 2, ty - 4, colour, false);
        }

        // The needle, drawn as a run of shrinking dots so it stays legible at any GUI scale.
        double angle = Math.toRadians(needle.get(partialTicks));
        for (int step = 3; step <= radius - 8; step += 3) {
            int nx = cx + (int) Math.round(Math.sin(angle) * step);
            int ny = cy - (int) Math.round(Math.cos(angle) * step);
            float along = step / (float) Math.max(1, radius - 8);
            int colour = AWAnim.blend(AWScreenStyle.ACCENT_DIM, AWScreenStyle.ACCENT, along);
            graphics.fill(nx - 1, ny - 1, nx + 1, ny + 1, colour);
        }
        graphics.fill(cx - 2, cy - 2, cx + 2, cy + 2, AWScreenStyle.TITLE);
    }

    private void renderRange(GuiGraphics graphics) {
        Rect panel = LAYOUT.range();
        AWScreenStyle.panel(graphics, guiLeft, guiTop, panel);

        int left = guiLeft + panel.x() + 2;
        int top = guiTop + panel.y() + 2;
        graphics.drawString(font, AWLang.translate("gui.rift_probe.range").component(),
                left, top, AWScreenStyle.LABEL, false);

        String value = AWLang.distance(shownRange());
        graphics.drawString(font, value, guiLeft + panel.right() - font.width(value) - 2, top,
                AWScreenStyle.VALUE, false);

        Rect track = sliderTrack();
        int span = Math.max(1, data.maximumRange() - data.minimumRange());
        float fraction = (shownRange() - data.minimumRange()) / (float) span;
        AWScreenStyle.bar(graphics, guiLeft + track.x(), guiTop + track.y() + 3,
                track.width(), 4, fraction, AWScreenStyle.ACCENT_DIM);

        int knob = guiLeft + track.x() + Math.round(track.width() * AWAnim.clamp(fraction));
        graphics.fill(knob - 2, guiTop + track.y(), knob + 2, guiTop + track.y() + 10,
                AWScreenStyle.ACCENT);
    }

    private void renderSupply(GuiGraphics graphics, float partialTicks) {
        Rect panel = LAYOUT.supply();
        AWScreenStyle.panel(graphics, guiLeft, guiTop, panel);

        int left = guiLeft + panel.x() + 2;
        int top = guiTop + panel.y() + 2;

        boolean enough = data.affordable();
        AWScreenStyle.readout(graphics, font, left, top, panel.width() - 4,
                AWLang.translate("gui.rift_probe.cost").component(),
                AWLang.essence(data.cost()), enough ? AWScreenStyle.VALUE : AWScreenStyle.BAD);

        AWScreenStyle.bar(graphics, left, top + AWLayout.LINE + 3, panel.width() - 4, 5,
                fill.get(partialTicks), enough ? AWScreenStyle.OK : AWScreenStyle.WARN);

        String held = AWLang.essence(data.essence(), data.capacity());
        graphics.drawString(font, held, left, top + AWLayout.LINE + 11, AWScreenStyle.LABEL, false);
    }

    /**
     * The ground a sounding found.
     *
     * <p>While one is out, the panel shows how long it has been waiting rather than a picture,
     * because there is nothing yet to draw and an empty frame reads as a failed sounding.
     */
    private void renderReading(GuiGraphics graphics, float partialTicks) {
        Rect panel = LAYOUT.reading();
        AWScreenStyle.panel(graphics, guiLeft, guiTop, panel);

        int left = guiLeft + panel.x();
        int top = guiTop + panel.y();

        if (data.state().busy()) {
            String message = AWLang.translate("gui.rift_probe.reaching").string();
            graphics.drawString(font, message, left + (panel.width() - font.width(message)) / 2,
                    top + panel.height() / 2 - 10, AWScreenStyle.ACCENT, false);
            AWScreenStyle.workingBar(graphics, left + 16, top + panel.height() / 2 + 4,
                    panel.width() - 32, 5, reach.get(partialTicks), AWScreenStyle.ACCENT_DIM,
                    ticksOpen + partialTicks);
            return;
        }

        ProbeSounding sounding = reading;
        if (sounding == null || texture == null) {
            String message = AWLang.translate(data.hasReading()
                    ? "gui.rift_probe.receiving" : "gui.rift_probe.no_reading").string();
            graphics.drawString(font, message, left + (panel.width() - font.width(message)) / 2,
                    top + panel.height() / 2 - 4, AWScreenStyle.LABEL, false);
            return;
        }

        texture.draw(graphics, left + AWLayout.INSET, top + AWLayout.INSET);

        // The fix, pulsing at the middle of its own survey.
        int centreX = left + AWLayout.INSET + AWLayouts.PREVIEW / 2;
        int centreY = top + AWLayout.INSET + AWLayouts.PREVIEW / 2;
        int reachOut = Math.round(AWAnim.pulse(ticksOpen + partialTicks, 34.0F) * 3.0F) + 3;
        graphics.fill(centreX - reachOut, centreY, centreX + reachOut + 1, centreY + 1, 0xFF_FF_FF_FF);
        graphics.fill(centreX, centreY - reachOut, centreX + 1, centreY + reachOut + 1, 0xFF_FF_FF_FF);

        graphics.drawString(font, "N", centreX - 2, top + AWLayout.INSET + 2, 0xFF_FF_FF_FF, true);

        String scale = AWLang.translate("gui.astrolabe.scale", sounding.survey().radius() * 2).string();
        graphics.drawString(font, scale, left, top + panel.height() + 2, AWScreenStyle.LABEL, false);
    }

    private void renderVerdict(GuiGraphics graphics) {
        Rect panel = LAYOUT.verdict();
        AWScreenStyle.panel(graphics, guiLeft, guiTop, panel);

        int left = guiLeft + panel.x() + 2;
        int top = guiTop + panel.y() + 2;
        int width = panel.width() - 4;

        if (!data.hasReading()) {
            // Wrapped rather than cut: this is the one instruction telling a new player what the two
            // controls above are for, and "Pick a direction and dist..." at 133px teaches nothing.
            int line = top;
            for (net.minecraft.util.FormattedCharSequence piece : font.split(
                    AWLang.translate("gui.rift_probe.nothing_yet").component(), width)) {
                graphics.drawString(font, piece, left, line, AWScreenStyle.LABEL, false);
                line += 10;
            }
            return;
        }

        ProbeVerdict verdict = data.verdict();
        int colour = switch (verdict) {
            case CLEAR -> AWScreenStyle.OK;
            case PARTIAL -> AWScreenStyle.WARN;
            case NO_GROUND -> AWScreenStyle.BAD;
        };
        AWScreenStyle.pill(graphics, font, left, top,
                AWLang.translate(verdict.translationKey()).string(), colour);

        String label = data.readingLabel();
        graphics.drawString(font, AWScreenStyle.trim(font, label, width - 4),
                guiLeft + panel.right() - font.width(AWScreenStyle.trim(font, label, width - 4)) - 2,
                top + 2, AWScreenStyle.TITLE, false);

        int line = top + 16;
        line = AWScreenStyle.readout(graphics, font, left, line, width,
                AWLang.translate("gui.astrolabe.ground").component(),
                verdict.usable() ? "y " + data.groundY() : "-", AWScreenStyle.VALUE);
        line = AWScreenStyle.readout(graphics, font, left, line, width,
                AWLang.translate("gui.rift_probe.coverage").component(),
                Math.round(data.coverage() * 100.0F) + "%",
                data.coverage() >= ProbeSounding.THIN_COVERAGE ? AWScreenStyle.VALUE : AWScreenStyle.WARN);
        // What this reading is *for*, rather than another number about it. Setting a course moves
        // nothing, so this line is the only thing that can tell a player it worked.
        String state;
        int stateColour;
        if (data.isCourse()) {
            state = AWLang.translate("gui.rift_probe.is_course").string();
            stateColour = AWScreenStyle.OK;
        } else if (!data.hasDrive()) {
            state = AWLang.translate("gui.rift_probe.drive_missing").string();
            stateColour = AWScreenStyle.BAD;
        } else if (verdict.usable()) {
            state = AWLang.translate("gui.rift_probe.can_send").string();
            stateColour = AWScreenStyle.LABEL;
        } else {
            state = AWLang.translate("gui.rift_probe.cannot_send").string();
            stateColour = AWScreenStyle.BAD;
        }
        AWScreenStyle.pill(graphics, font, left, line + 2, state, stateColour);
    }

    /** The survey grid is fixed by {@link DestinationSurvey}; the panel is sized to match it. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
