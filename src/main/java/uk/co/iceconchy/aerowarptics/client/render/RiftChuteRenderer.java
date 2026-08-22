package uk.co.iceconchy.aerowarptics.client.render;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import uk.co.iceconchy.aerowarptics.chute.RiftChuteBlockEntity;
import uk.co.iceconchy.aerowarptics.client.model.RiftChuteModel;

/**
 * Draws the Rift Chute's housing, and nothing else.
 *
 * <p>The aperture inside is not part of this model. It is the same rift a Rift Gate tears, held open
 * by {@code RiftEffectManager} from the block entity's client tick and billboarded to face the
 * viewer - so it shatters open and seals shut exactly as a full-sized one does, with none of that
 * duplicated here.
 *
 * <p>Nothing rotates the model either: the housing is open on all four sides and the block has no
 * facing state at all.
 */
@OnlyIn(Dist.CLIENT)
public class RiftChuteRenderer extends GeoBlockRenderer<RiftChuteBlockEntity> {

    public RiftChuteRenderer() {
        super(new RiftChuteModel());
    }
}
