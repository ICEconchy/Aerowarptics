package uk.co.iceconchy.aerowarptics.berth;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.warpnet.Membership;

import java.util.UUID;

/**
 * A Berth as the server remembers it while its block may not be loaded.
 *
 * <p>A warp is planned into a berth from wherever the ship is, which is nearly always somewhere the
 * station is not loaded - so everything that decides whether a ship may have a slip, and where that puts
 * it, is here and not only in the block: the slip, whose it is, whom it is kept for, and what is in it.
 * The block writes this on every change and takes its occupant <em>from</em> it when its chunk loads, as a
 * Rift Ward takes its charge: the record is what a warp was planned against while the chunk was away.
 *
 * @param anchor     the Warp Anchor whose station this is a berth of, or {@code null} for one not linked
 * @param offsetX    from the block to the middle of the slip's floor, in whole blocks, along the world's axes
 * @param yaw        the way a moored ship's bow points, in degrees, Minecraft's sense
 * @param priority   lower is offered first, among berths that fit a ship equally well
 * @param sternFirst whether a ship comes in backwards, to lie with its bow towards the way it came
 * @param tractor    whether a ship flown in by hand is caught
 * @param network    the network {@link BerthReserve#NETWORK} is about, by id
 * @param state      what is in the slip
 * @param ship       the ship that state is about; or, with {@code castOff}, a ship let go that has not left
 * @param shipName   that ship's name, for a panel that cannot ask an unloaded ship
 * @param until      the overworld game time a reservation lapses at; nought otherwise
 * @param castOff    whether {@code ship} was let go and is still in the slip - not held, not caught again, and
 *                   in the way of anything else until it moves
 */
public record Berth(UUID id,
                    String name,
                    ResourceKey<Level> dimension,
                    BlockPos pos,
                    @Nullable UUID anchor,
                    @Nullable UUID owner,
                    String ownerName,
                    int offsetX,
                    int offsetY,
                    int offsetZ,
                    int length,
                    int width,
                    int height,
                    int yaw,
                    int priority,
                    boolean enabled,
                    boolean sternFirst,
                    boolean tractor,
                    BerthReserve reserve,
                    @Nullable UUID network,
                    BerthState state,
                    @Nullable UUID ship,
                    String shipName,
                    long until,
                    boolean castOff) {

    public Berth {
        name = BerthRules.name(name);
        ownerName = ownerName == null ? "" : ownerName;
        shipName = shipName == null ? "" : shipName;
        pos = pos.immutable();
        offsetX = BerthRules.offset(offsetX);
        offsetY = BerthRules.offset(offsetY);
        offsetZ = BerthRules.offset(offsetZ);
        length = BerthRules.size(length, BerthRules.HARD_MAX_SIZE);
        width = BerthRules.size(width, BerthRules.HARD_MAX_SIZE);
        height = BerthRules.size(height, BerthRules.HARD_MAX_SIZE);
        yaw = BerthRules.yaw(yaw);
        priority = BerthRules.priority(priority);
        reserve = reserve == null ? BerthReserve.ANYONE : reserve;
        state = state == null ? BerthState.EMPTY : state;
        if (ship == null) {
            // A state about a ship with no ship is a slip that can never be given out again.
            state = BerthState.EMPTY;
            shipName = "";
            until = 0L;
            castOff = false;
        }
        if (state != BerthState.RESERVED) {
            until = 0L;
        }
        if (state != BerthState.EMPTY) {
            castOff = false;
        }
    }

    /** A berth as it is first set down: an ordinary slip, open to anyone, with nothing in it. */
    public static Berth create(UUID id, ResourceKey<Level> dimension, BlockPos pos, @Nullable UUID anchor,
                               @Nullable UUID owner, String ownerName, int offsetX, int offsetY, int offsetZ,
                               int yaw) {
        return new Berth(id, "", dimension, pos, anchor, owner, ownerName, offsetX, offsetY, offsetZ,
                BerthRules.DEFAULT_LENGTH, BerthRules.DEFAULT_WIDTH, BerthRules.DEFAULT_HEIGHT, yaw,
                BerthRules.DEFAULT_PRIORITY, true, false, true, BerthReserve.ANYONE, null, BerthState.EMPTY, null, "",
                0L, false);
    }

    // ------------------------------------------------------------------ what it is

    public BerthBox box() {
        return new BerthBox(pos.getX() + 0.5D + offsetX, pos.getY() + offsetY, pos.getZ() + 0.5D + offsetZ, length,
                width, height, yaw);
    }

    /** Display name, falling back to a short form of the id when nobody has named it. */
    public String displayName() {
        return name.isBlank() ? "Berth-" + id.toString().substring(0, 4) : name;
    }

    public boolean isOwnedBy(@Nullable UUID player) {
        return owner != null && owner.equals(player);
    }

    /** Whether whoever opened a rift may be given this berth. Nobody's rift is given only one kept for anyone. */
    public boolean permits(@Nullable UUID player, Membership memberships) {
        return BerthAccess.permits(reserve, isOwnedBy(player),
                player != null && network != null && memberships.isMember(network, player));
    }

    /**
     * Whether the slip has nothing in it and nothing on its way, as far as the record knows. A reservation
     * that has run out is no reservation: the warp it was for was lost, and nothing else will come to end it.
     *
     * <p>The record cannot see a hull somebody has simply left in the slip; whoever asks while the ground is
     * loaded looks as well.
     */
    public boolean free(long now) {
        if (!enabled) {
            return false;
        }
        return switch (state) {
            case EMPTY -> !castOff;
            case RESERVED -> until <= now;
            case DRAWING, MOORED -> false;
        };
    }

    /** Whether a ship is this berth's business just now: reserved for it, drawing it, holding it, or let go of it. */
    public boolean concerns(@Nullable UUID other) {
        return ship != null && ship.equals(other);
    }

    // ------------------------------------------------------------------ what is in it

    private Berth occupied(BerthState newState, @Nullable UUID newShip, String newShipName, long newUntil,
                           boolean newCastOff) {
        return new Berth(id, name, dimension, pos, anchor, owner, ownerName, offsetX, offsetY, offsetZ, length,
                width, height, yaw, priority, enabled, sternFirst, tractor, reserve, network, newState, newShip,
                newShipName, newUntil, newCastOff);
    }

    public Berth reserved(UUID forShip, String forName, long lapsesAt) {
        return occupied(BerthState.RESERVED, forShip, forName, lapsesAt, false);
    }

    public Berth drawing(UUID caught, String caughtName) {
        return occupied(BerthState.DRAWING, caught, caughtName, 0L, false);
    }

    public Berth moored(UUID held, String heldName) {
        return occupied(BerthState.MOORED, held, heldName, 0L, false);
    }

    /** Let go of its ship, which is still lying in the slip. */
    public Berth letGo() {
        return ship == null ? this : occupied(BerthState.EMPTY, ship, shipName, 0L, true);
    }

    public Berth emptied() {
        return occupied(BerthState.EMPTY, null, "", 0L, false);
    }

    // ------------------------------------------------------------------ what its owner set

    public Berth withAnchor(@Nullable UUID newAnchor) {
        return new Berth(id, name, dimension, pos, newAnchor, owner, ownerName, offsetX, offsetY, offsetZ, length,
                width, height, yaw, priority, enabled, sternFirst, tractor, reserve, network, state, ship, shipName,
                until, castOff);
    }

    /**
     * This berth taken off whatever network it named. One kept for a network becomes its owner's alone
     * rather than keeping a setting with nothing behind it - leaving a network must never be what opens a
     * berth to somebody new.
     */
    public Berth withoutNetwork() {
        return new Berth(id, name, dimension, pos, anchor, owner, ownerName, offsetX, offsetY, offsetZ, length,
                width, height, yaw, priority, enabled, sternFirst, tractor,
                reserve == BerthReserve.NETWORK ? BerthReserve.OWNER : reserve, null, state, ship, shipName, until,
                castOff);
    }

    /** Everything a panel sets, in one go; what is in the slip is left as it is. */
    public Berth withSettings(String newName, int newOffsetX, int newOffsetY, int newOffsetZ, int newLength,
                              int newWidth, int newHeight, int newYaw, int newPriority, boolean newEnabled,
                              boolean newSternFirst, boolean newTractor, BerthReserve newReserve,
                              @Nullable UUID newNetwork) {
        return new Berth(id, newName, dimension, pos, anchor, owner, ownerName, newOffsetX, newOffsetY, newOffsetZ,
                newLength, newWidth, newHeight, newYaw, newPriority, newEnabled, newSternFirst, newTractor, newReserve,
                newNetwork, state, ship, shipName, until, castOff);
    }

    // ------------------------------------------------------------------ nbt

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Dimension", dimension.location().toString());
        tag.put("Pos", NbtUtils.writeBlockPos(pos));
        saveSettings(tag);
        return tag;
    }

    /** Everything but where it is - what the block saves too: one way to write it, one way to read it. */
    public void saveSettings(CompoundTag tag) {
        tag.putString("Name", name);
        if (anchor != null) {
            tag.putUUID("Anchor", anchor);
        }
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
        tag.putInt("OffsetX", offsetX);
        tag.putInt("OffsetY", offsetY);
        tag.putInt("OffsetZ", offsetZ);
        tag.putInt("Length", length);
        tag.putInt("Width", width);
        tag.putInt("Height", height);
        tag.putInt("Yaw", yaw);
        tag.putInt("Priority", priority);
        tag.putBoolean("Enabled", enabled);
        tag.putBoolean("SternFirst", sternFirst);
        tag.putBoolean("Tractor", tractor);
        tag.putString("Reserve", reserve.name());
        if (network != null) {
            tag.putUUID("NetworkId", network);
        }
        tag.putString("State", state.name());
        if (ship != null) {
            tag.putUUID("Ship", ship);
            tag.putString("ShipName", shipName);
        }
        tag.putLong("Until", until);
        tag.putBoolean("CastOff", castOff);
    }

    /** Reads a berth back from disk, or {@code null} for a record too broken to keep. */
    @Nullable
    public static Berth load(CompoundTag tag) {
        if (!tag.hasUUID("Id") || !tag.contains("Pos")) {
            return null;
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString("Dimension"));
        BlockPos pos = NbtUtils.readBlockPos(tag, "Pos").orElse(null);
        if (dimensionId == null || pos == null) {
            return null;
        }
        return load(tag, tag.getUUID("Id"), ResourceKey.create(Registries.DIMENSION, dimensionId), pos);
    }

    /** The settings a tag holds, for a berth at a place its reader already knows. */
    public static Berth load(CompoundTag tag, UUID id, ResourceKey<Level> dimension, BlockPos pos) {
        return new Berth(id,
                tag.getString("Name"),
                dimension,
                pos,
                tag.hasUUID("Anchor") ? tag.getUUID("Anchor") : null,
                tag.hasUUID("Owner") ? tag.getUUID("Owner") : null,
                tag.getString("OwnerName"),
                tag.getInt("OffsetX"),
                tag.getInt("OffsetY"),
                tag.getInt("OffsetZ"),
                // A size missing from a save reads as nought, which the record would raise to the smallest
                // slip there is - so a missing one is the ordinary slip instead.
                tag.contains("Length") ? tag.getInt("Length") : BerthRules.DEFAULT_LENGTH,
                tag.contains("Width") ? tag.getInt("Width") : BerthRules.DEFAULT_WIDTH,
                tag.contains("Height") ? tag.getInt("Height") : BerthRules.DEFAULT_HEIGHT,
                tag.getInt("Yaw"),
                tag.contains("Priority") ? tag.getInt("Priority") : BerthRules.DEFAULT_PRIORITY,
                !tag.contains("Enabled") || tag.getBoolean("Enabled"),
                tag.getBoolean("SternFirst"),
                !tag.contains("Tractor") || tag.getBoolean("Tractor"),
                BerthReserve.byName(tag.getString("Reserve")),
                tag.hasUUID("NetworkId") ? tag.getUUID("NetworkId") : null,
                BerthState.byName(tag.getString("State")),
                tag.hasUUID("Ship") ? tag.getUUID("Ship") : null,
                tag.getString("ShipName"),
                tag.getLong("Until"),
                tag.getBoolean("CastOff"));
    }
}
