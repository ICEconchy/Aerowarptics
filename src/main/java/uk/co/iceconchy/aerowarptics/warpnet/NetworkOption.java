package uk.co.iceconchy.aerowarptics.warpnet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * A network as a list offers it: something to filter by, and the name to say it with.
 *
 * <p>Deliberately not the network itself. A list's filter is offered to anybody who can see a destination
 * on that network - a public anchor on one, say - and who is in a network is no business of theirs.
 */
public record NetworkOption(UUID id, String name) {

    private static final int NAME_LENGTH = 32;

    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkOption> STREAM_CODEC = StreamCodec.of(
            (buf, option) -> {
                buf.writeUUID(option.id);
                buf.writeUtf(option.name.length() > NAME_LENGTH ? option.name.substring(0, NAME_LENGTH)
                        : option.name, NAME_LENGTH);
            },
            buf -> new NetworkOption(buf.readUUID(), buf.readUtf(NAME_LENGTH)));

    public NetworkOption {
        name = name == null ? "" : name;
    }
}
