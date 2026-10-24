package uk.co.iceconchy.aerowarptics.gate;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.advancement.AWCriteria;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.detector.SpatialWaveDetectorBlockEntity;
import uk.co.iceconchy.aerowarptics.network.AWNetwork;
import uk.co.iceconchy.aerowarptics.network.ClientboundGatePassagePacket;
import uk.co.iceconchy.aerowarptics.registry.AWSounds;
import uk.co.iceconchy.aerowarptics.trace.Trace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everybody who is part of the way through a Rift Gate.
 *
 * <p>A crossing used to be one tick: a gate saw a change of side and whoever had changed it was at the
 * far gate. It is a passage now. The traveller is still put at the far gate at once - which is what lets
 * the ground there load in behind the tunnel they are shown - but for {@code gatePassageTicks} they are
 * <em>in passage</em>: they cannot be hurt, nothing may take them for a target, nothing may knock them
 * about, other players do not see them, and their own client does not let them walk. Then they arrive,
 * with the momentum they went in with, and a moment's grace while the light clears from their eyes.
 *
 * <h2>Nothing is written on the traveller</h2>
 * The obvious way to make a player safe is to set them so: invulnerable, weightless, a spectator. Every
 * one of those is saved with the player. A server that stopped in the wrong three seconds - and a crash
 * does not ask - would start again with a player who cannot be hurt, or cannot fall, or cannot touch
 * anything, and nothing left in the game that knew to put it right. So this sets none of them. It keeps
 * a ledger, and the game <em>asks</em>: whether this entity may be hurt, whether that mob may turn on
 * it, whether a blow may move it. A passage the server forgets is simply over. The one thing set on a
 * player is the flag that hides them, which a player does not save.
 *
 * <p>Gravity is left alone as well. A player who hangs in the air is thrown off the server for flying
 * after four seconds, and holding one up is not worth a mixin: somebody who comes out of a gate with
 * nothing under them falls while the tunnel plays, cannot be hurt by it, and lands as the light clears.
 *
 * <h2>Three kinds of traveller</h2>
 * A player on foot, who is put back if anything shifts them sideways before they arrive. A player riding
 * something, where the mount is what moves and what is given its momentum back. And the crew of a
 * vehicle, where the hull is held still at the far gate - commanded to a stop every tick, as a warp
 * commands a hull's speed - and driven off as they arrive. A vehicle with nobody aboard is not held at
 * all, because nobody is aboard to be shown anything; nor is a mob or a dropped item.
 *
 * <p>It runs on the server's tick, not a gate's: a gate that hangs up, unloads or is mined out mid-passage
 * must not leave anybody held at the other end of it.
 */
@EventBusSubscriber(modid = AeroWarptics.MODID)
public final class GatePassages {

    /** How far sideways a traveller on foot may have been shifted before they are put back, in blocks. */
    private static final double STRAY = 1.0D;

    /** How far from either gate a mob that was after the traveller is told to forget them, in blocks. */
    private static final double FORGET_RANGE = 32.0D;

    private static final class Passage {
        private final ResourceKey<Level> dimension;
        private final PassageClock clock;
        /** The players in it. Shrinks if one of them leaves the server, dies or changes dimension. */
        private final List<UUID> travellers = new ArrayList<>();
        /** What is given its momentum back on arrival: the player on foot, or what the players are riding. */
        @Nullable
        private UUID body;
        /** The vehicle being held at the far gate, hull first and then its parts. */
        private final List<UUID> vessel = new ArrayList<>();
        /** The ship the far gate is aboard, whose frame {@link #hold} and {@link #motion} are in. */
        @Nullable
        private UUID farShip;
        /** Where a traveller on foot came out, in the far gate's own frame. */
        @Nullable
        private Vec3 hold;
        /** The momentum owed on arrival, in the far gate's own frame. */
        private Vec3 motion = Vec3.ZERO;
        private boolean arrived;

        private Passage(ResourceKey<Level> dimension, int ticks) {
            this.dimension = dimension;
            this.clock = new PassageClock(ticks, GatePassage.EMERGE_TICKS);
        }

        @Nullable
        private UUID hull() {
            return vessel.isEmpty() ? null : vessel.get(0);
        }
    }

    private static final List<Passage> PASSAGES = new ArrayList<>();

    /** Everything a passage protects, by entity: its travellers and whatever they are riding. */
    private static final Map<UUID, Passage> COVERED = new HashMap<>();

    /** Every sub-level being held at a far gate. */
    private static final Map<UUID, Passage> HELD_HULLS = new HashMap<>();

    private GatePassages() {
    }

    // ---------------------------------------------------------------- questions

    /** Whether crossings are passages at all. With {@code gatePassageTicks} at nought a gate is instant. */
    public static boolean enabled() {
        return AWConfig.GATE_PASSAGE_TICKS.get() > 0;
    }

    /**
     * The players a crossing would put into a passage: the one who walked through, or the ones riding
     * what did. Nobody for anything else - a mob, an item, a spectator drifting through the frame - and
     * nobody at all while passages are switched off.
     */
    public static List<ServerPlayer> travellers(Entity crossing) {
        if (!enabled()) {
            return List.of();
        }
        if (crossing instanceof ServerPlayer player) {
            return player.isSpectator() ? List.of() : List.of(player);
        }
        List<ServerPlayer> riders = new ArrayList<>();
        for (Entity rider : crossing.getIndirectPassengers()) {
            if (rider instanceof ServerPlayer player && !player.isSpectator()) {
                riders.add(player);
            }
        }
        return riders;
    }

    /**
     * Whether something is in a passage, or carrying somebody who is.
     *
     * <p>A gate asks this before it so much as notes which side of it the entity is on. Somebody held at
     * a far gate is standing in that gate's own catchment, and must be neither sent back through it nor
     * remembered by it.
     */
    public static boolean inPassage(Entity entity) {
        return !COVERED.isEmpty() && COVERED.containsKey(entity.getUUID());
    }

    /** Whether a hull is being held at a far gate, and so is not any gate's to move. */
    public static boolean holding(UUID hull) {
        return !HELD_HULLS.isEmpty() && HELD_HULLS.containsKey(hull);
    }

    // ------------------------------------------------------------------ beginning

    /**
     * Puts somebody on foot, or everybody riding one mount, into a passage.
     *
     * <p>Called <em>before</em> the gate moves them, so that their client is told to draw the passage
     * before it is told where it now is, and never shows them the far side first.
     *
     * @param crossing     what went through: the player, or what the players are riding
     * @param travellers   the players, from {@link #travellers}
     * @param from         where it went in, in the world
     * @param exit         where it comes out, in the world
     * @param farAirship   the ship the far gate is aboard, if it is aboard one
     * @param localArrival where it comes out, in the far gate's own frame
     * @param localMotion  the momentum it is owed, in the far gate's own frame
     */
    public static void begin(ServerLevel level, Entity crossing, List<ServerPlayer> travellers, Vec3 from,
                             Vec3 exit, @Nullable Airship farAirship, Vec3 localArrival, Vec3 localMotion) {
        Passage passage = new Passage(level.dimension(), AWConfig.GATE_PASSAGE_TICKS.get());
        passage.body = crossing.getUUID();
        passage.farShip = farAirship == null ? null : farAirship.uuid();
        // Only somebody on their own feet is put back where they came out. A rider is wherever the mount is.
        passage.hold = crossing instanceof ServerPlayer ? localArrival : null;
        passage.motion = localMotion;
        open(level, passage, travellers, exit);
        COVERED.put(crossing.getUUID(), passage);
        forgetTargets(level, from, passage);
        forgetTargets(level, exit, passage);
        Trace.log(Trace.GATE, "{} into a passage of {}t from {} to {}{}", names(travellers),
                AWConfig.GATE_PASSAGE_TICKS.get(), Trace.vec(from), Trace.vec(exit),
                crossing instanceof ServerPlayer ? "" : " riding " + crossing.getName().getString());
    }

    /**
     * Holds a vehicle at the far gate, and puts its crew into a passage.
     *
     * <p>Called once the hull has been moved. A hull is a physics body and there is no putting one down
     * and coming back for it: it is commanded to a stop every tick until its crew arrive.
     *
     * @param vessel      the hull and its parts, as {@link Airship#vessel} gave them
     * @param crew        who was aboard, read before the hull moved
     * @param localMotion the hull's momentum, in the far gate's own frame
     */
    public static void beginAboard(ServerLevel level, Airship vehicle, List<Airship> vessel, List<ServerPlayer> crew,
                                   @Nullable Airship farAirship, Vec3 localMotion) {
        Passage passage = new Passage(level.dimension(), AWConfig.GATE_PASSAGE_TICKS.get());
        passage.farShip = farAirship == null ? null : farAirship.uuid();
        passage.motion = localMotion;
        for (Airship member : vessel) {
            passage.vessel.add(member.uuid());
            HELD_HULLS.put(member.uuid(), passage);
        }
        Vec3 exit = toVec(vehicle.centre(new Vector3d()));
        open(level, passage, crew, exit);
        forgetTargets(level, exit, passage);
        Trace.log(Trace.GATE, "vehicle {} held at {} for a passage of {}t with {} aboard", vehicle.uuid(),
                Trace.vec(exit), AWConfig.GATE_PASSAGE_TICKS.get(), names(crew));
    }

    private static void open(ServerLevel level, Passage passage, List<ServerPlayer> travellers, Vec3 exit) {
        int ticks = AWConfig.GATE_PASSAGE_TICKS.get();
        for (ServerPlayer player : travellers) {
            // Somebody who is somehow still in one: that one is over for them.
            leave(player);
            passage.travellers.add(player.getUUID());
            COVERED.put(player.getUUID(), passage);
            player.setInvisible(true);
            // To everybody near the far gate, who must not draw them standing there, and to the traveller
            // wherever they are - which, before the gate has moved them, may be a long way from it.
            AWNetwork.sendToTracking(level, exit, List.of(player),
                    ClientboundGatePassagePacket.enter(player.getId(), ticks));
        }
        PASSAGES.add(passage);
    }

    /**
     * Makes whatever was chasing a traveller give up.
     *
     * <p>New targets are refused as they are chosen - see {@link #onChangeTarget} - but a mob that had
     * already chosen is not asked again, and would stand at the far gate swinging at somebody who cannot
     * be hurt until the moment they can.
     */
    private static void forgetTargets(ServerLevel level, Vec3 around, Passage passage) {
        AABB reach = AABB.ofSize(around, FORGET_RANGE * 2.0D, FORGET_RANGE * 2.0D, FORGET_RANGE * 2.0D);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, reach,
                candidate -> candidate.getTarget() != null && COVERED.get(candidate.getTarget().getUUID()) == passage)) {
            mob.setTarget(null);
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
    }

    // --------------------------------------------------------------------- tick

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PASSAGES.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        Iterator<Passage> passages = PASSAGES.iterator();
        while (passages.hasNext()) {
            Passage passage = passages.next();
            ServerLevel level = server.getLevel(passage.dimension);
            if (level == null) {
                forget(passage);
                passages.remove();
                continue;
            }
            boolean arriving = passage.clock.tick();
            if (passage.clock.stage() == PassageClock.Stage.HELD && !hold(level, passage)) {
                // Nobody left to hold it for, or nothing left to hold: there is no passage to wait out.
                passage.clock.cutShort();
                arriving = true;
            }
            if (arriving) {
                arrive(level, passage);
            }
            if (passage.clock.stage() == PassageClock.Stage.OVER) {
                forget(passage);
                passages.remove();
            }
        }
    }

    /**
     * Keeps a passage as it is for one more tick.
     *
     * @return whether there is still anything to keep
     */
    private static boolean hold(ServerLevel level, Passage passage) {
        Iterator<UUID> travellers = passage.travellers.iterator();
        while (travellers.hasNext()) {
            UUID id = travellers.next();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player == null || !player.isAlive() || player.serverLevel() != level) {
                // Gone some way the events below did not hear of. They are not held, wherever they are.
                travellers.remove();
                COVERED.remove(id);
                continue;
            }
            // Said again every tick: the game works the flag out afresh whenever a potion effect changes.
            player.setInvisible(true);
        }
        if (passage.travellers.isEmpty()) {
            return false;
        }
        UUID hull = passage.hull();
        if (hull != null) {
            Airship vehicle = ship(level, hull);
            if (vehicle == null) {
                Trace.log(Trace.GATE, "vehicle {} went away while it was held at a gate; its crew arrive now", hull);
                return false;
            }
            vehicle.driveVelocity(new Vector3d());
        }
        return true;
    }

    // ------------------------------------------------------------------ arriving

    /**
     * Lets a passage's travellers out of the far gate.
     *
     * <p>This is the arrival the rest of the mod hears about. A gate that puts somebody into a passage
     * says nothing to a Spatial Wave Detector and awards nothing: they have not come out yet.
     */
    private static void arrive(ServerLevel level, Passage passage) {
        passage.arrived = true;
        Airship farShip = passage.farShip == null ? null : ship(level, passage.farShip);
        // Turned into the world now, not when the passage began: a ship has had three seconds to come about.
        // And nothing at all if the ship it was measured against has gone, rather than a deck's "forward"
        // read as a compass bearing.
        Vec3 motion = passage.farShip == null ? passage.motion
                : farShip == null ? Vec3.ZERO : toVec(farShip.toWorldDirection(toVector3d(passage.motion)));

        List<ServerPlayer> travellers = new ArrayList<>();
        for (UUID id : passage.travellers) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null && player.isAlive() && player.serverLevel() == level) {
                travellers.add(player);
            }
        }

        UUID hull = passage.hull();
        Vec3 exit = null;
        if (hull != null) {
            Airship vehicle = ship(level, hull);
            if (vehicle != null) {
                vehicle.driveVelocity(toVector3d(motion));
                exit = toVec(vehicle.centre(new Vector3d()));
                SpatialWaveDetectorBlockEntity.announce(level, vehicle.assemblyBounds().toMojang());
            }
            AWCriteria.gateTravelled(travellers, true);
        } else if (passage.body != null) {
            Entity body = level.getEntity(passage.body);
            if (body != null && body.isAlive()) {
                if (passage.hold != null && body instanceof ServerPlayer walker
                        && (passage.farShip == null || farShip != null)) {
                    putBack(walker, passage.hold, farShip);
                }
                body.setDeltaMovement(motion);
                body.hurtMarked = true;
                body.fallDistance = 0.0F;
                exit = body.position();
                SpatialWaveDetectorBlockEntity.announce(level, body.getBoundingBox());
            }
            AWCriteria.gateTravelled(travellers, false);
        }

        for (ServerPlayer player : travellers) {
            // Whatever they fell while they could not see is not theirs to land with.
            player.fallDistance = 0.0F;
            unveil(level, player);
        }
        if (exit != null) {
            level.playSound(null, exit.x, exit.y, exit.z, AWSounds.GATE_ARRIVE.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
        }
        Trace.log(Trace.GATE, "{} out of a passage at {} with motion {}{}", names(travellers),
                exit == null ? "nowhere - what carried them is gone" : Trace.vec(exit), Trace.vec(motion),
                hull == null ? "" : " aboard vehicle " + hull);
    }

    /**
     * Puts a traveller on foot back where they came out, if something has shifted them.
     *
     * <p>Only sideways counts. Down is gravity, which is left alone, and a traveller who has dropped to
     * the floor under the gate has done nothing that needs undoing. And sideways is measured in the far
     * gate's own frame: aboard a ship that has sailed on, where they came out has sailed on with it.
     */
    private static void putBack(ServerPlayer walker, Vec3 hold, @Nullable Airship farShip) {
        Vec3 now = farShip == null ? walker.position() : farShip.toShip(walker.position());
        double across = now.x - hold.x;
        double along = now.z - hold.z;
        if (across * across + along * along <= STRAY * STRAY) {
            return;
        }
        Vec3 back = farShip == null ? hold : farShip.toWorld(hold);
        Trace.log(Trace.GATE, "{} was shifted {} blocks in a passage; put back at {}", Trace.who(walker),
                Trace.num(Math.sqrt(across * across + along * along)), Trace.vec(back));
        walker.connection.teleport(back.x, back.y, back.z, walker.getYRot(), walker.getXRot());
    }

    /** Lets a traveller be seen again, and tells their client, and everybody's near them, that they are through. */
    private static void unveil(ServerLevel level, ServerPlayer player) {
        player.setInvisible(player.hasEffect(MobEffects.INVISIBILITY));
        AWNetwork.sendToTracking(level, player.position(), List.of(player),
                ClientboundGatePassagePacket.arrive(player.getId()));
    }

    // -------------------------------------------------------------------- ending

    /**
     * Takes one player out of whatever passage they are in, where they stand.
     *
     * <p>For the ways out of a passage that are not arriving: leaving the server, dying, leaving the
     * dimension. They are simply not in it any more. Whatever they were riding, or aboard, arrives
     * without them on the next tick if nobody else is left in it.
     */
    private static void leave(ServerPlayer player) {
        Passage passage = COVERED.remove(player.getUUID());
        if (passage == null) {
            return;
        }
        passage.travellers.remove(player.getUUID());
        if (!passage.arrived) {
            Trace.log(Trace.GATE, "{} left a passage before arriving", Trace.who(player));
            if (player.level() instanceof ServerLevel level) {
                unveil(level, player);
            }
        }
    }

    /** Drops a passage from the ledgers. Nothing else about its travellers needs putting back. */
    private static void forget(Passage passage) {
        COVERED.values().removeIf(covering -> covering == passage);
        HELD_HULLS.values().removeIf(holding -> holding == passage);
    }

    // --------------------------------------------------------------- protection

    /** Nothing hurts a traveller - bar what nothing is proof against: the void, and an operator's {@code /kill}. */
    @SubscribeEvent
    public static void onInvulnerabilityCheck(EntityInvulnerabilityCheckEvent event) {
        Entity entity = event.getEntity();
        // The server's entities only. The same question is asked of a client's copies, on another thread.
        if (entity.level().isClientSide || event.isInvulnerable() || !inPassage(entity)) {
            return;
        }
        if (!event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setInvulnerable(true);
        }
    }

    /** Nothing may take a traveller for its target. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target != null && !target.level().isClientSide && inPassage(target)) {
            event.setCanceled(true);
        }
    }

    /** Nothing knocks a traveller about. */
    @SubscribeEvent
    public static void onKnockBack(LivingKnockBackEvent event) {
        if (!event.getEntity().level().isClientSide && inPassage(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            leave(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            leave(player);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            leave(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PASSAGES.clear();
        COVERED.clear();
        HELD_HULLS.clear();
    }

    // ------------------------------------------------------------------- helpers

    @Nullable
    private static Airship ship(ServerLevel level, UUID id) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || !(container.getSubLevel(id) instanceof ServerSubLevel found) || found.isRemoved()) {
            return null;
        }
        Airship airship = Airship.of(found);
        return airship.isActive() ? airship : null;
    }

    private static String names(List<ServerPlayer> players) {
        if (!Trace.enabled()) {
            return "";
        }
        Set<String> names = new HashSet<>();
        for (ServerPlayer player : players) {
            names.add(player.getGameProfile().getName());
        }
        return names.isEmpty() ? "nobody" : String.join(", ", names);
    }

    private static Vec3 toVec(org.joml.Vector3dc vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }

    private static Vector3d toVector3d(Vec3 vec) {
        return new Vector3d(vec.x, vec.y, vec.z);
    }
}
