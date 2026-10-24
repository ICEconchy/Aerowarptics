package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rift's chorus-fruit hop, read off the source: neither side of it can run without a game.
 *
 * <p>What matters about a teleport is who it moves and whether a protection mod can stop it, and neither
 * shows up as a failure anywhere. A pearl that moved its thrower would walk them through every claim border
 * on a server; a hop that skipped the chorus fruit's event would do the same to whatever it hit.
 */
class RiftHopTest {

    private static final String SOURCES = "src/main/java/uk/co/iceconchy/aerowarptics/";

    @Test
    void everyHopAsksTheChorusFruitEventFirst() {
        String hop = read("rifting/RiftHop.java");
        int asked = hop.indexOf("EventHooks.onChorusFruitTeleport(");
        int cancelled = hop.indexOf("event.isCanceled()", asked);
        int moved = hop.indexOf(".randomTeleport(", cancelled);
        assertTrue(asked > 0 && cancelled > asked && moved > cancelled,
                "a hop teleports without asking the chorus fruit's event, or ignores a cancel");
        assertTrue(hop.contains("event.getTargetX()"), "a hop ignores where the event moved it to");
    }

    @Test
    void nothingABossOrACarriedCreatureIsHopped() {
        String hop = read("rifting/RiftHop.java");
        String mayHop = hop.substring(hop.indexOf("public static boolean mayHop("), hop.indexOf("public static boolean hop("));
        for (String refusal : new String[]{"Tags.EntityTypes.BOSSES", "Tags.EntityTypes.TELEPORTING_NOT_SUPPORTED",
                "AbstractContraptionEntity", "getTrackingOrVehicleSubLevel"}) {
            assertTrue(mayHop.contains(refusal), "mayHop no longer refuses " + refusal);
        }
    }

    /** A pearl hops what it hits - asked first - and never the one who threw it. */
    @Test
    void aPearlHopsWhatItHitsAndNeverItsThrower() {
        String pearl = read("pearl/RiftPearlEntity.java");
        String hit = pearl.substring(pearl.indexOf("protected void onHitEntity("), pearl.indexOf("protected void onHit(HitResult"));
        assertTrue(hit.contains("RiftHop.mayHop(living)") && hit.contains("RiftHop.hop(server, living,"),
                "a pearl no longer hops the creature it hits, or skips asking whether it may");
        assertFalse(pearl.contains("hop(server, getOwner()") || pearl.contains("owner.teleport"),
                "a pearl moves its thrower");
        String landed = pearl.substring(pearl.indexOf("protected void onHit(HitResult"));
        assertFalse(landed.contains("RiftHop.hop("), "a pearl hops something where it lands, not what it hits");
    }

    /** Rifting and the pearl share one hop, so a fix to one is a fix to both. */
    @Test
    void riftingUsesTheSameHop() {
        String rifting = read("rifting/RiftingType.java");
        assertTrue(rifting.contains("RiftHop.mayHop(") && rifting.contains("RiftHop.hop("));
        assertFalse(rifting.contains("onChorusFruitTeleport"), "rifting grew a hop of its own again");
    }

    private static String read(String path) {
        try {
            return Files.readString(Path.of(SOURCES + path));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
