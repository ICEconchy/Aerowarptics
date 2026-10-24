package uk.co.iceconchy.aerowarptics.hold;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

/**
 * Who holds a ship, and why: the key of this mod's Sable force-load ticket.
 *
 * <p>Sable tells tickets apart by type, ship and key, so one owner can hold one ship once for each
 * purpose - a drive's flight hold and its residency hold on the same hull are two tickets, and letting go
 * of one leaves the other. Sable saves the key with the ticket and parses it back when the level loads;
 * a key it cannot parse (a purpose some later version dropped) is logged and the ticket skipped, which is
 * exactly the right thing for a hold nobody can renew any more.
 *
 * @param owner   the machine holding the ship - a drive's own id
 * @param purpose why
 */
public record HoldKey(UUID owner, HoldPurpose purpose) {

    private static final Codec<HoldPurpose> PURPOSE_CODEC = Codec.STRING.comapFlatMap(
            id -> {
                HoldPurpose purpose = HoldPurpose.byId(id);
                return purpose == null ? DataResult.error(() -> "Unknown hold purpose " + id) : DataResult.success(purpose);
            },
            HoldPurpose::id);

    public static final Codec<HoldKey> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("owner").forGetter(HoldKey::owner),
            PURPOSE_CODEC.fieldOf("purpose").forGetter(HoldKey::purpose)
    ).apply(instance, HoldKey::new));
}
