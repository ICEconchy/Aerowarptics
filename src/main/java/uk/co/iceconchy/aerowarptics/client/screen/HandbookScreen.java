package uk.co.iceconchy.aerowarptics.client.screen;

import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import uk.co.iceconchy.aerowarptics.client.screen.AWLayout.Rect;
import uk.co.iceconchy.aerowarptics.guide.GuideBook;
import uk.co.iceconchy.aerowarptics.guide.GuideDiagram;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.List;

/**
 * The Navigator's Handbook, open on a table.
 *
 * <p>Everything else in this mod's UI is a panel over a machine, and is shaped by the machine: a
 * console reports, a chart lists, a dial connects. This one has no machine behind it and reports
 * nothing, so it is shaped by what it is instead - a bound book, two pages at a time, turned by the
 * corner.
 *
 * <h2>Why it moves</h2>
 * The animation is not decoration. A guide book that jumps from spread to spread gives a reader no
 * sense of where they are in it, which is precisely the sense a book is good at and a wiki is not: a
 * page turned forward comes off the right, a page turned back comes off the left, and after two of
 * them nobody has to be told which arrow does what. The same argument runs through the diagrams -
 * see {@link GuideDiagrams} - and it is why the leaf is drawn squashing towards the spine rather than
 * cross-fading, which would carry none of that.
 *
 * <p>What is drawn is worked out in {@link AWLayouts#book()} and what is written is
 * {@link GuideBook}, both free of Minecraft and both tested. This class is only the part that needs a
 * running game: paint, motion and clicks.
 */
@OnlyIn(Dist.CLIENT)
public class HandbookScreen extends AbstractSimiScreen {

    private static final AWLayouts.Book LAYOUT = AWLayouts.book();

    /**
     * Ticks a leaf takes to go over.
     *
     * <p>Half a second is a page turn; a second is a cutscene. Short enough that a reader hunting for
     * a chapter can hold an arrow down without the book fighting them.
     */
    private static final int TURN_TICKS = 9;

    /** Ticks the book takes to open, once. */
    private static final int OPEN_TICKS = 7;

    /** Ticks the ink on a fresh spread takes to settle. */
    private static final int SETTLE_TICKS = 12;

    private int spread;
    private int turningFrom = -1;
    private int turnTicks;

    private int ticksOpen;

    /**
     * Ticks since this spread arrived, which is what the ink settles against.
     *
     * <p>Starts negative so that nothing is written until the book has finished opening. That is
     * partly taste - a book opens and then you read it - but it is also correctness: the diagrams are
     * wiped in with a scissor, and a scissor is set in plain screen pixels regardless of any transform
     * in force, so while the opening animation still has the whole book scaled down the two would
     * disagree about where the picture is.
     */
    private int settleTicks = -OPEN_TICKS;

    /** How far each chapter's ribbon is pulled out. Eased so a hovered tab slides rather than snaps. */
    private final AWAnim.Eased[] ribbons =
            new AWAnim.Eased[GuideBook.chapters().size()];

    /** The same, for the two corner arrows: 0 is at rest, 1 is under the cursor. */
    private final AWAnim.Eased backArrow = new AWAnim.Eased(0.35F);
    private final AWAnim.Eased forwardArrow = new AWAnim.Eased(0.35F);

    public HandbookScreen() {
        super(AWLang.translate("gui.handbook.title").component());
        for (int index = 0; index < ribbons.length; index++) {
            ribbons[index] = new AWAnim.Eased(0.4F);
        }
    }

    @Override
    protected void init() {
        setWindowSize(AWLayouts.BOOK_WIDTH, AWLayouts.BOOK_HEIGHT);
        super.init();
        guiLeft = AWLayout.anchor(width, AWLayouts.BOOK_WIDTH, guiLeft);
        guiTop = AWLayout.anchor(height, AWLayouts.BOOK_HEIGHT, guiTop);
    }

    // ------------------------------------------------------------------ paging

    private boolean turning() {
        return turningFrom >= 0;
    }

    /**
     * Turns to a spread, however far away it is.
     *
     * <p>One leaf goes over whether the jump is one spread or six. A book flipped from the contents
     * to the last chapter does riffle through everything in between, but animating that would mean
     * six page turns before the reader sees the page they asked for.
     */
    private void turnTo(int target) {
        int clamped = Math.max(0, Math.min(GuideBook.spreads() - 1, target));
        if (clamped == spread || turning()) {
            return;
        }
        turningFrom = spread;
        spread = clamped;
        turnTicks = 0;
        settleTicks = 0;
        playTurn(clamped > turningFrom ? 1.0F : 0.88F);
    }

    private void playTurn(float pitch) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.BOOK_PAGE_TURN, 0.6F, pitch);
        }
    }

    @Override
    public void tick() {
        super.tick();
        ticksOpen++;
        if (turning()) {
            if (++turnTicks >= TURN_TICKS) {
                turningFrom = -1;
            }
        }
        settleTicks++;
        for (AWAnim.Eased ribbon : ribbons) {
            ribbon.tick();
        }
        backArrow.tick();
        forwardArrow.tick();
    }

    // ------------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double x = mouseX - guiLeft;
        double y = mouseY - guiTop;

        if (LAYOUT.back().contains(x, y)) {
            turnTo(spread - 1);
            return true;
        }
        if (LAYOUT.forward().contains(x, y)) {
            turnTo(spread + 1);
            return true;
        }

        int ribbon = ribbonAt(x, y);
        if (ribbon >= 0) {
            turnTo(GuideBook.spreadOf(GuideBook.chapters().get(ribbon)));
            return true;
        }

        int row = contentsRowAt(x, y);
        if (row >= 0) {
            turnTo(GuideBook.spreadOf(GuideBook.chapters().get(row)));
            return true;
        }

        // Anywhere else on a page turns forward, the way a thumb on the outer edge does.
        if (LAYOUT.right().contains(x, y)) {
            turnTo(spread + 1);
            return true;
        }
        if (LAYOUT.left().contains(x, y)) {
            turnTo(spread - 1);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0D) {
            turnTo(spread - (int) Math.signum(scrollY));
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        switch (key) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_PAGE_UP -> {
                turnTo(spread - 1);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_PAGE_DOWN -> {
                turnTo(spread + 1);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                turnTo(0);
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                turnTo(GuideBook.spreads() - 1);
                return true;
            }
            default -> {
                return super.keyPressed(key, scanCode, modifiers);
            }
        }
    }

    // ------------------------------------------------------------------ ribbons

    private Rect ribbonRect(int index) {
        Rect tabs = LAYOUT.tabs();
        int count = GuideBook.chapters().size();
        int each = tabs.width() / count;
        int x = tabs.x() + index * each;
        // The last ribbon takes the remainder, so the row ends flush with the page below it.
        int width = index == count - 1 ? tabs.right() - x : each - 1;
        return new Rect(x, tabs.y(), width, tabs.height());
    }

    private int ribbonAt(double x, double y) {
        for (int index = 0; index < GuideBook.chapters().size(); index++) {
            // Tested against the ribbon at full extension, so a tab does not slip out from under the
            // cursor that is pulling it out.
            Rect rect = ribbonRect(index);
            if (new Rect(rect.x(), rect.y(), rect.width(), rect.height() + 8).contains(x, y)) {
                return index;
            }
        }
        return -1;
    }

    // ----------------------------------------------------------------- contents

    /** Which page of the open spread carries the table of contents, or null if neither does. */
    @Nullable
    private Rect contentsPage() {
        if (turning()) {
            return null;
        }
        GuideBook.Page left = GuideBook.page(spread * 2);
        if (left != null && hasContents(left)) {
            return LAYOUT.left();
        }
        GuideBook.Page right = GuideBook.page(spread * 2 + 1);
        return right != null && hasContents(right) ? LAYOUT.right() : null;
    }

    private static boolean hasContents(GuideBook.Page page) {
        return page.entries().stream().anyMatch(entry -> entry.ink() == GuideBook.Ink.CONTENTS);
    }

    /**
     * Where one line of the contents sits.
     *
     * <p>Worked out rather than remembered from the last frame, so a click lands where the reader saw
     * the line even if a frame has not been drawn since. It holds because the contents is the only
     * thing on its page and that page carries no diagram - which {@code GuidebookTest} asserts, since
     * the day it stops being true this arithmetic would silently start pointing at the wrong chapter.
     */
    private Rect contentsRow(Rect page, int index) {
        return new Rect(page.x() + AWLayouts.BOOK_MARGIN - 1,
                page.y() + AWLayouts.BOOK_HEADING + index * AWLayouts.BOOK_CONTENTS_ROW,
                page.width() - (AWLayouts.BOOK_MARGIN - 1) * 2, AWLayouts.BOOK_CONTENTS_ROW);
    }

    private int contentsRowAt(double x, double y) {
        Rect page = contentsPage();
        if (page == null) {
            return -1;
        }
        for (int index = 0; index < GuideBook.chapters().size(); index++) {
            if (contentsRow(page, index).contains(x, y)) {
                return index;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ render

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        float ticks = ticksOpen + partialTicks;
        float open = AWAnim.easeOut((ticksOpen + partialTicks) / OPEN_TICKS);

        int centreX = guiLeft + AWLayouts.BOOK_WIDTH / 2;
        int centreY = guiTop + AWLayouts.BOOK_HEIGHT / 2;

        // Opening: the covers come apart and the block of pages straightens up. Done to the whole
        // screen with one transform rather than per element, because every part of a book opening
        // moves together - that is what makes it read as one object rather than as a screen
        // assembling itself.
        graphics.pose().pushPose();
        graphics.pose().translate(centreX, centreY, 0.0F);
        graphics.pose().scale(AWAnim.lerp(0.2F, 1.0F, open), AWAnim.lerp(0.75F, 1.0F, open), 1.0F);
        graphics.pose().translate(-centreX, -centreY, 0.0F);

        drawBook(graphics, mouseX, mouseY, ticks, partialTicks);

        graphics.pose().popPose();
    }

    private void drawBook(GuiGraphics graphics, int mouseX, int mouseY, float ticks, float partialTicks) {
        AWBookStyle.cover(on(graphics), guiLeft, guiTop,
                AWLayouts.BOOK_WIDTH - AWLayout.FRAME, AWLayouts.BOOK_HEIGHT - AWLayout.FRAME);

        drawRibbons(graphics, mouseX, mouseY, partialTicks);

        Rect spine = LAYOUT.spine();
        AWBookStyle.spine(on(graphics), guiLeft + spine.x(), guiTop + spine.y(),
                spine.width(), spine.height());

        float reveal = AWAnim.clamp((settleTicks + partialTicks) / SETTLE_TICKS);
        if (turning()) {
            drawTurn(graphics, mouseX, mouseY, ticks, partialTicks, reveal);
        } else {
            drawPage(graphics, GuideBook.page(spread * 2), LAYOUT.left(), false,
                    spread * 2, reveal, ticks, mouseX, mouseY);
            drawPage(graphics, GuideBook.page(spread * 2 + 1), LAYOUT.right(), true,
                    spread * 2 + 1, reveal, ticks, mouseX, mouseY);
        }

        drawFooter(graphics, mouseX, mouseY, partialTicks);
    }

    /**
     * One leaf going over.
     *
     * <p>The sheet being turned has two sides: the page you were reading is its front, and the page
     * you are turning to is its back. So the first half of the animation shows the old right-hand page
     * shrinking into the spine, and the second half shows the new left-hand page opening out of it -
     * and underneath, the new right-hand page is already there, being uncovered.
     *
     * <p>The squash is a horizontal scale about the spine, which is as close to foreshortening as a
     * flat GUI gets and considerably closer than a cross-fade.
     */
    private void drawTurn(GuiGraphics graphics, int mouseX, int mouseY, float ticks,
                          float partialTicks, float reveal) {
        float progress = AWAnim.easeInOut(AWAnim.clamp((turnTicks + partialTicks) / TURN_TICKS));
        boolean forward = spread > turningFrom;

        int fromLeft = turningFrom * 2;
        int toLeft = spread * 2;

        // What stays put while the leaf moves over it.
        int baseLeftIndex = forward ? fromLeft : toLeft;
        int baseRightIndex = forward ? toLeft + 1 : fromLeft + 1;
        drawPage(graphics, GuideBook.page(baseLeftIndex), LAYOUT.left(), false,
                baseLeftIndex, reveal, ticks, mouseX, mouseY);
        drawPage(graphics, GuideBook.page(baseRightIndex), LAYOUT.right(), true,
                baseRightIndex, reveal, ticks, mouseX, mouseY);

        boolean firstHalf = progress < 0.5F;
        float half = firstHalf ? 1.0F - progress * 2.0F : (progress - 0.5F) * 2.0F;

        // Which side the leaf is standing on, and which page is facing us from it.
        boolean onRight = forward == firstHalf;
        int leafIndex = firstHalf
                ? (forward ? fromLeft + 1 : fromLeft)
                : (forward ? toLeft : toLeft + 1);

        Rect page = onRight ? LAYOUT.right() : LAYOUT.left();
        int spineX = guiLeft + (onRight ? page.x() : page.right());
        int width = Math.max(1, Math.round(page.width() * half));
        int top = guiTop + page.y();

        int leafLeft = onRight ? spineX : spineX - width;
        drawLeafShadow(graphics, leafLeft, top, width, page.height(), onRight);

        graphics.enableScissor(leafLeft, top, leafLeft + width, top + page.height());
        graphics.pose().pushPose();
        graphics.pose().translate(spineX, 0.0F, 0.0F);
        graphics.pose().scale(half, 1.0F, 1.0F);
        graphics.pose().translate(-spineX, 0.0F, 0.0F);
        drawPage(graphics, GuideBook.page(leafIndex), page, onRight, leafIndex, 1.0F,
                ticks, -1, -1);
        graphics.pose().popPose();
        graphics.disableScissor();

        // The curve of the leaf, which is the only thing separating a turning page from a page that
        // is merely narrow.
        int shade = AWAnim.fade(0xFF_00_00_00, 0.45F * (1.0F - Math.abs(progress - 0.5F) * 2.0F) + 0.1F);
        if (onRight) {
            on(graphics).shadeAcross(leafLeft, top, width, page.height(), shade, 0x00_00_00_00);
            on(graphics).vLine(leafLeft + width - 1, top, page.height(), AWBookStyle.PAPER);
        } else {
            on(graphics).shadeAcross(leafLeft, top, width, page.height(), 0x00_00_00_00, shade);
            on(graphics).vLine(leafLeft, top, page.height(), AWBookStyle.PAPER);
        }
    }

    /** The shadow the raised leaf throws on the page it is passing over. */
    private void drawLeafShadow(GuiGraphics graphics, int leafLeft, int top, int width, int height,
                                boolean onRight) {
        int depth = 8;
        if (onRight) {
            on(graphics).shadeAcross(leafLeft + width, top, depth, height,
                    AWAnim.fade(0xFF_00_00_00, 0.35F), 0x00_00_00_00);
        } else {
            on(graphics).shadeAcross(leafLeft - depth, top, depth, height,
                    0x00_00_00_00, AWAnim.fade(0xFF_00_00_00, 0.35F));
        }
    }

    // -------------------------------------------------------------------- page

    private void drawPage(GuiGraphics graphics, @Nullable GuideBook.Page page, Rect rect,
                          boolean spineOnLeft, int index, float reveal, float ticks,
                          int mouseX, int mouseY) {
        int left = guiLeft + rect.x();
        int top = guiTop + rect.y();
        AWBookStyle.page(on(graphics), left, top, rect.width(), rect.height(), spineOnLeft);
        if (page == null) {
            return;
        }

        Rect words = AWLayouts.bookText(rect, page.diagram().height());
        int textLeft = guiLeft + words.x();
        int textWidth = words.width();

        if (reveal > 0.0F) {
            Component title = AWLang.translate(page.titleKey()).component();
            graphics.drawString(font, title, left + (rect.width() - font.width(title)) / 2, top + 4,
                    ink(AWBookStyle.INK_TITLE, reveal), false);
        }
        AWBookStyle.ornament(on(graphics), textLeft, top + AWLayouts.BOOK_HEADING - 4, textWidth);

        int y = guiTop + words.y();
        if (page.diagram() != GuideDiagram.NONE) {
            drawDiagram(graphics, page.diagram(), textLeft,
                    top + AWLayouts.BOOK_HEADING, textWidth, reveal, ticks);
        }

        // Lines arrive one after another rather than all at once - the ink settling, which is also
        // what stops a page of six paragraphs landing as a wall.
        int count = page.entries().size();
        int line = 0;
        for (GuideBook.Entry entry : page.entries()) {
            float alpha = AWAnim.clamp(reveal * (count + 2.0F) - line);
            y = switch (entry.ink()) {
                case CONTENTS -> drawContents(graphics, rect, reveal, mouseX, mouseY);
                case STEP -> drawStep(graphics, entry, textLeft, y, textWidth, alpha);
                case NOTE -> drawNote(graphics, entry, textLeft, y, textWidth, alpha);
                case TEXT -> drawText(graphics, entry, textLeft, y, textWidth, alpha);
            };
            line++;
        }

        if (reveal > 0.0F) {
            String number = String.valueOf(index + 1);
            graphics.drawString(font, number, left + (rect.width() - font.width(number)) / 2,
                    top + rect.height() - AWLayouts.BOOK_FOLIO + 2,
                    ink(AWBookStyle.INK_SOFT, reveal), false);
        }
    }

    /**
     * The picture, wiped in from the top as the page settles.
     *
     * <p>A scissor rather than a fade because the diagrams draw in dozens of colours and fading each
     * of them would mean every one of them taking an alpha and threading it through - for an effect
     * that is better anyway. A drawing appearing stroke by stroke is a drawing being drawn.
     */
    private void drawDiagram(GuiGraphics graphics, GuideDiagram diagram, int left, int top,
                             int width, float reveal, float ticks) {
        int height = diagram.height();
        float wipe = AWAnim.easeOut(AWAnim.clamp(reveal * 1.6F));
        int shown = Math.round(height * wipe);
        if (shown > 0) {
            graphics.enableScissor(left, top, left + width, top + shown);
            GuideDiagrams.draw(on(graphics), diagram, left, top, width, height, ticks);
            graphics.disableScissor();
        }
    }

    private int drawText(GuiGraphics graphics, GuideBook.Entry entry, int left, int y,
                         int width, float alpha) {
        return paragraph(graphics, AWLang.translate(entry.key()).component(),
                left, y, width, AWBookStyle.INK, alpha) + AWLayouts.BOOK_GAP;
    }

    private int drawStep(GuiGraphics graphics, GuideBook.Entry entry, int left, int y,
                         int width, float alpha) {
        // Both the number and the note's rule are skipped rather than drawn faintly while a line is
        // still on its way in: ink() has a floor under it, so drawing them at zero would leave a mark
        // sitting on the page ahead of the words it belongs to.
        if (alpha > 0.0F) {
            String number = entry.step() + ".";
            graphics.drawString(font, number, left, y, ink(AWBookStyle.RIFT_INK, alpha), false);
        }
        int indent = AWLayouts.BOOK_STEP_INDENT;
        return paragraph(graphics, AWLang.translate(entry.key()).component(),
                left + indent, y, width - indent, AWBookStyle.INK, alpha) + AWLayouts.BOOK_GAP;
    }

    /** An aside: set in italic, behind a brass rule, so the eye can skip it and come back. */
    private int drawNote(GuiGraphics graphics, GuideBook.Entry entry, int left, int y,
                         int width, float alpha) {
        Component text = AWLang.translate(entry.key()).component()
                .copy().withStyle(ChatFormatting.ITALIC);
        int indent = AWLayouts.BOOK_NOTE_INDENT;
        int bottom = paragraph(graphics, text, left + indent, y, width - indent,
                AWBookStyle.INK_SOFT, alpha);
        if (alpha > 0.0F) {
            graphics.fill(left + 1, y, left + 2, bottom - 1, ink(AWBookStyle.BRASS_DARK, alpha));
        }
        return bottom + AWLayouts.BOOK_GAP;
    }

    private int paragraph(GuiGraphics graphics, Component text, int left, int y, int width,
                          int colour, float alpha) {
        if (alpha <= 0.0F) {
            // Still has to claim its room, or the lines below it would slide up as it arrives.
            return y + font.split(text, width).size() * AWLayouts.BOOK_LINE;
        }
        int line = y;
        for (FormattedCharSequence piece : font.split(text, width)) {
            graphics.drawString(font, piece, left, line, ink(colour, alpha), false);
            line += AWLayouts.BOOK_LINE;
        }
        return line;
    }

    /** The table of contents: chapter, leader dots, and the page it starts on. */
    private int drawContents(GuiGraphics graphics, Rect page, float reveal, int mouseX, int mouseY) {
        List<GuideBook.Chapter> chapters = GuideBook.chapters();
        int bottom = 0;
        for (int index = 0; index < chapters.size(); index++) {
            GuideBook.Chapter chapter = chapters.get(index);
            Rect row = contentsRow(page, index);
            float alpha = AWAnim.clamp(reveal * (chapters.size() + 2.0F) - index);
            int left = guiLeft + row.x();
            int y = guiTop + row.y() + 2;
            bottom = guiTop + row.bottom();

            boolean hovered = row.contains(mouseX - guiLeft, mouseY - guiTop);
            if (hovered) {
                graphics.fill(left - 2, guiTop + row.y(), left + row.width() + 2, bottom,
                        AWAnim.fade(AWBookStyle.RIFT_INK, 0.12F));
            }

            String title = AWLang.translate(chapter.titleKey()).string();
            String number = String.valueOf(GuideBook.spreadOf(chapter) * 2 + 1);
            int colour = ink(hovered ? AWBookStyle.RIFT_INK : AWBookStyle.INK, alpha);
            graphics.drawString(font, title, left, y, colour, false);
            graphics.drawString(font, number, left + row.width() - font.width(number), y,
                    ink(AWBookStyle.INK_SOFT, alpha), false);

            // Leader dots, the way a printed contents joins a chapter to its page.
            int from = left + font.width(title) + 3;
            int to = left + row.width() - font.width(number) - 3;
            for (int dot = from; dot < to; dot += 3) {
                graphics.fill(dot, y + 6, dot + 1, y + 7, ink(AWBookStyle.INK_SOFT, alpha * 0.7F));
            }
        }
        return bottom;
    }

    // ----------------------------------------------------------------- furniture

    private void drawRibbons(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        List<GuideBook.Chapter> chapters = GuideBook.chapters();
        GuideBook.Chapter here = GuideBook.chapterAt(spread);
        int hovered = ribbonAt(mouseX - guiLeft, mouseY - guiTop);

        for (int index = 0; index < chapters.size(); index++) {
            GuideBook.Chapter chapter = chapters.get(index);
            boolean open = chapter == here;
            ribbons[index].set(open ? 8.0F : index == hovered ? 5.0F : 0.0F);

            Rect rect = ribbonRect(index);
            int out = Math.round(ribbons[index].get(partialTicks));
            AWBookStyle.ribbon(on(graphics), guiLeft + rect.x(), guiTop + rect.y(),
                    rect.width(), rect.height(), out, open);

            String label = String.valueOf(index + 1);
            graphics.drawString(font, label,
                    guiLeft + rect.centreX() - font.width(label) / 2,
                    guiTop + rect.y() + 3,
                    open ? 0xFF_F0_E4_D0 : 0xFF_B8_A2_84, false);
        }
    }

    private void drawFooter(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        Rect back = LAYOUT.back();
        Rect forward = LAYOUT.forward();
        boolean canGoBack = spread > 0;
        boolean canGoOn = spread < GuideBook.spreads() - 1;

        backArrow.set(canGoBack && back.contains(mouseX - guiLeft, mouseY - guiTop) ? 1.0F : 0.0F);
        forwardArrow.set(canGoOn && forward.contains(mouseX - guiLeft, mouseY - guiTop) ? 1.0F : 0.0F);

        AWBookStyle.turnArrow(on(graphics), guiLeft + back.centreX(), guiTop + back.centreY(),
                false, backArrow.get(partialTicks), canGoBack);
        AWBookStyle.turnArrow(on(graphics), guiLeft + forward.centreX(), guiTop + forward.centreY(),
                true, forwardArrow.get(partialTicks), canGoOn);

        String chapter = AWLang.translate(GuideBook.chapterAt(spread).titleKey()).string();
        graphics.drawString(font, chapter,
                guiLeft + back.right() + (forward.x() - back.right() - font.width(chapter)) / 2,
                guiTop + back.y() + 5, AWBookStyle.BRASS, false);
    }

    /** This screen's graphics, as the plain drawing surface the book's own code works on. */
    private static AWDraw on(GuiGraphics graphics) {
        return graphics::fill;
    }

    /**
     * A colour at a fraction of its opacity, floored so it still draws.
     *
     * <p>The floor is not tidiness. Minecraft's font renderer reads a colour whose top six alpha bits
     * are all zero as "no alpha given" and draws it fully opaque - so text fading in from nothing
     * would flash at full strength on its first frame, which is the opposite of the effect. Anything
     * that should be invisible is not drawn at all rather than drawn at zero.
     */
    private static int ink(int colour, float alpha) {
        return AWAnim.fade(colour, Math.max(0.06F, AWAnim.clamp(alpha)));
    }

    @Override
    protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY,
                                          float partialTicks) {
        super.renderWindowForeground(graphics, mouseX, mouseY, partialTicks);
        int ribbon = ribbonAt(mouseX - guiLeft, mouseY - guiTop);
        if (ribbon >= 0) {
            graphics.renderTooltip(font,
                    AWLang.translate(GuideBook.chapters().get(ribbon).titleKey()).component(),
                    mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
