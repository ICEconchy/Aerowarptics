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
 * <p>Courses are set at an Astrolabe Cartography Table and jumps are fired by a redstone input. Both
 * are shown here as requirements, so a drive that is waiting on one of them says so.
 */
@OnlyIn(Dist.CLIENT)
public class RiftDriveConsoleScreen extends AbstractSimiScreen {

    private static final int WINDOW_WIDTH = 264;
    private static final int WINDOW_HEIGHT = 196;
    private static final int REFRESH_INTERVAL = 10;

    private ClientboundDriveConsolePacket data;
    private int refreshTimer = REFRESH_INTERVAL;
    private int ticksOpen;

    private Button cancelButton;
    private Button headingButton;

    public RiftDriveConsoleScreen(ClientboundDriveConsolePacket data) {
        super(AWLang.translate("gui.rift_drive.title").component());
        this.data = data;
    }

    public boolean matches(BlockPos drivePos) {
        return data.drivePos().equals(drivePos);
    }

    /** Replaces the console's contents with a fresh server snapshot. */
    public void accept(ClientboundDriveConsolePacket packet) {
        this.data = packet;
        updateButtons();
    }

    @Override
    protected void init() {
        setWindowSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        super.init();
        clearWidgets();

        int buttonY = guiTop + WINDOW_HEIGHT - 26;
        cancelButton = Button.builder(AWLang.translate("gui.rift_navigation.abort").component(), b -> sendCancel())
                .bounds(guiLeft + 10, buttonY, 110, 18)
                .build();
        headingButton = Button.builder(headingLabel(), b -> {
            PacketDistributor.sendToServer(ServerboundWarpCommandPacket.cycleHeading(data.drivePos()));
            playClick(1.0F);
        }).bounds(guiLeft + WINDOW_WIDTH - 128, buttonY, 110, 18).build();
        headingButton.setTooltip(Tooltip.create(AWLang.translate("gui.rift_navigation.bow_hint").component()));

        addRenderableWidget(cancelButton);
        addRenderableWidget(headingButton);
        updateButtons();
    }

    private void updateButtons() {
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
        AWScreenStyle.window(graphics, guiLeft, guiTop, WINDOW_WIDTH, WINDOW_HEIGHT);

        RiftDriveTier tier = RiftDriveTier.byIndex(data.tierIndex());
        RiftDriveState state = RiftDriveState.byIndex(data.stateIndex());

        graphics.drawString(font, AWLang.translate("gui.rift_drive.title").component(),
                guiLeft + 10, guiTop + 10, AWScreenStyle.TITLE, false);
        graphics.drawString(font, AWLang.translate(tier.translationKey()).component(),
                guiLeft + 10, guiTop + 21, AWScreenStyle.LABEL, false);

        String ship = data.airshipName().isBlank()
                ? AWLang.translate("gui.rift_navigation.unnamed_ship").string()
                : data.airshipName();
        graphics.drawString(font, ship, guiLeft + WINDOW_WIDTH - 16 - font.width(ship),
                guiTop + 10, AWScreenStyle.LABEL, false);

        Component stateLabel = AWLang.translate(state.translationKey()).component();
        graphics.drawString(font, stateLabel, guiLeft + WINDOW_WIDTH - 16 - font.width(stateLabel),
                guiTop + 21, stateColour(state), false);

        renderReadouts(graphics);
        renderRequirements(graphics);
        renderBars(graphics, state);
    }

    private void renderReadouts(GuiGraphics graphics) {
        int left = guiLeft + 10;
        int top = guiTop + 36;
        int width = 118;
        AWScreenStyle.inset(graphics, left - 2, top - 2, width, 84);

        int line = top + 2;
        line = readout(graphics, left, line, width, "gui.rift_drive.speed",
                Math.round(data.currentRpm()) + " / " + data.requiredRpm(),
                data.currentRpm() >= data.requiredRpm() ? AWScreenStyle.VALUE : AWScreenStyle.BAD);
        line = readout(graphics, left, line, width, "gui.rift_drive.stress",
                String.format("%.0f su", data.stressImpact()), AWScreenStyle.VALUE);
        line = readout(graphics, left, line, width, "gui.rift_navigation.range",
                AWLang.distance(data.maximumRange()) + " m", AWScreenStyle.VALUE);
        line = readout(graphics, left, line, width, "gui.rift_drive.mass",
                AWLang.distance(data.airshipMass()), AWScreenStyle.VALUE);
        line = readout(graphics, left, line, width, "gui.rift_navigation.charge",
                AWLang.percent(data.charge()),
                data.charge() >= 1.0F ? AWScreenStyle.OK : AWScreenStyle.WARN);
        readout(graphics, left, line, width, "gui.rift_navigation.cooldown_short",
                data.cooldown() > 0 ? (data.cooldown() / 20) + "s" : "-",
                data.cooldown() > 0 ? AWScreenStyle.WARN : AWScreenStyle.LABEL);
    }

    private int readout(GuiGraphics graphics, int left, int y, int width, String key, String value, int colour) {
        graphics.drawString(font, AWLang.translate(key).component(), left + 2, y, AWScreenStyle.LABEL, false);
        String trimmed = font.plainSubstrByWidth(value, width - 8);
        graphics.drawString(font, trimmed, left + width - 8 - font.width(trimmed), y, colour, false);
        return y + 13;
    }

    /**
     * The list of things a jump needs, each either met or not.
     *
     * <p>Deliberately in the order they are checked when a warp actually starts, so working down the
     * list is the same as working through what the server would object to first.
     */
    private void renderRequirements(GuiGraphics graphics) {
        int left = guiLeft + 136;
        int top = guiTop + 36;
        int width = WINDOW_WIDTH - 154;
        AWScreenStyle.inset(graphics, left - 2, top - 2, width, 84);

        graphics.drawString(font, AWLang.translate("gui.rift_drive.requirements").component(),
                left + 2, top + 2, AWScreenStyle.TITLE, false);

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
        requirement(graphics, left, line, width,
                data.redstoneAllowed() ? "gui.rift_drive.req.signal" : "gui.rift_drive.req.signal_disabled",
                data.redstoneAllowed() && ready);

        // What the course actually is, under the list it is a line of. A name is worth more than a
        // tick when the question is "am I pointed at the right place".
        String label = course ? data.courseName()
                : AWLang.translate("gui.astrolabe.no_course").string();
        graphics.drawString(font, font.plainSubstrByWidth(label, width - 8),
                left + 2, top + 71, course ? AWScreenStyle.OK : AWScreenStyle.LABEL, false);
    }

    private int requirement(GuiGraphics graphics, int left, int y, int width, String key, boolean met) {
        AWScreenStyle.marker(graphics, left + 2, y, met);
        String label = font.plainSubstrByWidth(AWLang.translate(key).string(), width - 20);
        graphics.drawString(font, label, left + 12, y - 1, met ? AWScreenStyle.VALUE : AWScreenStyle.LABEL, false);
        return y + 10;
    }

    private void renderBars(GuiGraphics graphics, RiftDriveState state) {
        int left = guiLeft + 10;
        int width = WINDOW_WIDTH - 28;
        int top = guiTop + WINDOW_HEIGHT - 52;

        // A slow shimmer while charging so the bar reads as "working", not "stuck".
        int pulse = (int) (Math.sin((ticksOpen % 80) / 80.0D * Math.PI * 2.0D) * 24.0D);
        int chargeColour = data.charge() >= 1.0F
                ? AWScreenStyle.OK
                : 0xFF_00_00_00 | (Math.min(255, 0xC0 + pulse) << 16) | (0x90 << 8) | 0x3A;
        bar(graphics, left, top, width, data.charge(), chargeColour);

        // The spin-up bar only means anything while the drive is winding up, and showing an empty one
        // the rest of the time would read as a second thing that is not ready.
        if (state == RiftDriveState.STABILIZING) {
            graphics.drawString(font, AWLang.translate("gui.rift_drive.spin").component(),
                    left, top + 9, AWScreenStyle.LABEL, false);
            bar(graphics, left, top + 20, width, data.spinProgress(), 0xFF_C8_6C_FF);
        }
    }

    private static void bar(GuiGraphics graphics, int x, int y, int width, float fraction, int colour) {
        graphics.fill(x, y, x + width, y + 5, 0xFF_1A_16_12);
        int filled = (int) (width * Math.max(0.0F, Math.min(1.0F, fraction)));
        graphics.fill(x, y, x + filled, y + 5, colour);
    }

    private static int stateColour(RiftDriveState state) {
        return switch (state) {
            case ERROR -> AWScreenStyle.BAD;
            case COOLDOWN -> AWScreenStyle.WARN;
            case WARPING, ARRIVING, STABILIZING, DESTINATION_SELECTED -> 0xFF_C8_6C_FF;
            case CHARGED -> AWScreenStyle.OK;
            default -> AWScreenStyle.LABEL;
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
