package uk.co.iceconchy.aerowarptics.flotilla;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.network.AWNetwork;
import uk.co.iceconchy.aerowarptics.network.ClientboundCouplerPanelPacket;
import uk.co.iceconchy.aerowarptics.network.PacketLists;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Builds a coupler's panel for one player, out of the coupler, its muster and the registry. */
public final class CouplerPanels {

    private CouplerPanels() {
    }

    public static void sendTo(ServerPlayer player, RiftCouplerBlockEntity coupler) {
        sendTo(player, coupler, CouplerNotice.NONE);
    }

    /** The panel, with something to say about what this player just asked for - see {@link CouplerNotice}. */
    public static void sendTo(ServerPlayer player, RiftCouplerBlockEntity coupler, CouplerNotice notice) {
        if (player.level() instanceof ServerLevel level) {
            AWNetwork.sendTo(player, build(level, player, coupler).withNotice(notice.key(), notice.arg()));
        }
    }

    static ClientboundCouplerPanelPacket build(ServerLevel level, ServerPlayer player, RiftCouplerBlockEntity coupler) {
        FlotillaRegistry registry = FlotillaRegistry.get(level);
        RiftDriveBlockEntity drive = coupler.linkedDrive();
        Airship ship = coupler.airship();
        String name = ship == null || ship.name() == null ? "" : ship.name();
        int tier = drive == null ? -1 : drive.tier().index();
        float charge = drive == null ? 0.0F : drive.charge();
        float radius = ship == null ? 0.0F : (float) FleetSpace.footprint(ship).radius();
        float tolerance = (float) (double) AWConfig.STATION_TOLERANCE.get();
        UUID id = coupler.couplerId();

        if (coupler.role() == CouplerRole.FLAGSHIP) {
            FleetMuster muster = coupler.muster();
            Flotilla flotilla = id == null ? null : registry.led(id);
            List<ClientboundCouplerPanelPacket.Row> rows = new ArrayList<>();
            if (muster != null) {
                for (FleetMuster.Row row : muster.rows()) {
                    rows.add(row(row));
                }
            }
            List<ClientboundCouplerPanelPacket.Request> requests = new ArrayList<>();
            if (flotilla != null) {
                for (UUID request : flotilla.requests()) {
                    CouplerRecord record = registry.coupler(request);
                    if (record != null) {
                        float distance = ship == null ? -1.0F : (float) Math.sqrt(record.distanceSqr(
                                ship.centre(new Vector3d()).x, ship.centre(new Vector3d()).y,
                                ship.centre(new Vector3d()).z));
                        requests.add(new ClientboundCouplerPanelPacket.Request(request, record.shipName(), distance));
                    }
                }
            }
            FleetCourse course = muster == null ? null : muster.course();
            Formation.Extent extent = muster == null ? new Formation.Extent(0, 0, 0, 0, 0, 0) : muster.extent();
            int escorts = rows.size();
            return new ClientboundCouplerPanelPacket(coupler.getBlockPos(), coupler.role(), drive != null,
                    ship != null, coupler.mayCommand(player), tier, coupler.contents().getAmount(),
                    RiftCouplerBlockEntity.CAPACITY, name, tolerance,
                    muster == null ? FlagshipStatus.NO_DRIVE.ordinal() : muster.flagshipStatus().ordinal(),
                    muster == null ? 0 : muster.phase().ordinal(),
                    course == null ? "" : course.label(), course == null ? -1.0F : (float) course.distance(),
                    drive == null ? 0 : drive.tier().flotillaEscorts(),
                    flotilla == null || flotilla.autoAccept(), flotilla == null || flotilla.beaconCallsAll(),
                    flotilla != null && flotilla.sharedRift(),
                    drive == null ? 0 : FleetMuster.essenceNeeded(escorts, drive.tier()),
                    (float) extent.width(), (float) extent.height(), (float) extent.length(),
                    muster != null && muster.foldable(), charge, radius,
                    PacketLists.cap(rows, Comparator.comparingInt(r -> 0)),
                    PacketLists.cap(requests, Comparator.comparingDouble(ClientboundCouplerPanelPacket.Request::distance)),
                    List.of(), ClientboundCouplerPanelPacket.NIL, ClientboundCouplerPanelPacket.NIL, 0, "", "");
        }

        // An escort: where it stands with its flagship, and who else it could join.
        UUID lead = id == null ? null : registry.flagshipOf(id);
        UUID pending = id == null ? null : registry.pendingWith(id);
        CouplerRecord leadRecord = registry.coupler(lead != null ? lead : pending);
        RiftCouplerBlockEntity flagship = lead == null ? null : RiftCouplerBlockEntity.find(level, registry.coupler(lead));
        FleetMuster.Row own = null;
        int number = 0;
        int phase = 0;
        if (flagship != null && flagship.muster() != null) {
            List<FleetMuster.Row> rows = flagship.muster().rows();
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).coupler().equals(id)) {
                    own = rows.get(i);
                    number = i + 1;
                }
            }
            phase = flagship.muster().phase().ordinal();
        } else if (lead != null) {
            Flotilla flotilla = registry.led(lead);
            if (flotilla != null) {
                List<Flotilla.Member> members = flotilla.escorts();
                for (int i = 0; i < members.size(); i++) {
                    if (members.get(i).coupler().equals(id)) {
                        number = i + 1;
                    }
                }
            }
        }
        List<ClientboundCouplerPanelPacket.Nearby> nearby = new ArrayList<>();
        if (ship != null && lead == null) {
            Vector3d centre = ship.centre(new Vector3d());
            for (CouplerRecord record : registry.flagshipsNear(level.dimension(), centre.x, centre.y, centre.z,
                    AWConfig.TAKE_STATION_RANGE.get(), ship.uuid())) {
                Flotilla flotilla = registry.led(record.id());
                int cap = record.tier() < 0 ? 0
                        : uk.co.iceconchy.aerowarptics.drive.RiftDriveTier.byIndex(record.tier()).flotillaEscorts();
                nearby.add(new ClientboundCouplerPanelPacket.Nearby(record.id(), record.shipName(),
                        (float) Math.sqrt(record.distanceSqr(centre.x, centre.y, centre.z)),
                        flotilla == null ? 0 : flotilla.escorts().size(), cap));
            }
        }
        StationStatus status = own == null ? StationStatus.OUT_OF_CONTACT : own.status();
        return new ClientboundCouplerPanelPacket(coupler.getBlockPos(), coupler.role(), drive != null, ship != null,
                coupler.mayCommand(player), tier, coupler.contents().getAmount(), RiftCouplerBlockEntity.CAPACITY,
                name, tolerance, status.ordinal(), phase, leadRecord == null ? "" : leadRecord.shipName(), -1.0F,
                0, false, false, false, 0, 0, 0, 0, false, charge, radius,
                own == null ? List.of() : List.of(row(own)), List.of(),
                PacketLists.cap(nearby, Comparator.comparingDouble(ClientboundCouplerPanelPacket.Nearby::distance)),
                lead == null ? ClientboundCouplerPanelPacket.NIL : lead,
                pending == null ? ClientboundCouplerPanelPacket.NIL : pending, number, "", "");
    }

    private static ClientboundCouplerPanelPacket.Row row(FleetMuster.Row row) {
        FormationSlot slot = row.slot();
        FormationSlot actual = row.actual();
        return new ClientboundCouplerPanelPacket.Row(row.coupler(), row.record() == null ? "" : row.record().shipName(),
                (float) slot.right(), (float) slot.up(), (float) slot.ahead(),
                actual == null ? 0.0F : (float) (actual.right() - slot.right()),
                actual == null ? 0.0F : (float) (actual.up() - slot.up()),
                actual == null ? 0.0F : (float) (actual.ahead() - slot.ahead()),
                actual != null, (float) row.footprint().radius(), row.status().ordinal(), (float) row.drift(),
                row.charge(), row.harmony(), row.tether());
    }

    /** Whether the name a player wants is free among the rest of the flotilla this coupler is in. */
    static boolean nameFree(ServerLevel level, @Nullable UUID couplerId, String name) {
        if (couplerId == null) {
            return true;
        }
        FlotillaRegistry registry = FlotillaRegistry.get(level);
        Flotilla flotilla = registry.flotillaOf(couplerId);
        if (flotilla == null) {
            return true;
        }
        List<String> others = new ArrayList<>();
        if (!flotilla.flagship().equals(couplerId)) {
            CouplerRecord record = registry.coupler(flotilla.flagship());
            if (record != null) {
                others.add(record.shipName());
            }
        }
        for (Flotilla.Member member : flotilla.escorts()) {
            if (!member.coupler().equals(couplerId)) {
                CouplerRecord record = registry.coupler(member.coupler());
                if (record != null) {
                    others.add(record.shipName());
                }
            }
        }
        return !FleetNames.taken(name, others);
    }
}
