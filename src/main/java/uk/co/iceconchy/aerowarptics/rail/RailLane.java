package uk.co.iceconchy.aerowarptics.rail;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One rail lane: a track run into a gate, the corridor it leads into, and the stub it comes out of at the far end.
 *
 * <p>Owned by the gate the track was run into ({@link #gate}). The far end follows that gate's route, so it is the
 * part that changes: re-route the gate and every one of its lanes comes out of the new gate instead, while the near
 * track and the corridor stay where they are.
 *
 * @param id        the lane's own id
 * @param gate      the gate the near track runs into, which owns the lane
 * @param dimension that gate's dimension
 * @param track     the near track - the player's own, made into a portal track by Create
 * @param cell      the cell of the near gate's opening the track points into
 * @param heading   which way a train going in at the near end is travelling
 * @param slot      the lane's corridor, in {@link CorridorSlots}
 * @param length    the corridor's length, as laid
 * @param material  the track block Create laid in the corridor, which the far stub is laid in too
 * @param far       the gate the far end comes out of, or {@code null} while there is none
 * @param farDimension that gate's dimension
 * @param farCell   the far gate's cell a train comes out of
 * @param farStub   the portal track just outside it, bound to the corridor's east end
 * @param farHeading which way a train comes out
 * @param state     how far the lane is laid
 */
public record RailLane(UUID id,
                       UUID gate,
                       ResourceKey<Level> dimension,
                       BlockPos track,
                       BlockPos cell,
                       Direction heading,
                       int slot,
                       int length,
                       ResourceLocation material,
                       @Nullable UUID far,
                       @Nullable ResourceKey<Level> farDimension,
                       @Nullable BlockPos farCell,
                       @Nullable BlockPos farStub,
                       @Nullable Direction farHeading,
                       RailLaneState state) {

    public RailLane {
        track = track.immutable();
        cell = cell.immutable();
        farCell = farCell == null ? null : farCell.immutable();
        farStub = farStub == null ? null : farStub.immutable();
    }

    public static RailLane laid(UUID gate, ResourceKey<Level> dimension, BlockPos track, BlockPos cell,
                                Direction heading, int slot, int length, ResourceLocation material,
                                @Nullable UUID far) {
        return new RailLane(UUID.randomUUID(), gate, dimension, track, cell, heading, slot, length, material, far,
                null, null, null, null, far == null ? RailLaneState.NO_ROUTE : RailLaneState.FAR_PENDING);
    }

    /** The same lane with no far end yet, routed to {@code target} (or nowhere). */
    public RailLane routedTo(@Nullable UUID target) {
        return new RailLane(id, gate, dimension, track, cell, heading, slot, length, material, target, null, null,
                null, null, target == null ? RailLaneState.NO_ROUTE : RailLaneState.FAR_PENDING);
    }

    /** The same lane with its far end laid. */
    public RailLane withFarEnd(ResourceKey<Level> dimension, BlockPos cell, BlockPos stub, Direction heading) {
        return new RailLane(id, gate, this.dimension, track, this.cell, this.heading, slot, length, material, far,
                dimension, cell, stub, heading, RailLaneState.READY);
    }

    public RailLane withState(RailLaneState next) {
        return new RailLane(id, gate, dimension, track, cell, heading, slot, length, material, far, farDimension,
                farCell, farStub, farHeading, next);
    }

    public boolean ready() {
        return state == RailLaneState.READY && far != null && farDimension != null && farStub != null;
    }

    /** Whether the lane runs between these two gates, either way round. */
    public boolean joins(UUID one, @Nullable UUID other) {
        return other != null && (gate.equals(one) && other.equals(far) || gate.equals(other) && one.equals(far));
    }

    // ------------------------------------------------------------------ nbt

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("Gate", gate);
        tag.putString("Dimension", dimension.location().toString());
        tag.putLong("Track", track.asLong());
        tag.putLong("Cell", cell.asLong());
        tag.putString("Heading", heading.getSerializedName());
        tag.putInt("Slot", slot);
        tag.putInt("Length", length);
        tag.putString("Material", material.toString());
        if (far != null) {
            tag.putUUID("Far", far);
        }
        if (farDimension != null && farCell != null && farStub != null && farHeading != null) {
            tag.putString("FarDimension", farDimension.location().toString());
            tag.putLong("FarCell", farCell.asLong());
            tag.putLong("FarStub", farStub.asLong());
            tag.putString("FarHeading", farHeading.getSerializedName());
        }
        tag.putString("State", state.getSerializedName());
        return tag;
    }

    @Nullable
    public static RailLane load(CompoundTag tag) {
        ResourceKey<Level> dimension = dimension(tag.getString("Dimension"));
        Direction heading = Direction.byName(tag.getString("Heading"));
        ResourceLocation material = ResourceLocation.tryParse(tag.getString("Material"));
        if (!tag.hasUUID("Id") || !tag.hasUUID("Gate") || dimension == null || heading == null || material == null) {
            return null;
        }
        ResourceKey<Level> farDimension = tag.contains("FarDimension") ? dimension(tag.getString("FarDimension")) : null;
        Direction farHeading = tag.contains("FarHeading") ? Direction.byName(tag.getString("FarHeading")) : null;
        boolean farLaid = farDimension != null && farHeading != null;
        RailLaneState state = RailLaneState.byName(tag.getString("State"));
        if (state == RailLaneState.READY && !farLaid) {
            state = RailLaneState.FAR_PENDING;
        }
        return new RailLane(tag.getUUID("Id"), tag.getUUID("Gate"), dimension, BlockPos.of(tag.getLong("Track")),
                BlockPos.of(tag.getLong("Cell")), heading, tag.getInt("Slot"), tag.getInt("Length"), material,
                tag.hasUUID("Far") ? tag.getUUID("Far") : null,
                farLaid ? farDimension : null,
                farLaid ? BlockPos.of(tag.getLong("FarCell")) : null,
                farLaid ? BlockPos.of(tag.getLong("FarStub")) : null,
                farLaid ? farHeading : null,
                state);
    }

    @Nullable
    private static ResourceKey<Level> dimension(String name) {
        ResourceLocation location = ResourceLocation.tryParse(name);
        return location == null ? null : ResourceKey.create(Registries.DIMENSION, location);
    }
}
