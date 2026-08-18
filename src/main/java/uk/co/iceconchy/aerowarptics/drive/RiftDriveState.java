package uk.co.iceconchy.aerowarptics.drive;

import net.minecraft.util.StringRepresentable;

/**
 * The Rift Drive's state machine.
 *
 * <p>The server owns the current state; clients receive it and use it only to pick an animation and
 * a colour. Legal transitions are declared here rather than scattered through the tick handler so
 * that "cannot warp while warping", "cannot warp without charge" and friends are checkable in
 * isolation - and are unit tested as such.
 *
 * <pre>
 *   IDLE -> CHARGING -> CHARGED -> DESTINATION_SELECTED -> STABILIZING -> WARPING -> ARRIVING
 *                                                                                      |
 *                                                            COOLDOWN &lt;-----------------+
 *                                                                |
 *                                                              IDLE
 * </pre>
 * Any state may drop to {@link #ERROR}, and {@link #ERROR} recovers to {@link #IDLE}.
 */
public enum RiftDriveState implements StringRepresentable {

    /** Not turning, or turning too slowly to matter. */
    IDLE("idle"),
    /** Accumulating charge from rotational force. */
    CHARGING("charging"),
    /** Full charge, waiting for a destination. */
    CHARGED("charged"),
    /** A destination has been accepted; the drive is committing to it. */
    DESTINATION_SELECTED("destination_selected"),
    /** Rings aligning, destination locking. */
    STABILIZING("stabilizing"),
    /** Rift open, airship inside the corridor. */
    WARPING("warping"),
    /** Airship has been moved; the drive is confirming the arrival. */
    ARRIVING("arriving"),
    /** Recovering. Cannot start another warp. */
    COOLDOWN("cooldown"),
    /** Something went wrong. Recovers to IDLE on its own. */
    ERROR("error");

    private final String name;

    RiftDriveState(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** Name of the animation in {@code assets/aerowarptics/animations/rift_drive.animation.json}. */
    public String animation() {
        return "animation.rift_drive." + name;
    }

    /** Namespace-relative lang key, resolved through {@code AWLang}. */
    public String translationKey() {
        return "drive.state." + name;
    }

    /** States in which the drive is committed to a warp and must not accept new orders. */
    public boolean isSequenceRunning() {
        return this == DESTINATION_SELECTED || this == STABILIZING || this == WARPING || this == ARRIVING;
    }

    /** States from which a pilot may still call the warp off. */
    public boolean isCancellable() {
        return this == DESTINATION_SELECTED || this == STABILIZING;
    }

    /** Only a fully charged, idle drive may be given a destination. */
    public boolean acceptsDestination() {
        return this == CHARGED;
    }

    /** Whether the drive should keep gaining charge in this state. */
    public boolean accumulatesCharge() {
        return this == IDLE || this == CHARGING;
    }

    /**
     * Whether the charge cycle runs at all here.
     *
     * <p>Wider than {@link #accumulatesCharge()}: a fully charged drive gains nothing but still
     * bleeds when the shaft stops, whereas a warping or cooling drive is left completely alone.
     */
    public boolean participatesInCharging() {
        return accumulatesCharge() || this == CHARGED;
    }

    /**
     * Whether {@code this -> next} is a legal move.
     *
     * <p>Everything may fall into {@link #ERROR}; nothing may re-enter a state it is already in
     * except as a no-op, which callers treat as "no change" rather than a transition.
     */
    public boolean canTransitionTo(RiftDriveState next) {
        if (next == ERROR) {
            return this != ERROR;
        }
        return switch (this) {
            case IDLE -> next == CHARGING;
            case CHARGING -> next == CHARGED || next == IDLE;
            case CHARGED -> next == DESTINATION_SELECTED || next == CHARGING || next == IDLE;
            case DESTINATION_SELECTED -> next == STABILIZING || next == CHARGED || next == COOLDOWN;
            case STABILIZING -> next == WARPING || next == CHARGED || next == COOLDOWN;
            case WARPING -> next == ARRIVING || next == COOLDOWN;
            case ARRIVING -> next == COOLDOWN;
            case COOLDOWN -> next == IDLE;
            case ERROR -> next == IDLE;
        };
    }

    public static RiftDriveState byIndex(int index) {
        RiftDriveState[] values = values();
        return index >= 0 && index < values.length ? values[index] : IDLE;
    }
}
