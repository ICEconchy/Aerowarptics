package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Render types this mod needs and the game does not have.
 *
 * <p>Extends {@link RenderStateShard} for the same reason Create's does: the state constants are
 * declared there, and reaching them any other way means copying them.
 */
@OnlyIn(Dist.CLIENT)
public final class AWRenderTypes extends RenderStateShard {

    /**
     * The surface of a rift: opaque, and it writes depth.
     *
     * <p>That is the entire trick behind an airship disappearing into an aperture. The surface is
     * drawn after the world's blocks, so it is depth-tested against them - anything in front of the
     * aperture survives, and anything behind it is painted over and gone. A hull flying through is
     * therefore genuinely occluded, slice by slice, with no shader work and nothing to go wrong on
     * someone else's graphics driver.
     *
     * <p>Two-sided, because a rift is a hole rather than a pane and people watch them from both ends.
     */
    public static final RenderType RIFT_MEMBRANE = RenderType.create(
            "aerowarptics_rift_membrane",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            2048,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .createCompositeState(false));

    /**
     * Projected light: translucent, unlit, and it does not write depth.
     *
     * <p>Used for the Astrolabe's terrain hologram. Not writing depth is what lets the far side of the
     * relief show faintly through the near side, which is most of what makes a stack of coloured quads
     * read as a projection rather than as a small painted model of a hill.
     */
    public static final RenderType HOLOGRAM = RenderType.create(
            "aerowarptics_hologram",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            2048,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    /**
     * Broken space: translucent, unlit, and it does not write depth.
     *
     * <p>Deliberately not the membrane. A shard is a fragment catching light, not an occluder - drawn
     * opaque it would punch depth holes in the sky around the aperture and hide the very rift it came
     * off. Not writing depth also means the pieces blend into each other in any order, which matters
     * when fifty of them are tumbling through the same few metres.
     */
    public static final RenderType RIFT_SHARD = RenderType.create(
            "aerowarptics_rift_shard",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            2048,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    private AWRenderTypes() {
        super("aerowarptics", () -> {
        }, () -> {
        });
    }
}
