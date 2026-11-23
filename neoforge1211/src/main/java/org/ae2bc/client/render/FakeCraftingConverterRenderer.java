package org.ae2bc.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.ae2bc.fake_crafting_converter.FakeCraftingConverterBlockEntity;
import org.ae2bc.fake_crafting_converter.FakeCraftingConverterModel;
import org.ae2bc.registry.BlockRegistry;
import org.jspecify.annotations.NonNull;

public final class FakeCraftingConverterRenderer
        implements BlockEntityRenderer<FakeCraftingConverterBlockEntity> {
    private static final int BOB_PERIOD_TICKS = 80;
    private static final int ROTATION_PERIOD_TICKS = 160;
    private static final float BOB_AMPLITUDE = 0.5f / 16.0f;
    private final BlockRenderDispatcher blockRenderer;

    public FakeCraftingConverterRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    /* 注册假合成转换器中央纸张的客户端渲染 */
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                BlockRegistry.FAKE_CRAFTING_CONVERTER_ENTITY.get(),
                FakeCraftingConverterRenderer::new
        );
    }

    /* 从上方俯视为顺时针，按周期取余避免长时间运行造成浮点抖动。 */
    static void applyPaperAnimation(PoseStack poseStack, long gameTime, float partialTick) {
        float bobTime = gameTime % BOB_PERIOD_TICKS + partialTick;
        float bob = Mth.sin(bobTime * Mth.TWO_PI / BOB_PERIOD_TICKS) * BOB_AMPLITUDE;
        float rotationTime = gameTime % ROTATION_PERIOD_TICKS + partialTick;
        float rotation = -rotationTime * 360.0f / ROTATION_PERIOD_TICKS;
        poseStack.translate(0.5f, FakeCraftingConverterModel.PAPER_CENTER_Y + bob, 0.5f);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
        poseStack.translate(-0.5f, -FakeCraftingConverterModel.PAPER_CENTER_Y, -0.5f);
    }

    /* 单独绘制原版纸张，围绕自身中心持续旋转并上下浮动。 */
    @Override
    public void render(
            FakeCraftingConverterBlockEntity blockEntity,
            float partialTick, @NonNull PoseStack poseStack,
            @NonNull MultiBufferSource bufferSource,
            int packedLight, int packedOverlay
    ) {
        Level level = blockEntity.getLevel();
        if (level == null) {
            return;
        }
        BlockState state = blockEntity.getBlockState();
        BakedModel model = this.blockRenderer.getBlockModel(state);
        if (!(model instanceof FakeCraftingConverterModel.ConverterBakedModel converter)) {
            return;
        }
        poseStack.pushPose();
        FakeCraftingConverterRenderer.applyPaperAnimation(poseStack, level.getGameTime(), partialTick);
        this.blockRenderer.getModelRenderer().renderModel(
                poseStack.last(), bufferSource.getBuffer(Sheets.cutoutBlockSheet()), state,
                converter.getPaperModel(), 1, 1, 1, packedLight, packedOverlay,
                ModelData.EMPTY, RenderType.cutout()
        );
        poseStack.popPose();
    }
}
