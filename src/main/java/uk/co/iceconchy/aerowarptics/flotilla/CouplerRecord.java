package uk.co.iceconchy.aerowarptics.flotilla;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * What the server remembers about one Rift Coupler, whether or not it is loaded.
 *
 * <p>A coupler is known by its own id rather than by where it is, because where it is does not hold
 * still. Its block lives in its ship's plot, which moves nowhere when the ship warps - but a warp into
 * another dimension rebuilds the ship with a new UUID and every block at a new plot position, and a
 * flotilla keyed on either would lose its escorts at the first crossing. The id is written into the
 * coupler's own block entity, which the rebuild carries, and the coupler re-announces itself from
 * wherever it wakes up.
 *
 * @param id        the coupler's own id
 * @param dimension where it was last seen
 * @param pos       its block, inside its ship's plot
 * @param ship      its ship's sub-level, or {@code null} when it is not on one
 * @param owner     whoever placed it, or {@code null} for one placed by a command or a structure
 * @param shipName  its ship's name as Sable last reported it, possibly blank
 * @param x         where its ship's centre was last seen, in world space
 * @param y         the same
 * @param z         the same
 * @param role      whether it leads or follows
 * @param tier      its drive's tier index, or {@code -1} with no drive
 */
public record CouplerRecord(UUID id, ResourceKey<Level> dimension, BlockPos pos, @Nullable UUID ship,
                            @Nullable UUID owner, String shipName, double x, double y, double z,
                            CouplerRole role, int tier) {

    public CouplerRecord {
        shipName = shipName == null ? "" : shipName;
        role = role == null ? CouplerRole.ESCORT : role;
    }

    /** Squared distance from this coupler's ship to a point in the same dimension. */
    public double distanceSqr(double px, double py, double pz) {
        double dx = x - px;
        double dy = y - py;
        double dz = z - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Whether this is the same record in every way that matters to a save.
     *
     * <p>The ship's position is left out below a block of movement: a coupler reports itself every second,
     * and a registry that marked itself dirty on every ship that drifted a hand's breadth would be written
     * out at every autosave for nothing.
     */
    public boolean sameAs(CouplerRecord other) {
        return other != null && id.equals(other.id) && dimension.equals(other.dimension) && pos.equals(other.pos)
                && java.util.Objects.equals(ship, other.ship) && java.util.Objects.equals(owner, other.owner)
                && shipName.equals(other.shipName) && role == other.role && tier == other.tier
                && distanceSqr(other.x, other.y, other.z) < 1.0D;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Dimension", dimension.location().toString());
        tag.put("Pos", NbtUtils.writeBlockPos(pos));
        if (ship != null) {
            tag.putUUID("Ship", ship);
        }
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("ShipName", shipName);
        tag.putDouble("X", x);
        tag.putDouble("Y", y);
        tag.putDouble("Z", z);
        tag.putInt("Role", role.ordinal());
        tag.putInt("Tier", tier);
        return tag;
    }

    @Nullable
    public static CouplerRecord load(CompoundTag tag) {
        if (!tag.hasUUID("Id")) {
            return null;
        }
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dimension == null) {
            return null;
        }
        return new CouplerRecord(tag.getUUID("Id"), ResourceKey.create(Registries.DIMENSION, dimension),
                NbtUtils.readBlockPos(tag, "Pos").orElse(BlockPos.ZERO),
                tag.hasUUID("Ship") ? tag.getUUID("Ship") : null,
                tag.hasUUID("Owner") ? tag.getUUID("Owner") : null,
                tag.getString("ShipName"), tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"),
                CouplerRole.byIndex(tag.getInt("Role")), tag.contains("Tier") ? tag.getInt("Tier") : -1);
    }
}
