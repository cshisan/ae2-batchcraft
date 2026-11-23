package org.ae2bc.client.screen;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.ae2bc.fake_crafting_converter.FakeCraftingConverterMarkerSlot;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import java.util.List;

final class FakeCraftingMarkerRenderer implements AutoCloseable {
    private final List<Slot> slots;
    private @Nullable TextureTarget target;

    FakeCraftingMarkerRenderer(List<Slot> slots) {
        this.slots = List.copyOf(slots);
    }

    /* 模型和附魔光效先正常绘制，再整体淡化，避免物品渲染覆盖透明混合状态。 */
    void render(GuiGraphics graphics, int x, int y, int width, int height) {
        if (!this.hasVisibleMarker()) {
            return;
        }
        graphics.flush();
        Minecraft minecraft = Minecraft.getInstance();
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        Matrix4f originalModelView = new Matrix4f(modelView);
        float[] color = RenderSystem.getShaderColor().clone();
        ShaderInstance shader = RenderSystem.getShader();
        int texture = RenderSystem.getShaderTexture(0);
        int framebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int destRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int destAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        modelView.pushMatrix();
        try {
            RenderSystem.disableScissor();
            double scale = minecraft.getWindow().getGuiScale();
            TextureTarget image = this.prepareTarget(
                    (int) Math.ceil(width * scale), (int) Math.ceil(height * scale));
            image.clear(Minecraft.ON_OSX);
            image.bindWrite(true);
            RenderSystem.setProjectionMatrix(
                    new Matrix4f().setOrtho(0, width, height, 0, -1000, 1000),
                    VertexSorting.ORTHOGRAPHIC_Z);
            modelView.identity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            GuiGraphics markers = new GuiGraphics(minecraft, graphics.bufferSource());
            this.drawMarkers(markers);

            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            RenderSystem.setProjectionMatrix(projection, sorting);
            modelView.set(originalModelView);
            RenderSystem.applyModelViewMatrix();
            if (scissor) {
                GlStateManager._enableScissorTest();
            }
            FakeCraftingMarkerRenderer.blit(graphics, image, x, y, width, height, color);
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.blendFuncSeparate(sourceRgb, destRgb, sourceAlpha, destAlpha);
            if (blend) {
                RenderSystem.enableBlend();
            } else {
                RenderSystem.disableBlend();
            }
            if (depth) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            if (scissor) {
                GlStateManager._enableScissorTest();
            } else {
                RenderSystem.disableScissor();
            }
        }
    }

    /* 空槽才提供标记图像，实物及数量继续由原版槽位绘制。 */
    private boolean hasVisibleMarker() {
        for (Slot slot : this.slots) {
            if (slot instanceof FakeCraftingConverterMarkerSlot marker
                    && slot.isActive() && slot.getItem().isEmpty() && marker.getMarkerKey() != null) {
                return true;
            }
        }
        return false;
    }

    private void drawMarkers(GuiGraphics graphics) {
        for (Slot slot : this.slots) {
            if (slot instanceof FakeCraftingConverterMarkerSlot marker && slot.isActive()) {
                ItemStack stack = marker.getMarkerDisplayStack();
                if (!stack.isEmpty()) {
                    graphics.renderFakeItem(stack, slot.x, slot.y);
                }
            }
        }
        graphics.flush();
    }

    /* 绘图区在同一界面内复用，仅在 GUI 缩放或尺寸变化时重建。 */
    TextureTarget prepareTarget(int width, int height) {
        TextureTarget image = this.target;
        if (image == null) {
            image = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            image.setClearColor(0, 0, 0, 0);
            this.target = image;
        } else if (image.width != width || image.height != height) {
            image.resize(width, height, Minecraft.ON_OSX);
        }
        return image;
    }

    static void blit(
            GuiGraphics graphics, TextureTarget image, int x, int y, int width, int height, float[] color
    ) {
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        // 离屏图像已经预乘透明度，颜色和 alpha 必须一起缩放，避免透明物品边缘变黑。
        RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        float opacity = color[3] * 0.4F;
        RenderSystem.setShaderColor(color[0] * opacity, color[1] * opacity, color[2] * opacity, opacity);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, image.getColorTextureId());
        Matrix4f pose = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(pose, x, y, 0).setUv(0, 1);
        buffer.addVertex(pose, x, y + height, 0).setUv(0, 0);
        buffer.addVertex(pose, x + width, y + height, 0).setUv(1, 0);
        buffer.addVertex(pose, x + width, y, 0).setUv(1, 1);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    @Override
    public void close() {
        TextureTarget image = this.target;
        if (image != null) {
            this.target = null;
            image.destroyBuffers();
        }
    }
}
