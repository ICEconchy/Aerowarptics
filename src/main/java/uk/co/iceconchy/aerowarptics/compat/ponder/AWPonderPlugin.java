package uk.co.iceconchy.aerowarptics.compat.ponder;

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import uk.co.iceconchy.aerowarptics.AeroWarptics;

/**
 * This mod's entry in Create's Ponder index.
 *
 * <p>Ponder is the only part of this addon whose job is to be read rather than run: holding [W] over
 * any of these items plays a scene explaining it. That matters more here than in most addons,
 * because nothing in the mod is craftable-and-obvious - a Rift Gate is forty blocks of wall that does
 * nothing until it is powered, dialled and paired, and a player who places one and waits has no way
 * to find that out.
 *
 * <p>Registered from {@link uk.co.iceconchy.aerowarptics.client.AWClientSetup} during client setup,
 * which is where Create adds its own. Everything is gathered later, at load complete, so ordering
 * against Create does not matter - only that this runs before then.
 */
public class AWPonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return AeroWarptics.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        AWPonderScenes.register(helper);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        AWPonderTags.register(helper);
    }
}
