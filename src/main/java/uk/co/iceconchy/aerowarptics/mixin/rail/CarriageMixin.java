package uk.co.iceconchy.aerowarptics.mixin.rail;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import uk.co.iceconchy.aerowarptics.rail.RailGates;

/**
 * Lets a Rift Gate answer first when a carriage reaches a portal.
 *
 * <p>{@code Carriage.travel} makes exactly one call to {@code TravellingPoint.travel}, and hands it a listener that
 * Create asks whenever the point reaches an edge into another dimension. This wraps that listener - found by the
 * call it is passed to, never by the synthetic name of Create's lambda, which moves whenever Create reorders its
 * lambdas - so the gates are asked first and Create's own rule (a carriage in at most two dimensions) is still
 * asked after. See {@link RailGates}.
 *
 * <p>Nothing else lives here, and nothing may: classes in a mixin package cannot be loaded as ordinary classes.
 */
@Mixin(Carriage.class)
public abstract class CarriageMixin {

    @ModifyArg(method = "travel", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/trains/entity/TravellingPoint;travel(Lcom/simibubi/create/content/trains/graph/TrackGraph;DLcom/simibubi/create/content/trains/entity/TravellingPoint$ITrackSelector;Lcom/simibubi/create/content/trains/entity/TravellingPoint$IEdgePointListener;Lcom/simibubi/create/content/trains/entity/TravellingPoint$ITurnListener;Lcom/simibubi/create/content/trains/entity/TravellingPoint$IPortalListener;)D"),
            index = 5)
    private TravellingPoint.IPortalListener aerowarptics$askTheGates(TravellingPoint.IPortalListener original) {
        return RailGates.portalListener((Carriage) (Object) this, original);
    }
}
