package uk.co.iceconchy.aerowarptics.compat.dimensionalsable;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import uk.co.iceconchy.aerowarptics.AeroWarptics;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * The one place this mod touches Dimensional Sable.
 *
 * <p><a href="https://modrinth.com/mod/dimensional-sable">Dimensional Sable</a> (MIT, by HollowEgg) moves a
 * Sable sub-level, and everything joined to it, into another dimension - which Sable itself cannot do. It
 * is an optional dependency: without it, warps stay in their own dimension.
 *
 * <p>Reached by a method handle rather than compiled against, for two reasons. The mod is not on a Maven
 * repository and this project builds only against the jars in {@code Depends/}, so a compile-time
 * dependency would make every contributor fetch a jar for a feature that is off by default. And its whole
 * API is one static method, {@code dev.egg.SubLevelWarper.WarpSubLevel(ServerSubLevel, Level, Vector3d)},
 * so a handle costs nothing in clarity - while a signature that changes in some later version turns into
 * "not available" here, with a line in the log, rather than a {@code NoSuchMethodError} halfway through a
 * warp.
 *
 * <p>Its README says the Simulated team is building an official API for this, and that Dimensional Sable
 * will be retired when it lands. This class is what gets replaced then.
 */
public final class DimensionalSableBridge {

    public static final String MOD_ID = "dimensional_sable";
    static final String WARPER = "dev.egg.SubLevelWarper";
    static final String METHOD = "WarpSubLevel";
    static final MethodType SIGNATURE =
            MethodType.methodType(int.class, ServerSubLevel.class, Level.class, Vector3d.class);

    /** Resolved on first use; {@code null} inside means "looked, and it is not there". */
    @Nullable
    private static Resolved resolved;

    private record Resolved(@Nullable MethodHandle warp) {
    }

    private DimensionalSableBridge() {
    }

    /** Whether Dimensional Sable is installed and has the method this mod calls. */
    public static boolean available() {
        return handle() != null;
    }

    /**
     * Moves {@code subLevel} and every sub-level joined to it into {@code destination}, with its pose
     * position at {@code position} and its orientation unchanged.
     *
     * <p>Dimensional Sable rebuilds each part in the destination with a new UUID, moves the entities it
     * finds aboard, takes contraptions apart, and removes the originals last. It reports nothing but a
     * count; failure is whatever it throws.
     *
     * @return how many sub-levels it moved
     * @throws IllegalStateException when Dimensional Sable is not available
     */
    public static int warp(ServerSubLevel subLevel, Level destination, Vector3d position) throws Throwable {
        MethodHandle warp = handle();
        if (warp == null) {
            throw new IllegalStateException("Dimensional Sable is not available");
        }
        return (int) warp.invokeExact(subLevel, destination, position);
    }

    @Nullable
    private static MethodHandle handle() {
        Resolved current = resolved;
        if (current == null) {
            MethodHandle found = null;
            if (ModList.get() != null && ModList.get().isLoaded(MOD_ID)) {
                found = resolve(WARPER, DimensionalSableBridge.class.getClassLoader());
                if (found == null) {
                    AeroWarptics.LOGGER.warn("Dimensional Sable is installed but has no {}.{}{} - this version is "
                            + "not one AeroWarptics knows how to call, so warps will stay in their own dimension.",
                            WARPER, METHOD, SIGNATURE);
                }
            }
            current = new Resolved(found);
            resolved = current;
        }
        return current.warp();
    }

    /**
     * The warp method on {@code className}, or {@code null} when that class is missing or does not have it
     * with exactly the expected signature. Split out, and given the class name, so the lookup is tested
     * against stand-ins without the real mod.
     */
    @Nullable
    static MethodHandle resolve(String className, ClassLoader loader) {
        try {
            // Not initialised here: that happens on the first warp, not when somebody opens a chart.
            Class<?> warper = Class.forName(className, false, loader);
            return MethodHandles.publicLookup().findStatic(warper, METHOD, SIGNATURE);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }
}
