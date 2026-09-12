package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
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
     * A plain white sprite for the glow passes.
     *
     * <p>There is nothing to see in it, and that is the point - a rift's colour lives entirely in its
     * vertices, so the sampler must contribute nothing. It exists because the render types below
     * borrow the beacon beam's shader, and that shader samples a texture.
     */
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(
            "aerowarptics", "textures/misc/rift_glow.png");

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
     * The fire burning on and around a rift: additive, and visible from both sides.
     *
     * <p>Drawn through the <em>beacon beam</em> shader rather than a plain position-colour one, which
     * looks like an odd choice until a shader pack is installed. Iris routes geometry to one of the
     * pack's programs by the core shader it was drawn with, and untextured position-colour geometry
     * lands in {@code gbuffers_basic} - a program some packs, Complementary among them, run full scene
     * lighting and shadowing through. A rift is not a lit surface, so being lit as one turned a smooth
     * additive glow into a fan of flat shaded triangles: every facet of the geometry visible, none of
     * it blending. The beacon beam program is the one every pack agrees is emissive and unlit, because
     * that is the only thing vanilla ever uses it for.
     *
     * <p>Deliberately not {@link RenderType#lightning()}, which was the obvious thing to reach for and
     * is wrong here in three ways. It has no {@code NO_CULL}, so every quad is back-face culled from
     * one side - and an aperture is a hole in space that people stand on both sides of, so half the
     * time the fire, the rim, the cracks and the seal simply were not drawn. It also writes depth,
     * which is not a thing a glow should do to the world behind it, and it renders into the weather
     * buffer, which is somebody else's framebuffer.
     */
    public static final RenderType RIFT_FIRE = RenderType.create(
            "aerowarptics_rift_fire",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            2048,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_BEACON_BEAM_SHADER)
                    .setTextureState(new TextureStateShard(GLOW, false, false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
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
     * <p>On the beacon beam shader for the same reason the fire is - see {@link #RIFT_FIRE}.
     *
     * <p>Deliberately not the membrane. A shard is a fragment catching light, not an occluder - drawn
     * opaque it would punch depth holes in the sky around the aperture and hide the very rift it came
     * off. Not writing depth also means the pieces blend into each other in any order, which matters
     * when fifty of them are tumbling through the same few metres.
     */
    public static final RenderType RIFT_SHARD = RenderType.create(
            "aerowarptics_rift_shard",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            2048,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_BEACON_BEAM_SHADER)
                    .setTextureState(new TextureStateShard(GLOW, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    /** The drops a Rift Storm rains down. Coloured in the texture - see {@code tools/rift_rain_texture.py}. */
    private static final ResourceLocation RIFT_RAIN_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "aerowarptics", "textures/environment/rift_rain.png");

    /**
     * A Rift Storm's rain: vanilla rain's own recipe, with a different texture.
     *
     * <p>The particle shader and the lightmap, as vanilla's weather pass uses, so the rain is shaded
     * by the light where it falls and a shader pack treats it as weather rather than as a glow. It
     * does not write depth, so it never hides the rain behind it, and it names no output target: it is
     * drawn at the weather stage, where vanilla has already bound the weather target under Fabulous
     * graphics, and a render type that bound one of its own would unbind it again on the way out -
     * from underneath the world border vanilla draws next.
     */
    public static final RenderType RIFT_RAIN = RenderType.create(
            "aerowarptics_rift_rain",
            DefaultVertexFormat.PARTICLE,
            VertexFormat.Mode.QUADS,
            4096,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(new ShaderStateShard(net.minecraft.client.renderer.GameRenderer::getParticleShader))
                    .setTextureState(new TextureStateShard(RIFT_RAIN_TEXTURE, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(LIGHTMAP)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    /**
     * Light hung in the sky itself: additive, and tested against nothing.
     *
     * <p>Drawn straight after the sky and before any terrain, so there is no depth to test against -
     * and nothing needs one. Everything drawn afterwards simply paints over it, which is exactly how
     * the sun and the stars end up behind the hills. Plain position and colour, because the beacon
     * beam shader {@link #RIFT_FIRE} uses applies fog, and fog at this stage is the sky's: fogged
     * additive light would lay a glowing band along the horizon.
     */
    public static final RenderType RIFT_AURORA = RenderType.create(
            "aerowarptics_rift_aurora",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            1024,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    private AWRenderTypes() {
        super("aerowarptics", () -> {
        }, () -> {
        });
    }
}
