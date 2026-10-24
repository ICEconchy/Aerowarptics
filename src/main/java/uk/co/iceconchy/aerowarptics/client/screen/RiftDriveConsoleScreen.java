package uk.co.iceconchy.aerowarptics.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import uk.co.iceconchy.aerowarptics.airship.Fastening;
import uk.co.iceconchy.aerowarptics.client.screen.AWLayout.Rect;
import uk.co.iceconchy.aerowarptics.drive.DriveHeading;
import uk.co.iceconchy.aerowarptics.drive.DriveMode;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveState;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;
import uk.co.iceconchy.aerowarptics.network.ClientboundDriveConsolePacket;
import uk.co.iceconchy.aerowarptics.network.ServerboundDriveConsolePacket;
import uk.co.iceconchy.aerowarptics.network.ServerboundWarpCommandPacket;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.warp.CruiseSensor;

/**
 * The Rift Drive's console: a diagnostic panel, and nothing else.
 *
 * <p>There is no destination list here and no way to launch. A drive is a machine, and what a player
 * standing at one needs is the answer to "why has this not gone anywhere" - so the screen is built
 * around a list of the conditions a jump needs, each of them either met or not, rather than around a
 * button that would refuse to work without saying why.
 *
 * <p>Courses are set at an Astrolabe Cartography Table or a Rift Probe, and jumps are fired by a
 * redstone input. All of those are shown here as requirements, so a drive that is waiting on one of
 * them says so.
 *
 * <p>The things this screen does set are how the drive flies - to its course, or on a free cruise - and
 * whether it looks for terrain before it does; the one journey it can touch once under way is a cruise,
 * which it can bring to a stop. None of them sends a ship anywhere.
 *
 * <p>Every label here is sized against {@link AWLayouts.Console}'s rooms, and {@code ConsoleTextTest}
 * measures the English text against the same numbers, so nothing on this screen is drawn cut short or
 * scrolling. The window itself shrinks to fit a screen smaller than it, through {@link AWWindowScreen},
 * so no GUI scale leaves its buttons hanging off the bottom.
 */
@OnlyIn(Dist.CLIENT)
public class RiftDriveConsoleScreen extends AWWindowScreen {

    private static final int REFRESH_INTERVAL = 10;

    private static final AWLayouts.Console LAYOUT = AWLayouts.console();

    private ClientboundDriveConsolePacket data;
    private int refreshTimer = REFRESH_INTERVAL;
    private int ticksOpen;

    /** Both bars chase their readings, so a drive charging looks like one rather than ticking up. */
    private final AWAnim.Eased charge = new AWAnim.Eased(0.2F);
    private final AWAnim.Eased spin = new AWAnim.Eased(0.25F);

    private Button cancelButton;
    private Button headingButton;
    private Button modeButton;
    private Button clearanceButton;
    /** The odometer's bar chases the reading like the others do, so a cruise reads as moving. */
    private final AWAnim.Eased cruise = new AWAnim.Eased(0.25F);

    public RiftDriveConsoleScreen(ClientboundDriveConsolePacket data) {
        super(AWLang.translate("gui.rift_drive.title").component());
        this.data = data;
        this.charge.snap(data.charge());
        this.spin.snap(data.spinProgress());
        this.cruise.snap(cruiseFraction(data));
    }

    public boolean matches(BlockPos drivePos) {
        return data.drivePos().equals(drivePos);
    }

    /** Replaces the console's contents with a fresh server snapshot. */
    public void accept(ClientboundDriveConsolePacket packet) {
        this.data = packet;
        charge.set(packet.charge());
        spin.set(packet.spinProgress());
        cruise.set(cruiseFraction(packet));
        updateButtons();
    }

    /** 0..1 of the way to where the charge or the range would force the cruise out. */
    private static float cruiseFraction(ClientboundDriveConsolePacket packet) {
        return packet.cruiseReach() <= 0.0D ? 0.0F
                : (float) Math.min(1.0D, packet.cruiseDistance() / packet.cruiseReach());
    }

    private RiftDriveBlockEntity.CruisePhase cruisePhase() {
        return RiftDriveBlockEntity.CruisePhase.byIndex(data.cruisePhaseIndex());
    }

    /** Whether a cruise is in flight - past spinning up, and not yet stopped. */
    private boolean cruiseUnderWay() {
        RiftDriveBlockEntity.CruisePhase phase = cruisePhase();
        return phase == RiftDriveBlockEntity.CruisePhase.CRUISING
                || phase == RiftDriveBlockEntity.CruisePhase.DROPPING;
    }

    @Override
    protected void init() {
        setWindowSize(AWLayouts.CONSOLE_WIDTH, AWLayouts.CONSOLE_HEIGHT);
        super.init();
        placeWindow(AWLayouts.CONSOLE_WIDTH, AWLayouts.CONSOLE_HEIGHT);
        clearWidgets();

        Rect cancel = LAYOUT.cancel();
        Rect heading = LAYOUT.heading();
        Rect modeRect = LAYOUT.mode();
        Rect clearance = LAYOUT.clearance();

        cancelButton = Button.builder(AWLang.translate("gui.rift_navigation.abort").component(), b -> sendCancel())
                .bounds(guiLeft + cancel.x(), guiTop + cancel.y(), cancel.width(), cancel.height())
                .build();
        headingButton = Button.builder(headingLabel(), b -> {
            PacketDistributor.sendToServer(ServerboundWarpCommandPacket.cycleHeading(data.drivePos()));
            playClick(1.0F);
        }).bounds(guiLeft + heading.x(), guiTop + heading.y(), heading.width(), heading.height()).build();
        headingButton.setTooltip(Tooltip.create(AWLang.translate("gui.rift_navigation.bow_hint").component()));
        modeButton = Button.builder(modeLabel(), b -> {
            PacketDistributor.sendToServer(ServerboundWarpCommandPacket.cycleMode(data.drivePos()));
            playClick(1.1F);
        }).bounds(guiLeft + modeRect.x(), guiTop + modeRect.y(), modeRect.width(), modeRect.height()).build();
        modeButton.setTooltip(Tooltip.create(AWLang.translate("gui.rift_drive.mode_hint").component()));
        clearanceButton = Button.builder(clearanceLabel(), b -> {
            PacketDistributor.sendToServer(ServerboundWarpCommandPacket.toggleClearance(data.drivePos()));
            playClick(data.clearanceChecks() ? 0.7F : 1.2F);
        }).bounds(guiLeft + clearance.x(), guiTop + clearance.y(), clearance.width(), clearance.height()).build();
        clearanceButton.setTooltip(Tooltip.create(AWLang.translate("gui.rift_drive.clearance_hint").component()));

        addRenderableWidget(headingButton);
        addRenderableWidget(modeButton);
        addRenderableWidget(clearanceButton);
        addRenderableWidget(cancelButton);
        updateButtons();
    }

    private void updateButtons() {
        if (cancelButton == null || headingButton == null || modeButton == null || clearanceButton == null) {
            return;
        }
        RiftDriveState state = RiftDriveState.byIndex(data.stateIndex());
        boolean disengage = cruisePhase() == RiftDriveBlockEntity.CruisePhase.CRUISING;
        // Never greyed out: with nothing to disengage or abort it reads Close, and sendCancel closes the
        // panel. It was live only for the other two, so an idle drive showed a Close that could not be pressed.
        cancelButton.active = true;
        cancelButton.setMessage(disengage ? AWLang.translate("gui.rift_drive.disengage").component()
                : state.isCancellable() ? AWLang.translate("gui.rift_navigation.abort").component()
                : AWLang.translate("gui.rift_navigation.close").component());
        headingButton.active = !state.isSequenceRunning() && !data.access().isFailure();
        headingButton.setMessage(headingLabel());
        modeButton.active = !state.isSequenceRunning() && !data.access().isFailure();
        modeButton.setMessage(modeLabel());
        clearanceButton.active = !state.isSequenceRunning() && !data.access().isFailure();
        clearanceButton.setMessage(clearanceLabel());
    }

    /** Gold while off, so a drive that is not looking says so from across the room. */
    private Component clearanceLabel() {
        return data.clearanceChecks()
                ? AWLang.translate("gui.rift_drive.clearance_on").component()
                : AWLang.translate("gui.rift_drive.clearance_off").style(ChatFormatting.GOLD).component();
    }

    private Component modeLabel() {
        return AWLang.translate(DriveMode.byIndex(data.modeIndex()).translationKey()).component();
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

    // ------------------------------------------------------------------ input

    // Into window coordinates before the widgets see the pointer, so the buttons are hit where they are
    // drawn once the window is scaled down to fit. An identity when it fits, which is the common case.

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(unscaleX(mouseX), unscaleY(mouseY), button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(unscaleX(mouseX), unscaleY(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged(unscaleX(mouseX), unscaleY(mouseY), button, dragX, dragY);
    }

    private void sendCancel() {
        if (cruisePhase() == RiftDriveBlockEntity.CruisePhase.CRUISING) {
            PacketDistributor.sendToServer(ServerboundWarpCommandPacket.disengage(data.drivePos()));
            playClick(0.9F);
        } else if (RiftDriveState.byIndex(data.stateIndex()).isCancellable()) {
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
    public void tick() {
        super.tick();
        ticksOpen++;
        charge.tick();
        spin.tick();
        cruise.tick();
        // Twice a second. This screen exists to be watched while somebody fixes whatever it is
        // complaining about, so it has to notice the fix.
        if (--refreshTimer <= 0) {
            refreshTimer = REFRESH_INTERVAL;
            PacketDistributor.sendToServer(new ServerboundDriveConsolePacket(data.drivePos()));
        }
    }

    // ----------------------------------------------------------------- render

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        AWScreenStyle.window(graphics, guiLeft, guiTop, AWLayouts.CONSOLE_WIDTH, AWLayouts.CONSOLE_HEIGHT);

        RiftDriveTier tier = RiftDriveTier.byIndex(data.tierIndex());
        RiftDriveState state = RiftDriveState.byIndex(data.stateIndex());
        String ship = data.airshipName().isBlank()
                ? AWLang.translate("gui.rift_navigation.unnamed_ship").string()
                : data.airshipName();

        AWScreenStyle.header(graphics, font, guiLeft, guiTop, LAYOUT.header(),
                AWLang.translate("gui.rift_drive.title").component(),
                AWLang.translate(tier.translationKey()).string(),
                ship,
                AWLang.translate(cruisePhase() == RiftDriveBlockEntity.CruisePhase.CRUISING
                        ? "drive.state.cruising" : state.translationKey()).component(),
                stateColour(state),
                AWScreenStyle.LABEL);

        renderReadouts(graphics);
        renderRequirements(graphics);
        renderBars(graphics, state, partialTicks);
    }

    private void renderReadouts(GuiGraphics graphics) {
        Rect panel = LAYOUT.readouts();
        AWScreenStyle.panel(graphics, guiLeft, guiTop, panel);

        int left = guiLeft + panel.x() + AWLayouts.PANEL_TEXT_PAD;
        int width = LAYOUT.readoutRoom();
        int line = guiTop + panel.y() + AWLayouts.PANEL_TEXT_PAD;

        line = AWScreenStyle.readout(graphics, font, left, line, width,
                AWLang.translate("gui.rift_drive.speed").component(),
                Math.round(data.currentRpm()) + " / " + data.requiredRpm(),
                data.currentRpm() >= data.requiredRpm() ? AWScreenStyle.VALUE : AWScreenStyle.BAD);
        line = AWScreenStyle.readout(graphics, font, left, line, width,
                AWLang.translate("gui.rift_drive.stress").component(),
                String.format("%.0f su", data.stressImpact()), AWScreenStyle.VALUE);
        line = AWScreenStyle.readout(graphics, font, left, line, width,
                AWLang.translate("gui.rift_navigation.range").component(),
                AWLang.reach(data.maximumRange()), AWScreenStyle.VALUE);
        line = AWScreenStyle.readout(graphics, font, left, line, width,
                AWLang.translate("gui.rift_drive.ship_range").component(),
                shipRange(), shipRangeColour());
        line = AWScreenStyle.readout(graphics, font, left, line, width,
                AWLang.translate("gui.rift_drive.mass").component(),
                AWLang.count(data.airshipMass()), AWScreenStyle.VALUE);
        line = AWScreenStyle.readout(graphics, font, left, line, width,
                AWLang.translate("gui.rift_navigation.charge").component(),
                AWLang.charge(RiftDriveTier.byIndex(data.tierIndex()).creative(), data.charge()),
                data.charge() >= 1.0F ? AWScreenStyle.OK : AWScreenStyle.WARN);
        AWScreenStyle.readout(graphics, font, left, line, width,
                AWLang.translate("gui.rift_navigation.cooldown_short").component(),
                data.cooldown() > 0 ? (data.cooldown() / 20) + "s" : "-",
                data.cooldown() > 0 ? AWScreenStyle.WARN : AWScreenStyle.LABEL);
    }

    /**
     * How far this drive can throw the ship it is on, under how far it could throw nothing at all.
     *
     * <p>The line above is the tier's range, which is the same on every drive of that tier. This is the
     * one that answers "why is that anchor out of reach": a hull spends part of each charge on its own
     * mass, and a heavy one runs out of charge well short of the tier's figure. A dash off a ship, where
     * there is no hull to weigh.
     */
    private String shipRange() {
        if (!data.hasShipRange()) {
            return "-";
        }
        return data.shipRange() < 0.0D
                ? AWLang.translate("gui.rift_drive.ship_range_none").string()
                : AWLang.reach(data.shipRange());
    }

    /** Amber once the hull's mass is costing it range, red when it is costing it all of it. */
    private int shipRangeColour() {
        if (!data.hasShipRange()) {
            return AWScreenStyle.LABEL;
        }
        if (data.shipRange() < 0.0D) {
            return AWScreenStyle.BAD;
        }
        return data.shipRange() + 0.5D < data.maximumRange() ? AWScreenStyle.WARN : AWScreenStyle.VALUE;
    }

    /**
     * The list of things a jump needs, each either met or not.
     *
     * <p>Deliberately in the order they are checked when a warp actually starts, so working down the
     * list is the same as working through what the server would object to first.
     */
    private void renderRequirements(GuiGraphics graphics) {
        Rect panel = LAYOUT.requirements();
        AWScreenStyle.panel(graphics, guiLeft, guiTop, panel);

        int left = guiLeft + panel.x() + AWLayouts.PANEL_TEXT_PAD;
        int top = guiTop + panel.y() + AWLayouts.PANEL_TEXT_PAD;
        int width = panel.width() - AWLayouts.PANEL_TEXT_PAD * 2;

        graphics.drawString(font, AWLang.translate("gui.rift_drive.requirements").component(),
                left, top, AWScreenStyle.TITLE, false);
        AWScreenStyle.rule(graphics, left, top + 10, width);

        boolean aboard = !data.access().isFailure();
        boolean turning = data.currentRpm() >= data.requiredRpm();
        boolean charged = data.charge() >= 1.0F;
        boolean course = data.hasCourse();
        boolean reachable = course && !data.courseFailure().isFailure();
        boolean ready = RiftDriveState.byIndex(data.stateIndex()).acceptsDestination();

        if (DriveMode.byIndex(data.modeIndex()) == DriveMode.FREE_CRUISE) {
            renderCruiseRequirements(graphics, left, top + 15, width, aboard, turning, charged, ready);
            return;
        }

        int line = top + 15;
        line = vessel(graphics, left, line, aboard);
        line = requirement(graphics, left, line, "gui.rift_drive.req.rotation", turning);
        line = requirement(graphics, left, line, "gui.rift_drive.req.charge", charged);
        line = requirement(graphics, left, line, "gui.rift_drive.req.course", course);
        line = requirement(graphics, left, line, "gui.rift_drive.req.reachable", reachable);
        line = requirement(graphics, left, line,
                data.redstoneAllowed() ? "gui.rift_drive.req.signal" : "gui.rift_drive.req.signal_disabled",
                data.redstoneAllowed() && ready);

        // What the course actually is, under the list it is a line of. A name is worth more than a
        // tick when the question is "am I pointed at the right place".
        AWScreenStyle.rule(graphics, left, line + 2, width);
        if (heldBy(graphics, left, line + 7)) {
            return;
        }
        String label = course ? data.courseName() : AWLang.translate("gui.astrolabe.no_course").string();
        graphics.drawString(font, AWScreenStyle.trim(font, label, LAYOUT.courseRoom()),
                left, line + 7, course ? AWScreenStyle.OK : AWScreenStyle.LABEL, false);
    }

    /**
     * The first line of either list: aboard a vessel, and nothing tying it down.
     *
     * <p>One line for both rather than a line each, because the panel is sized to its six and a seventh
     * would push the course off the bottom of it. A ship that is tied down is not a vessel ready to go, so
     * the one line says which it is - and the line under the rule says what is doing the tying.
     */
    private int vessel(GuiGraphics graphics, int left, int y, boolean aboard) {
        boolean tied = data.fastening().isPresent();
        return requirement(graphics, left, y, tied ? "gui.rift_drive.req.fastened" : "gui.rift_drive.req.vessel",
                aboard && !tied);
    }

    /**
     * What ties the ship down, in place of the line that would have said where it is going or how far it
     * could cruise - neither of which matters while it cannot leave.
     *
     * @return whether it drew anything
     */
    private boolean heldBy(GuiGraphics graphics, int left, int y) {
        Fastening tie = data.fastening().orElse(null);
        if (tie == null) {
            return false;
        }
        String label = AWLang.translate("gui.rift_drive.held_by",
                AWLang.component(tie.kind().translationKey())).string();
        graphics.drawString(font, AWScreenStyle.trim(font, label, LAYOUT.courseRoom()),
                left, y, AWScreenStyle.WARN, false);
        return true;
    }

    /**
     * The same list for a drive set to cruise, which has no course to set or reach.
     *
     * <p>Five lines where a waypoint has six: the course and whether it is in reach become one question,
     * whether the charge will pay to set off. The line under the rule then says how far a cruise could
     * go, or how far the one in flight has come against how far it may.
     */
    private void renderCruiseRequirements(GuiGraphics graphics, int left, int line, int width,
                                          boolean aboard, boolean turning, boolean charged, boolean ready) {
        boolean affordable = !data.cruiseFailure().isFailure();
        line = vessel(graphics, left, line, aboard);
        line = requirement(graphics, left, line, "gui.rift_drive.req.rotation", turning);
        line = requirement(graphics, left, line, "gui.rift_drive.req.charge", charged);
        line = requirement(graphics, left, line, "gui.rift_drive.req.cruise_reach", affordable);
        line = requirement(graphics, left, line,
                data.redstoneAllowed() ? "gui.rift_drive.req.cruise_signal" : "gui.rift_drive.req.signal_disabled",
                data.redstoneAllowed() && ready);
        // Kept on the same line the waypoint list puts its course on, so the two lists end in the same place.
        line += 10;

        AWScreenStyle.rule(graphics, left, line + 2, width);
        if (!cruiseUnderWay() && heldBy(graphics, left, line + 7)) {
            return;
        }
        String label = cruiseUnderWay()
                ? AWLang.cruised(data.cruiseDistance(), data.cruiseReach()).string()
                : AWLang.cruiseReach(data.cruiseReach()).string();
        graphics.drawString(font, AWScreenStyle.trim(font, label, LAYOUT.courseRoom()),
                left, line + 7, affordable || cruiseUnderWay() ? AWScreenStyle.OK : AWScreenStyle.LABEL, false);
    }

    private int requirement(GuiGraphics graphics, int left, int y, String key, boolean met) {
        AWScreenStyle.marker(graphics, left, y, met);
        String label = AWScreenStyle.trim(font, AWLang.translate(key).string(), LAYOUT.requirementRoom());
        graphics.drawString(font, label, left + AWLayouts.REQUIREMENT_INDENT, y - 1,
                met ? AWScreenStyle.VALUE : AWScreenStyle.LABEL, false);
        return y + 10;
    }

    /**
     * The one bar band: spin-up while the drive is winding up, charge the rest of the time.
     *
     * <p>They share a band rather than having one each because they are never both worth showing. A
     * drive only reaches {@code STABILIZING} once it is fully charged, so the charge bar this replaces
     * is a full bar that has stopped being the answer to anything - and the readouts panel is still
     * carrying the charge as a number besides.
     */
    private void renderBars(GuiGraphics graphics, RiftDriveState state, float partialTicks) {
        Rect band = LAYOUT.bar();
        int left = guiLeft + band.x();
        int width = band.width();
        int caption = guiTop + band.y();
        int bar = caption + 10;

        if (cruiseUnderWay()) {
            // How far the cruise has flown against where it will be forced out, captioned with how fast
            // it is going - or, when something is holding it back, what. A cruise that will not speed up
            // should say why on the one screen a pilot opens to find out.
            boolean cruising = cruisePhase() == RiftDriveBlockEntity.CruisePhase.CRUISING;
            CruiseSensor.Limit sight = CruiseSensor.Limit.values()[
                    Math.max(0, Math.min(CruiseSensor.Limit.values().length - 1, data.cruiseSight()))];
            String notice = !cruising ? "gui.rift_drive.dropping_out"
                    : sight == CruiseSensor.Limit.TERRAIN ? "gui.rift_drive.terrain_ahead"
                    : sight == CruiseSensor.Limit.UNLOADED ? "gui.rift_drive.loading_ahead"
                    : null;
            Component label = notice != null ? AWLang.translate(notice).component()
                    : AWLang.translate("gui.rift_drive.cruise_speed", AWLang.count(data.cruiseSpeed())).component();
            graphics.drawString(font, AWScreenStyle.trim(font, label.getString(), width),
                    left, caption, notice != null ? AWScreenStyle.WARN : AWScreenStyle.LABEL, false);
            AWScreenStyle.workingBar(graphics, left, bar, width, AWLayouts.BAR,
                    cruise.get(partialTicks), cruising ? AWScreenStyle.ACCENT : AWScreenStyle.WARN,
                    ticksOpen + partialTicks);
            return;
        }

        if (state == RiftDriveState.STABILIZING) {
            graphics.drawString(font, AWLang.translate("gui.rift_drive.spin").component(),
                    left, caption, AWScreenStyle.LABEL, false);
            AWScreenStyle.workingBar(graphics, left, bar, width, AWLayouts.BAR,
                    spin.get(partialTicks), AWScreenStyle.ACCENT, ticksOpen + partialTicks);
            return;
        }

        graphics.drawString(font, AWLang.translate("gui.rift_navigation.charge").component(),
                left, caption, AWScreenStyle.LABEL, false);
        boolean full = data.charge() >= 1.0F;
        int chargeColour = full ? AWScreenStyle.OK : AWScreenStyle.WARN;
        if (full) {
            AWScreenStyle.bar(graphics, left, bar, width, AWLayouts.BAR,
                    charge.get(partialTicks), chargeColour);
        } else {
            // A travelling highlight while it fills, so the bar reads as working rather than stuck.
            AWScreenStyle.workingBar(graphics, left, bar, width, AWLayouts.BAR,
                    charge.get(partialTicks), chargeColour, ticksOpen + partialTicks);
        }
    }

    private static int stateColour(RiftDriveState state) {
        return switch (state) {
            case ERROR -> AWScreenStyle.BAD;
            case COOLDOWN -> AWScreenStyle.WARN;
            case WARPING, ARRIVING, STABILIZING, DESTINATION_SELECTED -> AWScreenStyle.ACCENT;
            case CHARGED -> AWScreenStyle.OK;
            default -> AWScreenStyle.LABEL;
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
