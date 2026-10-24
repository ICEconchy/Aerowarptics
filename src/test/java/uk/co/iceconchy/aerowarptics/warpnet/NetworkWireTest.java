package uk.co.iceconchy.aerowarptics.warpnet;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchor;
import uk.co.iceconchy.aerowarptics.anchor.WarpAnchorAccess;
import uk.co.iceconchy.aerowarptics.gate.RiftGate;
import uk.co.iceconchy.aerowarptics.gate.RiftGateRegistry;
import uk.co.iceconchy.aerowarptics.gate.RiftGateShape;
import uk.co.iceconchy.aerowarptics.network.ClientboundNetworkPanelPacket;
import uk.co.iceconchy.aerowarptics.network.ServerboundAstrolabePacket;
import uk.co.iceconchy.aerowarptics.network.ServerboundGatePacket;
import uk.co.iceconchy.aerowarptics.network.ServerboundNetworkPacket;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Networks on the wire and on disk: what goes in comes out, and nothing a sender says can make a receiver
 * allocate more than a network is allowed to hold.
 */
class NetworkWireTest {

    private static final UUID PILOT = UUID.randomUUID();
    private static final UUID MATE = UUID.randomUUID();
    private static final UUID NETWORK = UUID.randomUUID();

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.OTHER);
    }

    private static WarpNetwork harbour() {
        return WarpNetwork.create(NETWORK, "Harbour", PILOT, "Pilot").withMember(MATE, "Mate");
    }

    @Test
    void aNetworkSurvivesTheWire() {
        RegistryFriendlyByteBuf buf = buffer();
        WarpNetwork.STREAM_CODEC.encode(buf, harbour());
        assertEquals(harbour(), WarpNetwork.STREAM_CODEC.decode(buf));
        assertEquals(0, buf.readableBytes(), "the decoder left some of the network unread");
    }

    /** A count is range-checked before anything is allocated for it. */
    @Test
    void aNetworkClaimingMoreMembersThanOneMayHaveIsRefused() {
        RegistryFriendlyByteBuf buf = buffer();
        buf.writeUUID(NETWORK);
        buf.writeUtf("Harbour", 32);
        buf.writeUUID(PILOT);
        buf.writeUtf("Pilot", 32);
        buf.writeVarInt(WarpNetwork.MAX_MEMBERS + 1);
        assertThrows(DecoderException.class, () -> WarpNetwork.STREAM_CODEC.decode(buf));
    }

    /** One over the limit is written short rather than whole, so the reader is not handed a count it refuses. */
    @Test
    void aNetworkOverTheLimitIsSentShortNotRefusedByItsReader() {
        Map<UUID, String> crowd = new LinkedHashMap<>();
        for (int member = 0; member < WarpNetwork.MAX_MEMBERS + 20; member++) {
            crowd.put(UUID.randomUUID(), "Member" + member);
        }
        RegistryFriendlyByteBuf buf = buffer();
        WarpNetwork.STREAM_CODEC.encode(buf, new WarpNetwork(NETWORK, "Harbour", PILOT, "Pilot", crowd));
        assertEquals(WarpNetwork.MAX_MEMBERS, WarpNetwork.STREAM_CODEC.decode(buf).members().size());
    }

    @Test
    void aNoticeSurvivesTheWire() {
        for (NetworkNotice sent : List.of(NetworkNotice.NONE, NetworkNotice.refusal(NetworkNotice.NAME_TAKEN, "North"),
                NetworkNotice.done(NetworkNotice.ADDED, "Mate"))) {
            RegistryFriendlyByteBuf buf = buffer();
            NetworkNotice.encode(buf, sent);
            assertEquals(sent, NetworkNotice.decode(buf));
        }
    }

    @Test
    void thePanelSurvivesTheWireWithANetworkAndWithout() {
        List<NetworkOption> mine = List.of(new NetworkOption(NETWORK, "Harbour"));
        for (ClientboundNetworkPanelPacket sent : List.of(
                new ClientboundNetworkPanelPacket(mine, NETWORK, harbour(), NetworkNotice.done(NetworkNotice.ADDED, "Mate")),
                new ClientboundNetworkPanelPacket(mine, NETWORK, null, NetworkNotice.refusal(NetworkNotice.GONE)),
                new ClientboundNetworkPanelPacket(List.of(), null, null, NetworkNotice.NONE))) {
            RegistryFriendlyByteBuf buf = buffer();
            ClientboundNetworkPanelPacket.STREAM_CODEC.encode(buf, sent);
            assertEquals(sent, ClientboundNetworkPanelPacket.STREAM_CODEC.decode(buf));
        }
    }

    @Test
    void theOrdersSurviveTheWire() {
        for (ServerboundNetworkPacket sent : List.of(ServerboundNetworkPacket.list(null),
                ServerboundNetworkPacket.list(NETWORK), ServerboundNetworkPacket.rename(NETWORK, "Old Port"),
                ServerboundNetworkPacket.add(NETWORK, "Mate"), ServerboundNetworkPacket.remove(NETWORK, MATE),
                ServerboundNetworkPacket.leave(NETWORK), ServerboundNetworkPacket.disband(NETWORK))) {
            RegistryFriendlyByteBuf buf = buffer();
            ServerboundNetworkPacket.STREAM_CODEC.encode(buf, sent);
            assertEquals(sent, ServerboundNetworkPacket.STREAM_CODEC.decode(buf));
        }
    }

    /** Both list screens send their filter with every action, since every action is answered with the list. */
    @Test
    void aListsFilterRidesEveryActionAndNoFilterIsNil() {
        BlockPos pos = new BlockPos(12, 70, -5);
        UUID nil = new UUID(0L, 0L);
        for (ServerboundAstrolabePacket sent : List.of(ServerboundAstrolabePacket.open(pos, NETWORK),
                ServerboundAstrolabePacket.select(pos, MATE, NETWORK),
                ServerboundAstrolabePacket.holdAltitude(pos, true, NETWORK))) {
            RegistryFriendlyByteBuf buf = buffer();
            ServerboundAstrolabePacket.STREAM_CODEC.encode(buf, sent);
            assertEquals(NETWORK, ServerboundAstrolabePacket.STREAM_CODEC.decode(buf).filter());
        }
        assertEquals(nil, ServerboundAstrolabePacket.open(pos, null).filter());

        for (ServerboundGatePacket sent : List.of(ServerboundGatePacket.open(pos, NETWORK),
                ServerboundGatePacket.dial(pos, MATE, NETWORK), ServerboundGatePacket.hangUp(pos, NETWORK),
                ServerboundGatePacket.rename(pos, "Gate", NETWORK), ServerboundGatePacket.cycleAccess(pos, NETWORK),
                ServerboundGatePacket.setNetwork(pos, "Harbour", NETWORK))) {
            RegistryFriendlyByteBuf buf = buffer();
            ServerboundGatePacket.STREAM_CODEC.encode(buf, sent);
            assertEquals(sent, ServerboundGatePacket.STREAM_CODEC.decode(buf));
        }
        assertEquals(nil, ServerboundGatePacket.open(pos, null).filter());
    }

    // ------------------------------------------------------------ the machines

    private static WarpAnchor anchor(UUID owner, WarpAnchorAccess access, UUID network) {
        return new WarpAnchor(UUID.randomUUID(), "Brasshaven", Level.OVERWORLD, new BlockPos(1, 2, 3), owner, "Pilot",
                access, network, true, 6);
    }

    private static RiftGate gate(UUID owner, WarpAnchorAccess access, UUID network) {
        return RiftGate.create(UUID.randomUUID(), Level.OVERWORLD, new BlockPos(4, 5, 6),
                        new RiftGateShape(Direction.Axis.X, 0, 0, 0, 4, 4, 0), owner, "Pilot")
                .withAccess(access).withNetwork(network);
    }

    @Test
    void anAnchorAndAGateKeepTheirNetworkOnTheWireAndOnDisk() {
        for (UUID network : new UUID[]{NETWORK, null}) {
            WarpAnchor anchor = anchor(PILOT, WarpAnchorAccess.NETWORK, network);
            RegistryFriendlyByteBuf buf = buffer();
            WarpAnchor.STREAM_CODEC.encode(buf, anchor);
            assertEquals(anchor, WarpAnchor.STREAM_CODEC.decode(buf));
            assertEquals(anchor, WarpAnchor.load(anchor.save()));

            RiftGate gate = gate(PILOT, WarpAnchorAccess.NETWORK, network);
            buf = buffer();
            RiftGate.STREAM_CODEC.encode(buf, gate);
            assertEquals(gate, RiftGate.STREAM_CODEC.decode(buf));
            assertEquals(gate, RiftGate.load(gate.save()));
        }
    }

    /**
     * Leaving a network must never be what opens a machine to somebody new: one set to Network access
     * comes off as Private, and one that was Public or Private stays what it was.
     */
    @Test
    void aMachineTakenOffItsNetworkNeverOpens() {
        assertEquals(WarpAnchorAccess.PRIVATE, anchor(PILOT, WarpAnchorAccess.NETWORK, NETWORK).withoutNetwork().access());
        assertEquals(WarpAnchorAccess.PUBLIC, anchor(PILOT, WarpAnchorAccess.PUBLIC, NETWORK).withoutNetwork().access());
        assertEquals(WarpAnchorAccess.PRIVATE, anchor(PILOT, WarpAnchorAccess.PRIVATE, NETWORK).withoutNetwork().access());
        assertNull(anchor(PILOT, WarpAnchorAccess.NETWORK, NETWORK).withoutNetwork().network());

        assertEquals(WarpAnchorAccess.PRIVATE, gate(PILOT, WarpAnchorAccess.NETWORK, NETWORK).withoutNetwork().access());
        assertEquals(WarpAnchorAccess.PUBLIC, gate(PILOT, WarpAnchorAccess.PUBLIC, NETWORK).withoutNetwork().access());
        assertNull(gate(PILOT, WarpAnchorAccess.NETWORK, NETWORK).withoutNetwork().network());
    }

    /** A member put out takes their own gates off the network and nobody else's; a disbanding takes them all. */
    @Test
    void gatesComeOffANetworkByOwnerOrAllTogether() {
        RiftGateRegistry registry = new RiftGateRegistry();
        RiftGate pilots = gate(PILOT, WarpAnchorAccess.NETWORK, NETWORK);
        RiftGate mates = gate(MATE, WarpAnchorAccess.NETWORK, NETWORK);
        RiftGate elsewhere = gate(MATE, WarpAnchorAccess.NETWORK, UUID.randomUUID());
        registry.register(pilots);
        registry.register(mates);
        registry.register(elsewhere);

        List<RiftGate> off = registry.detach(g -> NETWORK.equals(g.network()) && g.isOwnedBy(MATE));

        assertEquals(List.of(mates.id()), off.stream().map(RiftGate::id).toList());
        assertNull(registry.byId(mates.id()).network());
        assertEquals(WarpAnchorAccess.PRIVATE, registry.byId(mates.id()).access());
        assertEquals(NETWORK, registry.byId(pilots.id()).network(), "the owner's gate came off with the member's");
        assertEquals(elsewhere, registry.byId(elsewhere.id()), "a gate on another network was touched");

        assertEquals(1, registry.detach(g -> NETWORK.equals(g.network())).size());
        assertTrue(registry.detach(g -> NETWORK.equals(g.network())).isEmpty());
        assertFalse(registry.byId(pilots.id()).access() == WarpAnchorAccess.NETWORK);
    }

    /** A gate from before gates had owners is anybody's; one with an owner is its owner's. */
    @Test
    void aGateRemembersItsOwnerAndAnOldOneHasNone() {
        RiftGate owned = gate(PILOT, WarpAnchorAccess.PUBLIC, null);
        assertTrue(owned.isOwnedBy(PILOT));
        assertFalse(owned.isOwnedBy(MATE));
        assertEquals(owned, RiftGate.load(owned.save()));

        RiftGate old = gate(null, WarpAnchorAccess.PUBLIC, null);
        assertNull(RiftGate.load(old.save()).owner());
        assertTrue(old.withOwner(MATE, "Mate").isOwnedBy(MATE));
    }
}
