package uk.co.iceconchy.aerowarptics.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import uk.co.iceconchy.aerowarptics.network.ClientboundWellheadPanelPacket;
import uk.co.iceconchy.aerowarptics.network.ServerboundWellheadOrderPacket;
import uk.co.iceconchy.aerowarptics.util.AWLang;
import uk.co.iceconchy.aerowarptics.wellhead.WellheadReactor;
import uk.co.iceconchy.aerowarptics.wellhead.WellheadTier;

/**
 * A Rift Wellhead's control panel: how strained it is, where that is going, and the two things an operator can
 * do about it from here.
 *
 * <p>It shows only what the server last said and works nothing out - every figure is the server's config at
 * work. It asks again twice a second while it is open, and every order is answered with the panel, so a scram
 * that came too late is seen not to have taken. The gauge is drawn in the four bands the reactor names, with the
 * strain as a white mark and the heading as a hollow one: a calm well with its heading in the red is one that is
 * going to run away, said while there is still time.
 *
 * <p>Plain vanilla drawing, deliberately small: where everything goes is {@link WellheadLayout}'s.
 */
public class RiftWellheadScreen extends Screen {

    private static final int REFRESH_TICKS = 10;

    private static final int BACK = 0xE0140A22;
    private static final int EDGE = 0xFF6B4AA8;
    private static final int TEXT = 0xFFE6DCF7;
    private static final int DIM = 0xFF9A8DB5;
    private static final int CALM = 0xFF3FA7A0;
    private static final int STRAINING = 0xFFD8C24A;
    private static final int TEARING = 0xFFE08A2E;
    private static final int CRITICAL = 0xFFD8402E;

    private final BlockPos collar;
    private ClientboundWellheadPanelPacket panel;
    private WellheadLayout layout = new WellheadLayout(0, 0);
    private Button hatch;
    private int age;

    public RiftWellheadScreen(ClientboundWellheadPanelPacket panel) {
        super(AWLang.translate("gui.rift_wellhead.title").component());
        this.collar = panel.collar();
        this.panel = panel;
    }

    public BlockPos collar() {
        return collar;
    }

    /** The server has spoken again. */
    public void update(ClientboundWellheadPanelPacket latest) {
        panel = latest;
        if (hatch != null) {
            hatch.setMessage(hatchLabel());
        }
    }

    private Component hatchLabel() {
        return AWLang.translate(panel.hatchShut() ? "gui.rift_wellhead.hatch.open" : "gui.rift_wellhead.hatch.shut").component();
    }

    private void order(ServerboundWellheadOrderPacket.Order order) {
        PacketDistributor.sendToServer(new ServerboundWellheadOrderPacket(collar, order));
    }

    @Override
    protected void init() {
        layout = WellheadLayout.in(width, height);
        int[] scram = layout.scramButton();
        addRenderableWidget(Button.builder(AWLang.translate("gui.rift_wellhead.scram").component(),
                button -> order(ServerboundWellheadOrderPacket.Order.SCRAM)).bounds(scram[0], scram[1], scram[2], scram[3]).build());
        int[] at = layout.hatchButton();
        hatch = addRenderableWidget(Button.builder(hatchLabel(),
                button -> order(ServerboundWellheadOrderPacket.Order.HATCH)).bounds(at[0], at[1], at[2], at[3]).build());
    }

    @Override
    public void tick() {
        super.tick();
        Minecraft minecraft = Minecraft.getInstance();
        // The machine can be taken out from under its own panel - by a pickaxe, or by itself.
        if (minecraft.player == null || minecraft.level == null
                || minecraft.player.blockPosition().distSqr(collar) > 32.0D * 32.0D) {
            onClose();
            return;
        }
        if (++age % REFRESH_TICKS == 0) {
            order(ServerboundWellheadOrderPacket.Order.REFRESH);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No blur and no dimming: a well is watched while it is run, and its frame is right behind the panel.
        int[] box = layout.panel();
        graphics.fill(box[0] - 1, box[1] - 1, box[0] + box[2] + 1, box[1] + box[3] + 1, EDGE);
        graphics.fill(box[0], box[1], box[0] + box[2], box[1] + box[3], BACK);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        WellheadTier tier = WellheadTier.values()[Math.max(0, Math.min(WellheadTier.values().length - 1, panel.tier()))];
        WellheadReactor.Band band = WellheadReactor.Band.of(panel.strain());
        graphics.drawString(font, AWLang.translate("gui.rift_wellhead.heading_line",
                AWLang.translate(tier.translationKey()).component(), AWLang.translate(band.translationKey()).component())
                .component(), layout.textX(), layout.titleY(), colour(band), false);

        int[] gauge = layout.gauge();
        band(graphics, gauge, 0.0D, WellheadReactor.STRAINING, CALM);
        band(graphics, gauge, WellheadReactor.STRAINING, WellheadReactor.TEARING, STRAINING);
        band(graphics, gauge, WellheadReactor.TEARING, WellheadReactor.CRITICAL, TEARING);
        band(graphics, gauge, WellheadReactor.CRITICAL, 1.0D, CRITICAL);
        // Where it is going, hollow; where it is, solid and on top.
        int ahead = layout.gaugeAt(panel.heading());
        graphics.fill(ahead - 1, gauge[1] - 2, ahead + 2, gauge[1], TEXT);
        graphics.fill(ahead - 1, gauge[1] + gauge[3], ahead + 2, gauge[1] + gauge[3] + 2, TEXT);
        int now = layout.gaugeAt(panel.strain());
        graphics.fill(now - 1, gauge[1] - 1, now + 2, gauge[1] + gauge[3] + 1, 0xFFFFFFFF);

        row(graphics, 0, "gui.rift_wellhead.strain", percent(panel.strain()), percent(panel.heading()));
        row(graphics, 1, "gui.rift_wellhead.yield", String.format(java.util.Locale.ROOT, "%.1f", panel.yield()),
                panel.storm() ? AWLang.translate("gui.rift_wellhead.storm").component().getString() : "");
        row(graphics, 2, "gui.rift_wellhead.gullet", AWLang.count(panel.matter()), AWLang.count(panel.gullet()));
        row(graphics, 3, "gui.rift_wellhead.tank", AWLang.count(panel.tank()), AWLang.count(panel.tankCapacity()));
        row(graphics, 4, "gui.rift_wellhead.rotation", percent(panel.speedShare()), "");
        row(graphics, 5, "gui.rift_wellhead.cage", panel.stabilisers() + " / " + panel.needed(), percent(panel.cageShare()));
        row(graphics, 6, panel.lockout() > 0 ? "gui.rift_wellhead.frame_locked" : "gui.rift_wellhead.frame",
                percent(panel.integrity()), String.valueOf(panel.lockout()));
    }

    private void band(GuiGraphics graphics, int[] gauge, double from, double to, int colour) {
        // A dim band, lit only as far as the strain has got: the gauge fills as a thermometer does.
        int left = layout.gaugeAt(from);
        int right = layout.gaugeAt(to);
        graphics.fill(left, gauge[1], right, gauge[1] + gauge[3], (colour & 0xFFFFFF) | 0x50000000);
        int lit = Math.min(right, layout.gaugeAt(panel.strain()));
        if (lit > left) {
            graphics.fill(left, gauge[1], lit, gauge[1] + gauge[3], colour);
        }
    }

    private void row(GuiGraphics graphics, int row, String key, Object first, Object second) {
        graphics.drawString(font, AWLang.translate(key, first, second).component(), layout.textX(), layout.rowY(row),
                row == 0 ? TEXT : DIM, false);
    }

    private static String percent(float share) {
        return String.valueOf(Math.round(share * 100.0F));
    }

    private static int colour(WellheadReactor.Band band) {
        return switch (band) {
            case CALM -> CALM;
            case STRAINING -> STRAINING;
            case TEARING -> TEARING;
            default -> CRITICAL;
        };
    }

    /** Opens the panel for a click on the machine, or brings an open one up to date. */
    public static void accept(ClientboundWellheadPanelPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof RiftWellheadScreen open && open.collar().equals(packet.collar())) {
            open.update(packet);
        } else if (packet.open() && minecraft.screen == null) {
            minecraft.setScreen(new RiftWellheadScreen(packet));
        }
    }
}
