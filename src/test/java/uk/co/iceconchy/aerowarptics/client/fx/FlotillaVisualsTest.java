package uk.co.iceconchy.aerowarptics.client.fx;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A harmonised fleet is heard as one note; the HUD's arrow points the way to the slot. */
class FlotillaVisualsTest {

    @Test
    void everyTierHumsItsOwnNote() {
        Set<Float> notes = new HashSet<>();
        for (int tier = 0; tier < 5; tier++) {
            notes.add(FlotillaVisuals.humPitch(tier));
        }
        assertEquals(5, notes.size(), "two tiers humming the same note could not be told apart");
    }

    @Test
    void aHarmonisedEscortHumsExactlyTheFlagshipsNote() {
        for (int own = 0; own < 5; own++) {
            for (int flagship = 0; flagship < 5; flagship++) {
                assertEquals(FlotillaVisuals.humPitch(flagship), FlotillaVisuals.slide(own, flagship, 1.0F, 123.0F),
                        1.0e-6F, "Mk " + own + " onto Mk " + flagship);
            }
        }
    }

    @Test
    void anUnharmonisedEscortStartsFromItsOwnNote() {
        float slid = FlotillaVisuals.slide(0, 2, 0.0F, 0.0F);
        assertEquals(FlotillaVisuals.humPitch(0), slid, 0.04F);
        assertNotEquals(FlotillaVisuals.humPitch(2), slid, 0.04F);
    }

    @Test
    void theSlideMovesOneWayOnly() {
        float previous = FlotillaVisuals.slide(0, 4, 0.0F, 0.0F);
        for (int step = 1; step <= 20; step++) {
            float now = FlotillaVisuals.slide(0, 4, step / 20.0F, 0.0F);
            assertTrue(now >= previous - 0.035F, "the hum should rise towards a higher flagship, wobble aside");
            previous = Math.max(previous, now);
        }
    }

    @Test
    void theChordIsARootAThirdAndAFifth() {
        float[] chord = FlotillaVisuals.chord(1);
        assertEquals(3, chord.length);
        assertEquals(FlotillaVisuals.humPitch(1), chord[0], 1.0e-6F);
        assertEquals(1.26F, chord[1] / chord[0], 0.01F);
        assertEquals(1.5F, chord[2] / chord[0], 0.01F);
        for (float pitch : FlotillaVisuals.chord(4)) {
            assertTrue(pitch <= 2.0F, "Minecraft will not play a note above twice its pitch");
        }
    }

    @Test
    void aTetherFlickersUntilItsDriveHarmonisesButNeverGoesOut() {
        float least = 1.0F;
        float most = 0.0F;
        for (int t = 0; t < 400; t++) {
            float alpha = FlotillaVisuals.tetherAlpha(0.0F, t, 11);
            least = Math.min(least, alpha);
            most = Math.max(most, alpha);
            assertEquals(1.0F, FlotillaVisuals.tetherAlpha(1.0F, t, 11), 1.0e-6F, "steady once harmonised");
        }
        assertTrue(least >= 0.35F - 1.0e-6F, "a tether never blinks out");
        assertTrue(most - least > 0.3F, "and it visibly flickers until then");
        assertEquals(0.0F, FlotillaVisuals.tetherSag(1.0F), 1.0e-6F);
    }

    @Test
    void theArrowPointsTheWayToTheSlot() {
        // Looking along the bow: behind the slot means "go forward", which is straight up.
        assertEquals(0.0D, FlotillaVisuals.arrow(0, -5, 90, 90), 1.0e-9D);
        // Off to port of the slot: go to starboard, to the right.
        assertEquals(Math.PI / 2, FlotillaVisuals.arrow(-5, 0, 0, 0), 1.0e-9D);
        // Facing a quarter turn to starboard of the bow, "forward" is to the player's left.
        assertEquals(-Math.PI / 2, FlotillaVisuals.arrow(0, -5, 90, 0), 1.0e-9D);
    }

    @Test
    void aYawLooksAlongItsDirection() {
        assertEquals(0.0D, FlotillaVisuals.yawOf(0, 1), 1.0e-9D, "yaw 0 is south");
        assertEquals(90.0D, FlotillaVisuals.yawOf(-1, 0), 1.0e-9D, "yaw 90 is west");
    }

    @Test
    void theShakeBuildsTowardsTheEndAndCanBeTurnedOff() {
        assertEquals(0.0F, FlotillaVisuals.countdownShake(0.0F, 1.0F), 1.0e-6F);
        assertTrue(FlotillaVisuals.countdownShake(1.0F, 1.0F) > FlotillaVisuals.countdownShake(0.5F, 1.0F) * 3.0F);
        assertEquals(0.0F, FlotillaVisuals.countdownShake(1.0F, 0.0F), 1.0e-6F);
    }
}
