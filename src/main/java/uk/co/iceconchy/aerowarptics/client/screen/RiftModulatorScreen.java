package uk.co.iceconchy.aerowarptics.client.screen;

import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.DyeColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveState;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;
import uk.co.iceconchy.aerowarptics.modulator.RiftModulatorBlockEntity;
import uk.co.iceconchy.aerowarptics.modulator.RiftModulatorTheme;
import uk.co.iceconchy.aerowarptics.network.ClientboundModulatorPanelPacket;
import uk.co.iceconchy.aerowarptics.network.ServerboundModulatorPacket;
import uk.co.iceconchy.aerowarptics.util.AWLang;

/**
 * A Rift Modulator's panel.
 *
 * <p>Sixteen dye-colour swatches, a theme button and an intensity slider are the whole of what this
 * screen lets a player set - there is deliberately no free colour picker. Sixteen recognisable colours,
 * the same ones a player has already chosen wool and banners in, is a choice made in one click and read
 * at a glance from across a hangar; an RGB slider is neither.
 *
 * <p>The grid sets <em>two</em> colours at once, by which button clicks it: left sets the core colour
 * at the heart of the rift, right sets the accent colour its rim fades towards. There is no separate
 * mode to switch between them first - a mouse already has two buttons, and a swatch a player wants for
 * both colours is one click of each rather than a click to aim and a click to set.
 */
@OnlyIn(Dist.CLIENT)
public class RiftModulatorScreen extends AbstractSimiScreen {

    private static final AWLayouts.Modulator LAYOUT = AWLayouts.modulator();
    private static final DyeColor[] SWATCHES = DyeColor.values();

    private ClientboundModulatorPanelPacket data;
    private Button themeButton;

    /** The themed flourish thrown up when the theme is changed - cogs for clockwork, runes for arcane. */
    private final ThemeMotes motes = new ThemeMotes();

    private boolean draggingIntensity;
    /** What the slider is showing while it is being dragged, before the server has agreed. */
    private float pendingIntensity;

    public RiftModulatorScreen(ClientboundModulatorPanelPacket data) {
        super(AWLang.translate("gui.rift_modulator.title").component());
        this.data = data;
    }

    public boolean matches(BlockPos modulatorPos) {
        return data.modulatorPos().equals(modulatorPos);
    }

    public void accept(ClientboundModulatorPanelPacket packet) {
        this.data = packet;
        updateButtons();
    }

    @Override
    protected void init() {
        setWindowSize(AWLayouts.MODULATOR_WIDTH, AWLayouts.MODULATOR_HEIGHT);
        super.init();
        guiLeft = AWLayout.anchor(width, AWLayouts.MODULATOR_WIDTH, guiLeft);
        guiTop = AWLayout.anchor(height, AWLayouts.MODULATOR_HEIGHT, guiTop);
        clearWidgets();

        AWLayout.Rect theme = LAYOUT.theme();
        themeButton = Button.builder(Component.empty(), b -> cycleTheme())
                .bounds(guiLeft + theme.x(), guiTop + theme.y(), theme.width(), theme.height())
                .build();
        addRenderableWidget(themeButton);
        updateButtons();
    }

    private void updateButtons() {
        if (themeButton == null) {
            return;
        }
        themeButton.setMessage(AWLang.translate(currentTheme().translationKey()).component());
    }

    private RiftModulatorTheme currentTheme() {
        return RiftModulatorTheme.byIndex(data.themeOrdinal());
    }

    // ------------------------------------------------------------------ input

    private void cycleTheme() {
        // Shift reverses the cycle. With a dozen themes on one button, going one past the one you
        // wanted otherwise means eleven more clicks to come back round to it.
        boolean back = hasShiftDown();
        RiftModulatorTheme next = back ? currentTheme().previous() : currentTheme().next();
        PacketDistributor.sendToServer(ServerboundModulatorPacket.setTheme(data.modulatorPos(), next));
        // Throw the flourish for the theme being switched *to*, from the top edge of the button that
        // sets it, so the motes rise up the panel from where the click landed. The server has not
        // echoed the change back yet, but the button already shows the new name, so the burst matching
        // it is the honest thing to draw.
        AWLayout.Rect theme = LAYOUT.theme();
        motes.burst(next, theme.centreX(), theme.y());
        // Pitched down going back, so the direction is audible as well as visible.
        playClick(back ? 0.9F : 1.1F);
    }

    private void playClick(float pitch) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.25F, pitch);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = swatchAt(mouseX, mouseY);
        // Left sets the core colour, right sets the rim - anything else on a swatch falls through
        // rather than doing nothing silently, in case a third mouse button is bound to something else.
        if (index >= 0 && (button == 0 || button == 1)) {
            int colour = SWATCHES[index].getTextureDiffuseColor() & 0xFF_FF_FF;
            BlockPos pos = data.modulatorPos();
            PacketDistributor.sendToServer(button == 0
                    ? ServerboundModulatorPacket.setColour(pos, colour)
                    : ServerboundModulatorPacket.setAccentColour(pos, colour));
            playClick((button == 0 ? 1.0F : 1.3F) + index * 0.02F);
            return true;
        }
        if (button == 0 && intensityTrack().contains(mouseX - guiLeft, mouseY - guiTop)) {
            draggingIntensity = true;
            dragIntensity(mouseX);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingIntensity) {
            dragIntensity(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingIntensity) {
            draggingIntensity = false;
            // Sent on release rather than on every pixel of the drag, the same reasoning the probe's
            // range slider follows: the server clamps and echoes back, and a packet per mouse-move
            // would be a packet per frame.
            PacketDistributor.sendToServer(
                    ServerboundModulatorPacket.setIntensity(data.modulatorPos(), pendingIntensity));
            playClick(0.9F);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void dragIntensity(double mouseX) {
        AWLayout.Rect track = intensityTrack();
        double fraction = (mouseX - guiLeft - track.x()) / Math.max(1, track.width());
        fraction = Math.max(0.0D, Math.min(1.0D, fraction));
        float span = RiftModulatorBlockEntity.MAX_INTENSITY - RiftModulatorBlockEntity.MIN_INTENSITY;
        pendingIntensity = RiftModulatorBlockEntity.MIN_INTENSITY + (float) fraction * span;
    }

    private float shownIntensity() {
        return draggingIntensity ? pendingIntensity : data.intensity();
    }

    private AWLayout.Rect intensityTrack() {
        return AWLayouts.sliderBar(LAYOUT.intensity());
    }

    /** Which swatch, if any, sits under this point. */
    private int swatchAt(double mouseX, double mouseY) {
        AWLayout.Rect swatches = LAYOUT.swatches();
        // Matches renderSwatches exactly: swatches are drawn from the panel's own origin, with no
        // extra inset - the recessed frame AWScreenStyle.panel draws is offset outward from this,
        // the same convention every other list-style screen in this mod follows.
        double x = mouseX - guiLeft - swatches.x();
        double y = mouseY - guiTop - swatches.y();
        int cell = AWLayouts.SWATCH + AWLayouts.SWATCH_GAP;
        int column = (int) Math.floor(x / cell);
        int row = (int) Math.floor(y / cell);
        if (column < 0 || column >= AWLayouts.SWATCH_COLUMNS || row < 0
                || x - column * cell >= AWLayouts.SWATCH || y - row * cell >= AWLayouts.SWATCH) {
            return -1;
        }
        int index = row * AWLayouts.SWATCH_COLUMNS + column;
        return index < SWATCHES.length ? index : -1;
    }

    // ----------------------------------------------------------------- render

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        AWScreenStyle.window(graphics, guiLeft, guiTop, AWLayouts.MODULATOR_WIDTH, AWLayouts.MODULATOR_HEIGHT);

        AWScreenStyle.header(graphics, font, guiLeft, guiTop, LAYOUT.header(),
                AWLang.translate("gui.rift_modulator.title").component(),
                data.linked() ? AWLang.translate(data.driveTier().translationKey()).string() : "",
                "",
                AWLang.translate(data.linked()
                        ? (data.active() ? "gui.rift_modulator.active" : "gui.rift_modulator.idle")
                        : "gui.rift_modulator.not_linked").component(),
                data.linked() ? (data.active() ? AWScreenStyle.OK : AWScreenStyle.WARN) : AWScreenStyle.BAD,
                AWScreenStyle.LABEL);

        renderSwatches(graphics, mouseX, mouseY);
        renderDetail(graphics);
        renderIntensity(graphics);
    }

    private void renderSwatches(GuiGraphics graphics, int mouseX, int mouseY) {
        AWLayout.Rect panel = LAYOUT.swatches();
        AWScreenStyle.panel(graphics, guiLeft, guiTop, panel);

        int left = guiLeft + panel.x();
        int top = guiTop + panel.y();
        int cell = AWLayouts.SWATCH + AWLayouts.SWATCH_GAP;
        int hovered = swatchAt(mouseX, mouseY);

        for (int index = 0; index < SWATCHES.length; index++) {
            int column = index % AWLayouts.SWATCH_COLUMNS;
            int row = index / AWLayouts.SWATCH_COLUMNS;
            int x = left + column * cell;
            int y = top + row * cell;
            int colour = 0xFF_00_00_00 | (SWATCHES[index].getTextureDiffuseColor() & 0xFF_FF_FF);
            boolean rgb = (colour & 0xFF_FF_FF) == data.colour();
            boolean rim = (colour & 0xFF_FF_FF) == data.accentColour();

            graphics.fill(x, y, x + AWLayouts.SWATCH, y + AWLayouts.SWATCH, colour);
            // Two independent rings rather than one that switches colour: a swatch can be both the
            // core and the rim at once, and both should be visible at a glance rather than one of
            // them winning. Nested rather than side by side, so a swatch that is only one of the two
            // still reads as a single clean outline instead of half a ring.
            if (rgb) {
                ring(graphics, x, y, AWLayouts.SWATCH, 1, AWScreenStyle.ACCENT);
            }
            if (rim) {
                ring(graphics, x, y, AWLayouts.SWATCH, rgb ? 3 : 1, AWScreenStyle.WARN);
            }
            if (!rgb && !rim && index == hovered) {
                graphics.fill(x, y, x + AWLayouts.SWATCH, y + AWLayouts.SWATCH, 0x55_FF_FF_FF);
            }
        }

        // The legend, in the room the four-by-four grid leaves under itself. It belongs beside the
        // swatches rather than over in the detail panel: it says what a click on *this* grid does,
        // and the detail panel is about the drive. It also costs nothing here - that space was empty.
        int legend = top + rows() * cell + AWLayout.GUTTER;
        chip(graphics, left, legend, data.colour(), AWScreenStyle.ACCENT, "gui.rift_modulator.core_hint");
        chip(graphics, left, legend + AWLayouts.CHIP + 5, data.accentColour(), AWScreenStyle.WARN,
                "gui.rift_modulator.rim_hint");
    }

    /** Rows the swatch grid actually occupies, so the legend under it cannot drift out of step. */
    private static int rows() {
        return (SWATCHES.length + AWLayouts.SWATCH_COLUMNS - 1) / AWLayouts.SWATCH_COLUMNS;
    }

    /** One legend entry: a colour chip, ringed as its swatch is, and the click that sets it. */
    private void chip(GuiGraphics graphics, int x, int y, int colour, int ringColour, String key) {
        graphics.fill(x, y, x + AWLayouts.CHIP, y + AWLayouts.CHIP, 0xFF_00_00_00 | colour);
        ring(graphics, x, y, AWLayouts.CHIP, 1, ringColour);
        graphics.drawString(font, AWLang.translate(key).component(),
                x + AWLayouts.CHIP + 5, y, AWScreenStyle.LABEL, false);
    }

    /**
     * A one-pixel outline standing {@code outset} pixels clear of a square of the given size.
     *
     * <p>The size is a parameter rather than assumed to be {@link AWLayouts#SWATCH}. It was assumed,
     * and the legend's half-size chips then got a full swatch-sized box drawn round them - which
     * painted straight over the label beside each chip and over the first line of the panel below.
     */
    private void ring(GuiGraphics graphics, int x, int y, int size, int outset, int colour) {
        int x0 = x - outset;
        int y0 = y - outset;
        int x1 = x + size + outset;
        int y1 = y + size + outset;
        graphics.fill(x0, y0, x1, y0 + 1, colour);
        graphics.fill(x0, y1 - 1, x1, y1, colour);
        graphics.fill(x0, y0, x0 + 1, y1, colour);
        graphics.fill(x1 - 1, y0, x1, y1, colour);
    }

    private void renderDetail(GuiGraphics graphics) {
        AWLayout.Rect panel = LAYOUT.detail();
        AWScreenStyle.panel(graphics, guiLeft, guiTop, panel);

        int left = guiLeft + panel.x() + 2;
        int top = guiTop + panel.y() + 2;
        int width = panel.width() - 4;

        // Label and reading on one line, with the bar under it - the shape every other readout in this
        // mod uses. It only fits because the window is now wide enough for it to; at the old width the
        // value was trimmed to "500 / 50...", and stacking it onto its own line to dodge that was
        // solving the wrong problem with the panel's vertical space.
        boolean low = data.capacity() > 0 && data.essence() < data.upkeepCost();
        int line = AWScreenStyle.readout(graphics, font, left, top, width,
                AWLang.translate("gui.rift_modulator.supply").component(),
                AWLang.essence(data.essence(), data.capacity()),
                low ? AWScreenStyle.BAD : AWScreenStyle.VALUE);
        AWScreenStyle.bar(graphics, left, line + 2, width, AWLayouts.BAR,
                data.capacity() <= 0 ? 0.0F : data.essence() / (float) data.capacity(),
                low ? AWScreenStyle.WARN : AWScreenStyle.OK);
        line += AWLayouts.BAR + AWLayout.GUTTER;

        AWScreenStyle.rule(graphics, left, line, width);
        line += 8;

        if (!data.linked()) {
            for (var piece : font.split(
                    AWLang.translate("gui.rift_modulator.not_linked_hint").component(), width - 4)) {
                graphics.drawString(font, piece, left, line, AWScreenStyle.BAD, false);
                line += 10;
            }
            return;
        }

        RiftDriveTier tier = data.driveTier();
        RiftDriveState state = data.driveStateValue();
        line = AWScreenStyle.readout(graphics, font, left, line, width,
                AWLang.translate("gui.rift_modulator.drive").component(),
                AWLang.translate(tier.translationKey()).string(), AWScreenStyle.VALUE);
        line = AWScreenStyle.readout(graphics, font, left, line, width,
                AWLang.translate("gui.rift_modulator.state").component(),
                AWLang.translate(state.translationKey()).string(), AWScreenStyle.VALUE);
        if (!data.driveLabel().isEmpty()) {
            AWScreenStyle.readout(graphics, font, left, line, width,
                    AWLang.translate("gui.rift_modulator.destination").component(),
                    data.driveLabel(), AWScreenStyle.VALUE);
        }
    }

    private void renderIntensity(GuiGraphics graphics) {
        AWLayout.Rect panel = LAYOUT.intensity();
        int left = guiLeft + panel.x();
        int top = guiTop + panel.y();

        graphics.drawString(font, AWLang.translate("gui.rift_modulator.intensity").component(),
                left, top, AWScreenStyle.LABEL, false);
        String value = Math.round(shownIntensity() * 100.0F) + "%";
        graphics.drawString(font, value, left + panel.width() - font.width(value), top,
                AWScreenStyle.VALUE, false);

        AWLayout.Rect track = intensityTrack();
        float span = RiftModulatorBlockEntity.MAX_INTENSITY - RiftModulatorBlockEntity.MIN_INTENSITY;
        float fraction = (shownIntensity() - RiftModulatorBlockEntity.MIN_INTENSITY) / span;
        AWScreenStyle.bar(graphics, guiLeft + track.x(), guiTop + track.y(),
                track.width(), track.height(), fraction, AWScreenStyle.ACCENT_DIM);

        int knob = guiLeft + track.x() + Math.round(track.width() * AWAnim.clamp(fraction));
        graphics.fill(knob - 2, guiTop + track.y() - 2, knob + 2, guiTop + track.y() + track.height() + 2,
                AWScreenStyle.ACCENT);
    }

    @Override
    public void tick() {
        super.tick();
        motes.tick();
    }

    /**
     * The themed motes are drawn in the foreground, over the panels and the button, so a burst reads as
     * rising out of the screen rather than being painted behind it. They are clipped to the window's
     * interior so a mote that flies wide fades against the frame instead of spilling onto the dimmed
     * game behind it.
     */
    @Override
    protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderWindowForeground(graphics, mouseX, mouseY, partialTicks);
        AWLayout.Rect interior = AWLayout.interior(AWLayouts.MODULATOR_WIDTH, AWLayouts.MODULATOR_HEIGHT);
        graphics.enableScissor(guiLeft + interior.x(), guiTop + interior.y(),
                guiLeft + interior.right(), guiTop + interior.bottom());
        motes.render((left, top, right, bottom, argb) ->
                graphics.fill(left + guiLeft, top + guiTop, right + guiLeft, bottom + guiTop, argb), partialTicks);
        graphics.disableScissor();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
