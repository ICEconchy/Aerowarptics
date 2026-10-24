package uk.co.iceconchy.aerowarptics.sunderer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchor;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorRegistry;
import uk.co.iceconchy.aerowarptics.fissure.RiftFissureBlockEntity;
import uk.co.iceconchy.aerowarptics.gate.RiftGateFrameBlock;
import uk.co.iceconchy.aerowarptics.hold.Holds;
import uk.co.iceconchy.aerowarptics.ward.RiftWard;
import uk.co.iceconchy.aerowarptics.ward.WardRegistry;
import uk.co.iceconchy.aerowarptics.wellhead.Meltdowns;
import uk.co.iceconchy.aerowarptics.wellhead.RiftWoundBlockEntity;
import uk.co.iceconchy.aerowarptics.wellhead.ScarZones;

/**
 * Whether a Rift Sunderer may fire where it stands, and the ring it would tear if it may.
 *
 * <p>Everything is found before anything is spent. The ring, its size, the charge and the speed are the
 * Sunderer's own and cheap, and are what the goggles show while it waits; what is round about - scar zones,
 * other tears, anchors, wards - is looked for only at the edge, and only in ground that is already loaded. An
 * unloaded chunk in reach is taken as clear: a survey that loaded chunks to look would be a way to load chunks.
 */
public final class SundererSurvey {

    private SundererSurvey() {
    }

    /** What a survey found: the first refusal, or none and the ring to tear. */
    public record Result(SundererFailure failure, @Nullable SundererRing.Ring ring) {

        public boolean ok() {
            return failure == SundererFailure.NONE && ring != null;
        }
    }

    /** The Sunderer's own checks: aboard a ship, the ring and its size, the charge and the speed. */
    public static Result own(ServerLevel level, RiftSundererBlockEntity sunderer) {
        BlockPos pos = sunderer.getBlockPos();
        if (Holds.inPlot(level, pos)) {
            return new Result(SundererFailure.ABOARD_SHIP, null);
        }
        SundererRing.Ring ring = ring(level, pos);
        if (ring == null) {
            return new Result(SundererFailure.NO_RING, null);
        }
        SundererFailure size = SundererRing.size(ring.shape(), AWConfig.SUNDERER_LEAST_OPENING.get(),
                AWConfig.SUNDERER_MOST_OPENING.get());
        if (size.isFailure()) {
            return new Result(size, ring);
        }
        if (sunderer.tank().getFluidAmount() < AWConfig.SUNDERER_CHARGE.get()) {
            return new Result(SundererFailure.NOT_CHARGED, ring);
        }
        if (Math.abs(sunderer.getSpeed()) < AWConfig.SUNDERER_MINIMUM_SPEED.get()) {
            return new Result(SundererFailure.TOO_SLOW, ring);
        }
        return new Result(SundererFailure.NONE, ring);
    }

    /** All of it, as a redstone edge asks: the Sunderer's own checks, then what is round about. */
    public static Result full(ServerLevel level, RiftSundererBlockEntity sunderer) {
        Result own = own(level, sunderer);
        if (!own.ok()) {
            return own;
        }
        BlockPos centre = own.ring().centre();
        SundererFailure round = roundAbout(level, centre, sunderer.getBlockPos());
        return round.isFailure() ? new Result(round, own.ring()) : own;
    }

    /** The ring round a Sunderer in the level, or null. */
    @Nullable
    public static SundererRing.Ring ring(ServerLevel level, BlockPos pos) {
        return SundererRing.find(pos,
                at -> level.isLoaded(at) && level.getBlockState(at).getBlock() instanceof RiftGateFrameBlock,
                at -> level.isLoaded(at) && level.getBlockState(at).isAir());
    }

    /** What is round a tear's centre that forbids it. */
    static SundererFailure roundAbout(ServerLevel level, BlockPos centre, BlockPos sunderer) {
        if (ScarZones.within(level, centre) || Meltdowns.over(level, centre)) {
            return SundererFailure.IN_SCAR_ZONE;
        }
        if (nearTear(level, centre, AWConfig.SUNDERER_SPACING.get(), sunderer)) {
            return SundererFailure.NEAR_TEAR;
        }
        if (nearAnchor(level, centre, AWConfig.SUNDERER_ANCHOR_SPACING.get())) {
            return SundererFailure.NEAR_ANCHOR;
        }
        if (inWardField(level, centre)) {
            return SundererFailure.IN_WARD_FIELD;
        }
        ResourceLocation dimension = level.dimension().location();
        for (String forbidden : AWConfig.SUNDERER_FORBIDDEN_DIMENSIONS.get()) {
            if (dimension.toString().equals(forbidden)) {
                return SundererFailure.DIMENSION;
            }
        }
        return SundererFailure.NONE;
    }

    /**
     * Whether a fissure, a Rift Wound or another running Sunderer stands within {@code spacing} of the centre.
     * Read off the block entities of chunks already loaded, never by loading one.
     */
    static boolean nearTear(ServerLevel level, BlockPos centre, int spacing, BlockPos self) {
        if (spacing <= 0) {
            return false;
        }
        double reach = (double) spacing * spacing;
        int chunks = SectionPos.blockToSectionCoord(spacing) + 1;
        int cx = SectionPos.blockToSectionCoord(centre.getX());
        int cz = SectionPos.blockToSectionCoord(centre.getZ());
        for (int x = cx - chunks; x <= cx + chunks; x++) {
            for (int z = cz - chunks; z <= cz + chunks; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (entity.getBlockPos().distSqr(centre) > reach || entity.getBlockPos().equals(self)) {
                        continue;
                    }
                    if (entity instanceof RiftFissureBlockEntity || entity instanceof RiftWoundBlockEntity
                            || entity instanceof RiftSundererBlockEntity other && other.running()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static boolean nearAnchor(ServerLevel level, BlockPos centre, int spacing) {
        if (spacing <= 0) {
            return false;
        }
        double reach = (double) spacing * spacing;
        for (WarpAnchor anchor : WarpAnchorRegistry.get(level).all()) {
            if (anchor.dimension().equals(level.dimension()) && anchor.pos().distSqr(centre) <= reach) {
                return true;
            }
        }
        return false;
    }

    /** Whether a standing ward's field covers the centre. A field is a circle on the ground, as the ward measures it. */
    static boolean inWardField(ServerLevel level, BlockPos centre) {
        if (!AWConfig.WARD_ENABLED.get()) {
            return false;
        }
        for (RiftWard ward : WardRegistry.get(level.getServer()).in(level.dimension())) {
            if (!ward.enabled() || !ward.standing()) {
                continue;
            }
            double dx = centre.getX() + 0.5D - ward.centreX();
            double dz = centre.getZ() + 0.5D - ward.centreZ();
            if (dx * dx + dz * dz <= (double) ward.radius() * ward.radius()) {
                return true;
            }
        }
        return false;
    }
}
