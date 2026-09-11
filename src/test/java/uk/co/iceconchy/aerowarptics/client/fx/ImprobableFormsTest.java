package uk.co.iceconchy.aerowarptics.client.fx;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The improbable rift's figures. Line drawings fail quietly - a point pushed outside the box draws a
 * limb through the rim, and a stroke with no length draws nothing at all - so those are what is held.
 */
class ImprobableFormsTest {

    @Test
    void everyFormIsWholeStrokes() {
        for (int form = 0; form < ImprobableForms.count(); form++) {
            float[] strokes = ImprobableForms.form(form);
            assertEquals(0, strokes.length % 4, "form " + form + " ends partway through a stroke");
            assertTrue(strokes.length / 4 >= 5, "form " + form + " is too sparse to be recognised");
        }
    }

    @Test
    void everyFormStaysInsideItsBox() {
        for (int form = 0; form < ImprobableForms.count(); form++) {
            for (float value : ImprobableForms.form(form)) {
                assertTrue(value >= -1.0F && value <= 1.0F,
                        "form " + form + " reaches " + value + ", outside the unit box it is scaled from");
            }
        }
    }

    @Test
    void noStrokeHasNoLength() {
        for (int form = 0; form < ImprobableForms.count(); form++) {
            float[] strokes = ImprobableForms.form(form);
            for (int i = 0; i < strokes.length; i += 4) {
                double length = Math.hypot(strokes[i + 2] - strokes[i], strokes[i + 3] - strokes[i + 1]);
                assertTrue(length > 1.0e-3D, "form " + form + " has an invisible stroke at " + i / 4);
            }
        }
    }

    @Test
    void theFormsAreAllDifferent() {
        for (int a = 0; a < ImprobableForms.count(); a++) {
            for (int b = a + 1; b < ImprobableForms.count(); b++) {
                assertFalse(Arrays.equals(ImprobableForms.form(a), ImprobableForms.form(b)),
                        "forms " + a + " and " + b + " are the same drawing");
            }
        }
    }

    @Test
    void theListWrapsRoundInBothDirections() {
        int count = ImprobableForms.count();
        assertSame(ImprobableForms.form(0), ImprobableForms.form(count));
        assertSame(ImprobableForms.form(count - 1), ImprobableForms.form(-1));
    }
}
