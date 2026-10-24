package uk.co.iceconchy.aerowarptics.flotilla;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.airship.Airship;
import uk.co.iceconchy.aerowarptics.network.AWNetwork;
import uk.co.iceconchy.aerowarptics.network.ClientboundFlotillaCuePacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundFlotillaCuePacket.Cue;
import uk.co.iceconchy.aerowarptics.network.ClientboundMusterPacket;
import uk.co.iceconchy.aerowarptics.registry.AWSounds;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Every beat of a muster and a fold, told to the people who should see and hear it.
 *
 * <p>Sounds that belong to a ship - the horn, the bell - are played at that ship's coupler, in the world,
 * so anyone near any ship of the fleet hears them from where the ship is. Cues that belong to a crew - a
 * title, the chord - are sent to the crews and dressed by the client, which is what lets a player who has
 * turned the titles off still hear the fleet become ready.
 */
public final class FleetCues {

    /** How far off the flotilla's own crews a player can be and still be shown its muster. */
    private static final double WATCH_RANGE = 128.0D;

    private FleetCues() {
    }

    // ------------------------------------------------------------------ muster

    static void soundStations(ServerLevel level, RiftCouplerBlockEntity flagship) {
        for (RiftCouplerBlockEntity ship : ships(level, flagship)) {
            play(level, ship, AWSounds.FLOTILLA_HORN.get(), 3.0F, 0.75F);
            play(level, ship, AWSounds.FLOTILLA_TETHER.get(), 1.0F, 1.0F);
        }
        List<ServerPlayer> escorts = escortCrews(level, flagship);
        send(escorts, new ClientboundFlotillaCuePacket(Cue.SOUND_STATIONS, 0, true));
        send(crew(level, flagship), new ClientboundFlotillaCuePacket(Cue.TETHERS_OUT, 0, true));
        send(escorts, new ClientboundFlotillaCuePacket(Cue.TETHERS_OUT, 0, true));
    }

    /** One escort's drive has matched the flagship's: its tether steadies. */
    static void harmonised(ServerLevel level, RiftCouplerBlockEntity flagship, UUID escort) {
        RiftCouplerBlockEntity ship = RiftCouplerBlockEntity.find(level,
                FlotillaRegistry.get(level).coupler(escort));
        if (ship != null) {
            play(level, ship, AWSounds.FLOTILLA_HARMONISED.get(), 0.8F, 1.0F);
        }
    }

    /** Every escort has matched: the hums resolve into one chord. */
    static void resolved(ServerLevel level, RiftCouplerBlockEntity flagship) {
        send(everyone(level, flagship), new ClientboundFlotillaCuePacket(Cue.RESOLVED, 0, true));
    }

    static void stoodDown(ServerLevel level, RiftCouplerBlockEntity flagship) {
        send(everyone(level, flagship), new ClientboundFlotillaCuePacket(Cue.STOOD_DOWN, 0, true));
    }

    /**
     * The muster as the crews' clients draw it: slot markers, tethers, hums and the station HUD.
     *
     * <p>Sent to every crew in the flotilla and to anyone standing near the flagship, since a fleet
     * mustering in the sky is worth seeing from the ground.
     */
    static void muster(ServerLevel level, RiftCouplerBlockEntity flagship, ClientboundMusterPacket packet) {
        Set<ServerPlayer> watchers = new LinkedHashSet<>(everyone(level, flagship));
        Airship ship = flagship.airship();
        if (ship != null) {
            org.joml.Vector3d centre = ship.centre(new org.joml.Vector3d());
            for (ServerPlayer player : level.players()) {
                if (player.position().distanceToSqr(centre.x, centre.y, centre.z) <= WATCH_RANGE * WATCH_RANGE) {
                    watchers.add(player);
                }
            }
        }
        send(new ArrayList<>(watchers), packet);
    }

    // -------------------------------------------------------------------- fold

    /** The countdown has begun: dim the sky, pull the streaks, build the shake. */
    static void countdown(ServerLevel level, RiftCouplerBlockEntity flagship, int ticks) {
        send(everyone(level, flagship), new ClientboundFlotillaCuePacket(Cue.COUNTDOWN, 0, true, ticks));
        for (RiftCouplerBlockEntity ship : ships(level, flagship)) {
            play(level, ship, AWSounds.FLOTILLA_COUNTDOWN.get(), 1.5F, 1.0F);
        }
    }

    /** One escort's crew is told how it stands: "SLOT 2 - IN STATION", or adrift in red. */
    static void slotState(ServerLevel level, RiftCouplerBlockEntity escort, int slot, boolean inStation) {
        Airship ship = escort.airship();
        if (ship != null) {
            send(ship.crew(), new ClientboundFlotillaCuePacket(Cue.SLOT_STATE, slot, inStation));
        }
    }

    /** A fold was called off before anything went: the tethers drop. */
    static void calledOff(ServerLevel level, RiftCouplerBlockEntity flagship) {
        send(everyone(level, flagship), new ClientboundFlotillaCuePacket(Cue.CALLED_OFF, 0, false));
    }

    /** Everyone but the stragglers is out the other side: the bell, and FLOTILLA ASSEMBLED. */
    static void assembled(ServerLevel level, RiftCouplerBlockEntity flagship, boolean stragglersDue) {
        for (RiftCouplerBlockEntity ship : ships(level, flagship)) {
            play(level, ship, AWSounds.FLOTILLA_BELL.get(), 2.0F, 1.0F);
        }
        send(everyone(level, flagship), new ClientboundFlotillaCuePacket(Cue.ASSEMBLED, 0, !stragglersDue));
    }

    /** A Wake Echo is about to tear: everyone hears somebody is coming in late. */
    static void echoInbound(ServerLevel level, RiftCouplerBlockEntity flagship) {
        send(everyone(level, flagship), new ClientboundFlotillaCuePacket(Cue.ECHO_INBOUND, 0, false));
        for (RiftCouplerBlockEntity ship : ships(level, flagship)) {
            play(level, ship, AWSounds.WAKE_ECHO.get(), 2.0F, 0.9F);
        }
    }

    /** A straggler's own crew: the flash and SHAKEN LOOSE. */
    static void shakenLoose(ServerLevel level, @Nullable Airship straggler) {
        if (straggler != null) {
            send(straggler.crew(), new ClientboundFlotillaCuePacket(Cue.SHAKEN_LOOSE, 0, false));
        }
    }

    /** Escorts that were not ready when a fold was forced. */
    static void leftBehind(ServerLevel level, @Nullable Airship ship) {
        if (ship != null) {
            send(ship.crew(), new ClientboundFlotillaCuePacket(Cue.LEFT_BEHIND, 0, false));
        }
    }

    // ------------------------------------------------------------------ people

    /** The flagship's coupler and every escort's that is loaded here. */
    static List<RiftCouplerBlockEntity> ships(ServerLevel level, RiftCouplerBlockEntity flagship) {
        List<RiftCouplerBlockEntity> out = new ArrayList<>();
        out.add(flagship);
        FlotillaRegistry registry = FlotillaRegistry.get(level);
        Flotilla flotilla = flagship.couplerId() == null ? null : registry.led(flagship.couplerId());
        if (flotilla != null) {
            for (Flotilla.Member member : flotilla.escorts()) {
                RiftCouplerBlockEntity escort = RiftCouplerBlockEntity.find(level, registry.coupler(member.coupler()));
                if (escort != null) {
                    out.add(escort);
                }
            }
        }
        return out;
    }

    static List<ServerPlayer> crew(ServerLevel level, RiftCouplerBlockEntity coupler) {
        Airship ship = coupler.airship();
        return ship == null ? List.of() : ship.crew();
    }

    static List<ServerPlayer> escortCrews(ServerLevel level, RiftCouplerBlockEntity flagship) {
        List<ServerPlayer> out = new ArrayList<>();
        List<RiftCouplerBlockEntity> ships = ships(level, flagship);
        for (int i = 1; i < ships.size(); i++) {
            out.addAll(crew(level, ships.get(i)));
        }
        return out;
    }

    /** Every crew in the flotilla, flagship first, nobody twice. */
    static List<ServerPlayer> everyone(ServerLevel level, RiftCouplerBlockEntity flagship) {
        Set<ServerPlayer> out = new LinkedHashSet<>();
        for (RiftCouplerBlockEntity ship : ships(level, flagship)) {
            out.addAll(crew(level, ship));
        }
        return new ArrayList<>(out);
    }

    private static void play(ServerLevel level, RiftCouplerBlockEntity ship, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, ship.getBlockPos(), sound, SoundSource.BLOCKS, volume, pitch);
    }

    private static void send(List<ServerPlayer> players, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        for (ServerPlayer player : players) {
            AWNetwork.sendTo(player, payload);
        }
    }

    /** Where a coupler is in the world, for anything that needs a point rather than a block. */
    static Vec3 worldOf(RiftCouplerBlockEntity coupler) {
        Airship ship = coupler.airship();
        Vec3 at = Vec3.atCenterOf(coupler.getBlockPos());
        return ship == null ? at : ship.toWorld(at);
    }
}
