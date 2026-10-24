package uk.co.iceconchy.aerowarptics.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import org.joml.Vector3f;
import uk.co.iceconchy.aerowarptics.AeroWarptics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A full cube whose faces join up with the blocks beside them: the {@code aerowarptics:connected} model loader.
 *
 * <pre>{@code
 * {
 *   "parent": "minecraft:block/block",
 *   "loader": "aerowarptics:connected",
 *   "method": "full",                                 // or "compact" - see ConnectedLayout
 *   "tiles": "aerowarptics:block/ctm/rift_gate_frame", // tiles/0, tiles/1, ...
 *   "connect_with": ["aerowarptics:rift_gate_frame"],  // optional; the block itself if left out
 *   "textures": {"particle": "aerowarptics:block/rift_gate_frame"}
 * }
 * }</pre>
 *
 * <p>Each entry of {@code connect_with} is a block, or {@code {"block": ..., "properties": {...}}} for only those of
 * its states. A formed Rift Wellhead turns its casing and glass into one block with a {@code glass} property, and
 * this is how its glass joins Warp Glass while its casing joins only itself.
 *
 * <p>Every quad a face could ever draw is baked here, once; {@link ConnectedBakedModel} only chooses between them.
 * They are baked by vanilla's own {@link FaceBakery}, from the boxes and the UVs vanilla gives a cube's faces, so
 * the tiles lie on each face the way a plain {@code cube_all} texture would and shade the same way. The
 * {@code render_type} a model names is kept, so Warp Glass still draws in the pass that lets rifts show through it.
 */
@OnlyIn(Dist.CLIENT)
public final class ConnectedGeometry implements IUnbakedGeometry<ConnectedGeometry> {

    /** The loader's name in a model's {@code "loader"}. */
    public static final ResourceLocation LOADER = AeroWarptics.id("connected");

    private final boolean full;
    private final ResourceLocation tiles;
    private final List<ConnectedBakedModel.Match> connectWith;

    private ConnectedGeometry(boolean full, ResourceLocation tiles, List<ConnectedBakedModel.Match> connectWith) {
        this.full = full;
        this.tiles = tiles;
        this.connectWith = connectWith;
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> sprites,
                           ModelState state, ItemOverrides overrides) {
        int count = full ? ConnectedLayout.FULL_TILES : ConnectedLayout.COMPACT_TILES;
        TextureAtlasSprite[] tileSprites = new TextureAtlasSprite[count];
        for (int tile = 0; tile < count; tile++) {
            tileSprites[tile] = sprites.apply(new Material(TextureAtlas.LOCATION_BLOCKS, tiles.withSuffix("/" + tile)));
        }
        TextureAtlasSprite particle = context.hasMaterial("particle")
                ? sprites.apply(context.getMaterial("particle")) : tileSprites[0];

        FaceBakery bakery = new FaceBakery();
        // [face][part][tile]: one part, the whole face, for full; four quarters for compact.
        BakedQuad[][][] quads = new BakedQuad[6][][];
        for (Direction face : Direction.values()) {
            int parts = full ? 1 : 4;
            quads[face.get3DDataValue()] = new BakedQuad[parts][count];
            for (int part = 0; part < parts; part++) {
                float[][] box = full ? new float[][]{{0, 0, 0}, {16, 16, 16}} : quarter(face, part);
                for (int tile = 0; tile < count; tile++) {
                    BlockElementFace element = new BlockElementFace(face, -1, "", new BlockFaceUV(uvs(face, box), 0));
                    quads[face.get3DDataValue()][part][tile] = bakery.bakeQuad(
                            new Vector3f(box[0][0], box[0][1], box[0][2]), new Vector3f(box[1][0], box[1][1], box[1][2]),
                            element, tileSprites[tile], face, state, null, true);
                }
            }
        }
        return new ConnectedBakedModel(full, quads, connectWith, particle, context.useAmbientOcclusion(),
                context.isGui3d(), context.useBlockLight(), context.getTransforms(),
                context.getRenderType(context.getRenderTypeHint()), overrides);
    }

    /**
     * The box of one quarter of a face, as a cube's sub-box: {@link ConnectedBakedModel#QUARTERS}' order - up-left,
     * up-right, down-left, down-right as the face is seen. Halved along the face's own right and up, which
     * {@link ConnectedBakedModel#right} and {@link ConnectedBakedModel#up} give in the world.
     */
    static float[][] quarter(Direction face, int part) {
        float[] from = {0, 0, 0};
        float[] to = {16, 16, 16};
        boolean rightHalf = part == 1 || part == 3;
        boolean upperHalf = part == 0 || part == 1;
        halve(from, to, ConnectedBakedModel.right(face), rightHalf);
        halve(from, to, ConnectedBakedModel.up(face), upperHalf);
        return new float[][]{from, to};
    }

    /** Keeps the half of the box towards {@code direction}, or away from it. */
    private static void halve(float[] from, float[] to, Direction direction, boolean towards) {
        int axis = direction.getAxis().ordinal();
        boolean positive = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        if (towards == positive) {
            from[axis] = 8;
        } else {
            to[axis] = 8;
        }
    }

    /** The UVs vanilla gives this face of a box when a model names none - see {@code BlockElement.uvsByFace}. */
    static float[] uvs(Direction face, float[][] box) {
        float[] from = box[0];
        float[] to = box[1];
        return switch (face) {
            case DOWN -> new float[]{from[0], 16 - to[2], to[0], 16 - from[2]};
            case UP -> new float[]{from[0], from[2], to[0], to[2]};
            case NORTH -> new float[]{16 - to[0], 16 - to[1], 16 - from[0], 16 - from[1]};
            case SOUTH -> new float[]{from[0], 16 - to[1], to[0], 16 - from[1]};
            case WEST -> new float[]{from[2], 16 - to[1], to[2], 16 - from[1]};
            case EAST -> new float[]{16 - to[2], 16 - to[1], 16 - from[2], 16 - from[1]};
        };
    }

    /** Reads a connected model. */
    public static final class Loader implements IGeometryLoader<ConnectedGeometry> {

        public static final Loader INSTANCE = new Loader();

        private Loader() {
        }

        @Override
        public ConnectedGeometry read(JsonObject json, JsonDeserializationContext context) throws JsonParseException {
            String method = GsonHelper.getAsString(json, "method");
            boolean full = switch (method) {
                case "full" -> true;
                case "compact" -> false;
                default -> throw new JsonParseException("a connected model's method is full or compact, not " + method);
            };
            ResourceLocation tiles = ResourceLocation.parse(GsonHelper.getAsString(json, "tiles"));
            List<ConnectedBakedModel.Match> connectWith = new ArrayList<>();
            if (json.has("connect_with")) {
                for (JsonElement entry : GsonHelper.getAsJsonArray(json, "connect_with")) {
                    connectWith.add(match(entry));
                }
            }
            return new ConnectedGeometry(full, tiles, List.copyOf(connectWith));
        }

        private static ConnectedBakedModel.Match match(JsonElement entry) {
            if (entry.isJsonPrimitive()) {
                return new ConnectedBakedModel.Match(ResourceLocation.parse(entry.getAsString()), Map.of());
            }
            JsonObject object = entry.getAsJsonObject();
            Map<String, String> properties = new LinkedHashMap<>();
            if (object.has("properties")) {
                for (Map.Entry<String, JsonElement> property : GsonHelper.getAsJsonObject(object, "properties").entrySet()) {
                    properties.put(property.getKey(), property.getValue().getAsString());
                }
            }
            return new ConnectedBakedModel.Match(ResourceLocation.parse(GsonHelper.getAsString(object, "block")),
                    Map.copyOf(properties));
        }
    }
}
