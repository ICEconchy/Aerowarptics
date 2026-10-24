package uk.co.iceconchy.aerowarptics.airship;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.airship.Fastening.End;
import uk.co.iceconchy.aerowarptics.airship.Fastening.Kind;
import uk.co.iceconchy.aerowarptics.network.ClientboundWarpFeedbackPacket;
import uk.co.iceconchy.aerowarptics.warp.WarpFailure;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What stops a warp among everything tying a vessel to something, and which of it a pilot is told.
 *
 * <p>The report behind this was a Docking Connector locked to the ground: the drive could not move the
 * hull, teleported it anyway, and the joint dragged it back when it let go. The rule pinned here is the
 * one that decides whether a tie like that refuses the warp - and, as important, that the ties a warp now
 * carries through with it do not, or a ship with a docked tender or a turret on a Swivel Bearing could
 * never leave. Finding the ties needs a game; judging them does not.
 */
class FasteningTest {

    private static Fastening tie(Kind kind, End end) {
        return new Fastening(kind, end, new BlockPos(120, 64, -30));
    }

    @Test
    void anythingFastenedToTheGroundOrAnotherShipHoldsTheWarp() {
        for (Kind kind : Kind.values()) {
            assertTrue(Fastening.holds(kind, End.GROUND), kind + " to the ground must refuse the warp");
            assertTrue(Fastening.holds(kind, End.OTHER_SHIP),
                    kind + " to a ship the warp leaves behind must refuse it");
        }
    }

    /**
     * Joined at points in two plots, both of which the warp moves by the same jump, so the joint is left
     * exactly as it was. Refusing these is what would keep a ship with a tender docked alongside on the
     * ground forever.
     */
    @Test
    void aJointBetweenTwoPartsOfTheVesselGoesThroughWithIt() {
        for (Kind kind : List.of(Kind.DOCKING_CONNECTOR, Kind.STICKER, Kind.SWIVEL_BEARING, Kind.SPRING,
                Kind.PLUNGER, Kind.PHYSICS_STAFF, Kind.ROPE)) {
            assertFalse(Fastening.holds(kind, End.CARRIED), kind + " between two carried parts is carried");
        }
    }

    /**
     * A rope from the hull to a loose weight hanging under it was refused as "tied down" (2026-10-16). The
     * weight is part of the vessel and the strand is moved with it, so only a rope to the ground or to a ship
     * that stays behind holds the warp.
     */
    @Test
    void aRopeHoldsOnlyWhenItsFarEndStaysBehind() {
        assertFalse(Fastening.holds(Kind.ROPE, End.CARRIED));
        assertTrue(Fastening.holds(Kind.ROPE, End.GROUND));
        assertTrue(Fastening.holds(Kind.ROPE, End.OTHER_SHIP));
    }

    /** Merging Glue is two sub-levels becoming one, which is a few ticks to wait rather than something to fly. */
    @Test
    void glueHoldsTheWarpEvenAboard() {
        assertTrue(Fastening.holds(Kind.MERGING_GLUE, End.CARRIED));
    }

    @Test
    void pausedPhysicsHoldsWhateverItIsSaidToBeFastenedTo() {
        for (End end : End.values()) {
            assertTrue(Fastening.holds(Kind.PHYSICS_PAUSED, end));
        }
    }

    @Test
    void nothingHoldingIsNothingToName() {
        assertEquals(Optional.empty(), Fastening.worst(List.of()));
        assertEquals(Optional.empty(), Fastening.worst(List.of(
                tie(Kind.DOCKING_CONNECTOR, End.CARRIED), tie(Kind.SWIVEL_BEARING, End.CARRIED))),
                "a docked tender and a turret are the vessel, not something holding it");
    }

    /**
     * One thing fits on the action bar, so it is the one most worth undoing: nothing on the deck helps while
     * physics is paused, then a line to the ground, then one to another ship, then glue setting aboard.
     */
    @Test
    void theOneNamedIsTheOneMostWorthUndoing() {
        Fastening glueAboard = tie(Kind.MERGING_GLUE, End.CARRIED);
        Fastening toAnotherShip = tie(Kind.STICKER, End.OTHER_SHIP);
        Fastening toTheGround = tie(Kind.DOCKING_CONNECTOR, End.GROUND);
        Fastening paused = new Fastening(Kind.PHYSICS_PAUSED, End.GROUND, null);
        Fastening carried = tie(Kind.SWIVEL_BEARING, End.CARRIED);

        assertEquals(Optional.of(toTheGround),
                Fastening.worst(List.of(carried, glueAboard, toAnotherShip, toTheGround)));
        assertEquals(Optional.of(toAnotherShip), Fastening.worst(List.of(glueAboard, carried, toAnotherShip)));
        assertEquals(Optional.of(glueAboard), Fastening.worst(List.of(carried, glueAboard)));
        assertEquals(Optional.of(paused), Fastening.worst(List.of(toTheGround, paused, glueAboard)));
    }

    @Test
    void equalTiesAreNamedInTheOrderTheyWereFound() {
        Fastening first = new Fastening(Kind.ROPE, End.GROUND, new BlockPos(1, 2, 3));
        Fastening second = new Fastening(Kind.DOCKING_CONNECTOR, End.GROUND, new BlockPos(4, 5, 6));
        assertEquals(Optional.of(first), Fastening.worst(List.of(first, second)));
        assertEquals(Optional.of(second), Fastening.worst(List.of(second, first)));
    }

    // ------------------------------------------------------------------ the wire

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);
    }

    @Test
    void whatTiesTheShipSurvivesTheWire() {
        for (Fastening sent : List.of(tie(Kind.DOCKING_CONNECTOR, End.GROUND),
                new Fastening(Kind.PHYSICS_PAUSED, End.GROUND, null),
                new Fastening(Kind.ROPE, End.CARRIED, new BlockPos(-30_000_000, -64, 29_999_999)))) {
            RegistryFriendlyByteBuf buf = buffer();
            ClientboundWarpFeedbackPacket.STREAM_CODEC.encode(buf,
                    ClientboundWarpFeedbackPacket.of(WarpFailure.SHIP_FASTENED, sent));
            ClientboundWarpFeedbackPacket read = ClientboundWarpFeedbackPacket.STREAM_CODEC.decode(buf);
            assertEquals(WarpFailure.SHIP_FASTENED, read.failure());
            assertEquals(Optional.of(sent), read.fastening(), "the pilot must be sent to the block that is doing it");
        }

        RegistryFriendlyByteBuf plain = buffer();
        ClientboundWarpFeedbackPacket.STREAM_CODEC.encode(plain, new ClientboundWarpFeedbackPacket(WarpFailure.NONE));
        assertEquals(Optional.empty(), ClientboundWarpFeedbackPacket.STREAM_CODEC.decode(plain).fastening());
    }

    /** A malformed packet must not name some other thing on somebody's deck. */
    @Test
    void aTieThatNamesNothingIsRefused() {
        RegistryFriendlyByteBuf badKind = buffer();
        badKind.writeVarInt(Kind.values().length);
        badKind.writeVarInt(0);
        badKind.writeBoolean(false);
        assertThrows(IllegalArgumentException.class, () -> Fastening.STREAM_CODEC.decode(badKind));

        RegistryFriendlyByteBuf badEnd = buffer();
        badEnd.writeVarInt(0);
        badEnd.writeVarInt(-1);
        badEnd.writeBoolean(false);
        assertThrows(IllegalArgumentException.class, () -> Fastening.STREAM_CODEC.decode(badEnd));
    }
}
