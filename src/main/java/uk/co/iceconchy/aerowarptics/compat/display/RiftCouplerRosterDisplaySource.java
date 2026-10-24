package uk.co.iceconchy.aerowarptics.compat.display;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import uk.co.iceconchy.aerowarptics.flotilla.CouplerReadings;
import uk.co.iceconchy.aerowarptics.flotilla.RiftCouplerBlockEntity;
import uk.co.iceconchy.aerowarptics.registry.AWBlockEntities;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.ArrayList;
import java.util.List;

/**
 * A flotilla's roster, one ship to a line: the flagship first, then each escort and how it stands.
 *
 * <p>The one source in this mod that writes more than one line, and on purpose: {@link AWDisplaySource}
 * explains why the rest keep to one value per link, and a roster is the thing that rule cannot express -
 * its length is the flotilla's. So it is its own source, rather than a mode of the coupler's that would
 * break the rule for every other mode beside it. On a Display Board each ship takes a row; on a sign,
 * as many as fit; on a nixie tube, the first, which is the flagship.
 */
public class RiftCouplerRosterDisplaySource extends DisplaySource {

    /** The registry path. Baked into every configured link's save data; do not rename. */
    public static final String SOURCE_ID = "rift_coupler_roster";

    RiftCouplerRosterDisplaySource() {
    }

    public String sourceId() {
        return SOURCE_ID;
    }

    public BlockEntityType<?> blockEntityType() {
        return AWBlockEntities.RIFT_COUPLER.get();
    }

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        BlockEntity be = context.getSourceBlockEntity();
        if (!(be instanceof RiftCouplerBlockEntity coupler) || !(coupler.getLevel() instanceof ServerLevel level)) {
            return EMPTY;
        }
        CouplerReadings readings = CouplerReadings.of(level, coupler);
        if (readings.roster().isEmpty()) {
            return List.of(AWLang.translate("display_source.rift_coupler.no_flotilla").component());
        }
        List<MutableComponent> lines = new ArrayList<>();
        for (CouplerReadings.Line line : readings.roster()) {
            String name = line.name().isBlank()
                    ? AWLang.translate("gui.rift_navigation.unnamed_ship").string() : line.name();
            lines.add(AWLang.translate("display_source.rift_coupler_roster.line", name,
                    AWLang.translate(line.status()).string()).component());
        }
        return lines;
    }

    @Override
    public List<List<MutableComponent>> provideFlapDisplayText(DisplayLinkContext context, DisplayTargetStats stats) {
        List<List<MutableComponent>> rows = new ArrayList<>();
        for (MutableComponent line : provideText(context, stats)) {
            rows.add(List.of(line));
        }
        return rows;
    }
}
