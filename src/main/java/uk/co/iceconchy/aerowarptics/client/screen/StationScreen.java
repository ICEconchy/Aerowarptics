package uk.co.iceconchy.aerowarptics.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.client.screen.AWLayout.Rect;
import uk.co.iceconchy.aerowarptics.flotilla.CouplerRole;
import uk.co.iceconchy.aerowarptics.flotilla.FleetMuster;
import uk.co.iceconchy.aerowarptics.flotilla.StationStatus;
import uk.co.iceconchy.aerowarptics.network.ClientboundCouplerPanelPacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundCouplerPanelPacket.Nearby;
import uk.co.iceconchy.aerowarptics.network.ClientboundCouplerPanelPacket.Row;
import uk.co.iceconchy.aerowarptics.network.ServerboundCouplerPacket.Action;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.UUID;

/**
 * An escort's Station screen.
 *
 * <p>Before the ship has joined anyone: the flagships near enough to join, by name, with how many escorts
 * each already leads, and Take Station. After: the station box - the slot in the middle, the tolerance
 * round it, and this ship as a dot, bow up - which is a steering aid as much as a readout, since holding
 * station is the escort pilot's whole job; beside it the drift, the charge and the harmony wave.
 */
@OnlyIn(Dist.CLIENT)
public class StationScreen extends CouplerScreen {

    private static final AWLayouts.Station LAYOUT = AWLayouts.station();

    private Button takeButton;
    private Button breakButton;
    private Button roleButton;
    private Button renameButton;

    @Nullable
    private UUID chosen;
    private int scroll;

    private final AWAnim.Eased driftRight = new AWAnim.Eased(0.3F);
    private final AWAnim.Eased driftAhead = new AWAnim.Eased(0.3F);

    public StationScreen(ClientboundCouplerPanelPacket data) {
        super(data);
        Row own = own();
        if (own != null) {
            driftRight.snap(own.dRight());
            driftAhead.snap(own.dAhead());
        }
    }

    @Override
    public boolean shows(CouplerRole role) {
        return role == CouplerRole.ESCORT;
    }

    private boolean joined() {
        return !data.flagship().equals(ClientboundCouplerPanelPacket.NIL);
    }

    private boolean pending() {
        return !data.pending().equals(ClientboundCouplerPanelPacket.NIL);
    }

    @Nullable
    private Row own() {
        return data.rows().isEmpty() ? null : data.rows().get(0);
    }

    @Override
    protected void init() {
        setWindowSize(AWLayouts.COUPLER_WIDTH, AWLayouts.COUPLER_HEIGHT);
        super.init();
        placeWindow(AWLayouts.COUPLER_WIDTH, AWLayouts.COUPLER_HEIGHT);
        clearWidgets();
        renameBox = null;
        takeButton = button(LAYOUT.take(), AWLang.translate("gui.rift_coupler.take_station").component(), b -> {
            if (chosen != null) {
                send(Action.TAKE_STATION, chosen, false);
                click(1.2F);
            }
        });
        breakButton = button(LAYOUT.breakOff(), AWLang.translate("gui.rift_coupler.break_off").component(), b -> {
            send(Action.BREAK_OFF);
            click(0.8F);
        });
        roleButton = button(LAYOUT.role(), AWLang.translate("gui.rift_coupler.be_flagship").component(), b -> {
            send(Action.SET_ROLE, ClientboundCouplerPanelPacket.NIL, true);
            click(1.0F);
        });
        roleButton.setTooltip(Tooltip.create(AWLang.translate("gui.rift_coupler.be_flagship_hint").component()));
        renameButton = button(LAYOUT.rename(), AWLang.translate("gui.rift_coupler.rename").component(),
                b -> startRename(LAYOUT.info()));
        refresh();
    }

    private Button button(Rect rect, Component label, Button.OnPress press) {
        Button button = Button.builder(label, press)
                .bounds(guiLeft + rect.x(), guiTop + rect.y(), rect.width(), rect.height()).build();
        addRenderableWidget(button);
        return button;
    }

    @Override
    protected void refresh() {
        if (takeButton == null) {
            return;
        }
        boolean folding = data.phase() >= FleetMuster.Phase.COMMITTED.ordinal();
        takeButton.active = data.maySteer() && !joined() && !pending() && chosen != null && data.linked()
                && data.aboard();
        breakButton.active = data.maySteer() && (joined() || pending()) && !folding;
        breakButton.setMessage(pending() && !joined()
                ? AWLang.translate("gui.rift_coupler.withdraw").component()
                : AWLang.translate("gui.rift_coupler.break_off").component());
        roleButton.active = data.maySteer() && !folding;
        renameButton.active = data.maySteer() && data.aboard();
        Row own = own();
        if (own != null) {
            driftRight.set(own.dRight());
            driftAhead.set(own.dAhead());
        }
    }

    @Override
    public void tick() {
        super.tick();
        driftRight.tick();
        driftAhead.tick();
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean windowClicked(double x, double y, int button) {
        if (button == 0 && !joined()) {
            Rect list = LAYOUT.list();
            if (list.contains(x - guiLeft, y - guiTop)) {
                int index = (int) ((y - guiTop - list.y() - AWLayout.INSET - AWLayout.ROW) / AWLayout.ROW) + scroll;
                if (index >= 0 && index < data.nearby().size()) {
                    Nearby entry = data.nearby().get(index);
                    chosen = entry.full() ? null : entry.coupler();
                    click(1.0F);
                    refresh();
                    return true;
                }
            }
        }
        return super.windowClicked(x, y, button);
    }

    @Override
    protected boolean windowScrolled(double x, double y, double amount) {
        if (!joined()) {
            int rows = AWLayout.visibleRows(LAYOUT.list()) - 1;
            scroll = Math.max(0, Math.min(Math.max(0, data.nearby().size() - rows), scroll - (int) Math.signum(amount)));
            return true;
        }
        return false;
    }

    // ----------------------------------------------------------------- render

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        AWScreenStyle.window(graphics, guiLeft, guiTop, AWLayouts.COUPLER_WIDTH, AWLayouts.COUPLER_HEIGHT);
        float ticks = ticksOpen + partialTicks;
        Row own = own();
        String subject = joined() && data.slot() > 0
                ? AWLang.translate("gui.rift_coupler.escort_subject", data.slot()).string()
                : AWLang.translate("gui.rift_coupler.escort_unattached").string();
        String subtitle = joined()
                ? AWLang.translate("gui.rift_coupler.following", shipName(data.destination())).string()
                : pending() ? AWLang.translate("gui.rift_coupler.asked", shipName(data.destination())).string()
                : AWLang.translate("gui.rift_coupler.unattached", shipName(data.shipName())).string();
        Component status = null;
        int statusColour = AWScreenStyle.LABEL;
        if (joined()) {
            status = own == null || data.phase() < FleetMuster.Phase.MUSTER.ordinal()
                    ? AWLang.translate("gui.rift_coupler.tether_slack").component()
                    : own.harmony() >= 1.0F ? AWLang.translate("gui.rift_coupler.tether_steady").component()
                    : AWLang.translate("gui.rift_coupler.tether_ragged").component();
            statusColour = own != null && own.harmony() >= 1.0F ? AWScreenStyle.OK : AWScreenStyle.WARN;
        }
        AWScreenStyle.header(graphics, font, guiLeft, guiTop, LAYOUT.header(),
                AWLang.translate("gui.rift_coupler.title").component(), subtitle, subject, status, statusColour,
                AWScreenStyle.VALUE);
        renderNotice(graphics, LAYOUT.info());

        if (!data.linked()) {
            Rect list = LAYOUT.list();
            AWScreenStyle.panel(graphics, guiLeft, guiTop, list);
            graphics.drawString(font, AWLang.translate("gui.rift_coupler.not_linked").string(),
                    guiLeft + list.x() + 2, guiTop + list.y() + 2, AWScreenStyle.BAD, false);
            return;
        }
        if (joined()) {
            renderBox(graphics, own);
            renderDetail(graphics, own, ticks);
        } else {
            renderList(graphics);
        }
    }

    /**
     * The station box: the slot at the centre, the tolerance as a square round it, this ship as a dot and
     * a line back to the slot. Scaled so the tolerance fills half the box, and further out only when the
     * ship is too far off to fit - so the box reads the same way every time a pilot is close.
     */
    private void renderBox(GuiGraphics graphics, @Nullable Row own) {
        Rect box = LAYOUT.box();
        AWScreenStyle.panel(graphics, guiLeft, guiTop, box);
        int cx = guiLeft + box.centreX();
        int cy = guiTop + box.centreY();
        int half = box.width() / 2 - 6;
        float dRight = driftRight.get(0.0F);
        float dAhead = driftAhead.get(0.0F);
        double span = Math.max(data.tolerance() * 2.0D, Math.max(Math.abs(dRight), Math.abs(dAhead)) * 1.15D);
        double scale = half / Math.max(1.0D, span);
        int tol = (int) Math.round(data.tolerance() * scale);
        boolean inStation = own != null && own.contact() && own.drift() <= data.tolerance();
        int ring = inStation ? AWScreenStyle.OK : AWScreenStyle.WARN;
        graphics.fill(cx - tol, cy - tol, cx + tol + 1, cy - tol + 1, ring);
        graphics.fill(cx - tol, cy + tol, cx + tol + 1, cy + tol + 1, ring);
        graphics.fill(cx - tol, cy - tol, cx - tol + 1, cy + tol + 1, ring);
        graphics.fill(cx + tol, cy - tol, cx + tol + 1, cy + tol + 1, ring);
        graphics.fill(cx - 2, cy, cx + 3, cy + 1, AWScreenStyle.LABEL);
        graphics.fill(cx, cy - 2, cx + 1, cy + 3, AWScreenStyle.LABEL);
        // Bow up: a small tick at the top of the box, so the pilot knows which way "ahead" is.
        graphics.fill(cx, guiTop + box.y() + 1, cx + 1, guiTop + box.y() + 5, AWScreenStyle.TITLE);
        if (own == null || !own.contact()) {
            String lost = AWLang.translate("flotilla.status.out_of_contact").string();
            graphics.drawCenteredString(font, AWScreenStyle.trim(font, lost, box.width() - 4), cx, cy + tol + 6,
                    AWScreenStyle.BAD);
            return;
        }
        int sx = cx + (int) Math.round(dRight * scale);
        int sy = cy - (int) Math.round(dAhead * scale);
        int steps = Math.max(Math.abs(sx - cx), Math.abs(sy - cy));
        for (int i = 0; i < steps; i += 2) {
            float t = i / (float) steps;
            graphics.fill(Math.round(sx + (cx - sx) * t), Math.round(sy + (cy - sy) * t),
                    Math.round(sx + (cx - sx) * t) + 1, Math.round(sy + (cy - sy) * t) + 1, AWAnim.fade(ring, 0.6F));
        }
        int tether = 0xFF_00_00_00 | own.tether();
        graphics.fill(sx - 2, sy - 2, sx + 3, sy + 3, tether);
        graphics.fill(sx - 1, sy - 1, sx + 2, sy + 2, AWScreenStyle.VALUE);
    }

    private void renderDetail(GuiGraphics graphics, @Nullable Row own, float ticks) {
        Rect detail = LAYOUT.detail();
        AWScreenStyle.panel(graphics, guiLeft, guiTop, detail);
        int left = guiLeft + detail.x() + AWLayouts.PANEL_TEXT_PAD;
        int width = detail.width() - AWLayouts.PANEL_TEXT_PAD * 2;
        int line = guiTop + detail.y() + AWLayouts.PANEL_TEXT_PAD;
        StationStatus status = data.stationStatus();
        boolean contact = own != null && own.contact();
        float drift = own == null ? 0.0F : own.drift();
        boolean inStation = contact && drift <= data.tolerance();

        line = AWScreenStyle.readout(graphics, font, left, line, width,
                AWLang.translate("gui.rift_coupler.drift").component(),
                contact ? AWLang.distance(drift) : "-", inStation ? AWScreenStyle.OK : AWScreenStyle.BAD);
        // Drift as a bar that empties as the ship closes on its slot: full is the edge of the tolerance.
        AWScreenStyle.bar(graphics, left, line - 2, width, 3,
                contact ? 1.0F - Math.min(1.0F, drift / Math.max(0.1F, data.tolerance())) : 0.0F,
                inStation ? AWScreenStyle.OK : AWScreenStyle.BAD);
        line += 4;
        line = AWScreenStyle.readout(graphics, font, left, line, width,
                AWLang.translate("gui.rift_coupler.tolerance").component(),
                AWLang.distance(data.tolerance()), AWScreenStyle.VALUE);

        // The two gauges start clear of the longer of their two names: at a fixed fifty the charge bar was
        // drawn across the end of "Drive Charge" (seen in game, 2026-10-11).
        int gauge = Math.max(50, Math.max(font.width(AWLang.translate("gui.rift_navigation.charge").component()),
                font.width(AWLang.translate("gui.rift_coupler.harmony").component())) + 6);
        graphics.drawString(font, AWLang.translate("gui.rift_navigation.charge").component(), left, line,
                AWScreenStyle.LABEL, false);
        AWScreenStyle.marker(graphics, left + width - 7, line + 1, data.charge() >= 1.0F);
        AWScreenStyle.bar(graphics, left + gauge, line + 2, width - gauge - 12, 4, data.charge(),
                data.charge() >= 1.0F ? AWScreenStyle.OK : AWScreenStyle.WARN);
        line += AWLayout.LINE;

        float harmony = own == null ? 0.0F : own.harmony();
        graphics.drawString(font, AWLang.translate("gui.rift_coupler.harmony").component(), left, line,
                AWScreenStyle.LABEL, false);
        AWScreenStyle.marker(graphics, left + width - 7, line + 1, harmony >= 1.0F);
        if (data.phase() >= FleetMuster.Phase.MUSTER.ordinal()) {
            wave(graphics, left + gauge, line, width - gauge - 12, harmony,
                    harmony >= 1.0F ? AWScreenStyle.OK : AWScreenStyle.WARN,
                    ticks);
        } else {
            graphics.drawString(font, AWLang.translate("gui.rift_coupler.awaiting_stations").string(), left + gauge, line,
                    AWScreenStyle.LABEL, false);
        }
        line += AWLayout.LINE + 2;

        AWScreenStyle.rule(graphics, left, line, width);
        graphics.drawString(font, AWScreenStyle.trim(font, AWLang.translate(status.translationKey()).string(), width),
                left, line + 5, statusColour(status), false);
    }

    private void renderList(GuiGraphics graphics) {
        Rect list = LAYOUT.list();
        AWScreenStyle.panel(graphics, guiLeft, guiTop, list);
        int left = guiLeft + list.x() + AWLayouts.PANEL_TEXT_PAD;
        int right = guiLeft + list.right() - AWLayouts.PANEL_TEXT_PAD;
        int top = guiTop + list.y() + AWLayout.INSET;
        graphics.drawString(font, AWLang.translate("gui.rift_coupler.nearby").component(), left, top + 2,
                AWScreenStyle.TITLE, false);
        if (data.nearby().isEmpty()) {
            graphics.drawString(font, AWLang.translate("gui.rift_coupler.none_nearby").string(), left,
                    top + 2 + AWLayout.ROW, AWScreenStyle.LABEL, false);
            return;
        }
        int rows = AWLayout.visibleRows(list) - 1;
        for (int i = 0; i < rows && i + scroll < data.nearby().size(); i++) {
            Nearby entry = data.nearby().get(i + scroll);
            int y = top + (i + 1) * AWLayout.ROW;
            boolean asked = entry.coupler().equals(data.pending());
            if (entry.coupler().equals(chosen) || asked) {
                AWScreenStyle.selectionBar(graphics, left - 2, y, AWLayout.ROW - 1, asked ? AWScreenStyle.WARN
                        : AWScreenStyle.TITLE);
            }
            int colour = entry.full() ? AWScreenStyle.LABEL : AWScreenStyle.VALUE;
            graphics.drawString(font, AWScreenStyle.trim(font, shipName(entry.name()), 140), left + 4, y + 2, colour, false);
            String count = entry.cap() < 0
                    ? AWLang.translate("gui.rift_coupler.escorts_unlimited", entry.escorts()).string()
                    : AWLang.translate("gui.rift_coupler.escorts_of", entry.escorts(), entry.cap()).string();
            String distance = AWLang.distance(entry.distance());
            graphics.drawString(font, distance, right - font.width(distance), y + 2, AWScreenStyle.LABEL, false);
            graphics.drawString(font, count, right - font.width(distance) - 10 - font.width(count), y + 2,
                    entry.full() ? AWScreenStyle.BAD : AWScreenStyle.LABEL, false);
        }
        AWScreenStyle.scrollbar(graphics, guiLeft + list.right(), guiTop + list.y(), list.height(),
                data.nearby().size(), rows, scroll);
        if (pending()) {
            graphics.drawString(font, AWScreenStyle.trim(font, AWLang.translate("gui.rift_coupler.waiting_on",
                    shipName(data.destination())).string(), list.width() - 4), left,
                    guiTop + list.bottom() - 11, AWScreenStyle.WARN, false);
        }
    }
}
