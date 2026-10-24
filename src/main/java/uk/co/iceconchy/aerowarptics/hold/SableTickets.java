package uk.co.iceconchy.aerowarptics.hold;

import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicket;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AeroWarptics;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Reads a Sable ticket's type and key whichever version of Sable is installed.
 *
 * <p>Sable 2.0.5 turned {@code SubLevelLoadingTicket} into a record, and its getters went with it:
 * {@code getType()} and {@code getKey()} became {@code type()} and {@code key()}. A call compiled against
 * one is a {@code NoSuchMethodError} against the other - and the first call is made on the server's first
 * tick, so a player on the other version could not load a world at all. So the accessor is looked up by
 * name when this class loads, newer name first, the way {@code DimensionalSableBridge} reaches a mod it
 * may not have.
 *
 * <p>Found neither way - some later Sable renaming them again - {@link #available()} says so, and the one
 * thing that reads tickets, the sweep for orphaned ones, stands down instead of taking the server with it.
 */
final class SableTickets {

    private static final MethodHandle TYPE = accessor(SubLevelLoadingTicket.class, "type", "getType");
    private static final MethodHandle KEY = accessor(SubLevelLoadingTicket.class, "key", "getKey");

    private SableTickets() {
    }

    /** Whether both accessors were found on the installed Sable. */
    static boolean available() {
        return TYPE != null && KEY != null;
    }

    /** A ticket's type, or {@code null} when it cannot be read. */
    @Nullable
    static Object type(SubLevelLoadingTicket<?> ticket) {
        return read(TYPE, ticket);
    }

    /** A ticket's key, or {@code null} when it cannot be read. */
    @Nullable
    static Object key(SubLevelLoadingTicket<?> ticket) {
        return read(KEY, ticket);
    }

    @Nullable
    private static Object read(@Nullable MethodHandle accessor, Object ticket) {
        if (accessor == null) {
            return null;
        }
        try {
            return accessor.invoke(ticket);
        } catch (Throwable failure) {
            return null;
        }
    }

    /**
     * The first of {@code names} that is a public, no-argument instance method of {@code owner}, as a handle
     * taking any object and returning one; {@code null} if there is none.
     */
    @Nullable
    static MethodHandle accessor(Class<?> owner, String... names) {
        for (String name : names) {
            try {
                Method method = owner.getMethod(name);
                if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() == void.class) {
                    continue;
                }
                return MethodHandles.publicLookup().unreflect(method)
                        .asType(MethodType.methodType(Object.class, Object.class));
            } catch (NoSuchMethodException | IllegalAccessException absent) {
                // Try the next name.
            }
        }
        AeroWarptics.LOGGER.warn("Sable's {} has none of {} - this version of AeroWarptics cannot read its tickets",
                owner.getSimpleName(), String.join(", ", names));
        return null;
    }
}
