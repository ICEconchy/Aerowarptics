package uk.co.iceconchy.aerowarptics.anchor;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * An immutable snapshot of a registered warp destination.
 *
 * <p>Anchors are identified by a UUID that is minted when the block is placed and kept across
 * breaking and re-placing the block only if the item carries it. The name is a display label and is
 * additionally indexed for lookup, but it is never the identity.
 *
 * @param id        stable identity
 * @param name      player-supplied display name; may be blank
 * @param dimension level the anchor block lives in
 * @param pos       block position of the anchor
 * @param owner     player who placed it, or {@code null} for anchors placed by non-players
 * @param ownerName last known name of the owner, for display
 * @param access    who may warp here
 * @param network   optional grouping label, used to filter long anchor lists
 * @param enabled   whether the anchor currently accepts arrivals
 */
public record WarpAnchor(UUID id,
                         String name,
                         ResourceKey<Level> dimension,
                         BlockPos pos,
                         @Nullable UUID owner,
                         String ownerName,
                         WarpAnchorAccess access,
                         String network,
                         boolean enabled) {

    public static final String UNNAMED = "";

    public static final StreamCodec<RegistryFriendlyByteBuf, WarpAnchor> STREAM_CODEC =
            StreamCodec.of(WarpAnchor::encode, WarpAnchor::decode);

    public WarpAnchor {
        name = name == null ? UNNAMED : name;
        ownerName = ownerName == null ? "" : ownerName;
        network = network == null ? "" : network;
        access = access == null ? WarpAnchorAccess.PUBLIC : access;
    }

    public static WarpAnchor create(UUID id, ResourceKey<Level> dimension, BlockPos pos, @Nullable Player placer) {
        return new WarpAnchor(id, UNNAMED, dimension, pos.immutable(),
                placer == null ? null : placer.getUUID(),
                placer == null ? "" : placer.getGameProfile().getName(),
                WarpAnchorAccess.PUBLIC, "", true);
    }

    public WarpAnchor withName(String newName) {
        return new WarpAnchor(id, newName, dimension, pos, owner, ownerName, access, network, enabled);
    }

    public WarpAnchor withAccess(WarpAnchorAccess newAccess) {
        return new WarpAnchor(id, name, dimension, pos, owner, ownerName, newAccess, network, enabled);
    }

    public WarpAnchor withNetwork(String newNetwork) {
        return new WarpAnchor(id, name, dimension, pos, owner, ownerName, access, newNetwork, enabled);
    }

    public WarpAnchor withEnabled(boolean newEnabled) {
        return new WarpAnchor(id, name, dimension, pos, owner, ownerName, access, network, newEnabled);
    }

    public WarpAnchor withPosition(ResourceKey<Level> newDimension, BlockPos newPos) {
        return new WarpAnchor(id, name, newDimension, newPos.immutable(), owner, ownerName, access, network, enabled);
    }

    /** Display name, falling back to a short form of the UUID when the player has not named it. */
    public String displayName() {
        return name.isBlank() ? "Anchor-" + id.toString().substring(0, 8) : name;
    }

    public boolean isOwnedBy(@Nullable UUID player) {
        return owner != null && owner.equals(player);
    }

    /**
     * Whether a player may select this anchor as a destination.
     *
     * <p>Operators bypass the access mode so a server admin can always recover a ship.
     */
    public boolean isVisibleTo(Player player) {
        if (!access.permitted()) {
            return isOwnedBy(player.getUUID());
        }
        return switch (access) {
            case PUBLIC -> true;
            case PRIVATE -> isOwnedBy(player.getUUID()) || player.hasPermissions(2);
        };
    }

    public boolean isInSameDimension(ServerLevel level) {
        return level.dimension().equals(dimension);
    }

    // ------------------------------------------------------------------ nbt

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        tag.putString("Dimension", dimension.location().toString());
        tag.put("Pos", NbtUtils.writeBlockPos(pos));
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
        tag.putString("Access", access.getSerializedName());
        tag.putString("Network", network);
        tag.putBoolean("Enabled", enabled);
        return tag;
    }

    /**
     * Reads an anchor back from disk.
     *
     * @return {@code null} when the entry is missing required fields or names a malformed dimension,
     * so corrupted records are dropped instead of crashing world load
     */
    @Nullable
    public static WarpAnchor load(CompoundTag tag) {
        if (!tag.hasUUID("Id") || !tag.contains("Pos")) {
            return null;
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dimensionId == null) {
            return null;
        }
        BlockPos pos = NbtUtils.readBlockPos(tag, "Pos").orElse(null);
        if (pos == null) {
            return null;
        }
        return new WarpAnchor(
                tag.getUUID("Id"),
                tag.getString("Name"),
                ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimensionId),
                pos,
                tag.hasUUID("Owner") ? tag.getUUID("Owner") : null,
                tag.getString("OwnerName"),
                WarpAnchorAccess.byName(tag.getString("Access")),
                tag.getString("Network"),
                !tag.contains("Enabled") || tag.getBoolean("Enabled"));
    }

    // --------------------------------------------------------------- network

    private static void encode(RegistryFriendlyByteBuf buf, WarpAnchor anchor) {
        buf.writeUUID(anchor.id);
        buf.writeUtf(anchor.name, 64);
        buf.writeResourceKey(anchor.dimension);
        buf.writeBlockPos(anchor.pos);
        ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8)
                .encode(buf, java.util.Optional.ofNullable(anchor.owner).map(UUID::toString));
        buf.writeUtf(anchor.ownerName, 32);
        buf.writeEnum(anchor.access);
        buf.writeUtf(anchor.network, 32);
        buf.writeBoolean(anchor.enabled);
    }

    private static WarpAnchor decode(RegistryFriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String name = buf.readUtf(64);
        ResourceKey<Level> dimension = buf.readResourceKey(net.minecraft.core.registries.Registries.DIMENSION);
        BlockPos pos = buf.readBlockPos();
        UUID owner = ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).decode(buf)
                .map(WarpAnchor::parseUuid).orElse(null);
        String ownerName = buf.readUtf(32);
        WarpAnchorAccess access = buf.readEnum(WarpAnchorAccess.class);
        String network = buf.readUtf(32);
        boolean enabled = buf.readBoolean();
        return new WarpAnchor(id, name, dimension, pos, owner, ownerName, access, network, enabled);
    }

    @Nullable
    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
