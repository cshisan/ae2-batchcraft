package org.ae2bc.client.screen;

import appeng.api.stacks.AEItemKey;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.ae2bc.fake_crafting_converter.FakeCraftingConverterMarkerSlot;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.mockito.MockMakers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.List;

class FakeCraftingMarkerRendererTest {
    /* 无标记、实物占用或槽位禁用时，不创建绘图区也不干扰原版渲染。 */
    @Test
    void hiddenMarkersDoNotTouchGraphicsOrAllocateTargets() throws ReflectiveOperationException {
        GuiGraphics graphics = Mockito.mock(GuiGraphics.class);
        FakeCraftingConverterMarkerSlot slot = Mockito.mock(
                FakeCraftingConverterMarkerSlot.class, Mockito.withSettings().mockMaker(MockMakers.INLINE));
        Mockito.when(slot.isActive()).thenReturn(true);
        Mockito.when(slot.getItem()).thenReturn(ItemStack.EMPTY);
        try (FakeCraftingMarkerRenderer renderer = new FakeCraftingMarkerRenderer(List.of(slot))) {
            renderer.render(graphics, 0, 0, 176, 166);
            Mockito.when(slot.getMarkerKey()).thenReturn(AEItemKey.of(Items.STONE));
            Mockito.when(slot.getItem()).thenReturn(new ItemStack(Items.STONE));
            renderer.render(graphics, 0, 0, 176, 166);
            Mockito.when(slot.getItem()).thenReturn(ItemStack.EMPTY);
            Mockito.when(slot.isActive()).thenReturn(false);
            renderer.render(graphics, 0, 0, 176, 166);
            Assertions.assertNull(FakeCraftingMarkerRendererTest.targetField().get(renderer));
            Mockito.verifyNoInteractions(graphics);
        }
    }

    /* 连续帧复用显存，缩放只调整尺寸，关闭时释放旧纹理且不重复释放。 */
    @Test
    void targetIsReusedResizedAndReleased() throws ReflectiveOperationException {
        TextureTarget first = Mockito.mock(TextureTarget.class);
        first.width = 352;
        first.height = 332;
        Mockito.doAnswer(invocation -> {
            first.width = invocation.getArgument(0);
            first.height = invocation.getArgument(1);
            return null;
        }).when(first).resize(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean());
        try (FakeCraftingMarkerRenderer renderer = new FakeCraftingMarkerRenderer(List.of())) {
            FakeCraftingMarkerRendererTest.targetField().set(renderer, first);
            Assertions.assertSame(first, renderer.prepareTarget(352, 332));
            Mockito.verify(first, Mockito.never()).resize(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean());
            Assertions.assertSame(first, renderer.prepareTarget(528, 498));
            Assertions.assertSame(first, renderer.prepareTarget(528, 498));
            Mockito.verify(first).resize(528, 498, Minecraft.ON_OSX);
            renderer.close();
            renderer.close();
            Mockito.verify(first).destroyBuffers();
            Assertions.assertNull(FakeCraftingMarkerRendererTest.targetField().get(renderer));
        }
    }

    /* 特殊物品绘制失败时也恢复主绘图区及矩阵，避免后续界面被离屏状态污染。 */
    @Test
    void itemRenderFailureRestoresFramebufferMatricesAndColor() throws ReflectiveOperationException {
        GuiGraphics graphics = Mockito.mock(GuiGraphics.class);
        Mockito.when(graphics.bufferSource()).thenReturn(Mockito.mock(MultiBufferSource.BufferSource.class));
        FakeCraftingConverterMarkerSlot slot = Mockito.mock(
                FakeCraftingConverterMarkerSlot.class, Mockito.withSettings().mockMaker(MockMakers.INLINE));
        Mockito.when(slot.isActive()).thenReturn(true);
        Mockito.when(slot.getItem()).thenReturn(ItemStack.EMPTY);
        Mockito.when(slot.getMarkerKey()).thenReturn(AEItemKey.of(Items.STONE));
        Mockito.when(slot.getMarkerDisplayStack()).thenReturn(new ItemStack(Items.STONE));
        Minecraft minecraft = Mockito.mock(Minecraft.class);
        Window window = Mockito.mock(Window.class, Mockito.withSettings().mockMaker(MockMakers.INLINE));
        Mockito.when(minecraft.getWindow()).thenReturn(window);
        Mockito.when(window.getGuiScale()).thenReturn(2.0);
        Matrix4f projection = new Matrix4f().scaling(2);
        Matrix4fStack modelView = new Matrix4fStack(8);
        modelView.translate(10, 20, -11000);
        Matrix4f originalModelView = new Matrix4f(modelView);
        IllegalStateException failure = new IllegalStateException("Item renderer failed");
        ItemRenderer itemRenderer = Mockito.mock(ItemRenderer.class);
        Mockito.when(minecraft.getItemRenderer()).thenReturn(itemRenderer);
        Mockito.when(itemRenderer.getModel(Mockito.any(ItemStack.class), Mockito.isNull(), Mockito.isNull(), Mockito.eq(0)))
                .thenThrow(failure);
        TextureTarget target = Mockito.mock(TextureTarget.class);
        target.width = 352;
        target.height = 332;
        try (MockedStatic<Minecraft> client = Mockito.mockStatic(
                Minecraft.class, Mockito.withSettings().mockMaker(MockMakers.INLINE));
             MockedStatic<RenderSystem> renderSystem = Mockito.mockStatic(
                     RenderSystem.class, Mockito.withSettings().mockMaker(MockMakers.INLINE));
             MockedStatic<GL11> gl = Mockito.mockStatic(
                     GL11.class, Mockito.withSettings().mockMaker(MockMakers.INLINE));
             MockedStatic<GlStateManager> glState = Mockito.mockStatic(
                     GlStateManager.class, Mockito.withSettings().mockMaker(MockMakers.INLINE));
             FakeCraftingMarkerRenderer renderer = new FakeCraftingMarkerRenderer(List.of(slot))) {
            FakeCraftingMarkerRendererTest.targetField().set(renderer, target);
            client.when(Minecraft::getInstance).thenReturn(minecraft);
            renderSystem.when(RenderSystem::getProjectionMatrix).thenReturn(projection);
            renderSystem.when(RenderSystem::getVertexSorting).thenReturn(VertexSorting.ORTHOGRAPHIC_Z);
            renderSystem.when(RenderSystem::getModelViewStack).thenReturn(modelView);
            renderSystem.when(RenderSystem::getShaderColor).thenReturn(new float[]{0.5F, 0.6F, 0.7F, 0.8F});
            renderSystem.when(() -> RenderSystem.getShaderTexture(0)).thenReturn(17);
            gl.when(() -> GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)).thenReturn(42);
            gl.when(() -> GL11.glIsEnabled(GL11.GL_BLEND)).thenReturn(true);
            gl.when(() -> GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)).thenReturn(true);

            Assertions.assertSame(failure, Assertions.assertThrows(IllegalStateException.class,
                    () -> renderer.render(graphics, 0, 0, 176, 166)));
            Assertions.assertEquals(originalModelView, new Matrix4f(modelView));
            Mockito.verify(target).bindWrite(true);
            renderSystem.verify(() -> RenderSystem.setProjectionMatrix(projection, VertexSorting.ORTHOGRAPHIC_Z));
            renderSystem.verify(() -> RenderSystem.setShaderColor(0.5F, 0.6F, 0.7F, 0.8F));
            renderSystem.verify(() -> RenderSystem.setShaderTexture(0, 17));
            renderSystem.verify(RenderSystem::enableBlend);
            renderSystem.verify(RenderSystem::disableDepthTest);
            glState.verify(() -> GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 42));
            glState.verify(GlStateManager::_enableScissorTest);
        }
    }

    private static Field targetField() throws ReflectiveOperationException {
        Field field = FakeCraftingMarkerRenderer.class.getDeclaredField("target");
        field.setAccessible(true);
        return field;
    }
}
