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
 * What a drive is told when it joins a fold: which fold, where the fleet is going, what its share costs,
 * and what the shared wake looks like.
 *
 * <p>Handed to every drive in the fold when Fold is pressed - the flagship's with {@code lead} set - and
 * kept on the drive for as long as its sequence runs. Saved with the drive, so that a crossing into
 * another dimension, which rebuilds the drive from its saved form, carries it; any other reload drops the
 * sequence, and the order with it, exactly as it always has.
 *
 * <p>The wake's look is the flagship's, taken when the fold is committed: every ship in it flies the same
 * corridor in the flagship's Modulator colours. An escort's own colours are for its harmonic tether.
 *
 * @param fold      this fold's own id, shared by every ship in it
 * @param flagship  the flagship's coupler
 * @param coupler   this drive's own coupler
 * @param lead      whether this is the flagship's drive
 * @param initiator whoever pressed Fold, or rang the beacon, or set the course a circuit fired
 * @param dimension where the fleet is going
 * @param target    the flagship's target block; for an escort, only somewhere to spin up towards until
 *                  the flagship's search says exactly where its slot is
 * @param distance  this ship's jump, as priced
 * @param cost      this ship's share, as a fraction of its full charge
 * @param label     what to call the destination
 * @param colour    the wake's colour, and so on for its rim, theme and intensity
 */
public record FoldOrder(UUID fold, UUID flagship, UUID coupler, boolean lead, @Nullable UUID initiator,
                        ResourceKey<Level> dimension, BlockPos target, double distance, float cost, String label,
                        int colour, int accent, int theme, float intensity) {

    public FoldOrder {
        label = label == null ? "" : label;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Fold", fold);
        tag.putUUID("Flagship", flagship);
        tag.putUUID("Coupler", coupler);
        tag.putBoolean("Lead", lead);
        if (initiator != null) {
            tag.putUUID("Initiator", initiator);
        }
        tag.putString("Dimension", dimension.location().toString());
        tag.put("Target", NbtUtils.writeBlockPos(target));
        tag.putDouble("Distance", distance);
        tag.putFloat("Cost", cost);
        tag.putString("Label", label);
        tag.putInt("Colour", colour);
        tag.putInt("Accent", accent);
        tag.putInt("Theme", theme);
        tag.putFloat("Intensity", intensity);
        return tag;
    }

    @Nullable
    public static FoldOrder load(@Nullable CompoundTag tag) {
        if (tag == null || !tag.hasUUID("Fold") || !tag.hasUUID("Flagship") || !tag.hasUUID("Coupler")) {
            return null;
        }
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dimension == null) {
            return null;
        }
        return new FoldOrder(tag.getUUID("Fold"), tag.getUUID("Flagship"), tag.getUUID("Coupler"),
                tag.getBoolean("Lead"), tag.hasUUID("Initiator") ? tag.getUUID("Initiator") : null,
                ResourceKey.create(Registries.DIMENSION, dimension),
                NbtUtils.readBlockPos(tag, "Target").orElse(BlockPos.ZERO), tag.getDouble("Distance"),
                tag.getFloat("Cost"), tag.getString("Label"), tag.getInt("Colour"), tag.getInt("Accent"),
                tag.getInt("Theme"), tag.getFloat("Intensity"));
    }
}
