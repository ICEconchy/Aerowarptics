package uk.co.iceconchy.aerowarptics.gate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorAccess;
import uk.co.iceconchy.aerowarptics.warpnet.Membership;
import uk.co.iceconchy.aerowarptics.warpnet.NetworkAccess;

import java.util.UUID;

/**
 * A Rift Gate, as the world knows about it rather than as its blocks do.
 *
 * <p>Deliberately the same shape as {@link uk.co.iceconchy.aerowarptics.anchor.WarpAnchor}: named,
 * owned, access-controlled, and listed. A gate and an anchor are both "a place somebody may be sent
 * to", and reusing {@link WarpAnchorAccess} means the rules about who may travel where are written
 * once and behave the same at a chart table and at a gate.
 *
 * <p>Carries its own {@link RiftGateShape}, because the far end of a dial is usually in chunks nobody
 * has loaded and the size of the opening decides both what may pass and how the aperture is drawn.
 *
 * @param controller where the gate's control block stands
 * @param shape      the opening, as it was when the gate last formed
 * @param owner      whoever set the controller down, or {@code null} for a gate from before gates had
 *                   owners, which is anybody's
 * @param network    the network this gate is on, by id, or {@code null} for none
 */
public record RiftGate(UUID id,
                       String name,
                       ResourceKey<Level> dimension,
                       BlockPos controller,
                       RiftGateShape shape,
                       @Nullable UUID owner,
                       String ownerName,
                       WarpAnchorAccess access,
                       @Nullable UUID network,
                       boolean enabled) {

    public static final String UNNAMED = "";

    public static final StreamCodec<RegistryFriendlyByteBuf, RiftGate> STREAM_CODEC =
            StreamCodec.of(RiftGate::encode, RiftGate::decode);

    public RiftGate {
        name = name == null ? UNNAMED : name;
        ownerName = ownerName == null ? "" : ownerName;
        access = access == null ? WarpAnchorAccess.PUBLIC : access;
    }

    public static RiftGate create(UUID id, ResourceKey<Level> dimension, BlockPos controller,
                                  RiftGateShape shape, @Nullable UUID owner, String ownerName) {
        return new RiftGate(id, UNNAMED, dimension, controller.immutable(), shape, owner, ownerName,
                WarpAnchorAccess.PUBLIC, null, true);
    }

    public RiftGate withName(String newName) {
        return new RiftGate(id, newName, dimension, controller, shape, owner, ownerName, access, network, enabled);
    }

    public RiftGate withAccess(WarpAnchorAccess newAccess) {
        return new RiftGate(id, name, dimension, controller, shape, owner, ownerName, newAccess, network, enabled);
    }

    public RiftGate withNetwork(@Nullable UUID newNetwork) {
        return new RiftGate(id, name, dimension, controller, shape, owner, ownerName, access, newNetwork, enabled);
    }

    /**
     * This gate taken off whatever network it was on. One set to Network access becomes Private, for the
     * reason an anchor does: leaving a network must never be what opens a gate to somebody new.
     */
    public RiftGate withoutNetwork() {
        return new RiftGate(id, name, dimension, controller, shape, owner, ownerName,
                access == WarpAnchorAccess.NETWORK ? WarpAnchorAccess.PRIVATE : access, null, enabled);
    }

    public RiftGate withOwner(@Nullable UUID newOwner, String newOwnerName) {
        return new RiftGate(id, name, dimension, controller, shape, newOwner, newOwnerName, access, network, enabled);
    }

    public RiftGate withEnabled(boolean newEnabled) {
        return new RiftGate(id, name, dimension, controller, shape, owner, ownerName, access, network, newEnabled);
    }

    public RiftGate withShape(RiftGateShape newShape) {
        return new RiftGate(id, name, dimension, controller, newShape, owner, ownerName, access, network, enabled);
    }

    public String displayName() {
        return name.isBlank() ? "Gate-" + id.toString().substring(0, 8) : name;
    }

    public boolean isOwnedBy(@Nullable UUID player) {
        return owner != null && owner.equals(player);
    }

    /**
     * Whether a player may dial this gate. Operators bypass the access mode.
     *
     * @param networks who is in which network, for a gate set to Network access
     */
    public boolean isVisibleTo(Player player, Membership networks) {
        return NetworkAccess.permits(access, access.permitted(), isOwnedBy(player.getUUID()),
                player.hasPermissions(2), networks.isMember(network, player.getUUID()));
    }

    /**
     * Whether a player may rename this gate, change who it is open to, or move it between networks.
     *
     * <p>An unowned gate is anybody's - which is what makes a gate placed by a command block usable, and
     * what every gate was before gates had owners.
     */
    public boolean mayEdit(Player player) {
        return owner == null || isOwnedBy(player.getUUID()) || player.hasPermissions(2);
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
        tag.putLong("Controller", controller.asLong());
        tag.put("Shape", shape.save());
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
        tag.putString("Access", access.getSerializedName());
        if (network != null) {
            tag.putUUID("NetworkId", network);
        }
        tag.putBoolean("Enabled", enabled);
        return tag;
    }

    public static RiftGate load(CompoundTag tag) {
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        return new RiftGate(
                tag.getUUID("Id"),
                tag.getString("Name"),
                ResourceKey.create(Registries.DIMENSION,
                        dimension == null ? ResourceLocation.withDefaultNamespace("overworld") : dimension),
                BlockPos.of(tag.getLong("Controller")),
                RiftGateShape.load(tag.getCompound("Shape")),
                tag.hasUUID("Owner") ? tag.getUUID("Owner") : null,
                tag.getString("OwnerName"),
                WarpAnchorAccess.byName(tag.getString("Access")),
                tag.hasUUID("NetworkId") ? tag.getUUID("NetworkId") : null,
                !tag.contains("Enabled") || tag.getBoolean("Enabled"));
    }

    // ----------------------------------------------------------------- wire

    private static void encode(RegistryFriendlyByteBuf buf, RiftGate gate) {
        buf.writeUUID(gate.id);
        buf.writeUtf(gate.name, 64);
        buf.writeResourceKey(gate.dimension);
        buf.writeBlockPos(gate.controller);
        RiftGateShape.STREAM_CODEC.encode(buf, gate.shape);
        buf.writeBoolean(gate.owner != null);
        if (gate.owner != null) {
            buf.writeUUID(gate.owner);
        }
        buf.writeUtf(gate.ownerName, 64);
        buf.writeEnum(gate.access);
        buf.writeBoolean(gate.network != null);
        if (gate.network != null) {
            buf.writeUUID(gate.network);
        }
        buf.writeBoolean(gate.enabled);
    }

    private static RiftGate decode(RegistryFriendlyByteBuf buf) {
        return new RiftGate(
                buf.readUUID(),
                buf.readUtf(64),
                buf.readResourceKey(Registries.DIMENSION),
                buf.readBlockPos(),
                RiftGateShape.STREAM_CODEC.decode(buf),
                buf.readBoolean() ? buf.readUUID() : null,
                buf.readUtf(64),
                buf.readEnum(WarpAnchorAccess.class),
                buf.readBoolean() ? buf.readUUID() : null,
                buf.readBoolean());
    }
}
