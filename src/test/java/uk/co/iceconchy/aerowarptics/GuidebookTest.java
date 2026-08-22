package uk.co.iceconchy.aerowarptics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.client.screen.AWLayout.Rect;
import uk.co.iceconchy.aerowarptics.client.screen.AWLayouts;
import uk.co.iceconchy.aerowarptics.guide.GuideBook;
import uk.co.iceconchy.aerowarptics.guide.GuideDiagram;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the Navigator's Handbook is a book somebody could actually read.
 *
 * <p>Every failure this guards against ships quietly. A line with no translation renders its own key
 * in the middle of a page; a page with one sentence too many draws that sentence off the bottom of
 * the paper, where there is nothing to tell you it was ever there; a chapter with an odd number of
 * pages starts on the right-hand side of somebody else's spread from then on, and so does every
 * chapter after it. None of it throws, and a page nobody has turned to could carry any of it for
 * months.
 *
 * <p>{@code GuideBook} and {@code AWLayouts} are both free of Minecraft, which is what lets this call
 * them directly rather than needing a game to find out.
 */
class GuidebookTest {

    private static final Path LANG =
            Path.of("src/main/resources/assets/aerowarptics/lang/en_us.json");

    private static JsonObject lang() {
        try (Reader reader = Files.newBufferedReader(LANG, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not read " + LANG, e);
        }
    }

    private static String text(JsonObject lang, String key) {
        String full = "aerowarptics." + key;
        return lang.has(full) ? lang.get(full).getAsString() : null;
    }

    // ------------------------------------------------------------------ the words

    @Test
    void everyLineInTheBookIsTranslated() {
        JsonObject lang = lang();
        List<String> missing = new ArrayList<>();

        for (GuideBook.Chapter chapter : GuideBook.chapters()) {
            if (text(lang, chapter.titleKey()) == null) {
                missing.add(chapter.titleKey());
            }
            for (GuideBook.Page page : chapter.pages()) {
                if (text(lang, page.titleKey()) == null) {
                    missing.add(page.titleKey());
                }
                for (GuideBook.Entry entry : page.entries()) {
                    if (entry.ink() == GuideBook.Ink.CONTENTS) {
                        continue;
                    }
                    if (text(lang, entry.key()) == null) {
                        missing.add(entry.key());
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "these would render as raw keys on the page:\n  "
                + String.join("\n  ", missing));
    }

    /**
     * Nothing in the book asks for an argument.
     *
     * <p>The screen hands its lines to the translator with nothing to fill a slot with, so a
     * {@code %s} that found its way into one of these would be drawn to the player as itself.
     * {@code LangFormatTest} cannot see this: it matches keys written out at a call site, and every
     * key here is assembled from the page it is on.
     */
    @Test
    void noLineInTheBookExpectsAnArgument() {
        JsonObject lang = lang();
        List<String> problems = new ArrayList<>();
        for (GuideBook.Page page : GuideBook.pages()) {
            for (GuideBook.Entry entry : page.entries()) {
                if (entry.ink() == GuideBook.Ink.CONTENTS) {
                    continue;
                }
                String value = text(lang, entry.key());
                if (value != null && value.contains("%")) {
                    problems.add(entry.key() + " = \"" + value + "\"");
                }
            }
        }
        assertTrue(problems.isEmpty(), "guide text with a placeholder in it:\n  "
                + String.join("\n  ", problems));
    }

    // ------------------------------------------------------------------ the binding

    /** A chapter with an odd number of pages knocks every chapter after it onto the wrong side. */
    @Test
    void everyChapterFillsWholeSpreads() {
        for (GuideBook.Chapter chapter : GuideBook.chapters()) {
            assertEquals(0, chapter.pages().size() % 2,
                    chapter.id() + " has " + chapter.pages().size()
                            + " pages, so the chapter after it starts mid-spread");
            assertTrue(!chapter.pages().isEmpty(), chapter.id() + " has no pages");
        }
    }

    /** A ribbon jumps to the spread its chapter opens on, and finds that chapter there. */
    @Test
    void everyRibbonLandsOnItsOwnChapter() {
        for (GuideBook.Chapter chapter : GuideBook.chapters()) {
            int spread = GuideBook.spreadOf(chapter);
            assertEquals(chapter, GuideBook.chapterAt(spread),
                    "the ribbon for " + chapter.id() + " opens a spread belonging to "
                            + GuideBook.chapterAt(spread).id());
            assertEquals(chapter.pages().getFirst(), GuideBook.page(spread * 2),
                    chapter.id() + " does not start on the left of its own spread");
        }
    }

    /** Every spread the reader can reach has a chapter to name in the footer. */
    @Test
    void everySpreadBelongsToAChapter() {
        Set<String> ids = new HashSet<>();
        GuideBook.chapters().forEach(chapter -> ids.add(chapter.id()));
        for (int spread = 0; spread < GuideBook.spreads(); spread++) {
            assertTrue(ids.contains(GuideBook.chapterAt(spread).id()),
                    "spread " + spread + " belongs to no chapter");
        }
    }

    @Test
    void pageNamesAreUnique() {
        Set<String> seen = new HashSet<>();
        for (GuideBook.Page page : GuideBook.pages()) {
            assertTrue(seen.add(page.id()),
                    "two pages are both called " + page.id() + ", so they share every key on them");
        }
    }

    /** The numbers a reader counts along run 1, 2, 3 within a chapter, with nothing skipped. */
    @Test
    void stepsAreNumberedInOrderWithinAChapter() {
        for (GuideBook.Chapter chapter : GuideBook.chapters()) {
            int expected = 0;
            for (GuideBook.Page page : chapter.pages()) {
                for (GuideBook.Entry entry : page.entries()) {
                    if (entry.ink() != GuideBook.Ink.STEP) {
                        assertEquals(0, entry.step(),
                                entry.key() + " is not a step but carries a step number");
                        continue;
                    }
                    assertEquals(++expected, entry.step(),
                            "steps in " + chapter.id() + " jump at " + entry.key());
                }
            }
        }
    }

    /**
     * The table of contents is the only thing on its page.
     *
     * <p>{@code HandbookScreen} works out where a contents row is from the top of the page rather
     * than from wherever the renderer happened to have got to, so that a click lands on the line the
     * reader is looking at. That arithmetic is only right while nothing else shares the page - so
     * this is the assertion holding the click and the drawing together.
     */
    @Test
    void theContentsHasItsPageToItself() {
        int found = 0;
        for (GuideBook.Page page : GuideBook.pages()) {
            boolean carries = page.entries().stream()
                    .anyMatch(entry -> entry.ink() == GuideBook.Ink.CONTENTS);
            if (!carries) {
                continue;
            }
            found++;
            assertEquals(1, page.entries().size(),
                    page.id() + " has the contents on it and something else besides");
            assertEquals(GuideDiagram.NONE, page.diagram(),
                    page.id() + " has the contents on it and a diagram above them");
        }
        assertEquals(1, found, "the book has " + found + " tables of contents");
    }

    // -------------------------------------------------------------------- the fit

    /**
     * Widths of the default Minecraft font, near enough to lay type out with.
     *
     * <p>An approximation, deliberately: reading the real glyph widths would mean loading a font from
     * the game, and this test exists to be runnable without one. Everything not listed is six pixels
     * of advance, which is what the great majority of the sheet actually is, and the pessimism that
     * remains is in the right direction - it over-estimates rather than under-estimates the room a
     * sentence needs.
     */
    private static final Map<Character, Integer> WIDTHS = Map.ofEntries(
            Map.entry(' ', 4), Map.entry('i', 2), Map.entry('l', 3), Map.entry('t', 4),
            Map.entry('f', 5), Map.entry('k', 5), Map.entry('I', 4), Map.entry('.', 2),
            Map.entry(',', 2), Map.entry(':', 2), Map.entry(';', 2), Map.entry('!', 2),
            Map.entry('|', 2), Map.entry('\'', 3), Map.entry('"', 5), Map.entry('(', 5),
            Map.entry(')', 5), Map.entry('[', 4), Map.entry(']', 4), Map.entry('-', 6));

    private static int width(String text) {
        int total = 0;
        for (char letter : text.toCharArray()) {
            total += WIDTHS.getOrDefault(letter, 6);
        }
        return total;
    }

    /** How many lines a sentence takes at a given width, wrapped the way the font renderer wraps. */
    private static int lines(String text, int width) {
        int count = 1;
        int used = 0;
        for (String word : text.split(" ")) {
            int wordWidth = width(word);
            int needed = used == 0 ? wordWidth : width(" ") + wordWidth;
            if (used + needed > width && used > 0) {
                count++;
                used = wordWidth;
            } else {
                used += needed;
            }
        }
        return count;
    }

    /**
     * Nothing is written past the bottom of the paper.
     *
     * <p>The failure this catches has no symptom other than absence. A page with one line too many
     * draws that line under the edge of the book, where it is clipped by nothing and simply sits on
     * the leather - or, once the page is turning, is scissored away entirely.
     */
    @Test
    void everyPageHasRoomForWhatIsWrittenOnIt() {
        JsonObject lang = lang();
        List<String> problems = new ArrayList<>();

        for (GuideBook.Page page : GuideBook.pages()) {
            Rect words = AWLayouts.bookText(AWLayouts.book().left(), page.diagram().height());
            int used = 0;
            for (GuideBook.Entry entry : page.entries()) {
                if (entry.ink() == GuideBook.Ink.CONTENTS) {
                    used += GuideBook.chapters().size() * AWLayouts.BOOK_CONTENTS_ROW;
                    continue;
                }
                int indent = switch (entry.ink()) {
                    case STEP -> AWLayouts.BOOK_STEP_INDENT;
                    case NOTE -> AWLayouts.BOOK_NOTE_INDENT;
                    default -> 0;
                };
                String value = text(lang, entry.key());
                if (value == null) {
                    // everyLineInTheBookIsTranslated owns this failure; nothing to measure here.
                    continue;
                }
                used += lines(value, words.width() - indent) * AWLayouts.BOOK_LINE
                        + AWLayouts.BOOK_GAP;
            }
            if (used > words.height()) {
                problems.add(page.id() + " needs " + used + "px of a " + words.height()
                        + "px page - about " + ((used - words.height()) / AWLayouts.BOOK_LINE + 1)
                        + " line(s) too many");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /** A heading has to fit across the page too, or it is trimmed to something meaningless. */
    @Test
    void everyHeadingFitsOnOneLine() {
        JsonObject lang = lang();
        int available = AWLayouts.book().left().width() - AWLayouts.BOOK_MARGIN * 2;
        List<String> problems = new ArrayList<>();
        for (GuideBook.Page page : GuideBook.pages()) {
            String title = text(lang, page.titleKey());
            if (title != null && width(title) > available) {
                problems.add(page.id() + ": \"" + title + "\" is " + width(title)
                        + "px across a " + available + "px page");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /**
     * A chapter title fits on the contents, beside its page number and the dots between them.
     *
     * <p>And in the strip at the foot of the book, which is narrower - it has an arrow at each end.
     */
    @Test
    void everyChapterTitleFitsWhereItIsShown() {
        JsonObject lang = lang();
        AWLayouts.Book book = AWLayouts.book();
        int onContents = book.right().width() - AWLayouts.BOOK_MARGIN * 2 - 20;
        int inFooter = book.forward().x() - book.back().right() - 8;
        List<String> problems = new ArrayList<>();

        for (GuideBook.Chapter chapter : GuideBook.chapters()) {
            String title = text(lang, chapter.titleKey());
            if (title == null) {
                continue;
            }
            if (width(title) > onContents) {
                problems.add("\"" + title + "\" does not fit the contents (" + onContents + "px)");
            }
            if (width(title) > inFooter) {
                problems.add("\"" + title + "\" does not fit the footer (" + inFooter + "px)");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /** The contents lists every chapter, and the page has room for the whole list. */
    @Test
    void theContentsHasRoomForEveryChapter() {
        Rect words = AWLayouts.bookText(AWLayouts.book().right(), 0);
        int needed = GuideBook.chapters().size() * AWLayouts.BOOK_CONTENTS_ROW;
        assertTrue(needed <= words.height(),
                "the contents needs " + needed + "px of a " + words.height() + "px page");
    }

    // ---------------------------------------------------------------- the pictures

    /**
     * A drawing surface that keeps no pixels, only the extent of what it was asked to draw.
     *
     * <p>This is what {@link uk.co.iceconchy.aerowarptics.client.screen.AWDraw} is for: the diagrams
     * are written against one method that puts a coloured rectangle somewhere, so a test can supply
     * that method and find out where a drawing actually goes without a game to draw it in.
     */
    private static final class Extent implements uk.co.iceconchy.aerowarptics.client.screen.AWDraw {

        private int left = Integer.MAX_VALUE;
        private int top = Integer.MAX_VALUE;
        private int right = Integer.MIN_VALUE;
        private int bottom = Integer.MIN_VALUE;
        private int rectangles;

        @Override
        public void fill(int x0, int y0, int x1, int y1, int argb) {
            // An empty rectangle and a wholly transparent one both draw nothing, and neither should
            // be held against a diagram's extent - several of these fade a shape out rather than
            // stopping short of drawing it.
            if (x1 <= x0 || y1 <= y0 || (argb >>> 24) == 0) {
                return;
            }
            rectangles++;
            left = Math.min(left, x0);
            top = Math.min(top, y0);
            right = Math.max(right, x1);
            bottom = Math.max(bottom, y1);
        }
    }

    /**
     * No diagram draws outside the box its page set aside for it.
     *
     * <p>The failure is quiet and specific: a drawing that reaches past its box is painted over the
     * first line of the text underneath it, or over the margin of the paper, and it only does so at
     * the moment of its animation when whatever is moving happens to be at its furthest out. Four of
     * these did exactly that when they were first written - a marker ring that grew five pixels too
     * far, a ring gate half as tall again as its space - which is why the whole animation is walked
     * rather than one frame of it.
     */
    @Test
    void everyDiagramStaysInsideTheBoxThePageGivesIt() {
        Rect page = AWLayouts.book().left();
        int left = page.x() + AWLayouts.BOOK_MARGIN;
        int top = page.y() + AWLayouts.BOOK_HEADING;
        int width = page.width() - AWLayouts.BOOK_MARGIN * 2;
        List<String> problems = new ArrayList<>();

        for (GuideDiagram diagram : GuideDiagram.values()) {
            if (diagram == GuideDiagram.NONE) {
                continue;
            }
            int height = diagram.height();
            // Long enough to cover the slowest cycle any of them runs on, and stepped by an amount
            // that is not a factor of any of their periods.
            for (float ticks = 0.0F; ticks < 700.0F; ticks += 2.3F) {
                Extent extent = new Extent();
                uk.co.iceconchy.aerowarptics.client.screen.GuideDiagrams
                        .draw(extent, diagram, left, top, width, height, ticks);

                // Four is a box, and a box is the least any of these ever comes down to - the
                // siphon at the moment its vessel is empty is an outline and a foot. Below that,
                // something has stopped drawing.
                if (extent.rectangles < 4) {
                    problems.add(diagram + " draws almost nothing at tick " + ticks
                            + " (" + extent.rectangles + " rectangles)");
                    break;
                }
                if (extent.left < left || extent.top < top
                        || extent.right > left + width || extent.bottom > top + height) {
                    problems.add(diagram + " reaches (" + extent.left + "," + extent.top + ")-("
                            + extent.right + "," + extent.bottom + ") at tick " + ticks
                            + ", outside its (" + left + "," + top + ")-("
                            + (left + width) + "," + (top + height) + ") box");
                    break;
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /** A drawing nothing shows is a drawing nobody maintains. */
    @Test
    void everyDiagramIsOnAPage() {
        Set<GuideDiagram> used = new HashSet<>();
        GuideBook.pages().forEach(page -> used.add(page.diagram()));
        List<String> unused = new ArrayList<>();
        for (GuideDiagram diagram : GuideDiagram.values()) {
            if (diagram != GuideDiagram.NONE && !used.contains(diagram)) {
                unused.add(diagram.name());
            }
        }
        assertTrue(unused.isEmpty(), "diagrams no page asks for: " + unused);
    }

    /** A ribbon has to be wide enough to hit and to hold its number. */
    @Test
    void everyChapterHasARibbonWideEnoughToClick() {
        int each = AWLayouts.book().tabs().width() / GuideBook.chapters().size();
        assertTrue(each >= 12, "a ribbon is only " + each + "px wide");
        assertTrue(AWLayouts.book().tabs().height() >= AWLayouts.BOOK_LINE,
                "the ribbons are too short to carry a number");
    }
}
