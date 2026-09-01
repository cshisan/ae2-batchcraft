package cn.ae2bc.client;

import cn.ae2bc.Ae2bcMod;
import cn.ae2bc.placer.ComponentPlacerItem;
import cn.ae2bc.placer.ComponentPlacerSelection;
import cn.ae2bc.placer.ComponentPlacerSettings;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Ae2bcMod.MOD_ID, value = Dist.CLIENT)
public final class ComponentPlacerSelectionRenderer {
    private ComponentPlacerSelectionRenderer() { }

    @SubscribeEvent
    public static void render(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        ItemStack stack = minecraft.player.getMainHandItem();
        if (!(stack.getItem() instanceof ComponentPlacerItem)) stack = minecraft.player.getOffhandItem();
        if (!(stack.getItem() instanceof ComponentPlacerItem)) return;
        ComponentPlacerSelection selection = ComponentPlacerItem.getSelection(stack);
        if (selection == null || !selection.getDimension().equals(minecraft.level.dimension().location())) return;
        ComponentPlacerSettings settings = ComponentPlacerItem.getSettings(stack);
        net.minecraft.util.math.BlockPos first = selection.getFirst();
        net.minecraft.util.math.BlockPos second = selection.getSecond() == null ? first : selection.getSecond();
        AxisAlignedBB box = new AxisAlignedBB(
                Math.min(first.getX(), second.getX()) + settings.getOffsetX(),
                Math.min(first.getY(), second.getY()) + settings.getOffsetY(),
                Math.min(first.getZ(), second.getZ()) + settings.getOffsetZ(),
                Math.max(first.getX(), second.getX()) + settings.getOffsetX() + 1,
                Math.max(first.getY(), second.getY()) + settings.getOffsetY() + 1,
                Math.max(first.getZ(), second.getZ()) + settings.getOffsetZ() + 1);
        Vector3d camera = minecraft.gameRenderer.getMainCamera().getPosition();
        MatrixStack matrices = event.getMatrixStack();
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        IRenderTypeBuffer.Impl buffers = minecraft.renderBuffers().bufferSource();
        WorldRenderer.renderLineBox(matrices, buffers.getBuffer(RenderType.lines()), box,
                0.15F, 0.9F, 0.95F, 1.0F);
        buffers.endBatch(RenderType.lines());
        matrices.popPose();
    }
}
