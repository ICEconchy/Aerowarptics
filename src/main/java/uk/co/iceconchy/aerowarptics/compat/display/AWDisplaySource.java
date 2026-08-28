package uk.co.iceconchy.aerowarptics.compat.display;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.content.trains.display.FlapDisplaySection;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.List;
import java.util.function.Supplier;

/**
 * Base class for every Display Link source this mod adds.
 *
 * <p>Create's display sources are <b>one value per link</b>. A source offers a list of modes on a
 * scroll input, the player picks one, and the link writes that single value to whatever it points
 * at &mdash; a sign, a nixie tube, one row of a Display Board. A board that reports four things is
 * four links stacked up its side, not one source cramming four readings onto a line. An earlier
 * version of this package had an "Overview" mode that did exactly that; it truncated on a nixie
 * tube, clipped on a sign, and lost its separators on a Display Board, whose flap alphabet has no
 * em dash in it. Every mode here is therefore short, and numeric modes are a bare number so that
 * the numeric displays can use them.
 *
 * <p>What a subclass supplies: the registry path, the block entity it reads, the modes in scroll
 * order, and {@link #line(BlockEntity, int)} to turn a machine and a mode into one line. What this
 * class handles is everything Create needs around that and everything that has gone wrong before:
 *
 * <ul>
 *   <li><b>A missing machine clears the target.</b> Returning {@link DisplaySource#EMPTY_LINE} is
 *       Create's signal for "nothing to say", and {@code SingleLineDisplaySource} checks for it by
 *       identity. Returning a "no machine" message instead leaves that message written on the sign
 *       for good once the block is broken.</li>
 *   <li><b>The mode index is clamped.</b> It comes off disk, from a link that may have been
 *       configured against an older version of this mod with a different number of modes. An
 *       unclamped index falls through to whatever the switch's default branch happens to be, which
 *       is a reading of something the player did not ask for.</li>
 *   <li><b>The scroll input is titled and positioned the way Create's own are</b> &mdash; x 0,
 *       width 120, with a title above it, matching {@code FillLevelDisplaySource}. Without the
 *       title the widget sits in the configuration screen unlabelled.</li>
 *   <li><b>Numeric modes get a numeric Display Board layout.</b> A flap display renders a value
 *       through a named layout with a character cycle; the default cycle is the alphabet, which
 *       spins a long way round to reach a digit. {@link #numeric(int)} says which modes are plain
 *       numbers, and those get Create's {@code numeric} cycle instead.</li>
 * </ul>
 *
 * <p>Line text that needs formatting is passed to {@code AWLang} by subclasses as a whole literal
 * key rather than assembled here from a prefix, so that {@code LangCoverageTest} can see it. A key
 * built out of a variable is a key that test skips, and a missing translation renders as a raw key
 * on a sign with nothing at all to warn you.
 *
 * @param <T> the block entity this source reads
 */
public abstract class AWDisplaySource<T extends BlockEntity> extends SingleLineDisplaySource {

    /**
     * Config tag key the mode scroll input writes into the link's {@code sourceConfig}.
     *
     * <p>Create's own sources use this name too; it is per-source storage, so there is no clash.
     * Changing it would lose every player's saved selection.
     */
    public static final String MODE_KEY = "Mode";

    /** Flap display layout names, as {@code NumericSingleLineDisplaySource} and its parent use them. */
    private static final String NUMERIC_LAYOUT = "Number";
    private static final String NUMERIC_CYCLE = "numeric";

    private final String sourceId;
    private final Class<T> machineType;
    private final Supplier<? extends BlockEntityType<?>> blockEntityType;
    private final List<String> modes;

    /**
     * @param sourceId        registry path, becoming {@code aerowarptics:<sourceId>}. Baked into
     *                        every configured link's save data &mdash; changing it orphans them all.
     * @param machineType     block entity class this source reads
     * @param blockEntityType supplier for the type to register against, resolved during setup.
     *                        Pass a lambda ({@code () -> AWBlockEntities.X.get()}), never the
     *                        deferred holder itself: naming the holder loads {@code AWBlockEntities},
     *                        whose static initialiser needs a bootstrapped Minecraft, and that puts
     *                        the whole source list out of reach of the tests.
     * @param modes           mode names in scroll order; the index into this list is what is stored
     */
    protected AWDisplaySource(String sourceId, Class<T> machineType,
                              Supplier<? extends BlockEntityType<?>> blockEntityType,
                              String... modes) {
        this.sourceId = sourceId;
        this.machineType = machineType;
        this.blockEntityType = blockEntityType;
        this.modes = List.of(modes);
    }

    // -------------------------------------------------------- what a subclass provides

    /**
     * One line for the machine in the given mode.
     *
     * <p>Return {@link DisplaySource#EMPTY_LINE} for "nothing to report" &mdash; no fault, no course
     * set &mdash; and the target is cleared rather than being left holding a stale reading.
     *
     * @param mode already clamped into range, so a subclass may index its own arrays with it
     */
    protected abstract MutableComponent line(T machine, int mode);

    /**
     * Whether a mode produces a bare number, and so should use the numeric flap cycle.
     *
     * <p>Default is no. A mode that returns "62%" counts as numeric &mdash; Create's numeric cycle
     * carries the percent sign.
     */
    protected boolean numeric(int mode) {
        return false;
    }

    // -------------------------------------------------------- accessors used by the registrar and tests

    public String sourceId() {
        return sourceId;
    }

    /** Mode names in scroll order. Immutable. */
    public List<String> modes() {
        return modes;
    }

    /**
     * The block entity this source reads.
     *
     * <p>The class rather than the {@link BlockEntityType}, because the type is behind a deferred
     * holder that only resolves once registration has run &mdash; which is exactly what a unit test
     * cannot stand up, and the coverage test needs to know what each source is for.
     */
    public Class<T> machineType() {
        return machineType;
    }

    public BlockEntityType<?> blockEntityType() {
        return blockEntityType.get();
    }

    // -------------------------------------------------------- line provision

    @Override
    protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
        BlockEntity be = context.getSourceBlockEntity();
        if (!machineType.isInstance(be)) {
            // The block has gone, or the link is pointed at something else now. Clear the target
            // rather than leaving a message on it that will never be replaced.
            return EMPTY_LINE;
        }
        return line(machineType.cast(be), mode(context));
    }

    /** The selected mode, clamped into range. See {@link DisplayReadout#clampMode}. */
    protected final int mode(DisplayLinkContext context) {
        return DisplayReadout.clampMode(context.sourceConfig().getInt(MODE_KEY), modes.size());
    }

    // -------------------------------------------------------- configuration screen

    @Override
    protected boolean allowsLabeling(DisplayLinkContext context) {
        return true;
    }

    @Override
    public void initConfigurationWidgets(DisplayLinkContext context,
                                         ModularGuiLineBuilder builder, boolean isFirstLine) {
        super.initConfigurationWidgets(context, builder, isFirstLine);
        // The parent puts its labelling text box on the first line; the mode scroll goes on the
        // second, which is the shape every one of Create's own multi-widget sources has.
        if (isFirstLine) {
            return;
        }
        builder.addSelectionScrollInput(0, 120,
                (scroll, label) -> scroll
                        .forOptions(CreateLang.translatedOptions(optionPrefix(), modes.toArray(String[]::new)))
                        .titled(CreateLang.translateDirect(optionPrefix() + ".mode")),
                MODE_KEY);
    }

    /**
     * Prefix for the scroll input's option and title keys.
     *
     * <p>{@code CreateLang} hard-prefixes {@code create.}, so these keys live under Create's
     * namespace however much they belong to this mod: {@code create.display_source.rift_gate.state}.
     * The source's own <em>name</em> in the dropdown does not &mdash; {@code DisplaySource.getName}
     * builds that one from the registry id's namespace, so it is
     * {@code aerowarptics.display_source.rift_gate}. Both sets have to exist in the lang file.
     */
    private String optionPrefix() {
        return "display_source." + sourceId;
    }

    // -------------------------------------------------------- Display Board layout

    @Override
    protected String getFlapDisplayLayoutName(DisplayLinkContext context) {
        return numeric(mode(context)) ? NUMERIC_LAYOUT : super.getFlapDisplayLayoutName(context);
    }

    @Override
    protected FlapDisplaySection createSectionForValue(DisplayLinkContext context, int size) {
        if (!numeric(mode(context))) {
            return super.createSectionForValue(context, size);
        }
        // Mirrors NumericSingleLineDisplaySource: the same width per character, on the digit cycle.
        return new FlapDisplaySection(size * 7.0F, NUMERIC_CYCLE, false, false);
    }

    // -------------------------------------------------------- shared formatting

    /** A literal line. */
    protected static MutableComponent text(String text) {
        return AWLang.text(text).component();
    }

    /** A bare number, for the numeric modes. */
    protected static MutableComponent number(int value) {
        return text(Integer.toString(value));
    }

    /** A 0..1 fraction as a whole percentage, worded as the machine screens word it. */
    protected static MutableComponent percent(float fraction) {
        return text(AWLang.percent(fraction));
    }

    /**
     * Ticks as whole seconds, rounding up. See {@link DisplayReadout#seconds}.
     *
     * <p>A readout that says zero while the machine is still waiting is a readout the pilot stops
     * trusting.
     */
    protected static int seconds(int ticks) {
        return DisplayReadout.seconds(ticks);
    }
}
