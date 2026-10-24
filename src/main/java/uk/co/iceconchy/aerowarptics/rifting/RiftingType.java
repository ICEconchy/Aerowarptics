package uk.co.iceconchy.aerowarptics.rifting;

import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.foundation.recipe.RecipeApplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.registry.AWBlocks;
import uk.co.iceconchy.aerowarptics.registry.AWParticles;
import uk.co.iceconchy.aerowarptics.registry.AWRecipeTypes;

import java.util.List;

/**
 * Rifting: Create's fan processing, through a standing rift.
 *
 * <p>Beside washing, smoking, blasting and haunting, and working exactly as they do - an encased fan blows
 * items through a catalyst and the air past it carries the process on. The catalyst here is a rift that
 * is already open and stays open, which is the whole of the rule it carries: <em>anything that tears a
 * rift is built with a lens that has seen one.</em> Three things hold one open:
 * <ul>
 *   <li>a <b>Rift Fissure</b>, free for as long as it is left open - which is what turns a found scar
 *       into the best industrial site in the world;</li>
 *   <li>a <b>Rift Brazier</b>, lit by redstone, burning essence by the second;</li>
 *   <li>an open gate's <b>Rift Portal</b> pane, paid for by the gate's own upkeep. Items blown into the
 *       pane are posted through to the far gate, which is the gate doing its job; a depot or belt standing
 *       behind it is in the rifted air.</li>
 * </ul>
 *
 * <p>A pool of Rift Essence is deliberately not one. It would be a single line - fluids are passable - but
 * a placed bucket would be a free catalyst forever, and it can be scooped back up.
 *
 * <p>Registered, not merely constructed: {@code FanProcessing} writes the type's registry key onto an item
 * entity partway through processing it, and an unregistered type there is an exception.
 */
public class RiftingType implements FanProcessingType {

    /** Between haunting (300) and smoking (200): a rift beside a campfire is still a rift. */
    public static final int PRIORITY = 250;

    /** Ticks between two hops of the same creature, however long it stands in the stream. */
    private static final int HOP_INTERVAL = 30;

    private static final String HOP_TAG = "aerowarptics:rift_hop";

    @Override
    public boolean isValidAt(Level level, BlockPos pos) {
        return isCatalyst(level.getBlockState(pos));
    }

    /** Whether a block holds a rift open. Asked by the fan, and by the Ponder and JEI for what to show. */
    public static boolean isCatalyst(BlockState state) {
        // A fissure a Rift Wellhead holds rifts nothing: harnessing one is a trade against the free catalyst.
        if (state.is(AWBlocks.RIFT_FISSURE.get())) {
            return !state.getValue(uk.co.iceconchy.aerowarptics.fissure.RiftFissureBlock.HARNESSED);
        }
        if (state.is(AWBlocks.RIFT_PORTAL.get())) {
            return true;
        }
        return state.is(AWBlocks.RIFT_BRAZIER.get()) && state.getValue(RiftBrazierBlock.LIT);
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public boolean canProcess(ItemStack stack, Level level) {
        return AWRecipeTypes.RIFTING.find(new SingleRecipeInput(stack), level).isPresent();
    }

    @Nullable
    @Override
    public List<ItemStack> process(ItemStack stack, Level level) {
        // Not told which catalyst the item passed, which is why a brazier burns by the second rather than
        // by the item: there is no way to charge the one that did the work.
        return AWRecipeTypes.RIFTING.<SingleRecipeInput, RiftingRecipe>find(new SingleRecipeInput(stack), level)
                .map(RecipeHolder::value)
                .map(recipe -> RecipeApplier.applyRecipeOn(level, stack, recipe, true))
                .orElse(null);
    }

    @Override
    public void spawnProcessingParticles(Level level, Vec3 pos) {
        if (level.random.nextInt(6) == 0) {
            level.addParticle(AWParticles.RIFT_SPARK.get(), pos.x, pos.y + 0.3D, pos.z,
                    (level.random.nextDouble() - 0.5D) * 0.04D, 0.02D, (level.random.nextDouble() - 0.5D) * 0.04D);
        }
    }

    @Override
    public void morphAirFlow(AirFlowParticleAccess particleAccess, RandomSource random) {
        // The rift's own violet, darkening towards the void the fissure is a window into.
        particleAccess.setColor(mix(0x2A0F4A, 0x9B6BFF, random.nextFloat()));
        particleAccess.setAlpha(0.8F);
        if (random.nextFloat() < 1.0F / 48.0F) {
            particleAccess.spawnExtraParticle(AWParticles.RIFT_SPARK.get(), 0.15F);
        }
    }

    @Override
    public void affectEntity(Entity entity, Level level) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof LivingEntity living)) {
            return;
        }
        if (!AWConfig.RIFTING_HOPS.get() || !RiftHop.mayHop(living)) {
            return;
        }
        long now = server.getGameTime();
        long last = living.getPersistentData().getLong(HOP_TAG);
        if (now - last < HOP_INTERVAL && now >= last) {
            return;
        }
        living.getPersistentData().putLong(HOP_TAG, now);
        RiftHop.hop(server, living, AWConfig.RIFTING_HOP_RANGE.get());
    }

    private static int mix(int from, int to, float t) {
        int r = (int) Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
        return (r << 16) | (g << 8) | b;
    }
}
