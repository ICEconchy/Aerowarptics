package uk.co.iceconchy.aerowarptics.warpnet;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Who is in which network - the one question a destination has to ask before it can say who may use it.
 *
 * <p>An interface, and this small, so that an anchor or a gate can be asked about a player without a
 * server behind it: {@link WarpNetworkRegistry} answers in the game and a test answers with a lambda.
 */
@FunctionalInterface
public interface Membership {

    /** Nobody is in anything. What a destination is asked against where there are no networks at all. */
    Membership NOBODY = (network, player) -> false;

    /**
     * Whether a player belongs to a network, as its owner or as a member.
     *
     * @param network the network's id; {@code null}, or an id no network has, is a network nobody is in -
     *                so a destination left pointing at a disbanded network closes rather than opens
     */
    boolean isMember(@Nullable UUID network, UUID player);
}
