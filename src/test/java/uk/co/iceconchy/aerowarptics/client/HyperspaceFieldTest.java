package uk.co.iceconchy.aerowarptics.client;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.client.fx.HyperspaceField;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cruise's starlines.
 *
 * <p>One promise here matters more than how the effect looks: a cruising pilot can always see where the
 * ship is going. The streaks may fill the edges of the view as densely as they like, and must never put a
 * single lit pixel inside the window kept down the heading - at any speed, any stretch, any moment, any
 * heading.
 */
class HyperspaceFieldTest {

    private static final Vector3d[] HEADINGS = {
            new Vector3d(0.0D, 0.0D, 1.0D),
            new Vector3d(1.0D, 0.0D, 0.0D),
            new Vector3d(-0.6D, 0.0D, -0.8D),
            new Vector3d(Math.sqrt(0.5D), 0.0D, -Math.sqrt(0.5D)),
    };

    @Test
    void noVisibleStreakEverEntersTheWindowAhead() {
        int visible = 0;
        for (Vector3d heading : HEADINGS) {
            for (double scroll = 0.0D; scroll < 600.0D; scroll += 7.3D) {
                for (double speed : new double[]{0.2D, 0.6D, 1.0D}) {
                    for (int index = 0; index < HyperspaceField.STREAKS; index++) {
                        HyperspaceField.Streak streak = HyperspaceField.streak(index, heading, scroll, speed, 1.0D);
                        if (streak.alpha() <= 0.0F) {
                            continue;
                        }
                        visible++;
                        // Every point along the line, not just its ends.
                        for (int step = 0; step <= 8; step++) {
                            Vector3d point = new Vector3d(streak.head()).lerp(streak.tail(), step / 8.0D);
                            double degrees = HyperspaceField.degreesFrom(heading, point);
                            assertTrue(degrees >= HyperspaceField.CLEAR_DEGREES - 1.0e-6D,
                                    "streak " + index + " is lit " + degrees + " degrees off the heading");
                        }
                    }
                }
            }
        }
        assertTrue(visible > 10_000, "only " + visible + " streaks were ever visible - the test proves nothing");
    }

    @Test
    void aShipAtRestDrawsNothing() {
        for (int index = 0; index < HyperspaceField.STREAKS; index++) {
            assertEquals(0.0F, HyperspaceField.streak(index, HEADINGS[0], 40.0D, 0.0D, 1.0D).alpha(), 1.0e-9F);
            assertEquals(0.0F, HyperspaceField.streak(index, HEADINGS[0], 40.0D, 1.0D, 0.0D).alpha(), 1.0e-9F,
                    "before the jump stretches them, the lines are not there");
        }
    }

    @Test
    void theFasterTheShipTheLongerTheLines() {
        double slow = 0.0D;
        double fast = 0.0D;
        for (int index = 0; index < HyperspaceField.STREAKS; index++) {
            slow += length(HyperspaceField.streak(index, HEADINGS[0], 0.0D, 0.25D, 1.0D));
            fast += length(HyperspaceField.streak(index, HEADINGS[0], 0.0D, 1.0D, 1.0D));
        }
        assertEquals(4.0D, fast / slow, 1.0e-6D, "line length is proportional to speed");
    }

    @Test
    void theLinesRushPastTowardsTheViewer() {
        Vector3d heading = HEADINGS[1];
        // A short scroll, over which a line either comes a little closer or - the few right at the back -
        // wraps round to the far end. Nearly all must be doing the first.
        int backwards = 0;
        for (int index = 0; index < HyperspaceField.STREAKS; index++) {
            double before = HyperspaceField.streak(index, heading, 100.0D, 1.0D, 1.0D).head().dot(heading);
            double after = HyperspaceField.streak(index, heading, 101.0D, 1.0D, 1.0D).head().dot(heading);
            if (after < before && before - after < 2.0D) {
                backwards++;
            }
        }
        assertTrue(backwards > HyperspaceField.STREAKS * 0.95D,
                "moving forward, the field has to come back past the camera; only " + backwards + " did");
    }

    @Test
    void theFieldHoldsItsShapeFromFrameToFrame() {
        HyperspaceField.Streak a = HyperspaceField.streak(17, HEADINGS[2], 55.5D, 0.7D, 1.0D);
        HyperspaceField.Streak b = HyperspaceField.streak(17, HEADINGS[2], 55.5D, 0.7D, 1.0D);
        assertEquals(a.head(), b.head());
        assertEquals(a.tail(), b.tail());
    }

    private static double length(HyperspaceField.Streak streak) {
        return streak.head().distance(streak.tail());
    }
}
