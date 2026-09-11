package uk.co.iceconchy.aerowarptics.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * A Catnip window that shrinks to fit rather than hanging off the screen.
 *
 * <p>Most of this mod's screens are shorter than the 240 virtual pixels "Auto" GUI scale guarantees,
 * so they sit inside {@link AbstractSimiScreen} untouched. Two are not: the Astrolabe's chart and the
 * Rift Probe both have to show a 129px survey, which pushes them past 270 - taller than an ordinary
 * 1080p desktop's virtual screen. {@link AWLayout#anchor} pins those to the top-left corner, which
 * keeps the header reachable but leaves the buttons along the bottom off the screen entirely.
 *
 * <p>This base scales the whole window down by {@link AWLayout#fitScale} when it would not otherwise
 * fit, so every edge stays on screen. When it fits - which is the common case, and every case at a
 * generous GUI scale - {@link #fitScale} is exactly 1 and this class does nothing but forward to its
 * parent. That "does nothing when it fits" property is what makes it safe to sit under a screen that
 * was written without it in mind.
 *
 * <p>The one thing scaling has to carry with it is the mouse. A subclass reads pointer positions in
 * the window's own coordinates, and vanilla widgets hit-test in them too, so both have to be handed
 * coordinates divided back down by the scale. Subclasses do that by routing their overrides through
 * {@link #unscaleX}/{@link #unscaleY}; the render side is handled here.
 */
@OnlyIn(Dist.CLIENT)
public abstract class AWWindowScreen extends AbstractSimiScreen {

    /** How much the window is scaled to fit the screen. 1 when it fits as-is, which is usual. */
    protected float fitScale = 1.0F;

    protected AWWindowScreen(Component title) {
        super(title);
    }

    /**
     * Places the window: scales it to fit if it must, then centres it on the space that leaves.
     *
     * <p>Called after {@code super.init()} has set {@code width}/{@code height}, in place of the
     * {@link AWLayout#anchor} pair a fixed-size screen would use. When the window fits, the arithmetic
     * reduces to the same centre-then-clamp those screens already did.
     */
    protected void placeWindow(int windowWidth, int windowHeight) {
        fitScale = AWLayout.fitScale(width, height, windowWidth, windowHeight);
        // The window is laid out in "virtual" pixels the scale expands back out to the real screen.
        // When nothing is scaled these equal the real screen, so the placement below is unchanged.
        int virtualWidth = Math.round(width / fitScale);
        int virtualHeight = Math.round(height / fitScale);
        guiLeft = AWLayout.anchor(virtualWidth, windowWidth, (virtualWidth - windowWidth) / 2);
        guiTop = AWLayout.anchor(virtualHeight, windowHeight, (virtualHeight - windowHeight) / 2);
    }

    /** A real pointer x, in the window coordinates the layout and the widgets are drawn in. */
    protected double unscaleX(double mouseX) {
        return mouseX / fitScale;
    }

    /** A real pointer y, in the window coordinates the layout and the widgets are drawn in. */
    protected double unscaleY(double mouseY) {
        return mouseY / fitScale;
    }

    /**
     * {@link GuiGraphics#enableScissor} that carries the fit scale with it.
     *
     * <p>A scissor's bounds are taken in real screen pixels and do not go through the pose the way
     * drawing does, so a clip set in window coordinates lands in the wrong place once the window is
     * scaled down - the diagram it was meant to reveal ends up cropped against nothing. Scaling the
     * bounds the same amount the pose scales the picture puts the two back over each other. An identity
     * when the window fits, so the caller can use this everywhere and think about it nowhere.
     */
    protected void enableScaledScissor(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        if (fitScale >= 1.0F) {
            graphics.enableScissor(x1, y1, x2, y2);
            return;
        }
        graphics.enableScissor(Math.round(x1 * fitScale), Math.round(y1 * fitScale),
                Math.round(x2 * fitScale), Math.round(y2 * fitScale));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (fitScale >= 1.0F) {
            super.render(graphics, mouseX, mouseY, partialTicks);
            return;
        }

        // The same sequence AbstractSimiScreen.render runs, under a scale about the top-left corner.
        // Reproduced rather than wrapped because the parent's own pushPose would scale the backgrounds
        // too, and those fill width x height - which under the scale would cover only a corner of the
        // screen and leave the rest of the game sharp behind the window.
        float ticks = AnimationTickHolder.getPartialTicksUI();
        int mx = (int) Math.round(mouseX / (double) fitScale);
        int my = (int) Math.round(mouseY / (double) fitScale);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.scale(fitScale, fitScale, 1.0F);

        prepareFrame();
        renderScaledBackground(graphics, mx, my, ticks);
        renderWindow(graphics, mx, my, ticks);
        for (Renderable widget : getRenderables()) {
            widget.render(graphics, mx, my, ticks);
        }
        renderWindowForeground(graphics, mx, my, ticks);
        endFrame();

        pose.popPose();
    }

    /**
     * The window and menu backgrounds, painted across the whole real screen.
     *
     * <p>Both fill {@code width x height}, which the scale would otherwise shrink into a corner. Sizing
     * them to the virtual screen the scale expands back out means they cover every real pixel - so the
     * dim behind a scaled window looks exactly like the dim behind one that fit. {@code width}/{@code
     * height} are restored before anything else reads them.
     */
    private void renderScaledBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int realWidth = width;
        int realHeight = height;
        try {
            width = Math.round(realWidth / fitScale);
            height = Math.round(realHeight / fitScale);
            renderMenuBackground(graphics);
            renderWindowBackground(graphics, mouseX, mouseY, partialTicks);
        } finally {
            width = realWidth;
            height = realHeight;
        }
    }
}
