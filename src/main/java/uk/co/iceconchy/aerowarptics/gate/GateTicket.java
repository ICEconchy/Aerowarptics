package uk.co.iceconchy.aerowarptics.gate;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

/**
 * Keeps the far end of an open gate loaded.
 *
 * <p>A gate is dialled from one end and arrived at from the other, and the far end is usually in
 * chunks nobody is standing in. Without this, a vehicle driving through would be relocated into
 * unloaded world - which either stalls the tick thread loading it at the worst moment or, worse, puts
 * a sub-level somewhere the physics system cannot see the ground.
 *
 * <p>The ticket expires on its own and is re-issued while the connection holds. That is deliberate:
 * an explicit release can be missed - a chunk unload, a crash, a gate broken by someone else - and a
 * forced chunk that outlives its reason is a permanent, invisible drain. Something that has to be
 * renewed cannot leak, it can only stop.
 */
public final class GateTicket {

    /** Long enough to outlast any hiccup, short enough that a forgotten gate lets go within a minute. */
    private static final int LIFESPAN_TICKS = 400;

    /** How often an open gate renews its claim. Comfortably inside the lifespan. */
    public static final int RENEW_INTERVAL = 200;

    private static final TicketType<ChunkPos> GATE_OPEN =
            TicketType.create("aerowarptics:gate_open", Comparator.comparingLong(ChunkPos::toLong),
                    LIFESPAN_TICKS);

    private GateTicket() {
    }

    /**
     * Claims the ground around a gate.
     *
     * @param opening how wide the gate is, so a large doorway claims enough for what fits through it
     */
    public static void hold(ServerLevel level, Vec3 centre, double opening) {
        ChunkPos chunk = new ChunkPos(Mth.floor(centre.x) >> 4, Mth.floor(centre.z) >> 4);
        int radius = Mth.clamp((int) Math.ceil(opening / 16.0D) + 1, 2, 6);
        level.getChunkSource().addRegionTicket(GATE_OPEN, chunk, radius, chunk);
    }
}
