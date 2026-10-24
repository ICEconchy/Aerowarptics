package uk.co.iceconchy.aerowarptics.flotilla;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.airship.Fastenings;
import uk.co.iceconchy.aerowarptics.drive.DriveMode;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveBlockEntity;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveState;
import uk.co.iceconchy.aerowarptics.drive.RiftDriveTier;
import uk.co.iceconchy.aerowarptics.network.ClientboundCouplerPanelPacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundMusterPacket;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.warp.ArrivalTicket;
import uk.co.iceconchy.aerowarptics.warp.FleetPassage;
import uk.co.iceconchy.aerowarptics.warp.FlightGeometry;
import uk.co.iceconchy.aerowarptics.warp.WarpFlight;
import uk.co.iceconchy.aerowarptics.warp.CrossDimensionWarp;
import uk.co.iceconchy.aerowarptics.warp.SafeArrival;
import uk.co.iceconchy.aerowarptics.warp.WarpCost;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;
import uk.co.iceconchy.aerowarptics.warp.WarpValidator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A flagship's muster and fold: sounding stations, harmonising, the countdown and the ripple.
 *
 * <p>Held by the flagship's coupler and never saved. A muster is a few minutes of ships holding station
 * and a fold is a few seconds of them going; neither survives a restart any more than a warp in
 * progress does, and the flotilla itself - which does - lives in the {@link FlotillaRegistry}.
 *
 * <h2>Reading the escorts</h2>
 * Every tick, each escort's coupler is looked up where the registry last saw it, and its ship and drive
 * are read directly: where it is against its slot, whether its drive is powered and full, whether the
 * flagship's course is within its reach and its share of the fold within its charge. What that adds up
 * to is {@link Readiness}'s to say; this only takes the readings.
 */
public final class FleetMuster {

    /** Why a muster stopped without folding. */
    public enum StandDown {
        ROLE_CHANGED,
        COUPLER_LOST,
        EMPTY,
        /** The flagship's owner pressed Stand Down. */
        ORDERED
    }

    /** Where the muster is. Everything from {@link #COMMITTED} on is a fold in hand. */
    public enum Phase {
        /** Nobody has sounded stations. */
        STANDBY,
        /** Stations sounded: escorts holding station and harmonising. */
        MUSTER,
        /** Fold pressed: every drive has taken its orders and the flagship's is spinning up. */
        COMMITTED,
        /** The flagship's drive is spun up and holding for the countdown. */
        COUNTDOWN,
        /** Rifts open: ships in the fold, until the last is out the other side. */
        FOLDING;

        public boolean folding() {
            return this.ordinal() >= COMMITTED.ordinal();
        }
    }

    /**
     * How a fold was called.
     *
     * <p>From the Fleet Board, a fold is a deliberate act by someone standing at the flagship: every
     * escort must be ready, or - held - the ones that are not are left behind and dropped. A beacon or a
     * circuit has nobody at the board to decide that, so it takes whoever is in station with a ready drive
     * and leaves the rest where they are, still in the flotilla, to be told nothing worse than that they
     * missed it.
     */
    public enum Call {
        BOARD,
        FORCED,
        BEACON,
        REDSTONE;

        boolean fromBoard() {
            return this == BOARD || this == FORCED;
        }
    }

    /** An escort that is going: its coupler, its slot, its number on the board and the room it takes. */
    private record Joined(UUID coupler, FormationSlot slot, int number, Formation.Footprint footprint) {
    }

    /**
     * When an escort's rift opens, and where it comes out - and, going through the fleet's one rift, the
     * passage it keeps step with.
     */
    private record Release(UUID coupler, net.minecraft.world.phys.Vec3 centre, int at, boolean echo,
                           @Nullable FleetPassage passage, @Nullable uk.co.iceconchy.aerowarptics.berth.Berths.Assignment berth) {
    }

    /**
     * The berth each escort was given, by ship, when the fold in hand is bound for a station: set by the
     * flagship's drive as it plans, read as the escorts are told where they come out, and gone with the fold.
     */
    private final Map<UUID, uk.co.iceconchy.aerowarptics.berth.Berths.Assignment> berths = new HashMap<>();

    /**
     * The flagship has had a berth for every ship of the fleet from a station. Each escort then comes out in its
     * own slip, turned to it, and not in its slot - a station has no room for a formation, only for ships.
     */
    public void berthed(Map<UUID, uk.co.iceconchy.aerowarptics.berth.Berths.Assignment> given) {
        berths.clear();
        berths.putAll(given);
    }

    /**
     * One escort as the flagship last read it.
     *
     * @param actual   where it is in the formation, or {@code null} out of contact
     * @param charge   its drive's charge, 0 to 1
     * @param harmony  how far through harmonising, 0 to 1
     * @param tether   its ship's tether colour
     */
    public record Row(UUID coupler, @Nullable CouplerRecord record, FormationSlot slot,
                      @Nullable FormationSlot actual, double drift, StationStatus status, float charge,
                      float harmony, Formation.Footprint footprint, int tether) {
    }

    /** Ticks between muster broadcasts: half a second, which the client smooths between. */
    private static final int BROADCAST_INTERVAL = 10;

    /** How often the muster looks for what ties its ships down; see {@link #fastened}. */
    private static final int FASTENING_INTERVAL = 20;

    private final RiftCouplerBlockEntity coupler;

    private Phase phase = Phase.STANDBY;
    private final Map<UUID, Integer> harmony = new HashMap<>();
    private final Map<UUID, Readiness.Survey> surveys = new HashMap<>();
    /**
     * Escorts found tied down at the last look, by coupler, and the flagship. Looked at once every
     * {@link #FASTENING_INTERVAL} rather than with every reading: it is a search of every part of every ship,
     * and a tie is something a crew makes by hand, not something that changes between two ticks. The drive
     * asks again for itself when the fold is committed, so a stale answer here only ever delays a row.
     */
    private final Map<UUID, Boolean> fastened = new HashMap<>();
    private boolean flagshipFastened;
    private Readiness.Survey flagshipSurvey = Readiness.Survey.UNKNOWN;
    private List<Row> rows = List.of();
    private FlagshipStatus flagshipStatus = FlagshipStatus.NO_DRIVE;
    @Nullable
    private FleetCourse course;
    private Formation.Extent extent = new Formation.Extent(0, 0, 0, 0, 0, 0);
    private int surveyTimer;
    /** Set once every escort has harmonised this muster, so the chord sounds once, not every tick. */
    private boolean resolved;

    // ---- the fold in hand, from Fold to the last release
    @Nullable
    private UUID foldId;
    /**
     * Whether the fold in hand goes through one rift: the flotilla's setting, taken when Fold was pressed so
     * that changing it mid-fold changes the next one rather than this one.
     */
    private boolean sharedFold;
    private final List<Joined> joined = new ArrayList<>();
    private final java.util.Set<UUID> shaken = new java.util.HashSet<>();
    private final List<Release> releases = new ArrayList<>();
    private int countdown;
    private int foldTicks;
    private int upkeepTimer;

    FleetMuster(RiftCouplerBlockEntity coupler) {
        this.coupler = coupler;
    }

    // ----------------------------------------------------------------- reading

    /** Whether stations have been sounded and the muster has not ended. */
    public boolean active() {
        return phase != Phase.STANDBY;
    }

    /** Whether a fold is committed: drives spinning, counting down, or ships in the fold. */
    public boolean folding() {
        return phase.folding();
    }

    public Phase phase() {
        return phase;
    }

    public List<Row> rows() {
        return rows;
    }

    public FlagshipStatus flagshipStatus() {
        return flagshipStatus;
    }

    @Nullable
    public FleetCourse course() {
        return course;
    }

    public Formation.Extent extent() {
        return extent;
    }

    /** Whether the Fold button may be pressed without holding it. */
    public boolean foldable() {
        List<StationStatus> statuses = new ArrayList<>();
        for (Row row : rows) {
            statuses.add(row.status());
        }
        return flagshipStatus == FlagshipStatus.READY && Readiness.allReady(statuses);
    }

    /** Rift Essence a fold with this many escorts asks for before it starts. */
    public static int essenceNeeded(int escorts, RiftDriveTier tier) {
        // The longest this tier's fold could take, as the spin-up beside it is: the corridor of a jump at
        // the very edge of its range, which is the tier's own figure unless the server has warps take
        // longer the further they go.
        int corridorTicks = uk.co.iceconchy.aerowarptics.warp.CorridorTime.fromConfig(tier.warpTicks(),
                tier.maximumRange());
        int foldTicks = AWConfig.FOLD_COUNTDOWN_TICKS.get() + tier.stabilizeTicksFar()
                + AWConfig.APPROACH_LIMIT_TICKS.get() + corridorTicks + tier.arriveTicks()
                + AWConfig.RIFT_TRANSIT_TICKS.get() + AWConfig.WAKE_ECHO_DELAY_TICKS.get();
        return FlotillaRules.essenceForFold(escorts, AWConfig.COUPLER_UPKEEP_COST.get(), foldTicks,
                AWConfig.COUPLER_UPKEEP_INTERVAL.get());
    }

    // ----------------------------------------------------------------- ticking

    void tick(ServerLevel level) {
        UUID id = coupler.couplerId();
        if (id == null) {
            return;
        }
        FlotillaRegistry registry = FlotillaRegistry.get(level);
        Flotilla flotilla = registry.led(id);
        if (phase.folding()) {
            foldTick(level);
            if (phase.folding() && level.getGameTime() % BROADCAST_INTERVAL == 0L) {
                broadcast(level);
            }
            return;
        }
        if (flotilla == null || flotilla.escorts().isEmpty()) {
            rows = List.of();
            if (phase != Phase.STANDBY) {
                stand(level, StandDown.EMPTY);
            }
            flagshipStatus = readFlagship(level, List.of());
            return;
        }
        read(level, registry, flotilla);
        if (phase == Phase.MUSTER && --surveyTimer <= 0) {
            surveyTimer = AWConfig.SLOT_SURVEY_INTERVAL.get();
            survey(level);
        }
        if (phase != Phase.STANDBY && level.getGameTime() % BROADCAST_INTERVAL == 0L) {
            broadcast(level);
        }
    }

    /** Tells every crew and bystander what the muster looks like now. See {@link ClientboundMusterPacket}. */
    void broadcast(ServerLevel level) {
        Airship flagship = coupler.airship();
        RiftDriveBlockEntity drive = coupler.linkedDrive();
        if (flagship == null || drive == null) {
            return;
        }
        Direction bow = drive.shipBow();
        FlotillaRegistry registry = FlotillaRegistry.get(level);
        List<ClientboundMusterPacket.Entry> entries = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            RiftCouplerBlockEntity escort = RiftCouplerBlockEntity.find(level, registry.coupler(row.coupler()));
            Airship ship = escort == null ? null : escort.airship();
            entries.add(new ClientboundMusterPacket.Entry(
                    ship == null ? ClientboundCouplerPanelPacket.NIL : ship.uuid(),
                    escort == null ? net.minecraft.core.BlockPos.ZERO : escort.getBlockPos(), i + 1,
                    row.slot().right(), row.slot().up(), row.slot().ahead(), (float) row.footprint().radius(),
                    (float) row.footprint().halfHeight(), row.status().ordinal(), row.harmony(), row.tether(),
                    escort == null || escort.linkedDrive() == null ? 0 : escort.linkedDrive().tier().index(),
                    row.actual() != null && Formation.inStation(row.drift(), AWConfig.STATION_TOLERANCE.get()),
                    row.actual() == null ? 0.0F : (float) (row.actual().right() - row.slot().right()),
                    row.actual() == null ? 0.0F : (float) (row.actual().up() - row.slot().up()),
                    row.actual() == null ? 0.0F : (float) (row.actual().ahead() - row.slot().ahead())));
        }
        FleetCues.muster(level, coupler, new ClientboundMusterPacket(flagship.uuid(), coupler.getBlockPos(),
                FleetSpace.centreInShip(flagship), bow.getStepX(), bow.getStepZ(), phase.ordinal(),
                countdownProgress(), (float) (double) AWConfig.STATION_TOLERANCE.get(), drive.tier().index(),
                drive.tetherColour(), entries));
    }

    /** 0 to 1 through the countdown; 0 before it, 1 once the rifts are open. */
    float countdownProgress() {
        return switch (phase) {
            case COUNTDOWN -> 1.0F - countdown / (float) Math.max(1, AWConfig.FOLD_COUNTDOWN_TICKS.get());
            case FOLDING -> 1.0F;
            default -> 0.0F;
        };
    }

    /** Takes a reading of every escort, and of the flagship itself. */
    private void read(ServerLevel level, FlotillaRegistry registry, Flotilla flotilla) {
        Airship flagship = coupler.airship();
        RiftDriveBlockEntity flagshipDrive = coupler.linkedDrive();
        course = flagship == null || flagshipDrive == null ? null : FleetCourse.of(level, flagshipDrive, flagship);
        Direction bow = flagshipDrive == null ? Direction.NORTH : flagshipDrive.shipBow();

        // First pass: who is there, and how heavy. The fold's price depends on the whole fleet, so it
        // cannot be read escort by escort.
        List<Flotilla.Member> members = flotilla.escorts();
        List<RiftCouplerBlockEntity> found = new ArrayList<>();
        double total = flagship == null ? 0.0D : WarpValidator.effectiveMass(flagship);
        double heaviest = total;
        for (Flotilla.Member member : members) {
            RiftCouplerBlockEntity escort = RiftCouplerBlockEntity.find(level, registry.coupler(member.coupler()));
            Airship ship = escort == null ? null : escort.airship();
            found.add(ship == null || escort.linkedDrive() == null ? null : escort);
            if (ship != null && escort.linkedDrive() != null) {
                double mass = WarpValidator.effectiveMass(ship);
                total += mass;
                heaviest = Math.max(heaviest, mass);
            }
        }
        double exponent = AWConfig.FLOTILLA_MASS_EXPONENT.get();
        double tolerance = AWConfig.STATION_TOLERANCE.get();
        int settle = AWConfig.HARMONISE_TICKS.get();
        boolean sounded = phase == Phase.MUSTER;
        // Where the flagship would come out, worked out once for every escort's distance rather than once each.
        Vector3d arrival = course == null || flagship == null ? null : flagshipArrivalCentre(flagship, course);
        boolean look = level.getGameTime() % FASTENING_INTERVAL == 0L;
        if (look) {
            flagshipFastened = flagship != null && Fastenings.holding(flagship).isPresent();
        }

        // What each row said last tick, so the trace hears of a change and not of every reading.
        Map<UUID, StationStatus> was = new HashMap<>();
        if (Trace.enabled()) {
            for (Row row : rows) {
                was.put(row.coupler(), row.status());
            }
        }

        List<Row> next = new ArrayList<>();
        List<FormationSlot> slots = new ArrayList<>();
        List<Formation.Footprint> footprints = new ArrayList<>();
        if (flagship != null) {
            slots.add(FormationSlot.FLAGSHIP);
            footprints.add(FleetSpace.footprint(flagship));
        }
        boolean allReady = true;
        for (int i = 0; i < members.size(); i++) {
            Flotilla.Member member = members.get(i);
            RiftCouplerBlockEntity escort = found.get(i);
            CouplerRecord record = registry.coupler(member.coupler());
            Airship ship = escort == null ? null : escort.airship();
            RiftDriveBlockEntity drive = escort == null ? null : escort.linkedDrive();
            boolean contact = ship != null && drive != null && flagship != null && flagshipDrive != null;
            if (!contact) {
                harmony.remove(member.coupler());
                Formation.Footprint footprint = new Formation.Footprint(0, 0);
                next.add(new Row(member.coupler(), record, member.slot(), null, 0.0D, StationStatus.OUT_OF_CONTACT,
                        0.0F, 0.0F, footprint, 0));
                allReady = false;
                continue;
            }
            FormationSlot actual = FleetSpace.slotOf(flagship, bow, ship);
            double drift = Formation.drift(actual, member.slot());
            RiftDriveState state = drive.state();
            boolean busy = !(state == RiftDriveState.IDLE || state == RiftDriveState.CHARGING
                    || state == RiftDriveState.CHARGED);
            boolean powered = drive.isRunningFastEnough();
            boolean charged = state == RiftDriveState.CHARGED;
            if (look) {
                fastened.put(member.coupler(), Fastenings.holding(ship).isPresent());
            }
            boolean tied = fastened.getOrDefault(member.coupler(), false);
            Readiness.Reach reach = Readiness.Reach.WITHIN;
            boolean affordable = true;
            double distance = 0.0D;
            WarpCost.Formula formula = WarpCost.fromConfig(drive.tier());
            if (course != null) {
                distance = escortDistance(flagship, bow, ship, member.slot(), arrival);
                reach = Readiness.reach(formula, distance, course.crossing(), CrossDimensionWarp.isSupported());
                affordable = !FlotillaRules.exceedsFullCharge(formula, distance, WarpValidator.effectiveMass(ship),
                        total, heaviest, exponent);
            }
            int ticks = Readiness.nextHarmony(harmony.getOrDefault(member.coupler(), 0), sounded,
                    Formation.inStation(drift, tolerance),
                    !busy && !tied && powered && charged && reach == Readiness.Reach.WITHIN && affordable, settle);
            int before = harmony.getOrDefault(member.coupler(), 0);
            harmony.put(member.coupler(), ticks);
            if (before < settle && ticks >= settle) {
                FleetCues.harmonised(level, coupler, member.coupler());
            }
            Readiness.Reading reading = new Readiness.Reading(true, busy, powered, charged, reach, affordable,
                    drift, tolerance, ticks, settle, surveys.getOrDefault(member.coupler(), Readiness.Survey.UNKNOWN),
                    tied);
            StationStatus status = Readiness.status(reading);
            if (Trace.enabled() && status != was.get(member.coupler())) {
                // Read every tick, so said once a change - and with the two distances a row has, because the
                // board only has room for the word and "range" was once taken for the wrong one of them.
                Trace.log(Trace.FLEET, "{} escort {} is {} (was {}): {} from its slot of a {} tolerance; course {}"
                                + " away, its {} drive jumps {} to {}; charge {}",
                        Trace.pos(coupler.getBlockPos()), member.coupler(), status, was.get(member.coupler()),
                        Trace.num(drift), Trace.num(tolerance), course == null ? "not set" : Trace.num(distance),
                        drive.tier(), Trace.num(formula.minimumDistance()), Trace.num(formula.maximumDistance()),
                        Trace.percent(drive.charge()));
            }
            allReady &= status.ready() || status == StationStatus.SURVEYING || status == StationStatus.OBSTRUCTED;
            Formation.Footprint footprint = FleetSpace.footprint(ship);
            next.add(new Row(member.coupler(), record, member.slot(), actual, drift, status, drive.charge(),
                    Readiness.harmony(ticks, settle), footprint, drive.tetherColour()));
            slots.add(member.slot());
            footprints.add(footprint);
        }
        rows = List.copyOf(next);
        extent = slots.isEmpty() ? new Formation.Extent(0, 0, 0, 0, 0, 0) : Formation.extent(slots, footprints);
        flagshipStatus = readFlagship(level, rows);

        // Every escort harmonised: the hums resolve, once. Anything slipping afterwards lets the chord go
        // again, so it can sound a second time once they have all found it again.
        boolean harmonised = sounded && !rows.isEmpty() && allReady && rows.stream().allMatch(
                row -> harmony.getOrDefault(row.coupler(), 0) >= settle);
        if (harmonised && !resolved) {
            FleetCues.resolved(level, coupler);
        }
        resolved = harmonised;
    }

    /**
     * An escort's jump, as it will be priced: from where it is to where its slot will be at the far end,
     * with the flagship coming in straight above its target at the course's height.
     */
    private double escortDistance(Airship flagship, Direction bow, Airship escort, FormationSlot slot,
                                  @Nullable Vector3d flagshipArrival) {
        FleetCourse here = course;
        if (here == null) {
            return 0.0D;
        }
        Vector3d arrival = new Vector3d(flagshipArrival != null ? flagshipArrival : flagshipArrivalCentre(flagship, here));
        arrival.add(FleetSpace.worldStep(flagship, bow, slot));
        return here.distanceFor(escort, arrival);
    }

    /** Where the flagship's centre comes out if nothing is in the way: straight in over its target. */
    private static Vector3d flagshipArrivalCentre(Airship flagship, FleetCourse course) {
        SafeArrival.Result straight = SafeArrival.straightIn(flagship, course.target(), flagship.orientation(),
                course.arrivalHeight());
        if (straight == null) {
            return new Vector3d(course.target().getX() + 0.5D, course.target().getY() + course.arrivalHeight(),
                    course.target().getZ() + 0.5D);
        }
        return straight.bounds().center(new Vector3d());
    }

    /** The flagship's own readiness, the first thing wrong with it - and a line in the trace when that changes. */
    private FlagshipStatus readFlagship(ServerLevel level, List<Row> escorts) {
        FlagshipStatus next = flagshipReading(level, escorts);
        if (Trace.enabled() && next != flagshipStatus) {
            RiftDriveBlockEntity drive = coupler.linkedDrive();
            FleetCourse here = course;
            WarpCost.Formula formula = drive == null ? null : WarpCost.fromConfig(drive.tier());
            Trace.log(Trace.FLEET, "{} flagship is {} (was {}): course {}, its {} drive jumps {} to {}; phase {}, {}"
                            + " escort(s)", Trace.pos(coupler.getBlockPos()), next, flagshipStatus,
                    here == null ? "not set" : here.label() + " " + Trace.num(here.distance()) + " away",
                    drive == null ? "missing" : drive.tier().toString(),
                    formula == null ? "-" : Trace.num(formula.minimumDistance()),
                    formula == null ? "-" : Trace.num(formula.maximumDistance()), phase, escorts.size());
        }
        return next;
    }

    private FlagshipStatus flagshipReading(ServerLevel level, List<Row> escorts) {
        if (phase.folding()) {
            return FlagshipStatus.FOLDING;
        }
        RiftDriveBlockEntity drive = coupler.linkedDrive();
        if (drive == null) {
            return FlagshipStatus.NO_DRIVE;
        }
        Airship flagship = coupler.airship();
        if (flagship == null) {
            return FlagshipStatus.NOT_ABOARD;
        }
        if (drive.mode() == DriveMode.FREE_CRUISE) {
            return FlagshipStatus.CRUISE;
        }
        FleetCourse here = course;
        if (here == null) {
            return FlagshipStatus.NO_COURSE;
        }
        if (here.crossing() && !CrossDimensionWarp.isSupported()) {
            return FlagshipStatus.NO_CROSSING;
        }
        RiftDriveState state = drive.state();
        if (!(state == RiftDriveState.IDLE || state == RiftDriveState.CHARGING || state == RiftDriveState.CHARGED)) {
            return FlagshipStatus.BUSY;
        }
        if (flagshipFastened) {
            return FlagshipStatus.FASTENED;
        }
        if (!drive.isRunningFastEnough()) {
            return FlagshipStatus.UNPOWERED;
        }
        if (state != RiftDriveState.CHARGED) {
            return FlagshipStatus.UNCHARGED;
        }
        WarpCost.Formula formula = WarpCost.fromConfig(drive.tier());
        WarpFailure range = formula.checkRange(here.distance());
        if (range.isFailure()) {
            return range == WarpFailure.DESTINATION_TOO_CLOSE ? FlagshipStatus.TOO_CLOSE : FlagshipStatus.TOO_FAR;
        }
        double own = WarpValidator.effectiveMass(flagship);
        double total = own;
        double heaviest = own;
        for (Row row : escorts) {
            RiftCouplerBlockEntity escort = RiftCouplerBlockEntity.find(level, row.record());
            Airship ship = escort == null ? null : escort.airship();
            if (ship != null) {
                double mass = WarpValidator.effectiveMass(ship);
                total += mass;
                heaviest = Math.max(heaviest, mass);
            }
        }
        if (FlotillaRules.exceedsFullCharge(formula, here.distance(), own, total, heaviest,
                AWConfig.FLOTILLA_MASS_EXPONENT.get())) {
            return FlagshipStatus.UNAFFORDABLE;
        }
        if (phase != Phase.MUSTER) {
            return FlagshipStatus.NOT_SOUNDED;
        }
        if (coupler.contents().getAmount() < essenceNeeded(escorts.size(), drive.tier())) {
            return FlagshipStatus.NO_ESSENCE;
        }
        return switch (flagshipSurvey) {
            case OBSTRUCTED -> FlagshipStatus.OBSTRUCTED;
            case UNKNOWN -> FlagshipStatus.SURVEYING;
            case CLEAR -> FlagshipStatus.READY;
        };
    }

    // ------------------------------------------------------------------ survey

    /**
     * Looks at where every ship in the formation would come out, if the flagship came in straight over its
     * target - and keeps that ground loading while it does.
     *
     * <p>Reads only ground that has already loaded. A survey that loaded what it read would do on the
     * server thread, every couple of seconds, exactly what the drive's own preload exists to keep off it;
     * instead the ground is claimed in the background and a slot whose ground has not arrived yet says
     * "Surveying" until it has. The fold's own arrival search is the check that decides anything - this is
     * so a pilot can see what it is likely to say before pressing the button.
     */
    private void survey(ServerLevel level) {
        FleetCourse here = course;
        Airship flagship = coupler.airship();
        RiftDriveBlockEntity drive = coupler.linkedDrive();
        if (here == null || flagship == null || drive == null) {
            flagshipSurvey = Readiness.Survey.UNKNOWN;
            surveys.clear();
            return;
        }
        Direction bow = drive.shipBow();
        Vector3d centre = flagshipArrivalCentre(flagship, here);
        List<ArrivalTicket.Square> squares = new ArrayList<>();
        BoundingBox3d own = around(flagship.worldBounds(), flagship.centre(new Vector3d()), centre);
        flagshipSurvey = look(here.level(), flagship, own, squares);
        FlotillaRegistry registry = FlotillaRegistry.get(level);
        for (Row row : rows) {
            RiftCouplerBlockEntity escort = RiftCouplerBlockEntity.find(level, registry.coupler(row.coupler()));
            Airship ship = escort == null ? null : escort.airship();
            if (ship == null) {
                surveys.remove(row.coupler());
                continue;
            }
            Vector3d slot = new Vector3d(centre).add(FleetSpace.worldStep(flagship, bow, row.slot()));
            surveys.put(row.coupler(), look(here.level(), ship, around(ship.worldBounds(),
                    ship.centre(new Vector3d()), slot), squares));
        }
        if (!squares.isEmpty()) {
            ArrivalTicket.preload(here.level(), ArrivalTicket.withinBudget(squares, ArrivalTicket.maxChunks()));
        }
    }

    /** A hull's world box, moved so its centre is at {@code to}, with the arrival clearance round it. */
    private static BoundingBox3d around(BoundingBox3dc hull, Vector3d from, Vector3d to) {
        double pad = AWConfig.ARRIVAL_CLEARANCE.get();
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        return new BoundingBox3d(hull.minX() + dx - pad, hull.minY() + dy - pad, hull.minZ() + dz - pad,
                hull.maxX() + dx + pad, hull.maxY() + dy + pad, hull.maxZ() + dz + pad);
    }

    /** One box's verdict, from loaded ground only; the ground it wanted is added to {@code squares}. */
    private static Readiness.Survey look(ServerLevel level, Airship ship, BoundingBox3d box,
                                         List<ArrivalTicket.Square> squares) {
        int minX = (int) Math.floor(box.minX()) >> 4;
        int maxX = (int) Math.floor(box.maxX()) >> 4;
        int minZ = (int) Math.floor(box.minZ()) >> 4;
        int maxZ = (int) Math.floor(box.maxZ()) >> 4;
        List<ArrivalTicket.Square> wanted = ArrivalTicket.tile(minX, minZ, maxX, maxZ);
        squares.addAll(wanted);
        if (!ArrivalTicket.loaded(level, wanted)) {
            return Readiness.Survey.UNKNOWN;
        }
        return switch (SafeArrival.clearance(ship, level, box)) {
            case CLEAR -> Readiness.Survey.CLEAR;
            case OBSTRUCTED -> Readiness.Survey.OBSTRUCTED;
            case UNPROVEN -> Readiness.Survey.UNKNOWN;
        };
    }

    // ----------------------------------------------------------------- orders

    /**
     * Sound Stations: the horn, the tethers, and the escorts start finding the flagship's note.
     *
     * <p>Sounding again while a muster is already on sounds the horn again and changes nothing else, so
     * that it never throws away the harmony the escorts have built. The Fleet Board offers Stand Down in its
     * place while a muster is on - see {@link #standDown}.
     */
    public boolean soundStations(ServerLevel level, ServerPlayer player) {
        if (!coupler.mayCommand(player) || phase.folding() || rows.isEmpty()) {
            return false;
        }
        if (phase == Phase.STANDBY) {
            phase = Phase.MUSTER;
            harmony.clear();
            surveys.clear();
            flagshipSurvey = Readiness.Survey.UNKNOWN;
            resolved = false;
            surveyTimer = 0;
        }
        FleetCues.soundStations(level, coupler);
        return true;
    }

    /**
     * Stand Down: ends a muster that has not folded, as though stations had never been sounded. The escorts'
     * harmony goes with it, so a muster sounded again starts them finding the note from the beginning.
     *
     * <p>Only a muster. A fold in hand is past standing down - the drives are spinning, and a flagship that
     * wants out cancels its own drive, which calls the fold off for everyone.
     */
    public boolean standDown(ServerLevel level, ServerPlayer player) {
        if (!coupler.mayCommand(player) || phase != Phase.MUSTER) {
            return false;
        }
        stand(level, StandDown.ORDERED);
        return true;
    }

    /** Fold, from the Fleet Board. A refusal is told to the player the way a drive's own refusals are. */
    public boolean fold(ServerLevel level, ServerPlayer player, boolean forced) {
        WarpFailure result = call(level, player, forced ? Call.FORCED : Call.BOARD);
        // A tied-down ship has already said what ties it and where, to the crews and to whoever gave the
        // order; a bare "tied down" after it would only write over the half that says where.
        if (result != null && result.isFailure() && result != WarpFailure.SHIP_FASTENED) {
            uk.co.iceconchy.aerowarptics.network.AWNetwork.sendTo(player,
                    new uk.co.iceconchy.aerowarptics.network.ClientboundWarpFeedbackPacket(result));
        }
        return result == WarpFailure.NONE;
    }

    /**
     * Commits a fold: every escort that is going is given its order and starts spinning up, and the
     * flagship's drive fires its course at the fleet's price.
     *
     * <p>The escorts go first, so that a flagship whose own warp is refused has not left anything already
     * committed behind it - they are stood down again, at no cost, before the refusal is returned. Nothing
     * moves here; the fold waits for the flagship's spin, then counts down.
     *
     * @return {@link WarpFailure#NONE} for a fold under way, a refusal, or {@code null} when a beacon or a
     * circuit finds no flotilla to take and the flagship should fly alone as it always did
     */
    @Nullable
    public WarpFailure call(ServerLevel level, @Nullable ServerPlayer player, Call how) {
        WarpFailure result = commit(level, player, how);
        if (Trace.enabled()) {
            StringBuilder stations = new StringBuilder();
            for (Row row : rows) {
                stations.append(stations.isEmpty() ? "" : ", ").append(row.coupler()).append('=')
                        .append(row.status()).append(" drift ").append(Trace.num(row.drift()))
                        .append(" charge ").append(Trace.percent(row.charge()));
            }
            Trace.log(Trace.FLEET, "{} {} fold by {} -> {} (phase {}, flotilla {}, course {}) stations: [{}]",
                    Trace.pos(coupler.getBlockPos()), how, player == null ? "circuit or beacon" : Trace.who(player),
                    result == null ? "no flotilla to take - the flagship flies alone" : result.getSerializedName(),
                    phase, coupler.couplerId(), course == null ? "none" : course.label() + " " + Trace.pos(course.target()),
                    stations);
        }
        return result;
    }

    @Nullable
    private WarpFailure commit(ServerLevel level, @Nullable ServerPlayer player, Call how) {
        UUID id = coupler.couplerId();
        FlotillaRegistry registry = FlotillaRegistry.get(level);
        Flotilla flotilla = id == null ? null : registry.led(id);
        boolean board = how.fromBoard();
        if (flotilla == null || flotilla.escorts().isEmpty()) {
            return board ? WarpFailure.FLOTILLA_NOT_READY : null;
        }
        if (how == Call.BEACON && !flotilla.beaconCallsAll()) {
            return null;
        }
        if (phase.folding()) {
            return WarpFailure.DRIVE_BUSY;
        }
        RiftDriveBlockEntity drive = coupler.linkedDrive();
        Airship flagship = coupler.airship();
        if (drive == null || flagship == null) {
            return board ? WarpFailure.NO_AIRSHIP : null;
        }
        if (board) {
            if (player == null || !coupler.mayCommand(player)) {
                return WarpFailure.UNAUTHORISED;
            }
            WarpFailure authority = WarpValidator.validatePlayer(player, drive);
            if (authority.isFailure()) {
                return authority;
            }
        }
        read(level, registry, flotilla);
        FleetCourse here = course;
        if (here == null) {
            return board ? WarpFailure.ANCHOR_MISSING : null;
        }

        // Who goes.
        List<Row> going = new ArrayList<>();
        List<Row> behind = new ArrayList<>();
        for (Row row : rows) {
            boolean ready = row.status().ready();
            boolean willing = ready || row.status() == StationStatus.HARMONISING
                    || row.status() == StationStatus.SURVEYING || row.status() == StationStatus.OBSTRUCTED;
            boolean goes = how.fromBoard() ? ready : willing;
            if (goes) {
                going.add(row);
            } else {
                behind.add(row);
            }
        }
        switch (how) {
            case BOARD -> {
                if (phase != Phase.MUSTER || !foldable()) {
                    return WarpFailure.FLOTILLA_NOT_READY;
                }
            }
            case FORCED -> {
                if (phase != Phase.MUSTER || !flagshipStatus.forceable()) {
                    return WarpFailure.FLOTILLA_NOT_READY;
                }
            }
            case BEACON, REDSTONE -> {
                if (going.isEmpty()) {
                    return null;
                }
            }
        }
        if (coupler.contents().getAmount() < essenceNeeded(going.size(), drive.tier())) {
            return WarpFailure.INSUFFICIENT_ESSENCE;
        }

        // What it costs: every hull going, flagship included, priced together.
        double exponent = AWConfig.FLOTILLA_MASS_EXPONENT.get();
        double flagshipMass = WarpValidator.effectiveMass(flagship);
        double total = flagshipMass;
        double heaviest = flagshipMass;
        List<RiftCouplerBlockEntity> escorts = new ArrayList<>();
        for (Row row : going) {
            RiftCouplerBlockEntity escort = RiftCouplerBlockEntity.find(level, row.record());
            escorts.add(escort);
            Airship ship = escort == null ? null : escort.airship();
            if (ship != null) {
                double mass = WarpValidator.effectiveMass(ship);
                total += mass;
                heaviest = Math.max(heaviest, mass);
            }
        }
        RiftDriveBlockEntity.FoldLook look = drive.foldLook();
        boolean shared = flotilla.sharedRift();
        UUID fold = UUID.randomUUID();
        UUID initiator = player != null ? player.getUUID() : null;
        Direction bow = drive.shipBow();

        // The escorts' orders first.
        List<Joined> committed = new ArrayList<>();
        List<RiftDriveBlockEntity> committedDrives = new ArrayList<>();
        for (int i = 0; i < going.size(); i++) {
            Row row = going.get(i);
            RiftCouplerBlockEntity escort = escorts.get(i);
            Airship ship = escort == null ? null : escort.airship();
            RiftDriveBlockEntity escortDrive = escort == null ? null : escort.linkedDrive();
            WarpFailure joinedResult = WarpFailure.NO_AIRSHIP;
            if (ship != null && escortDrive != null) {
                double distance = escortDistance(flagship, bow, ship, row.slot(), null);
                float cost = (float) FlotillaRules.foldCost(WarpCost.fromConfig(escortDrive.tier()), distance,
                        WarpValidator.effectiveMass(ship), total, heaviest, exponent);
                joinedResult = escortDrive.followFold(new FoldOrder(fold, id, row.coupler(), false, initiator,
                        here.level().dimension(), here.target(), distance, cost, here.label(), look.colour(),
                        look.accent(), look.theme(), look.intensity()));
            }
            if (joinedResult.isFailure()) {
                if (how == Call.BOARD) {
                    standDown(committedDrives);
                    return joinedResult;
                }
                behind.add(row);
                continue;
            }
            committed.add(new Joined(row.coupler(), row.slot(), rows.indexOf(row) + 1, row.footprint()));
            committedDrives.add(escortDrive);
        }

        // Then the flagship's own.
        float flagshipCost = (float) FlotillaRules.foldCost(WarpCost.fromConfig(drive.tier()), here.distance(),
                flagshipMass, total, heaviest, exponent);
        WarpFailure led = drive.leadFold(new FoldOrder(fold, id, id, true, initiator, here.level().dimension(),
                here.target(), here.distance(), flagshipCost, here.label(), look.colour(), look.accent(),
                look.theme(), look.intensity()), player);
        if (led.isFailure()) {
            standDown(committedDrives);
            return led;
        }

        // A forced fold drops whoever it leaves; a beacon or a circuit only leaves them where they are.
        int left = 0;
        if (how == Call.FORCED) {
            for (Row row : behind) {
                if (registry.castOff(id, row.coupler())) {
                    left++;
                    FleetNotices.leftBehind(level, row.record(), registry.coupler(id));
                    RiftCouplerBlockEntity escort = RiftCouplerBlockEntity.find(level, row.record());
                    FleetCues.leftBehind(level, escort == null ? null : escort.airship());
                }
            }
        }
        int cap = FlotillaRules.cap(drive.tier().flotillaEscorts());
        boolean full = cap != Integer.MAX_VALUE && committed.size() >= cap;
        List<UUID> ids = new ArrayList<>();
        for (Joined j : committed) {
            ids.add(j.coupler());
        }
        registry.folds().begin(fold, id, ids, left, full, level.getGameTime());

        foldId = fold;
        sharedFold = shared;
        joined.clear();
        joined.addAll(committed);
        shaken.clear();
        releases.clear();
        countdown = 0;
        foldTicks = 0;
        upkeepTimer = 0;
        phase = Phase.COMMITTED;
        if (!board) {
            // Nobody sounded stations for a beacon or a circuit: the horn is how the escorts find out.
            FleetCues.soundStations(level, coupler);
        }
        return WarpFailure.NONE;
    }

    /** Stands down escorts whose drives had taken their orders, at no cost to them. */
    private static void standDown(List<RiftDriveBlockEntity> drives) {
        if (!drives.isEmpty()) {
            Trace.log(Trace.FLEET, "standing down {} escort drive(s) already committed, at no cost to them",
                    drives.size());
        }
        for (RiftDriveBlockEntity drive : drives) {
            drive.callOffFold();
        }
    }

    /**
     * Asked by the flagship's drive each tick it is fully spun up and ready to open its rift. The first
     * ask starts the countdown; the answer is yes once it has run.
     */
    public boolean readyToTear(ServerLevel level) {
        if (phase == Phase.COMMITTED) {
            phase = Phase.COUNTDOWN;
            countdown = AWConfig.FOLD_COUNTDOWN_TICKS.get();
            Trace.log(Trace.FLEET, "{} flagship spun up: fold {} counting down {}t with {} escort(s)",
                    Trace.pos(coupler.getBlockPos()), foldId, countdown, joined.size());
            FleetCues.countdown(level, coupler, countdown);
            return false;
        }
        return phase != Phase.COUNTDOWN || countdown <= 0;
    }

    /**
     * The formation, for the flagship's search to prove along with its own hull: every escort that is going,
     * its slot as a step in the world from the flagship's centre, its hull, and the bearing and run-out it
     * will come out of its own rift on - or, going through the fleet's one rift, the fleet's.
     *
     * @param passage the fleet's passage through one rift, or {@code null} for a rift each
     */
    public List<SafeArrival.Companion> companions(ServerLevel level, @Nullable FleetPassage passage) {
        Airship flagship = coupler.airship();
        RiftDriveBlockEntity drive = coupler.linkedDrive();
        if (flagship == null || drive == null) {
            return List.of();
        }
        Direction bow = drive.shipBow();
        FlotillaRegistry registry = FlotillaRegistry.get(level);
        List<SafeArrival.Companion> out = new ArrayList<>();
        for (Joined j : joined) {
            RiftCouplerBlockEntity escort = RiftCouplerBlockEntity.find(level, registry.coupler(j.coupler()));
            Airship ship = escort == null ? null : escort.airship();
            RiftDriveBlockEntity escortDrive = escort == null ? null : escort.linkedDrive();
            if (ship == null || escortDrive == null) {
                continue;
            }
            // Going through the fleet's one rift, an escort comes out along the flagship's bearing on the
            // fleet's run out, whichever way it happens to face; a straggler comes out of its own.
            boolean inStep = passage != null && !shaken.contains(j.coupler());
            Vector3d approach = inStep ? new Vector3d(passage.heading())
                    : WarpFlight.worldBow(ship, escortDrive.shipBow());
            double runOut;
            if (inStep) {
                runOut = passage.emergeDistance();
            } else {
                FlightGeometry geometry = WarpFlight.geometry(ship, approach, escortDrive.tier().arriveTicks());
                runOut = geometry == null ? 0.0D : geometry.emergeDistance();
            }
            out.add(new SafeArrival.Companion(ship, FleetSpace.worldStep(flagship, bow, j.slot()),
                    new BoundingBox3d(ship.worldBounds()), approach, runOut));
        }
        return out;
    }

    /**
     * The passage the fleet flies through one rift, from where every ship going through it is now.
     *
     * <p>{@code null} when the fold in hand is one of a rift each, or there is no flagship to lead it. Asked
     * by the flagship's drive as it spins up, for its preload, and again as it plans; the far end is added in
     * {@link #flagshipPlanned}, once the search has put the formation down.
     */
    @Nullable
    public FleetPassage passage(ServerLevel level) {
        if (!sharedFold || foldId == null) {
            return null;
        }
        Airship flagship = coupler.airship();
        RiftDriveBlockEntity drive = coupler.linkedDrive();
        if (flagship == null || drive == null) {
            return null;
        }
        Vector3d heading = WarpFlight.worldBow(flagship, drive.shipBow());
        int emergeTicks = drive.tier().arriveTicks();
        FleetPassage.Hull own = hull(flagship, heading, emergeTicks);
        if (own == null) {
            return null;
        }
        List<FleetPassage.Hull> hulls = new ArrayList<>();
        hulls.add(own);
        FlotillaRegistry registry = FlotillaRegistry.get(level);
        for (Joined j : joined) {
            if (shaken.contains(j.coupler())) {
                continue;
            }
            RiftCouplerBlockEntity escort = RiftCouplerBlockEntity.find(level, registry.coupler(j.coupler()));
            Airship ship = escort == null ? null : escort.airship();
            FleetPassage.Hull hull = ship == null ? null : hull(ship, heading, emergeTicks);
            if (hull != null) {
                hulls.add(hull);
            }
        }
        return FleetPassage.of(heading, hulls, AWConfig.RIFT_LEAD_DISTANCE.get(), AWConfig.STATION_TOLERANCE.get(),
                AWConfig.RIFT_TRANSIT_TICKS.get(), drive.corridorTicks(), emergeTicks,
                AWConfig.EMERGE_DISTANCE_FACTOR.get());
    }

    /** A ship as a passage sees it: where its hull is, how long it is along the heading, its own aperture. */
    @Nullable
    private static FleetPassage.Hull hull(Airship ship, Vector3d heading, int emergeTicks) {
        FlightGeometry geometry = WarpFlight.geometry(ship, heading, emergeTicks);
        return geometry == null ? null
                : new FleetPassage.Hull(ship.worldBounds().center(new Vector3d()), geometry.hullSpan(),
                        geometry.riftRadius());
    }

    /**
     * The flagship's search has put the formation down: work out where each escort comes out, and when
     * its rift opens - front to back, a few ticks apart, so the fleet goes like a broadside.
     *
     * <p>An escort shaken loose in the countdown is not put in its slot. It is thrown off to the side,
     * somewhere its hull fits clear of the ground and of every other ship's slot, and comes the long way
     * round through a Wake Echo. If nowhere nearby is clear it comes out in its own slot after all, which
     * this search has just proved clear.
     *
     * <p>Going through one rift, there is no broadside. The passage is finished - its exit, and when the
     * fleet sets off - and every escort in step is told at once.
     *
     * @param passage the passage the flagship planned with, or {@code null} for a rift each
     * @return the finished passage for the flagship's own flight to keep step with, or {@code null}
     */
    @Nullable
    public FleetPassage flagshipPlanned(ServerLevel level, ServerLevel destination, WarpFlight planned,
                                        @Nullable FleetPassage passage) {
        Airship flagship = coupler.airship();
        RiftDriveBlockEntity drive = coupler.linkedDrive();
        if (flagship == null || drive == null || foldId == null) {
            return null;
        }
        Direction bow = drive.shipBow();
        Vector3d arrival = planned.arrivalCentre();
        List<FormationSlot> slots = new ArrayList<>();
        for (Joined j : joined) {
            slots.add(j.slot());
        }
        FlotillaRegistry registry = FlotillaRegistry.get(level);
        FleetPassage shared = passage == null ? null
                : finishPassage(level, registry, flagship, bow, arrival, passage);
        List<Integer> order = Formation.rippleOrder(slots);
        int ripple = AWConfig.RIPPLE_TICKS.get();
        releases.clear();
        for (int rank = 0; rank < order.size(); rank++) {
            Joined j = joined.get(order.get(rank));
            boolean echo = shaken.contains(j.coupler());
            FormationSlot slot = j.slot();
            if (echo) {
                RiftCouplerBlockEntity escort = RiftCouplerBlockEntity.find(level, registry.coupler(j.coupler()));
                Airship ship = escort == null ? null : escort.airship();
                if (ship != null) {
                    slot = echoSlot(destination, flagship, bow, arrival, j, ship);
                }
            }
            Vector3d centre = new Vector3d(arrival).add(FleetSpace.worldStep(flagship, bow, slot));
            // Bound for a station, an escort's slot is its berth - shaken loose or not: there is nowhere else.
            uk.co.iceconchy.aerowarptics.berth.Berths.Assignment berth = null;
            if (!berths.isEmpty()) {
                RiftCouplerBlockEntity berthing = RiftCouplerBlockEntity.find(level, registry.coupler(j.coupler()));
                Airship berthingShip = berthing == null ? null : berthing.airship();
                berth = berthingShip == null ? null : berths.get(berthingShip.uuid());
                if (berth != null) {
                    centre.set(berth.centre());
                }
            }
            // Through the fleet's one rift there is no broadside: everybody is told at once, and every flight
            // holds for the passage's start. A straggler still tears its own, in its turn.
            boolean inStep = shared != null && !echo;
            releases.add(new Release(j.coupler(), new net.minecraft.world.phys.Vec3(centre.x, centre.y, centre.z),
                    inStep ? foldTicks : foldTicks + (rank + 1) * ripple, echo, inStep ? shared : null, berth));
        }
        berths.clear();
        phase = Phase.FOLDING;
        return shared;
    }

    /**
     * The passage's far end: the exit, stood in front of where the formation reappears, and when the fleet
     * sets off. And the fold is told who is going through, so that each end of the rift closes behind the
     * last ship through it and not the first.
     */
    private FleetPassage finishPassage(ServerLevel level, FlotillaRegistry registry, Airship flagship, Direction bow,
                                       Vector3d arrival, FleetPassage passage) {
        Vector3d heading = new Vector3d(passage.heading());
        List<FleetPassage.Hull> resting = new ArrayList<>();
        List<UUID> through = new ArrayList<>();
        FleetPassage.Hull own = hull(flagship, heading, passage.emergeTicks());
        if (own != null) {
            resting.add(own.at(arrival));
        }
        through.add(coupler.couplerId());
        for (Joined j : joined) {
            if (shaken.contains(j.coupler())) {
                continue;
            }
            RiftCouplerBlockEntity escort = RiftCouplerBlockEntity.find(level, registry.coupler(j.coupler()));
            Airship ship = escort == null ? null : escort.airship();
            FleetPassage.Hull hull = ship == null ? null : hull(ship, heading, passage.emergeTicks());
            if (hull != null) {
                resting.add(hull.at(new Vector3d(arrival).add(FleetSpace.worldStep(flagship, bow, j.slot()))));
                through.add(j.coupler());
            }
        }
        if (resting.isEmpty()) {
            resting.add(new FleetPassage.Hull(arrival, 0.0D, passage.entry().radius()));
        }
        registry.folds().share(foldId, through);
        return passage.arriving(resting, level.getGameTime() + FleetPassage.START_DELAY_TICKS);
    }

    /** Where a straggler is thrown: see {@link WakeEcho}. */
    private FormationSlot echoSlot(ServerLevel destination, Airship flagship, Direction bow, Vector3d arrival,
                                   Joined straggler, Airship ship) {
        double tolerance = AWConfig.STATION_TOLERANCE.get();
        double edge = straggler.footprint().radius() + tolerance;
        List<FormationSlot> others = new ArrayList<>();
        List<Formation.Footprint> footprints = new ArrayList<>();
        others.add(FormationSlot.FLAGSHIP);
        footprints.add(FleetSpace.footprint(flagship));
        for (Joined j : joined) {
            if (!j.coupler().equals(straggler.coupler())) {
                others.add(j.slot());
                footprints.add(j.footprint());
            }
        }
        List<FormationSlot> candidates = WakeEcho.candidates(foldId.getLeastSignificantBits()
                        ^ straggler.coupler().getMostSignificantBits(), ECHO_CANDIDATES,
                edge + AWConfig.WAKE_ECHO_MIN_OFFSET.get(), edge + AWConfig.WAKE_ECHO_MAX_OFFSET.get());
        Vector3d now = ship.centre(new Vector3d());
        BoundingBox3dc hull = ship.worldBounds();
        double pad = AWConfig.ARRIVAL_CLEARANCE.get();
        WakeEcho.Placement placement = WakeEcho.place(straggler.slot(), candidates, at -> {
            if (Formation.firstClash(at, straggler.footprint(), others, footprints, tolerance) >= 0) {
                return false;
            }
            Vector3d centre = new Vector3d(arrival).add(FleetSpace.worldStep(flagship, bow, at));
            BoundingBox3d box = new BoundingBox3d(hull.minX() - now.x + centre.x - pad, hull.minY() - now.y + centre.y - pad,
                    hull.minZ() - now.z + centre.z - pad, hull.maxX() - now.x + centre.x + pad,
                    hull.maxY() - now.y + centre.y + pad, hull.maxZ() - now.z + centre.z + pad);
            return SafeArrival.clearance(ship, destination, box) == SafeArrival.Clearance.CLEAR;
        });
        return placement.slot();
    }

    /** Places a straggler may be thrown. Each is a clearance read, so not many. */
    private static final int ECHO_CANDIDATES = 12;

    /** One tick of a fold in hand. */
    private void foldTick(ServerLevel level) {
        foldTicks++;
        RiftDriveBlockEntity drive = coupler.linkedDrive();
        boolean flagshipGoing = drive != null && drive.foldOrder() != null && foldId != null
                && foldId.equals(drive.foldOrder().fold());
        // The coupler's upkeep, a trickle per escort for as long as the fold is in hand.
        if (--upkeepTimer <= 0) {
            upkeepTimer = Math.max(1, AWConfig.COUPLER_UPKEEP_INTERVAL.get());
            coupler.spend(AWConfig.COUPLER_UPKEEP_COST.get() * joined.size());
        }
        switch (phase) {
            case COMMITTED -> {
                if (!flagshipGoing) {
                    callOff(level);
                }
            }
            case COUNTDOWN -> {
                if (!flagshipGoing) {
                    callOff(level);
                    return;
                }
                countdown--;
                watchStation(level);
            }
            case FOLDING -> release(level);
            default -> {
            }
        }
    }

    /**
     * Watches every escort's station through the countdown. Drifting out of tolerance now is too late to
     * be cancelled and too late to be in formation: the ship is shaken loose, for good, and told so.
     */
    private void watchStation(ServerLevel level) {
        Airship flagship = coupler.airship();
        RiftDriveBlockEntity drive = coupler.linkedDrive();
        if (flagship == null || drive == null || foldId == null) {
            return;
        }
        Direction bow = drive.shipBow();
        double tolerance = AWConfig.STATION_TOLERANCE.get();
        FlotillaRegistry registry = FlotillaRegistry.get(level);
        boolean title = foldTicks % TITLE_INTERVAL == 0;
        for (Joined j : joined) {
            RiftCouplerBlockEntity escort = RiftCouplerBlockEntity.find(level, registry.coupler(j.coupler()));
            Airship ship = escort == null ? null : escort.airship();
            if (ship == null) {
                continue;
            }
            boolean inStation = Formation.inStation(Formation.drift(FleetSpace.slotOf(flagship, bow, ship), j.slot()),
                    tolerance);
            if (!inStation && shaken.add(j.coupler())) {
                registry.folds().shaken(foldId, j.coupler());
                FleetCues.slotState(level, escort, j.number(), false);
            } else if (title) {
                FleetCues.slotState(level, escort, j.number(), inStation && !shaken.contains(j.coupler()));
            }
        }
    }

    /** How often the countdown tells each escort's crew how their slot stands. */
    private static final int TITLE_INTERVAL = 20;

    /** Opens each escort's rift in its turn. The last one out ends the muster. */
    private void release(ServerLevel level) {
        FlotillaRegistry registry = FlotillaRegistry.get(level);
        java.util.Iterator<Release> due = releases.iterator();
        while (due.hasNext()) {
            Release next = due.next();
            if (next.at() > foldTicks) {
                continue;
            }
            due.remove();
            RiftCouplerBlockEntity escort = RiftCouplerBlockEntity.find(level, registry.coupler(next.coupler()));
            RiftDriveBlockEntity escortDrive = escort == null ? null : escort.linkedDrive();
            if (escortDrive != null && escortDrive.foldOrder() != null && foldId != null
                    && foldId.equals(escortDrive.foldOrder().fold())) {
                escortDrive.releaseFold(next.centre(), next.echo() ? AWConfig.WAKE_ECHO_DELAY_TICKS.get() : 0,
                        next.echo(), next.passage());
                if (next.berth() != null) {
                    escortDrive.berthFold(next.berth());
                }
            }
        }
        if (releases.isEmpty()) {
            finish();
        }
    }

    /** The flagship's drive stopped before its rift opened: every escort stands down, and the muster goes on. */
    private void callOff(ServerLevel level) {
        FlotillaRegistry registry = FlotillaRegistry.get(level);
        for (Joined j : joined) {
            RiftCouplerBlockEntity escort = RiftCouplerBlockEntity.find(level, registry.coupler(j.coupler()));
            RiftDriveBlockEntity escortDrive = escort == null ? null : escort.linkedDrive();
            if (escortDrive != null && escortDrive.foldOrder() != null && foldId != null
                    && foldId.equals(escortDrive.foldOrder().fold())) {
                escortDrive.callOffFold();
            }
        }
        FleetCues.calledOff(level, coupler);
        finish();
        phase = Phase.MUSTER;
        harmony.clear();
        resolved = false;
    }

    private void finish() {
        foldId = null;
        sharedFold = false;
        joined.clear();
        releases.clear();
        shaken.clear();
        countdown = 0;
        phase = Phase.STANDBY;
        harmony.clear();
        surveys.clear();
        flagshipSurvey = Readiness.Survey.UNKNOWN;
        resolved = false;
    }

    /** Ends a muster that has not folded, and forgets what it had read. */
    void stand(ServerLevel level, StandDown why) {
        if (phase == Phase.STANDBY) {
            return;
        }
        if (phase == Phase.COMMITTED || phase == Phase.COUNTDOWN) {
            callOff(level);
            phase = Phase.STANDBY;
            return;
        }
        if (phase == Phase.FOLDING) {
            // The flagship's rift is already open. Nothing is gained by leaving escorts behind now; every one
            // still waiting is sent after it at once.
            for (Release next : List.copyOf(releases)) {
                releases.remove(next);
                releases.add(new Release(next.coupler(), next.centre(), 0, next.echo(), next.passage(),
                        next.berth()));
            }
            release(level);
            return;
        }
        phase = Phase.STANDBY;
        harmony.clear();
        surveys.clear();
        flagshipSurvey = Readiness.Survey.UNKNOWN;
        resolved = false;
        FleetCues.stoodDown(level, coupler);
    }
}
