package uk.co.iceconchy.aerowarptics.client.screen;

import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import uk.co.iceconchy.aerowarptics.client.PlotterCameraRig;
import uk.co.iceconchy.aerowarptics.client.screen.AWLayout.Rect;
import uk.co.iceconchy.aerowarptics.network.ServerboundConfigureWardPacket;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.ward.RiftWardBlockEntity;
import uk.co.iceconchy.aerowarptics.ward.WardMode;
import uk.co.iceconchy.aerowarptics.ward.WardRules;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkRules;

import java.util.ArrayList;
import java.util.List;

/**
 * A Rift Ward's panel: how wide its field is, whom it turns aside, whether it is on - set by looking at it.
 *
 * <p>A field is a circle on the ground up to a couple of hundred blocks across, and nobody standing at the machine
 * can see where it ends. So this is not a window over the game. It is a strip down the right of the screen
 * ({@link WardLayout}) with the world undimmed beside it, and for as long as it is open the camera leaves the
 * player's head and looks down on the ward from far enough off to take in the whole field - the Rift Plotter's
 * camera, {@link PlotterCameraRig}, borrowed whole. The rim of the field <em>as the slider has it</em> is drawn over
 * the ground ({@code WardEffects}); move the slider and the rim moves, and the camera draws back to keep it in
 * view. Drag over the world to look round, scroll to come closer.
 *
 * <p>Two tabs. <b>Field</b>: what the ward has banked and what the field costs at the radius being set, the
 * radius, and the switch. <b>Who</b>: the mode - turn everyone, everyone but some networks, or only some networks
 * - the networks it is about, typed by name, and whether the owner is turned too, which is how one player tries
 * their own ward.
 *
 * <p>Nothing is sent until Save, and the server checks all of it. The camera is always given back: closing the
 * panel any way at all goes through {@link #removed}.
 */
@OnlyIn(Dist.CLIENT)
public class RiftWardScreen extends AbstractSimiScreen {

    private enum Tab {
        FIELD("field"), WHO("who");

        final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    private static final int STONE = 0xE8_2A2336;
    private static final int PATINA_DARK = 0xFF_27554B;
    private static final int PATINA_LIGHT = 0xFF_70B698;
    private static final int BRASS_DARK = 0xFF_7A6038;
    private static final int BRASS = 0xFF_B08D57;
    private static final int BRASS_LIGHT = 0xFF_D8B068;
    private static final int TEXT = 0xFF_E6E1D6;
    private static final int FAINT = 0xFF_8E86A0;
    private static final int GLYPH = 0xFF_A060F0;
    private static final int INK = 0xFF_2A1C0C;

    /** How steeply the camera looks down on a field, in radians above the horizon, and which way round it starts. */
    private static final float CAMERA_PITCH = 1.0F;
    private static final float CAMERA_YAW = 0.6F;

    private final RiftWardBlockEntity ward;

    private WardLayout.Sidebar sidebar;
    private Tab tab = Tab.FIELD;
    private EditBox nameBox;

    private int radius;
    private boolean enabled;
    private boolean selfTest;
    private WardMode mode;
    private final List<String> names = new ArrayList<>();
    private int nameScroll;

    private boolean draggingRadius;
    private boolean orbiting;
    /** Once the player has zoomed for themselves, the camera stops drawing back to fit the slider. */
    private boolean zoomedByHand;
    private float distance;
    private boolean flown;

    public RiftWardScreen(RiftWardBlockEntity ward) {
        super(AWLang.translate("gui.rift_ward.title").component());
        this.ward = ward;
        this.radius = WardRules.clampRadius(ward.radius(), ward.maxRadius());
        this.enabled = ward.enabled();
        this.selfTest = ward.selfTest();
        this.mode = ward.mode();
        this.names.addAll(ward.networkNames());
    }

    /** The ward this panel is open on, for what draws its rim. */
    public BlockPos wardPos() {
        return ward.getBlockPos();
    }

    /** The radius as the slider has it now, saved or not. */
    public double draftRadius() {
        return radius;
    }

    @Override
    protected void init() {
        // Laid out against the whole screen: the strip sits at its edge and is never scaled.
        setWindowSize(width, height);
        super.init();
        String typed = nameBox == null ? "" : nameBox.getValue();
        clearWidgets();
        guiLeft = 0;
        guiTop = 0;
        sidebar = WardLayout.sidebar(width, height);
        nameBox = null;
        if (tab == Tab.WHO && mode.usesNetworks()) {
            Rect box = sidebar.nameBox();
            nameBox = new EditBox(font, box.x(), box.y(), box.width(), box.height(),
                    AWLang.translate("gui.rift_ward.network").component());
            nameBox.setMaxLength(NetworkRules.NAME_MAX);
            nameBox.setValue(typed);
            addRenderableWidget(nameBox);
        }
        if (!flown) {
            flown = true;
            distance = WardLayout.cameraDistance(radius);
            PlotterCameraRig.start(Vec3.atCenterOf(ward.getBlockPos()), CAMERA_YAW, CAMERA_PITCH, distance);
        }
    }

    /** Every way out of the panel comes through here, and the camera goes back to the player. */
    @Override
    public void removed() {
        PlotterCameraRig.stop();
        super.removed();
    }

    /** With the world in view, nothing is dimmed or blurred behind the strip: the field is set by looking at it. */
    @Override
    protected void renderMenuBackground(GuiGraphics graphics) {
    }

    @Override
    protected void renderWindowBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
    }

    @Override
    public void tick() {
        super.tick();
        if (ward.isRemoved()) {
            onClose();
        }
    }

    private int most() {
        return Math.max(WardRules.MIN_RADIUS, ward.maxRadius());
    }

    private void setRadius(int wanted) {
        int next = WardRules.clampRadius(wanted, most());
        if (next == radius) {
            return;
        }
        radius = next;
        if (!zoomedByHand) {
            // Drawn back, or brought in, to keep the rim in view.
            float wantedDistance = WardLayout.cameraDistance(radius);
            PlotterCameraRig.zoom(wantedDistance / distance);
            distance = wantedDistance;
        }
    }

    private void showTab(Tab next) {
        if (tab != next) {
            tab = next;
            click(1.0F);
            rebuildWidgets();
        }
    }

    private void addName() {
        if (nameBox == null) {
            return;
        }
        String name = NetworkRules.clean(nameBox.getValue());
        if (name.isEmpty() || names.size() >= WardRules.MOST_NETWORKS) {
            return;
        }
        for (String already : names) {
            if (NetworkRules.key(already).equals(NetworkRules.key(name))) {
                nameBox.setValue("");
                return;
            }
        }
        names.add(name);
        nameBox.setValue("");
        nameScroll = WardLayout.clampScroll(names.size(), names.size());
        click(1.1F);
    }

    private void save() {
        // A name still in the box was meant: the Add button is easy to miss on the way to Save.
        addName();
        PacketDistributor.sendToServer(new ServerboundConfigureWardPacket(ward.getBlockPos(), radius, mode,
                List.copyOf(names), enabled, selfTest));
    }

    private void click(float pitch) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
        }
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
        if (tab == Tab.FIELD) {
            if (rows.get(3).contains(x, y)) {
                draggingRadius = true;
                dragRadius(x);
                return true;
            }
            if (rows.get(4).contains(x, y)) {
                enabled = !enabled;
                click(enabled ? 1.2F : 0.8F);
                return true;
            }
            return true;
        }
        if (rows.get(0).contains(x, y)) {
            mode = mode.next();
            click(1.0F);
            rebuildWidgets();
            return true;
        }
        if (rows.get(5).contains(x, y)) {
            selfTest = !selfTest;
            click(selfTest ? 1.2F : 0.8F);
            return true;
        }
        if (mode.usesNetworks()) {
            if (sidebar.add().contains(x, y)) {
                addName();
                return true;
            }
            for (int shown = 0; shown < WardLayout.NAMES_SHOWN; shown++) {
                int index = shown + nameScroll;
                if (index < names.size() && sidebar.strike(shown).contains(x, y)) {
                    names.remove(index);
                    nameScroll = WardLayout.clampScroll(nameScroll, names.size());
                    click(0.8F);
                    return true;
                }
            }
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
        if (draggingRadius) {
            dragRadius(x);
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
        boolean ours = draggingRadius || orbiting;
        draggingRadius = false;
        orbiting = false;
        return ours || super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (sidebar == null || scrollY == 0.0D) {
            return super.mouseScrolled(x, y, scrollX, scrollY);
        }
        if (!sidebar.frame().contains(x, y)) {
            zoomedByHand = true;
            float factor = scrollY > 0 ? 0.87F : 1.15F;
            PlotterCameraRig.zoom(factor);
            distance = Math.max(OrbitCamera.MIN_DISTANCE, Math.min(OrbitCamera.MAX_DISTANCE, distance * factor));
            return true;
        }
        if (tab == Tab.FIELD && sidebar.rows().get(3).contains(x, y)) {
            // A block at a time under the wheel: the slider is a block and a half to the pixel at its widest.
            setRadius(radius + (int) Math.signum(scrollY));
            return true;
        }
        if (tab == Tab.WHO && sidebar.names().contains(x, y)) {
            nameScroll = WardLayout.clampScroll(nameScroll - (int) Math.signum(scrollY), names.size());
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (nameBox != null && nameBox.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            addName();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void dragRadius(double mouseX) {
        Rect track = WardLayout.sliderBar(sidebar.rows().get(3));
        double fraction = Math.max(0.0D, Math.min(1.0D, (mouseX - track.x()) / Math.max(1, track.width())));
        setRadius((int) Math.round(WardRules.MIN_RADIUS + fraction * (most() - WardRules.MIN_RADIUS)));
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        WardLayout.Sidebar side = sidebar;
        if (side == null) {
            return;
        }
        Rect frame = side.frame();
        int x = frame.x();
        int w = frame.width();
        int h = frame.height();
        graphics.fill(x, 0, x + w, h, STONE);
        graphics.fill(x, 0, x + 2, h, PATINA_DARK);
        graphics.fill(x, 0, x + 1, h, PATINA_LIGHT);
        // The strip's seam is lit as the machine is: dark off, low while the field stands, dull while it is down.
        float lit = !ward.enabled() ? 0.0F : ward.standing() ? 0.75F : 0.25F;
        graphics.fill(x + 4, 4, x + 5, h - 4, AWAnim.blend(0xFF_2A1838, GLYPH, lit));

        graphics.drawString(font, AWLang.translate("gui.rift_ward.title").component(), side.header().x(),
                side.header().y() + 2, BRASS_LIGHT, false);
        button(graphics, side.close(), AWLang.translate("gui.rift_ward.close").component(), true);
        for (int i = 0; i < side.tabs().size(); i++) {
            Rect rect = side.tabs().get(i);
            boolean on = Tab.values()[i] == tab;
            graphics.fill(rect.x(), rect.y(), rect.right() - 1, rect.bottom(), on ? BRASS : BRASS_DARK);
            graphics.fill(rect.x(), rect.y(), rect.right() - 1, rect.y() + 1, on ? BRASS_LIGHT : BRASS);
            Component label = AWLang.translate("gui.rift_ward.tab." + Tab.values()[i].key).component();
            graphics.drawString(font, label, rect.x() + (rect.width() - font.width(label)) / 2, rect.y() + 3,
                    on ? INK : 0xFF_E8D8B0, false);
        }
        if (tab == Tab.FIELD) {
            drawField(graphics, side.rows());
        } else {
            drawWho(graphics, side);
        }

        Rect status = side.status();
        Component state = AWLang.translate(ward.stateKey()).component();
        graphics.drawString(font, state, status.x(), status.y() + 2,
                ward.standing() ? AWScreenStyle.OK : ward.enabled() ? AWScreenStyle.WARN : FAINT, false);
        graphics.drawString(font, AWScreenStyle.trim(font, AWLang.translate("gui.rift_ward.turned",
                String.format("%,d", ward.turned())).string(), status.width()), status.x(), status.y() + 15, FAINT, false);
        button(graphics, side.save(), AWLang.translate("gui.rift_ward.save").component(), true);
    }

    /** What the ward has banked and what the field costs at the radius being set; the radius; the switch. */
    private void drawField(GuiGraphics graphics, List<Rect> rows) {
        Rect charge = rows.get(0);
        AWScreenStyle.readout(graphics, font, charge.x(), charge.y() + 1, charge.width(),
                // What is held only: the bar under it is the share of what the ward can bank, and both figures do not fit.
                AWLang.translate("gui.rift_ward.charge").component(), AWLang.essence(ward.charge()), TEXT);
        AWScreenStyle.bar(graphics, charge.x(), charge.y() + 13, charge.width(), 5, (float) ward.charged(),
                ward.standing() ? AWScreenStyle.ACCENT : AWScreenStyle.ACCENT_DIM);

        Rect tank = rows.get(1);
        AWScreenStyle.readout(graphics, font, tank.x(), tank.y() + 6, tank.width(),
                AWLang.translate("gui.rift_ward.tank").component(),
                AWLang.essence(ward.contents().getAmount(), RiftWardBlockEntity.TANK_CAPACITY), TEXT);

        Rect upkeep = rows.get(2);
        AWScreenStyle.readout(graphics, font, upkeep.x(), upkeep.y() + 6, upkeep.width(),
                AWLang.translate("gui.rift_ward.upkeep").component(),
                AWLang.translate("gui.rift_ward.per_minute", String.format("%,d", Math.round(draftUpkeep()))).string(), TEXT);

        Rect row = rows.get(3);
        graphics.drawString(font, AWLang.translate("gui.rift_ward.radius").component(), row.x(), row.y() + 1, FAINT, false);
        String value = AWLang.distance(radius);
        graphics.drawString(font, value, row.right() - font.width(value), row.y() + 1, TEXT, false);
        Rect track = WardLayout.sliderBar(row);
        float fraction = most() <= WardRules.MIN_RADIUS ? 0.0F
                : (radius - WardRules.MIN_RADIUS) / (float) (most() - WardRules.MIN_RADIUS);
        AWScreenStyle.bar(graphics, track.x(), track.y(), track.width(), track.height(), fraction, AWScreenStyle.ACCENT_DIM);
        int knob = track.x() + Math.round(track.width() * AWAnim.clamp(fraction));
        graphics.fill(knob - 2, track.y() - 2, knob + 2, track.bottom() + 2, AWScreenStyle.ACCENT);

        toggle(graphics, rows.get(4), AWLang.translate(enabled ? "gui.rift_ward.on" : "gui.rift_ward.off").component(),
                enabled);
        for (int line = 0; line < 2; line++) {
            graphics.drawString(font, AWLang.translate("gui.rift_ward.hint." + line).component(), rows.get(5).x(),
                    rows.get(5).y() + 1 + line * 10, FAINT, false);
        }
    }

    /** The mode, the networks it is about, and whether the owner is turned too. */
    private void drawWho(GuiGraphics graphics, WardLayout.Sidebar side) {
        List<Rect> rows = side.rows();
        button(graphics, rows.get(0), AWLang.translate(mode.translationKey()).component(), true);
        Rect list = side.names();
        if (!mode.usesNetworks()) {
            graphics.drawString(font, AWLang.translate("gui.rift_ward.everyone").component(), rows.get(1).x(),
                    rows.get(1).y() + 6, FAINT, false);
        } else {
            button(graphics, side.add(), AWLang.translate("gui.rift_ward.add").component(),
                    names.size() < WardRules.MOST_NETWORKS);
            graphics.fill(list.x(), list.y(), list.right(), list.bottom(), 0xFF_16121B);
            AWScreenStyle.frame(graphics, list.x(), list.y(), list.width(), list.height(), BRASS_DARK);
            if (names.isEmpty()) {
                graphics.drawString(font, AWLang.translate("gui.rift_ward.none_named").component(), list.x() + 4,
                        list.y() + 5, FAINT, false);
            }
            for (int shown = 0; shown < WardLayout.NAMES_SHOWN; shown++) {
                int index = shown + nameScroll;
                if (index >= names.size()) {
                    break;
                }
                Rect line = side.name(shown);
                Rect strike = side.strike(shown);
                graphics.drawString(font, AWScreenStyle.trim(font, names.get(index), line.width() - strike.width() - 6),
                        line.x() + 3, line.y() + 2, TEXT, false);
                graphics.drawString(font, "x", strike.x() + 4, strike.y() + 1, AWScreenStyle.BAD, false);
            }
            if (names.size() > WardLayout.NAMES_SHOWN) {
                AWScreenStyle.scrollbar(graphics, list.right() - 1, list.y() + 2,
                        WardLayout.NAMES_SHOWN * WardLayout.NAME_HEIGHT, names.size(), WardLayout.NAMES_SHOWN, nameScroll);
            }
        }
        toggle(graphics, rows.get(5), AWLang.translate("gui.rift_ward.self_test").component(), selfTest);
    }

    /**
     * What the field would cost a minute at the radius the slider has, worked from what it costs at the radius the
     * server last counted: upkeep goes by area, and a client never reads the server's rate.
     */
    private double draftUpkeep() {
        double set = Math.max(1.0D, ward.radius());
        return ward.upkeepPerMinute() * (radius * (double) radius) / (set * set);
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
