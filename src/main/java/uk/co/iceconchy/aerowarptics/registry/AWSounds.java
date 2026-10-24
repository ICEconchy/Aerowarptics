package uk.co.iceconchy.aerowarptics.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import uk.co.iceconchy.aerowarptics.AeroWarptics;

/**
 * Sound events for every stage of the warp sequence.
 *
 * <p>All of them are positional (played through {@code level.playSound} at the drive's position), so
 * the sequence is audible to everyone nearby rather than only the pilot. The definitions in
 * {@code sounds.json} are built out of existing game audio rather than shipping new files.
 */
public final class AWSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, AeroWarptics.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> DRIVE_CHARGING = register("drive_charging");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRIVE_CHARGED = register("drive_charged");
    public static final DeferredHolder<SoundEvent, SoundEvent> STABILIZING = register("stabilizing");
    public static final DeferredHolder<SoundEvent, SoundEvent> DESTINATION_LOCK = register("destination_lock");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_OPEN = register("rift_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARP_TRAVEL = register("warp_travel");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARP_EXIT = register("warp_exit");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARP_FAILED = register("warp_failed");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRIVE_COOLDOWN = register("drive_cooldown");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANCHOR_CONFIGURED = register("anchor_configured");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARP_SCATTER = register("warp_scatter");

    /**
     * A drive winding up to tear its rift: a loop, and the one drive sound the client plays for itself
     * rather than being sent, because its pitch climbs with the spin and only a sound still playing can
     * be turned. See {@code client.fx.DriveSpinUpSound}.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> DRIVE_SPIN_UP = register("drive_spin_up");

    /**
     * A passage through a Rift Gate. The rush of it is the traveller's alone - their own client plays it,
     * since by then they are at the far gate and nowhere near where they went in - and the arrival is
     * played at the far gate for everybody there.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> GATE_PASSAGE = register("gate_passage");
    public static final DeferredHolder<SoundEvent, SoundEvent> GATE_ARRIVE = register("gate_arrive");

    /** A Cooling Vent blowing the heat of a warp out, and the ice against a drive taking its share. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VENT_BLAST = register("vent_blast");
    public static final DeferredHolder<SoundEvent, SoundEvent> COOLANT_HISS = register("coolant_hiss");

    // Flotillas. Every one of these is heard: a fleet should be heard becoming ready before anyone
    // reads it on a screen, so each beat of a muster and a fold has its own sound.
    public static final DeferredHolder<SoundEvent, SoundEvent> FLOTILLA_HORN = register("flotilla_horn");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLOTILLA_TETHER = register("flotilla_tether");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLOTILLA_HARMONISED = register("flotilla_harmonised");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLOTILLA_CHORD = register("flotilla_chord");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLOTILLA_COUNTDOWN = register("flotilla_countdown");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLOTILLA_BELL = register("flotilla_bell");
    public static final DeferredHolder<SoundEvent, SoundEvent> WAKE_ECHO = register("wake_echo");

    /**
     * Each drive's hum while a flotilla musters, one loop per tier so a fleet's ships sound like
     * different machines until they harmonise. The client slides each escort's pitch towards the
     * flagship's as it does.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> HUM_MK_I = register("drive_hum_mk_i");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUM_MK_II = register("drive_hum_mk_ii");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUM_MK_III = register("drive_hum_mk_iii");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUM_SINGULARITY = register("drive_hum_singularity");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUM_CREATIVE = register("drive_hum_creative");

    /**
     * The Collapse Cradle's ritual, in the order it is heard. The spin-up, the tear, the drone and howl (loops
     * that rise with it), the heart beating under it, the lightning reaching for the stabilisers, the in-breath
     * of the hush, the boom - heard a long way off - and how it ended.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> COLLAPSE_SPIN_UP = register("collapse_spin_up");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLLAPSE_TEAR = register("collapse_tear");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLLAPSE_DRONE = register("collapse_drone");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLLAPSE_HOWL = register("collapse_howl");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLLAPSE_HEARTBEAT = register("collapse_heartbeat");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLLAPSE_ARC = register("collapse_arc");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLLAPSE_FOLD = register("collapse_fold");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLLAPSE_INHALE = register("collapse_inhale");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLLAPSE_BOOM = register("collapse_boom");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLLAPSE_SETTLE = register("collapse_settle");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLLAPSE_FLING = register("collapse_fling");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLLAPSE_SPIT = register("collapse_spit");

    /** A Rift Ward's one toll as it catches a rift, heard across its field. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WARD_TOLL = register("ward_toll");
    /** The caught rift breaking open at the field's rim. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WARD_BREAK = register("ward_break");
    /** A ward's field dropping: its charge gone, or a rift it could not pay to turn. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WARD_GIVE_WAY = register("ward_give_way");
    /** A ward's field coming back. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WARD_RETURN = register("ward_return");
    /** The low drone of a ward whose field stands - one of the ways it says it is on. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WARD_HUM = register("ward_hum");
    /** The corridor wrenched sideways, for those aboard a ship being turned aside. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WARD_WRENCH = register("ward_wrench");

    /**
     * A Rift Sunderer's tear, in the order it is heard: the ring reaching for a far side, the groan as it strains
     * (a loop that climbs), each block cracking free, the creak and the breath of whatever reaches through, the
     * implosion, and the two ways it can fail. Played by each client on the scene's own clock.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> SUNDERER_REACH = register("sunderer_reach");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUNDERER_STRAIN = register("sunderer_strain");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUNDERER_CRACK = register("sunderer_crack");
    /** Two blocks of an unravelling frame scraping past each other in orbit. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SUNDERER_GRIND = register("sunderer_grind");
    /** A block of the frame falling into the seed. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SUNDERER_SWALLOW = register("sunderer_swallow");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUNDERER_HAND_CREAK = register("sunderer_hand_creak");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUNDERER_HAND_BREATH = register("sunderer_hand_breath");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUNDERER_BREACH = register("sunderer_breach");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUNDERER_MISFIRE = register("sunderer_misfire");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUNDERER_RUPTURE = register("sunderer_rupture");

    /** The hum loop for a tier, by its index. */
    public static SoundEvent hum(int tierIndex) {
        return switch (tierIndex) {
            case 1 -> HUM_MK_II.get();
            case 2 -> HUM_MK_III.get();
            case 3 -> HUM_SINGULARITY.get();
            case 4 -> HUM_CREATIVE.get();
            default -> HUM_MK_I.get();
        };
    }

    private AWSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(AeroWarptics.id(name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }
}
