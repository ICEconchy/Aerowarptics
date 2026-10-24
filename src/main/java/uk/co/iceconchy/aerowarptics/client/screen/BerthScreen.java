package uk.co.iceconchy.aerowarptics.client.screen;

import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import uk.co.iceconchy.aerowarptics.berth.Berth;
import uk.co.iceconchy.aerowarptics.berth.BerthBlockEntity;
import uk.co.iceconchy.aerowarptics.berth.BerthBox;
import uk.co.iceconchy.aerowarptics.berth.BerthReserve;
import uk.co.iceconchy.aerowarptics.berth.BerthRules;
import uk.co.iceconchy.aerowarptics.berth.BerthState;
import uk.co.iceconchy.aerowarptics.client.PlotterCameraRig;
import uk.co.iceconchy.aerowarptics.client.screen.AWLayout.Rect;
import uk.co.iceconchy.aerowarptics.network.ServerboundBerthPacket;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkRules;

import java.util.List;

/**
 * A Berth's panel: how big its slip is, where it lies and which way it faces, and whom it is kept for - set by
 * looking at it.
 *
 * <p>The Rift Ward's panel in everything but what is on it ({@link BerthLayout}): a strip down the right of the
 * screen with the world undimmed beside it, and for as long as it is open the camera leaves the player's head
 * and looks down on the slip. The slip <em>as the sliders have it</em> is drawn over the world
 * ({@code BerthGuides}) - its box, an arrow for the way a moored ship's bow points, and the lane behind it that
 * a ship flies down to come in. Drag over the world to look round, scroll to come closer.
 *
 * <p>Three tabs. <b>Slip</b>: length, width, height, facing, which end ships come in by, and the switch.
 * <b>Place</b>: where the slip lies from the block, its place in the queue, whether it catches ships flown in
 * by hand, and Cast Off. <b>Who</b>: whom it is kept for, its name, and its anchor.
 *
 * <p>Nothing is sent until Save, and the server checks all of it. The camera is always given back: closing the
 * panel any way at all goes through {@link #removed}.
 */
@OnlyIn(Dist.CLIENT)
public class BerthScreen extends AbstractSimiScreen {

    private enum Tab {
        SLIP("slip"), PLACE("place"), WHO("who");

        final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    /** A row that is a slider: which, so that a drag begun on one stays on it. */
    private enum Slider {
        LENGTH, WIDTH, HEIGHT, YAW, EAST, UP, SOUTH, PRIORITY
    }

    private static final int STONE = 0xE8_2A2336;
    private static final int BRASS_DARK = 0xFF_7A6038;
    private static final int BRASS = 0xFF_B08D57;
    private static final int BRASS_LIGHT = 0xFF_D8B068;
    private static final int TEXT = 0xFF_E6E1D6;
    private static final int FAINT = 0xFF_8E86A0;
    private static final int GLYPH = 0xFF_A060F0;
    private static final int INK = 0xFF_2A1C0C;

    private static final float CAMERA_PITCH = 0.9F;
    private static final float CAMERA_YAW = 0.6F;

    private final BerthBlockEntity block;

    private WardLayout.Sidebar sidebar;
    private Tab tab = Tab.SLIP;
    private EditBox nameBox;
    private EditBox networkBox;

    private String name;
    private String network;
    private int offsetX;
    private int offsetY;
    private int offsetZ;
    private int slipLength;
    private int slipWidth;
    private int slipHeight;
    private int yaw;
    private int priority;
    private boolean enabled;
    private boolean sternFirst;
    private boolean tractor;
    private BerthReserve reserve;

    private Slider dragging;
    private boolean orbiting;
    private boolean flown;
    private float distance;

    public BerthScreen(BerthBlockEntity block) {
        super(AWLang.translate("gui.berth.title").component());
        this.block = block;
        Berth berth = block.berth();
        this.name = berth.name();
        this.network = block.networkName();
        this.offsetX = berth.offsetX();
        this.offsetY = berth.offsetY();
        this.offsetZ = berth.offsetZ();
        this.slipLength = Math.min(berth.length(), block.maxLength());
        this.slipWidth = Math.min(berth.width(), block.maxWidth());
        this.slipHeight = Math.min(berth.height(), block.maxHeight());
        this.yaw = berth.yaw();
        this.priority = berth.priority();
        this.enabled = berth.enabled();
        this.sternFirst = berth.sternFirst();
        this.tractor = berth.tractor();
        this.reserve = berth.reserve();
    }

    /** The slip as the sliders have it now, saved or not - what is drawn over the world while this is open. */
    public BerthBox draftBox() {
        return new BerthBox(block.getBlockPos().getX() + 0.5D + offsetX, block.getBlockPos().getY() + offsetY,
                block.getBlockPos().getZ() + 0.5D + offsetZ, slipLength, slipWidth, slipHeight, yaw);
    }

    public boolean draftSternFirst() {
        return sternFirst;
    }

    public boolean draftEnabled() {
        return enabled;
    }

    @Override
    protected void init() {
        // Laid out against the whole screen: the strip sits at its edge and is never scaled.
        setWindowSize(width, height);
        super.init();
        if (nameBox != null) {
            name = nameBox.getValue();
        }
        if (networkBox != null) {
            network = networkBox.getValue();
        }
        clearWidgets();
        guiLeft = 0;
        guiTop = 0;
        sidebar = BerthLayout.sidebar(width, height);
        nameBox = null;
        networkBox = null;
        if (tab == Tab.WHO) {
            if (reserve == BerthReserve.NETWORK) {
                Rect box = BerthLayout.textBox(sidebar.rows().get(1));
                networkBox = new EditBox(font, box.x(), box.y(), box.width(), box.height(),
                        AWLang.translate("gui.berth.network").component());
                networkBox.setMaxLength(NetworkRules.NAME_MAX);
                networkBox.setValue(network);
                addRenderableWidget(networkBox);
            }
            Rect box = BerthLayout.textBox(sidebar.rows().get(3));
            nameBox = new EditBox(font, box.x(), box.y(), box.width(), box.height(),
                    AWLang.translate("gui.berth.name").component());
            nameBox.setMaxLength(BerthRules.NAME_LENGTH);
            nameBox.setValue(name);
            addRenderableWidget(nameBox);
        }
        if (!flown) {
            flown = true;
            distance = BerthLayout.cameraDistance(slipLength, slipWidth);
            BerthBox box = draftBox();
            PlotterCameraRig.start(new Vec3(box.centreX(), box.floorY() + box.height() * 0.5D, box.centreZ()),
                    CAMERA_YAW, CAMERA_PITCH, distance);
        }
    }

    /** Every way out of the panel comes through here, and the camera goes back to the player. */
    @Override
    public void removed() {
        PlotterCameraRig.stop();
        super.removed();
    }

    /** With the world in view, nothing is dimmed or blurred behind the strip: the slip is set by looking at it. */
    @Override
    protected void renderMenuBackground(GuiGraphics graphics) {
    }

    @Override
    protected void renderWindowBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
    }

    @Override
    public void tick() {
        super.tick();
        if (block.isRemoved() || block.berth() == null) {
            onClose();
        }
    }

    private void showTab(Tab next) {
        if (tab != next) {
            tab = next;
            click(1.0F);
            rebuildWidgets();
        }
    }

    private void save() {
        if (nameBox != null) {
            name = nameBox.getValue();
        }
        if (networkBox != null) {
            network = networkBox.getValue();
        }
        PacketDistributor.sendToServer(new ServerboundBerthPacket(block.getBlockPos(), ServerboundBerthPacket.Action.SAVE,
                BerthRules.name(name), offsetX, offsetY, offsetZ, slipLength, slipWidth, slipHeight, yaw, priority, enabled,
                sternFirst, tractor, reserve, network == null ? "" : network));
    }

    private void order(ServerboundBerthPacket.Action action) {
        PacketDistributor.sendToServer(ServerboundBerthPacket.order(block.getBlockPos(), action));
    }

    private void click(float pitch) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
        }
    }

    // ------------------------------------------------------------------ the sliders

    /** The slider on a row of the tab that is showing, or {@code null} for a row that is not one. */
    private Slider sliderAt(int row) {
        if (tab == Tab.SLIP) {
            return switch (row) {
                case 0 -> Slider.LENGTH;
                case 1 -> Slider.WIDTH;
                case 2 -> Slider.HEIGHT;
                case 3 -> Slider.YAW;
                default -> null;
            };
        }
        if (tab == Tab.PLACE) {
            return switch (row) {
                case 0 -> Slider.EAST;
                case 1 -> Slider.UP;
                case 2 -> Slider.SOUTH;
                case 3 -> Slider.PRIORITY;
                default -> null;
            };
        }
        return null;
    }

    private int rowOf(Slider slider) {
        return switch (slider) {
            case LENGTH, EAST -> 0;
            case WIDTH, UP -> 1;
            case HEIGHT, SOUTH -> 2;
            case YAW, PRIORITY -> 3;
        };
    }

    private int min(Slider slider) {
        return switch (slider) {
            case LENGTH, WIDTH, HEIGHT -> BerthRules.MIN_SIZE;
            case YAW -> 0;
            case EAST, UP, SOUTH -> -BerthRules.MAX_OFFSET;
            case PRIORITY -> BerthRules.MIN_PRIORITY;
        };
    }

    private int max(Slider slider) {
        return switch (slider) {
            case LENGTH -> Math.max(BerthRules.MIN_SIZE, block.maxLength());
            case WIDTH -> Math.max(BerthRules.MIN_SIZE, block.maxWidth());
            case HEIGHT -> Math.max(BerthRules.MIN_SIZE, block.maxHeight());
            case YAW -> 360 - BerthRules.YAW_STEP;
            case EAST, UP, SOUTH -> BerthRules.MAX_OFFSET;
            case PRIORITY -> BerthRules.MAX_PRIORITY;
        };
    }

    private int step(Slider slider) {
        return slider == Slider.YAW ? BerthRules.YAW_STEP : 1;
    }

    private int value(Slider slider) {
        return switch (slider) {
            case LENGTH -> slipLength;
            case WIDTH -> slipWidth;
            case HEIGHT -> slipHeight;
            case YAW -> yaw;
            case EAST -> offsetX;
            case UP -> offsetY;
            case SOUTH -> offsetZ;
            case PRIORITY -> priority;
        };
    }

    private void set(Slider slider, int wanted) {
        int held = Math.max(min(slider), Math.min(max(slider), wanted));
        switch (slider) {
            case LENGTH -> slipLength = held;
            case WIDTH -> slipWidth = held;
            case HEIGHT -> slipHeight = held;
            case YAW -> yaw = BerthRules.yaw(held);
            case EAST -> offsetX = held;
            case UP -> offsetY = held;
            case SOUTH -> offsetZ = held;
            case PRIORITY -> priority = held;
        }
    }

    private String shown(Slider slider) {
        return switch (slider) {
            case YAW -> AWLang.translate("gui.berth.degrees", yaw).string();
            case PRIORITY -> Integer.toString(priority);
            default -> AWLang.distance(value(slider));
        };
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (sidebar == null) {
            return super.mouseClicked(x, y, button);
        }
        if (!sidebar.frame().contains(x, y)) {
            // Anywhere over the world is the camera's.
            orbiting = button == 0;
            if (nameBox != null) {
                nameBox.setFocused(false);
            }
            if (networkBox != null) {
                networkBox.setFocused(false);
            }
            return true;
        }
        if (button != 0) {
            return super.mouseClicked(x, y, button);
        }
        if (sidebar.close().contains(x, y)) {
            click(0.9F);
            onClose();
            return true;
        }
        if (sidebar.save().contains(x, y)) {
            click(1.0F);
            save();
            onClose();
            return true;
        }
        for (int i = 0; i < sidebar.tabs().size(); i++) {
            if (sidebar.tabs().get(i).contains(x, y)) {
                showTab(Tab.values()[i]);
                return true;
            }
        }
        List<Rect> rows = sidebar.rows();
        for (int row = 0; row < rows.size(); row++) {
            if (!rows.get(row).contains(x, y)) {
                continue;
            }
            Slider slider = sliderAt(row);
            if (slider != null) {
                dragging = slider;
                drag(x);
                return true;
            }
            if (clickedRow(row)) {
                return true;
            }
        }
        return super.mouseClicked(x, y, button);
    }

    /** A click on a row that is not a slider. */
    private boolean clickedRow(int row) {
        if (tab == Tab.SLIP && row == 4) {
            sternFirst = !sternFirst;
            click(1.0F);
            return true;
        }
        if (tab == Tab.SLIP && row == 5) {
            enabled = !enabled;
            click(enabled ? 1.2F : 0.8F);
            return true;
        }
        if (tab == Tab.PLACE && row == 4) {
            tractor = !tractor;
            click(tractor ? 1.2F : 0.8F);
            return true;
        }
        if (tab == Tab.PLACE && row == 5) {
            if (block.berth() != null && block.berth().state().holds()) {
                click(1.0F);
                order(ServerboundBerthPacket.Action.CAST_OFF);
                onClose();
            }
            return true;
        }
        if (tab == Tab.WHO && row == 0) {
            reserve = reserve.next();
            click(1.0F);
            rebuildWidgets();
            return true;
        }
        if (tab == Tab.WHO && row == 5) {
            click(1.0F);
            order(ServerboundBerthPacket.Action.RELINK);
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
        if (dragging != null) {
            drag(x);
            return true;
        }
        if (orbiting) {
            PlotterCameraRig.turn((float) (-dragX * 0.012D), (float) (dragY * 0.01D));
            return true;
        }
        return super.mouseDragged(x, y, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        boolean ours = dragging != null || orbiting;
        dragging = null;
        orbiting = false;
        return ours || super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (sidebar == null || scrollY == 0.0D) {
            return super.mouseScrolled(x, y, scrollX, scrollY);
        }
        if (!sidebar.frame().contains(x, y)) {
            float factor = scrollY > 0 ? 0.87F : 1.15F;
            PlotterCameraRig.zoom(factor);
            distance = Math.max(OrbitCamera.MIN_DISTANCE, Math.min(OrbitCamera.MAX_DISTANCE, distance * factor));
            return true;
        }
        List<Rect> rows = sidebar.rows();
        for (int row = 0; row < rows.size(); row++) {
            Slider slider = rows.get(row).contains(x, y) ? sliderAt(row) : null;
            if (slider != null) {
                // A step at a time under the wheel: a long slider is two blocks to the pixel.
                set(slider, value(slider) + (int) Math.signum(scrollY) * step(slider));
                return true;
            }
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    private void drag(double mouseX) {
        Slider slider = dragging;
        if (slider == null) {
            return;
        }
        set(slider, BerthLayout.sliderValue(sidebar.rows().get(rowOf(slider)), mouseX, min(slider), max(slider),
                step(slider)));
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        WardLayout.Sidebar side = sidebar;
        Berth berth = block.berth();
        if (side == null || berth == null) {
            return;
        }
        Rect frame = side.frame();
        int x = frame.x();
        int w = frame.width();
        int h = frame.height();
        graphics.fill(x, 0, x + w, h, STONE);
        graphics.fill(x, 0, x + 2, h, BRASS_DARK);
        graphics.fill(x, 0, x + 1, h, BRASS_LIGHT);
        // The strip's seam is lit as the bollard's lamp is.
        graphics.fill(x + 4, 4, x + 5, h - 4, AWAnim.blend(0xFF_2A1838, lamp(berth.state()), berth.enabled() ? 0.8F : 0.0F));

        graphics.drawString(font, AWScreenStyle.trim(font, AWLang.translate("gui.berth.title").string(),
                side.header().width()), side.header().x(), side.header().y() + 2, BRASS_LIGHT, false);
        button(graphics, side.close(), AWLang.translate("gui.berth.close").component(), true);
        for (int i = 0; i < side.tabs().size(); i++) {
            Rect rect = side.tabs().get(i);
            boolean on = Tab.values()[i] == tab;
            graphics.fill(rect.x(), rect.y(), rect.right() - 1, rect.bottom(), on ? BRASS : BRASS_DARK);
            graphics.fill(rect.x(), rect.y(), rect.right() - 1, rect.y() + 1, on ? BRASS_LIGHT : BRASS);
            Component label = AWLang.translate("gui.berth.tab." + Tab.values()[i].key).component();
            graphics.drawString(font, label, rect.x() + (rect.width() - font.width(label)) / 2, rect.y() + 3,
                    on ? INK : 0xFF_E8D8B0, false);
        }
        List<Rect> rows = side.rows();
        switch (tab) {
            case SLIP -> {
                slider(graphics, rows, Slider.LENGTH, "gui.berth.length");
                slider(graphics, rows, Slider.WIDTH, "gui.berth.width");
                slider(graphics, rows, Slider.HEIGHT, "gui.berth.height");
                slider(graphics, rows, Slider.YAW, "gui.berth.facing");
                button(graphics, rows.get(4), AWLang.translate(sternFirst ? "gui.berth.stern_first"
                        : "gui.berth.bow_first").component(), true);
                toggle(graphics, rows.get(5), AWLang.translate(enabled ? "gui.berth.on" : "gui.berth.off").component(),
                        enabled);
            }
            case PLACE -> {
                slider(graphics, rows, Slider.EAST, "gui.berth.east");
                slider(graphics, rows, Slider.UP, "gui.berth.up");
                slider(graphics, rows, Slider.SOUTH, "gui.berth.south");
                slider(graphics, rows, Slider.PRIORITY, "gui.berth.priority");
                toggle(graphics, rows.get(4), AWLang.translate("gui.berth.tractor").component(), tractor);
                button(graphics, rows.get(5), AWLang.translate("gui.berth.cast_off").component(), berth.state().holds());
            }
            case WHO -> {
                button(graphics, rows.get(0), AWLang.translate("gui.berth.reserve." + reserve.id()).component(), true);
                if (reserve != BerthReserve.NETWORK) {
                    graphics.drawString(font, AWScreenStyle.trim(font, AWLang.translate("gui.berth.reserve."
                            + reserve.id() + ".about").string(), rows.get(1).width()), rows.get(1).x(),
                            rows.get(1).y() + 6, FAINT, false);
                }
                graphics.drawString(font, AWLang.translate("gui.berth.name").component(), rows.get(2).x(),
                        rows.get(2).y() + 10, FAINT, false);
                AWScreenStyle.readout(graphics, font, rows.get(4).x(), rows.get(4).y() + 6, rows.get(4).width(),
                        AWLang.translate("gui.berth.anchor").component(), block.anchorName().isEmpty()
                                ? AWLang.translate("gui.berth.no_anchor").string() : block.anchorName(), TEXT);
                button(graphics, rows.get(5), AWLang.translate("gui.berth.relink").component(), true);
            }
        }

        Rect status = side.status();
        graphics.drawString(font, AWScreenStyle.trim(font, statusLine(berth), status.width()), status.x(),
                status.y() + 2, berth.state() == BerthState.MOORED ? AWScreenStyle.OK
                        : berth.state() == BerthState.EMPTY ? FAINT : AWScreenStyle.WARN, false);
        graphics.drawString(font, AWScreenStyle.trim(font, AWLang.translate("gui.berth.takes",
                Math.max(0, slipLength - 4), Math.max(0, slipWidth - 4), Math.max(0, slipHeight - 4)).string(), status.width()),
                status.x(), status.y() + 15, FAINT, false);
        button(graphics, side.save(), AWLang.translate("gui.berth.save").component(), true);
    }

    /** What is in the slip, in a line. */
    private String statusLine(Berth berth) {
        String ship = berth.shipName().isBlank() ? AWLang.translate("gui.berth.a_ship").string() : berth.shipName();
        return switch (berth.state()) {
            case EMPTY -> AWLang.translate(berth.castOff() ? "gui.berth.state.cast_off" : "gui.berth.state.empty", ship)
                    .string();
            case RESERVED -> AWLang.translate("gui.berth.state.reserved", ship).string();
            case DRAWING -> AWLang.translate("gui.berth.state.drawing", ship).string();
            case MOORED -> AWLang.translate("gui.berth.state.moored", ship).string();
        };
    }

    private static int lamp(BerthState state) {
        return switch (state) {
            case EMPTY -> 0xFF_2A1838;
            case RESERVED -> 0xFF_F0A83C;
            case DRAWING -> 0xFF_5C3E9A;
            case MOORED -> GLYPH;
        };
    }

    private void slider(GuiGraphics graphics, List<Rect> rows, Slider slider, String key) {
        Rect row = rows.get(rowOf(slider));
        graphics.drawString(font, AWLang.translate(key).component(), row.x(), row.y() + 1, FAINT, false);
        String value = shown(slider);
        graphics.drawString(font, value, row.right() - font.width(value), row.y() + 1, TEXT, false);
        Rect track = WardLayout.sliderBar(row);
        float fraction = BerthLayout.sliderFraction(value(slider), min(slider), max(slider));
        AWScreenStyle.bar(graphics, track.x(), track.y(), track.width(), track.height(), fraction, AWScreenStyle.ACCENT_DIM);
        int knob = track.x() + Math.round(track.width() * AWAnim.clamp(fraction));
        graphics.fill(knob - 2, track.y() - 2, knob + 2, track.bottom() + 2, AWScreenStyle.ACCENT);
    }

    private void button(GuiGraphics graphics, Rect rect, Component label, boolean active) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), active ? BRASS : BRASS_DARK);
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.y() + 1, active ? BRASS_LIGHT : BRASS);
        graphics.fill(rect.x(), rect.bottom() - 1, rect.right(), rect.bottom(), 0xFF_4A3820);
        String shown = AWScreenStyle.trim(font, label.getString(), rect.width() - 4);
        graphics.drawString(font, shown, rect.x() + (rect.width() - font.width(shown)) / 2,
                rect.y() + (rect.height() - 8) / 2, active ? INK : 0xFF_6A5A40, false);
    }

    /** A switch: a small lamp, lit or not, and what it switches. */
    private void toggle(GuiGraphics graphics, Rect row, Component label, boolean on) {
        int y = row.y() + (row.height() - 10) / 2;
        graphics.fill(row.x(), y, row.x() + 10, y + 10, BRASS_DARK);
        graphics.fill(row.x() + 1, y + 1, row.x() + 9, y + 9, on ? GLYPH : 0xFF_16121B);
        graphics.drawString(font, AWScreenStyle.trim(font, label.getString(), row.width() - 14), row.x() + 14, y + 1,
                on ? TEXT : FAINT, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
