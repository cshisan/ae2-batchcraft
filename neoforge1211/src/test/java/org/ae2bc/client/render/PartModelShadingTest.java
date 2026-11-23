package org.ae2bc.client.render;

import appeng.api.parts.IPartModel;
import appeng.client.render.cablebus.P2PTunnelFrequencyModel;
import appeng.client.render.cablebus.P2PTunnelFrequencyModelData;
import appeng.client.render.cablebus.QuadRotator;
import appeng.core.AppEng;
import appeng.parts.p2p.P2PModels;
import appeng.thirdparty.fabric.MeshBuilderImpl;
import appeng.thirdparty.fabric.QuadEmitter;
import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.NamedRenderTypeManager;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.GeometryLoaderManager;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import org.ae2bc.part.Pp2mpPartModels;
import org.ae2bc.part.input.Pp2mpInputModel;
import org.ae2bc.part.output.Pp2mpOutputModel;
import org.ae2bc.part.resource_intake.Pp2mpResourceIntakeModel;
import org.ae2bc.part.unit.manager.Pp2mpUnitManagerModel;
import org.ae2bc.part.unit.port.base.Pp2mpUnitPortPart;
import org.ae2bc.part.unit.port.base.Pp2mpUnitPortPartModel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

class PartModelShadingTest {
    @Test
    void managerIndicatorCacheIncludesPowerFrequencyAndUnitIdAndRemainsBounded() throws Exception {
        ResourceLocation spriteId = ResourceLocation.fromNamespaceAndPath("ae2_batchcraft", "test/manager");
        try (SpriteContents contents = new SpriteContents(spriteId, new FrameSize(16, 16),
                new NativeImage(16, 16, false), ResourceMetadata.EMPTY)) {
            Class<?> type = Class.forName(Pp2mpUnitManagerModel.class.getName() + "$IndicatorLightModel");
            Constructor<?> constructor = type.getDeclaredConstructor(TextureAtlasSprite.class);
            constructor.setAccessible(true);
            Object instance = constructor.newInstance(new TestSprite(contents));
            if (!(instance instanceof BakedModel model)) {
                throw new AssertionError("Indicator must be a baked model");
            }
            ModelData unpowered = ModelData.builder()
                    .with(P2PTunnelFrequencyModelData.FREQUENCY, 1L)
                    .with(Pp2mpUnitManagerModel.PP2MP_UNIT_ID, 2L).build();
            List<BakedQuad> first = PartModelShadingTest.quads(model, unpowered);
            Assertions.assertSame(first, PartModelShadingTest.quads(model, unpowered));
            Assertions.assertNotSame(first, PartModelShadingTest.quads(model, unpowered.derive()
                    .with(P2PTunnelFrequencyModelData.FREQUENCY, 0x10001L).build()));
            Assertions.assertNotSame(first, PartModelShadingTest.quads(model, unpowered.derive()
                    .with(Pp2mpUnitManagerModel.PP2MP_UNIT_ID, 3L).build()));
            for (long frequency = 0; frequency < 120; frequency++) {
                PartModelShadingTest.quads(model, unpowered.derive()
                        .with(P2PTunnelFrequencyModelData.FREQUENCY, frequency).build());
            }
            Field field = type.getDeclaredField("quadCache");
            field.setAccessible(true);
            if (!(field.get(instance) instanceof Cache<?, ?> cache)) {
                throw new AssertionError("Indicator must use a bounded cache");
            }
            cache.cleanUp();
            Assertions.assertTrue(cache.size() <= 100);
        }
    }

    private static void registerLoader(ModelEvent.RegisterGeometryLoaders event, String id, String className)
            throws ReflectiveOperationException {
        Field instance = Class.forName(className).getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        event.register(ResourceLocation.fromNamespaceAndPath("ae2_batchcraft", id),
                (IGeometryLoader<?>) instance.get(null));
    }

    /* 验证别名只改变 shade，不改变原版模型、光照、颜色、UV 或 AO。 */
    private static void assertSharedModelsUnchanged(TestBaker baker, ModelData data) {
        Map<ResourceLocation, ResourceLocation> aliases = Map.of(
                Pp2mpPartModels.STATUS_OFF, P2PModels.MODEL_STATUS_OFF,
                Pp2mpPartModels.STATUS_ON, P2PModels.MODEL_STATUS_ON,
                Pp2mpPartModels.STATUS_HAS_CHANNEL, P2PModels.MODEL_STATUS_HAS_CHANNEL,
                Pp2mpPartModels.FREQUENCY, P2PModels.MODEL_FREQUENCY);
        for (Map.Entry<ResourceLocation, ResourceLocation> alias : aliases.entrySet()) {
            BakedModel original = baker.bake(alias.getValue(), BlockModelRotation.X0_Y0, baker.getModelTextureGetter());
            BakedModel changed = baker.bake(alias.getKey(), BlockModelRotation.X0_Y0, baker.getModelTextureGetter());
            Assertions.assertNotNull(original, alias.getValue().toString());
            Assertions.assertNotNull(changed, alias.getKey().toString());
            List<BakedQuad> before = PartModelShadingTest.quads(original, data);
            List<BakedQuad> after = PartModelShadingTest.quads(changed, data);
            Assertions.assertEquals(original.useAmbientOcclusion(), changed.useAmbientOcclusion());
            Assertions.assertEquals(before.size(), after.size());
            for (int index = 0; index < before.size(); index++) {
                BakedQuad source = before.get(index);
                BakedQuad result = after.get(index);
                Assertions.assertTrue(source.isShade(), alias.getValue().toString());
                Assertions.assertFalse(result.isShade());
                Assertions.assertArrayEquals(source.getVertices(), result.getVertices());
                Assertions.assertEquals(source.hasAmbientOcclusion(), result.hasAmbientOcclusion());
                Assertions.assertEquals(source.getTintIndex(), result.getTintIndex());
                Assertions.assertEquals(source.getDirection(), result.getDirection());
                Assertions.assertSame(source.getSprite(), result.getSprite());
                // 走 AE2 实际使用的旋转/重新烘焙链路，确保标志在六面安装后仍有效。
                for (Direction direction : Direction.values()) {
                    QuadEmitter emitter = new MeshBuilderImpl().getEmitter();
                    emitter.fromVanilla(result, null);
                    QuadRotator.get(direction, 0).transform(emitter);
                    BakedQuad rotated = emitter.toBakedQuad(result.getSprite());
                    Assertions.assertFalse(rotated.isShade());
                    Assertions.assertEquals(result.hasAmbientOcclusion(), rotated.hasAmbientOcclusion());
                }
            }
        }
    }

    private static List<BakedQuad> quads(BakedModel model, ModelData data) {
        return model.getQuads(Blocks.GLASS.defaultBlockState(), null, RandomSource.create(0), data, RenderType.cutout());
    }

    @Test
    void commonModelsInitializeWithoutClientRenderingClasses() throws Exception {
        String modelClassName = Pp2mpPartModels.class.getName();
        byte[] modelBytes;
        try (InputStream stream = Pp2mpPartModels.class.getResourceAsStream("Pp2mpPartModels.class")) {
            Assertions.assertNotNull(stream);
            modelBytes = stream.readAllBytes();
        }
        // 隔离加载公共记录类型，禁止其初始化或方法签名解析触及客户端实现。
        ClassLoader commonOnlyLoader = new ClassLoader(Pp2mpPartModels.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("net.minecraft.client.")
                        || name.startsWith("net.neoforged.neoforge.client.")
                        || name.startsWith(modelClassName + "$Client")) {
                    throw new ClassNotFoundException("Client rendering is unavailable: " + name);
                }
                if (!name.equals(modelClassName)) {
                    return super.loadClass(name, resolve);
                }
                synchronized (this.getClassLoadingLock(name)) {
                    Class<?> loaded = this.findLoadedClass(name);
                    if (loaded == null) {
                        loaded = this.defineClass(name, modelBytes, 0, modelBytes.length);
                    }
                    if (resolve) {
                        this.resolveClass(loaded);
                    }
                    return loaded;
                }
            }
        };
        Class<?> modelType = Class.forName(modelClassName, true, commonOnlyLoader);
        Assertions.assertNotSame(Pp2mpPartModels.class, modelType);
        modelType.getDeclaredFields();
        modelType.getDeclaredConstructors();
        modelType.getDeclaredMethods();
        RecordComponent[] components = modelType.getRecordComponents();
        Assertions.assertNotNull(components);
        Assertions.assertEquals(3, components.length);
        ResourceLocation front = ResourceLocation.fromNamespaceAndPath("ae2_batchcraft", "part/pp2mp_input");
        Object modelSet = modelType.getConstructor(ResourceLocation.class).newInstance(front);
        if (modelType.getMethod("getModels").invoke(modelSet) instanceof List<?> models) {
            Assertions.assertEquals(3, models.size());
            Assertions.assertSame(models.get(0),
                    modelType.getMethod("getModel", boolean.class, boolean.class).invoke(modelSet, false, false));
            Assertions.assertSame(models.get(1),
                    modelType.getMethod("getModel", boolean.class, boolean.class).invoke(modelSet, true, false));
            Assertions.assertSame(models.get(2),
                    modelType.getMethod("getModel", boolean.class, boolean.class).invoke(modelSet, true, true));
        } else {
            Assertions.fail("Part models must expose their three states");
        }
    }

    @Test
    void allPlacedPartModelsDisableOnlyDirectionalShade() throws Exception {
        Field loadersField = GeometryLoaderManager.class.getDeclaredField("LOADERS");
        loadersField.setAccessible(true);
        Object previousLoaders = loadersField.get(null);
        Map<ResourceLocation, IGeometryLoader<?>> loaders = new HashMap<>();
        ModelEvent.RegisterGeometryLoaders event = new ModelEvent.RegisterGeometryLoaders(loaders);
        Pp2mpPartModels.Client.registerGeometryLoaders(event);
        // FML 单元测试运行在服务端侧，直接注册加载器以避开被剥离的 @OnlyIn 注册入口。
        PartModelShadingTest.registerLoader(event, "pp2mp_unit_port",
                Pp2mpUnitPortPartModel.class.getName() + "$UnitPortGeometry");
        PartModelShadingTest.registerLoader(event, "pp2mp_unit_manager_frequency",
                Pp2mpUnitManagerModel.class.getName() + "$IndicatorLightGeometryLoader");
        loadersField.set(null, ImmutableMap.copyOf(loaders));
        NamedRenderTypeManager.init();
        ResourceLocation spriteId = ResourceLocation.fromNamespaceAndPath("ae2_batchcraft", "test/part");
        try (SpriteContents contents = new SpriteContents(spriteId, new FrameSize(16, 16),
                new NativeImage(16, 16, false), ResourceMetadata.EMPTY)) {
            TestBaker baker = new TestBaker(new TestSprite(contents));
            Set<ResourceLocation> locations = new LinkedHashSet<>();
            for (Pp2mpPartModels models : List.of(Pp2mpInputModel.MODELS, Pp2mpOutputModel.MODELS,
                    Pp2mpResourceIntakeModel.MODELS)) {
                for (IPartModel model : models.getModels()) {
                    locations.addAll(model.getModels());
                }
            }
            for (Pp2mpUnitPortPartModel models : List.of(Pp2mpUnitPortPartModel.MODEL_TRANSFER,
                    Pp2mpUnitPortPartModel.MODEL_DROP, Pp2mpUnitPortPartModel.MODEL_PLACE,
                    Pp2mpUnitPortPartModel.MODEL_PROVIDER, Pp2mpUnitPortPartModel.MODEL_RETURN,
                    Pp2mpUnitPortPartModel.MODEL_EXTRACT, Pp2mpUnitPortPartModel.MODEL_BREAK,
                    Pp2mpUnitPortPartModel.MODEL_COLLECT, Pp2mpUnitPortPartModel.MODEL_REDSTONE)) {
                for (IPartModel model : models.models()) {
                    locations.addAll(model.getModels());
                }
            }
            locations.addAll(List.of(Pp2mpUnitManagerModel.BASE_MODEL, Pp2mpUnitManagerModel.GLASS_MODEL,
                    Pp2mpUnitManagerModel.FREQUENCY_MODEL));
            Assertions.assertEquals(19, locations.size());
            for (long flags : new long[]{0L, 0x1234L, 0x11234L}) {
                ModelData data = ModelData.builder()
                        .with(P2PTunnelFrequencyModelData.FREQUENCY, flags)
                        .with(Pp2mpUnitPortPart.PP2MP_UNIT_ID, flags)
                        .with(Pp2mpUnitManagerModel.PP2MP_UNIT_ID, flags).build();
                for (ResourceLocation location : locations) {
                    BakedModel model = baker.bake(location, BlockModelRotation.X0_Y0, baker.getModelTextureGetter());
                    Assertions.assertNotNull(model, location.toString());
                    List<BakedQuad> quads = PartModelShadingTest.quads(model, data);
                    Assertions.assertFalse(quads.isEmpty(), location.toString());
                    for (BakedQuad quad : quads) {
                        Assertions.assertFalse(quad.isShade(), location.toString());
                    }
                }
                PartModelShadingTest.assertSharedModelsUnchanged(baker, data);
            }
        } finally {
            loadersField.set(null, previousLoaders);
        }
    }

    private static final class TestSprite extends TextureAtlasSprite {
        private TestSprite(SpriteContents contents) {
            super(InventoryMenu.BLOCK_ATLAS, contents, 16, 16, 0, 0);
        }
    }

    private static final class TestBaker implements ModelBaker {
        private final Map<ResourceLocation, UnbakedModel> models = new HashMap<>();
        private final TextureAtlasSprite sprite;

        private TestBaker(TextureAtlasSprite sprite) {
            this.sprite = sprite;
            this.models.put(ResourceLocation.withDefaultNamespace("block/block"), BlockModel.fromString("{}"));
            this.models.put(P2PModels.MODEL_FREQUENCY, new P2PTunnelFrequencyModel());
        }

        @Override
        public UnbakedModel getModel(ResourceLocation location) {
            UnbakedModel existing = this.models.get(location);
            if (existing != null) {
                return existing;
            }
            String path = "/assets/" + location.getNamespace() + "/models/" + location.getPath() + ".json";
            Class<?> resourceOwner = "ae2".equals(location.getNamespace()) ? AppEng.class : PartModelShadingTest.class;
            try (InputStream stream = resourceOwner.getResourceAsStream(path)) {
                Assertions.assertNotNull(stream, path);
                BlockModel model = BlockModel.fromString(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                this.models.put(location, model);
                model.resolveParents(this::getModel);
                return model;
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public UnbakedModel getTopLevelModel(ModelResourceLocation location) {
            return this.getModel(location.id());
        }

        @Override
        @Deprecated
        public BakedModel bake(ResourceLocation location, ModelState state) {
            return this.bake(location, state, this.getModelTextureGetter());
        }

        @Override
        public BakedModel bake(ResourceLocation location, ModelState state, Function<Material, TextureAtlasSprite> sprites) {
            return this.bakeUncached(this.getModel(location), state, sprites);
        }

        @Override
        public BakedModel bakeUncached(UnbakedModel model, ModelState state, Function<Material, TextureAtlasSprite> sprites) {
            return model.bake(this, sprites, state);
        }

        @Override
        public Function<Material, TextureAtlasSprite> getModelTextureGetter() {
            return material -> this.sprite;
        }
    }
}
