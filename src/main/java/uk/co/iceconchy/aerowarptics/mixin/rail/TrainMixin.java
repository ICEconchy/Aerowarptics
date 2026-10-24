package uk.co.iceconchy.aerowarptics.mixin.rail;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.trains.entity.Navigation;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TrainStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import uk.co.iceconchy.aerowarptics.rail.RailGates;

/**
 * Lets a train waiting at a Rift Gate keep its route.
 *
 * <p>When any of a train's carriages is blocked, {@code Train.tick} stops it dead, cancels its navigation and tells
 * its owner it has reached the end of its track. For a train at a shut gate that is wrong twice over: a train with
 * its navigation cancelled searches for a new path on the very next tick, finds the one through the gate, is blocked
 * again, and loops; and it is not at the end of anything. So, only on a tick the gates held it, the first of the two
 * {@code cancelNavigation} calls - the one in the blocked branch; the second is the high-stress derailment - and
 * the end-of-track report are skipped. The train still stops: Create sets its speed to nought before either call.
 *
 * <p>Nothing else lives here, and nothing may: classes in a mixin package cannot be loaded as ordinary classes.
 */
@Mixin(Train.class)
public abstract class TrainMixin {

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/trains/entity/Navigation;cancelNavigation()V", ordinal = 0))
    private void aerowarptics$keepRouteAtAGate(Navigation navigation, Operation<Void> original) {
        if (!RailGates.waitingAtGate((Train) (Object) this)) {
            original.call(navigation);
        }
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/trains/entity/TrainStatus;endOfTrack()V"))
    private void aerowarptics$notTheEndOfTheTrack(TrainStatus status, Operation<Void> original) {
        if (!RailGates.waitingAtGate((Train) (Object) this)) {
            original.call(status);
        }
    }
}
