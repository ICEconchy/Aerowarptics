package uk.co.iceconchy.aerowarptics.client.screen;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.platform.GlConst;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import uk.co.iceconchy.aerowarptics.client.PlotterCameraRig;
import uk.co.iceconchy.aerowarptics.client.PlotterPreview;
import uk.co.iceconchy.aerowarptics.client.PlotterSurveyCache;
import uk.co.iceconchy.aerowarptics.client.fx.PlotterHologram;
import uk.co.iceconchy.aerowarptics.client.screen.AWLayout.Rect;
import uk.co.iceconchy.aerowarptics.network.ClientboundPlotterPanelPacket;
import uk.co.iceconchy.aerowarptics.network.ServerboundPlotterPacket;
import uk.co.iceconchy.aerowarptics.plotter.Firing;
import uk.co.iceconchy.aerowarptics.plotter.Plot;
import uk.co.iceconchy.aerowarptics.plotter.PlotCost;
import uk.co.iceconchy.aerowarptics.plotter.PlotFailure;
import uk.co.iceconchy.aerowarptics.plotter.PlotGeometry;
import uk.co.iceconchy.aerowarptics.plotter.PlotterState;
import uk.co.iceconchy.aerowarptics.plotter.PlotterWorks;
import uk.co.iceconchy.aerowarptics.probe.ProbeBearing;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

/**
 * The Rift Plotter's table: a model of the ground round the target, turned in the hands, and the plot laid on it.
 *
 * <p>The model is the survey the server sent, built into a solid of columns ({@link PlotterHologram}) and drawn in
 * three dimensions inside the slab - dragged to turn it, scrolled to come closer. It rises out of the table when it
 * arrives, column by column, as the chart's scan line sweeps a map in. The target burns on it as a beam; the exit is
 * a ring hung over the ground where the far rift will open, an arrow the way it faces, and a line of fire to the
 * target. Everything a player sets moves those marks as they set it, so the plot is read off the ground, not off
 * the numbers.
 *
 * <p>Three tabs down the right. <b>Target</b>: the coordinates, typed, and the survey that loads the ground there;
 * a click on the model moves the target to the block clicked. <b>Exit</b>: how far out, on what bearing, how high,
 * whether it faces the target or is aimed off it, and how fast things come out; a click on the model puts the exit
 * over the ground clicked, and its ring can be dragged.
 *
 * <p><b>Entry</b> is set in the world, not on the slab. The table folds away into a sidebar, the camera leaves the
 * player and circles the machine ({@link PlotterCameraRig}), and a hologram of the entry stands where it will tear -
 * its rim, the way through it, the beam out to it from the ball, and the dock behind it - moving as the sliders do,
 * while the dish swings round to point at it. The sidebar has four tabs: where the rift is thrown and how wide; which
 * way it faces, which is straight out from the machine until it is turned; the dock helper, how far out and how far
 * back it catches, and whether a docked vehicle fires the plotter; and the other ways to set it off, with how long a
 * strike stands.
 *
 * <p>Engage sends the plot and asks the server, which decides exactly as a redstone pulse would. The camera then
 * sweeps down behind the exit and looks along the line of fire, which is the whole of what the plot is for.
 */
@OnlyIn(Dist.CLIENT)
public class RiftPlotterScreen extends AWWindowScreen {

    private static final PlotterLayout.Panel LAYOUT = PlotterLayout.panel();

    private static final int STONE = 0xFF_2A2336;
    private static final int MORTAR = 0xFF_1B1524;
    private static final int WELL = 0xFF_0E0A14;
    private static final int PATINA_DARK = 0xFF_27554B;
    private static final int PATINA = 0xFF_43866F;
    private static final int PATINA_LIGHT = 0xFF_70B698;
    private static final int BRONZE = 0xFF_7E5C34;
    private static final int BRONZE_LIGHT = 0xFF_B4884F;
    private static final int BRASS_DARK = 0xFF_7A6038;
    private static final int BRASS = 0xFF_B08D57;
    private static final int BRASS_LIGHT = 0xFF_D8B068;
    private static final int TEXT = 0xFF_E6E1D6;
    private static final int LABEL = 0xFF_9A8FB0;
    private static final int GOOD = 0xFF_49D9C4;
    private static final int WARN = 0xFF_E0B04A;
    private static final int BAD = 0xFF_D95C4A;
    private static final int GLYPH = 0xFF_A060F0;

    /** Ticks between asking the server how the machine stands. */
    private static final int REFRESH_INTERVAL = 10;

    /** Ticks a dragged plot waits before it is sent, so a drag is a few packets and not one a frame. */
    private static final int SEND_INTERVAL = 4;

    /** Ticks the model takes to rise out of the table. */
    private static final int RISE_TICKS = 26;

    /** Ticks the Engage sweep holds behind the exit before flying back out. */
    private static final int SWEEP_TICKS = 70;

    /** Pixels a press may wander and still be a click rather than a drag. */
    private static final double CLICK_SLOP = 3.0D;

    private enum Tab {
        TARGET("target"), EXIT("exit"), ENTRY("entry");

        final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    /** The sidebar's tabs, while the entry is set in the world: {@link PlotterLayout#SIDEBAR_TABS} of them. */
    private enum SideTab {
        RIFT("rift"), FACING("facing"), DOCK("dock"), FIRE("fire");

        final String key;

        SideTab(String key) {
            this.key = key;
        }
    }

    /** Which set of controls a slider or a switch belongs to. */
    private enum Page {
        TARGET, EXIT, RIFT, FACING, DOCK, FIRE
    }

    private ClientboundPlotterPanelPacket data;
    private Plot draft;
    private Firing firingDraft;
    /** Whether the table has folded into the sidebar, with the world in view, for setting the entry. */
    private boolean field;
    private SideTab sideTab = SideTab.RIFT;
    @Nullable
    private PlotterLayout.Sidebar sidebar;
    /** Whether a drag over the world is turning the camera. */
    private boolean orbiting;
    private boolean dirty;
    private int sinceSent;
    /**
     * Ticks the plot on the table is kept over whatever the server says, after sending it. An answer to a refresh
     * asked before the change went is still on its way, and taken it would snap the sliders back for a moment.
     */
    private int trustDraft;
    private int refreshTimer = REFRESH_INTERVAL;
    private int ticksOpen;
    private Tab tab = Tab.TARGET;

    @Nullable
    private EditBox xBox;
    @Nullable
    private EditBox zBox;

    private final OrbitCamera camera = new OrbitCamera(0.6F, 0.8F, 180.0F);
    private boolean cameraPlaced;
    private int knownArrivals = -1;
    private int riseTicks = RISE_TICKS;
    private boolean surveying;
    private int surveyTicks;
    private int sweepTicks = -1;
    private int flashTicks = 1000;
    private int lastCarried;

    private boolean pressedInView;
    private boolean draggingExit;
    private double pressX;
    private double pressY;
    private boolean movedSincePress;
    @Nullable
    private Slider dragging;
    @Nullable
    private HologramPick.Hit hover;

    private final List<Slider> sliders = new ArrayList<>();
    private final List<Toggle> toggles = new ArrayList<>();

    public RiftPlotterScreen(ClientboundPlotterPanelPacket data) {
        super(AWLang.translate("gui.rift_plotter.title").component());
        this.data = data;
        this.draft = data.plot();
        this.firingDraft = data.firing();
        this.lastCarried = data.carried();
        if (!data.plot().set()) {
            tab = Tab.TARGET;
        }
    }

    public boolean matches(BlockPos pos) {
        return data.pos().equals(pos);
    }

    /** The machine as the server now has it. The plot on the table is kept while the player is changing it. */
    public void accept(ClientboundPlotterPanelPacket packet) {
        this.data = packet;
        if (packet.carried() > lastCarried) {
            flashTicks = 0;
        }
        lastCarried = packet.carried();
        if (!dirty && trustDraft <= 0 && dragging == null && !draggingExit && !typing()) {
            draft = packet.plot();
            firingDraft = packet.firing();
            fillBoxes();
        }
    }

    /** How deep a dock nobody has sized reaches, as the server last said - for the hologram of it in the world. */
    public double dockReach() {
        return data.dockReach();
    }

    /** The page of controls on show. */
    private Page page() {
        if (field) {
            return switch (sideTab) {
                case RIFT -> Page.RIFT;
                case FACING -> Page.FACING;
                case DOCK -> Page.DOCK;
                case FIRE -> Page.FIRE;
            };
        }
        return tab == Tab.EXIT ? Page.EXIT : Page.TARGET;
    }

    private boolean busy() {
        return data.plotterState().busy();
    }

    private boolean typing() {
        return xBox != null && xBox.isFocused() || zBox != null && zBox.isFocused();
    }

    @Nullable
    private PlotterHologram hologram() {
        return PlotterSurveyCache.table(data.pos());
    }

    // ------------------------------------------------------------------ setup

    @Override
    protected void init() {
        if (field) {
            initField();
            return;
        }
        setWindowSize(PlotterLayout.WIDTH, PlotterLayout.HEIGHT);
        super.init();
        placeWindow(PlotterLayout.WIDTH, PlotterLayout.HEIGHT);
        clearWidgets();
        sidebar = null;
        Rect row = LAYOUT.rows().get(0);
        int half = (row.width() - 6) / 2;
        xBox = new EditBox(font, guiLeft + row.x() + 10, guiTop + row.y() + 6, half - 10, 12,
                AWLang.translate("gui.rift_plotter.x").component());
        zBox = new EditBox(font, guiLeft + row.x() + half + 16, guiTop + row.y() + 6, half - 10, 12,
                AWLang.translate("gui.rift_plotter.z").component());
        for (EditBox box : List.of(xBox, zBox)) {
            box.setMaxLength(9);
            box.setFilter(text -> text.matches("-?\\d{0,8}"));
            box.setTextColor(TEXT);
            addRenderableWidget(box);
        }
        fillBoxes();
        showTab(tab);
        buildSliders();
        // A plot with a target the table has no model of: ask for one, as the probe asks for its reading.
        PlotterHologram hologram = hologram();
        if (data.plot().set() && (hologram == null || hologram.survey().centreX() != data.plot().targetX()
                || hologram.survey().centreZ() != data.plot().targetZ())) {
            requestSurvey(draft);
        }
    }

    /**
     * The sidebar: laid out against the whole screen, so the window's own placement is set aside - it sits at the
     * screen's corner and is never scaled, and every rect in the sidebar is already where it is drawn.
     */
    private void initField() {
        setWindowSize(width, height);
        super.init();
        clearWidgets();
        guiLeft = 0;
        guiTop = 0;
        fitScale = 1.0F;
        xBox = null;
        zBox = null;
        sidebar = PlotterLayout.sidebar(width, height);
        buildSliders();
    }

    /** Folds the table away and takes the camera out to the entry. */
    private void enterField() {
        field = true;
        dragging = null;
        pressedInView = false;
        Vec3 centre = PlotGeometry.input(data.pos(), draft).centre();
        Vec3 behind = PlotGeometry.lens(data.pos()).subtract(centre);
        // Round behind the machine and off to one side, looking past it at the entry: the plotter in the foreground,
        // the rift it will tear beyond it.
        float yaw = (float) Math.atan2(behind.x, behind.z) + 0.55F;
        float distance = (float) Math.max(14.0D, behind.length() * 1.3D + draft.radius() * 2.5D);
        PlotterCameraRig.start(centre, yaw, 0.42F, distance);
        PlotterPreview.show(data.pos(), draft, firingDraft);
        click(1.1F);
        rebuildWidgets();
    }

    /** Back to the slab, and the camera back to the player. */
    private void leaveField() {
        field = false;
        orbiting = false;
        dragging = null;
        PlotterCameraRig.stop();
        PlotterPreview.hide();
        tab = Tab.EXIT;
        click(0.9F);
        rebuildWidgets();
    }

    @Override
    public void removed() {
        PlotterCameraRig.stop();
        PlotterPreview.hide();
        super.removed();
    }

    /** With the world in view, nothing is dimmed or blurred behind the sidebar: the entry is set by looking at it. */
    @Override
    protected void renderMenuBackground(GuiGraphics graphics) {
        if (!field) {
            super.renderMenuBackground(graphics);
        }
    }

    @Override
    protected void renderWindowBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!field) {
            super.renderWindowBackground(graphics, mouseX, mouseY, partialTicks);
        }
    }

    private void fillBoxes() {
        if (xBox == null || zBox == null || typing()) {
            return;
        }
        xBox.setValue(draft.set() ? Integer.toString(draft.targetX()) : "");
        zBox.setValue(draft.set() ? Integer.toString(draft.targetZ()) : "");
    }

    private void showTab(Tab next) {
        if (next == Tab.ENTRY) {
            enterField();
            return;
        }
        tab = next;
        if (xBox != null && zBox != null) {
            xBox.visible = next == Tab.TARGET;
            zBox.visible = next == Tab.TARGET;
            if (next != Tab.TARGET) {
                xBox.setFocused(false);
                zBox.setFocused(false);
            }
        }
    }

    /** Every slider and switch on show, in its row. The drafts are what they read and write. */
    private void buildSliders() {
        sliders.clear();
        toggles.clear();
        if (field && sidebar != null) {
            buildSidebar(sidebar.rows());
            return;
        }
        List<Rect> rows = LAYOUT.rows();
        double surveyReach = Math.max(16, data.surveyRadius() - 4);
        sliders.add(new Slider(Page.EXIT, rows.get(0), "gui.rift_plotter.distance", 0.0D, Math.min(Plot.MAX_DISTANCE, surveyReach), 1.0D,
                () -> draft.distance(), value -> edit(draft.withExit(value, draft.bearing(), draft.height())),
                value -> Math.round(value) + " m", () -> true));
        sliders.add(new Slider(Page.EXIT, rows.get(1), "gui.rift_plotter.bearing", 0.0D, 359.0D, 1.0D,
                () -> draft.bearing(), value -> edit(draft.withExit(draft.distance(), value, draft.height())),
                value -> Math.round(value) + "\u00b0 " + compass(value), () -> true));
        sliders.add(new Slider(Page.EXIT, rows.get(2), "gui.rift_plotter.height", 0.0D, 160.0D, 1.0D,
                () -> draft.height(), value -> edit(draft.withExit(draft.distance(), draft.bearing(), value)),
                value -> Math.round(value) + " m", () -> true));
        sliders.add(new Slider(Page.EXIT, rows.get(4), "gui.rift_plotter.aim_yaw", -90.0D, 90.0D, 1.0D,
                () -> draft.aimYaw(), value -> edit(draft.withAim(false, value, draft.aimPitch())),
                value -> signed(value) + "\u00b0", () -> !draft.aimed()));
        sliders.add(new Slider(Page.EXIT, rows.get(5), "gui.rift_plotter.aim_pitch", -Plot.MAX_AIM_PITCH, Plot.MAX_AIM_PITCH, 1.0D,
                () -> draft.aimPitch(), value -> edit(draft.withAim(false, draft.aimYaw(), value)),
                value -> (value > 0.5D ? AWLang.translate("gui.rift_plotter.dive", Math.round(value)).string()
                        : value < -0.5D ? AWLang.translate("gui.rift_plotter.climb", Math.round(-value)).string()
                        : AWLang.translate("gui.rift_plotter.level").string()), () -> !draft.aimed()));
        // Exit speed: its first notch keeps whatever speed a thing went in with; nought is a delivery, out at rest.
        sliders.add(new Slider(Page.EXIT, rows.get(6), "gui.rift_plotter.exit_speed", Firing.KEEP_SPEED,
                Math.max(1.0D, data.maxExitSpeed()), 1.0D, () -> firingDraft.exitSpeed(),
                value -> editFiring(firingDraft.withExitSpeed(value < 0.0D ? Firing.KEEP_SPEED : value)),
                value -> value < 0.0D ? AWLang.translate("gui.rift_plotter.keep_speed").string()
                        : value < 0.5D ? AWLang.translate("gui.rift_plotter.at_rest").string()
                        : Math.round(value) + " m/s", () -> true, true));
    }

    /** The sidebar's four tabs: where the rift is, which way it faces, its dock, and what sets it off. */
    private void buildSidebar(List<Rect> rows) {
        sliders.add(new Slider(Page.RIFT, rows.get(0), "gui.rift_plotter.heading", 0.0D, 359.0D, 1.0D,
                () -> compassOfYaw(draft.inputYaw()),
                value -> edit(draft.withInput(yawOfCompass(value), draft.inputPitch(), draft.inputReach(), draft.radius())),
                value -> Math.round(value) + "\u00b0 " + compass(value), () -> true));
        sliders.add(new Slider(Page.RIFT, rows.get(1), "gui.rift_plotter.tilt", -90.0D, 90.0D, 1.0D,
                () -> -draft.inputPitch(),
                value -> edit(draft.withInput(draft.inputYaw(), -value, draft.inputReach(), draft.radius())),
                value -> (value > 80.0D ? AWLang.translate("gui.rift_plotter.skyward").string()
                        : value < -80.0D ? AWLang.translate("gui.rift_plotter.groundward").string()
                        : signed(value) + "\u00b0"), () -> true));
        sliders.add(new Slider(Page.RIFT, rows.get(2), "gui.rift_plotter.reach", Plot.MIN_REACH, Plot.MAX_REACH, 1.0D,
                () -> draft.inputReach(),
                value -> edit(draft.withInput(draft.inputYaw(), draft.inputPitch(), value, draft.radius())),
                value -> Math.round(value) + " m", () -> true));
        sliders.add(new Slider(Page.RIFT, rows.get(3), "gui.rift_plotter.radius", Plot.MIN_RADIUS,
                Math.max(Plot.MIN_RADIUS + 0.5D, data.maxRadius()), 0.5D, () -> draft.radius(),
                value -> edit(draft.withInput(draft.inputYaw(), draft.inputPitch(), draft.inputReach(), value)),
                value -> String.format("%.1f m", value), () -> true));

        // Which way it faces. Switched to face straight out, the two sliders show where that is and are not live;
        // switched off, it starts from exactly where it stood, so letting go of the machine's line moves nothing.
        toggles.add(new Toggle(Page.FACING, rows.get(0), "gui.rift_plotter.face_out", () -> !draft.inputTurned(),
                on -> edit(on ? draft.withFacing(false, draft.faceYaw(), draft.facePitch())
                        : draft.withFacing(true, draft.inputYaw(), draft.inputPitch())), () -> !busy()));
        sliders.add(new Slider(Page.FACING, rows.get(1), "gui.rift_plotter.face_yaw", 0.0D, 359.0D, 1.0D,
                () -> compassOfYaw(draft.facingYaw()),
                value -> edit(draft.withFacing(true, yawOfCompass(value), draft.facingPitch())),
                value -> Math.round(value) + "\u00b0 " + compass(value), () -> draft.inputTurned()));
        sliders.add(new Slider(Page.FACING, rows.get(2), "gui.rift_plotter.face_pitch", -90.0D, 90.0D, 1.0D,
                () -> -draft.facingPitch(),
                value -> edit(draft.withFacing(true, draft.facingYaw(), -value)),
                value -> (value > 0.5D ? AWLang.translate("gui.rift_plotter.climb", Math.round(value)).string()
                        : value < -0.5D ? AWLang.translate("gui.rift_plotter.dive", Math.round(-value)).string()
                        : AWLang.translate("gui.rift_plotter.level").string()), () -> draft.inputTurned()));

        // The dock. Each of its two measurements has a notch below its smallest that hands it back to the machine,
        // as the exit speed's first notch hands the speed back to whatever went in.
        toggles.add(new Toggle(Page.DOCK, rows.get(0), "gui.rift_plotter.dock", () -> firingDraft.dock(),
                on -> editFiring(firingDraft.withDock(on)), () -> true));
        sliders.add(new Slider(Page.DOCK, rows.get(1), "gui.rift_plotter.dock_radius", Firing.MIN_DOCK_RADIUS - 1.0D,
                Math.max(Firing.MIN_DOCK_RADIUS + 1.0D, data.maxDockRadius()), 1.0D,
                () -> firingDraft.autoDockRadius() ? Firing.MIN_DOCK_RADIUS - 1.0D : firingDraft.dockRadius(),
                value -> editFiring(firingDraft.withDockSize(value < Firing.MIN_DOCK_RADIUS ? Firing.AUTO : value,
                        firingDraft.dockDepth())),
                value -> value < Firing.MIN_DOCK_RADIUS ? AWLang.translate("gui.rift_plotter.dock_auto",
                        Math.round(firingDraft.dockRadius(draft.radius()))).string() : Math.round(value) + " m",
                () -> firingDraft.dock(), true));
        sliders.add(new Slider(Page.DOCK, rows.get(2), "gui.rift_plotter.dock_depth", Firing.MIN_DOCK_DEPTH - 1.0D,
                Math.max(Firing.MIN_DOCK_DEPTH + 1.0D, data.maxDockDepth()), 1.0D,
                () -> firingDraft.autoDockDepth() ? Firing.MIN_DOCK_DEPTH - 1.0D : firingDraft.dockDepth(),
                value -> editFiring(firingDraft.withDockSize(firingDraft.dockRadius(),
                        value < Firing.MIN_DOCK_DEPTH ? Firing.AUTO : value)),
                value -> value < Firing.MIN_DOCK_DEPTH ? AWLang.translate("gui.rift_plotter.dock_auto",
                        Math.round(firingDraft.dockDepth(data.dockReach()))).string() : Math.round(value) + " m",
                () -> firingDraft.dock(), true));
        toggles.add(new Toggle(Page.DOCK, rows.get(3), "gui.rift_plotter.fire_on_dock", () -> firingDraft.fireOnDock(),
                on -> editFiring(firingDraft.withFireOnDock(on)), () -> firingDraft.dock()));
        sliders.add(new Slider(Page.DOCK, rows.get(4), "gui.rift_plotter.dock_delay", 0.0D, Firing.MAX_DOCK_DELAY, 1.0D,
                () -> firingDraft.dockDelay(), value -> editFiring(firingDraft.withDockDelay((int) Math.round(value))),
                value -> Math.round(value) + " s", () -> firingDraft.dock() && firingDraft.fireOnDock(), true));

        toggles.add(new Toggle(Page.FIRE, rows.get(0), "gui.rift_plotter.fire_on_redstone",
                () -> firingDraft.fireOnRedstone(), on -> editFiring(firingDraft.withFireOnRedstone(on)), () -> true));
        toggles.add(new Toggle(Page.FIRE, rows.get(1), "gui.rift_plotter.lane", () -> firingDraft.lane(),
                on -> editFiring(firingDraft.withLane(on)), () -> true));
        sliders.add(new Slider(Page.FIRE, rows.get(2), "gui.rift_plotter.window", 1.0D, Math.max(2, data.maxWindow()), 1.0D,
                () -> firingDraft.window(), value -> editFiring(firingDraft.withWindow((int) Math.round(value))),
                value -> Math.round(value) + " s", () -> !firingDraft.lane(), true));
    }

    // ------------------------------------------------------------------ editing

    /** A change to the plot at the table: kept here at once, sent a moment later. */
    private void edit(Plot next) {
        if (busy()) {
            return;
        }
        draft = next.clamped(Math.max(Plot.MIN_RADIUS, data.maxRadius()));
        dirty = true;
    }

    /**
     * A change to how it fires. Taken even while a strike is under way - an exit speed or a dock switched off is wanted
     * at once - where the plot itself waits for rest.
     */
    private void editFiring(Firing next) {
        firingDraft = next.clamped(Math.max(1, data.maxWindow()), data.maxExitSpeed(), data.maxDockRadius(),
                data.maxDockDepth());
        dirty = true;
    }

    private void send(ServerboundPlotterPacket.Action action) {
        PacketDistributor.sendToServer(new ServerboundPlotterPacket(data.pos(), action, draft, firingDraft));
        dirty = false;
        sinceSent = 0;
        trustDraft = REFRESH_INTERVAL + 4;
    }

    private void requestSurvey(Plot plot) {
        draft = plot;
        PacketDistributor.sendToServer(new ServerboundPlotterPacket(data.pos(), ServerboundPlotterPacket.Action.SURVEY, plot,
                firingDraft));
        dirty = false;
        trustDraft = REFRESH_INTERVAL + 4;
        surveying = true;
        surveyTicks = 0;
    }

    /** The coordinates typed in, as a new target - or {@code null} while they are not both numbers. */
    @Nullable
    private Plot typedTarget() {
        if (xBox == null || zBox == null) {
            return null;
        }
        try {
            int x = Integer.parseInt(xBox.getValue());
            int z = Integer.parseInt(zBox.getValue());
            return draft.withTarget(x, draft.targetY(), z);
        } catch (NumberFormatException notYet) {
            return null;
        }
    }

    private void survey() {
        Plot typed = typedTarget();
        if (typed == null || busy()) {
            click(0.6F);
            return;
        }
        if (xBox != null) {
            xBox.setFocused(false);
        }
        if (zBox != null) {
            zBox.setFocused(false);
        }
        requestSurvey(typed);
        click(1.1F);
    }

    private void engage() {
        if (busy()) {
            send(ServerboundPlotterPacket.Action.STAND_DOWN);
            click(0.8F);
            return;
        }
        send(ServerboundPlotterPacket.Action.ENGAGE);
        click(1.3F);
        PlotterHologram hologram = hologram();
        if (!field && hologram != null && draft.set()) {
            // Down behind the exit, looking along the line of fire at the target.
            int targetY = hologram.groundAt(draft.targetX(), draft.targetZ(), draft.targetY());
            Vec3 target = hologram.toModel(PlotGeometry.target(draft.withTargetY(targetY)));
            Vec3 exit = hologram.toModel(PlotGeometry.exitCentre(PlotGeometry.target(draft.withTargetY(targetY)), draft));
            Vec3 back = exit.subtract(target);
            double length = Math.max(1.0D, back.length());
            float yaw = (float) Math.atan2(back.x, back.z);
            float pitch = (float) Math.asin(Mth.clamp(back.y / length, -1.0D, 1.0D));
            camera.flyTo(yaw, Math.max(OrbitCamera.MIN_PITCH, pitch + 0.12F),
                    (float) (length + Math.max(18.0D, draft.radius() * 4.0D)), (float) target.x, (float) target.y,
                    (float) target.z);
            sweepTicks = 0;
        }
    }

    private void overview(PlotterHologram hologram) {
        int targetY = hologram.groundAt(draft.targetX(), draft.targetZ(), draft.targetY());
        Vec3 target = hologram.toModel(PlotGeometry.target(draft.withTargetY(targetY)));
        camera.flyTo(camera.yaw(1.0F), 0.78F, hologram.radius() * 2.3F, (float) target.x, (float) target.y,
                (float) target.z);
    }

    private void click(float pitch) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.25F, pitch);
        }
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        ticksOpen++;
        camera.tick();
        if (riseTicks < RISE_TICKS) {
            riseTicks++;
        }
        if (flashTicks < 1000) {
            flashTicks++;
        }
        if (trustDraft > 0) {
            trustDraft--;
        }
        if (surveying && ++surveyTicks > 20 * 30) {
            surveying = false;
        }
        int arrivals = PlotterSurveyCache.arrivals();
        PlotterHologram hologram = hologram();
        if (arrivals != knownArrivals) {
            knownArrivals = arrivals;
            if (hologram != null) {
                surveying = false;
                riseTicks = 0;
                if (!cameraPlaced) {
                    cameraPlaced = true;
                    camera.flyTo(0.6F, 0.9F, hologram.radius() * 2.6F, 0.0F, 0.0F, 0.0F);
                }
                overview(hologram);
            }
        }
        if (sweepTicks >= 0 && ++sweepTicks > SWEEP_TICKS) {
            sweepTicks = -1;
            if (hologram != null) {
                overview(hologram);
            }
        }
        if (field) {
            PlotterCameraRig.aim(PlotGeometry.input(data.pos(), draft).centre());
            PlotterPreview.show(data.pos(), draft, firingDraft);
        }
        if (dirty && ++sinceSent >= SEND_INTERVAL && dragging == null && !draggingExit) {
            send(ServerboundPlotterPacket.Action.SET);
        }
        if (--refreshTimer <= 0) {
            refreshTimer = REFRESH_INTERVAL;
            PacketDistributor.sendToServer(ServerboundPlotterPacket.refresh(data.pos()));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (typing()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                survey();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                xBox.setFocused(false);
                zBox.setFocused(false);
                return true;
            }
            EditBox focused = xBox.isFocused() ? xBox : zBox;
            // Any key the box takes is the box's - never the inventory key closing the table on an 'e'.
            return focused.keyPressed(key, scanCode, modifiers) || focused.canConsumeInput()
                    || super.keyPressed(key, scanCode, modifiers);
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (field) {
            return fieldClicked(mouseX, mouseY, button);
        }
        double x = unscaleX(mouseX);
        double y = unscaleY(mouseY);
        double lx = x - guiLeft;
        double ly = y - guiTop;

        // A click on a coordinate box is the box's, which focuses it; a click anywhere else lets go of both, or
        // the next key pressed over the model would still be typed into one.
        if (tab == Tab.TARGET && xBox != null && zBox != null && (xBox.isMouseOver(x, y) || zBox.isMouseOver(x, y))) {
            return super.mouseClicked(x, y, button);
        }
        if (xBox != null && zBox != null) {
            xBox.setFocused(false);
            zBox.setFocused(false);
            setFocused(null);
        }

        for (int i = 0; i < LAYOUT.tabButtons().size(); i++) {
            if (LAYOUT.tabButtons().get(i).contains(lx, ly)) {
                showTab(Tab.values()[i]);
                click(1.0F);
                return true;
            }
        }
        if (LAYOUT.engage().contains(lx, ly)) {
            engage();
            return true;
        }
        if (tab == Tab.TARGET && surveyButton().contains(lx, ly)) {
            survey();
            return true;
        }
        if (tab == Tab.EXIT && aimToggle().contains(lx, ly) && !busy()) {
            edit(draft.withAim(!draft.aimed(), draft.aimYaw(), draft.aimPitch()));
            click(draft.aimed() ? 1.2F : 0.9F);
            return true;
        }
        for (Slider slider : sliders) {
            if (slider.page == page() && slider.live() && slider.hit().contains(lx, ly)) {
                dragging = slider;
                slider.drag(lx);
                return true;
            }
        }
        if (LAYOUT.view().contains(lx, ly)) {
            pressedInView = true;
            pressX = x;
            pressY = y;
            movedSincePress = false;
            draggingExit = button == 0 && tab == Tab.EXIT && !busy() && nearExitHandle(x, y);
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        double x = unscaleX(mouseX);
        double y = unscaleY(mouseY);
        if (dragging != null) {
            dragging.drag(x - guiLeft);
            return true;
        }
        if (field && orbiting) {
            PlotterCameraRig.turn((float) (-dragX * 0.012D), (float) (dragY * 0.01D));
            return true;
        }
        if (pressedInView) {
            if (Math.abs(x - pressX) > CLICK_SLOP || Math.abs(y - pressY) > CLICK_SLOP) {
                movedSincePress = true;
            }
            if (draggingExit) {
                HologramPick.Hit hit = pick(x, y);
                if (hit != null) {
                    exitOver(hit);
                }
            } else if (movedSincePress) {
                camera.turn((float) (-dragX / fitScale * 0.012D), (float) (dragY / fitScale * 0.01D));
            }
            return true;
        }
        return super.mouseDragged(x, y, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        double x = unscaleX(mouseX);
        double y = unscaleY(mouseY);
        if (dragging != null) {
            dragging = null;
            send(ServerboundPlotterPacket.Action.SET);
            click(0.9F);
            return true;
        }
        if (orbiting) {
            orbiting = false;
            return true;
        }
        if (pressedInView) {
            pressedInView = false;
            if (draggingExit) {
                draggingExit = false;
                send(ServerboundPlotterPacket.Action.SET);
                return true;
            }
            if (!movedSincePress && button == 0 && !busy()) {
                HologramPick.Hit hit = pick(x, y);
                PlotterHologram hologram = hologram();
                if (hit != null && hologram != null) {
                    if (tab == Tab.TARGET) {
                        edit(draft.withTarget(hologram.worldX(hit.x()), hologram.worldY(hit.x(), hit.z()),
                                hologram.worldZ(hit.z())));
                        fillBoxesAnyway();
                        click(1.2F);
                    } else if (tab == Tab.EXIT) {
                        exitOver(hit);
                        click(1.2F);
                    }
                }
            }
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double x = unscaleX(mouseX) - guiLeft;
        double y = unscaleY(mouseY) - guiTop;
        if (field && sidebar != null && !sidebar.frame().contains(x, y) && scrollY != 0.0D) {
            PlotterCameraRig.zoom(scrollY > 0 ? 0.87F : 1.15F);
            return true;
        }
        if (LAYOUT.view().contains(x, y) && scrollY != 0.0D) {
            if (hasShiftDown() && tab == Tab.EXIT && !busy()) {
                edit(draft.withExit(draft.distance(), draft.bearing(), draft.height() + Math.signum(scrollY) * 2.0D));
            } else {
                camera.zoom(scrollY > 0 ? 0.87F : 1.15F);
            }
            return true;
        }
        for (Slider slider : sliders) {
            if (slider.page == page() && slider.live() && slider.row.contains(x, y) && scrollY != 0.0D) {
                slider.nudge(Math.signum(scrollY));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);
        hover = field ? null : pick(unscaleX(mouseX), unscaleY(mouseY));
    }

    /** A click with the table folded into the sidebar: its controls, or - anywhere over the world - a camera drag. */
    private boolean fieldClicked(double x, double y, int button) {
        if (sidebar == null) {
            return false;
        }
        if (sidebar.back().contains(x, y)) {
            leaveField();
            return true;
        }
        for (int i = 0; i < sidebar.tabs().size(); i++) {
            if (sidebar.tabs().get(i).contains(x, y)) {
                sideTab = SideTab.values()[i];
                click(1.0F);
                return true;
            }
        }
        if (sidebar.engage().contains(x, y)) {
            engage();
            return true;
        }
        for (Toggle toggle : toggles) {
            if (toggle.page == page() && toggle.enabled() && toggle.row.contains(x, y)) {
                toggle.flip();
                click(toggle.get() ? 1.2F : 0.9F);
                return true;
            }
        }
        for (Slider slider : sliders) {
            if (slider.page == page() && slider.live() && slider.hit().contains(x, y)) {
                dragging = slider;
                slider.drag(x);
                return true;
            }
        }
        if (!sidebar.frame().contains(x, y)) {
            orbiting = true;
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    private void fillBoxesAnyway() {
        if (xBox != null && zBox != null) {
            xBox.setValue(Integer.toString(draft.targetX()));
            zBox.setValue(Integer.toString(draft.targetZ()));
        }
    }

    /** Puts the exit over a point on the model: distance and bearing from the target, in the plot's terms. */
    private void exitOver(HologramPick.Hit hit) {
        PlotterHologram hologram = hologram();
        if (hologram == null) {
            return;
        }
        int targetY = hologram.groundAt(draft.targetX(), draft.targetZ(), draft.targetY());
        Vec3 target = hologram.toModel(PlotGeometry.target(draft.withTargetY(targetY)));
        double dx = hit.px() - target.x;
        double dz = hit.pz() - target.z;
        double distance = Math.min(Plot.MAX_DISTANCE, Math.hypot(dx, dz));
        double bearing = Plot.wrapBearing(Math.toDegrees(Math.atan2(dx, -dz)));
        edit(draft.withExit(distance, bearing, draft.height()));
    }

    // ------------------------------------------------------------------ the view's projection

    private Matrix4f projection() {
        Rect view = LAYOUT.view();
        PlotterHologram hologram = hologram();
        float far = hologram == null ? 1024.0F : hologram.radius() * 12.0F + 600.0F;
        return OrbitCamera.projection(view.width() / (float) view.height(), far);
    }

    /** The model under a pointer, or {@code null}. */
    @Nullable
    private HologramPick.Hit pick(double x, double y) {
        PlotterHologram hologram = hologram();
        Rect view = LAYOUT.view();
        double lx = x - guiLeft - view.x();
        double ly = y - guiTop - view.y();
        if (hologram == null || lx < 0 || ly < 0 || lx >= view.width() || ly >= view.height()) {
            return null;
        }
        float ndcX = (float) (lx / view.width() * 2.0D - 1.0D);
        float ndcY = (float) (1.0D - ly / view.height() * 2.0D);
        double[] ray = OrbitCamera.ray(camera.view(1.0F), projection(), ndcX, ndcY);
        return HologramPick.pick(new double[]{ray[0], ray[1], ray[2]}, new double[]{ray[3], ray[4], ray[5]},
                hologram.size(), hologram::top);
    }

    /** Where a model point falls in the window, or {@code null} behind the camera. */
    @Nullable
    private float[] onScreen(Vec3 model, float partialTick) {
        Rect view = LAYOUT.view();
        Vector4f clip = new Vector4f((float) model.x, (float) model.y, (float) model.z, 1.0F)
                .mul(new Matrix4f(projection()).mul(camera.view(partialTick)));
        if (clip.w <= 0.01F) {
            return null;
        }
        float nx = clip.x / clip.w;
        float ny = clip.y / clip.w;
        return new float[]{guiLeft + view.x() + (nx + 1.0F) * 0.5F * view.width(),
                guiTop + view.y() + (1.0F - ny) * 0.5F * view.height()};
    }

    private boolean nearExitHandle(double x, double y) {
        PlotterHologram hologram = hologram();
        if (hologram == null || !draft.set()) {
            return false;
        }
        int targetY = hologram.groundAt(draft.targetX(), draft.targetZ(), draft.targetY());
        Vec3 exit = hologram.toModel(PlotGeometry.exitCentre(PlotGeometry.target(draft.withTargetY(targetY)), draft));
        double ground = hologram.groundUnder(exit.x, exit.z);
        for (Vec3 handle : List.of(exit, new Vec3(exit.x, ground, exit.z))) {
            float[] at = onScreen(handle, 1.0F);
            if (at != null && Math.hypot(at[0] - x, at[1] - y) < 10.0D) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (field) {
            drawSidebar(graphics);
            return;
        }
        frame(graphics);
        view(graphics, partialTicks);
        tabs(graphics);
        if (tab == Tab.EXIT) {
            exitTab(graphics);
        } else {
            targetTab(graphics);
        }
        for (Slider slider : sliders) {
            if (slider.page == page()) {
                slider.draw(graphics);
            }
        }
        footer(graphics, mouseX, mouseY);
    }

    private float time(float partialTicks) {
        return ticksOpen + partialTicks;
    }

    /** The slab: verdigris frame, rift-stone within, brass at the corners, the header. */
    private void frame(GuiGraphics graphics) {
        int x = guiLeft;
        int y = guiTop;
        int w = PlotterLayout.WIDTH;
        int h = PlotterLayout.HEIGHT;
        graphics.fill(x, y, x + w, y + h, PATINA_DARK);
        graphics.fill(x, y, x + w, y + 1, PATINA_LIGHT);
        graphics.fill(x, y, x + 1, y + h, PATINA_LIGHT);
        graphics.fill(x + 1, y + 1, x + w - 1, y + 3, PATINA);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFF_132A25);
        graphics.fill(x + w - 1, y, x + w, y + h, 0xFF_132A25);
        graphics.fill(x + 4, y + 4, x + w - 4, y + h - 4, STONE);
        for (int row = 0; y + 4 + row * 12 < y + h - 4; row++) {
            int top = y + 4 + row * 12;
            graphics.fill(x + 4, top, x + w - 4, top + 1, MORTAR);
            for (int joint = (row % 2) * 12; joint < w - 8; joint += 24) {
                graphics.fill(x + 4 + joint, top, x + 5 + joint, Math.min(top + 12, y + h - 4), MORTAR);
            }
        }
        int glow = AWAnim.blend(0xFF_2A1838, GLYPH, busy() ? 0.6F + 0.4F * AWAnim.pulse(ticksOpen, 20.0F) : 0.25F);
        graphics.fill(x + 6, y + 6, x + w - 6, y + 7, glow);
        graphics.fill(x + 6, y + h - 7, x + w - 6, y + h - 6, glow);
        graphics.fill(x + 6, y + 6, x + 7, y + h - 6, glow);
        graphics.fill(x + w - 7, y + 6, x + w - 6, y + h - 6, glow);
        for (int[] corner : new int[][]{{x, y}, {x + w - 12, y}, {x, y + h - 12}, {x + w - 12, y + h - 12}}) {
            graphics.fill(corner[0], corner[1], corner[0] + 12, corner[1] + 12, BRASS);
            graphics.fill(corner[0], corner[1], corner[0] + 12, corner[1] + 1, BRASS_LIGHT);
            graphics.fill(corner[0], corner[1] + 11, corner[0] + 12, corner[1] + 12, BRASS_DARK);
            graphics.fill(corner[0] + 2, corner[1] + 2, corner[0] + 4, corner[1] + 4, BRASS_DARK);
            graphics.fill(corner[0] + 8, corner[1] + 8, corner[0] + 10, corner[1] + 10, BRASS_DARK);
        }
        Rect header = LAYOUT.header();
        graphics.drawString(font, title, x + header.x() + 4, y + header.y() + 2, BRASS_LIGHT, false);
        Component state = AWLang.translate(data.plotterState().translationKey()).component();
        int colour = switch (data.plotterState()) {
            case OPEN -> AWAnim.blend(GLYPH, TEXT, AWAnim.pulse(ticksOpen, 10.0F));
            case CHARGING, REACHING -> WARN;
            case CLOSING -> LABEL;
            case IDLE -> data.enabled() ? LABEL : BAD;
        };
        String shown = AWScreenStyle.trim(font, state.getString(), header.width() - 16 - font.width(title));
        graphics.drawString(font, shown, x + header.right() - 4 - font.width(shown), y + header.y() + 2, colour, false);
    }

    private void inset(GuiGraphics graphics, Rect rect) {
        int x = guiLeft + rect.x();
        int y = guiTop + rect.y();
        graphics.fill(x - 1, y - 1, x + rect.width() + 1, y + rect.height() + 1, BRONZE);
        graphics.fill(x - 1, y - 1, x + rect.width() + 1, y, BRONZE_LIGHT);
        graphics.fill(x, y, x + rect.width(), y + rect.height(), WELL);
    }

    /** The model of the ground, in three dimensions, and what is written over it. */
    private void view(GuiGraphics graphics, float partialTicks) {
        Rect view = LAYOUT.view();
        inset(graphics, view);
        int left = guiLeft + view.x();
        int top = guiTop + view.y();
        // A faint survey grid under everything, so an empty table still reads as a table.
        for (int gx = 0; gx < view.width(); gx += 12) {
            graphics.fill(left + gx, top, left + gx + 1, top + view.height(), 0x18_70B698);
        }
        for (int gy = 0; gy < view.height(); gy += 12) {
            graphics.fill(left, top + gy, left + view.width(), top + gy + 1, 0x18_70B698);
        }
        PlotterHologram hologram = hologram();
        if (hologram == null) {
            String key = surveying ? "gui.rift_plotter.surveying" : draft.set() ? "gui.rift_plotter.no_survey"
                    : "gui.rift_plotter.no_target";
            Component message = AWLang.translate(key).component();
            int colour = surveying ? AWAnim.blend(LABEL, GOOD, AWAnim.pulse(ticksOpen, 16.0F)) : LABEL;
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(message, view.width() - 20);
            int y = top + view.height() / 2 - lines.size() * 5;
            for (net.minecraft.util.FormattedCharSequence line : lines) {
                graphics.drawString(font, line, left + (view.width() - font.width(line)) / 2, y, colour, false);
                y += 10;
            }
            if (surveying) {
                scan(graphics, view, partialTicks);
            }
            return;
        }
        draw3d(graphics, hologram, partialTicks);
        compassLetters(graphics, hologram, partialTicks);
        if (surveying) {
            scan(graphics, view, partialTicks);
        }
        if (riseTicks < RISE_TICKS) {
            // The scan line that raises the model, sweeping down the view as it rises.
            int line = top + Math.round(view.height() * AWAnim.easeOut((riseTicks + partialTicks) / RISE_TICKS));
            graphics.fill(left, Math.min(top + view.height() - 1, line), left + view.width(),
                    Math.min(top + view.height(), line + 1), 0xC0_70B698);
        }
        if (hover != null && !busy()) {
            String where = hologram.worldX(hover.x()) + ", " + hologram.worldY(hover.x(), hover.z()) + ", "
                    + hologram.worldZ(hover.z());
            graphics.drawString(font, where, left + 4, top + view.height() - 11, 0xC0_E6E1D6, false);
        }
        if (sweepTicks >= 0) {
            float flash = 1.0F - Math.min(1.0F, sweepTicks / 20.0F);
            if (flash > 0.0F) {
                graphics.fill(left, top, left + view.width(), top + view.height(),
                        AWAnim.fade(0xFF_E0305A, flash * 0.25F));
            }
            Component plotted = AWLang.translate("gui.rift_plotter.plotted").component();
            graphics.drawString(font, plotted, left + (view.width() - font.width(plotted)) / 2, top + 6,
                    AWAnim.fade(BRASS_LIGHT, Math.min(1.0F, (SWEEP_TICKS - sweepTicks) / 20.0F)), false);
        }
        if (flashTicks < 12) {
            graphics.fill(left, top, left + view.width(), top + view.height(),
                    AWAnim.fade(0xFF_FFD0E0, (12 - flashTicks) / 12.0F * 0.3F));
        }
    }

    /** A sweep across the view while the ground is loading, which is all there is to show. */
    private void scan(GuiGraphics graphics, Rect view, float partialTicks) {
        int left = guiLeft + view.x();
        int top = guiTop + view.y();
        int at = Math.round(AWAnim.sweep(time(partialTicks), 40.0F) * view.width());
        for (int i = 0; i < 16; i++) {
            int x = left + Math.floorMod(at - i, view.width());
            graphics.fill(x, top, x + 1, top + view.height(), AWAnim.fade(0xFF_49D9C4, 0.35F * (1.0F - i / 16.0F)));
        }
    }

    /** The model itself, drawn in its own viewport with its own depth, then the plot's marks over it. */
    private void draw3d(GuiGraphics graphics, PlotterHologram hologram, float partialTicks) {
        graphics.flush();
        Rect view = LAYOUT.view();
        Window window = Minecraft.getInstance().getWindow();
        double scale = window.getGuiScale() * fitScale;
        int px = (int) Math.round((guiLeft + view.x()) * scale);
        int pw = (int) Math.round(view.width() * scale);
        int ph = (int) Math.round(view.height() * scale);
        int py = window.getHeight() - (int) Math.round((guiTop + view.y() + view.height()) * scale);
        if (pw <= 0 || ph <= 0) {
            return;
        }
        Matrix4f projection = projection();
        Matrix4f cameraView = camera.view(partialTicks);
        float rise = AWAnim.easeOut(Math.min(1.0F, (riseTicks + partialTicks) / RISE_TICKS));

        RenderSystem.viewport(px, py, pw, ph);
        RenderSystem.enableScissor(px, py, pw, ph);
        RenderSystem.clear(GlConst.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        stack.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            hologram.draw(cameraView, projection, 1.0F, 1.0F, 1.0F, 1.0F, rise);
            if (draft.set()) {
                int targetY = hologram.groundAt(draft.targetX(), draft.targetZ(), draft.targetY());
                double pulse = switch (data.plotterState()) {
                    case OPEN -> 0.6D + 0.4D * Math.sin(time(partialTicks) * 0.4D);
                    case CHARGING -> PlotCost.progress(data.charge(), data.cost());
                    case REACHING -> 1.0D;
                    default -> 0.25D + 0.15D * Math.sin(time(partialTicks) * 0.15D);
                };
                if (sweepTicks >= 0) {
                    pulse = 1.0D;
                }
                RenderSystem.disableDepthTest();
                hologram.marks(cameraView, draft, targetY, pulse, 0xE0305A, tab == Tab.EXIT, draft.distance());
            }
        } finally {
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            stack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            // The GUI draws over this with depth of its own; leave it none of the model's to fail against.
            RenderSystem.clear(GlConst.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.disableScissor();
            RenderSystem.viewport(0, 0, window.getWidth(), window.getHeight());
        }
    }

    /** North, east, south and west, at the edges of the model, turning with it. */
    private void compassLetters(GuiGraphics graphics, PlotterHologram hologram, float partialTicks) {
        double edge = hologram.radius() + 4.0D;
        Object[][] points = {{"N", new Vec3(0.5D, 0.0D, -edge)}, {"E", new Vec3(edge, 0.0D, 0.5D)},
                {"S", new Vec3(0.5D, 0.0D, edge)}, {"W", new Vec3(-edge, 0.0D, 0.5D)}};
        Rect view = LAYOUT.view();
        for (Object[] point : points) {
            float[] at = onScreen((Vec3) point[1], partialTicks);
            if (at == null) {
                continue;
            }
            String letter = (String) point[0];
            int x = Math.round(at[0]) - font.width(letter) / 2;
            int y = Math.round(at[1]) - 4;
            if (x > guiLeft + view.x() && x < guiLeft + view.right() - 6 && y > guiTop + view.y()
                    && y < guiTop + view.bottom() - 8) {
                graphics.drawString(font, letter, x, y, letter.equals("N") ? BAD : LABEL, true);
            }
        }
    }

    private void tabs(GuiGraphics graphics) {
        for (int i = 0; i < LAYOUT.tabButtons().size(); i++) {
            Rect rect = LAYOUT.tabButtons().get(i);
            Tab which = Tab.values()[i];
            boolean on = which == tab;
            int x = guiLeft + rect.x();
            int y = guiTop + rect.y();
            graphics.fill(x, y, x + rect.width() - 1, y + rect.height(), on ? BRASS : BRASS_DARK);
            graphics.fill(x, y, x + rect.width() - 1, y + 1, on ? BRASS_LIGHT : BRASS);
            Component label = AWLang.translate("gui.rift_plotter.tab." + which.key).component();
            graphics.drawString(font, label, x + (rect.width() - font.width(label)) / 2, y + 3,
                    on ? 0xFF_2A1C0C : 0xFF_E8D8B0, false);
        }
    }

    private Rect surveyButton() {
        Rect row = LAYOUT.rows().get(1);
        return new Rect(row.x(), row.y() + 2, 56, 16);
    }

    private Rect aimToggle() {
        Rect row = LAYOUT.rows().get(3);
        return new Rect(row.x(), row.y() + 3, row.width(), 14);
    }

    private void targetTab(GuiGraphics graphics) {
        Rect row0 = LAYOUT.rows().get(0);
        graphics.drawString(font, "X", guiLeft + row0.x(), guiTop + row0.y() + 8, LABEL, false);
        int half = (row0.width() - 6) / 2;
        graphics.drawString(font, "Z", guiLeft + row0.x() + half + 6, guiTop + row0.y() + 8, LABEL, false);

        Rect button = surveyButton();
        boolean ready = typedTarget() != null && !busy();
        brassButton(graphics, button, AWLang.translate(surveying ? "gui.rift_plotter.surveying_short"
                : "gui.rift_plotter.survey").component(), ready);
        PlotterHologram hologram = hologram();
        if (hologram != null) {
            // The percent sign travels in the argument: a translation's own % is a format code.
            String coverage = AWLang.translate("gui.rift_plotter.coverage",
                    Math.round(hologram.survey().coverage() * 100.0F) + "%").string();
            graphics.drawString(font, AWScreenStyle.trim(font, coverage, LAYOUT.rows().get(1).right() - button.right() - 6),
                    guiLeft + button.right() + 6, guiTop + button.y() + 4, LABEL, false);
        }
        int y = guiTop + LAYOUT.rows().get(2).y() + 4;
        if (draft.set()) {
            int ground = hologram == null ? draft.targetY() : hologram.groundAt(draft.targetX(), draft.targetZ(), draft.targetY());
            y = readout(graphics, y, "gui.rift_plotter.ground", "y " + ground, TEXT);
            Vec3 lens = PlotGeometry.lens(data.pos());
            Vec3 target = PlotGeometry.target(draft.withTargetY(ground));
            double dx = target.x - lens.x;
            double dz = target.z - lens.z;
            double compass = Plot.wrapBearing(Math.toDegrees(Math.atan2(dx, -dz)));
            y = readout(graphics, y, "gui.rift_plotter.away", AWLang.distance(Math.hypot(dx, dz)) + " " + compass(compass),
                    TEXT);
        }
        hint(graphics, y + 4, tab == Tab.TARGET ? "gui.rift_plotter.hint.target" : "");
    }

    private void exitTab(GuiGraphics graphics) {
        Rect toggle = aimToggle();
        int x = guiLeft + toggle.x();
        int y = guiTop + toggle.y();
        graphics.fill(x, y + 2, x + 9, y + 11, BRONZE);
        graphics.fill(x + 1, y + 3, x + 8, y + 10, WELL);
        if (draft.aimed()) {
            graphics.fill(x + 2, y + 4, x + 7, y + 9, GOOD);
        }
        graphics.drawString(font, AWScreenStyle.trim(font, AWLang.translate("gui.rift_plotter.aimed").string(),
                toggle.width() - 13), x + 13, y + 3, draft.aimed() ? TEXT : LABEL, false);
    }

    /**
     * The sidebar: rift-stone down the right of the screen, the world undimmed beside it. Its back button and tabs,
     * the controls of the tab on show, then what the plot costs and what the machine is doing, and the fire button.
     */
    private void drawSidebar(GuiGraphics graphics) {
        PlotterLayout.Sidebar side = sidebar;
        if (side == null) {
            return;
        }
        Rect frame = side.frame();
        int x = frame.x();
        int w = frame.width();
        int h = frame.height();
        graphics.fill(x, 0, x + w, h, 0xE8_2A2336);
        graphics.fill(x, 0, x + 2, h, PATINA_DARK);
        graphics.fill(x, 0, x + 1, h, PATINA_LIGHT);
        int glow = AWAnim.blend(0xFF_2A1838, GLYPH, busy() ? 0.6F + 0.4F * AWAnim.pulse(ticksOpen, 20.0F) : 0.25F);
        graphics.fill(x + 4, 4, x + 5, h - 4, glow);
        graphics.drawString(font, AWLang.translate("gui.rift_plotter.entry_title").component(), side.header().x(),
                side.header().y() + 2, BRASS_LIGHT, false);
        brassButton(graphics, side.back(), AWLang.translate("gui.rift_plotter.back").component(), true);
        for (int i = 0; i < side.tabs().size(); i++) {
            Rect rect = side.tabs().get(i);
            SideTab which = SideTab.values()[i];
            boolean on = which == sideTab;
            graphics.fill(rect.x(), rect.y(), rect.right() - 1, rect.bottom(), on ? BRASS : BRASS_DARK);
            graphics.fill(rect.x(), rect.y(), rect.right() - 1, rect.y() + 1, on ? BRASS_LIGHT : BRASS);
            Component label = AWLang.translate("gui.rift_plotter.side." + which.key).component();
            graphics.drawString(font, label, rect.x() + (rect.width() - font.width(label)) / 2, rect.y() + 3,
                    on ? 0xFF_2A1C0C : 0xFF_E8D8B0, false);
        }
        for (Slider slider : sliders) {
            if (slider.page == page()) {
                slider.draw(graphics);
            }
        }
        for (Toggle toggle : toggles) {
            if (toggle.page == page()) {
                toggle.draw(graphics);
            }
        }
        if (sideTab == SideTab.RIFT) {
            Rect row = side.rows().get(4);
            int y = row.y() + 2;
            y = AWScreenStyle.readout(graphics, font, row.x(), y, row.width(),
                    AWLang.translate("gui.rift_plotter.fits").component(), AWLang.translate("gui.rift_plotter.fits_value",
                            Math.round(draft.radius() * Math.sqrt(2.0D) * 10.0D) / 10.0D).string(), TEXT);
            AWScreenStyle.readout(graphics, font, row.x(), y, row.width(),
                    AWLang.translate("gui.rift_plotter.carries").component(), AWLang.translate(data.carriesEntities()
                            ? "gui.rift_plotter.carries_all" : "gui.rift_plotter.carries_vehicles").string(), TEXT);
        }
        // What it costs and what it is doing, over the fire button.
        Rect status = side.status();
        String price = firingDraft.lane()
                ? AWLang.translate("gui.rift_plotter.side.lane_cost", AWLang.count(previewPrice()),
                AWLang.count(data.upkeep())).string()
                : AWLang.translate("gui.rift_plotter.side.cost", AWLang.count(previewPrice())).string();
        graphics.drawString(font, AWScreenStyle.trim(font, price, status.width()), status.x(), status.y(), TEXT, false);
        String dock = !firingDraft.dock() ? "" : AWLang.translate(!data.dockHolding() ? "gui.rift_plotter.dock_empty"
                : data.dockLinedUp() ? "gui.rift_plotter.dock_lined_up" : "gui.rift_plotter.dock_holding").string();
        graphics.drawString(font, AWScreenStyle.trim(font, dock, status.width()), status.x(), status.y() + 10,
                data.dockLinedUp() ? GOOD : LABEL, false);
        graphics.drawString(font, AWScreenStyle.trim(font, sideStatus().getString(), status.width()), status.x(),
                status.y() + 20, statusColour(), false);
        boolean canFire = busy() || dirty || !data.previewFailure().isFailure();
        brassButton(graphics, side.engage(), AWLang.translate(busy() ? "gui.rift_plotter.stand_down"
                : "gui.rift_plotter.engage").component(), canFire);
        // A hint along the bottom of the world side, since the world is the control - wrapped to the world side, so
        // on a narrow screen it goes onto a second line rather than under the sidebar.
        List<net.minecraft.util.FormattedCharSequence> hint = font.split(
                AWLang.translate("gui.rift_plotter.hint.field").component(), Math.max(60, x - 16));
        int hy = h - 6 - hint.size() * 10;
        for (net.minecraft.util.FormattedCharSequence line : hint) {
            graphics.drawString(font, line, Math.max(4, (x - font.width(line)) / 2), hy, 0xC0_E6E1D6, true);
            hy += 10;
        }
    }

    /** What the plot as it is being set would cost, priced here from the numbers the server sent. */
    private long previewPrice() {
        PlotterHologram hologram = hologram();
        int ground = hologram == null ? draft.targetY() : hologram.groundAt(draft.targetX(), draft.targetZ(), draft.targetY());
        Plot grounded = draft.withTargetY(ground);
        double reach = PlotGeometry.lens(data.pos()).distanceTo(PlotGeometry.exitCentre(PlotGeometry.target(grounded), grounded));
        return PlotCost.essence(reach, draft.radius(), data.baseCost(), data.perKm(), data.perRadius());
    }

    private int readout(GuiGraphics graphics, int y, String key, String value, int colour) {
        Rect row = LAYOUT.rows().get(0);
        return AWScreenStyle.readout(graphics, font, guiLeft + row.x(), y, row.width(),
                AWLang.translate(key).component(), value, colour);
    }

    private void hint(GuiGraphics graphics, int y, String key) {
        if (key.isEmpty()) {
            return;
        }
        Rect row = LAYOUT.rows().get(0);
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(AWLang.translate(key).component(), row.width());
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            if (y > guiTop + LAYOUT.view().bottom() - 9) {
                break;
            }
            graphics.drawString(font, line, guiLeft + row.x(), y, 0xFF_6E6680, false);
            y += 9;
        }
    }

    private void brassButton(GuiGraphics graphics, Rect rect, Component label, boolean active) {
        int x = guiLeft + rect.x();
        int y = guiTop + rect.y();
        graphics.fill(x, y, x + rect.width(), y + rect.height(), active ? BRASS : BRASS_DARK);
        graphics.fill(x, y, x + rect.width(), y + 1, active ? BRASS_LIGHT : BRASS);
        graphics.fill(x, y + rect.height() - 1, x + rect.width(), y + rect.height(), 0xFF_4A3820);
        String shown = AWScreenStyle.trim(font, label.getString(), rect.width() - 4);
        graphics.drawString(font, shown, x + (rect.width() - font.width(shown)) / 2, y + (rect.height() - 8) / 2,
                active ? 0xFF_2A1C0C : 0xFF_6A5A40, false);
    }

    /** What the plot costs, what the machine holds, what it is doing, and the button that fires it. */
    private void footer(GuiGraphics graphics, int mouseX, int mouseY) {
        Rect readouts = LAYOUT.readouts();
        int left = guiLeft + readouts.x();
        int top = guiTop + readouts.y();
        PlotterHologram hologram = hologram();
        int ground = hologram == null ? draft.targetY() : hologram.groundAt(draft.targetX(), draft.targetZ(), draft.targetY());
        Plot grounded = draft.withTargetY(ground);
        Vec3 lens = PlotGeometry.lens(data.pos());
        Vec3 exit = PlotGeometry.exitCentre(PlotGeometry.target(grounded), grounded);
        double reach = lens.distanceTo(exit);
        long price = PlotCost.essence(reach, draft.radius(), data.baseCost(), data.perKm(), data.perRadius());
        double compass = Plot.wrapBearing(Math.toDegrees(Math.atan2(exit.x - lens.x, -(exit.z - lens.z))));

        String first = draft.set()
                ? AWLang.translate("gui.rift_plotter.far_end", AWLang.distance(reach), compass(compass),
                String.format("%,d", price)).string()
                : AWLang.translate("gui.rift_plotter.no_target_short").string();
        graphics.drawString(font, AWScreenStyle.trim(font, first, footerWidth()), left, top + 2, TEXT, false);

        // The tank, and over it the charge: gold as it fills towards the price.
        int barY = top + 13;
        int width = footerWidth();
        float tank = data.capacity() <= 0 ? 0.0F : data.essence() / (float) data.capacity();
        graphics.fill(left, barY, left + width, barY + 5, 0xFF_140F1C);
        graphics.fill(left, barY, left + Math.round(width * tank), barY + 5, 0xFF_6B3AA8);
        if (data.plotterState() == PlotterState.CHARGING) {
            float charged = PlotCost.progress(data.charge(), data.cost());
            graphics.fill(left, barY + 1, left + Math.round(width * charged), barY + 4,
                    AWAnim.blend(WARN, BRASS_LIGHT, AWAnim.pulse(ticksOpen, 8.0F)));
        }
        String held = AWLang.translate("gui.rift_plotter.tank", String.format("%,d", data.essence()),
                String.format("%,d", data.capacity()), String.format("%,d", data.charge())).string();
        graphics.drawString(font, AWScreenStyle.trim(font, held, width), left, barY + 7, LABEL, false);

        Component status = status();
        int statusColour = statusColour();
        graphics.drawString(font, AWScreenStyle.trim(font, status.getString(), width), left, barY + 17, statusColour,
                false);

        Rect engage = LAYOUT.engage();
        // While a change is on its way the preview is of the old plot, so it is not held against the new one.
        boolean canFire = busy() || dirty || !data.previewFailure().isFailure();
        Component label = AWLang.translate(busy() ? "gui.rift_plotter.stand_down" : "gui.rift_plotter.engage").component();
        brassButton(graphics, engage, label, canFire);
        if (!busy() && canFire) {
            int x = guiLeft + engage.x();
            int y = guiTop + engage.y();
            int glow = AWAnim.fade(0xFF_E0305A, 0.4F + 0.4F * AWAnim.pulse(ticksOpen, 18.0F));
            graphics.fill(x - 1, y - 1, x + engage.width() + 1, y, glow);
            graphics.fill(x - 1, y + engage.height(), x + engage.width() + 1, y + engage.height() + 1, glow);
        }
    }

    /** How wide the footer's lines may run: up to the Engage button, and no further. */
    private static int footerWidth() {
        return LAYOUT.readouts().width();
    }

    private Component status() {
        PlotterState state = data.plotterState();
        return switch (state) {
            case CHARGING -> AWLang.translate("gui.rift_plotter.charging", Math.round(PlotCost.progress(data.charge(),
                    data.cost()) * 100.0F) + "%", String.format("%,d", data.charge()), String.format("%,d", data.cost()))
                    .component();
            case REACHING -> AWLang.translate("gui.rift_plotter.reaching").component();
            case OPEN -> data.laneOpen()
                    ? AWLang.translate("gui.rift_plotter.lane_open", data.carried()).component()
                    : AWLang.translate("gui.rift_plotter.open", Math.max(0, (data.openTicks() - data.stateTicks()) / 20),
                    data.carried()).component();
            case CLOSING -> AWLang.translate("gui.rift_plotter.closing").component();
            case IDLE -> {
                if (dirty) {
                    yield AWLang.translate("gui.rift_plotter.sending").component();
                }
                PlotFailure preview = data.previewFailure();
                if (preview.isFailure()) {
                    yield AWLang.translate(preview.translationKey()).component();
                }
                PlotFailure last = data.lastFailure();
                if (last.isFailure()) {
                    yield AWLang.translate("gui.rift_plotter.last", AWLang.translate(last.translationKey()).component())
                            .component();
                }
                yield AWLang.translate("gui.rift_plotter.ready").component();
            }
        };
    }

    /**
     * {@link #status}, for the sidebar's 144 pixels: the charge as a share, the last refusal with no "Last:" before it
     * - its colour says it is the last one rather than the next.
     */
    private Component sideStatus() {
        PlotterState state = data.plotterState();
        if (state == PlotterState.CHARGING) {
            return AWLang.translate("gui.rift_plotter.side.charging", Math.round(PlotCost.progress(data.charge(),
                    data.cost()) * 100.0F) + "%").component();
        }
        if (state == PlotterState.IDLE && !dirty && !data.previewFailure().isFailure() && data.lastFailure().isFailure()) {
            return AWLang.translate(data.lastFailure().translationKey()).component();
        }
        return status();
    }

    private int statusColour() {
        return switch (data.plotterState()) {
            case CHARGING, REACHING -> WARN;
            case OPEN -> GOOD;
            case CLOSING -> LABEL;
            case IDLE -> dirty ? LABEL : data.previewFailure().isFailure() ? BAD
                    : data.lastFailure().isFailure() ? WARN : GOOD;
        };
    }

    // ------------------------------------------------------------------ helpers

    private static String compass(double degrees) {
        return ProbeBearing.byIndex((int) Math.round(Plot.wrapBearing(degrees) / 45.0D)).abbreviation();
    }

    /** A Minecraft yaw as a compass bearing: yaw 0 faces south, 180 on the compass. */
    private static double compassOfYaw(double yaw) {
        return Plot.wrapBearing(yaw + 180.0D);
    }

    private static double yawOfCompass(double bearing) {
        return Mth.wrapDegrees(bearing - 180.0D);
    }

    private static String signed(double value) {
        long rounded = Math.round(value);
        return rounded > 0 ? "+" + rounded : Long.toString(rounded);
    }

    /** One control in a row: a label, its value, and a track to drag. */
    private final class Slider {

        final Page page;
        final Rect row;
        final String key;
        final double min;
        final double max;
        final double step;
        final DoubleSupplier get;
        final DoubleConsumer set;
        final DoubleFunction<String> format;
        final java.util.function.BooleanSupplier enabled;
        /** Whether it may be moved while a strike is under way: a firing setting may, a plot's may not. */
        final boolean whileBusy;

        Slider(Page page, Rect row, String key, double min, double max, double step, DoubleSupplier get,
               DoubleConsumer set, DoubleFunction<String> format, java.util.function.BooleanSupplier enabled) {
            this(page, row, key, min, max, step, get, set, format, enabled, false);
        }

        Slider(Page page, Rect row, String key, double min, double max, double step, DoubleSupplier get,
               DoubleConsumer set, DoubleFunction<String> format, java.util.function.BooleanSupplier enabled,
               boolean whileBusy) {
            this.whileBusy = whileBusy;
            this.page = page;
            this.row = row;
            this.key = key;
            this.min = min;
            this.max = max;
            this.step = step;
            this.get = get;
            this.set = set;
            this.format = format;
            this.enabled = enabled;
        }

        boolean enabled() {
            return enabled.getAsBoolean();
        }

        boolean live() {
            return enabled() && (whileBusy || !busy());
        }

        Rect track() {
            return new Rect(row.x() + 2, row.y() + 12, row.width() - 4, 5);
        }

        /** The track and a little round it, so a thin bar is not a fiddly target. */
        Rect hit() {
            Rect track = track();
            return new Rect(track.x() - 2, track.y() - 4, track.width() + 4, track.height() + 7);
        }

        void drag(double localX) {
            Rect track = track();
            double fraction = Mth.clamp((localX - track.x()) / Math.max(1.0D, track.width()), 0.0D, 1.0D);
            double value = min + fraction * (max - min);
            set.accept(Math.round(value / step) * step);
        }

        void nudge(double direction) {
            set.accept(Mth.clamp(get.getAsDouble() + direction * step, min, max));
        }

        void draw(GuiGraphics graphics) {
            boolean live = live();
            int x = guiLeft + row.x();
            int y = guiTop + row.y();
            double value = get.getAsDouble();
            String shown = format.apply(value);
            String label = AWScreenStyle.trim(font, AWLang.translate(key).string(), row.width() - font.width(shown) - 6);
            graphics.drawString(font, label, x, y + 1, live ? LABEL : 0xFF_5A5268, false);
            graphics.drawString(font, shown, x + row.width() - font.width(shown), y + 1, live ? TEXT : 0xFF_6E6680, false);
            Rect track = track();
            int tx = guiLeft + track.x();
            int ty = guiTop + track.y();
            graphics.fill(tx, ty, tx + track.width(), ty + track.height(), 0xFF_140F1C);
            float fraction = (float) Mth.clamp((value - min) / Math.max(1.0e-6D, max - min), 0.0D, 1.0D);
            int filled = Math.round(track.width() * fraction);
            graphics.fill(tx, ty + 1, tx + filled, ty + track.height() - 1, live ? PATINA : 0xFF_2E3A36);
            int knob = tx + filled;
            graphics.fill(knob - 2, ty - 2, knob + 2, ty + track.height() + 2, live ? BRASS_LIGHT : BRASS_DARK);
            graphics.fill(knob - 1, ty - 1, knob + 1, ty + track.height() + 1, live ? BRASS : 0xFF_4A3820);
        }
    }

    /** An on-off switch in a row: a box and a label, the whole row the target. */
    private final class Toggle {

        final Page page;
        final Rect row;
        final String key;
        final java.util.function.BooleanSupplier get;
        final java.util.function.Consumer<Boolean> set;
        final java.util.function.BooleanSupplier enabled;

        Toggle(Page page, Rect row, String key, java.util.function.BooleanSupplier get,
               java.util.function.Consumer<Boolean> set, java.util.function.BooleanSupplier enabled) {
            this.page = page;
            this.row = row;
            this.key = key;
            this.get = get;
            this.set = set;
            this.enabled = enabled;
        }

        boolean get() {
            return get.getAsBoolean();
        }

        boolean enabled() {
            return enabled.getAsBoolean();
        }

        void flip() {
            set.accept(!get());
        }

        void draw(GuiGraphics graphics) {
            boolean live = enabled();
            int x = guiLeft + row.x();
            int y = guiTop + row.y() + 4;
            graphics.fill(x, y, x + 10, y + 10, live ? BRONZE : 0xFF_3A2E20);
            graphics.fill(x + 1, y + 1, x + 9, y + 9, WELL);
            if (get()) {
                graphics.fill(x + 2, y + 2, x + 8, y + 8, live ? GOOD : 0xFF_2E5A52);
            }
            graphics.drawString(font, AWScreenStyle.trim(font, AWLang.translate(key).string(), row.width() - 14), x + 14,
                    y + 1, live ? (get() ? TEXT : LABEL) : 0xFF_5A5268, false);
        }
    }
}
