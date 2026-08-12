package cn.ae2bc.client.model;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import appeng.api.parts.IPartBakedModel;
import appeng.api.util.AEColor;
import appeng.client.render.cablebus.CubeBuilder;
import appeng.util.Platform;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ICustomModelLoader;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.common.model.IModelState;

/** Renders the unit identity marker as an AE2 part-model component. */
public enum PatternP2PUnitPortIdentityGeometry implements ICustomModelLoader, IModel {
    INSTANCE;

    private static final ResourceLocation ACTUAL_MODEL = new ResourceLocation(
            "ae2_batchcraft", "models/part/p2p/pattern_p2p_unit_port_identity");
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            "appliedenergistics2", "parts/p2p_tunnel_frequency");
    private static final float PANEL_OFFSET = 1.0f / 64.0f;
    private static final float PANEL_THICKNESS = 1.0f / 32.0f;

    @Override public void onResourceManagerReload(IResourceManager resourceManager) { }
    @Override public boolean accepts(ResourceLocation modelLocation) {
        return ACTUAL_MODEL.equals(modelLocation);
    }
    @Override public IModel loadModel(ResourceLocation modelLocation) { return this; }
    @Override public Collection<ResourceLocation> getTextures() { return Collections.singleton(TEXTURE); }

    @Override
    public IBakedModel bake(IModelState state, VertexFormat format,
            Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        return new IdentityModel(format, bakedTextureGetter.apply(TEXTURE));
    }

    private static final class IdentityModel implements IBakedModel, IPartBakedModel {
        private static final Cache<Long, List<BakedQuad>> CACHE = CacheBuilder.newBuilder()
                .maximumSize(100).build();
        private final VertexFormat format;
        private final TextureAtlasSprite texture;

        private IdentityModel(VertexFormat format, TextureAtlasSprite texture) {
            this.format = format;
            this.texture = texture;
        }

        @Override
        public List<BakedQuad> getPartQuads(Long renderFlags, long rand) {
            if (renderFlags == null) return Collections.emptyList();
            final long identityFlags = renderFlags.longValue() >>> 17 & 0x1ffffL;
            try {
                return CACHE.get(Long.valueOf(identityFlags), () -> build(format, texture, identityFlags));
            } catch (ExecutionException ignored) {
                return Collections.emptyList();
            }
        }

        @Override public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
            return Collections.emptyList();
        }
        @Override public boolean isAmbientOcclusion() { return false; }
        @Override public boolean isGui3d() { return false; }
        @Override public boolean isBuiltInRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleTexture() { return texture; }
        @Override public ItemCameraTransforms getItemCameraTransforms() { return ItemCameraTransforms.DEFAULT; }
        @Override public ItemOverrideList getOverrides() { return ItemOverrideList.NONE; }
    }

    private static List<BakedQuad> build(VertexFormat format, TextureAtlasSprite texture, long flags) {
        AEColor[] colors = Platform.p2p().toColors((short) flags);
        boolean active = (flags & 0x10000L) != 0;
        CubeBuilder builder = new CubeBuilder(format);
        builder.setTexture(texture);
        builder.setRenderFullBright(active);
        for (int colorIndex = 0; colorIndex < 4; colorIndex++) {
            setColor(builder, colors[colorIndex], active);
            addSideCells(builder, colorIndex % 2, colorIndex / 2);
        }
        builder.setRenderFullBright(false);
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
