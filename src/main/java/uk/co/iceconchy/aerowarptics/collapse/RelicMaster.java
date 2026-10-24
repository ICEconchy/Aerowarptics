package uk.co.iceconchy.aerowarptics.collapse;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * A relic machine's master, as its floor knows it: the thing whose load the input turning it has to carry, and itself
 * one of the inputs - the one a shaft turns from below - see {@link RelicFloor}.
 */
public interface RelicMaster {

    /** The stress the machine asks for now, per RPM - counted on whichever input is turning it. */
    float relicImpact();

    /**
     * The speed of the line run in under the master, as Create has it - nothing while that line is overstressed. The
     * master's own {@code getSpeed()} is the machine's, which may be another input's.
     */
    float lineSpeed();

    /** What Create's goggles say of the master as a kinetic block - its load on the line below, where it is the feeder. */
    boolean lineGoggles(List<Component> tooltip, boolean isPlayerSneaking);

    /** Has the line below asked again, on the master's next tick, what the master costs it - see {@link RelicFloor#changed}. */
    void retell();
}
