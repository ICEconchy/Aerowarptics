package uk.co.iceconchy.aerowarptics.hold;

import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicket;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That a Sable ticket can be read on either side of Sable 2.0.5's rename.
 *
 * <p>Built against 2.0.3, whose ticket has {@code getType()}; 2.0.5's is a record with {@code type()}. The
 * direct call was a {@code NoSuchMethodError} on the server's first tick for anyone on 2.0.5 - a world that
 * would not load. The real ticket class on the test classpath checks one side; the two stand-ins below, one
 * shaped like each version, check that the lookup finds either.
 */
class SableTicketsTest {

    /** Shaped like Sable 2.0.5's ticket: a record, so its accessors are named after its components. */
    public record NewTicket(Object type, UUID subLevelId, Object key) {
    }

    /** Shaped like Sable 2.0.3's: a class with getters. */
    public static final class OldTicket {
        private final Object type;

        public OldTicket(Object type) {
            this.type = type;
        }

        public Object getType() {
            return type;
        }
    }

    @Test
    void theInstalledSablesTicketsCanBeRead() throws Exception {
        assertTrue(SableTickets.available(), "neither accessor name was found on the Sable the mod builds against");
        HoldKey key = new HoldKey(UUID.randomUUID(), HoldPurpose.FLIGHT);
        SubLevelLoadingTicket<HoldKey> ticket = new SubLevelLoadingTicket<>(Holds.SHIP_TICKET, UUID.randomUUID(), key);
        assertEquals(Holds.SHIP_TICKET, SableTickets.type(ticket));
        assertEquals(key, SableTickets.key(ticket));
    }

    @Test
    void eitherNameIsFound() throws Throwable {
        Object marker = new Object();
        MethodHandle recordStyle = SableTickets.accessor(NewTicket.class, "type", "getType");
        assertNotNull(recordStyle, "a record's accessor was not found");
        assertEquals(marker, recordStyle.invoke(new NewTicket(marker, UUID.randomUUID(), "k")));

        MethodHandle getterStyle = SableTickets.accessor(OldTicket.class, "type", "getType");
        assertNotNull(getterStyle, "the older getter was not found");
        assertEquals(marker, getterStyle.invoke(new OldTicket(marker)));
    }

    /** A Sable that renamed them again gets a null, which stands the sweep down, not an exception. */
    @Test
    void neitherNameIsANullNotACrash() {
        assertNull(SableTickets.accessor(OldTicket.class, "kind", "getKind"));
    }
}
