package uk.co.iceconchy.aerowarptics.chute;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorAccess;

import java.util.UUID;

/**
 * A Rift Chute, as the server knows about it rather than as its block does.
 *
 * <p>The same shape as {@link uk.co.iceconchy.aerowarptics.gate.RiftGate} and
 * {@link uk.co.iceconchy.aerowarptics.anchor.WarpAnchor}: named, owned, access-controlled, listed.
 * A chute is a thing you pick out of a list and bind to, so it wants the same record the other two
 * have and the same {@link WarpAnchorAccess} rules, rather than a third idea of who may use what.
 *
 * <p>Unlike a gate, a chute keeps its own {@code partner}. A gate's connection is transient - dialled,
 * held, dropped - because somebody is standing at it. A chute's binding is a piece of infrastructure
 * that has to survive the player walking away, the ship warping to another continent and the server
 * restarting, so it is part of the record rather than part of the block entity's tick.
 *
 * @param pos     where the chute stands, in its own level's coordinates - which for a chute aboard a
 *                ship is a position inside Sable's plot grid rather than a world coordinate
 * @param partner the chute this one sends to, or {@code null} when nothing is bound
 */
public record RiftChute(UUID id,
                        String name,
                        ResourceKey<Level> dimension,
                        BlockPos pos,
                        @Nullable UUID partner,
                        @Nullable UUID owner,
                        String ownerName,
                        WarpAnchorAccess access,
                        boolean enabled) {

    public static final String UNNAMED = "";

    public static final StreamCodec<RegistryFriendlyByteBuf, RiftChute> STREAM_CODEC =
            StreamCodec.of(RiftChute::encode, RiftChute::decode);

    public RiftChute {
        name = name == null ? UNNAMED : name;
        ownerName = ownerName == null ? "" : ownerName;
        access = access == null ? WarpAnchorAccess.PUBLIC : access;
    }

    public static RiftChute create(UUID id, ResourceKey<Level> dimension, BlockPos pos,
                                   @Nullable Player builder) {
        return new RiftChute(id, UNNAMED, dimension, pos.immutable(), null,
                builder == null ? null : builder.getUUID(),
                builder == null ? "" : builder.getGameProfile().getName(),
                WarpAnchorAccess.PUBLIC, true);
    }

    public RiftChute withName(String newName) {
        return new RiftChute(id, newName, dimension, pos, partner, owner, ownerName, access, enabled);
    }

    public RiftChute withPartner(@Nullable UUID newPartner) {
        return new RiftChute(id, name, dimension, pos, newPartner, owner, ownerName, access, enabled);
    }

    public RiftChute withAccess(WarpAnchorAccess newAccess) {
        return new RiftChute(id, name, dimension, pos, partner, owner, ownerName, newAccess, enabled);
    }

    public RiftChute withEnabled(boolean newEnabled) {
        return new RiftChute(id, name, dimension, pos, partner, owner, ownerName, access, newEnabled);
    }

    public String displayName() {
        return name.isBlank() ? "Chute-" + id.toString().substring(0, 8) : name;
    }

    public boolean isBound() {
        return partner != null;
    }

    public boolean isOwnedBy(@Nullable UUID player) {
        return owner != null && owner.equals(player);
    }

    /** Whether a player may bind to this chute. Operators bypass the access mode. */
    public boolean isVisibleTo(Player player) {
        if (!access.permitted()) {
            return isOwnedBy(player.getUUID());
        }
        return switch (access) {
            case PUBLIC -> true;
            case PRIVATE -> isOwnedBy(player.getUUID()) || player.hasPermissions(2);
        };
    }

    // ------------------------------------------------------------------ nbt

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        tag.putString("Dimension", dimension.location().toString());
        tag.putLong("Pos", pos.asLong());
        if (partner != null) {
            tag.putUUID("Partner", partner);
        }
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
        tag.putString("Access", access.getSerializedName());
        tag.putBoolean("Enabled", enabled);
        return tag;
    }

    public static RiftChute load(CompoundTag tag) {
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        return new RiftChute(
                tag.getUUID("Id"),
                tag.getString("Name"),
                ResourceKey.create(Registries.DIMENSION,
                        dimension == null ? ResourceLocation.withDefaultNamespace("overworld") : dimension),
                BlockPos.of(tag.getLong("Pos")),
                tag.hasUUID("Partner") ? tag.getUUID("Partner") : null,
                tag.hasUUID("Owner") ? tag.getUUID("Owner") : null,
                tag.getString("OwnerName"),
                WarpAnchorAccess.byName(tag.getString("Access")),
                !tag.contains("Enabled") || tag.getBoolean("Enabled"));
    }

    // ----------------------------------------------------------------- wire

    private static void encode(RegistryFriendlyByteBuf buf, RiftChute chute) {
        buf.writeUUID(chute.id);
        buf.writeUtf(chute.name, 64);
        buf.writeResourceKey(chute.dimension);
        buf.writeBlockPos(chute.pos);
        buf.writeBoolean(chute.partner != null);
        if (chute.partner != null) {
            buf.writeUUID(chute.partner);
        }
        buf.writeBoolean(chute.owner != null);
        if (chute.owner != null) {
            buf.writeUUID(chute.owner);
        }
        buf.writeUtf(chute.ownerName, 64);
        buf.writeEnum(chute.access);
        buf.writeBoolean(chute.enabled);
    }

    private static RiftChute decode(RegistryFriendlyByteBuf buf) {
        return new RiftChute(
                buf.readUUID(),
                buf.readUtf(64),
                buf.readResourceKey(Registries.DIMENSION),
                buf.readBlockPos(),
                buf.readBoolean() ? buf.readUUID() : null,
                buf.readBoolean() ? buf.readUUID() : null,
                buf.readUtf(64),
                buf.readEnum(WarpAnchorAccess.class),
                buf.readBoolean());
    }
}
