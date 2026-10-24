package uk.co.iceconchy.aerowarptics.ward;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.network.AWNetwork;
import uk.co.iceconchy.aerowarptics.network.ClientboundWardCuePacket;
import uk.co.iceconchy.aerowarptics.trace.Trace;
import uk.co.iceconchy.aerowarptics.warpnet.Membership;
import uk.co.iceconchy.aerowarptics.warpnet.WarpNetworkRegistry;
import uk.co.iceconchy.aerowarptics.weather.RiftStorm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Where Rift Wards meet everything that opens a rift: the one place a destination is asked whether a field
 * stands over it, and the one place a field is paid for.
 *
 * <p>Anything that brings something out of a rift at a place it chose - a drive's arrival, a plotter's far end -
 * asks {@link #turnAside} for the place it will really come out, plans on that, and only then {@link #commit}s:
 * a deflection is planned before anything moves and paid for only once it is planned. A new kind of warp that
 * plans its own arrival and does not ask here lands strangers in the middle of a base, and nothing fails.
 *
 * <p>A ward is asked whether or not its block is loaded. A loaded one answers for itself; an unloaded one is
 * read from {@link WardRegistry}, with its upkeep since it was last counted taken off ({@link WardCharge#after}).
 * Nothing here loads a chunk.
 */
public final class Wards {

    private Wards() {
    }

    /**
     * What the wards made of a rift.
     *
     * @param aimed    where it was aimed
     * @param target   where it will come out: {@code aimed}, unless something turned it
     * @param by       the wards that turned it, each of which is owed its price
     * @param fields   every field it must stay out of, as it met them - what a planner checks its own answer
     *                 against, and walks the rim of
     * @param gaveWay  a ward whose field it was aimed into that could not pay, and so let it through
     * @param settled  whether {@code target} is clear of every field; false only in a thicket of overlapping ones
     */
    public record Turn(BlockPos aimed, BlockPos target, List<RiftWard> by, List<WardField.Circle> fields,
                       @Nullable RiftWard gaveWay, boolean settled) {

        public static Turn untouched(BlockPos target) {
            return new Turn(target, target, List.of(), List.of(), null, true);
        }

        public boolean turned() {
            return !by.isEmpty();
        }

        /** Whether a place is inside a field this rift is being kept out of. */
        public boolean holds(double x, double z) {
            return WardField.inside(fields, x, z);
        }

        /**
         * Other places to come out, round the rim of the ward that turned it first, for a planner whose first
         * choice has nowhere to land. None of them is inside another field.
         */
        public List<BlockPos> alternatives() {
            if (by.isEmpty() || fields.isEmpty()) {
                return List.of();
            }
            RiftWard first = by.get(0);
            WardField.Circle rim = null;
            for (WardField.Circle field : fields) {
                if (field.x() == first.centreX() && field.z() == first.centreZ()) {
                    rim = field;
                }
            }
            if (rim == null) {
                return List.of();
            }
            List<BlockPos> out = new ArrayList<>();
            WardField.Point from = new WardField.Point(target.getX() + 0.5D, target.getZ() + 0.5D);
            for (WardField.Point point : WardField.rimWalk(rim, from, WardRules.RIM_STEP_DEGREES, WardRules.RIM_STEPS)) {
                if (!WardField.inside(fields, point.x(), point.z())) {
                    out.add(BlockPos.containing(point.x(), target.getY(), point.z()));
                }
            }
            return out;
        }

        /** This turn, coming out somewhere else on the rim. */
        public Turn at(BlockPos elsewhere) {
            return new Turn(aimed, elsewhere, by, fields, gaveWay, settled);
        }
    }

    /** A ward as it stands this tick, whichever of its block and its record knows. */
    private record Live(RiftWard ward, double charge, boolean standing, @Nullable RiftWardBlockEntity block) {
    }

    // ------------------------------------------------------------------ asking

    /**
     * Where a rift aimed at {@code target} will come out. Changes nothing: see {@link #commit}.
     *
     * @param from        where the traveller is in this level, or {@code null} where it is in another
     * @param who         whoever opened the rift, or {@code null} for a rift nobody can be named for
     * @param penetration how far into a field this rift pushes before it is turned, 0..1
     * @param clearance   how far outside a field's rim the place it comes out must be
     * @param cost        what turning it aside costs a ward
     */
    public static Turn turnAside(ServerLevel destination, BlockPos target, @Nullable Vec3 from, @Nullable UUID who,
                                 double penetration, double clearance, long cost) {
        if (!AWConfig.WARD_ENABLED.get() || penetration >= 1.0D) {
            return Turn.untouched(target);
        }
        MinecraftServer server = destination.getServer();
        List<RiftWard> wards = WardRegistry.get(server).in(destination.dimension());
        if (wards.isEmpty()) {
            return Turn.untouched(target);
        }
        Membership networks = WarpNetworkRegistry.get(server);
        boolean storm = RiftStorm.ragingOver(destination);
        double tx = target.getX() + 0.5D;
        double tz = target.getZ() + 0.5D;

        List<RiftWard> eligible = new ArrayList<>();
        List<WardField.Circle> fields = new ArrayList<>();
        RiftWard gaveWay = null;
        for (RiftWard ward : wards) {
            Live live = live(server, ward);
            if (!live.standing()) {
                continue;
            }
            double standing = WardField.standing(ward.radius(), storm, AWConfig.WARD_STORM_THINNING.get());
            WardField.Circle met = new WardField.Circle(ward.centreX(), ward.centreZ(),
                    WardField.met(standing, penetration));
            if (ward.passes(who, networks)) {
                if (met.contains(tx, tz) && Trace.enabled()) {
                    Trace.log(Trace.WARD, "ward {} at {} lets {} through to {}: mode={} owner={} selfTest={} listed={}",
                            ward.id(), Trace.pos(ward.pos()), Trace.who(who), Trace.pos(target), ward.mode(),
                            ward.isOwnedBy(who), ward.selfTest(), ward.lists(who, networks));
                }
                continue;
            }
            if (!WardCharge.affords(live.charge(), cost)) {
                if (gaveWay == null && met.contains(tx, tz)) {
                    gaveWay = ward;
                    Trace.log(Trace.WARD, "ward {} at {} cannot turn {} from {}: {} mB held, {} needed - it gives way",
                            ward.id(), Trace.pos(ward.pos()), Trace.who(who), Trace.pos(target),
                            Trace.num(live.charge()), cost);
                }
                continue;
            }
            eligible.add(ward);
            fields.add(met);
        }
        if (fields.isEmpty()) {
            return new Turn(target, target, List.of(), List.of(), gaveWay, true);
        }
        WardField.Point start = from == null ? null : new WardField.Point(from.x, from.z);
        WardField.Turned turned = WardField.settle(fields, start, new WardField.Point(tx, tz), clearance);
        if (!turned.moved()) {
            return new Turn(target, target, List.of(), fields, gaveWay, true);
        }
        List<RiftWard> by = new ArrayList<>();
        for (int index : turned.turnedBy()) {
            by.add(eligible.get(index));
        }
        BlockPos out = BlockPos.containing(turned.at().x(), target.getY(), turned.at().z());
        if (Trace.enabled()) {
            Trace.log(Trace.WARD, "{}'s rift to {} turned to {} by {} ward(s), first {} at {} (radius {} set, storm {},"
                            + " push {}), {} mB each, clearance {}{}", Trace.who(who), Trace.pos(target), Trace.pos(out),
                    by.size(), by.get(0).id(), Trace.pos(by.get(0).pos()), by.get(0).radius(), storm,
                    Trace.num(penetration), cost, Trace.blocks(clearance),
                    turned.settled() ? "" : " - NOT clear of every field");
        }
        return new Turn(target, out, List.copyOf(by), List.copyOf(fields), gaveWay, turned.settled());
    }

    /**
     * Whether a place is inside a field that would scramble a stranger's reading of it. Costs the wards nothing by
     * itself - see {@link #scanned}.
     */
    public static List<WardField.Circle> scrambling(ServerLevel level, @Nullable UUID who) {
        if (!AWConfig.WARD_ENABLED.get() || !AWConfig.WARD_SCRAMBLES_SCANS.get()) {
            return List.of();
        }
        MinecraftServer server = level.getServer();
        List<RiftWard> wards = WardRegistry.get(server).in(level.dimension());
        if (wards.isEmpty()) {
            return List.of();
        }
        Membership networks = WarpNetworkRegistry.get(server);
        boolean storm = RiftStorm.ragingOver(level);
        List<WardField.Circle> out = new ArrayList<>();
        for (RiftWard ward : wards) {
            Live live = live(server, ward);
            if (!live.standing() || !WardCharge.affords(live.charge(), AWConfig.WARD_SCAN_COST.get())
                    || ward.passes(who, networks)) {
                continue;
            }
            out.add(new WardField.Circle(ward.centreX(), ward.centreZ(),
                    WardField.standing(ward.radius(), storm, AWConfig.WARD_STORM_THINNING.get())));
        }
        return out;
    }

    /** A stranger's scan read ground inside these fields: each ward whose field it was pays for the scrambling. */
    public static void scanned(ServerLevel level, List<WardField.Circle> touched) {
        if (touched.isEmpty()) {
            return;
        }
        MinecraftServer server = level.getServer();
        for (RiftWard ward : WardRegistry.get(server).in(level.dimension())) {
            for (WardField.Circle field : touched) {
                if (field.x() == ward.centreX() && field.z() == ward.centreZ()) {
                    spend(server, ward, AWConfig.WARD_SCAN_COST.get(), false);
                }
            }
        }
    }

    // ------------------------------------------------------------------ paying

    /**
     * Pays for a turn that has been planned: each ward that turned the rift spends its price, and one that gave
     * way drops its field. Call it once the flight is certain, and not before.
     */
    public static void commit(ServerLevel destination, Turn turn, long cost) {
        MinecraftServer server = destination.getServer();
        for (RiftWard ward : turn.by()) {
            spend(server, ward, cost, true);
        }
        if (turn.gaveWay() != null) {
            drop(server, turn.gaveWay());
        }
    }

    private static void spend(MinecraftServer server, RiftWard ward, long cost, boolean deflection) {
        Live live = live(server, ward);
        if (live.block() != null) {
            live.block().spend(cost, deflection);
            return;
        }
        double left = Math.max(0.0D, live.charge() - cost);
        WardRegistry.get(server).register(ward.counted(left, now(server), left > 0.0D && live.standing()));
    }

    private static void drop(MinecraftServer server, RiftWard ward) {
        Live live = live(server, ward);
        if (live.block() != null) {
            live.block().giveWay();
            return;
        }
        WardRegistry.get(server).register(ward.counted(live.charge(), now(server), false));
    }

    /**
     * A network has been disbanded: every ward that named it stops naming it, in the record and - where the block is
     * loaded - in the block, which would otherwise write it straight back.
     *
     * <p>Only a disbanding does this. A ward may name a network its owner is not in - that is how a rival's is shut
     * out - so a member leaving a network changes nothing about the wards that name it.
     */
    public static List<RiftWard> forget(MinecraftServer server, UUID network) {
        List<RiftWard> off = WardRegistry.get(server).forget(network);
        for (RiftWard ward : off) {
            RiftWardBlockEntity block = loaded(server, ward);
            if (block != null) {
                block.forgetNetwork(network);
            }
        }
        return off;
    }

    // ------------------------------------------------------------------ telling

    /**
     * Shows a turn to everyone who should see it: those near the field, and the crew wherever they are.
     *
     * @param went  where the hull will really come out
     * @param total ticks until it does
     */
    public static void announce(ServerLevel destination, Turn turn, Vec3 went, int total, int colour,
                                Collection<ServerPlayer> crew, boolean local) {
        RiftWard ward = turn.turned() ? turn.by().get(0) : turn.gaveWay();
        if (ward == null) {
            return;
        }
        Vec3 aimed = new Vec3(turn.aimed().getX() + 0.5D, went.y, turn.aimed().getZ() + 0.5D);
        ClientboundWardCuePacket.Kind kind = !turn.turned() ? ClientboundWardCuePacket.Kind.GAVE_WAY
                : local ? ClientboundWardCuePacket.Kind.PLOTTED : ClientboundWardCuePacket.Kind.DEFLECTED;
        ClientboundWardCuePacket cue = new ClientboundWardCuePacket(ward.pos(), aimed, turn.turned() ? went : aimed,
                WardChoreography.runUp(total), kind, colour, AWConfig.WARD_TOLL_RANGE.get(), false);
        double range = AWConfig.WARD_SPECTACLE_RANGE.get();
        Vec3 middle = Vec3.atCenterOf(ward.pos());
        for (ServerPlayer player : destination.players()) {
            double dx = player.getX() - middle.x;
            double dz = player.getZ() - middle.z;
            double reach = range + ward.radius();
            if (dx * dx + dz * dz <= reach * reach && !crew.contains(player)) {
                AWNetwork.sendTo(player, cue);
            }
        }
        for (ServerPlayer player : crew) {
            AWNetwork.sendTo(player, cue.forCrew());
        }
        RiftWardBlockEntity block = loaded(destination.getServer(), ward);
        if (block != null) {
            block.witnessed(turn.turned());
        }
    }

    // ------------------------------------------------------------------ helpers

    private static Live live(MinecraftServer server, RiftWard ward) {
        RiftWardBlockEntity block = loaded(server, ward);
        if (block != null) {
            return new Live(ward, block.charge(), block.standing(), block);
        }
        ServerLevel level = server.getLevel(ward.dimension());
        if (level == null) {
            // A dimension this server does not have just now. Its wards hold nothing, and are not forgotten for it.
            return new Live(ward, ward.charge(), false, null);
        }
        if (level.isLoaded(ward.pos())) {
            // Its ground is loaded and it is not there: a record left behind by a ward that went without saying so.
            Trace.log(Trace.WARD, "ward {} at {} is not where its record says: forgetting it", ward.id(),
                    Trace.pos(ward.pos()));
            WardRegistry.get(server).remove(ward.id());
            return new Live(ward, 0.0D, false, null);
        }
        if (!ward.enabled()) {
            return new Live(ward, ward.charge(), false, null);
        }
        double charge = WardCharge.after(ward.charge(), now(server) - ward.countedAt(),
                WardCharge.upkeepPerSecond(ward.radius(), AWConfig.WARD_UPKEEP.get()));
        boolean standing = WardCharge.standing(ward.standing(), true, charge, AWConfig.WARD_CAPACITY.get(),
                AWConfig.WARD_RECOVER_FRACTION.get());
        return new Live(ward, charge, standing, null);
    }

    /** A ward's block, if it is in the world and loaded. Never loads it. */
    @Nullable
    static RiftWardBlockEntity loaded(MinecraftServer server, RiftWard ward) {
        ServerLevel level = server.getLevel(ward.dimension());
        if (level == null || !level.isLoaded(ward.pos())) {
            return null;
        }
        return level.getBlockEntity(ward.pos()) instanceof RiftWardBlockEntity block
                && ward.id().equals(block.wardId()) ? block : null;
    }

    /** The clock upkeep is counted on: the overworld's, so a dimension with nobody in it still runs down. */
    static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }
}
