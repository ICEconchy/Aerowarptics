package uk.co.iceconchy.aerowarptics.compat.display;

/**
 * The arithmetic behind the display readouts, with no Minecraft in it.
 *
 * <p>Kept separate for the same reason {@code AWLayout} and {@code AWDraw} are: a class that
 * imports nothing can be tested directly, and both of these are decisions that fail quietly rather
 * than loudly. A mode index that is not clamped reads out the wrong value; a cooldown that rounds
 * down says {@code 0} while the machine is still waiting.
 */
final class DisplayReadout {

    private DisplayReadout() {
    }

    /**
     * Clamps a stored mode index into {@code [0, count)}.
     *
     * <p>The index comes off disk, from a link that may have been configured against an older
     * version of this mod with a different number of modes.
     */
    static int clampMode(int raw, int count) {
        if (count <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(count - 1, raw));
    }

    /**
     * Ticks as whole seconds, rounding up.
     *
     * <p>Ceiling division, so a countdown with a fraction of a second left reads {@code 1} rather
     * than {@code 0}.
     */
    static int seconds(int ticks) {
        return ticks <= 0 ? 0 : (ticks + 19) / 20;
    }
}
