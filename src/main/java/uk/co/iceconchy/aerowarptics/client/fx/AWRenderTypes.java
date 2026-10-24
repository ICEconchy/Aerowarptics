package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;
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
     * The surface of an Ender rift: the End portal's own starfield, opaque, and it writes depth.
     *
     * <p>Everything {@link #RIFT_MEMBRANE} is, bar the shader - the same occluder painted with vanilla's
     * End portal program and its two textures instead of a colour, so a hull is hidden by it in exactly
     * the same way. Because that program projects its textures from the screen, a face, a bore and the
     * patches that bleed in ahead of them all show one continuous sky however they overlap.
     *
     * <p>Not {@link RenderType#endPortal()} itself, which is the one thing that would be simpler. It
     * culls back faces, and a rift is watched from both ends: half its viewers would see no face at
     * all, and the hull behind it.
     *
     * <p>Position only. The face and bore are written by {@code RiftEffectManager.solidVertex}, the same
     * writer the membrane uses, which also writes a colour - and that is safe, not an accident waiting to
     * crash: {@code BufferBuilder} skips any element its format does not have. The crash
     * {@code RiftVertexFormatTest} guards against is the opposite case, a format asking for an element
     * nobody wrote. That test also pins this type to plain position, which is the only format for which
     * the reasoning holds.
     */
    public static final RenderType RIFT_END_SKY = RenderType.create(
            "aerowarptics_rift_end_sky",
            DefaultVertexFormat.POSITION,
            VertexFormat.Mode.QUADS,
            2048,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_END_PORTAL_SHADER)
                    .setTextureState(MultiTextureStateShard.builder()
                            .add(TheEndPortalRenderer.END_SKY_LOCATION, false, false)
                            .add(TheEndPortalRenderer.END_PORTAL_LOCATION, false, false)
                            .build())
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
     * {@link #HOLOGRAM} seen through whatever is in front of it: no depth test at all.
     *
     * <p>For a guide that has to be found as well as read - a Rift Plotter's dock stands in the air behind terrain and
     * over water as often as not. Drawn faint, under the depth-tested pass, so it is a ghost where it is hidden and
     * solid where it is not.
     */
    public static final RenderType HOLOGRAM_THROUGH = RenderType.create(
            "aerowarptics_hologram_through",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            2048,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setDepthTestState(NO_DEPTH_TEST)
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

    /**
     * A Rift Storm's rain: vanilla rain's own recipe, and vanilla's own drops, recoloured - see
     * {@link RiftRainTexture}, which must be registered before this is first drawn.
     *
     * <p>The particle shader and the lightmap, as vanilla's weather pass uses, so the rain is shaded
     * by the light where it falls and a shader pack treats it as weather rather than as a glow. It
     * does not write depth, so it never hides the rain behind it, and it names no output target: it is
     * drawn at the weather stage, where vanilla has already bound the weather target under Fabulous
     * graphics, and a render type that bound one of its own would unbind it again on the way out -
     * from underneath the world border vanilla draws next. Under Fabulous graphics it is
     * {@link #RIFT_RAIN_LAYERED} instead.
     */
    public static final RenderType RIFT_RAIN = rain("aerowarptics_rift_rain", COLOR_WRITE);

    /**
     * {@link #RIFT_RAIN} for Fabulous graphics, where it does write depth - as vanilla's rain does there.
     *
     * <p>Fabulous draws the weather into a layer of its own, starting from an empty depth buffer, and merges
     * the layers afterwards by the depth each one holds. Rain that wrote none sat at the far plane in that
     * merge: behind every cloud, and behind every hill and hull too, so a storm's rain showed only against
     * open sky.
     */
    public static final RenderType RIFT_RAIN_LAYERED = rain("aerowarptics_rift_rain_layered", COLOR_DEPTH_WRITE);

    private static RenderType rain(String name, WriteMaskStateShard writes) {
        return RenderType.create(
                name,
                DefaultVertexFormat.PARTICLE,
                VertexFormat.Mode.QUADS,
                4096,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(new ShaderStateShard(net.minecraft.client.renderer.GameRenderer::getParticleShader))
                        .setTextureState(new TextureStateShard(RiftRainTexture.LOCATION, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setLightmapState(LIGHTMAP)
                        .setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(writes)
                        .createCompositeState(false));
    }

    /**
     * Light hung in the sky itself: additive, tested against depth, and writing none.
     *
     * <p>Hung just inside the far plane, beyond anything the world draws, so the depth test passes only where
     * nothing has been drawn at all - the sky - and every hill and hull stands in front of it the way they do
     * in front of the stars. It is drawn after the clouds, against the depth the world had before them, which
     * is what keeps the clouds from painting over it too - see {@code CloudlessDepth}. Plain position and
     * colour, because the beacon beam shader {@link #RIFT_FIRE} uses applies fog, and fogged additive light
     * would lay a glowing band along the horizon.
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
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    /**
     * Shade hung in the sky itself: {@link #RIFT_AURORA} in every way but the blend.
     *
     * <p>The storm's vortex is dark cloud turning overhead, and additive light cannot draw dark - see the
     * note on ink in {@code RiftFurniture}. So this blends by alpha, laying violet-black over whatever sky
     * is behind it; everything else, the distance it hangs at and why it takes no fog, is the aurora's.
     */
    public static final RenderType RIFT_SKY_SHADE = RenderType.create(
            "aerowarptics_rift_sky_shade",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            4096,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    /**
     * Bent space: the frame as it stood a moment ago, redrawn a little way from where it belongs.
     *
     * <p>The one render type here that reads the screen. {@code GravityLens} copies the frame once the
     * rifts' faces and throats are in it, and this draws a disc round a Mostly Dangerous rift out of that
     * copy, each point of it looking somewhere nearer the middle - see {@code LensProfile}.
     *
     * <p>On a shader of the mod's own - one of two, with a gate passage's - because nothing in vanilla both reads a
     * texture and ignores that texture's alpha. A frame's alpha is not a transparency - it is whatever the
     * last thing drawn there left behind - and a lens that believed it would punch holes in the sky. Here
     * only the colour's own alpha counts, which is the feather at the lens's edge.
     *
     * <p>Position, texture and colour, written only by {@code GravityLens.lensVertex} -
     * {@code RiftVertexFormatTest} holds the two to each other. Depth-tested, so whatever stands in front
     * of the rift stays in front of the bending; writing no depth, so everything drawn after it draws as
     * if it were not there.
     */
    public static final RenderType GRAVITY_LENS = RenderType.create(
            "aerowarptics_gravity_lens",
            DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS,
            2048,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(new ShaderStateShard(GravityLens::shader))
                    .setTextureState(new EmptyTextureStateShard(GravityLens::bindCopy, () -> {
                    }))
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
