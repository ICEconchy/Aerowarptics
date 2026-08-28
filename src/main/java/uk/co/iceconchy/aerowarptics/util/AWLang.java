package uk.co.iceconchy.aerowarptics.util;

import net.createmod.catnip.lang.Lang;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.network.chat.Component;
import uk.co.iceconchy.aerowarptics.AeroWarptics;

/**
 * Namespaced entry point into Catnip's {@link LangBuilder}, matching how Create and its addons build
 * translated text and goggle tooltips.
 */
public final class AWLang {

    private AWLang() {
    }

    public static LangBuilder builder() {
        return Lang.builder(AeroWarptics.MODID);
    }

    /** Key is relative to the mod namespace, e.g. {@code gui.rift_drive.title}. */
    public static LangBuilder translate(String key, Object... args) {
        return builder().translate(key, args);
    }

    public static LangBuilder text(String text) {
        return builder().text(text);
    }

    public static Component component(String key, Object... args) {
        return translate(key, args).component();
    }

    /**
     * A distance in blocks, with its unit.
     *
     * <p>The unit is part of what this returns, deliberately. It used to be left to the caller, and
     * five of the six call sites appended {@code " m"} to a string this had already abbreviated with a
     * {@code k} - so a Creative drive's reach read "100,000.0k m", which is not a distance in any
     * unit. The sixth appended nothing and showed a bare number. A formatter that can abbreviate has
     * to own the unit it abbreviated into, or every caller has to know when it did.
     *
     * <p>Metres below ten thousand and kilometres above, and no decimal at all once the number is
     * large enough that a tenth of a kilometre is noise.
     */
    public static String distance(double blocks) {
        if (blocks >= 1_000_000.0D) {
            return String.format("%,d km", Math.round(blocks / 1000.0D));
        }
        if (blocks >= 10_000.0D) {
            return String.format("%,.1f km", blocks / 1000.0D);
        }
        return String.format("%,d m", Math.round(blocks));
    }

    /**
     * A plain count of things - blocks in a hull, items in a chute - with no unit.
     *
     * <p>Separate from {@link #distance} because a hull mass is not a length, and was being run
     * through the distance formatter purely because it also wanted thousands separators. That worked
     * until a hull got big enough to be abbreviated, at which point it would have claimed to be
     * measured in kilometres.
     */
    public static String count(double amount) {
        if (amount >= 10_000.0D) {
            return String.format("%,.1fk", amount / 1000.0D);
        }
        return String.format("%,d", Math.round(amount));
    }

    /**
     * A tank reading in millibuckets: what is held, against what it holds.
     *
     * <p>Here rather than assembled at each screen. Seven of them were building this string by hand
     * out of two raw {@code int}s and a literal {@code " mB"}, so a fissure holding ten thousand read
     * "10105 / 10105 mB" while every other number on the same screen was written with separators. One
     * place also means the unit is one edit away from being translatable, which as a literal repeated
     * seven times it was not.
     */
    public static String essence(double held, double capacity) {
        return count(held) + " / " + count(capacity) + " mB";
    }

    /** A single quantity of Rift Essence, in millibuckets. */
    public static String essence(double millibuckets) {
        return count(millibuckets) + " mB";
    }

    /** Formats a 0..1 fraction as a whole percentage. */
    public static String percent(double fraction) {
        return Math.round(Math.max(0.0D, Math.min(1.0D, fraction)) * 100.0D) + "%";
    }
}
