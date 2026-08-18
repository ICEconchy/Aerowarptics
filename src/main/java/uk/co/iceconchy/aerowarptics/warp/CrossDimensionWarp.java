package uk.co.iceconchy.aerowarptics.warp;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.airship.Airship;

/**
 * Extension point for moving an airship between dimensions.
 *
 * <h2>Why this is not implemented</h2>
 * Sable stores each airship as a sub-level inside one {@code ServerLevel}'s plot grid: its blocks
 * live in reserved chunks of that level, its physics body belongs to that level's
 * {@code SubLevelPhysicsSystem}, and {@code PhysicsPipeline.teleport} takes a position and an
 * orientation but no destination level. Sable 2.0.3 exposes no API for handing a sub-level, its plot
 * chunks, its rigid body and its loading tickets over to a different level, and re-creating the
 * airship in the target dimension by copying blocks would mean rebuilding the ship - exactly what
 * this addon must not do.
 *
 * <p>So cross-dimension warping is refused with {@link WarpFailure#DIMENSION_UNSUPPORTED} unless a
 * downstream mod (or a future Sable release) registers a handler here. Everything else in the warp
 * pipeline - anchors, cost, validation, the state machine, the effects - is already
 * dimension-agnostic, so a handler is the only missing piece.
 *
 * <p>Faking it by teleporting only the players is deliberately not offered.
 */
public final class CrossDimensionWarp {

    /** Implemented by whoever can genuinely move a Sable sub-level across levels. */
    @FunctionalInterface
    public interface Handler {
        /**
         * Moves the airship into {@code destination}.
         *
         * @return {@link WarpFailure#NONE} on success, otherwise the reason it could not be done
         */
        WarpFailure transfer(Airship airship, ServerLevel destination, BlockPos anchorPos);
    }

    @Nullable
    private static Handler handler;

    private CrossDimensionWarp() {
    }

    /** Registers the handler that makes cross-dimension warping possible. */
    public static void setHandler(@Nullable Handler newHandler) {
        handler = newHandler;
    }

    @Nullable
    public static Handler handler() {
        return handler;
    }

    /** True when the config permits it and something is actually able to perform it. */
    public static boolean isSupported() {
        return handler != null && AWConfig.ALLOW_CROSS_DIMENSION_WARP.get();
    }

    /** Performs the transfer, or reports why it cannot happen. */
    public static WarpFailure transfer(Airship airship, ServerLevel destination, BlockPos anchorPos) {
        Handler current = handler;
        if (current == null || !AWConfig.ALLOW_CROSS_DIMENSION_WARP.get()) {
            return WarpFailure.DIMENSION_UNSUPPORTED;
        }
        return current.transfer(airship, destination, anchorPos);
    }
}
