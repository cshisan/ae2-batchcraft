package cn.ae2bc.client.model;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;

import appeng.api.util.AEColor;
import appeng.client.render.cablebus.CubeBuilder;
import appeng.util.Platform;
import net.minecraft.block.BlockState;
import net.minecraft.client.renderer.model.BakedQuad;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.model.IModelTransform;
import net.minecraft.client.renderer.model.IUnbakedModel;
import net.minecraft.client.renderer.model.ItemOverrideList;
import net.minecraft.client.renderer.model.ModelBakery;
import net.minecraft.client.renderer.model.RenderMaterial;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.IResourceManager;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModelConfiguration;
import net.minecraftforge.client.model.IModelLoader;
import net.minecraftforge.client.model.data.IDynamicBakedModel;
import net.minecraftforge.client.model.data.IModelData;
import net.minecraftforge.client.model.geometry.IModelGeometry;

/** Renders the unit identity marker as an AE2 part-model component. */
public enum PatternP2PUnitPortIdentityGeometry implements
        IModelGeometry<PatternP2PUnitPortIdentityGeometry>,
        IModelLoader<PatternP2PUnitPortIdentityGeometry> {
    INSTANCE;

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            "appliedenergistics2", "part/p2p_tunnel_frequency");
    private static final RenderMaterial MATERIAL = new RenderMaterial(AtlasTexture.LOCATION_BLOCKS, TEXTURE);
    private static final float PANEL_OFFSET = 1.0f / 64.0f;
    private static final float PANEL_THICKNESS = 1.0f / 32.0f;

    @Override
    public PatternP2PUnitPortIdentityGeometry read(JsonDeserializationContext context, JsonObject modelContents) {
        return INSTANCE;
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
    }

    @Override
    public IBakedModel bake(IModelConfiguration owner, ModelBakery bakery,
            Function<RenderMaterial, TextureAtlasSprite> spriteGetter, IModelTransform modelTransform,
            ItemOverrideList overrides, ResourceLocation modelLocation) {
        return new IdentityModel(spriteGetter.apply(MATERIAL));
    }

    @Override
    public Collection<RenderMaterial> getTextures(IModelConfiguration owner,
            Function<ResourceLocation, IUnbakedModel> modelGetter,
            Set<Pair<String, String>> missingTextureErrors) {
        return Collections.singleton(MATERIAL);
    }

    private static final class IdentityModel implements IDynamicBakedModel {
        private static final Cache<Long, List<BakedQuad>> CACHE = CacheBuilder.newBuilder()
                .maximumSize(100).build();
        private final TextureAtlasSprite texture;

        private IdentityModel(TextureAtlasSprite texture) {
            this.texture = texture;
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, Random random, IModelData modelData) {
            if (side != null || !modelData.hasProperty(PatternP2PUnitModelData.PATTERN_P2P_UNIT_ID)) {
                return Collections.emptyList();
            }
            final long flags = modelData.getData(PatternP2PUnitModelData.PATTERN_P2P_UNIT_ID).longValue();
            try {
                return CACHE.get(Long.valueOf(flags), () -> build(texture, flags));
            } catch (ExecutionException ignored) {
                return Collections.emptyList();
            }
        }

        @Override public boolean useAmbientOcclusion() { return false; }
        @Override public boolean isGui3d() { return false; }
        @Override public boolean usesBlockLight() { return false; }
        @Override public boolean isCustomRenderer() { return true; }
        @Override public TextureAtlasSprite getParticleIcon() { return texture; }
        @Override public ItemOverrideList getOverrides() { return ItemOverrideList.EMPTY; }
    }

    private static List<BakedQuad> build(TextureAtlasSprite texture, long flags) {
        AEColor[] colors = Platform.p2p().toColors((short) flags);
        boolean active = (flags & 0x10000L) != 0;
        CubeBuilder builder = new CubeBuilder();
        builder.setTexture(texture);
        builder.setEmissiveMaterial(active);
        for (int colorIndex = 0; colorIndex < 4; colorIndex++) {
            setColor(builder, colors[colorIndex], active);
            addSideCells(builder, colorIndex % 2, colorIndex / 2);
        }
        builder.setEmissiveMaterial(false);
        return builder.getOutput();
    }

    private static void addSideCells(CubeBuilder builder, float first, float second) {
        float x = 7 + first;
        float y = 7 + second;
        float z = first;
        float depth = second;
        float outerMax = 14 + PANEL_OFFSET;
        float outerMin = 2 - PANEL_OFFSET;
        builder.addCube(outerMax, y, z, outerMax + PANEL_THICKNESS, y + 1, z + 1);
        builder.addCube(outerMin - PANEL_THICKNESS, y, z, outerMin, y + 1, z + 1);
        builder.addCube(x, outerMax, depth, x + 1, outerMax + PANEL_THICKNESS, depth + 1);
        builder.addCube(x, outerMin - PANEL_THICKNESS, depth, x + 1, outerMin, depth + 1);
    }

    private static void setColor(CubeBuilder builder, AEColor color, boolean active) {
        if (active) {
            builder.setColorRGB(color.mediumVariant);
            return;
        }
        float scale = 0.3f / 255.0f;
        builder.setColorRGB((color.blackVariant >> 16 & 0xff) * scale,
                (color.blackVariant >> 8 & 0xff) * scale,
                (color.blackVariant & 0xff) * scale);
    }
}
