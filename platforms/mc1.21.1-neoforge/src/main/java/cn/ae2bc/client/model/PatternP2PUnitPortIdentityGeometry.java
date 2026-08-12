package cn.ae2bc.client.model;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import appeng.client.render.cablebus.CubeBuilder;
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
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/** Renders a central 2x2 unit identity marker on each of a port's four side faces. */
public enum PatternP2PUnitPortIdentityGeometry implements
        IUnbakedGeometry<PatternP2PUnitPortIdentityGeometry>, IGeometryLoader<PatternP2PUnitPortIdentityGeometry> {
    INSTANCE;

    private static final float PANEL_OFFSET = 1.0f / 64.0f;
    private static final float PANEL_THICKNESS = 1.0f / 32.0f;

    @Override
    public PatternP2PUnitPortIdentityGeometry read(JsonObject json, JsonDeserializationContext context) {
        return INSTANCE;
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                            Function<Material, TextureAtlasSprite> spriteGetter,
                            ModelState modelState, ItemOverrides overrides) {
        return new IdentityModel(spriteGetter.apply(PatternP2PUnitModelSupport.FREQUENCY_TEXTURE));
    }

    private static final class IdentityModel implements IDynamicBakedModel {
        private static final Cache<Long, List<BakedQuad>> CACHE = CacheBuilder.newBuilder()
                .maximumSize(100).build();
        private final TextureAtlasSprite texture;

        private IdentityModel(TextureAtlasSprite texture) {
            this.texture = texture;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                        RandomSource random, ModelData modelData,
                                        @Nullable RenderType renderType) {
            if (side != null || !modelData.has(PatternP2PUnitModelData.PATTERN_P2P_UNIT_ID)) {
                return Collections.emptyList();
            }
            long flags = modelData.get(PatternP2PUnitModelData.PATTERN_P2P_UNIT_ID);
            try {
                return CACHE.get(flags, () -> build(flags));
            } catch (ExecutionException ignored) {
                return Collections.emptyList();
            }
        }

        private List<BakedQuad> build(long flags) {
            var colors = PatternP2PUnitModelSupport.colors(flags);
            boolean active = PatternP2PUnitModelSupport.isActive(flags);
            CubeBuilder builder = new CubeBuilder();
            builder.setTexture(texture);
            builder.setEmissiveMaterial(active);
            for (int colorIndex = 0; colorIndex < 4; colorIndex++) {
                PatternP2PUnitModelSupport.setColor(builder, colors[colorIndex], active);
                float first = colorIndex % 2;
                float second = colorIndex / 2;
                addSideCells(builder, first, second);
            }
            builder.setEmissiveMaterial(false);
            return builder.getOutput();
        }

        private static void addSideCells(CubeBuilder builder, float first, float second) {
            float x = 7 + first;
            float y = 7 + second;
            float z = first;
            float depth = second;

            // The local north face is the port front. These four panels are its lateral faces,
            // so a port facing up shows the marker on north, east, south and west only.
            float outerMax = 14 + PANEL_OFFSET;
            float outerMin = 2 - PANEL_OFFSET;
            builder.addCube(outerMax, y, z, outerMax + PANEL_THICKNESS, y + 1, z + 1);
            builder.addCube(outerMin - PANEL_THICKNESS, y, z, outerMin, y + 1, z + 1);
            builder.addCube(x, outerMax, depth, x + 1, outerMax + PANEL_THICKNESS, depth + 1);
            builder.addCube(x, outerMin - PANEL_THICKNESS, depth, x + 1, outerMin, depth + 1);
        }

        @Override public boolean useAmbientOcclusion() { return false; }
        @Override public boolean isGui3d() { return false; }
        @Override public boolean usesBlockLight() { return false; }
        @Override public boolean isCustomRenderer() { return true; }
        @Override
        @SuppressWarnings("deprecation") // Required by BakedModel in the targeted NeoForge version.
        public TextureAtlasSprite getParticleIcon() { return texture; }
        @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    }
}
