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

    /** Formats a block distance the way the navigation UI shows it. */
    public static String distance(double blocks) {
        if (blocks >= 10_000.0D) {
            return String.format("%,.1fk", blocks / 1000.0D);
        }
        return String.format("%,d", Math.round(blocks));
    }

    /** Formats a 0..1 fraction as a whole percentage. */
    public static String percent(double fraction) {
        return Math.round(Math.max(0.0D, Math.min(1.0D, fraction)) * 100.0D) + "%";
    }
}
