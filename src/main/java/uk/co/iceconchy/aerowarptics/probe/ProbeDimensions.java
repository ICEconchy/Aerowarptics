package uk.co.iceconchy.aerowarptics.probe;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.warp.CrossDimensionWarp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Which dimensions a Rift Probe offers on its panel, and what it calls them.
 *
 * <p>A probe's own dimension is always one of them, and first. The rest are the ones a ship could then be
 * sent into, which is three questions: whether this server crosses dimensions at all, whether its owner
 * has listed the dimension ({@code probe.probeDimensions}), and whether Sable keeps the two the same way
 * - the part of a crossing's preflight that is about the dimensions and not about the ship. A scan of
 * somewhere no drive could follow it is essence spent on a picture, and a server asked to generate ground
 * in a dimension for no reason.
 *
 * <p>The list is the owner's rather than everything the server has because a probe is the one machine in
 * this mod that reaches a place nobody has walked into. An anchor in a mod's pocket dimension had to be
 * carried there; a probe would simply be offered it.
 */
public final class ProbeDimensions {

    /**
     * Most dimensions a panel is sent. A server with more than this has them for something else.
     *
     * <p>Cut here, in {@link #ordered}, and read by the panel packet's codec as its bound - a list codec
     * throws on encode when it is handed too many, so the two must be the one number.
     */
    public static final int MAX_LISTED = 32;

    /** The entry in {@code probe.probeDimensions} that stands for every dimension the server has. */
    public static final String EVERY = "*";

    /** Longest a dimension's plain name may be, so a course's label still fits where it is sent. */
    private static final int LONGEST_NAME = 32;

    private ProbeDimensions() {
    }

    // ------------------------------------------------------------------ rules

    /** Whether the server's list lets a probe look at {@code dimension} from somewhere else. */
    public static boolean listed(List<? extends String> configured, ResourceLocation dimension) {
        String id = dimension.toString();
        for (String entry : configured) {
            String trimmed = entry.trim();
            if (trimmed.equals(EVERY) || trimmed.equals(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The panel's list: the probe's own dimension first, then the others by id.
     *
     * <p>By id rather than in the order the server happens to hold them, so the list reads the same on
     * every opening and after every restart.
     */
    public static List<ResourceKey<Level>> ordered(ResourceKey<Level> own, Collection<ResourceKey<Level>> others) {
        List<ResourceKey<Level>> sorted = new ArrayList<>(others);
        sorted.remove(own);
        sorted.sort(Comparator.comparing(key -> key.location().toString()));
        List<ResourceKey<Level>> ordered = new ArrayList<>(Math.min(MAX_LISTED, sorted.size() + 1));
        ordered.add(own);
        for (ResourceKey<Level> other : sorted) {
            if (ordered.size() >= MAX_LISTED) {
                break;
            }
            if (!ordered.contains(other)) {
                ordered.add(other);
            }
        }
        return ordered;
    }

    // ----------------------------------------------------------------- server

    /** Every dimension a probe standing in {@code here} may sound, its own first. */
    public static List<ResourceKey<Level>> offered(ServerLevel here) {
        List<ResourceKey<Level>> others = new ArrayList<>();
        if (CrossDimensionWarp.isSupported()) {
            List<? extends String> configured = AWConfig.PROBE_DIMENSIONS.get();
            for (ServerLevel level : here.getServer().getAllLevels()) {
                // Never the Rift Corridor, whatever the list says - "*" included. It is a few strips of track in a
                // void, kept for trains; a ship sent there would arrive over nothing, in a dimension nobody may build in.
                if (level != here && !uk.co.iceconchy.aerowarptics.rail.RailCorridor.isCorridor(level)
                        && listed(configured, level.dimension().location())
                        && CrossDimensionWarp.joined(here, level)) {
                    others.add(level.dimension());
                }
            }
        }
        return ordered(here.dimension(), others);
    }

    /** Whether a probe in {@code here} may sound {@code dimension} now. */
    public static boolean offers(ServerLevel here, ResourceKey<Level> dimension) {
        return offered(here).contains(dimension);
    }

    // ------------------------------------------------------------------ names

    /**
     * A dimension's name with no translation to hand: its id's last word or words, capitalised.
     *
     * <p>{@code minecraft:the_nether} is "The Nether", {@code twilightforest:twilight_forest} is "Twilight
     * Forest". What a course's label carries, since the label is written on the server and the server does
     * not know what language anybody reads.
     */
    public static String plainName(ResourceLocation dimension) {
        String path = dimension.getPath();
        String last = path.substring(path.lastIndexOf('/') + 1);
        StringBuilder name = new StringBuilder();
        for (String word : last.split("[_\\-.]+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        if (name.isEmpty()) {
            name.append(dimension);
        }
        return name.length() <= LONGEST_NAME ? name.toString() : name.substring(0, LONGEST_NAME).trim();
    }

    /** The key a translation of a dimension's name would be under. */
    public static String translationKey(ResourceLocation dimension) {
        return "aerowarptics.dimension." + dimension.getNamespace() + "." + dimension.getPath().replace('/', '.');
    }

    /**
     * A dimension's name as a player reads it.
     *
     * <p>The game has no names for its dimensions - a dimension is an id and nothing else - so the three
     * vanilla ones are named in this mod's own language file, and a resource pack can name any other
     * under {@link #translationKey}. Anything nobody has named falls back on {@link #plainName}, which is
     * never a raw key.
     */
    public static Component name(ResourceLocation dimension) {
        return Component.translatableWithFallback(translationKey(dimension), plainName(dimension));
    }
}
