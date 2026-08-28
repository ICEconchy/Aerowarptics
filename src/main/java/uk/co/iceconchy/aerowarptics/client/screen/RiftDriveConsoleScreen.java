package uk.co.iceconchy.aerowarptics.client.screen;

import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import uk.co.iceconchy.aerowarptics.client.screen.AWLayout.Rect;
import uk.co.iceconchy.aerowarptics.drive.DriveHeading;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveState;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;
import uk.co.iceconchy.aerowarptics.network.ClientboundDriveConsolePacket;
import uk.co.iceconchy.aerowarptics.network.ServerboundDriveConsolePacket;
import uk.co.iceconchy.aerowarptics.network.ServerboundWarpCommandPacket;
import uk.co.iceconchy.aerowarptics.util.AWLang;

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
 */
@OnlyIn(Dist.CLIENT)
public class RiftDriveConsoleScreen extends AbstractSimiScreen {

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

    public RiftDriveConsoleScreen(ClientboundDriveConsolePacket data) {
        super(AWLang.translate("gui.rift_drive.title").component());
        this.data = data;
        this.charge.snap(data.charge());
        this.spin.snap(data.spinProgress());
    }

    public boolean matches(BlockPos drivePos) {
        return data.drivePos().equals(drivePos);
    }

    /** Replaces the console's contents with a fresh server snapshot. */
    public void accept(ClientboundDriveConsolePacket packet) {
        this.data = packet;
        charge.set(packet.charge());
        spin.set(packet.spinProgress());
        updateButtons();
    }

    @Override
    protected void init() {
        setWindowSize(AWLayouts.CONSOLE_WIDTH, AWLayouts.CONSOLE_HEIGHT);
        super.init();
        guiLeft = AWLayout.anchor(width, AWLayouts.CONSOLE_WIDTH, guiLeft);
        guiTop = AWLayout.anchor(height, AWLayouts.CONSOLE_HEIGHT, guiTop);
        clearWidgets();

        Rect cancel = LAYOUT.cancel();
        Rect heading = LAYOUT.heading();

        cancelButton = Button.builder(AWLang.translate("gui.rift_navigation.abort").component(), b -> sendCancel())
                .bounds(guiLeft + cancel.x(), guiTop + cancel.y(), cancel.width(), cancel.height())
                .build();
        headingButton = Button.builder(headingLabel(), b -> {
            PacketDistributor.sendToServer(ServerboundWarpCommandPacket.cycleHeading(data.drivePos()));
            playClick(1.0F);
        }).bounds(guiLeft + heading.x(), guiTop + heading.y(), heading.width(), heading.height()).build();
        headingButton.setTooltip(Tooltip.create(AWLang.translate("gui.rift_navigation.bow_hint").component()));

        addRenderableWidget(cancelButton);
        addRenderableWidget(headingButton);
        updateButtons();
    }

    private void updateButtons() {
        if (cancelButton == null || headingButton == null) {
            return;
        }
        RiftDriveState state = RiftDriveState.byIndex(data.stateIndex());
        cancelButton.active = state.isCancellable();
        cancelButton.setMessage(state.isCancellable()
                ? AWLang.translate("gui.rift_navigation.abort").component()
                : AWLang.translate("gui.rift_navigation.close").component());
        headingButton.active = !state.isSequenceRunning() && !data.access().isFailure();
        headingButton.setMessage(headingLabel());
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
    public void tick() {
        super.tick();
        ticksOpen++;
        charge.tick();
        spin.tick();
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
                AWLang.translate(state.translationKey()).component(),
                stateColour(state),
                AWScreenStyle.LABEL);

        renderReadouts(graphics);
        renderRequirements(graphics);
        renderBars(graphics, state, partialTicks);
    }

    private void renderReadouts(GuiGraphics graphics) {
        Rect panel = LAYOUT.readouts();
        AWScreenStyle.panel(graphics, guiLeft, guiTop, panel);

        int left = guiLeft + panel.x() + 2;
        int width = panel.width() - 4;
        int line = guiTop + panel.y() + 2;

        line = AWScreenStyle.readout(graphics, font, left, line, width,
                AWLang.translate("gui.rift_drive.speed").component(),
                Math.round(data.currentRpm()) + " / " + data.requiredRpm(),
                data.currentRpm() >= data.requiredRpm() ? AWScreenStyle.VALUE : AWScreenStyle.BAD);
        line = AWScreenStyle.readout(graphics, font, left, line, width,
                AWLang.translate("gui.rift_drive.stress").component(),
                String.format("%.0f su", data.stressImpact()), AWScreenStyle.VALUE);
        line = AWScreenStyle.readout(graphics, font, left, line, width,
                AWLang.translate("gui.rift_navigation.range").component(),
                AWLang.distance(data.maximumRange()), AWScreenStyle.VALUE);
        line = AWScreenStyle.readout(graphics, font, left, line, width,
                AWLang.translate("gui.rift_drive.mass").component(),
                AWLang.count(data.airshipMass()), AWScreenStyle.VALUE);
        line = AWScreenStyle.readout(graphics, font, left, line, width,
                AWLang.translate("gui.rift_navigation.charge").component(),
                AWLang.percent(data.charge()),
                data.charge() >= 1.0F ? AWScreenStyle.OK : AWScreenStyle.WARN);
        AWScreenStyle.readout(graphics, font, left, line, width,
                AWLang.translate("gui.rift_navigation.cooldown_short").component(),
                data.cooldown() > 0 ? (data.cooldown() / 20) + "s" : "-",
                data.cooldown() > 0 ? AWScreenStyle.WARN : AWScreenStyle.LABEL);
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

        int left = guiLeft + panel.x() + 2;
        int top = guiTop + panel.y() + 2;
        int width = panel.width() - 4;

        graphics.drawString(font, AWLang.translate("gui.rift_drive.requirements").component(),
                left, top, AWScreenStyle.TITLE, false);
        AWScreenStyle.rule(graphics, left, top + 10, width);

        boolean aboard = !data.access().isFailure();
        boolean turning = data.currentRpm() >= data.requiredRpm();
        boolean charged = data.charge() >= 1.0F;
        boolean course = data.hasCourse();
        boolean reachable = course && !data.courseFailure().isFailure();
        boolean ready = RiftDriveState.byIndex(data.stateIndex()).acceptsDestination();

        int line = top + 15;
        line = requirement(graphics, left, line, width, "gui.rift_drive.req.vessel", aboard);
        line = requirement(graphics, left, line, width, "gui.rift_drive.req.rotation", turning);
        line = requirement(graphics, left, line, width, "gui.rift_drive.req.charge", charged);
        line = requirement(graphics, left, line, width, "gui.rift_drive.req.course", course);
        line = requirement(graphics, left, line, width, "gui.rift_drive.req.reachable", reachable);
        line = requirement(graphics, left, line, width,
                data.redstoneAllowed() ? "gui.rift_drive.req.signal" : "gui.rift_drive.req.signal_disabled",
                data.redstoneAllowed() && ready);

        // What the course actually is, under the list it is a line of. A name is worth more than a
        // tick when the question is "am I pointed at the right place".
        AWScreenStyle.rule(graphics, left, line + 2, width);
        String label = course ? data.courseName() : AWLang.translate("gui.astrolabe.no_course").string();
        graphics.drawString(font, AWScreenStyle.trim(font, label, width - 2),
                left, line + 7, course ? AWScreenStyle.OK : AWScreenStyle.LABEL, false);
    }

    private int requirement(GuiGraphics graphics, int left, int y, int width, String key, boolean met) {
        AWScreenStyle.marker(graphics, left, y, met);
        String label = AWScreenStyle.trim(font, AWLang.translate(key).string(), width - 12);
        graphics.drawString(font, label, left + 10, y - 1, met ? AWScreenStyle.VALUE : AWScreenStyle.LABEL, false);
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
