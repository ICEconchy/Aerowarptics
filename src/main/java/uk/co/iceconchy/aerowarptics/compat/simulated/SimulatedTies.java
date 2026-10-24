package uk.co.iceconchy.aerowarptics.compat.simulated;

import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import dev.simulated_team.simulated.content.blocks.merging_glue.MergingGlueBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import dev.simulated_team.simulated.content.blocks.spring.SpringBlockEntity;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.link_block.SwivelBearingPlateBlockEntity;
import dev.simulated_team.simulated.content.entities.launched_plunger.LaunchedPlungerEntity;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.airship.Fastening;
import uk.co.iceconchy.aerowarptics.airship.Fastenings;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Every way Create Simulated ties a sub-level to something, read off Simulated's own machines.
 *
 * <p>With {@link CarriedRopes} and {@code ClientRopes}, the only code in this mod that names a Simulated
 * class. Simulated comes inside Create: Aeronautics,
 * so it is always there, but it is a separate mod with its own releases; each probe goes through
 * {@link Fastenings#probe} so that one of them failing to link against a newer Simulated switches that one
 * off instead of throwing in a server tick.
 *
 * <p>What each machine makes, and how it is read:
 * <ul>
 *   <li><b>Docking Connector</b> - a fixed joint to its partner, from the moment it starts locking. The
 *       partner's sub-level is saved on it, {@code null} when the partner stands on the ground.</li>
 *   <li><b>Rope</b> - a strand simulated in world space with a joint between its two ends. Read from
 *       the level's rope manager rather than the connectors, so a strand is found whichever end owns it.
 *       Whatever it ends on is part of the vessel ({@link #ropedTo}), so only a rope to the ground holds.</li>
 *   <li><b>Swivel Bearing</b> - a hinge between the bearing's sub-level and the plate's. A bearing aboard
 *       carries its plate, which is part of the vessel; a plate aboard is the ship hinged to its bearing,
 *       which may be on the ground.</li>
 *   <li><b>Physics Staff</b> - a lock to the world, kept in the level's saved data.</li>
 *   <li><b>Spring</b> - a force between a pair, not a joint, but a pair to the ground is a line the warp
 *       would stretch across the map.</li>
 *   <li><b>Merging Glue</b> - a joint for the few ticks it takes two sub-levels to become one.</li>
 *   <li><b>Plunger</b> - a pair of plungers joined by a line, each stuck into a block.</li>
 * </ul>
 * A Handle gripped from off the ship also makes a joint, but who is gripping it is private to the Handle,
 * and a crew member steadying themselves on one aboard makes none; it is left alone rather than risk
 * refusing a warp for somebody holding on.
 */
public final class SimulatedTies {

    /** How far round the vessel a plunger stuck into it could be - a plunger is an entity, not a block. */
    private static final double PLUNGER_REACH = 4.0D;

    private SimulatedTies() {
    }

    public static void collect(Fastenings.Vessel vessel, List<Fastening> into) {
        Fastenings.probe("docking connector", () -> dockingConnectors(vessel, into));
        Fastenings.probe("rope", () -> ropes(vessel, into));
        Fastenings.probe("swivel bearing", () -> swivelBearings(vessel, into));
        Fastenings.probe("physics staff", () -> physicsStaff(vessel, into));
        Fastenings.probe("spring", () -> springs(vessel, into));
        Fastenings.probe("merging glue", () -> mergingGlue(vessel, into));
        Fastenings.probe("plunger", () -> plungers(vessel, into));
    }

    private static void dockingConnectors(Fastenings.Vessel vessel, List<Fastening> into) {
        for (Airship member : vessel.members()) {
            for (DockingConnectorBlockEntity connector : member.machines(DockingConnectorBlockEntity.class)) {
                // Paired from the moment it starts locking, which is when the joint is made - not only once
                // the lock has finished.
                if (connector.hasOtherConnector()) {
                    into.add(new Fastening(Fastening.Kind.DOCKING_CONNECTOR,
                            vessel.endOf(connector.otherConnectorSubLevelId),
                            vessel.world(member, connector.getBlockPos())));
                }
            }
        }
    }

    private static void ropes(Fastenings.Vessel vessel, List<Fastening> into) {
        for (ServerRopeStrand strand : ServerLevelRopeManager.getOrCreate(vessel.level()).getAllStrands()) {
            Fastening.End end = null;
            BlockPos where = null;
            boolean aboard = false;
            for (RopeAttachment attachment : strand.getAttachments()) {
                Fastening.End here = vessel.endOf(attachment.subLevelID());
                Airship member = vessel.member(attachment.subLevelID());
                if (member != null) {
                    aboard = true;
                    if (where == null && attachment.blockAttachment() != null) {
                        where = vessel.world(member, attachment.blockAttachment());
                    }
                }
                // The far end that matters most: the ground over another ship over the vessel itself.
                if (end == null || here.ordinal() < end.ordinal()) {
                    end = here;
                }
            }
            if (aboard) {
                into.add(new Fastening(Fastening.Kind.ROPE, end, where));
            }
        }
    }

    /**
     * Every sub-level a rope from {@code member} ends on.
     *
     * <p>A rope makes a joint between its two ends that nothing of Sable's lists, so a weight hung on a rope
     * under a hull, or a skiff on a line astern, was not part of the vessel: the rope to it was refused as
     * tying the ship to something it would leave behind. It is part of it, like a ship on a Docking
     * Connector - flown with the hull and carried through the warp, with the rope itself moved by
     * {@link CarriedRopes}. A rope to the ground has no sub-level at its far end and adds nothing; it is
     * still refused.
     */
    public static void ropedTo(SubLevel member, List<SubLevel> into) {
        if (!(member.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ServerLevelRopeManager ropes = ServerLevelRopeManager.getOrCreate(level);
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (ropes == null || container == null) {
            return;
        }
        UUID id = member.getUniqueId();
        for (ServerRopeStrand strand : ropes.getAllStrands()) {
            boolean fromHere = false;
            for (RopeAttachment attachment : strand.getAttachments()) {
                if (id.equals(attachment.subLevelID())) {
                    fromHere = true;
                    break;
                }
            }
            if (!fromHere) {
                continue;
            }
            for (RopeAttachment attachment : strand.getAttachments()) {
                UUID other = attachment.subLevelID();
                SubLevel far = other == null || other.equals(id) ? null : container.getSubLevel(other);
                if (far != null && !far.isRemoved()) {
                    into.add(far);
                }
            }
        }
    }

    private static void swivelBearings(Fastenings.Vessel vessel, List<Fastening> into) {
        for (Airship member : vessel.members()) {
            for (SwivelBearingBlockEntity bearing : member.machines(SwivelBearingBlockEntity.class)) {
                if (bearing.isAssembled() && bearing.getSubLevelID() != null) {
                    into.add(new Fastening(Fastening.Kind.SWIVEL_BEARING, vessel.endOf(bearing.getSubLevelID()),
                            vessel.world(member, bearing.getBlockPos())));
                }
            }
            // The plate keeps its bearing in private fields, but saves them: the bearing's position always,
            // and its sub-level only when it has one. A position with no sub-level is a bearing on the ground.
            for (SwivelBearingPlateBlockEntity plate : member.machines(SwivelBearingPlateBlockEntity.class)) {
                CompoundTag saved = plate.saveWithoutMetadata(vessel.level().registryAccess());
                if (!saved.contains("ParentPos")) {
                    continue;
                }
                UUID bearingShip = saved.hasUUID("ParentSubLevelId") ? saved.getUUID("ParentSubLevelId") : null;
                Fastening.End end = vessel.endOf(bearingShip);
                BlockPos plateAt = vessel.world(member, plate.getBlockPos());
                // A bearing on the ground already has a world position, and it is the thing to go and look at.
                BlockPos where = end == Fastening.End.GROUND
                        ? NbtUtils.readBlockPos(saved, "ParentPos").orElse(plateAt)
                        : plateAt;
                into.add(new Fastening(Fastening.Kind.SWIVEL_BEARING, end, where));
            }
        }
    }

    private static void physicsStaff(Fastenings.Vessel vessel, List<Fastening> into) {
        PhysicsStaffServerHandler staff = PhysicsStaffServerHandler.get(vessel.level());
        for (Airship member : vessel.members()) {
            if (staff.isLocked(member.subLevel())) {
                into.add(new Fastening(Fastening.Kind.PHYSICS_STAFF, Fastening.End.GROUND, null));
            }
        }
    }

    private static void springs(Fastenings.Vessel vessel, List<Fastening> into) {
        for (Airship member : vessel.members()) {
            for (SpringBlockEntity spring : member.machines(SpringBlockEntity.class)) {
                if (spring.getPairedSpring() != null) {
                    into.add(new Fastening(Fastening.Kind.SPRING, vessel.endOf(spring.getPartnerSubLevelID()),
                            vessel.world(member, spring.getBlockPos())));
                }
            }
        }
    }

    private static void mergingGlue(Fastenings.Vessel vessel, List<Fastening> into) {
        for (Airship member : vessel.members()) {
            for (MergingGlueBlockEntity glue : member.machines(MergingGlueBlockEntity.class)) {
                MergingGlueBlockEntity partner = glue.isController() ? glue.getPartnerGlue() : null;
                if (partner != null) {
                    into.add(new Fastening(Fastening.Kind.MERGING_GLUE, vessel.endAt(partner.getBlockPos()),
                            vessel.world(member, glue.getBlockPos())));
                }
            }
        }
    }

    /**
     * Plungers stuck into the vessel, and where their partners are stuck.
     *
     * <p>A plunger is an entity, found by looking round each member - in the world, and in the plot in case
     * it is filed there - and judged by the block it is stuck into rather than where it happens to be.
     */
    private static void plungers(Fastenings.Vessel vessel, List<Fastening> into) {
        Set<LaunchedPlungerEntity> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Airship member : vessel.members()) {
            AABB world = member.worldBounds().toMojang().inflate(PLUNGER_REACH);
            seen.addAll(vessel.level().getEntitiesOfClass(LaunchedPlungerEntity.class, world, p -> !p.isRemoved()));
            LevelPlot plot = member.subLevel().getPlot();
            BoundingBox3ic box = plot == null ? null : plot.getBoundingBox();
            if (box != null) {
                AABB inPlot = new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1)
                        .inflate(PLUNGER_REACH);
                seen.addAll(vessel.level().getEntitiesOfClass(LaunchedPlungerEntity.class, inPlot, p -> !p.isRemoved()));
            }
        }
        for (LaunchedPlungerEntity plunger : seen) {
            BlockPos stuck = stuckInto(plunger);
            LaunchedPlungerEntity other = plunger.getOther();
            BlockPos otherStuck = other == null ? null : stuckInto(other);
            if (stuck == null || otherStuck == null || vessel.endAt(stuck) != Fastening.End.CARRIED) {
                continue;
            }
            SubLevel on = Sable.HELPER.getContaining(vessel.level(), stuck);
            Airship member = on == null ? null : vessel.member(on.getUniqueId());
            into.add(new Fastening(Fastening.Kind.PLUNGER, vessel.endAt(otherStuck),
                    member == null ? null : vessel.world(member, stuck)));
        }
    }

    @Nullable
    private static BlockPos stuckInto(LaunchedPlungerEntity plunger) {
        if (!plunger.isPlunged()) {
            return null;
        }
        return plunger.getEntityData().get(LaunchedPlungerEntity.PLUNGED_BLOCK_POS);
    }
}
