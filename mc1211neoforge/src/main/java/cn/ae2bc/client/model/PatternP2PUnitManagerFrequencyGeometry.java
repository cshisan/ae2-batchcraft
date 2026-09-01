package cn.ae2bc.client.model;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import appeng.client.render.cablebus.CubeBuilder;
import appeng.client.render.cablebus.P2PTunnelFrequencyModelData;
import cn.ae2bc.logic.PatternP2PUnitDimensions;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/** Renders frequency colors at the corners and unit identity colors in the center. */
public enum PatternP2PUnitManagerFrequencyGeometry implements
        IUnbakedGeometry<PatternP2PUnitManagerFrequencyGeometry>, IGeometryLoader<PatternP2PUnitManagerFrequencyGeometry> {
    INSTANCE;

    @Override
    public PatternP2PUnitManagerFrequencyGeometry read(JsonObject json, JsonDeserializationContext context) {
        return INSTANCE;
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                            Function<Material, TextureAtlasSprite> spriteGetter,
                            ModelState modelState, ItemOverrides overrides) {
        return new FrequencyModel(spriteGetter.apply(PatternP2PUnitModelSupport.FREQUENCY_TEXTURE));
    }

    private static final class FrequencyModel implements IDynamicBakedModel {
        private static final Cache<Long, List<BakedQuad>> CACHE = CacheBuilder.newBuilder()
                .maximumSize(100).build();
        private final TextureAtlasSprite texture;

        private FrequencyModel(TextureAtlasSprite texture) {
            this.texture = texture;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                        RandomSource random, ModelData modelData,
                                        @Nullable RenderType renderType) {
            if (side != null || !modelData.has(P2PTunnelFrequencyModelData.FREQUENCY)) {
                return Collections.emptyList();
            }
            long frequencyFlags = modelData.get(P2PTunnelFrequencyModelData.FREQUENCY);
            long patternP2PUnitFlags = modelData.has(PatternP2PUnitModelData.PATTERN_P2P_UNIT_ID)
                    ? modelData.get(PatternP2PUnitModelData.PATTERN_P2P_UNIT_ID) : 0;
            long cacheKey = frequencyFlags | patternP2PUnitFlags << 17;
            try {
                return CACHE.get(cacheKey, () -> build(frequencyFlags, patternP2PUnitFlags));
            } catch (ExecutionException ignored) {
                return Collections.emptyList();
            }
        }

        private List<BakedQuad> build(long frequencyFlags, long patternP2PUnitFlags) {
            CubeBuilder builder = new CubeBuilder();
            builder.setTexture(texture);
            addFrequencyCorners(builder, frequencyFlags);
            addPatternP2PUnitCenter(builder, patternP2PUnitFlags);
            builder.setEmissiveMaterial(false);
            return builder.getOutput();
        }

        private static void addFrequencyCorners(CubeBuilder builder, long flags) {
            var colors = PatternP2PUnitModelSupport.colors(flags);
            boolean active = PatternP2PUnitModelSupport.isActive(flags);
            builder.setEmissiveMaterial(active);
            for (int colorIndex = 0; colorIndex < 4; colorIndex++) {
                PatternP2PUnitModelSupport.setColor(builder, colors[colorIndex], active);
                addColorToEveryCornerOnEveryFace(builder, colorIndex);
            }
        }

        private static void addPatternP2PUnitCenter(CubeBuilder builder, long flags) {
            var colors = PatternP2PUnitModelSupport.colors(flags);
            boolean active = PatternP2PUnitModelSupport.isActive(flags);
            builder.setEmissiveMaterial(active);
            for (int colorIndex = 0; colorIndex < 4; colorIndex++) {
                PatternP2PUnitModelSupport.setColor(builder, colors[colorIndex], active);
                float u = PatternP2PUnitDimensions.ID_MIN + colorIndex % 2;
                float v = PatternP2PUnitDimensions.ID_MIN + colorIndex / 2;
                addCellToEveryFace(builder, u, v);
            }
        }

        private static void addColorToEveryCornerOnEveryFace(CubeBuilder builder, int colorIndex) {
            float cellU = colorIndex % 2;
            float cellV = colorIndex / 2;
            for (int cornerU = 0; cornerU < 2; cornerU++) {
                for (int cornerV = 0; cornerV < 2; cornerV++) {
                    float u = (cornerU == 0
                            ? PatternP2PUnitDimensions.FRAME_MIN
                            : PatternP2PUnitDimensions.INNER_MAX) + cellU;
                    float v = (cornerV == 0
                            ? PatternP2PUnitDimensions.FRAME_MIN
                            : PatternP2PUnitDimensions.INNER_MAX) + cellV;
                    addCellToEveryFace(builder, u, v);
                }
            }
        }

        private static void addCellToEveryFace(CubeBuilder builder, float u, float v) {
            float min = PatternP2PUnitDimensions.FRAME_MIN;
            float max = PatternP2PUnitDimensions.FRAME_MAX;
            float depth = PatternP2PUnitDimensions.INDICATOR_DEPTH;
            builder.addCube(u, v, min - depth, u + 1, v + 1, min);
            builder.addCube(u, v, max, u + 1, v + 1, max + depth);
            builder.addCube(min - depth, v, u, min, v + 1, u + 1);
            builder.addCube(max, v, u, max + depth, v + 1, u + 1);
            builder.addCube(u, min - depth, v, u + 1, min, v + 1);
            builder.addCube(u, max, v, u + 1, max + depth, v + 1);
        }

        @Override public boolean useAmbientOcclusion() { return false; }
        @Override public boolean isGui3d() { return true; }
        @Override public boolean usesBlockLight() { return false; }
        @Override public boolean isCustomRenderer() { return true; }
        @Override
        @SuppressWarnings("deprecation") // Required by BakedModel in the targeted NeoForge version.
        public TextureAtlasSprite getParticleIcon() { return texture; }
        @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    }
}
