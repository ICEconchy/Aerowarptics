package uk.co.iceconchy.aerowarptics.rail;

import net.minecraft.network.FriendlyByteBuf;

/**
 * What a gate's dial panel says about its rails, sent with the panel.
 *
 * <p>Counts only, each capped as it is read, so it cannot be made to allocate anything: the panel shows how many
 * lanes there are and how they stand, not the lanes themselves.
 *
 * @param lanes    lanes run into this gate
 * @param ready    of those, how many have their far end laid
 * @param incoming lanes laid out of this gate from elsewhere
 * @param waiting  trains waiting at this gate now
 * @param running  trains in this gate's corridors now, either way
 * @param held     whether redstone is holding trains back here
 */
public record RailSummary(int lanes, int ready, int incoming, int waiting, int running, boolean held) {

    /** Larger than any figure a gate can have, which is all a cap needs to be. */
    private static final int CAP = 999;

    public static final RailSummary NONE = new RailSummary(0, 0, 0, 0, 0, false);

    public RailSummary {
        lanes = clamp(lanes);
        ready = Math.min(clamp(ready), lanes);
        incoming = clamp(incoming);
        waiting = clamp(waiting);
        running = clamp(running);
    }

    /** Whether the gate has anything to do with rails at all, and so whether the panel mentions them. */
    public boolean any() {
        return lanes > 0 || incoming > 0 || waiting > 0 || running > 0;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(CAP, value));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(lanes);
        buf.writeVarInt(ready);
        buf.writeVarInt(incoming);
        buf.writeVarInt(waiting);
        buf.writeVarInt(running);
        buf.writeBoolean(held);
    }

    public static RailSummary decode(FriendlyByteBuf buf) {
        return new RailSummary(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readBoolean());
    }
}
