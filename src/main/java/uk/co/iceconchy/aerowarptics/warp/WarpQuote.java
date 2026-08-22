package uk.co.iceconchy.aerowarptics.warp;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Comparator;
import java.util.UUID;

/**
 * One row of the Rift Navigation list: an anchor plus everything the server worked out about warping
 * to it.
 *
 * <p>The client never computes any of these numbers. It receives the quote, draws it, and sends back
 * only an anchor id if the pilot commits.
 *
 * @param anchorId    destination anchor
 * @param name        display name at the time of the quote
 * @param dimension   dimension the anchor lives in
 * @param pos         anchor block position
 * @param network     optional grouping label
 * @param distance    blocks between the airship and the anchor, or {@code -1} across dimensions
 * @param cost        fraction of a full drive charge this jump consumes
 * @param failure     why this destination is unusable, or {@link WarpFailure#NONE}
 */
public record WarpQuote(UUID anchorId,
                        String name,
                        ResourceKey<Level> dimension,
                        BlockPos pos,
                        String network,
                        double distance,
                        double cost,
                        WarpFailure failure) {

    public static final StreamCodec<RegistryFriendlyByteBuf, WarpQuote> STREAM_CODEC =
            StreamCodec.of(WarpQuote::encode, WarpQuote::decode);

    /**
     * Which destinations are worth keeping when a chart has more than one packet can carry.
     *
     * <p>Reachable ones first, because a destination the drive cannot reach is not a destination; then
     * same-dimension, because cross-dimension quotes have no distance to rank by and warping across
     * dimensions is refused anyway; then nearest first. Name only breaks ties, so the ordering is
     * total and two identical charts truncate identically.
     */
    public static final Comparator<WarpQuote> NEAREST_USABLE_FIRST =
            Comparator.comparing((WarpQuote quote) -> !quote.usable())
                    .thenComparing(quote -> !quote.sameDimension())
                    .thenComparingDouble(WarpQuote::distance)
                    .thenComparing(WarpQuote::name, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(quote -> quote.anchorId().toString());

    public boolean usable() {
        return !failure.isFailure();
    }

    public boolean sameDimension() {
        return distance >= 0.0D;
    }

    private static void encode(RegistryFriendlyByteBuf buf, WarpQuote quote) {
        buf.writeUUID(quote.anchorId);
        buf.writeUtf(quote.name, 64);
        buf.writeResourceKey(quote.dimension);
        buf.writeBlockPos(quote.pos);
        buf.writeUtf(quote.network, 32);
        buf.writeDouble(quote.distance);
        buf.writeDouble(quote.cost);
        buf.writeEnum(quote.failure);
    }

    private static WarpQuote decode(RegistryFriendlyByteBuf buf) {
        return new WarpQuote(
                buf.readUUID(),
                buf.readUtf(64),
                buf.readResourceKey(net.minecraft.core.registries.Registries.DIMENSION),
                buf.readBlockPos(),
                buf.readUtf(32),
                buf.readDouble(),
                buf.readDouble(),
                buf.readEnum(WarpFailure.class));
    }
}
