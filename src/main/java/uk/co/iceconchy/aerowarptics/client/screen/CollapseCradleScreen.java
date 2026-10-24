package uk.co.iceconchy.aerowarptics.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.client.CradlePanels;
import uk.co.iceconchy.aerowarptics.client.screen.AWLayout.Rect;
import uk.co.iceconchy.aerowarptics.collapse.CollapseCradleMenu;
import uk.co.iceconchy.aerowarptics.collapse.CollapseFailure;
import uk.co.iceconchy.aerowarptics.collapse.CollapseRules;
import uk.co.iceconchy.aerowarptics.collapse.CradlePanel;
import uk.co.iceconchy.aerowarptics.collapse.CradleWorks;
import uk.co.iceconchy.aerowarptics.network.ServerboundCradleStartPacket;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Collapse Cradle's panel: the end of the game's own screen, dressed as the machine is.
 *
 * <p>A slab of rift-stone in a verdigris frame, brass plates bolted at its corners, a channel of carving round its
 * edge that lights as the cradle works - and fails with it: when a collapse is heading for trouble the carving
 * flickers and runs red, cracks spread in from the corners, and the dial's needle will not keep still. Nothing
 * here comes from a texture; it is fills, like the rest of this mod's screens, laid out by {@link CradleLayout}.
 *
 * <p>On the left, the machine in elevation with its three slots where they are on the machine - the arms in the
 * rings, the cup in the plinth, the tray below - and the offerings on the depots round it down either side. In
 * the middle, where a collapse's instability is heading against the config's bands, and how far through it is. On
 * the right, the tank, the shaft and the stabilisers. Under them, Start, and what it would make and cost, or why
 * it would refuse. All of it read from the {@link CradlePanel} the server sends while this is open; Start asks the
 * server, which decides exactly as a redstone pulse would.
 */
@OnlyIn(Dist.CLIENT)
public class CollapseCradleScreen extends AbstractContainerScreen<CollapseCradleMenu> {

    private static final int STONE = 0xFF_2A2336;
    private static final int STONE_LIGHT = 0xFF_3B3349;
    private static final int STONE_EDGE = 0xFF_50465F;
    private static final int MORTAR = 0xFF_1B1524;
    private static final int WELL = 0xFF_140F1C;
    private static final int PATINA_DARK = 0xFF_27554B;
    private static final int PATINA = 0xFF_43866F;
    private static final int PATINA_LIGHT = 0xFF_70B698;
    private static final int BRONZE = 0xFF_7E5C34;
    private static final int BRONZE_LIGHT = 0xFF_B4884F;
    private static final int BRASS_DARK = 0xFF_7A6038;
    private static final int BRASS = 0xFF_B08D57;
    private static final int BRASS_LIGHT = 0xFF_D8B068;
    private static final int GLYPH = 0xA060F0;
    private static final int ESSENCE = 0xFF_9B5CFF;
    private static final int TEXT = 0xFF_E6E1D6;
    private static final int LABEL = 0xFF_9A8FB0;
    private static final int GOOD = 0xFF_49D9C4;
    private static final int WARN = 0xFF_E0B04A;
    private static final int BAD = 0xFF_D95C4A;

    private final CradleLayout.Panel layout = CradleLayout.panel();

    public CollapseCradleScreen(CollapseCradleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = CradleLayout.WIDTH;
        imageHeight = CradleLayout.HEIGHT;
        inventoryLabelX = CradleLayout.INVENTORY_X;
        inventoryLabelY = CradleLayout.INVENTORY_Y - 11;
    }

    @Nullable
    private CradlePanel panel() {
        return CradlePanels.of(menu.pos());
    }

    @Override
    public void removed() {
        super.removed();
        CradlePanels.forget();
    }

    private float time() {
        return minecraft == null || minecraft.level == null ? 0.0F
                : minecraft.level.getGameTime() + minecraft.getTimer().getGameTimeDeltaPartialTick(false);
    }

    /** How badly a collapse under way is failing, 0..1 - what the panel shows of it. */
    private double strain(@Nullable CradlePanel panel) {
        return panel == null || panel.mode() != CradlePanel.COLLAPSE ? 0.0D : CradleWorks.strain(panel.heading());
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        tooltips(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        CradlePanel panel = panel();
        double strain = strain(panel);
        long tick = minecraft == null || minecraft.level == null ? 0L : minecraft.level.getGameTime();
        frame(graphics, panel, strain, tick);
        altar(graphics, panel);
        dial(graphics, panel, strain);
        readouts(graphics, panel);
        start(graphics, panel, mouseX, mouseY);
        if (strain > 0.0D) {
            cracks(graphics, strain, tick);
        }
    }

    /** The titles are drawn with the frame; only the inventory's label is left where the game puts it. */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);
    }

    /** The slab, its frame, its brass corners, the carving round its edge, and the header. */
    private void frame(GuiGraphics graphics, @Nullable CradlePanel panel, double strain, long tick) {
        int x = leftPos;
        int y = topPos;
        int w = imageWidth;
        int h = imageHeight;
        // Verdigris frame, bevelled: light catching the top and left, shadow under the bottom and right.
        graphics.fill(x, y, x + w, y + h, PATINA_DARK);
        graphics.fill(x, y, x + w, y + 1, PATINA_LIGHT);
        graphics.fill(x, y, x + 1, y + h, PATINA_LIGHT);
        graphics.fill(x + 1, y + 1, x + w - 1, y + 3, PATINA);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFF_132A25);
        graphics.fill(x + w - 1, y, x + w, y + h, 0xFF_132A25);
        // Rift-stone within it, laid in courses.
        graphics.fill(x + 4, y + 4, x + w - 4, y + h - 4, STONE);
        for (int row = 0; y + 4 + row * 12 < y + h - 4; row++) {
            int top = y + 4 + row * 12;
            graphics.fill(x + 4, top, x + w - 4, top + 1, MORTAR);
            for (int joint = (row % 2) * 12; joint < w - 8; joint += 24) {
                graphics.fill(x + 4 + joint, top, x + 5 + joint, Math.min(top + 12, y + h - 4), MORTAR);
            }
        }
        // The carving round the edge, lit as the machine works - and flickering red as it fails.
        int glow = carving(panel, strain, tick);
        graphics.fill(x + 6, y + 6, x + w - 6, y + 7, glow);
        graphics.fill(x + 6, y + h - 7, x + w - 6, y + h - 6, glow);
        graphics.fill(x + 6, y + 6, x + 7, y + h - 6, glow);
        graphics.fill(x + w - 7, y + 6, x + w - 6, y + h - 6, glow);
        // Brass plates bolted over the corners, where the old frame split.
        for (int[] corner : new int[][]{{x, y}, {x + w - 12, y}, {x, y + h - 12}, {x + w - 12, y + h - 12}}) {
            graphics.fill(corner[0], corner[1], corner[0] + 12, corner[1] + 12, BRASS);
            graphics.fill(corner[0], corner[1], corner[0] + 12, corner[1] + 1, BRASS_LIGHT);
            graphics.fill(corner[0], corner[1] + 11, corner[0] + 12, corner[1] + 12, BRASS_DARK);
            graphics.fill(corner[0] + 2, corner[1] + 2, corner[0] + 4, corner[1] + 4, BRASS_DARK);
            graphics.fill(corner[0] + 8, corner[1] + 8, corner[0] + 10, corner[1] + 10, BRASS_DARK);
        }
        // The header: the machine's name, and what it is doing.
        graphics.drawString(font, title, x + 14, y + 6, BRASS_LIGHT, false);
        Component state = state(panel);
        int stateColour = panel == null ? LABEL : strain > 0.5D && (tick / 6L) % 2L == 0L ? BAD
                : panel.mode() == CradlePanel.IDLE ? LABEL : GOOD;
        String shownState = AWScreenStyle.trim(font, state.getString(), w - 28 - font.width(title) - 8);
        graphics.drawString(font, shownState, x + w - 14 - font.width(shownState), y + 6, stateColour, false);
        inset(graphics, layout.altar());
        inset(graphics, layout.dial());
        inset(graphics, layout.status());
        inset(graphics, layout.readouts());
    }

    /** The carving's colour: faint at rest, bright as it works, and flickering hot as a collapse fails. */
    private int carving(@Nullable CradlePanel panel, double strain, long tick) {
        double bright;
        if (panel == null || panel.mode() == CradlePanel.IDLE) {
            bright = 0.18D + 0.06D * Math.sin(time() * 0.08D);
        } else if (panel.mode() == CradlePanel.FOLD) {
            bright = CradleWorks.foldGlow(panel.progress());
        } else {
            bright = CradleWorks.glow(panel.progress(), true, 1.0D, 0.0D);
        }
        bright *= CradleWorks.flicker(strain, tick);
        double[] hot = CradleWorks.hot(strain);
        int r = (int) Mth.clamp(((GLYPH >> 16) & 0xFF) * hot[0] * bright + 20, 0, 255);
        int g = (int) Mth.clamp(((GLYPH >> 8) & 0xFF) * hot[1] * bright + 14, 0, 255);
        int b = (int) Mth.clamp((GLYPH & 0xFF) * hot[2] * bright + 28, 0, 255);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private void inset(GuiGraphics graphics, Rect rect) {
        int x = leftPos + rect.x();
        int y = topPos + rect.y();
        graphics.fill(x - 1, y - 1, x + rect.width() + 1, y + rect.height() + 1, BRONZE);
        graphics.fill(x - 1, y - 1, x + rect.width() + 1, y, BRONZE_LIGHT);
        graphics.fill(x, y, x + rect.width(), y + rect.height(), WELL);
    }

    private Component state(@Nullable CradlePanel panel) {
        if (panel == null) {
            return AWLang.translate("gui.collapse_cradle.panel.reading").component();
        }
        return switch (panel.mode()) {
            case CradlePanel.COLLAPSE -> panel.held()
                    ? AWLang.translate("gui.collapse_cradle.panel.held").component()
                    : AWLang.translate("gui.collapse_cradle.panel.collapsing", AWLang.percent(panel.progress())).component();
            case CradlePanel.FOLD -> AWLang.translate("gui.collapse_cradle.panel.folding", AWLang.percent(panel.progress())).component();
            default -> AWLang.translate("gui.collapse_cradle.panel.idle").component();
        };
    }

    // ------------------------------------------------------------------ the altar

    /** The machine in elevation, its slots where they are on it, and the depots round it down either side. */
    private void altar(GuiGraphics graphics, @Nullable CradlePanel panel) {
        Rect altar = layout.altar();
        int x = leftPos + altar.x();
        int y = topPos + altar.y();
        int cx = leftPos + CradleLayout.ARMS_X + 8;
        // Crown, pillars and plinth.
        graphics.fill(cx - 26, y + 4, cx + 26, y + 10, PATINA);
        graphics.fill(cx - 26, y + 4, cx + 26, y + 5, PATINA_LIGHT);
        boolean open = panel != null && panel.mode() == CradlePanel.COLLAPSE;
        graphics.fill(cx - 8, y + 5, cx + 8, y + 9, open ? 0xFF_05030A : BRONZE);
        graphics.fill(cx - 26, y + 10, cx - 21, y + 64, PATINA);
        graphics.fill(cx + 21, y + 10, cx + 26, y + 64, PATINA);
        graphics.fill(cx - 26, y + 22, cx - 21, y + 23, BRASS);
        graphics.fill(cx + 21, y + 22, cx + 26, y + 23, BRASS);
        graphics.fill(cx - 26, y + 44, cx - 21, y + 45, BRASS);
        graphics.fill(cx + 21, y + 44, cx + 26, y + 45, BRASS);
        graphics.fill(cx - 30, y + 64, cx + 30, y + 86, STONE_LIGHT);
        graphics.fill(cx - 30, y + 64, cx + 30, y + 65, STONE_EDGE);
        graphics.fill(cx - 34, y + 86, cx + 34, y + 90, STONE_EDGE);
        // The rings round the arms: flat at rest, tumbling while a collapse winds.
        rings(graphics, panel, cx, topPos + CradleLayout.ARMS_Y + 8);
        // The arms reaching up from the plinth to hold what the cradle holds.
        int wrist = topPos + CradleLayout.ARMS_Y + 12;
        thick(graphics, cx - 18, y + 64, cx - 12, wrist, BRASS);
        thick(graphics, cx + 18, y + 64, cx + 12, wrist, BRASS);
        // The tray under the machine, where what it made is held.
        graphics.fill(cx - 16, topPos + CradleLayout.OUT_Y + 12, cx + 16, topPos + CradleLayout.OUT_Y + 18, BRASS_DARK);
        graphics.drawString(font, AWLang.translate("gui.collapse_cradle.panel.made").component(), cx + 12,
                topPos + CradleLayout.OUT_Y + 4, LABEL, false);
        well(graphics, CradleLayout.ARMS_X, CradleLayout.ARMS_Y, panel != null && panel.ready());
        well(graphics, CradleLayout.CUP_X, CradleLayout.CUP_Y, false);
        well(graphics, CradleLayout.OUT_X, CradleLayout.OUT_Y, false);
        // The depots: those on the west of the machine down the left, those on the east down the right.
        List<Rect> spots = layout.offerings();
        for (Rect spot : spots) {
            graphics.fill(leftPos + spot.x() - 1, topPos + spot.y() - 1, leftPos + spot.right() + 1,
                    topPos + spot.bottom() + 1, MORTAR);
        }
        List<CradlePanel.Offering> placed = placed(panel);
        for (int i = 0; i < placed.size(); i++) {
            CradlePanel.Offering offering = placed.get(i);
            if (offering == null || offering.stack().isEmpty()) {
                continue;
            }
            Rect spot = spots.get(i);
            graphics.renderItem(offering.stack(), leftPos + spot.x(), topPos + spot.y());
            graphics.renderItemDecorations(font, offering.stack(), leftPos + spot.x(), topPos + spot.y());
        }
    }

    /** Each depot's offering put in the spot on its side of the machine, nearest the north at the top. */
    private List<CradlePanel.Offering> placed(@Nullable CradlePanel panel) {
        List<CradlePanel.Offering> spots = new ArrayList<>(java.util.Collections.nCopies(8, null));
        if (panel == null) {
            return spots;
        }
        List<CradlePanel.Offering> west = new ArrayList<>();
        List<CradlePanel.Offering> east = new ArrayList<>();
        for (CradlePanel.Offering offering : panel.offerings()) {
            (offering.x() < 0 || offering.x() == 0 && offering.z() < 0 ? west : east).add(offering);
        }
        west.sort(Comparator.comparingInt(CradlePanel.Offering::z));
        east.sort(Comparator.comparingInt(CradlePanel.Offering::z));
        // A side with more than four spills onto the other's free spots.
        List<CradlePanel.Offering> order = new ArrayList<>();
        order.addAll(west);
        order.addAll(east);
        int left = 0;
        int right = 4;
        for (CradlePanel.Offering offering : order) {
            boolean westward = west.contains(offering);
            if (westward && left < 4 || !westward && right >= 8 && left < 4) {
                spots.set(left++, offering);
            } else if (right < 8) {
                spots.set(right++, offering);
            }
        }
        return spots;
    }

    private void rings(GuiGraphics graphics, @Nullable CradlePanel panel, int cx, int cy) {
        double t = time();
        boolean tumbling = panel != null && panel.mode() == CradlePanel.COLLAPSE
                && CradleWorks.tumble(panel.progress(), true) > 0.0D;
        double[][] rings = {{22, 0.9D}, {18, -1.3D}, {14, 1.7D}};
        for (int i = 0; i < rings.length; i++) {
            double radius = rings[i][0];
            double squash = tumbling ? Math.abs(Math.cos(t * 0.05D * rings[i][1])) : 0.18D;
            int colour = i == 0 ? BRONZE_LIGHT : PATINA_LIGHT;
            for (int step = 0; step < 72; step++) {
                double a = Math.PI * 2.0D * step / 72.0D;
                int px = cx + (int) Math.round(Math.cos(a) * radius);
                int py = cy + (int) Math.round(Math.sin(a) * radius * Math.max(0.12D, squash));
                graphics.fill(px, py, px + 1, py + 1, colour);
            }
        }
    }

    private void well(GuiGraphics graphics, int itemX, int itemY, boolean lit) {
        Rect well = CradleLayout.well(itemX, itemY);
        int x = leftPos + well.x();
        int y = topPos + well.y();
        graphics.fill(x - 1, y - 1, x + well.width() + 1, y + well.height() + 1, lit ? BRASS_LIGHT : BRASS_DARK);
        graphics.fill(x, y, x + well.width(), y + well.height(), WELL);
    }

    // ------------------------------------------------------------------ the dial

    /** Where a collapse's instability is heading, against the config's bands, and how far through it is. */
    private void dial(GuiGraphics graphics, @Nullable CradlePanel panel, double strain) {
        Rect dial = layout.dial();
        int cx = leftPos + dial.centreX();
        int cy = topPos + dial.bottom() - 10;
        double settle = panel == null ? 0.35D : panel.settleBelow();
        double spit = panel == null ? 0.7D : panel.spitAbove();
        for (int step = 0; step <= 120; step++) {
            double f = step / 120.0D;
            int colour = f < settle ? GOOD : f < spit ? WARN : BAD;
            double a = Math.PI * (1.0D - f);
            for (int r = 38; r <= 42; r++) {
                int px = cx + (int) Math.round(Math.cos(a) * r);
                int py = cy - (int) Math.round(Math.sin(a) * r);
                graphics.fill(px, py, px + 1, py + 1, (colour & 0x00FFFFFF) | 0xC0000000);
            }
        }
        graphics.drawString(font, AWLang.translate("gui.collapse_cradle.panel.steady").component(), cx - 50, cy + 2, GOOD, false);
        Component spat = AWLang.translate("gui.collapse_cradle.panel.spat").component();
        graphics.drawString(font, spat, cx + 50 - font.width(spat), cy + 2, BAD, false);
        if (panel != null && panel.mode() == CradlePanel.COLLAPSE) {
            // Where it has got to, as a mark on the band.
            double got = Mth.clamp(panel.instability(), 0.0F, 1.0F);
            double a = Math.PI * (1.0D - got);
            thick(graphics, cx + (int) (Math.cos(a) * 36), cy - (int) (Math.sin(a) * 36),
                    cx + (int) (Math.cos(a) * 44), cy - (int) (Math.sin(a) * 44), TEXT);
            // Where it is heading: the needle, trembling as it fails.
            double shake = strain * 0.06D * Math.sin(time() * 2.7D + Math.sin(time() * 7.1D));
            double heading = Mth.clamp(panel.heading() + shake, 0.0D, 1.0D);
            double h = Math.PI * (1.0D - heading);
            thick(graphics, cx, cy, cx + (int) Math.round(Math.cos(h) * 34), cy - (int) Math.round(Math.sin(h) * 34),
                    heading < settle ? GOOD : heading < spit ? WARN : BAD);
        } else {
            thick(graphics, cx, cy, cx - 30, cy, LABEL);
        }
        graphics.fill(cx - 2, cy - 2, cx + 3, cy + 3, BRASS);

        // Progress, with the beats of a collapse marked.
        Rect bar = layout.progress();
        int bx = leftPos + bar.x();
        int by = topPos + bar.y();
        graphics.fill(bx, by, bx + bar.width(), by + bar.height(), WELL);
        graphics.fill(bx, by, bx + bar.width(), by + 1, BRONZE);
        if (panel != null && panel.mode() != CradlePanel.IDLE) {
            int filled = (int) Math.round(bar.width() * Mth.clamp(panel.progress(), 0.0F, 1.0F));
            graphics.fill(bx, by + 2, bx + filled, by + bar.height() - 1, ESSENCE);
            if (panel.mode() == CradlePanel.COLLAPSE) {
                for (double beat : new double[]{CollapseRules.SPIN_UP_END, CollapseRules.OPENING_END, CollapseRules.WINDING_END}) {
                    int tx = bx + (int) Math.round(bar.width() * beat);
                    graphics.fill(tx, by + 1, tx + 1, by + bar.height(), BRASS_LIGHT);
                }
            }
        }

        // What is under way, how unsteady, and how the last one ended.
        Rect status = layout.status();
        int sx = leftPos + status.x() + 3;
        int sy = topPos + status.y() + 3;
        List<Component> lines = statusLines(panel, strain);
        int foot = topPos + status.bottom() - 9;
        for (Component line : lines) {
            int colour = line.getStyle().getColor() == null ? TEXT : line.getStyle().getColor().getValue() | 0xFF000000;
            // Wrapped rather than cut: a line that runs on is read on the next, where a cut one just stops.
            for (net.minecraft.util.FormattedCharSequence part : font.split(Component.literal(line.getString()),
                    status.width() - 6)) {
                if (sy > foot) {
                    return;
                }
                graphics.drawString(font, part, sx, sy, colour, false);
                sy += 11;
            }
        }
    }

    private List<Component> statusLines(@Nullable CradlePanel panel, double strain) {
        List<Component> lines = new ArrayList<>();
        if (panel == null) {
            lines.add(AWLang.translate("gui.collapse_cradle.panel.reading").component());
            return lines;
        }
        if (panel.mode() == CradlePanel.COLLAPSE) {
            lines.add(AWLang.translate("gui.collapse_cradle.panel.phase."
                    + CollapseRules.phase(panel.progress()).name().toLowerCase(java.util.Locale.ROOT)).component());
            lines.add(AWLang.translate("gui.collapse_cradle.panel.heading", AWLang.percent(panel.heading())).component()
                    .copy().withColor(strain > 0.3D ? BAD & 0xFFFFFF : TEXT & 0xFFFFFF));
            if (strain > 0.5D) {
                lines.add(AWLang.translate("gui.collapse_cradle.panel.unstable").component().copy().withColor(BAD & 0xFFFFFF));
            }
        } else if (panel.mode() == CradlePanel.FOLD) {
            lines.add(AWLang.translate("gui.collapse_cradle.panel.folding_what").component());
            lines.add(makes(panel.result()));
        } else {
            lines.add(AWLang.translate(panel.next() == CradlePanel.FOLD ? "gui.collapse_cradle.panel.next_fold"
                    : panel.next() == CradlePanel.COLLAPSE ? "gui.collapse_cradle.panel.next_collapse"
                    : "gui.collapse_cradle.panel.nothing").component());
            if (!panel.lastOutcome().isEmpty()) {
                lines.add(AWLang.translate("gui.collapse_cradle.outcome." + panel.lastOutcome()).component().copy()
                        .withColor(WARN & 0xFFFFFF));
            } else if (panel.lastFailureValue().isFailure()) {
                lines.add(AWLang.translate(panel.lastFailureValue().translationKey()).component().copy()
                        .withColor(BAD & 0xFFFFFF));
            }
        }
        return lines;
    }

    private Component makes(ItemStack result) {
        if (result.isEmpty()) {
            return AWLang.translate("gui.collapse_cradle.panel.nothing_made").component();
        }
        return AWLang.translate("gui.collapse_cradle.panel.makes", AWLang.count(result.getCount()), result.getHoverName())
                .component();
    }

    // ------------------------------------------------------------------ the readouts

    /** The tank, the shaft and the stabilisers. */
    private void readouts(GuiGraphics graphics, @Nullable CradlePanel panel) {
        Rect essence = layout.essence();
        Rect speed = layout.speed();
        Rect lean = layout.lean();
        int ex = leftPos + essence.x();
        int ey = topPos + essence.y();
        graphics.drawString(font, AWLang.translate("gui.collapse_cradle.panel.essence_short").component(), ex - 2,
                ey - 11, LABEL, false);
        gauge(graphics, essence, panel == null ? 0.0D : panel.essence() / (double) Math.max(1, panel.capacity()), ESSENCE,
                panel == null || panel.essenceNeeded() <= 0 ? -1.0D
                        : panel.essenceNeeded() / (double) Math.max(1, panel.capacity()), -1.0D);

        int sx = leftPos + speed.x();
        int sy = topPos + speed.y();
        graphics.drawString(font, AWLang.translate("gui.collapse_cradle.panel.speed_short").component(), sx - 2,
                sy - 11, LABEL, false);
        double top = panel == null ? 256.0D : Math.max(256.0D, panel.optimalSpeed());
        double turning = panel == null ? 0.0D : Math.abs(panel.speed());
        int speedColour = panel == null || turning < panel.minimumSpeed() ? BAD
                : turning < panel.optimalSpeed() ? WARN : GOOD;
        gauge(graphics, speed, turning / top, speedColour,
                panel == null ? -1.0D : panel.minimumSpeed() / top, panel == null ? -1.0D : panel.optimalSpeed() / top);

        // The stabilisers: how many, and which way the cage leans - a dot off the middle is a lopsided cage.
        int lx = leftPos + lean.centreX();
        int ly = topPos + lean.centreY();
        for (int step = 0; step < 48; step++) {
            double a = Math.PI * 2.0D * step / 48.0D;
            int px = lx + (int) Math.round(Math.cos(a) * 14);
            int py = ly + (int) Math.round(Math.sin(a) * 14);
            graphics.fill(px, py, px + 1, py + 1, PATINA_LIGHT);
        }
        graphics.fill(lx - 1, ly - 1, lx + 2, ly + 2, BRASS_DARK);
        if (panel != null && panel.stabilisers() > 0) {
            int dx = (int) Math.round(Mth.clamp(panel.leanX(), -1.0F, 1.0F) * 12);
            int dz = (int) Math.round(Mth.clamp(panel.leanZ(), -1.0F, 1.0F) * 12);
            boolean even = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
            graphics.fill(lx + dx - 2, ly + dz - 2, lx + dx + 3, ly + dz + 3, even ? GOOD : WARN);
        }
        Rect figures = layout.figures();
        int textY = topPos + figures.y();
        int textX = leftPos + figures.x();
        Component count = panel == null || panel.stabilisers() == 0
                ? AWLang.translate("gui.collapse_cradle.panel.no_stabilisers").component()
                : AWLang.translate("gui.collapse_cradle.panel.stabilisers", panel.stabilisers()).component();
        drawSmall(graphics, count, textX, textY, LABEL);
        if (panel != null) {
            drawSmall(graphics, Component.literal(AWLang.count(panel.essence()) + " mB"), textX, textY + 12, ESSENCE);
            drawSmall(graphics, Component.literal(AWLang.count(Math.abs(panel.speed())) + " rpm"), textX, textY + 24,
                    speedColour);
        }
    }

    /** A vertical gauge, filled from the bottom, with up to two marks across it. */
    private void gauge(GuiGraphics graphics, Rect rect, double fill, int colour, double mark, double secondMark) {
        int x = leftPos + rect.x();
        int y = topPos + rect.y();
        graphics.fill(x - 1, y - 1, x + rect.width() + 1, y + rect.height() + 1, BRONZE);
        graphics.fill(x, y, x + rect.width(), y + rect.height(), WELL);
        int filled = (int) Math.round(rect.height() * Mth.clamp(fill, 0.0D, 1.0D));
        graphics.fill(x + 1, y + rect.height() - filled, x + rect.width() - 1, y + rect.height(), colour);
        for (double at : new double[]{mark, secondMark}) {
            if (at >= 0.0D) {
                int my = y + rect.height() - (int) Math.round(rect.height() * Mth.clamp(at, 0.0D, 1.0D));
                graphics.fill(x - 3, my, x + rect.width() + 3, my + 1, BRASS_LIGHT);
            }
        }
    }

    private void drawSmall(GuiGraphics graphics, Component text, int x, int y, int colour) {
        graphics.drawString(font, font.plainSubstrByWidth(text.getString(), layout.figures().right() - (x - leftPos)),
                x, y, colour, false);
    }

    // ------------------------------------------------------------------ start

    /** Start, lit when it would begin something; under it, what it would make and cost, or why it would not. */
    private void start(GuiGraphics graphics, @Nullable CradlePanel panel, int mouseX, int mouseY) {
        Rect start = layout.start();
        int x = leftPos + start.x();
        int y = topPos + start.y();
        boolean ready = panel != null && panel.ready();
        boolean hover = ready && start.contains(mouseX - leftPos, mouseY - topPos);
        int face = ready ? hover ? BRASS_LIGHT : BRASS : BRONZE;
        graphics.fill(x, y, x + start.width(), y + start.height(), face);
        graphics.fill(x, y, x + start.width(), y + 1, ready ? 0xFF_F4DC9C : BRONZE_LIGHT);
        graphics.fill(x, y + start.height() - 2, x + start.width(), y + start.height(), ready ? BRASS_DARK : 0xFF_4A3420);
        for (int[] rivet : new int[][]{{x + 3, y + 3}, {x + start.width() - 5, y + 3},
                {x + 3, y + start.height() - 6}, {x + start.width() - 5, y + start.height() - 6}}) {
            graphics.fill(rivet[0], rivet[1], rivet[0] + 2, rivet[1] + 2, BRASS_DARK);
        }
        Component label = startLabel(panel);
        graphics.drawString(font, label, x + (start.width() - font.width(label)) / 2, y + 8,
                ready ? 0xFF_2A1C0C : 0xFF_C8B8A0, false);

        Rect preview = layout.preview();
        int px = leftPos + preview.x();
        int py = topPos + preview.y();
        if (panel == null) {
            return;
        }
        ItemStack result = panel.result();
        if (!result.isEmpty()) {
            graphics.renderItem(result, px, py + 2);
            graphics.renderItemDecorations(font, result, px, py + 2);
            graphics.drawString(font, font.plainSubstrByWidth(result.getHoverName().getString(), preview.width() - 22),
                    px + 20, py + 2, TEXT, false);
            if (panel.essenceNeeded() > 0) {
                graphics.drawString(font, AWScreenStyle.trim(font, AWLang.translate("gui.collapse_cradle.panel.cost",
                        AWLang.count(panel.essenceNeeded())).string(), preview.width() - 20), px + 20, py + 12, ESSENCE, false);
            }
        }
        CollapseFailure failure = panel.failureValue();
        if (panel.mode() == CradlePanel.IDLE && failure.isFailure()) {
            List<net.minecraft.util.FormattedCharSequence> why = font.split(
                    AWLang.translate(failure.translationKey()).component(), preview.width());
            int wy = py + 24;
            for (int i = 0; i < Math.min(2, why.size()); i++) {
                graphics.drawString(font, why.get(i), px, wy, BAD, false);
                wy += 10;
            }
        } else if (panel.mode() == CradlePanel.IDLE && panel.minimumSpeed() > 0.0F) {
            graphics.drawString(font, AWScreenStyle.trim(font, AWLang.translate("gui.collapse_cradle.panel.needs_speed",
                    AWLang.count(panel.minimumSpeed()), AWLang.count(panel.optimalSpeed())).string(), preview.width()),
                    px, py + 24, LABEL, false);
        }
    }

    private Component startLabel(@Nullable CradlePanel panel) {
        if (panel == null || panel.mode() != CradlePanel.IDLE) {
            return AWLang.translate("gui.collapse_cradle.panel.at_work").component();
        }
        return AWLang.translate(switch (panel.next()) {
            case CradlePanel.FOLD -> "gui.collapse_cradle.panel.begin_fold";
            case CradlePanel.COLLAPSE -> "gui.collapse_cradle.panel.begin_collapse";
            default -> "gui.collapse_cradle.panel.nothing";
        }).component();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        CradlePanel panel = panel();
        if (button == 0 && panel != null && panel.ready() && layout.start().contains(mouseX - leftPos, mouseY - topPos)) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ServerboundCradleStartPacket(menu.pos()));
            if (minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.8F));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ------------------------------------------------------------------ failing

    /** Cracks run in from the corners as a collapse fails, further the worse it gets, glowing hot in their depths. */
    private void cracks(GuiGraphics graphics, double strain, long tick) {
        int[][] corners = {{leftPos + 4, topPos + 4, 1, 1}, {leftPos + imageWidth - 5, topPos + 4, -1, 1},
                {leftPos + 4, topPos + imageHeight - 5, 1, -1}, {leftPos + imageWidth - 5, topPos + imageHeight - 5, -1, -1}};
        int length = (int) Math.round(10 + 70 * strain);
        for (int c = 0; c < corners.length; c++) {
            int x = corners[c][0];
            int y = corners[c][1];
            for (int step = 0; step < length; step++) {
                double wander = Math.sin(step * 0.7D + c * 2.1D);
                x += corners[c][2];
                y += (step % 3 == 0 ? corners[c][3] : 0) + (wander > 0.6D ? corners[c][3] : 0);
                graphics.fill(x, y, x + 1, y + 1, 0xFF_0A0610);
                if (step % 5 == 0 && (tick + step) % 7 < 4 * strain) {
                    graphics.fill(x, y + 1, x + 1, y + 2, 0xFF_D95C4A);
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /** A line two pixels thick, drawn as squares along it. */
    private static void thick(GuiGraphics graphics, int x0, int y0, int x1, int y1, int colour) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (steps == 0 ? 0 : (x1 - x0) * i / steps);
            int y = y0 + (steps == 0 ? 0 : (y1 - y0) * i / steps);
            graphics.fill(x, y, x + 2, y + 2, colour);
        }
    }

    /** How wide a tooltip's lines run before they wrap. Vanilla wraps nothing handed to it as one component. */
    static final int TOOLTIP_WIDTH = 180;

    /** A tooltip, wrapped to {@link #TOOLTIP_WIDTH} - and kept on the screen, which vanilla does for wrapped lines. */
    private void tip(GuiGraphics graphics, Component text, int mouseX, int mouseY) {
        graphics.renderTooltip(font, font.split(text, TOOLTIP_WIDTH), mouseX, mouseY);
    }

    /** Tooltips for the gauges, the depots and the empty wells. */
    private void tooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        CradlePanel panel = panel();
        int mx = mouseX - leftPos;
        int my = mouseY - topPos;
        if (panel != null && layout.essence().contains(mx, my)) {
            tip(graphics, AWLang.translate("gui.collapse_cradle.panel.essence_tip",
                    AWLang.count(panel.essence()), AWLang.count(panel.capacity()), AWLang.count(panel.essenceNeeded()))
                    .component(), mouseX, mouseY);
        } else if (panel != null && layout.speed().contains(mx, my)) {
            tip(graphics, AWLang.translate("gui.collapse_cradle.panel.speed_tip",
                    AWLang.count(Math.abs(panel.speed())), AWLang.count(panel.minimumSpeed()),
                    AWLang.count(panel.optimalSpeed())).component(), mouseX, mouseY);
        } else if (panel != null && layout.lean().contains(mx, my)) {
            tip(graphics, AWLang.translate("gui.collapse_cradle.panel.lean_tip").component(), mouseX, mouseY);
        } else if (layout.dial().contains(mx, my)) {
            tip(graphics, AWLang.translate("gui.collapse_cradle.panel.dial_tip").component(), mouseX, mouseY);
        } else if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index < 3) {
            String which = switch (hoveredSlot.index) {
                case 0 -> "arms";
                case 1 -> "cup";
                default -> "tray";
            };
            tip(graphics, AWLang.translate("gui.collapse_cradle.panel." + which + "_tip").component(), mouseX, mouseY);
        } else {
            List<Rect> spots = layout.offerings();
            List<CradlePanel.Offering> placed = placed(panel);
            for (int i = 0; i < spots.size(); i++) {
                if (spots.get(i).contains(mx, my) && placed.get(i) != null && !placed.get(i).stack().isEmpty()) {
                    graphics.renderTooltip(font, placed.get(i).stack(), mouseX, mouseY);
                }
            }
        }
    }
}
