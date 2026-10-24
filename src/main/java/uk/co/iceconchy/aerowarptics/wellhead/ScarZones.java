package uk.co.iceconchy.aerowarptics.wellhead;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.network.ClientboundScarZonePacket;
import uk.co.iceconchy.aerowarptics.registry.AWDamageTypes;
import uk.co.iceconchy.aerowarptics.rifting.RiftHop;
import uk.co.iceconchy.aerowarptics.trace.Trace;

import java.util.ArrayList;
import java.util.List;

/**
 * The unsettled ground meltdowns have left in a level, each for a while.
 *
 * <p>A zone is a record, not a thing in the world: it loads no chunk and holds nothing. Once a second it bears
 * on whatever living is standing in it where the ground happens to be loaded - hurting it, and hopping it about
 * as a rifting fan's stream does - hardest in the middle and less as it fades. Nothing forms a wellhead inside
 * one, and a siphon standing in one catches nothing. Players near enough are told where it is, so their own
 * client can show it; what it shows without Rift Goggles is only that the air is wrong.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID)
public class ScarZones extends SavedData {

    private static final String FILE_ID = AeroWarptics.MODID + "_scar_zones";

    /** How far past a zone's edge, in blocks, a player is still told of it. */
    private static final int TOLD_PAST_EDGE = 48;

    private final List<ScarZone> zones = new ArrayList<>();

    public static ScarZones get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(ScarZones::new, ScarZones::load, null), FILE_ID);
    }

    /** A meltdown leaves its ground unsettled. */
    public static void leave(ServerLevel level, BlockPos centre, int radius, WellheadTier tier) {
        leave(level, centre, radius, AWConfig.WELLHEAD_ZONE_MINUTES.get() * 1_200L, tier.getSerializedName());
    }

    /**
     * Leaves ground unsettled for {@code ticks}: a meltdown's, or a Rift Sunderer's rupture.
     *
     * @param what what left it, for the trace
     */
    public static void leave(ServerLevel level, BlockPos centre, int radius, long ticks, String what) {
        if (ticks <= 0L) {
            return;
        }
        ScarZones zones = get(level);
        long now = level.getGameTime();
        zones.zones.removeIf(zone -> zone.centre() == centre.asLong());
        zones.zones.add(new ScarZone(centre.asLong(), radius, now, now + ticks));
        zones.setDirty();
        Trace.always(Trace.WELLHEAD, "scar zone left at {} ({}): radius {}, for {} ticks", Trace.pos(centre),
                what, radius, ticks);
    }

    /** Whether a block stands in ground a meltdown has left unsettled. */
    public static boolean within(ServerLevel level, BlockPos pos) {
        long now = level.getGameTime();
        for (ScarZone zone : get(level).zones) {
            if (!zone.over(now) && BlockPos.of(zone.centre()).distSqr(pos) <= (double) zone.radius() * zone.radius()) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 20L != 0L) {
            return;
        }
        ScarZones zones = get(level);
        if (zones.zones.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        if (zones.zones.removeIf(zone -> zone.over(now))) {
            zones.setDirty();
        }
        float harm = (float) (double) AWConfig.WELLHEAD_ZONE_HARM.get();
        for (ScarZone zone : zones.zones) {
            BlockPos centre = BlockPos.of(zone.centre());
            Vec3 point = Vec3.atCenterOf(centre);
            for (ServerPlayer player : level.players()) {
                double reach = zone.radius() + TOLD_PAST_EDGE;
                if (player.blockPosition().distSqr(centre) <= reach * reach) {
                    PacketDistributor.sendToPlayer(player,
                            new ClientboundScarZonePacket(centre, zone.radius(), (float) zone.strength(now)));
                }
            }
            // Only what is loaded: asking a level for its entities loads nothing.
            if (!level.isLoaded(centre)) {
                continue;
            }
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(centre).inflate(zone.radius()))) {
                if (living instanceof ArmorStand || !living.isAlive() || living.isSpectator()
                        || living instanceof ServerPlayer player && player.isCreative()) {
                    continue;
                }
                double bears = zone.bears(living.position().distanceTo(point), now);
                if (bears <= 0.0D) {
                    continue;
                }
                if (harm > 0.0F) {
                    living.hurt(AWDamageTypes.fallout(level, point), (float) (harm * bears));
                }
                if (AWConfig.RIFTING_HOPS.get() && RiftHop.mayHop(living) && level.random.nextDouble() < 0.25D * bears) {
                    RiftHop.hop(level, living, AWConfig.RIFTING_HOP_RANGE.get());
                }
            }
        }
    }

    // -------------------------------------------------------------------- nbt

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (ScarZone zone : zones) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Centre", zone.centre());
            entry.putInt("Radius", zone.radius());
            entry.putLong("Began", zone.began());
            entry.putLong("Expires", zone.expires());
            list.add(entry);
        }
        tag.put("Zones", list);
        return tag;
    }

    private static ScarZones load(CompoundTag tag, HolderLookup.Provider registries) {
        ScarZones zones = new ScarZones();
        ListTag list = tag.getList("Zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            zones.zones.add(new ScarZone(entry.getLong("Centre"), Math.max(1, Math.min(256, entry.getInt("Radius"))),
                    entry.getLong("Began"), entry.getLong("Expires")));
        }
        return zones;
    }
}
