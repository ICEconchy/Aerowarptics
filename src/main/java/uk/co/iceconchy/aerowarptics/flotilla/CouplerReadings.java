package uk.co.iceconchy.aerowarptics.flotilla;

import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What a Rift Coupler has to report to a Display Link: its role, its flotilla, how many are ready, and
 * the roster ship by ship.
 *
 * <p>Read the same way whichever end of the flotilla the link is pointed at. An escort reports its
 * flagship's flotilla, from its flagship's own readings when that coupler is loaded here - so a board
 * on an escort's bridge shows the same roster as one on the flagship's - and from the registry alone,
 * every ship out of contact, when it is not.
 *
 * @param role         what this coupler is set to be
 * @param flotilla     the flagship's ship name, blank for an unnamed one; {@code null} in no flotilla
 * @param ready        escorts with every condition met
 * @param escorts      escorts in the flotilla
 * @param status       the translation key for this coupler's own state: its flagship status as a
 *                     flagship, its station status as an escort
 * @param roster       every ship, flagship first
 */
public record CouplerReadings(CouplerRole role, @Nullable String flotilla, int ready, int escorts, String status,
                              List<Line> roster) {

    /** One ship on the roster: its name, blank if it has none, and the key of how it stands. */
    public record Line(String name, String status) {
    }

    public static CouplerReadings of(ServerLevel level, RiftCouplerBlockEntity coupler) {
        FlotillaRegistry registry = FlotillaRegistry.get(level);
        UUID id = coupler.couplerId();
        UUID lead = id == null ? null : coupler.role() == CouplerRole.FLAGSHIP ? id : registry.flagshipOf(id);
        Flotilla flotilla = lead == null ? null : registry.led(lead);
        if (flotilla == null || coupler.role() == CouplerRole.FLAGSHIP && flotilla.escorts().isEmpty()) {
            String own = coupler.role() == CouplerRole.FLAGSHIP
                    ? FlagshipStatus.NO_DRIVE.translationKey() : StationStatus.OUT_OF_CONTACT.translationKey();
            if (coupler.role() == CouplerRole.FLAGSHIP && coupler.muster() != null) {
                own = coupler.muster().flagshipStatus().translationKey();
            }
            return new CouplerReadings(coupler.role(), null, 0, 0, own, List.of());
        }
        RiftCouplerBlockEntity flagship = coupler.role() == CouplerRole.FLAGSHIP ? coupler
                : RiftCouplerBlockEntity.find(level, registry.coupler(lead));
        FleetMuster muster = flagship == null ? null : flagship.muster();
        CouplerRecord leadRecord = registry.coupler(lead);
        String flagshipName = leadRecord == null ? "" : leadRecord.shipName();

        List<Line> roster = new ArrayList<>();
        String flagshipStatus = muster == null ? StationStatus.OUT_OF_CONTACT.translationKey()
                : muster.flagshipStatus().translationKey();
        roster.add(new Line(flagshipName, flagshipStatus));
        int ready = 0;
        String own = coupler.role() == CouplerRole.FLAGSHIP ? flagshipStatus
                : StationStatus.OUT_OF_CONTACT.translationKey();
        if (muster != null && !muster.rows().isEmpty()) {
            for (FleetMuster.Row row : muster.rows()) {
                roster.add(new Line(row.record() == null ? "" : row.record().shipName(),
                        row.status().translationKey()));
                if (row.status().ready()) {
                    ready++;
                }
                if (row.coupler().equals(id)) {
                    own = row.status().translationKey();
                }
            }
        } else {
            for (Flotilla.Member member : flotilla.escorts()) {
                CouplerRecord record = registry.coupler(member.coupler());
                roster.add(new Line(record == null ? "" : record.shipName(),
                        StationStatus.OUT_OF_CONTACT.translationKey()));
            }
        }
        return new CouplerReadings(coupler.role(), flagshipName, ready, flotilla.escorts().size(), own,
                List.copyOf(roster));
    }
}
