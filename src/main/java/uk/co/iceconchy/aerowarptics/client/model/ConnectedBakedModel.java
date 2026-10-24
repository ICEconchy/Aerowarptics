package uk.co.iceconchy.aerowarptics.client.model;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A baked {@link ConnectedGeometry}: a full cube that picks, face by face, the tile for the blocks it joins.
 *
 * <p>Which neighbours a face joins is worked out in {@link #getModelData}, which the game asks while it builds a chunk
 * section: for each face, the eight blocks round it in that face's plane. A neighbour joins if it is one of the
 * blocks the model connects with, and the face it would show beside this one is not buried - a block of the same set
 * in front of it means that face is not drawn, and a seam that ran into it would end in the air. Without a level - an
 * item in a hand, a block in a minecart - every face is drawn alone.
 */
@OnlyIn(Dist.CLIENT)
public final class ConnectedBakedModel implements IDynamicBakedModel {

    /** Which of the eight neighbours each face joins, by {@link Direction#get3DDataValue()}. */
    static final ModelProperty<int[]> JOINS = new ModelProperty<>();

    /** The four quarters of a compact face, in {@link ConnectedGeometry#quarter}'s order, and the neighbours each reads. */
    static final int[][] QUARTERS = {
            {ConnectedLayout.LEFT, ConnectedLayout.UP, ConnectedLayout.UP_LEFT},
            {ConnectedLayout.RIGHT, ConnectedLayout.UP, ConnectedLayout.UP_RIGHT},
            {ConnectedLayout.LEFT, ConnectedLayout.DOWN, ConnectedLayout.DOWN_LEFT},
            {ConnectedLayout.RIGHT, ConnectedLayout.DOWN, ConnectedLayout.DOWN_RIGHT},
    };

    /** One block a model connects with, or only those of its states with these property values. */
    record Match(ResourceLocation block, Map<String, String> properties) {

        boolean test(BlockState state) {
            if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(block)) {
                return false;
            }
            for (Map.Entry<String, String> wanted : properties.entrySet()) {
                Property<?> property = state.getBlock().getStateDefinition().getProperty(wanted.getKey());
                if (property == null || !wanted.getValue().equals(value(state, property))) {
                    return false;
                }
            }
            return true;
        }

        private static <T extends Comparable<T>> String value(BlockState state, Property<T> property) {
            return property.getName(state.getValue(property));
        }
    }

    private final boolean full;
    private final BakedQuad[][][] quads;
    private final List<Match> connectWith;
    private final TextureAtlasSprite particle;
    private final boolean ambientOcclusion;
    private final boolean gui3d;
    private final boolean blockLight;
    private final ItemTransforms transforms;
    private final RenderTypeGroup renderTypes;
    private final ItemOverrides overrides;

    ConnectedBakedModel(boolean full, BakedQuad[][][] quads, List<Match> connectWith, TextureAtlasSprite particle,
                        boolean ambientOcclusion, boolean gui3d, boolean blockLight, ItemTransforms transforms,
                        RenderTypeGroup renderTypes, ItemOverrides overrides) {
        this.full = full;
        this.quads = quads;
        this.connectWith = connectWith;
        this.particle = particle;
        this.ambientOcclusion = ambientOcclusion;
        this.gui3d = gui3d;
        this.blockLight = blockLight;
        this.transforms = transforms;
        this.renderTypes = renderTypes;
        this.overrides = overrides;
    }

    /** The way a face's texture runs left to right, seen from outside - as vanilla lays a cube's faces out. */
    static Direction right(Direction face) {
        return switch (face) {
            case NORTH -> Direction.WEST;
            case SOUTH, UP, DOWN -> Direction.EAST;
            case WEST -> Direction.SOUTH;
            case EAST -> Direction.NORTH;
        };
    }

    /** The way a face's texture runs bottom to top, seen from outside. */
    static Direction up(Direction face) {
        return switch (face) {
            case UP -> Direction.NORTH;
            case DOWN -> Direction.SOUTH;
            default -> Direction.UP;
        };
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        int[] joins = new int[6];
        for (Direction face : Direction.values()) {
            Direction right = right(face);
            Direction up = up(face);
            int mask = 0;
            mask |= joins(level, state, pos.relative(up), face) ? ConnectedLayout.UP : 0;
            mask |= joins(level, state, pos.relative(up.getOpposite()), face) ? ConnectedLayout.DOWN : 0;
            mask |= joins(level, state, pos.relative(right.getOpposite()), face) ? ConnectedLayout.LEFT : 0;
            mask |= joins(level, state, pos.relative(right), face) ? ConnectedLayout.RIGHT : 0;
            mask |= joins(level, state, pos.relative(up).relative(right.getOpposite()), face) ? ConnectedLayout.UP_LEFT : 0;
            mask |= joins(level, state, pos.relative(up).relative(right), face) ? ConnectedLayout.UP_RIGHT : 0;
            mask |= joins(level, state, pos.relative(up.getOpposite()).relative(right.getOpposite()), face)
                    ? ConnectedLayout.DOWN_LEFT : 0;
            mask |= joins(level, state, pos.relative(up.getOpposite()).relative(right), face)
                    ? ConnectedLayout.DOWN_RIGHT : 0;
            joins[face.get3DDataValue()] = mask;
        }
        return data.derive().with(JOINS, joins).build();
    }

    /** Whether the block at {@code at} joins a face of {@code self}'s: one of the set, its own face there not buried. */
    private boolean joins(BlockAndTintGetter level, BlockState self, BlockPos at, Direction face) {
        return connects(self, level.getBlockState(at)) && !connects(self, level.getBlockState(at.relative(face)));
    }

    private boolean connects(BlockState self, BlockState other) {
        if (connectWith.isEmpty()) {
            return other.getBlock() == self.getBlock();
        }
        for (Match match : connectWith) {
            if (match.test(other)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                    ModelData data, @Nullable RenderType renderType) {
        if (side == null) {
            return List.of();
        }
        int[] joins = data.get(JOINS);
        int mask = joins == null ? 0 : joins[side.get3DDataValue()];
        BakedQuad[][] face = quads[side.get3DDataValue()];
        if (full) {
            return List.of(face[0][ConnectedLayout.full(mask)]);
        }
        List<BakedQuad> parts = new ArrayList<>(4);
        for (int part = 0; part < 4; part++) {
            int[] reads = QUARTERS[part];
            parts.add(face[part][ConnectedLayout.compact(
                    (mask & reads[0]) != 0, (mask & reads[1]) != 0, (mask & reads[2]) != 0)]);
        }
        return parts;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        return renderTypes.isEmpty() ? ChunkRenderTypeSet.of(RenderType.solid()) : ChunkRenderTypeSet.of(renderTypes.block());
    }

    @Override
    public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
        if (renderTypes.isEmpty()) {
            return IDynamicBakedModel.super.getRenderTypes(stack, fabulous);
        }
        return List.of(fabulous ? renderTypes.entityFabulous() : renderTypes.entity());
    }

    @Override
    public boolean useAmbientOcclusion() {
        return ambientOcclusion;
    }

    @Override
    public boolean isGui3d() {
        return gui3d;
    }

    @Override
    public boolean usesBlockLight() {
        return blockLight;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return particle;
    }

    @Override
    public ItemTransforms getTransforms() {
        return transforms;
    }

    @Override
    public ItemOverrides getOverrides() {
        return overrides;
    }
}
