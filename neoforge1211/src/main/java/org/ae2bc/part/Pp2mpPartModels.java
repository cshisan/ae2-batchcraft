package org.ae2bc.part;

import appeng.api.parts.IPartModel;
import appeng.parts.PartModel;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import org.ae2bc.Ae2bcMod;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.function.Function;

public record Pp2mpPartModels(IPartModel off, IPartModel on, IPartModel channel) {
    public static final ResourceLocation STATUS_OFF = Pp2mpPartModels.location("status_off");
    public static final ResourceLocation STATUS_ON = Pp2mpPartModels.location("status_on");
    public static final ResourceLocation STATUS_HAS_CHANNEL = Pp2mpPartModels.location("status_has_channel");
    public static final ResourceLocation FREQUENCY = Pp2mpPartModels.location("frequency");

    /* 复用 AE2 状态模型的本模组无方向阴影版本，不影响原版 P2P。 */
    public Pp2mpPartModels(ResourceLocation front) {
        this(new PartModel(STATUS_OFF, FREQUENCY, front), new PartModel(STATUS_ON, FREQUENCY, front),
                new PartModel(STATUS_HAS_CHANNEL, FREQUENCY, front));
    }

    private static ResourceLocation location(String name) {
        return ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "part/p2p/" + name);
    }

    public IPartModel getModel(boolean powered, boolean active) {
        return powered ? active ? this.channel : this.on : this.off;
    }

    public List<IPartModel> getModels() {
        return List.of(this.off, this.on, this.channel);
    }

    /* 客户端渲染实现按需加载，公共模型标识和状态选择不依赖渲染类。 */
    public static final class Client {
        private Client() {
        }

        /* 为本模组烘焙模型统一关闭方向明暗，保留原始模型及其他光照属性。 */
        public static BakedModel withoutShade(BakedModel model) {
            return new UnshadedBakedModel(model);
        }

        /* 在 AE2 组装线缆模型前处理本模组的模型，避免修改原版共享模型。 */
        public static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
            event.register(ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "unshaded"), Loader.INSTANCE);
        }

        private enum Loader implements IGeometryLoader<Geometry> {
            INSTANCE;

            @Override
            public @NonNull Geometry read(JsonObject json, @NonNull JsonDeserializationContext context) {
                if (json.has("source")) {
                    return new Geometry(null, ResourceLocation.parse(json.get("source").getAsString()));
                }
                JsonObject body = json.deepCopy();
                body.remove("loader");
                return new Geometry(context.deserialize(body, BlockModel.class), null);
            }
        }

        private static final class UnshadedBakedModel extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
            private final LoadingCache<BakedQuad, BakedQuad> quads = CacheBuilder.newBuilder()
                    .weakKeys()
                    .maximumSize(4096)
                    .build(CacheLoader.from(UnshadedBakedModel::removeShade));

            private UnshadedBakedModel(BakedModel originalModel) {
                super(originalModel);
            }

            /* 只关闭模型面的方向明暗，保留顶点颜色、光照值和环境遮蔽。 */
            private static BakedQuad removeShade(BakedQuad quad) {
                return new BakedQuad(quad.getVertices().clone(), quad.getTintIndex(), quad.getDirection(),
                        quad.getSprite(), false, quad.hasAmbientOcclusion());
            }

            @Override
            public @NonNull List<BakedQuad> getQuads(
                    @Nullable BlockState state, @Nullable Direction side, @NonNull RandomSource random
            ) {
                return this.getQuads(state, side, random, ModelData.EMPTY, null);
            }

            @Override
            public @NonNull List<BakedQuad> getQuads(
                    @Nullable BlockState state, @Nullable Direction side,
                    @NonNull RandomSource random, @NonNull ModelData data,
                    @Nullable RenderType renderType
            ) {
                return this.withoutShade(this.originalModel.getQuads(state, side, random, data, renderType));
            }

            private List<BakedQuad> withoutShade(List<BakedQuad> originalQuads) {
                return originalQuads.stream()
                        .map(quad -> quad.isShade() ? this.quads.getUnchecked(quad) : quad).toList();
            }
        }

        private record Geometry(@Nullable BlockModel body, @Nullable ResourceLocation source)
                implements IUnbakedGeometry<Geometry> {
            @Override
            public void resolveParents(
                    @NonNull Function<ResourceLocation, UnbakedModel> modelGetter,
                    @NonNull IGeometryBakingContext context
            ) {
                if (this.source != null) {
                    modelGetter.apply(this.source).resolveParents(modelGetter);
                } else {
                    this.inlineModel().resolveParents(modelGetter);
                }
            }

            @Override
            public @NonNull BakedModel bake(
                    @NonNull IGeometryBakingContext context, @NonNull ModelBaker baker,
                    @NonNull Function<Material, TextureAtlasSprite> spriteGetter,
                    @NonNull ModelState modelState, @NonNull ItemOverrides overrides
            ) {
                BakedModel model = this.source != null
                        ? baker.bake(this.source, modelState, spriteGetter)
                        : this.inlineModel().bake(baker, spriteGetter, modelState);
                if (model == null) {
                    throw new IllegalStateException("Failed to bake unshaded model: " + this.source);
                }
                return Client.withoutShade(model);
            }

            private BlockModel inlineModel() {
                if (this.body == null) {
                    throw new IllegalStateException("Unshaded geometry requires a source or an inline model");
                }
                return this.body;
            }
        }
    }
}
