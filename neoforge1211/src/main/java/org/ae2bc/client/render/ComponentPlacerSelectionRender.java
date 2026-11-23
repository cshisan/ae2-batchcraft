package org.ae2bc.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.ae2bc.placer.ComponentPlacerData;
import org.ae2bc.placer.ComponentPlacerItem;
import org.ae2bc.placer.ComponentPlacerPose;
import org.ae2bc.placer.ComponentPlacerSelection;

public final class ComponentPlacerSelectionRender {
    private ComponentPlacerSelectionRender() {
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        ItemStack stack = minecraft.player.getMainHandItem();
        if (!(stack.getItem() instanceof ComponentPlacerItem)) {
            stack = minecraft.player.getOffhandItem();
            if (!(stack.getItem() instanceof ComponentPlacerItem)) {
                return;
            }
        }
        if (ComponentPlacerData.frequencyMode(stack)) {
            return;
        }
        ComponentPlacerSelection selection = ComponentPlacerData.selection(stack);
        if (selection == null || !selection.dimension().equals(minecraft.level.dimension().location())) {
            return;
        }
        ComponentPlacerPose settings = ComponentPlacerData.pose(stack);
        BlockPos second = selection.second() == null ? selection.first() : selection.second();
        double minX = Math.min(selection.first().getX(), second.getX()) + settings.pos().x();
        double minY = Math.min(selection.first().getY(), second.getY()) + settings.pos().y();
        double minZ = Math.min(selection.first().getZ(), second.getZ()) + settings.pos().z();
        double maxX = Math.max(selection.first().getX(), second.getX()) + settings.pos().x() + 1;
        double maxY = Math.max(selection.first().getY(), second.getY()) + settings.pos().y() + 1;
        double maxZ = Math.max(selection.first().getZ(), second.getZ()) + settings.pos().z() + 1;
        Vec3 camera = event.getCamera().getPosition();
        event.getPoseStack().pushPose();
        event.getPoseStack().translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        RenderType lines = RenderType.lines();
        LevelRenderer.renderLineBox(
                event.getPoseStack(), buffers.getBuffer(lines),
                new AABB(minX, minY, minZ, maxX, maxY, maxZ),
                0.15F, 0.9F, 0.95F, 1.0F
        );
        buffers.endBatch(lines);
        event.getPoseStack().popPose();
    }
}
