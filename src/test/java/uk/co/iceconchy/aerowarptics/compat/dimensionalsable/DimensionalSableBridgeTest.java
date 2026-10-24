package uk.co.iceconchy.aerowarptics.compat.dimensionalsable;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Finding Dimensional Sable's warp without compiling against it.
 *
 * <p>The failure this guards would not throw until a warp reached its crossing: a handle looked up with
 * the wrong name or shape resolves to nothing, and every crossing is quietly refused as unsupported - or,
 * looked up too loosely, is invoked with arguments the real method does not take.
 */
class DimensionalSableBridgeTest {

    /** Stands in for {@code dev.egg.SubLevelWarper} as of Dimensional Sable 1.0.5. */
    public static final class Current {
        public static int WarpSubLevel(ServerSubLevel subLevel, Level dimension, Vector3d position) {
            return 3;
        }
    }

    /** The same name with another shape, as a later version might have. */
    public static final class Changed {
        public static int WarpSubLevel(ServerSubLevel subLevel, Level dimension, Vector3d position, boolean connected) {
            return 1;
        }
    }

    /** Private, as the internal overload is. */
    public static final class Hidden {
        private static int WarpSubLevel(ServerSubLevel subLevel, Level dimension, Vector3d position) {
            return 1;
        }
    }

    private static final ClassLoader LOADER = DimensionalSableBridgeTest.class.getClassLoader();

    @Test
    void theCurrentSignatureIsFoundAndCallable() throws Throwable {
        MethodHandle warp = DimensionalSableBridge.resolve(Current.class.getName(), LOADER);
        assertNotNull(warp);
        assertEquals(3, (int) warp.invokeExact((ServerSubLevel) null, (Level) null, new Vector3d()));
    }

    @Test
    void aChangedOrHiddenMethodIsUnavailableRatherThanAFailureLater() {
        assertNull(DimensionalSableBridge.resolve(Changed.class.getName(), LOADER));
        assertNull(DimensionalSableBridge.resolve(Hidden.class.getName(), LOADER));
    }

    @Test
    void aMissingModIsUnavailable() {
        assertNull(DimensionalSableBridge.resolve("dev.egg.NoSuchWarper", LOADER));
    }

    /**
     * The real thing, when its jar is in {@code Depends/} (which puts it on this classpath). Skipped
     * otherwise, because the mod is optional and nobody should need it to build - but whoever updates
     * the jar gets told straight away if the method this mod calls has gone.
     */
    @Test
    void theRealModStillHasTheMethodWhenItsJarIsPresent() {
        boolean present;
        try {
            Class.forName(DimensionalSableBridge.WARPER, false, LOADER);
            present = true;
        } catch (ClassNotFoundException e) {
            present = false;
        }
        assumeTrue(present, "Dimensional Sable is not in Depends/");
        assertNotNull(DimensionalSableBridge.resolve(DimensionalSableBridge.WARPER, LOADER),
                "Dimensional Sable no longer has WarpSubLevel(ServerSubLevel, Level, Vector3d)");
    }
}
