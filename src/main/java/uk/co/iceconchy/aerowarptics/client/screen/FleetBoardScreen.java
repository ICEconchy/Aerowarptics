package uk.co.iceconchy.aerowarptics.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.client.screen.AWLayout.Rect;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;
import uk.co.iceconchy.aerowarptics.flotilla.CouplerRole;
import uk.co.iceconchy.aerowarptics.flotilla.FlagshipStatus;
import uk.co.iceconchy.aerowarptics.flotilla.FleetMuster;
import uk.co.iceconchy.aerowarptics.flotilla.StationStatus;
import uk.co.iceconchy.aerowarptics.network.ClientboundCouplerPanelPacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundCouplerPanelPacket.Request;
import uk.co.iceconchy.aerowarptics.network.ClientboundCouplerPanelPacket.Row;
import uk.co.iceconchy.aerowarptics.network.ServerboundCouplerPacket;
import uk.co.iceconchy.aerowarptics.network.ServerboundCouplerPacket.Action;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The flagship's Fleet Board: the formation, the roster, and the three orders that make a fold.
 *
 * <p>Left, the formation from above with the bow at the top: the flagship in the middle, each escort's
 * slot as a hollow marker and the ship itself as a dot, joined by a line while it is adrift, with a small
 * arrow beside the dot when the ship is above or below its slot's height. The flagship's owner drags a
 * marker to move a slot, scrolls over one to raise or lower it, and right-clicks one - or drops it back on
 * its own ship - to slot it where that ship is now, height and all; the server decides whether the new slot
 * is clear of the others and snaps it, and says why on the info line when it is not. Right, one row per
 * escort - in station or adrift, its charge, and the harmony wave settling from ragged to steady - then the
 * open slots the flagship's drive has room for, then anyone waiting to join, with Accept and Refuse beside
 * them.
 *
 * <p>Height is the one thing the grid cannot show, being drawn from above, and it is the one a slot most
 * often wanders off on. Before any of that, a ship fifteen blocks under its slot sat with its dot in the
 * middle of the box while its row said adrift, and dragging the box back onto the dot - the obvious reset -
 * changed nothing, because a drag only ever moved a slot across the grid.
 *
 * <p>Fold stays greyed until every row is ready. Held for {@code forceFoldHoldTicks}, it folds anyway and
 * leaves whoever is not ready behind: a deliberate choice, so the button fills red as it is held and says
 * how many it will leave, and letting go before it fills changes nothing.
 */
@OnlyIn(Dist.CLIENT)
public class FleetBoardScreen extends CouplerScreen {

    private static final AWLayouts.FleetBoard LAYOUT = AWLayouts.fleetBoard();

    /** How long a Disband click waits for the second one that confirms it. */
    private static final int CONFIRM_TICKS = 60;

    /** Ticks a scrolled height waits for the wheel to stop before it is sent: one order, not one per notch. */
    private static final int RAISE_SETTLE_TICKS = 8;

    /** Pixels from its ship's dot at which a dragged marker is dropped on that ship. */
    private static final int DOT_SNAP = 4;

    /** Pixels a marker has to be dragged before letting go of it moves it, rather than only selecting it. */
    private static final int DRAG_THRESHOLD = 2;

    private Button soundButton;
    private Button castOffButton;
    private Button foldButton;
    private Button roleButton;
    private Button autoButton;
    private Button beaconButton;
    private Button riftButton;
    private Button renameButton;

    @Nullable
    private UUID selected;
    @Nullable
    private UUID dragging;
    private double dragX;
    private double dragY;
    private double dragFromX;
    private double dragFromY;
    /**
     * A slot's height being scrolled and not sent yet: whose, and the height it will have. Sent once the
     * wheel has been still a moment, and shown until the panel that answers it arrives.
     */
    @Nullable
    private UUID raising;
    private double raisedTo;
    private int raiseSettle;
    private boolean raiseSent;
    /** The formation's tooltip, drawn after everything else so that no panel drawn later covers it. */
    private List<Component> formationTip = List.of();
    /** The grid's reach, held still while a marker is dragged so the view does not slide under it. */
    private double reach = FormationGrid.MIN_REACH;
    private int scroll;

    private boolean holding;
    private int held;
    private int roleConfirm;

    public FleetBoardScreen(ClientboundCouplerPanelPacket data) {
        super(data);
        fitReach();
    }

    @Override
    public boolean shows(CouplerRole role) {
        return role == CouplerRole.FLAGSHIP;
    }

    @Override
    protected void init() {
        setWindowSize(AWLayouts.COUPLER_WIDTH, AWLayouts.COUPLER_HEIGHT);
        super.init();
        placeWindow(AWLayouts.COUPLER_WIDTH, AWLayouts.COUPLER_HEIGHT);
        clearWidgets();
        renameBox = null;

        // Sound Stations, and while a muster is on, Stand Down: sounded by mistake, or no longer wanted,
        // a muster has to be able to end without disbanding the flotilla or folding it.
        soundButton = button(LAYOUT.sound(), Component.empty(), b -> {
            boolean mustering = mustering();
            send(mustering ? Action.STAND_DOWN : Action.SOUND_STATIONS);
            click(mustering ? 0.5F : 0.7F);
        });
        castOffButton = button(LAYOUT.castOff(), AWLang.translate("gui.rift_coupler.cast_off").component(), b -> {
            if (selected != null) {
                send(Action.CAST_OFF, selected, false);
                selected = null;
                click(0.8F);
            }
        });
        foldButton = button(LAYOUT.fold(), AWLang.translate("gui.rift_coupler.fold").component(), b -> {
            // A ready fleet folds on a click. One that is not is held instead - see tick.
            if (data.foldable()) {
                send(Action.FOLD, ClientboundCouplerPanelPacket.NIL, false);
                click(1.4F);
            }
        });
        roleButton = button(LAYOUT.role(), AWLang.translate("gui.rift_coupler.be_escort").component(), b -> {
            if (!data.rows().isEmpty() && roleConfirm <= 0) {
                roleConfirm = CONFIRM_TICKS;
                refresh();
                return;
            }
            roleConfirm = 0;
            send(Action.SET_ROLE, ClientboundCouplerPanelPacket.NIL, false);
            click(0.9F);
        });
        roleButton.setTooltip(Tooltip.create(AWLang.translate("gui.rift_coupler.be_escort_hint").component()));
        autoButton = button(LAYOUT.autoAccept(), Component.empty(), b -> {
            send(Action.AUTO_ACCEPT, ClientboundCouplerPanelPacket.NIL, !data.autoAccept());
            click(1.0F);
        });
        autoButton.setTooltip(Tooltip.create(AWLang.translate("gui.rift_coupler.auto_accept_hint").component()));
        beaconButton = button(LAYOUT.beacon(), Component.empty(), b -> {
            send(Action.BEACON_CALLS_ALL, ClientboundCouplerPanelPacket.NIL, !data.beaconCallsAll());
            click(1.0F);
        });
        beaconButton.setTooltip(Tooltip.create(AWLang.translate("gui.rift_coupler.beacon_hint").component()));
        riftButton = button(LAYOUT.sharedRift(), Component.empty(), b -> {
            send(Action.SHARED_RIFT, ClientboundCouplerPanelPacket.NIL, !data.sharedRift());
            click(1.0F);
        });
        riftButton.setTooltip(Tooltip.create(AWLang.translate("gui.rift_coupler.rift_hint").component()));
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
        if (foldButton == null) {
            return;
        }
        boolean steer = data.maySteer() && !folding();
        FlagshipStatus status = data.flagshipStatus();
        boolean forceable = status.forceable() && !data.rows().isEmpty() && data.phase() == FleetMuster.Phase.MUSTER.ordinal();
        boolean mustering = mustering();
        soundButton.active = steer && (mustering || !data.rows().isEmpty());
        soundButton.setMessage(mustering ? AWLang.translate("gui.rift_coupler.stand_down").component()
                : AWLang.translate("gui.rift_coupler.sound_stations").component());
        soundButton.setTooltip(Tooltip.create(mustering
                ? AWLang.translate("gui.rift_coupler.stand_down_hint").component()
                : AWLang.translate("gui.rift_coupler.sound_stations_hint").component()));
        castOffButton.active = steer && selected != null && rowFor(selected) != null;
        foldButton.active = steer && (data.foldable() || forceable);
        // Held, the button says how many it will leave behind instead - see renderHold - and its own label
        // would be drawn underneath that one.
        foldButton.setMessage(holding ? Component.empty()
                : data.foldable() || !forceable
                ? AWLang.translate("gui.rift_coupler.fold").component()
                : AWLang.translate("gui.rift_coupler.hold_to_fold").style(ChatFormatting.RED).component());
        roleButton.active = steer;
        roleButton.setMessage(roleConfirm > 0
                ? AWLang.translate("gui.rift_coupler.disband").style(ChatFormatting.RED).component()
                : AWLang.translate("gui.rift_coupler.be_escort").component());
        autoButton.active = steer;
        autoButton.setMessage(data.autoAccept() ? AWLang.translate("gui.rift_coupler.auto_on").component()
                : AWLang.translate("gui.rift_coupler.auto_off").component());
        beaconButton.active = steer;
        beaconButton.setMessage(data.beaconCallsAll() ? AWLang.translate("gui.rift_coupler.beacon_fleet").component()
                : AWLang.translate("gui.rift_coupler.beacon_ship").component());
        riftButton.active = steer;
        riftButton.setMessage(data.sharedRift() ? AWLang.translate("gui.rift_coupler.rift_one").component()
                : AWLang.translate("gui.rift_coupler.rift_each").component());
        renameButton.active = data.maySteer() && data.aboard();
        if (dragging == null) {
            fitReach();
        }
    }

    private boolean folding() {
        return data.phase() >= FleetMuster.Phase.COMMITTED.ordinal();
    }

    /** Whether stations are sounded and the fleet is mustering, with no fold in hand yet. */
    private boolean mustering() {
        return data.phase() == FleetMuster.Phase.MUSTER.ordinal();
    }

    @Override
    public void accept(ClientboundCouplerPanelPacket packet) {
        if (raiseSent) {
            // The answer to the height sent: the panel now says what the slot's height is.
            raising = null;
            raiseSent = false;
        }
        super.accept(packet);
    }

    @Override
    public void removed() {
        // A height scrolled just before closing the board is still meant.
        flushRaise();
        super.removed();
    }

    @Nullable
    private Row rowFor(UUID coupler) {
        for (Row row : data.rows()) {
            if (row.coupler().equals(coupler)) {
                return row;
            }
        }
        return null;
    }

    /** The grid reaches the furthest ship - its slot or where it actually is - with its footprint. */
    private void fitReach() {
        double furthest = data.radius();
        for (Row row : data.rows()) {
            double slot = Math.max(Math.abs(row.right()), Math.abs(row.ahead())) + row.radius();
            double actual = Math.max(Math.abs(row.right() + row.dRight()), Math.abs(row.ahead() + row.dAhead()));
            furthest = Math.max(furthest, Math.max(slot, actual + row.radius()));
        }
        reach = furthest * 1.1D;
    }

    private FormationGrid grid() {
        Rect panel = LAYOUT.formation();
        return new FormationGrid(guiLeft + panel.x(), guiTop + panel.y(), panel.width(), panel.height(), reach);
    }

    /** Half the width of a slot's box on the grid: the room its ship has to keep station in. */
    private int markerHalf(FormationGrid grid) {
        return Math.max(3, (int) Math.round(data.tolerance() * grid.scale()));
    }

    /** The slot marker under a pixel, or {@code null}. */
    @Nullable
    private Row markerAt(FormationGrid grid, double x, double y) {
        List<Row> rows = data.rows();
        double[] xs = new double[rows.size()];
        double[] ys = new double[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            xs[i] = grid.x(rows.get(i).right());
            ys[i] = grid.y(rows.get(i).ahead());
        }
        int index = FormationGrid.pick(xs, ys, x, y, markerHalf(grid) + 1);
        return index < 0 ? null : rows.get(index);
    }

    /** Whether a marker let go of at this pixel has been dropped back on its own ship. */
    private boolean onOwnShip(FormationGrid grid, Row row, double x, double y) {
        return row.contact()
                && Math.abs(grid.x(row.right() + row.dRight()) - x) <= DOT_SNAP
                && Math.abs(grid.y(row.ahead() + row.dAhead()) - y) <= DOT_SNAP;
    }

    /** The height a slot is drawn with: the one being scrolled to, or the one the server says it has. */
    private double shownUp(Row row) {
        return row.coupler().equals(raising) ? raisedTo : row.up();
    }

    /** Sends a scrolled height, if there is one waiting. */
    private void flushRaise() {
        if (raising == null || raiseSent) {
            return;
        }
        Row row = rowFor(raising);
        if (row == null) {
            raising = null;
            return;
        }
        PacketDistributor.sendToServer(ServerboundCouplerPacket.moveSlot(data.couplerPos(), raising,
                (float) row.right(), (float) raisedTo, (float) row.ahead()));
        raiseSent = true;
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean windowClicked(double x, double y, int button) {
        if (button == 0 && foldButton != null && foldButton.active && !data.foldable() && foldButton.isMouseOver(x, y)) {
            hold(true);
            click(0.6F);
            return true;
        }
        if (button == 0 && clickFormation(x, y)) {
            return true;
        }
        if (button == 1 && slotHere(x, y)) {
            return true;
        }
        if (button == 0 && clickRoster(x, y)) {
            return true;
        }
        return super.windowClicked(x, y, button);
    }

    @Override
    protected boolean windowReleased(double x, double y, int button) {
        if (holding) {
            hold(false);
        }
        if (dragging != null) {
            FormationGrid grid = grid();
            UUID moved = dragging;
            dragging = null;
            Row row = rowFor(moved);
            if (row == null || Math.abs(x - dragFromX) + Math.abs(y - dragFromY) < DRAG_THRESHOLD) {
                // Clicked, not dragged: that only selects it.
                return true;
            }
            if (onOwnShip(grid, row, x, y)) {
                send(Action.SLOT_HERE, moved, false);
            } else {
                // The grid is the formation from above: a drag moves a slot across it and keeps its height.
                PacketDistributor.sendToServer(ServerboundCouplerPacket.moveSlot(data.couplerPos(), moved,
                        (float) grid.right(grid.clampX(x)), (float) row.up(), (float) grid.ahead(grid.clampY(y))));
            }
            click(1.1F);
            return true;
        }
        return super.windowReleased(x, y, button);
    }

    @Override
    protected boolean windowDragged(double x, double y, int button, double dx, double dy) {
        if (dragging != null) {
            dragX = x;
            dragY = y;
            return true;
        }
        return super.windowDragged(x, y, button, dx, dy);
    }

    @Override
    protected boolean windowScrolled(double x, double y, double amount) {
        Rect roster = LAYOUT.roster();
        if (roster.contains(x - guiLeft, y - guiTop)) {
            scroll = Math.max(0, Math.min(Math.max(0, lines().size() - LAYOUT.rosterRows()), scroll - (int) Math.signum(amount)));
            return true;
        }
        if (LAYOUT.formation().contains(x - guiLeft, y - guiTop)) {
            return raise(x, y, amount);
        }
        return false;
    }

    /**
     * Raises or lowers the slot under the pointer - or, with none under it, the selected one - a block a
     * notch, five with Shift held. Sent when the wheel stops rather than every notch: see {@link #flushRaise}.
     */
    private boolean raise(double x, double y, double amount) {
        if (!data.maySteer() || folding() || dragging != null || amount == 0.0D) {
            return false;
        }
        Row row = markerAt(grid(), x, y);
        if (row == null && selected != null) {
            row = rowFor(selected);
        }
        if (row == null) {
            return false;
        }
        if (raising != null && !raising.equals(row.coupler())) {
            flushRaise();
        }
        double from = raising != null && raising.equals(row.coupler()) ? raisedTo : row.up();
        double step = hasShiftDown() ? 5.0D : 1.0D;
        raising = row.coupler();
        raisedTo = Math.round(from) + Math.signum(amount) * step;
        raiseSettle = RAISE_SETTLE_TICKS;
        raiseSent = false;
        selected = row.coupler();
        refresh();
        return true;
    }

    /** Picks up a slot marker to drag, for the one player allowed to move it. */
    private boolean clickFormation(double x, double y) {
        if (!data.maySteer() || folding()) {
            return false;
        }
        Row row = markerAt(grid(), x, y);
        if (row == null) {
            return false;
        }
        flushRaise();
        dragging = row.coupler();
        selected = row.coupler();
        dragX = x;
        dragY = y;
        dragFromX = x;
        dragFromY = y;
        refresh();
        return true;
    }

    /** Right-click on a marker: slot that escort where its ship is now, height and all. */
    private boolean slotHere(double x, double y) {
        if (!data.maySteer() || folding() || dragging != null) {
            return false;
        }
        Row row = markerAt(grid(), x, y);
        if (row == null) {
            return false;
        }
        if (row.coupler().equals(raising)) {
            raising = null;
            raiseSent = false;
        }
        send(Action.SLOT_HERE, row.coupler(), false);
        selected = row.coupler();
        click(1.3F);
        refresh();
        return true;
    }

    /** Selects a row, or presses Accept or Refuse on a request. */
    private boolean clickRoster(double x, double y) {
        Rect roster = LAYOUT.roster();
        if (!roster.contains(x - guiLeft, y - guiTop)) {
            return false;
        }
        int index = (int) ((y - guiTop - roster.y() - AWLayout.INSET) / AWLayout.ROW) + scroll;
        List<Line> lines = lines();
        if (index < 0 || index >= lines.size()) {
            return false;
        }
        Line line = lines.get(index);
        if (line.row() != null) {
            selected = line.row().coupler();
            click(1.0F);
            refresh();
            return true;
        }
        if (line.request() != null && data.maySteer()) {
            int right = guiLeft + roster.right() - 2;
            int refuseWidth = AWScreenStyle.pillWidth(font, AWLang.translate("gui.rift_coupler.refuse").string());
            int acceptWidth = AWScreenStyle.pillWidth(font, AWLang.translate("gui.rift_coupler.accept").string());
            if (x >= right - refuseWidth) {
                send(Action.REFUSE, line.request().coupler(), false);
                click(0.8F);
            } else if (x >= right - refuseWidth - 3 - acceptWidth) {
                send(Action.ACCEPT, line.request().coupler(), false);
                click(1.2F);
            }
            return true;
        }
        return false;
    }

    /** Starts or ends holding Fold, and swaps the button's label for the count it will leave, or back. */
    private void hold(boolean on) {
        holding = on;
        held = 0;
        refresh();
    }

    @Override
    public void tick() {
        super.tick();
        if (roleConfirm > 0 && --roleConfirm == 0) {
            refresh();
        }
        if (raising != null && !raiseSent && --raiseSettle <= 0) {
            flushRaise();
        }
        if (holding) {
            if (foldButton == null || !foldButton.active || data.foldable()) {
                hold(false);
                return;
            }
            held++;
            if (held % 10 == 0 && minecraft != null && minecraft.player != null) {
                // A rising tick while it is held, so letting go before the end is a decision and not a slip.
                minecraft.player.playSound(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.4F, 0.6F + held / 100.0F);
            }
            if (held >= AWConfig.FORCE_FOLD_HOLD_TICKS.get()) {
                hold(false);
                send(Action.FOLD, ClientboundCouplerPanelPacket.NIL, true);
                if (minecraft != null && minecraft.player != null) {
                    minecraft.player.playSound(SoundEvents.ANVIL_LAND, 0.35F, 0.6F);
                }
            }
        }
    }

    // ----------------------------------------------------------------- render

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        AWScreenStyle.window(graphics, guiLeft, guiTop, AWLayouts.COUPLER_WIDTH, AWLayouts.COUPLER_HEIGHT);
        float ticks = ticksOpen + partialTicks;

        FlagshipStatus status = data.flagshipStatus();
        String tier = data.tier() < 0 ? "" : AWLang.translate(RiftDriveTier.byIndex(data.tier()).translationKey()).string();
        String subject = AWLang.translate("gui.rift_coupler.flagship_subject", tier).string();
        String course = data.destination().isBlank() ? AWLang.translate("gui.astrolabe.no_course").string()
                : data.distance() >= 0.0F
                ? AWLang.translate("gui.rift_coupler.course", shipName(data.shipName()), data.destination(),
                        AWLang.distance(data.distance())).string()
                : AWLang.translate("gui.rift_coupler.course_unmeasured", shipName(data.shipName()),
                        data.destination()).string();
        AWScreenStyle.header(graphics, font, guiLeft, guiTop, LAYOUT.header(),
                AWLang.translate("gui.rift_coupler.title").component(), course, subject,
                AWLang.translate(status.translationKey()).component(), flagshipColour(status), AWScreenStyle.VALUE);

        renderFormation(graphics, mouseX, mouseY, ticks);
        renderRoster(graphics, mouseX, mouseY, ticks);
        renderInfo(graphics);
        renderHold(graphics);
        if (!formationTip.isEmpty()) {
            graphics.renderComponentTooltip(font, formationTip, mouseX, mouseY);
        }
    }

    private void renderFormation(GuiGraphics graphics, int mouseX, int mouseY, float ticks) {
        Rect panel = LAYOUT.formation();
        AWScreenStyle.panel(graphics, guiLeft, guiTop, panel);
        FormationGrid grid = grid();
        int cx = (int) Math.round(grid.centreX());
        int cy = (int) Math.round(grid.centreY());
        int left = guiLeft + panel.x();
        int top = guiTop + panel.y();
        // Faint cross-hairs through the flagship: its keel line and its beam.
        graphics.fill(left + 2, cy, left + panel.width() - 2, cy + 1, AWScreenStyle.RULE);
        graphics.fill(cx, top + 2, cx + 1, top + panel.height() - 2, AWScreenStyle.RULE);

        int half = markerHalf(grid);
        boolean steer = data.maySteer() && !folding();
        Row hovered = dragging == null && grid.contains(mouseX, mouseY) ? markerAt(grid, mouseX, mouseY) : null;
        for (Row row : data.rows()) {
            boolean moving = row.coupler().equals(dragging);
            boolean dropping = moving && onOwnShip(grid, row, dragX, dragY);
            int colour = statusColour(row.statusValue());
            int ax = (int) Math.round(grid.x(row.right() + row.dRight()));
            int ay = (int) Math.round(grid.y(row.ahead() + row.dAhead()));
            // Held over its own ship, a dragged marker sits on it: let go there and it takes the ship's place.
            int sx = (int) Math.round(dropping ? ax : moving ? grid.clampX(dragX) : grid.x(row.right()));
            int sy = (int) Math.round(dropping ? ay : moving ? grid.clampY(dragY) : grid.y(row.ahead()));
            if (row.contact()) {
                if (!moving && row.drift() > data.tolerance()) {
                    line(graphics, sx, sy, ax, ay, AWAnim.fade(colour, 0.5F));
                }
                graphics.fill(ax - 1, ay - 1, ax + 2, ay + 2, 0xFF_00_00_00 | row.tether());
                // Above or below its slot, which the grid cannot show: a small arrow the way the ship has gone.
                if (row.dUp() > data.tolerance()) {
                    graphics.fill(ax - 1, ay - 3, ax + 2, ay - 2, colour);
                    graphics.fill(ax, ay - 4, ax + 1, ay - 3, colour);
                } else if (row.dUp() < -data.tolerance()) {
                    graphics.fill(ax - 1, ay + 2, ax + 2, ay + 3, colour);
                    graphics.fill(ax, ay + 3, ax + 1, ay + 4, colour);
                }
            }
            // The slot: a hollow square as wide as the tolerance, the room the ship has to keep station in.
            boolean picked = row.coupler().equals(selected) || row == hovered || dropping;
            outline(graphics, sx - half, sy - half, sx + half + 1, sy + half + 1,
                    picked ? AWScreenStyle.TITLE : colour);
            String number = Integer.toString(data.rows().indexOf(row) + 1);
            graphics.drawString(font, number, sx + half + 2, sy - 4, colour, false);
            // Its height, while it is being set or looked at: the one thing the grid has no room to draw.
            if (row.coupler().equals(raising) || row == hovered || (steer && row.coupler().equals(selected))) {
                String height = signed(shownUp(row));
                graphics.drawString(font, height, sx - font.width(height) / 2, sy + half + 2,
                        row.coupler().equals(raising) ? AWScreenStyle.TITLE : AWScreenStyle.LABEL, false);
            }
        }
        formationTip = hovered == null ? List.of() : slotTip(hovered, steer);
        // The flagship last, on top: a diamond with its bow marked.
        int accent = AWScreenStyle.ACCENT;
        graphics.fill(cx - 1, cy - 4, cx + 2, cy + 5, accent);
        graphics.fill(cx - 4, cy - 1, cx + 5, cy + 2, accent);
        graphics.fill(cx - 2, cy - 2, cx + 3, cy + 3, accent);
        graphics.fill(cx, cy - 7, cx + 1, cy - 4, AWScreenStyle.TITLE);

        String scaleText = AWLang.distance(grid.reach());
        graphics.drawString(font, scaleText, left + panel.width() - font.width(scaleText) - 2,
                top + panel.height() - 10, AWScreenStyle.LABEL, false);
        if (data.rows().isEmpty()) {
            String empty = AWLang.translate("gui.rift_coupler.no_escorts").string();
            graphics.drawString(font, AWScreenStyle.trim(font, empty, panel.width() - 4), left + 2, top + 2,
                    AWScreenStyle.LABEL, false);
        } else if (data.maySteer() && !folding()) {
            graphics.drawString(font, AWScreenStyle.trim(font,
                    AWLang.translate("gui.rift_coupler.drag_hint").string(), panel.width() - 4),
                    left + 2, top + 2, AWScreenStyle.LABEL, false);
        }
    }

    /** A height in metres with its sign: above the flagship's centre, or below it. */
    private static String signed(double up) {
        long rounded = Math.round(up);
        return (rounded > 0 ? "+" : rounded < 0 ? "-" : "") + AWLang.distance(Math.abs(rounded));
    }

    /** What hovering a slot marker says: whose it is, how high, where its ship is, and what can be done with it. */
    private List<Component> slotTip(Row row, boolean steer) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(shipName(row.name())));
        double up = shownUp(row);
        String metres = AWLang.distance(Math.abs(Math.round(up)));
        lines.add((Math.round(up) == 0 ? AWLang.translate("gui.rift_coupler.slot_level")
                : up > 0 ? AWLang.translate("gui.rift_coupler.slot_above", metres)
                : AWLang.translate("gui.rift_coupler.slot_below", metres)).style(ChatFormatting.GRAY).component());
        Component vertical = verticalDrift(row);
        if (vertical != null) {
            lines.add(vertical);
        }
        if (steer) {
            lines.add(AWLang.translate("gui.rift_coupler.slot_drag").style(ChatFormatting.DARK_GRAY).component());
            lines.add(AWLang.translate("gui.rift_coupler.slot_scroll").style(ChatFormatting.DARK_GRAY).component());
            lines.add(AWLang.translate("gui.rift_coupler.slot_here").style(ChatFormatting.DARK_GRAY).component());
        }
        return lines;
    }

    /** "The ship is twelve metres below its slot", when it is further off its height than it may be. */
    @Nullable
    private Component verticalDrift(Row row) {
        if (!row.contact() || Math.abs(row.dUp()) <= data.tolerance()) {
            return null;
        }
        String metres = AWLang.distance(Math.abs(Math.round(row.dUp())));
        return (row.dUp() > 0 ? AWLang.translate("gui.rift_coupler.ship_above", metres)
                : AWLang.translate("gui.rift_coupler.ship_below", metres)).style(ChatFormatting.RED).component();
    }

    /** One line of the roster: an escort, an open slot, a heading, or a request. */
    private record Line(@Nullable Row row, @Nullable Request request, boolean open, boolean heading) {
    }

    private List<Line> lines() {
        List<Line> lines = new ArrayList<>();
        for (Row row : data.rows()) {
            lines.add(new Line(row, null, false, false));
        }
        int open = data.cap() < 0 ? 1 : Math.max(0, data.cap() - data.rows().size());
        for (int i = 0; i < Math.min(open, 8); i++) {
            lines.add(new Line(null, null, true, false));
        }
        if (!data.requests().isEmpty()) {
            lines.add(new Line(null, null, false, true));
            for (Request request : data.requests()) {
                lines.add(new Line(null, request, false, false));
            }
        }
        return lines;
    }

    private void renderRoster(GuiGraphics graphics, int mouseX, int mouseY, float ticks) {
        Rect panel = LAYOUT.roster();
        AWScreenStyle.panel(graphics, guiLeft, guiTop, panel);
        int left = guiLeft + panel.x() + AWLayouts.PANEL_TEXT_PAD;
        int right = guiLeft + panel.right() - AWLayouts.PANEL_TEXT_PAD;
        int nameRoom = 72;
        int chargeX = left + 10 + nameRoom + 4;
        int chargeWidth = 40;
        int waveX = chargeX + chargeWidth + 6;
        List<Line> lines = lines();
        int visible = LAYOUT.rosterRows();
        List<Component> hover = List.of();
        for (int i = 0; i < visible && i + scroll < lines.size(); i++) {
            Line line = lines.get(i + scroll);
            int y = guiTop + panel.y() + AWLayout.INSET + i * AWLayout.ROW;
            if (line.row() != null) {
                Row row = line.row();
                StationStatus status = row.statusValue();
                int colour = statusColour(status);
                if (row.coupler().equals(selected)) {
                    AWScreenStyle.selectionBar(graphics, left - 2, y, AWLayout.ROW - 1, AWScreenStyle.TITLE);
                }
                boolean inStation = row.contact() && row.drift() <= data.tolerance();
                // Filled in station, hollow adrift - the Fleet Board's two dots.
                graphics.fill(left + 1, y + 3, left + 6, y + 8, AWAnim.fade(0xFF_00_00_00 | row.tether(), 1.0F));
                if (!inStation) {
                    graphics.fill(left + 2, y + 4, left + 5, y + 7, AWScreenStyle.TRACK);
                }
                graphics.drawString(font, AWScreenStyle.trim(font, shipName(row.name()), nameRoom), left + 10, y + 2,
                        AWScreenStyle.VALUE, false);
                if (!row.contact()) {
                    graphics.drawString(font, AWLang.translate("flotilla.status.out_of_contact").string(),
                            chargeX, y + 2, AWScreenStyle.BAD, false);
                } else if (!inStation) {
                    graphics.drawString(font, AWScreenStyle.trim(font, AWLang.translate("gui.rift_coupler.adrift",
                            AWLang.distance(row.drift())).string(), chargeWidth + 40), chargeX, y + 2,
                            AWScreenStyle.BAD, false);
                } else {
                    AWScreenStyle.bar(graphics, chargeX, y + 4, chargeWidth, 4, row.charge(),
                            row.charge() >= 1.0F ? AWScreenStyle.OK : AWScreenStyle.WARN);
                    if (data.phase() >= FleetMuster.Phase.MUSTER.ordinal()) {
                        wave(graphics, waveX, y + 1, 22, row.harmony(),
                                row.harmony() >= 1.0F ? AWScreenStyle.OK : AWScreenStyle.WARN, ticks);
                    } else {
                        graphics.fill(waveX, y + 5, waveX + 22, y + 6, AWScreenStyle.LABEL);
                    }
                }
                AWScreenStyle.marker(graphics, right - 7, y + 3, status.ready());
                if (mouseY >= y && mouseY < y + AWLayout.ROW && mouseX >= left && mouseX <= right) {
                    List<Component> tip = new ArrayList<>();
                    tip.add(AWLang.translate(status.translationKey()).style(ChatFormatting.WHITE).component());
                    Component vertical = verticalDrift(row);
                    if (vertical != null) {
                        tip.add(vertical);
                    }
                    hover = tip;
                }
            } else if (line.open()) {
                graphics.drawString(font, AWLang.translate("gui.rift_coupler.open_slot").string(), left + 10, y + 2,
                        AWScreenStyle.LABEL, false);
            } else if (line.heading()) {
                graphics.drawString(font, AWLang.translate("gui.rift_coupler.waiting").string(), left, y + 2,
                        AWScreenStyle.TITLE, false);
            } else if (line.request() != null) {
                Request request = line.request();
                graphics.drawString(font, AWScreenStyle.trim(font, shipName(request.name()), nameRoom), left + 10,
                        y + 2, AWScreenStyle.WARN, false);
                if (data.maySteer()) {
                    String refuse = AWLang.translate("gui.rift_coupler.refuse").string();
                    String accept = AWLang.translate("gui.rift_coupler.accept").string();
                    int refuseX = right - AWScreenStyle.pillWidth(font, refuse);
                    int acceptX = refuseX - 3 - AWScreenStyle.pillWidth(font, accept);
                    AWScreenStyle.pill(graphics, font, acceptX, y, accept, AWScreenStyle.OK);
                    AWScreenStyle.pill(graphics, font, refuseX, y, refuse, AWScreenStyle.BAD);
                }
            }
        }
        AWScreenStyle.scrollbar(graphics, guiLeft + panel.right(), guiTop + panel.y(), panel.height(),
                lines.size(), visible, scroll);
        if (!hover.isEmpty()) {
            graphics.renderComponentTooltip(font, hover, mouseX, mouseY);
        }
    }

    /** Wake clearance and essence, on the one line under the panels. */
    private void renderInfo(GuiGraphics graphics) {
        if (renameBox != null || renderNotice(graphics, LAYOUT.info())) {
            return;
        }
        Rect info = LAYOUT.info();
        int left = guiLeft + info.x();
        int y = guiTop + info.y() + 1;
        FlagshipStatus status = data.flagshipStatus();
        boolean clear = status == FlagshipStatus.READY || status == FlagshipStatus.NO_ESSENCE;
        AWScreenStyle.marker(graphics, left, y, clear);
        String wake = AWLang.translate("gui.rift_coupler.wake", Math.round(data.width()), Math.round(data.height()),
                Math.round(data.length())).string();
        graphics.drawString(font, wake, left + 10, y - 1, clear ? AWScreenStyle.VALUE : AWScreenStyle.LABEL, false);
        String essence = AWLang.translate("gui.rift_coupler.essence_line", AWLang.essence(data.essence(), data.capacity()))
                .string();
        boolean enough = data.essence() >= data.essenceNeeded();
        graphics.drawString(font, essence, guiLeft + info.right() - font.width(essence), y - 1,
                enough ? AWScreenStyle.VALUE : AWScreenStyle.BAD, false);
    }

    /** The Fold button filling red as it is held, with how many it will leave behind. */
    private void renderHold(GuiGraphics graphics) {
        if (!holding || foldButton == null) {
            return;
        }
        Rect fold = LAYOUT.fold();
        int x = guiLeft + fold.x();
        int y = guiTop + fold.y();
        float progress = AWAnim.clamp(held / (float) Math.max(1, AWConfig.FORCE_FOLD_HOLD_TICKS.get()));
        graphics.fill(x + 1, y + 1, x + 1 + Math.round((fold.width() - 2) * progress), y + fold.height() - 1,
                0x88_D9_5C_4A);
        int behind = 0;
        for (Row row : data.rows()) {
            if (!row.statusValue().ready()) {
                behind++;
            }
        }
        String label = AWLang.translate("gui.rift_coupler.leaving", behind).string();
        graphics.drawCenteredString(font, AWScreenStyle.trim(font, label, fold.width() - 4), x + fold.width() / 2,
                y + 5, 0xFF_FF_FF_FF);
    }

    private static int flagshipColour(FlagshipStatus status) {
        return switch (status) {
            case READY -> AWScreenStyle.OK;
            case FOLDING -> AWScreenStyle.ACCENT;
            case SURVEYING, NOT_SOUNDED, UNCHARGED -> AWScreenStyle.WARN;
            default -> AWScreenStyle.BAD;
        };
    }

    private static void outline(GuiGraphics graphics, int x0, int y0, int x1, int y1, int colour) {
        graphics.fill(x0, y0, x1, y0 + 1, colour);
        graphics.fill(x0, y1 - 1, x1, y1, colour);
        graphics.fill(x0, y0, x0 + 1, y1, colour);
        graphics.fill(x1 - 1, y0, x1, y1, colour);
    }

    /** A one-pixel line by steps, for the leash from a slot to a ship that has wandered off it. */
    private static void line(GuiGraphics graphics, int x0, int y0, int x1, int y1, int colour) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i += 2) {
            float t = steps == 0 ? 0 : i / (float) steps;
            int x = Math.round(x0 + (x1 - x0) * t);
            int y = Math.round(y0 + (y1 - y0) * t);
            graphics.fill(x, y, x + 1, y + 1, colour);
        }
    }
}
