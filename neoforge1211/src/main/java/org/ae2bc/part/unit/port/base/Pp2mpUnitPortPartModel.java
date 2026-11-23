package org.ae2bc.part.unit.port.base;

import appeng.api.parts.IPartModel;
import appeng.api.parts.PartModels;
import appeng.api.util.AEColor;
import appeng.client.render.cablebus.CubeBuilder;
import appeng.core.AppEng;
import appeng.parts.PartModel;
import appeng.util.Platform;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
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
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.core.constant.UnitManagerConstant;
import org.ae2bc.part.Pp2mpIndicatorColor;
import org.ae2bc.part.Pp2mpPartModels;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

public record Pp2mpUnitPortPartModel(IPartModel off, IPartModel on, IPartModel channel) {
    private static final float UNIT_ID_INDICATOR_MIN_Z = 3.0f;
    private static final float UNIT_ID_INDICATOR_MAX_Z = 3.01f;

    // 输出型单元端口
    private static final ResourceLocation MODEL_TRANSFER_RL = ResourceLocation.fromNamespaceAndPath(
            Ae2bcMod.MOD_ID, "part/pp2mp_unit_port_transfer"
    );
    public static final Pp2mpUnitPortPartModel MODEL_TRANSFER = new Pp2mpUnitPortPartModel(MODEL_TRANSFER_RL);
    private static final ResourceLocation MODEL_DROP_RL = ResourceLocation.fromNamespaceAndPath(
            Ae2bcMod.MOD_ID, "part/pp2mp_unit_port_drop"
    );
    public static final Pp2mpUnitPortPartModel MODEL_DROP = new Pp2mpUnitPortPartModel(MODEL_DROP_RL);
    private static final ResourceLocation MODEL_PLACE_RL = ResourceLocation.fromNamespaceAndPath(
            Ae2bcMod.MOD_ID, "part/pp2mp_unit_port_place"
    );
    public static final Pp2mpUnitPortPartModel MODEL_PLACE = new Pp2mpUnitPortPartModel(MODEL_PLACE_RL);
    private static final ResourceLocation MODEL_PROVIDER_RL = ResourceLocation.fromNamespaceAndPath(
            Ae2bcMod.MOD_ID, "part/pp2mp_unit_port_provider"
    );
    public static final Pp2mpUnitPortPartModel MODEL_PROVIDER = new Pp2mpUnitPortPartModel(MODEL_PROVIDER_RL);

    // 输入型单元端口
    private static final ResourceLocation MODEL_RETURN_RL = ResourceLocation.fromNamespaceAndPath(
            Ae2bcMod.MOD_ID, "part/pp2mp_unit_port_return"
    );
    public static final Pp2mpUnitPortPartModel MODEL_RETURN = new Pp2mpUnitPortPartModel(MODEL_RETURN_RL);
    private static final ResourceLocation MODEL_EXTRACT_RL = ResourceLocation.fromNamespaceAndPath(
            Ae2bcMod.MOD_ID, "part/pp2mp_unit_port_extract"
    );
    public static final Pp2mpUnitPortPartModel MODEL_EXTRACT = new Pp2mpUnitPortPartModel(MODEL_EXTRACT_RL);
    private static final ResourceLocation MODEL_BREAK_RL = ResourceLocation.fromNamespaceAndPath(
            Ae2bcMod.MOD_ID, "part/pp2mp_unit_port_break"
    );
    public static final Pp2mpUnitPortPartModel MODEL_BREAK = new Pp2mpUnitPortPartModel(MODEL_BREAK_RL);
    private static final ResourceLocation MODEL_COLLECT_RL = ResourceLocation.fromNamespaceAndPath(
            Ae2bcMod.MOD_ID, "part/pp2mp_unit_port_collect"
    );
    public static final Pp2mpUnitPortPartModel MODEL_COLLECT = new Pp2mpUnitPortPartModel(MODEL_COLLECT_RL);

    // 红石单元端口
    private static final ResourceLocation MODEL_REDSTONE_RL = ResourceLocation.fromNamespaceAndPath(
            Ae2bcMod.MOD_ID, "part/pp2mp_unit_port_redstone"
    );
    public static final Pp2mpUnitPortPartModel MODEL_REDSTONE = new Pp2mpUnitPortPartModel(MODEL_REDSTONE_RL);

    Pp2mpUnitPortPartModel(ResourceLocation front) {
        this(
                new PartModel(Pp2mpPartModels.STATUS_OFF, Pp2mpPartModels.FREQUENCY, front),
                new PartModel(Pp2mpPartModels.STATUS_ON, Pp2mpPartModels.FREQUENCY, front),
                new PartModel(Pp2mpPartModels.STATUS_HAS_CHANNEL, Pp2mpPartModels.FREQUENCY, front)
        );
    }

    public static void register(Pp2mpUnitPortPartModel models) {
        PartModels.registerModels(models.models().stream().flatMap(
                model -> model.getModels().stream()
        ).toList());
    }

    @OnlyIn(Dist.CLIENT)
    public static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(ResourceLocation.fromNamespaceAndPath(Ae2bcMod.MOD_ID, "pp2mp_unit_port"),
                UnitPortGeometry.INSTANCE);
    }

    public IPartModel getModel(boolean hasPower, boolean hasChannel) {
        return hasPower && hasChannel ? channel : hasPower ? on : off;
    }

    public List<IPartModel> models() {
        return List.of(off, on, channel);
    }

    private enum UnitPortGeometry implements IGeometryLoader<UnitPortModelGeometry> {
        INSTANCE;

        @Override
        public @NonNull UnitPortModelGeometry read(
                @NonNull JsonObject json, @NonNull JsonDeserializationContext context
        ) {
            JsonObject bodyJson = json.deepCopy();
            bodyJson.remove("loader");
            return new UnitPortModelGeometry(BlockModel.fromString(bodyJson.toString()));
        }
    }

    private record UnitPortModelGeometry(BlockModel bodyModel) implements IUnbakedGeometry<UnitPortModelGeometry> {
        private static final Material UNIT_ID_TEXTURE = new Material(
                InventoryMenu.BLOCK_ATLAS, AppEng.makeId("part/p2p_tunnel_frequency")
        );

        @Override
        public void resolveParents(
                @NonNull Function<ResourceLocation, UnbakedModel> modelGetter,
                @NonNull IGeometryBakingContext context
        ) {
            this.bodyModel.resolveParents(modelGetter);
        }

        @Override
        public @NonNull BakedModel bake(
                @NonNull IGeometryBakingContext context, @NonNull ModelBaker baker,
                @NonNull Function<Material, TextureAtlasSprite> spriteGetter,
                @NonNull ModelState modelState, @NonNull ItemOverrides overrides
        ) {
            BakedModel bakedBody = this.bodyModel.bake(baker, spriteGetter, modelState);
            if (bakedBody == null) {
                throw new IllegalStateException("Failed to bake unit port body model");
            }
            return Pp2mpPartModels.Client.withoutShade(new UnitPortBakedModel(bakedBody, spriteGetter.apply(UNIT_ID_TEXTURE)));
        }
    }

    private static final class UnitPortBakedModel extends BakedModelWrapper<BakedModel> implements IDynamicBakedModel {
        private final Cache<Long, List<BakedQuad>> quadCache = CacheBuilder.newBuilder()
                .maximumSize(100)
                .build();
        private final TextureAtlasSprite texture;

        private UnitPortBakedModel(BakedModel originalModel, TextureAtlasSprite texture) {
            super(originalModel);
            this.texture = texture;
        }

        private static void addUnitIdCells(CubeBuilder builder, int colorIndex) {
            float colorU = colorIndex % 2;
            float colorV = 1 - (float) (colorIndex / 2);
            addUnitIdCell(builder, UnitManagerConstant.ID_MIN, UnitManagerConstant.FRAME_MIN, colorU, colorV);
            addUnitIdCell(builder, UnitManagerConstant.INNER_MAX, UnitManagerConstant.ID_MIN, colorU, colorV);
            addUnitIdCell(builder, UnitManagerConstant.ID_MIN, UnitManagerConstant.INNER_MAX, colorU, colorV);
            addUnitIdCell(builder, UnitManagerConstant.FRAME_MIN, UnitManagerConstant.ID_MIN, colorU, colorV);
        }

        private static void addUnitIdCell(CubeBuilder builder, float baseU, float baseV, float colorU, float colorV) {
            float u = baseU + colorU;
            float v = baseV + colorV;
            builder.addCube(u, v, UNIT_ID_INDICATOR_MIN_Z,
                    u + 1, v + 1, UNIT_ID_INDICATOR_MAX_Z);
        }

        @Override
        public @NonNull List<BakedQuad> getQuads(
                @Nullable BlockState state, @Nullable Direction direction,
                @NonNull RandomSource random, @NonNull ModelData modelData,
                @Nullable RenderType renderType
        ) {
            List<BakedQuad> quads = new ArrayList<>(super.getQuads(state, direction, random, modelData, renderType));
            if (direction != null) {
                return quads;
            }

            Long flags = modelData.get(Pp2mpUnitPortPart.PP2MP_UNIT_ID);
            if (flags == null) {
                return quads;
            }
            try {
                quads.addAll(this.quadCache.get(flags, () -> this.buildIdentityQuads(flags)));
            } catch (ExecutionException ignored) {
                // 可选的身份覆盖层失败时，继续渲染原始端口模型。
            }
            return quads;
        }

        private List<BakedQuad> buildIdentityQuads(long flags) {
            AEColor[] colors = Platform.p2p().toColors((short) (flags & 0xffff));
            boolean active = (flags & 0x10000L) != 0;
            CubeBuilder builder = new CubeBuilder();
            builder.setTexture(this.texture);
            builder.setEmissiveMaterial(active);
            for (int colorIndex = 0; colorIndex < 4; colorIndex++) {
                Pp2mpIndicatorColor.apply(builder, colors[colorIndex], active);
                addUnitIdCells(builder, colorIndex);
            }
            builder.setEmissiveMaterial(false);
            return List.copyOf(builder.getOutput());
        }
    }
}
