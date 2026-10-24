package uk.co.iceconchy.aerowarptics.client.fx;

import java.util.Set;

/**
 * Which of the mod's sounds are the sounds of a warp, for the player who has switched those off.
 *
 * <p>Most of a warp is heard through the server: a drive plays each stage at its own position, so that
 * everybody nearby hears it and not only the pilot. That is the right way round for a machine, and it
 * means a client-side setting cannot simply decline to play them - they arrive as ordinary sound packets
 * like any other. So the switch is thrown where every sound passes on its way to the speakers, by name;
 * see {@link WarpSoundFilter}.
 *
 * <p>The names rather than the registered events, so that this stays free of Minecraft and a test can
 * hold it to {@code sounds.json}. By name is also why a brazier or a probe that borrows one of these goes
 * quiet with the drives: from here a sound is only what it is called.
 */
public final class WarpSoundSwitch {

    /** The mod's namespace, as a sound's location carries it. */
    private static final String NAMESPACE = "aerowarptics";

    /**
     * Every stage of the warp sequence, the drive's spin-up loop with them, and a Rift Gate's passage. Not
     * the flotilla's sounds, which are how a fleet is told it is ready, nor the Collapse Cradle's, which are
     * not a warp's at all. Of a Rift Ward's, only the wrench those aboard a turned ship hear: the toll and the
     * rest are how a ward's owner is told something came, and are not a pilot's to switch off.
     */
    private static final Set<String> WARP = Set.of(
            "drive_charging",
            "drive_charged",
            "destination_lock",
            "stabilizing",
            "drive_spin_up",
            "rift_open",
            "warp_travel",
            "warp_exit",
            "warp_failed",
            "warp_scatter",
            "drive_cooldown",
            "gate_passage",
            "gate_arrive",
            "ward_wrench");

    private WarpSoundSwitch() {
    }

    /** The names this switch covers, for the test that checks each of them is a sound the mod ships. */
    public static Set<String> names() {
        return WARP;
    }

    /**
     * Whether a sound is one the {@code warpSounds} setting governs.
     *
     * @param namespace the sound's location's namespace
     * @param path      and its path
     */
    public static boolean covers(String namespace, String path) {
        return NAMESPACE.equals(namespace) && WARP.contains(path);
    }
}
