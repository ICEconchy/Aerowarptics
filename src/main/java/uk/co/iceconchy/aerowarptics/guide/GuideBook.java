package uk.co.iceconchy.aerowarptics.guide;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Navigator's Handbook says, and in what order.
 *
 * <p>The mod's three existing ways in each answer a different question. Ponder shows one machine
 * working, JEI says what an item is, and the advancement tree says what to do next. None of them says
 * <em>how to fly a ship somewhere</em> from a standing start, because that is eight steps across four
 * blocks and no single scene holds it. This is that walkthrough.
 *
 * <p>Deliberately free of Minecraft, like {@code AWLayouts} and {@code AWAnim} beside it. The screen
 * draws what is here; a test reads the same structure and checks every line has a translation and
 * every page has room for the words on it. Neither of those failures throws - the first renders a raw
 * key in the middle of a page and the second quietly drops the last sentence off the bottom, and a
 * page nobody has turned to might carry either for months.
 *
 * <h2>Keys are named, never positional</h2>
 * Ponder's text keys are numbered by the order they are called in, which means inserting a sentence
 * mid-scene renumbers every key after it - a trap this codebase has already been caught by once and
 * generates a lang file to avoid. Nothing here is numbered. A line's key is its page and its own name
 * ({@code guide.page.drive_fit.mount}), so a line can be moved, or another dropped in above it,
 * without touching a single translation.
 *
 * <p>Step <em>numbers</em> are the opposite case and are assigned by position on purpose: they are
 * what the reader counts along, so inserting a step ought to renumber the ones below it. They run per
 * chapter, because a chapter is one procedure.
 */
public final class GuideBook {

    /** How a line is set. Not decoration - each of these is read differently. */
    public enum Ink {

        /** Ordinary prose. */
        TEXT,

        /** One numbered instruction in a procedure. */
        STEP,

        /** An aside: the caveat, or the thing that catches people out. */
        NOTE,

        /**
         * The table of contents, which is a list of the chapters rather than a line of text.
         *
         * <p>Carries no key of its own: what it says is the chapter titles, and writing them out a
         * second time here would be two lists to keep in step.
         */
        CONTENTS
    }

    /**
     * One line of the book.
     *
     * @param key  the line's translation key, relative to the mod namespace; empty for {@link
     *             Ink#CONTENTS}
     * @param step which instruction this is within its chapter, or {@code 0} if it is not one
     */
    public record Entry(Ink ink, String key, int step) {
    }

    /**
     * One page: a heading, a picture, and the lines under it.
     *
     * @param id      short name, unique across the book, and the middle of every key on the page
     * @param diagram the animated picture at the top, or {@link GuideDiagram#NONE}
     */
    public record Page(String id, GuideDiagram diagram, List<Entry> entries) {

        public String titleKey() {
            return "guide.page." + id + ".title";
        }
    }

    /** One chapter: a ribbon on the top edge of the book, and the pages behind it. */
    public record Chapter(String id, List<Page> pages) {

        public String titleKey() {
            return "guide.chapter." + id;
        }
    }

    private GuideBook() {
    }

    // ------------------------------------------------------------------ authoring

    /**
     * A line, before it knows which page it is on.
     *
     * <p>The page fills the middle of the key in, so a name cannot drift away from the page it was
     * written for - which is the failure a hand-written key invites and the reason these are not
     * simply written out in full.
     */
    private record Draft(Ink ink, String name) {
    }

    private static Draft text(String name) {
        return new Draft(Ink.TEXT, name);
    }

    private static Draft step(String name) {
        return new Draft(Ink.STEP, name);
    }

    private static Draft note(String name) {
        return new Draft(Ink.NOTE, name);
    }

    private static Draft contents() {
        return new Draft(Ink.CONTENTS, "");
    }

    private record PageDraft(String id, GuideDiagram diagram, List<Draft> lines) {
    }

    private static PageDraft page(String id, GuideDiagram diagram, Draft... lines) {
        return new PageDraft(id, diagram, List.of(lines));
    }

    /**
     * Binds a chapter's pages to their keys and numbers its steps.
     *
     * <p>Numbering happens here rather than at the call site so that the numbers cannot disagree with
     * the order the steps are actually in.
     */
    private static Chapter chapter(String id, PageDraft... drafts) {
        List<Page> pages = new ArrayList<>(drafts.length);
        int step = 0;
        for (PageDraft draft : drafts) {
            List<Entry> entries = new ArrayList<>(draft.lines().size());
            for (Draft line : draft.lines()) {
                int number = line.ink() == Ink.STEP ? ++step : 0;
                String key = line.ink() == Ink.CONTENTS
                        ? ""
                        : "guide.page." + draft.id() + "." + line.name();
                entries.add(new Entry(line.ink(), key, number));
            }
            pages.add(new Page(draft.id(), draft.diagram(), List.copyOf(entries)));
        }
        return new Chapter(id, List.copyOf(pages));
    }

    // ------------------------------------------------------------------- the book

    /**
     * The chapters, in reading order.
     *
     * <p>Every chapter holds an even number of pages, so each one opens on the left of a fresh
     * spread rather than beginning halfway down somebody else's. {@code GuidebookTest} holds that,
     * because it is the sort of thing that quietly stops being true the moment a page is added.
     */
    private static final List<Chapter> CHAPTERS = List.of(

            chapter("contents",
                    page("cover", GuideDiagram.RIFT,
                            text("aim"),
                            text("what"),
                            note("read")),
                    page("contents", GuideDiagram.NONE,
                            contents())),

            chapter("drive",
                    page("drive_fit", GuideDiagram.DRIVE,
                            step("assemble"),
                            step("mount"),
                            step("spin"),
                            note("tiers")),
                    page("drive_bow", GuideDiagram.BOW,
                            text("why"),
                            step("bow"),
                            step("needle"),
                            note("compass"))),

            chapter("charts",
                    page("anchors", GuideDiagram.ANCHOR,
                            text("why"),
                            step("place"),
                            step("name"),
                            note("loaded")),
                    page("chart", GuideDiagram.CHART,
                            step("lay"),
                            step("pick"),
                            text("shows"),
                            note("course"))),

            chapter("launch",
                    page("wiring", GuideDiagram.REDSTONE,
                            text("split"),
                            step("check"),
                            step("signal"),
                            note("button")),
                    page("journey", GuideDiagram.CORRIDOR,
                            text("flown"),
                            text("stay"),
                            note("safe"))),

            chapter("probe",
                    page("probe_scan", GuideDiagram.PROBE,
                            text("why"),
                            step("point"),
                            step("scan"),
                            note("partial")),
                    page("probe_fix", GuideDiagram.FIX,
                            text("fix"),
                            step("set"),
                            note("essence"))),

            chapter("gates",
                    page("gate_ring", GuideDiagram.GATE,
                            step("ring"),
                            step("controller"),
                            step("feed"),
                            note("size")),
                    page("gate_dial", GuideDiagram.GATE_PAIR,
                            step("dial"),
                            text("cross"),
                            note("pays"))),

            chapter("essence",
                    page("siphon", GuideDiagram.SIPHON,
                            text("what"),
                            text("odds"),
                            note("fluid")),
                    page("chute", GuideDiagram.CHUTE,
                            text("what"),
                            step("pair"),
                            step("feed"),
                            note("cost")),
                    page("modulator_link", GuideDiagram.NONE,
                            text("what"),
                            step("place"),
                            step("fuel"),
                            note("fallback")),
                    page("modulator_look", GuideDiagram.NONE,
                            text("choose"),
                            text("where"),
                            note("board"))),

            chapter("fissures",
                    page("fissures", GuideDiagram.GOGGLES,
                            text("what"),
                            text("hidden"),
                            step("wear"),
                            note("ruins")),
                    page("closing", GuideDiagram.FISSURE,
                            step("place"),
                            text("draws"),
                            note("spent"))),

            chapter("help",
                    page("faults", GuideDiagram.CHECKLIST,
                            text("role"),
                            text("order"),
                            note("common")),
                    page("more", GuideDiagram.NONE,
                            text("ponder"),
                            text("jei"),
                            text("goggles"),
                            text("advancements"),
                            note("wiki"))));

    /** Every page in the book, flattened, in reading order. */
    private static final List<Page> PAGES = CHAPTERS.stream()
            .flatMap(chapter -> chapter.pages().stream())
            .toList();

    public static List<Chapter> chapters() {
        return CHAPTERS;
    }

    public static List<Page> pages() {
        return PAGES;
    }

    /** How many spreads the book has: two pages are visible at once, so this is what you page by. */
    public static int spreads() {
        return (PAGES.size() + 1) / 2;
    }

    /** The page shown on the given side of a spread, or empty where the book has run out. */
    public static Page page(int index) {
        return index >= 0 && index < PAGES.size() ? PAGES.get(index) : null;
    }

    /** The spread a chapter opens on, for the ribbon that jumps to it. */
    public static int spreadOf(Chapter chapter) {
        int index = 0;
        for (Chapter candidate : CHAPTERS) {
            if (candidate == chapter) {
                return index / 2;
            }
            index += candidate.pages().size();
        }
        return 0;
    }

    /** Which chapter a spread belongs to, so the right ribbon can be drawn as the open one. */
    public static Chapter chapterAt(int spread) {
        Chapter current = CHAPTERS.getFirst();
        int index = 0;
        for (Chapter candidate : CHAPTERS) {
            if (index / 2 > spread) {
                break;
            }
            current = candidate;
            index += candidate.pages().size();
        }
        return current;
    }
}
