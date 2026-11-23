package org.ae2bc.part.unit.manager;

import appeng.api.ids.AEBlockIds;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartModel;
import appeng.api.parts.PartModels;
import appeng.api.util.AEColor;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.client.render.cablebus.CubeBuilder;
import appeng.client.render.cablebus.P2PTunnelFrequencyModelData;
import appeng.core.AppEng;
import appeng.parts.PartModel;
import appeng.util.Platform;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.core.constant.UnitManagerConstant;
import org.ae2bc.part.Pp2mpIndicatorColor;
import org.ae2bc.part.Pp2mpPartModels;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class Pp2mpUnitManagerModel {
    public static final ResourceLocation BASE_MODEL = ResourceLocation.fromNamespaceAndPath(
            Ae2bcMod.MOD_ID,
            "part/pp2mp_unit_manager"
    );
    public static final ResourceLocation GLASS_MODEL = ResourceLocation.fromNamespaceAndPath(
            Ae2bcMod.MOD_ID,
            "part/pp2mp_unit_manager_glass"
    );
    public static final ResourceLocation FREQUENCY_MODEL = ResourceLocation.fromNamespaceAndPath(
            Ae2bcMod.MOD_ID,
            "part/pp2mp_unit_manager_frequency"
    );
    public static final ModelProperty<Long> PP2MP_UNIT_ID = new ModelProperty<>();
    private static final IPartModel MODEL = new PartModel(BASE_MODEL, GLASS_MODEL, FREQUENCY_MODEL);

    /**
     * 注册模型
     */
    public static void register() {
        PartModels.registerModels(MODEL.getModels());
    }

    /**
     * 修改模型烘焙结果。
     */
    @OnlyIn(Dist.CLIENT)
    public static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        // 获取需要组装的模型
        List<ResourceLocation> locations = List.of(BASE_MODEL, GLASS_MODEL, FREQUENCY_MODEL);
        Map<ResourceLocation, BakedModel> locationMap = event.getModels().entrySet().stream().filter(entry -> {
            ModelResourceLocation location = entry.getKey();
            return locations.contains(location.id())
                    && ModelResourceLocation.STANDALONE_VARIANT.equals(location.getVariant());
        }).collect(Collectors.toMap(entry -> entry.getKey().id(), Map.Entry::getValue));
        BakedModel baseModel = locationMap.get(BASE_MODEL);
        BakedModel glassModel = locationMap.get(GLASS_MODEL);
        BakedModel frequencyModel = locationMap.get(FREQUENCY_MODEL);

        if (baseModel == null || glassModel == null || frequencyModel == null) {
            Ae2bcMod.LOGGER.error("Cannot find unit manager baked models");
            return;
        }

        Map<BakedModel, BakedModel> wrappedModels = new IdentityHashMap<>();
        for (Map.Entry<ModelResourceLocation, BakedModel> entry : event.getModels().entrySet()) {
            if (!entry.getKey().id().equals(AEBlockIds.CABLE_BUS)) {
                continue;
            }

            BakedModel wrappedModel = wrappedModels.computeIfAbsent(entry.getValue(), originalModel ->
                    new Pp2mpUnitManagerModel.ManagerBakedModel(originalModel, baseModel, glassModel, frequencyModel)
            );

            entry.setValue(wrappedModel);
        }
    }

    /**
     * 注册模型加载器
     */
    @OnlyIn(Dist.CLIENT)
    public static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(ResourceLocation.fromNamespaceAndPath(
                Ae2bcMod.MOD_ID, "pp2mp_unit_manager_frequency"
        ), IndicatorLightGeometryLoader.INSTANCE);
    }

    public static final class ManagerBakedModel extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private final BakedModel frequencyModel;
        private final List<BakedQuad> baseQuads;
        private final List<BakedQuad> glassQuads;

        public ManagerBakedModel(
                BakedModel originalModel, BakedModel baseModel,
                BakedModel glassModel, BakedModel frequencyModel
        ) {
            super(originalModel);
            this.frequencyModel = frequencyModel;
            this.baseQuads = List.copyOf(baseModel.getQuads(
                    null, null, RandomSource.create(), ModelData.EMPTY, RenderType.cutout()
            ));
            this.glassQuads = List.copyOf(glassModel.getQuads(
                    null, null, RandomSource.create(), ModelData.EMPTY, RenderType.cutout()
            ));
        }

        @Override
        public @NonNull ModelData getModelData(
                @NonNull BlockAndTintGetter level, @NonNull BlockPos pos,
                @NonNull BlockState state, @NonNull ModelData modelData
        ) {
            ModelData delegatedData = super.getModelData(level, pos, state, modelData);

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof CableBusBlockEntity cableBus)) {
                return delegatedData;
            }

            IPart centerPart = cableBus.getCableBus().getPart(null);
            if (!(centerPart instanceof Pp2mpUnitManagerPart manager)) {
                return delegatedData;
            }

            ModelData managerData = manager.getModelData();
            Long frequencyValue = managerData.get(P2PTunnelFrequencyModelData.FREQUENCY);
            Long unitIdValue = managerData.get(PP2MP_UNIT_ID);

            long frequencyFlags = frequencyValue == null ? 0L : frequencyValue;
            long unitIdFlags = unitIdValue == null ? 0L : unitIdValue;

            return delegatedData.derive()
                    .with(P2PTunnelFrequencyModelData.FREQUENCY, frequencyFlags)
                    .with(PP2MP_UNIT_ID, unitIdFlags)
                    .build();
        }

        @Override
        public @NonNull List<BakedQuad> getQuads(
                @Nullable BlockState state, @Nullable Direction direction,
                @NonNull RandomSource random, @NonNull ModelData data,
                @Nullable RenderType renderType
        ) {
            List<BakedQuad> original = super.getQuads(state, direction, random, data, renderType);

            if (direction != null || (renderType != null && renderType != RenderType.cutout())) {
                return original;
            }

            Long frequency = data.get(P2PTunnelFrequencyModelData.FREQUENCY);
            Long unitId = data.get(PP2MP_UNIT_ID);

            if (unitId == null) {
                return original;
            }

            List<BakedQuad> quads = new ArrayList<>(original);
            quads.addAll(this.baseQuads);
            quads.addAll(this.glassQuads);
            ModelData frequencyData = ModelData.builder()
                    .with(P2PTunnelFrequencyModelData.FREQUENCY, frequency != null ? frequency : 0L)
                    .with(PP2MP_UNIT_ID, unitId)
                    .build();
            quads.addAll(this.frequencyModel.getQuads(
                    null, null, random,
                    frequencyData, RenderType.cutout()
            ));
            return quads;
        }

    }


    public static final class IndicatorLightGeometryLoader implements IUnbakedGeometry<IndicatorLightGeometryLoader>, IGeometryLoader<IndicatorLightGeometryLoader> {
        // 客户端加载几何时才初始化纹理和加载器，公共模型标识可在服务端使用。
        private static final IndicatorLightGeometryLoader INSTANCE = new IndicatorLightGeometryLoader();
        private static final Material FREQ_TEXTURE = new Material(
                InventoryMenu.BLOCK_ATLAS, AppEng.makeId("part/p2p_tunnel_frequency"));

        @Override
        public @NonNull IndicatorLightGeometryLoader read(
                @NonNull JsonObject json, @NonNull JsonDeserializationContext context
        ) {
            return IndicatorLightGeometryLoader.INSTANCE;
        }

        @Override
        public @NonNull BakedModel bake(
                @NonNull IGeometryBakingContext context, @NonNull ModelBaker baker,
                @NonNull Function<Material, TextureAtlasSprite> spriteGetter,
                @NonNull ModelState modelState, @NonNull ItemOverrides overrides
        ) {
            return Pp2mpPartModels.Client.withoutShade(new IndicatorLightModel(spriteGetter.apply(FREQ_TEXTURE)));
        }
    }

    private static final class IndicatorLightModel implements IDynamicBakedModel {
        private final TextureAtlasSprite texture;
        private final Cache<IndicatorState, List<BakedQuad>> quadCache = CacheBuilder.newBuilder()
                .maximumSize(100)
                .build();

        private IndicatorLightModel(TextureAtlasSprite texture) {
            this.texture = texture;
        }

        @Override
        public @NonNull List<BakedQuad> getQuads(
                @Nullable BlockState state, @Nullable Direction side,
                @NonNull RandomSource random, @NonNull ModelData modelData,
                @Nullable RenderType renderType
        ) {
            if (side != null || !modelData.has(P2PTunnelFrequencyModelData.FREQUENCY)) {
                return Collections.emptyList();
            }
            Long frequencyFlags = modelData.get(P2PTunnelFrequencyModelData.FREQUENCY);
            Long unitIdFlags = modelData.get(PP2MP_UNIT_ID);
            long frequency = frequencyFlags == null ? 0 : frequencyFlags;
            long unitId = unitIdFlags == null ? 0 : unitIdFlags;
            IndicatorState cacheState = new IndicatorState(frequency, unitId);
            List<BakedQuad> cached = this.quadCache.getIfPresent(cacheState);
            if (cached != null) {
                return cached;
            }
            List<BakedQuad> built = this.build(frequency, unitId);
            this.quadCache.put(cacheState, built);
            return built;
        }

        private List<BakedQuad> build(long frequencyFlags, long unitIdFlags) {
            CubeBuilder builder = new CubeBuilder();
            builder.setTexture(this.texture);
            this.addFrequencyCorners(builder, frequencyFlags);
            this.addPatternP2PUnitCenter(builder, unitIdFlags);
            builder.setEmissiveMaterial(false);
            return List.copyOf(builder.getOutput());
        }

        private record IndicatorState(long frequency, long unitId) {
        }

        private void addFrequencyCorners(CubeBuilder builder, long flags) {
            AEColor[] colors = this.colors(flags);
            boolean active = this.isActive(flags);
            builder.setEmissiveMaterial(active);
            for (int colorIndex = 0; colorIndex < 4; colorIndex++) {
                Pp2mpIndicatorColor.apply(builder, colors[colorIndex], active);
                this.addColorToEveryCornerOnEveryFace(builder, colorIndex);
            }
        }

        private void addPatternP2PUnitCenter(CubeBuilder builder, long flags) {
            AEColor[] colors = this.colors(flags);
            boolean active = this.isActive(flags);
            builder.setEmissiveMaterial(active);
            for (int colorIndex = 0; colorIndex < 4; colorIndex++) {
                Pp2mpIndicatorColor.apply(builder, colors[colorIndex], active);
                float u = UnitManagerConstant.ID_MIN + colorIndex % 2;
                float v = UnitManagerConstant.ID_MIN + 1 - (float) (colorIndex / 2);
                this.addCellToEveryFace(builder, u, v);
            }
        }

        private void addColorToEveryCornerOnEveryFace(CubeBuilder builder, int colorIndex) {
            float cellU = colorIndex % 2;
            float cellV = 1 - (float) (colorIndex / 2);
            for (int cornerU = 0; cornerU < 2; cornerU++) {
                for (int cornerV = 0; cornerV < 2; cornerV++) {
                    float u = (cornerU == 0
                            ? UnitManagerConstant.FRAME_MIN
                            : UnitManagerConstant.INNER_MAX) + cellU;
                    float v = (cornerV == 0
                            ? UnitManagerConstant.FRAME_MIN
                            : UnitManagerConstant.INNER_MAX) + cellV;
                    this.addCellToEveryFace(builder, u, v);
                }
            }
        }

        private void addCellToEveryFace(CubeBuilder builder, float u, float v) {
            float min = UnitManagerConstant.FRAME_MIN;
            float max = UnitManagerConstant.FRAME_MAX;
            float depth = UnitManagerConstant.INDICATOR_DEPTH;
            builder.addCube(u, v, min - depth, u + 1, v + 1, min);
            builder.addCube(u, v, max, u + 1, v + 1, max + depth);
            builder.addCube(min - depth, v, u, min, v + 1, u + 1);
            builder.addCube(max, v, u, max + depth, v + 1, u + 1);
            builder.addCube(u, min - depth, v, u + 1, min, v + 1);
            builder.addCube(u, max, v, u + 1, max + depth, v + 1);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return false;
        }

        @Override
        public boolean isGui3d() {
            return true;
        }

        @Override
        public boolean usesBlockLight() {
            return false;
        }

        @Override
        public boolean isCustomRenderer() {
            return true;
        }

        @Override
        @Deprecated
        public @NonNull TextureAtlasSprite getParticleIcon() {
            return this.texture;
        }

        @Override
        public @NonNull ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }

        public AEColor[] colors(long flags) {
            return Platform.p2p().toColors((short) (flags & 0xffff));
        }

        public boolean isActive(long flags) {
            return (flags & 0x10000L) != 0;
        }

    }
}
