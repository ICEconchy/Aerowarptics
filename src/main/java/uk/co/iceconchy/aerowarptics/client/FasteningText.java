package uk.co.iceconchy.aerowarptics.client;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import uk.co.iceconchy.aerowarptics.airship.Fastening;
import uk.co.iceconchy.aerowarptics.util.AWLang;

/**
 * How a {@link Fastening} is said to a player: what it is, where it is, and what it is fastened to - "a
 * Docking Connector at 120, 64, -30 is fastened to the ground".
 *
 * <p>Built here from keys rather than sent as text, so it reads in the player's own language. The position
 * goes with the thing rather than at the end of the sentence, because it is the thing a pilot has to go and
 * find.
 */
public final class FasteningText {

    private FasteningText() {
    }

    public static Component describe(Fastening fastening) {
        if (fastening.kind() == Fastening.Kind.PHYSICS_PAUSED) {
            return AWLang.component("warp.fastening.paused");
        }
        Component thing = AWLang.component(fastening.kind().translationKey());
        BlockPos where = fastening.where();
        if (where != null) {
            thing = AWLang.component("warp.fastening.at", thing, where.getX(), where.getY(), where.getZ());
        }
        return AWLang.component(fastening.end().translationKey(), thing);
    }
}
