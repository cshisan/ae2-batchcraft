package cn.ae2bc.client;

import cn.ae2bc.placer.ComponentPlacerItem;
import cn.ae2bc.placer.ComponentPlacerSelection;
import cn.ae2bc.placer.ComponentPlacerSettings;
import cn.ae2bc.registry.ModContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class ComponentPlacerSelectionRenderer {
    private ComponentPlacerSelectionRenderer() {
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        ItemStack placer = findHeldPlacer(minecraft.player.getMainHandItem(), minecraft.player.getOffhandItem());
        if (placer.isEmpty()) {
            return;
        }
        ComponentPlacerSelection selection = placer.get(ModContent.PLACER_SELECTION.get());
        if (selection == null || !selection.dimension().equals(minecraft.level.dimension().location())) {
            return;
        }
        ComponentPlacerSettings settings = placer.getOrDefault(
                ModContent.PLACER_SETTINGS.get(), ComponentPlacerSettings.DEFAULT);

        var first = selection.first();
        var second = selection.second() == null ? first : selection.second();
        int minX = Math.min(first.getX(), second.getX()) + settings.offsetX();
        int minY = Math.min(first.getY(), second.getY()) + settings.offsetY();
        int minZ = Math.min(first.getZ(), second.getZ()) + settings.offsetZ();
        int maxX = Math.max(first.getX(), second.getX()) + settings.offsetX() + 1;
        int maxY = Math.max(first.getY(), second.getY()) + settings.offsetY() + 1;
        int maxZ = Math.max(first.getZ(), second.getZ()) + settings.offsetZ() + 1;

        var camera = event.getCamera().getPosition();
        var poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        var buffers = minecraft.renderBuffers().bufferSource();
        var renderType = RenderType.lines();
        var consumer = buffers.getBuffer(renderType);
        LevelRenderer.renderLineBox(poseStack, consumer,
                new AABB(minX, minY, minZ, maxX, maxY, maxZ),
                0.15F, 0.9F, 0.95F, 1.0F);
        buffers.endBatch(renderType);
        poseStack.popPose();
    }

    private static ItemStack findHeldPlacer(ItemStack mainHand, ItemStack offHand) {
        if (mainHand.getItem() instanceof ComponentPlacerItem) {
            return mainHand;
        }
        if (offHand.getItem() instanceof ComponentPlacerItem) {
            return offHand;
        }
        return ItemStack.EMPTY;
    }
}
