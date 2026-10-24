package uk.co.iceconchy.aerowarptics.ward;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a Rift Ward's field sends a rift aimed into it.
 *
 * <p>The failure this guards is the silent one: a hull put down a block inside the field it was turned from, or
 * turned somewhere that depends on which way the ship happened to be facing. Either flies, lands and looks like a
 * deflection.
 */
class WardFieldTest {

    private static final WardField.Circle FIELD = new WardField.Circle(100.0D, -40.0D, 48.0D);

    private static double out(WardField.Circle field, WardField.Point point) {
        return Math.hypot(point.x() - field.x(), point.z() - field.z());
    }

    @Test
    void aTargetOutsideTheFieldIsLeftExactlyWhereItWas() {
        WardField.Point target = new WardField.Point(100.0D + 48.0D, -40.0D);
        assertSame(target, WardField.deflect(FIELD, new WardField.Point(900.0D, 0.0D), target, 12.0D),
                "the rim itself is outside the field");
        WardField.Point far = new WardField.Point(400.0D, 400.0D);
        assertSame(far, WardField.deflect(FIELD, null, far, 12.0D));
    }

    @Test
    void whateverIsTurnedComesOutClearOfTheRimByItsOwnReach() {
        Random random = new Random(7);
        for (int trial = 0; trial < 2000; trial++) {
            double clearance = random.nextDouble() * 40.0D;
            double bearing = random.nextDouble() * Math.PI * 2.0D;
            double away = 60.0D + clearance + random.nextDouble() * 5000.0D;
            WardField.Point from = new WardField.Point(FIELD.x() + Math.cos(bearing) * away, FIELD.z() + Math.sin(bearing) * away);
            double inside = random.nextDouble() * 47.9D;
            double at = random.nextDouble() * Math.PI * 2.0D;
            WardField.Point target = new WardField.Point(FIELD.x() + Math.cos(at) * inside, FIELD.z() + Math.sin(at) * inside);

            WardField.Point turned = WardField.deflect(FIELD, from, target, clearance);
            assertEquals(FIELD.radius() + clearance, out(FIELD, turned), 1.0e-6D,
                    "a turned rift comes out exactly its clearance outside the rim");
            assertFalse(FIELD.contains(turned));
        }
    }

    /** It comes out on the side it came in by: somewhere on the line it was flying, short of where it aimed. */
    @Test
    void itComesOutOnTheLineItWasFlyingAndShortOfItsTarget() {
        WardField.Point from = new WardField.Point(-900.0D, 300.0D);
        WardField.Point target = new WardField.Point(110.0D, -30.0D);
        WardField.Point turned = WardField.deflect(FIELD, from, target, 10.0D);
        double whole = from.distanceTo(target);
        assertEquals(whole, from.distanceTo(turned) + turned.distanceTo(target), 1.0e-6D, "off the line it was flying");
        assertTrue(from.distanceTo(turned) < whole);
        // And the near side: closer to where it came from than the middle of the field is.
        assertTrue(from.distanceTo(turned) < from.distanceTo(new WardField.Point(FIELD.x(), FIELD.z())));
    }

    /**
     * The whole picture turned about the ward gives the same answer turned with it. A deflection worked out from a
     * world box, or along one axis first, passes every other test here and fails this one.
     */
    @Test
    void theAnswerDoesNotDependOnTheCompass() {
        WardField.Point from = new WardField.Point(FIELD.x() + 700.0D, FIELD.z() + 120.0D);
        WardField.Point target = new WardField.Point(FIELD.x() + 9.0D, FIELD.z() - 21.0D);
        WardField.Point straight = WardField.deflect(FIELD, from, target, 16.0D);
        for (int degrees = 15; degrees < 360; degrees += 15) {
            double turn = Math.toRadians(degrees);
            WardField.Point turned = WardField.deflect(FIELD, about(from, turn), about(target, turn), 16.0D);
            WardField.Point expected = about(straight, turn);
            assertEquals(expected.x(), turned.x(), 1.0e-6D, "at " + degrees + " degrees");
            assertEquals(expected.z(), turned.z(), 1.0e-6D, "at " + degrees + " degrees");
        }
    }

    private static WardField.Point about(WardField.Point point, double turn) {
        double dx = point.x() - FIELD.x();
        double dz = point.z() - FIELD.z();
        return new WardField.Point(FIELD.x() + dx * Math.cos(turn) - dz * Math.sin(turn),
                FIELD.z() + dx * Math.sin(turn) + dz * Math.cos(turn));
    }

    @Test
    void aTravellerWithNoLineIsPushedStraightOutThroughItsTarget() {
        WardField.Point target = new WardField.Point(FIELD.x() + 10.0D, FIELD.z());
        // From inside the field, from another dimension, and from directly over its own target.
        for (WardField.Point from : new WardField.Point[]{new WardField.Point(FIELD.x() - 5.0D, FIELD.z() + 3.0D), null, target}) {
            WardField.Point turned = WardField.deflect(FIELD, from, target, 6.0D);
            assertEquals(FIELD.x() + 54.0D, turned.x(), 1.0e-6D);
            assertEquals(FIELD.z(), turned.z(), 1.0e-6D);
        }
        // Aimed at the very middle with nowhere to come from, it still goes somewhere, and outside.
        WardField.Point middle = WardField.deflect(FIELD, null, new WardField.Point(FIELD.x(), FIELD.z()), 6.0D);
        assertEquals(54.0D, out(FIELD, middle), 1.0e-6D);
    }

    @Test
    void overlappingFieldsLeaveItOutsideEveryOneOfThem() {
        List<WardField.Circle> two = List.of(new WardField.Circle(0.0D, 0.0D, 50.0D), new WardField.Circle(70.0D, 0.0D, 50.0D));
        List<WardField.Circle> three = List.of(new WardField.Circle(0.0D, 0.0D, 50.0D), new WardField.Circle(70.0D, 0.0D, 50.0D),
                new WardField.Circle(35.0D, 60.0D, 50.0D));
        Random random = new Random(11);
        for (List<WardField.Circle> fields : List.of(two, three)) {
            for (int trial = 0; trial < 1000; trial++) {
                double bearing = random.nextDouble() * Math.PI * 2.0D;
                WardField.Point from = random.nextInt(5) == 0 ? null
                        : new WardField.Point(Math.cos(bearing) * 900.0D, Math.sin(bearing) * 900.0D);
                WardField.Point target = new WardField.Point(random.nextDouble() * 160.0D - 50.0D, random.nextDouble() * 160.0D - 50.0D);
                WardField.Turned turned = WardField.settle(fields, from, target, 8.0D);
                assertTrue(turned.settled(), "not clear of " + fields.size() + " fields from " + target);
                assertFalse(WardField.inside(fields, turned.at().x(), turned.at().z()));
                if (turned.moved()) {
                    for (WardField.Circle field : fields) {
                        assertTrue(out(field, turned.at()) >= field.radius() + 8.0D - 1.0e-6D, "turned out of one field"
                                + " and left " + (out(field, turned.at()) - field.radius()) + " from another's rim");
                    }
                }
                assertEquals(WardField.inside(fields, target.x(), target.z()), turned.moved(),
                        "only a rift aimed into a field is turned, and every one that is, is");
            }
        }
    }

    @Test
    void theFieldThatTurnsItIsNamedAndOneThatDoesNotIsNot() {
        List<WardField.Circle> fields = List.of(new WardField.Circle(0.0D, 0.0D, 30.0D), new WardField.Circle(500.0D, 0.0D, 30.0D));
        WardField.Turned turned = WardField.settle(fields, new WardField.Point(0.0D, 900.0D), new WardField.Point(505.0D, 3.0D), 4.0D);
        assertEquals(List.of(1), turned.turnedBy());
        assertTrue(WardField.settle(fields, null, new WardField.Point(250.0D, 0.0D), 4.0D).turnedBy().isEmpty());
    }

    @Test
    void aStormThinsAFieldAndABetterDrivePushesFurtherIntoIt() {
        assertEquals(64.0D, WardField.standing(64.0D, false, 0.6D));
        assertEquals(38.4D, WardField.standing(64.0D, true, 0.6D), 1.0e-9D);
        assertEquals(64.0D, WardField.met(64.0D, 0.0D));
        assertEquals(32.0D, WardField.met(64.0D, 0.5D));
        assertEquals(0.0D, WardField.met(64.0D, 1.0D));
        // So a drive that pushes half way in lands where it aimed anywhere in the outer half.
        WardField.Circle met = new WardField.Circle(0.0D, 0.0D, WardField.met(64.0D, 0.5D));
        WardField.Point outerHalf = new WardField.Point(40.0D, 0.0D);
        assertSame(outerHalf, WardField.deflect(met, new WardField.Point(900.0D, 0.0D), outerHalf, 5.0D));
        // Nothing has a field: no radius, no holding.
        assertFalse(new WardField.Circle(0.0D, 0.0D, 0.0D).contains(0.0D, 0.0D));
    }

    @Test
    void theRimWalkStaysOnTheRimAndStepsAwayEitherSide() {
        WardField.Point first = new WardField.Point(FIELD.x() + 60.0D, FIELD.z());
        List<WardField.Point> walk = WardField.rimWalk(FIELD, first, 20.0D, 4);
        assertEquals(8, walk.size());
        double last = 0.0D;
        for (int index = 0; index < walk.size(); index++) {
            WardField.Point point = walk.get(index);
            assertEquals(60.0D, out(FIELD, point), 1.0e-6D, "a step round the rim left it");
            double away = first.distanceTo(point);
            assertTrue(away >= last - 1.0e-9D, "the walk came back towards where it started");
            last = away;
        }
        assertEquals(walk.get(0).x(), walk.get(1).x(), 1.0e-6D, "a step either way is the same step");
        assertEquals(-(walk.get(0).z() - FIELD.z()), walk.get(1).z() - FIELD.z(), 1.0e-6D);
    }
}
