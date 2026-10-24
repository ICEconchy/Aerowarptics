package uk.co.iceconchy.aerowarptics.warpnet;

import io.netty.handler.codec.DecoderException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A network: a named group of players who share destinations.
 *
 * <p>An anchor or a gate set to Network access may be travelled to by everybody in its network and
 * nobody else, which is the middle the Public/Private choice never had. A network is also what the
 * destination lists are filtered by.
 *
 * <p>Identified by its id, never its name. A destination remembers the id, so a network can be renamed
 * without every machine on it - most of them in chunks nobody has loaded - having to be found and told.
 *
 * @param id        stable identity
 * @param name      what players call it; unique on the server regardless of case
 * @param owner     the one player who may rename it, change who is in it, or disband it
 * @param ownerName last known name of the owner, for display
 * @param members   everybody else in it, by id, with the name each last went by; never holds the owner
 */
public record WarpNetwork(UUID id, String name, UUID owner, String ownerName, Map<UUID, String> members) {

    /**
     * Most members the wire will carry, and so the most a network may ever have.
     *
     * <p>The server's own limit, {@code maxNetworkMembers}, is at or under this.
     */
    public static final int MAX_MEMBERS = 256;

    private static final int NAME_LENGTH = 32;

    public static final StreamCodec<RegistryFriendlyByteBuf, WarpNetwork> STREAM_CODEC =
            StreamCodec.of(WarpNetwork::encode, WarpNetwork::decode);

    public WarpNetwork {
        Objects.requireNonNull(id, "a network has an id");
        Objects.requireNonNull(owner, "a network has an owner");
        name = name == null ? "" : name;
        ownerName = ownerName == null ? "" : ownerName;
        Map<UUID, String> copy = new LinkedHashMap<>();
        if (members != null) {
            members.forEach((player, known) -> copy.put(player, known == null ? "" : known));
        }
        copy.remove(owner);
        members = Collections.unmodifiableMap(copy);
    }

    public static WarpNetwork create(UUID id, String name, UUID owner, String ownerName) {
        return new WarpNetwork(id, name, owner, ownerName, Map.of());
    }

    public WarpNetwork withName(String newName) {
        return new WarpNetwork(id, newName, owner, ownerName, members);
    }

    public WarpNetwork withMember(UUID player, String playerName) {
        Map<UUID, String> next = new LinkedHashMap<>(members);
        next.put(player, playerName);
        return new WarpNetwork(id, name, owner, ownerName, next);
    }

    public WarpNetwork withoutMember(UUID player) {
        Map<UUID, String> next = new LinkedHashMap<>(members);
        next.remove(player);
        return new WarpNetwork(id, name, owner, ownerName, next);
    }

    /**
     * The same network under a new owner, with the old one kept on as a member.
     *
     * <p>Kept on, because handing a network over is not the same as walking out of it: the old owner's
     * machines are still on it, and dropping them from it would shut those to everybody.
     */
    public WarpNetwork withOwner(UUID newOwner, String newOwnerName) {
        Map<UUID, String> next = new LinkedHashMap<>(members);
        next.put(owner, ownerName);
        next.remove(newOwner);
        return new WarpNetwork(id, name, newOwner, newOwnerName, next);
    }

    public boolean isOwnedBy(@Nullable UUID player) {
        return owner.equals(player);
    }

    /** Whether a player is in this network, as its owner or as a member. */
    public boolean has(@Nullable UUID player) {
        return player != null && (owner.equals(player) || members.containsKey(player));
    }

    /** How many players are in it, the owner included. */
    public int size() {
        return members.size() + 1;
    }

    // ------------------------------------------------------------------ nbt

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName);
        ListTag list = new ListTag();
        members.forEach((player, known) -> {
            CompoundTag member = new CompoundTag();
            member.putUUID("Id", player);
            member.putString("Name", known);
            list.add(member);
        });
        tag.put("Members", list);
        return tag;
    }

    /**
     * Reads a network back from disk.
     *
     * @return {@code null} when the entry has no id, no owner or no name, so a damaged record is dropped
     * instead of stopping the world from loading
     */
    @Nullable
    public static WarpNetwork load(CompoundTag tag) {
        if (!tag.hasUUID("Id") || !tag.hasUUID("Owner") || tag.getString("Name").isBlank()) {
            return null;
        }
        Map<UUID, String> members = new LinkedHashMap<>();
        ListTag list = tag.getList("Members", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size() && members.size() < MAX_MEMBERS; index++) {
            CompoundTag member = list.getCompound(index);
            if (member.hasUUID("Id")) {
                members.put(member.getUUID("Id"), member.getString("Name"));
            }
        }
        return new WarpNetwork(tag.getUUID("Id"), tag.getString("Name"), tag.getUUID("Owner"),
                tag.getString("OwnerName"), members);
    }

    // ----------------------------------------------------------------- wire

    private static void encode(RegistryFriendlyByteBuf buf, WarpNetwork network) {
        buf.writeUUID(network.id);
        buf.writeUtf(cut(network.name), NAME_LENGTH);
        buf.writeUUID(network.owner);
        buf.writeUtf(cut(network.ownerName), NAME_LENGTH);
        // Counted rather than trusted: a network over the limit would otherwise be written whole and
        // refused by whoever reads it, which is a disconnect in place of a short list.
        int count = Math.min(network.members.size(), MAX_MEMBERS);
        buf.writeVarInt(count);
        int written = 0;
        for (Map.Entry<UUID, String> member : network.members.entrySet()) {
            if (written++ >= count) {
                break;
            }
            buf.writeUUID(member.getKey());
            buf.writeUtf(cut(member.getValue()), NAME_LENGTH);
        }
    }

    private static WarpNetwork decode(RegistryFriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String name = buf.readUtf(NAME_LENGTH);
        UUID owner = buf.readUUID();
        String ownerName = buf.readUtf(NAME_LENGTH);
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_MEMBERS) {
            throw new DecoderException("A network of " + count + " members; the most is " + MAX_MEMBERS);
        }
        Map<UUID, String> members = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            members.put(buf.readUUID(), buf.readUtf(NAME_LENGTH));
        }
        return new WarpNetwork(id, name, owner, ownerName, members);
    }

    private static String cut(String text) {
        return text.length() > NAME_LENGTH ? text.substring(0, NAME_LENGTH) : text;
    }
}
