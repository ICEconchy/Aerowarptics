package uk.co.iceconchy.aerowarptics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.chute.RiftChute;
import uk.co.iceconchy.aerowarptics.chute.RiftChuteRegistry;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That a chute's bindings stay consistent at both ends.
 *
 * <p>Written after a real bug: a broken chute went on haunting every other chute's list forever,
 * because the block never told the block entity it had been destroyed. The deregistration itself is
 * a block-lifecycle problem and cannot be unit-tested, but everything the registry does once it is
 * told can be - and a dangling partner is the shape that bug took, so it is pinned here.
 */
class RiftChuteRegistryTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"));

    private static RiftChute chute(RiftChuteRegistry registry, String name, int x) {
        RiftChute chute = RiftChute.create(UUID.randomUUID(), OVERWORLD, new BlockPos(x, 64, 0), null)
                .withName(name);
        registry.register(chute);
        return chute;
    }

    /** A pair is symmetric. A chute pointing at a partner that does not point back is a half-pipe. */
    @Test
    void bindingWritesBothEnds() {
        RiftChuteRegistry registry = new RiftChuteRegistry();
        RiftChute a = chute(registry, "a", 0);
        RiftChute b = chute(registry, "b", 10);

        assertTrue(registry.bind(a.id(), b.id()));
        assertEquals(b.id(), registry.byId(a.id()).partner(), "the near end was not bound");
        assertEquals(a.id(), registry.byId(b.id()).partner(), "the far end was not bound");
    }

    /** Unbinding clears both ends too, or the far chute keeps sending into nothing. */
    @Test
    void unbindingClearsBothEnds() {
        RiftChuteRegistry registry = new RiftChuteRegistry();
        RiftChute a = chute(registry, "a", 0);
        RiftChute b = chute(registry, "b", 10);
        registry.bind(a.id(), b.id());

        assertTrue(registry.bind(a.id(), null));
        assertNull(registry.byId(a.id()).partner(), "the near end stayed bound");
        assertNull(registry.byId(b.id()).partner(), "the far end stayed bound");
    }

    /**
     * Re-pairing releases whatever either chute was already paired with.
     *
     * <p>A chute may only be in one pair, so binding A to C has to let B go - otherwise B is left
     * pointing at a chute that is no longer pointing back, which is exactly the dangling state this
     * class exists to rule out.
     */
    @Test
    void rebindingReleasesTheOldPartner() {
        RiftChuteRegistry registry = new RiftChuteRegistry();
        RiftChute a = chute(registry, "a", 0);
        RiftChute b = chute(registry, "b", 10);
        RiftChute c = chute(registry, "c", 20);
        registry.bind(a.id(), b.id());

        registry.bind(a.id(), c.id());
        assertEquals(c.id(), registry.byId(a.id()).partner());
        assertEquals(a.id(), registry.byId(c.id()).partner());
        assertNull(registry.byId(b.id()).partner(), "the abandoned chute is still pointing at a pair");
    }

    /** This is the bug, in registry terms: removing a chute must not leave its partner dangling. */
    @Test
    void removingAChuteUnbindsWhateverPointedAtIt() {
        RiftChuteRegistry registry = new RiftChuteRegistry();
        RiftChute a = chute(registry, "a", 0);
        RiftChute b = chute(registry, "b", 10);
        registry.bind(a.id(), b.id());

        assertTrue(registry.remove(b.id()));
        assertNull(registry.byId(b.id()), "the removed chute is still listed");
        assertNull(registry.byId(a.id()).partner(), "its partner is still bound to a chute that is gone");
    }

    /** A chute cannot be paired with itself, however the request arrives. */
    @Test
    void aChuteCannotPairWithItself() {
        RiftChuteRegistry registry = new RiftChuteRegistry();
        RiftChute a = chute(registry, "a", 0);
        assertFalse(registry.bind(a.id(), a.id()));
        assertNull(registry.byId(a.id()).partner());
    }

    /** Binding to something that does not exist changes nothing rather than half-applying. */
    @Test
    void bindingToAMissingChuteIsRefused() {
        RiftChuteRegistry registry = new RiftChuteRegistry();
        RiftChute a = chute(registry, "a", 0);
        RiftChute b = chute(registry, "b", 10);
        registry.bind(a.id(), b.id());

        assertFalse(registry.bind(a.id(), UUID.randomUUID()));
        assertEquals(b.id(), registry.byId(a.id()).partner(), "a refused bind still broke the old pair");
    }

    /** Names are unique, so a binding list cannot show two rows a player is unable to tell apart. */
    @Test
    void twoChutesCannotShareAName() {
        RiftChuteRegistry registry = new RiftChuteRegistry();
        RiftChute a = chute(registry, "", 0);
        RiftChute b = chute(registry, "", 10);

        assertTrue(registry.rename(a.id(), "dock"));
        assertFalse(registry.rename(b.id(), "dock"), "two chutes were allowed the same name");
        assertTrue(registry.rename(b.id(), "hold"));
    }

    /** An unnamed chute still has something to show in a list. */
    @Test
    void anUnnamedChuteFallsBackToItsId() {
        RiftChuteRegistry registry = new RiftChuteRegistry();
        RiftChute a = chute(registry, "", 0);
        assertFalse(registry.byId(a.id()).displayName().isBlank());
    }
}
