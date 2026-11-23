package org.ae2bc.fake_crafting_converter;

import appeng.api.util.AEColor;
import appeng.util.Platform;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.CompositeModel;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import net.neoforged.neoforge.client.model.geometry.UnbakedGeometryHelper;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.part.Pp2mpIndicatorColor;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class FakeCraftingConverterModel {
    public static final float PAPER_CENTER_Y = 11.25f / 16.0f;
    private static final float PAPER_SCALE = 5.0f / 16.0f;

    private FakeCraftingConverterModel() {
    }

    /* 保留机身几何，复用原版纸张轮廓并将纸张独立交给方块实体渲染器。 */
    public static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "fake_crafting_converter"),
                Loader.INSTANCE);
    }

    private enum Loader implements IGeometryLoader<Geometry> {
        INSTANCE;

        @Override
        public @NonNull Geometry read(JsonObject json, @NonNull JsonDeserializationContext context) {
            List<ModelElement> elements = new ArrayList<>();
            for (JsonElement element : json.getAsJsonArray("elements")) {
                JsonObject elementJson = element.getAsJsonObject();
                BlockElement blockElement = context.deserialize(elementJson, BlockElement.class);
                Transformation rotation = elementJson.has("extended_rotation")
                        ? this.readRotation(elementJson.getAsJsonObject("extended_rotation"))
                        : Transformation.identity();
                elements.add(new ModelElement(blockElement, rotation));
            }
            return new Geometry(List.copyOf(elements));
        }

        /* 保留导入模型的任意角度，按 Blockbench 的 ZYX 欧拉顺序围绕元素原点旋转。 */
        private Transformation readRotation(JsonObject json) {
            float x = GsonHelper.getAsFloat(json, "x", 0);
            float y = GsonHelper.getAsFloat(json, "y", 0);
            float z = GsonHelper.getAsFloat(json, "z", 0);
            if (json.has("axis")) {
                float angle = GsonHelper.getAsFloat(json, "angle");
                switch (GsonHelper.getAsString(json, "axis")) {
                    case "x" -> x = angle;
                    case "y" -> y = angle;
                    case "z" -> z = angle;
                    default -> throw new IllegalArgumentException("Invalid model rotation axis");
                }
            }
            JsonArray originJson = json.getAsJsonArray("origin");
            Vector3f origin = new Vector3f(originJson.get(0).getAsFloat(),
                    originJson.get(1).getAsFloat(), originJson.get(2).getAsFloat()).div(16);
            Quaternionf rotation = new Quaternionf().rotationZYX(
                    (float) Math.toRadians(z), (float) Math.toRadians(y), (float) Math.toRadians(x));
            return new Transformation(new Matrix4f().translation(origin).rotate(rotation)
                    .translate(-origin.x(), -origin.y(), -origin.z()));
        }
    }

    private enum RenderLayer {
        SOLID("solid"),
        CUTOUT("cutout"),
        TRANSLUCENT("translucent");

        private final ResourceLocation renderType;

        RenderLayer(String name) {
            this.renderType = ResourceLocation.withDefaultNamespace(name);
        }
    }

    private record ModelElement(BlockElement element, Transformation rotation) {
    }

    private record Geometry(List<ModelElement> elements) implements IUnbakedGeometry<Geometry> {
        /* 按贴图透明度分层烘焙，实体先于玻璃绘制，避免玻璃深度遮挡实体柱体。 */
        @Override
        public @NonNull BakedModel bake(
                IGeometryBakingContext context, @NonNull ModelBaker baker,
                Function<Material, TextureAtlasSprite> spriteGetter,
                @NonNull ModelState modelState, @NonNull ItemOverrides overrides
        ) {
            TextureAtlasSprite particle = spriteGetter.apply(context.getMaterial("particle"));
            Map<RenderLayer, SimpleBakedModel.Builder> layers = new EnumMap<>(RenderLayer.class);
            SimpleBakedModel.Builder paper = this.createBuilder(context, overrides, particle);
            List<BakedQuad> frequencyFaces = new ArrayList<>();
            for (ModelElement modelElement : this.elements) {
                ModelState elementState = UnbakedGeometryHelper.composeRootTransformIntoModelState(modelState,
                        context.getRootTransform().compose(modelElement.rotation()));
                BlockElement element = modelElement.element();
                for (Map.Entry<Direction, BlockElementFace> entry : element.faces.entrySet()) {
                    Direction direction = entry.getKey();
                    BlockElementFace face = entry.getValue();
                    TextureAtlasSprite sprite = spriteGetter.apply(context.getMaterial(face.texture()));
                    BakedQuad quad = BlockModel.bakeFace(element, face, sprite, direction, elementState);
                    if ("#frequency".equals(face.texture())) {
                        if (quad.getTintIndex() < 0 || quad.getTintIndex() >= 4) {
                            throw new IllegalArgumentException("Frequency cells must use tintindex 0 through 3");
                        }
                        frequencyFaces.add(quad);
                        continue;
                    }
                    RenderLayer layer = this.getRenderLayer(face, sprite.contents());
                    SimpleBakedModel.Builder body = layers.computeIfAbsent(layer,
                            key -> this.createBuilder(context, overrides, particle));
                    Direction cullDirection = face.cullForDirection();
                    if (cullDirection == null || !modelElement.rotation().isIdentity()) {
                        body.addUnculledFace(quad);
                    } else {
                        Direction cull = elementState.getRotation().rotateTransform(cullDirection);
                        body.addCulledFace(cull, quad);
                    }
                }
            }
            this.addPaper(context, spriteGetter, modelState, paper);
            BakedModel paperModel = paper.build(context.getRenderType(RenderLayer.CUTOUT.renderType));
            CompositeModel.Baked.Builder body = CompositeModel.Baked.builder(
                    context, particle, overrides, context.getTransforms());
            CompositeModel.Baked.Builder complete = CompositeModel.Baked.builder(
                    context, particle, overrides, context.getTransforms());
            BakedModel frequencyModel = new FrequencyBakedModel(this.createBuilder(context, overrides, particle)
                    .build(context.getRenderType(RenderLayer.SOLID.renderType)), frequencyFaces);
            for (RenderLayer layer : RenderLayer.values()) {
                if (layer == RenderLayer.TRANSLUCENT) {
                    complete.addLayer(paperModel);
                }
                SimpleBakedModel.Builder layerBuilder = layers.get(layer);
                if (layerBuilder != null) {
                    RenderTypeGroup renderTypes = context.getRenderType(layer.renderType);
                    BakedModel layerModel = layerBuilder.build(renderTypes);
                    body.addLayer(layerModel);
                    complete.addLayer(layerModel);
                }
                if (layer == RenderLayer.SOLID) {
                    body.addLayer(frequencyModel);
                    complete.addLayer(frequencyModel);
                }
            }
            return new ConverterBakedModel(body.build(), paperModel, complete.build());
        }

        /* 烘焙时借用图集像素判断透明层，资源仍由图集释放。 */
        private RenderLayer getRenderLayer(BlockElementFace face, SpriteContents contents) {
            NativeImage image = contents.getOriginalImage();
            float minU = Float.POSITIVE_INFINITY;
            float minV = Float.POSITIVE_INFINITY;
            float maxU = Float.NEGATIVE_INFINITY;
            float maxV = Float.NEGATIVE_INFINITY;
            for (int vertex = 0; vertex < 4; vertex++) {
                minU = Math.min(minU, face.uv().getU(vertex));
                minV = Math.min(minV, face.uv().getV(vertex));
                maxU = Math.max(maxU, face.uv().getU(vertex));
                maxV = Math.max(maxV, face.uv().getV(vertex));
            }
            int minX = Mth.clamp(Mth.floor(minU * contents.width() / 16), 0, contents.width() - 1);
            int minY = Mth.clamp(Mth.floor(minV * contents.height() / 16), 0, contents.height() - 1);
            int maxX = Mth.clamp(Mth.ceil(maxU * contents.width() / 16), minX + 1, contents.width());
            int maxY = Mth.clamp(Mth.ceil(maxV * contents.height() / 16), minY + 1, contents.height());
            RenderLayer layer = RenderLayer.SOLID;
            for (int frameY = 0; frameY < image.getHeight(); frameY += contents.height()) {
                for (int frameX = 0; frameX < image.getWidth(); frameX += contents.width()) {
                    for (int y = minY; y < maxY; y++) {
                        for (int x = minX; x < maxX; x++) {
                            int alpha = image.getPixelRGBA(frameX + x, frameY + y) >>> 24;
                            if (alpha > 0 && alpha < 255) {
                                return RenderLayer.TRANSLUCENT;
                            }
                            if (alpha == 0) {
                                layer = RenderLayer.CUTOUT;
                            }
                        }
                    }
                }
            }
            return layer;
        }

        /* 使用原版物品生成器生成纸张正反面和透明轮廓侧边，不再使用图集中的纸张和阴影。 */
        private void addPaper(IGeometryBakingContext context, Function<Material, TextureAtlasSprite> spriteGetter,
                              ModelState modelState, SimpleBakedModel.Builder paper) {
            TextureAtlasSprite sprite = spriteGetter.apply(context.getMaterial("paper"));
            Transformation placement = new Transformation(new Matrix4f()
                    .translation(0.5f, PAPER_CENTER_Y, 0.5f).scale(PAPER_SCALE).translate(-0.5f, -0.5f, -0.5f));
            ModelState paperState = UnbakedGeometryHelper.composeRootTransformIntoModelState(
                    modelState, context.getRootTransform().compose(placement));
            for (BlockElement element : UnbakedGeometryHelper.createUnbakedItemElements(0, sprite)) {
                for (Map.Entry<Direction, BlockElementFace> entry : element.faces.entrySet()) {
                    BakedQuad quad = BlockModel.bakeFace(element, entry.getValue(), sprite, entry.getKey(), paperState);
                    paper.addUnculledFace(quad);
                }
            }
        }

        private SimpleBakedModel.Builder createBuilder(IGeometryBakingContext context,
                                                       ItemOverrides overrides, TextureAtlasSprite particle) {
            return new SimpleBakedModel.Builder(context.useAmbientOcclusion(), context.useBlockLight(),
                    context.isGui3d(), context.getTransforms(), overrides).particle(particle);
        }
    }

    private record FrequencyState(short frequency, boolean active) {
    }

    private static final class FrequencyBakedModel extends BakedModelWrapper<BakedModel> {
        private final List<BakedQuad> faces;
        private final LoadingCache<FrequencyState, List<BakedQuad>> cache;

        private FrequencyBakedModel(BakedModel base, List<BakedQuad> faces) {
            super(base);
            this.faces = List.copyOf(faces);
            // 缓存属于当前烘焙模型，资源重载后不会继续引用旧纹理。
            this.cache = CacheBuilder.newBuilder().maximumSize(100)
                    .build(CacheLoader.from(this::colorFaces));
        }

        /* 频率零也使用 AE2 的白色配色，亮暗只由同步的网络状态决定。 */
        private List<BakedQuad> colorFaces(FrequencyState state) {
            AEColor[] colors = Platform.p2p().toColors(state.frequency());
            List<BakedQuad> result = new ArrayList<>(this.faces.size());
            for (BakedQuad template : this.faces) {
                BakedQuad quad = new BakedQuad(template.getVertices().clone(), -1, template.getDirection(),
                        template.getSprite(), false, false);
                int color = Pp2mpIndicatorColor.getRgb(colors[template.getTintIndex()], state.active());
                QuadTransformers.applyingColor(0xff000000 | color).processInPlace(quad);
                if (state.active()) {
                    QuadTransformers.settingMaxEmissivity().processInPlace(quad);
                }
                result.add(quad);
            }
            return List.copyOf(result);
        }

        @Override
        public @NonNull List<BakedQuad> getQuads(
                @Nullable BlockState state, @Nullable Direction side, @NonNull RandomSource random,
                @NonNull ModelData data, @Nullable RenderType renderType
        ) {
            if (side != null || renderType != null && renderType != RenderType.solid()) {
                return List.of();
            }
            Short value = data.get(FakeCraftingConverterBlockEntity.FREQUENCY_PROPERTY);
            short frequency = value == null ? 0 : value;
            boolean active = Boolean.TRUE.equals(data.get(FakeCraftingConverterBlockEntity.ACTIVE_PROPERTY));
            return this.cache.getUnchecked(new FrequencyState(frequency, active));
        }

        @Override
        public @NonNull List<BakedQuad> getQuads(
                @Nullable BlockState state, @Nullable Direction side, @NonNull RandomSource random
        ) {
            return this.getQuads(state, side, random, ModelData.EMPTY, null);
        }
    }

    public static final class ConverterBakedModel extends BakedModelWrapper<BakedModel> {
        private final BakedModel paper;
        private final BakedModel complete;

        private ConverterBakedModel(BakedModel body, BakedModel paper, BakedModel complete) {
            super(body);
            this.paper = paper;
            this.complete = complete;
        }

        public BakedModel getPaperModel() {
            return this.paper;
        }

        /* 背包和手持使用完整导入模型，世界中的纸张由渲染器单独绘制。 */
        @Override
        public @NonNull BakedModel applyTransform(
                @NonNull ItemDisplayContext displayContext,
                @NonNull PoseStack poseStack, boolean leftHand
        ) {
            return this.complete.applyTransform(displayContext, poseStack, leftHand);
        }

        @Override
        public @NonNull List<BakedModel> getRenderPasses(@NonNull ItemStack stack, boolean fabulous) {
            return this.complete.getRenderPasses(stack, fabulous);
        }
    }
}
