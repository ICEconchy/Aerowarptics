package uk.co.iceconchy.aerowarptics.beacon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * The ship a Rift Beacon has been told to call.
 *
 * <p>Stored on the item rather than in a world-wide registry, and that is the whole design. Neither
 * Sable nor Simulated tracks who owns a vessel, so this mod's answer to "may you move this ship" has
 * always been proximity plus presence - you must be within reach of the drive and standing on the
 * hull it is about to move. A beacon exists precisely to move a ship you are <em>not</em> standing
 * on, so it cannot ask that question at the moment it is used.
 *
 * <p>It asks it at the moment it is <em>bound</em> instead, when the player is on the deck with the
 * drive in front of them, and then carries the answer. The consequence is deliberate and worth being
 * plain about: <strong>a bound beacon is a key.</strong> Whoever holds it can call that ship, and
 * handing one over hands over the ship. Rebinding it elsewhere, or breaking the drive, revokes it.
 *
 * <p>The drive is identified by its block position because that position does not move. An airship's
 * blocks live in a reserved plot and stay there; warping changes the hull's pose, not its plot. So a
 * binding taken here is still valid after the ship has crossed the world.
 *
 * @param dimension where the drive is, so a beacon cannot reach across one
 * @param drivePos  the drive's position inside its airship's plot
 * @param shipLabel what to call the ship on the beacon's tooltip
 */
public record RiftBeaconBinding(ResourceKey<Level> dimension, BlockPos drivePos, String shipLabel) {

    public static final Codec<RiftBeaconBinding> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.xmap(
                            id -> ResourceKey.create(Registries.DIMENSION, id),
                            ResourceKey::location).fieldOf("dimension").forGetter(RiftBeaconBinding::dimension),
                    BlockPos.CODEC.fieldOf("drive").forGetter(RiftBeaconBinding::drivePos),
                    Codec.STRING.fieldOf("ship").forGetter(RiftBeaconBinding::shipLabel)
            ).apply(instance, RiftBeaconBinding::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RiftBeaconBinding> STREAM_CODEC =
            StreamCodec.of(
                    (buf, binding) -> {
                        buf.writeResourceKey(binding.dimension);
                        buf.writeBlockPos(binding.drivePos);
                        buf.writeUtf(binding.shipLabel, 64);
                    },
                    buf -> new RiftBeaconBinding(
                            buf.readResourceKey(Registries.DIMENSION),
                            buf.readBlockPos(),
                            buf.readUtf(64)));

    public RiftBeaconBinding {
        shipLabel = shipLabel == null ? "" : shipLabel;
    }

    public boolean isIn(Level level) {
        return level.dimension().equals(dimension);
    }
}
