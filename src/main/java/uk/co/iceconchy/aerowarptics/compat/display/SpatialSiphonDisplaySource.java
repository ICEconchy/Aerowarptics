package uk.co.iceconchy.aerowarptics.compat.display;

import net.minecraft.network.chat.MutableComponent;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.siphon.SpatialSiphonBlockEntity;
import uk.co.iceconchy.aerowarptics.util.AWLang;

/**
 * Reports a Spatial Siphon to a Display Link.
 *
 * <p>A siphon fills slowly, in draughts taken off completed warps, and empties whenever anything
 * downstream asks for essence. The reading that matters on a wall is how full it is; the reading
 * that matters on a pipe network is how much room is left before a draught starts being wasted,
 * which is what {@code ROOM} is for.
 *
 * <p>{@code STATE} says whether the vessel has drawn recently &mdash; the same sixty-tick window the
 * renderer uses to decide whether to show the vessel working &mdash; so a board can show a warp
 * being harvested as it happens.
 */
public class SpatialSiphonDisplaySource extends AWDisplaySource<SpatialSiphonBlockEntity> {

    /** Registry path, {@code aerowarptics:spatial_siphon} in full. */
    public static final String SOURCE_ID = "spatial_siphon";

    static final int MODE_STATE = 0;
    static final int MODE_ESSENCE = 1;
    static final int MODE_FILL = 2;
    static final int MODE_ROOM = 3;

    SpatialSiphonDisplaySource() {
        super(SOURCE_ID, SpatialSiphonBlockEntity.class, () -> AWBlockEntities.SPATIAL_SIPHON.get(),
                "state", "essence", "fill", "room");
    }

    @Override
    protected boolean numeric(int mode) {
        return mode == MODE_ESSENCE || mode == MODE_FILL || mode == MODE_ROOM;
    }

    @Override
    protected MutableComponent line(SpatialSiphonBlockEntity siphon, int mode) {
        return switch (mode) {
            case MODE_STATE -> AWLang.translate(siphon.isDrawing()
                    ? "display_source.spatial_siphon.drawing"
                    : "display_source.spatial_siphon.dormant").component();
            case MODE_ESSENCE -> number(siphon.contents().getAmount());
            case MODE_FILL -> percent(siphon.fillLevel());
            case MODE_ROOM -> number(siphon.room());
            // Unreachable: the mode is clamped to the list this source declares.
            default -> EMPTY_LINE;
        };
    }
}
