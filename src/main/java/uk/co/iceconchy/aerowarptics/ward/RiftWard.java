package uk.co.iceconchy.aerowarptics.ward;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.warpnet.Membership;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A Rift Ward as the server remembers it while its block may not be loaded.
 *
 * <p>A ward's field has to turn a rift aside whether or not anybody is standing near the machine - a base
 * nobody is at is the one that needs it - so everything that decides that is here and not only in the block:
 * where it is, how wide, whose, whom it turns, and what it has banked. The charge is kept with the time it was
 * counted at, and upkeep since then is taken off whenever something asks ({@link WardCharge#after}); nothing has
 * to tick for an unloaded ward to run down.
 *
 * @param mode      whom it turns aside - see {@link WardAccess}
 * @param networks  the networks that mode is about, by id, at most {@link WardRules#MOST_NETWORKS}. Ids and not
 *                  names, as an anchor's is, so a network renamed while this ward's chunk is unloaded stays
 *                  renamed. They need not be networks the ward's owner is in: a ward may shut out a rival's
 * @param selfTest  whether its owner is turned too, so one player can try their own ward
 * @param enabled   whether its owner has it switched on
 * @param radius    the field's radius as set, in blocks
 * @param charge    millibuckets banked when it was last counted
 * @param countedAt the overworld game time the charge was counted at
 * @param standing  whether the field stood then: a field that has dropped waits for its charge to recover
 */
public record RiftWard(UUID id,
                       ResourceKey<Level> dimension,
                       BlockPos pos,
                       @Nullable UUID owner,
                       String ownerName,
                       WardMode mode,
                       List<UUID> networks,
                       boolean selfTest,
                       boolean enabled,
                       int radius,
                       double charge,
                       long countedAt,
                       boolean standing) {

    public RiftWard {
        ownerName = ownerName == null ? "" : ownerName;
        pos = pos.immutable();
        mode = mode == null ? WardMode.BLOCK_ALL : mode;
        networks = networks == null ? List.of() : List.copyOf(networks);
        charge = Math.max(0.0D, charge);
    }

    public boolean isOwnedBy(@Nullable UUID player) {
        return owner != null && owner.equals(player);
    }

    /** Whether a player is in any of the networks this ward names. Nobody is, of a ward that names none. */
    public boolean lists(@Nullable UUID player, Membership memberships) {
        if (player == null) {
            return false;
        }
        for (UUID network : networks) {
            if (memberships.isMember(network, player)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a rift this player opened comes out where it was aimed - {@link WardAccess}, with this ward's facts. */
    public boolean passes(@Nullable UUID player, Membership memberships) {
        return WardAccess.passes(mode, isOwnedBy(player), selfTest, lists(player, memberships));
    }

    /** This ward no longer naming a network - one that has been disbanded. */
    public RiftWard without(UUID network) {
        List<UUID> left = new ArrayList<>(networks);
        left.remove(network);
        return new RiftWard(id, dimension, pos, owner, ownerName, mode, left, selfTest, enabled, radius, charge,
                countedAt, standing);
    }

    /** This ward as it stands at another time: what it has banked, and whether its field is up. */
    public RiftWard counted(double newCharge, long now, boolean nowStanding) {
        return new RiftWard(id, dimension, pos, owner, ownerName, mode, networks, selfTest, enabled, radius, newCharge,
                now, nowStanding);
    }

    /**
     * This ward emptied and switched off - what an operator leaves of one whose machine is not loaded to be
     * broken. The machine takes it up when it next loads, so its field stays down until its owner sees to it.
     */
    public RiftWard stoodDown(long now) {
        return new RiftWard(id, dimension, pos, owner, ownerName, mode, networks, selfTest, false, radius, 0.0D, now,
                false);
    }

    /** The middle of the machine, where its field is measured from. */
    public double centreX() {
        return pos.getX() + 0.5D;
    }

    public double centreZ() {
        return pos.getZ() + 0.5D;
    }

    // ------------------------------------------------------------------ nbt

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Dimension", dimension.location().toString());
        tag.put("Pos", NbtUtils.writeBlockPos(pos));
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
        saveAccess(tag, mode, networks, selfTest);
        tag.putBoolean("Enabled", enabled);
        tag.putInt("Radius", radius);
        tag.putDouble("Charge", charge);
        tag.putLong("CountedAt", countedAt);
        tag.putBoolean("Standing", standing);
        return tag;
    }

    /** Reads a ward back from disk, or {@code null} for a record too broken to keep. */
    @Nullable
    public static RiftWard load(CompoundTag tag) {
        if (!tag.hasUUID("Id") || !tag.contains("Pos")) {
            return null;
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString("Dimension"));
        BlockPos pos = NbtUtils.readBlockPos(tag, "Pos").orElse(null);
        if (dimensionId == null || pos == null) {
            return null;
        }
        return new RiftWard(
                tag.getUUID("Id"),
                ResourceKey.create(Registries.DIMENSION, dimensionId),
                pos,
                tag.hasUUID("Owner") ? tag.getUUID("Owner") : null,
                tag.getString("OwnerName"),
                loadMode(tag),
                loadNetworks(tag),
                tag.getBoolean("SelfTest"),
                tag.getBoolean("Enabled"),
                Math.max(WardRules.MIN_RADIUS, tag.getInt("Radius")),
                tag.getDouble("Charge"),
                tag.getLong("CountedAt"),
                tag.getBoolean("Standing"));
    }

    // ------------------------------------------------------------------ whom it turns, in a tag
    //
    // Shared with the block, which saves the same three things: one way to write them, one way to read them.

    public static void saveAccess(CompoundTag tag, WardMode mode, List<UUID> networks, boolean selfTest) {
        tag.putString("Mode", mode.name());
        ListTag list = new ListTag();
        for (UUID network : networks) {
            list.add(NbtUtils.createUUID(network));
        }
        tag.put("Networks", list);
        tag.putBoolean("SelfTest", selfTest);
    }

    /**
     * The mode a tag holds. A ward saved while it had the one network box, before modes, let that network through
     * and nobody else - or, with the box empty, its owner alone: so such a save reads as allowing its network, or
     * as blocking everyone, and turns exactly whom it turned.
     */
    public static WardMode loadMode(CompoundTag tag) {
        if (tag.contains("Mode")) {
            return WardMode.byName(tag.getString("Mode"));
        }
        return tag.hasUUID("NetworkId") ? WardMode.ALLOW_NETWORKS : WardMode.BLOCK_ALL;
    }

    public static List<UUID> loadNetworks(CompoundTag tag) {
        List<UUID> networks = new ArrayList<>();
        if (tag.contains("Networks", Tag.TAG_LIST)) {
            for (Tag entry : tag.getList("Networks", Tag.TAG_INT_ARRAY)) {
                if (entry instanceof IntArrayTag ints && ints.size() == 4 && networks.size() < WardRules.MOST_NETWORKS) {
                    UUID network = NbtUtils.loadUUID(entry);
                    if (!networks.contains(network)) {
                        networks.add(network);
                    }
                }
            }
        } else if (tag.hasUUID("NetworkId")) {
            networks.add(tag.getUUID("NetworkId"));
        }
        return networks;
    }
}
