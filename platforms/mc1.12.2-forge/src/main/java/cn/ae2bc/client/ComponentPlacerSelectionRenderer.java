package cn.ae2bc.client;

import cn.ae2bc.Ae2bcMod;
import cn.ae2bc.placer.ComponentPlacerItem;
import cn.ae2bc.placer.ComponentPlacerSelection;
import cn.ae2bc.placer.ComponentPlacerSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = Ae2bcMod.MOD_ID, value = Side.CLIENT)
public final class ComponentPlacerSelectionRenderer {
    private ComponentPlacerSelectionRenderer() { }
    @SubscribeEvent public static void render(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft(); if (minecraft.player == null || minecraft.world == null) return;
        ItemStack stack = minecraft.player.getHeldItemMainhand();
        if (!(stack.getItem() instanceof ComponentPlacerItem)) stack = minecraft.player.getHeldItemOffhand();
        if (!(stack.getItem() instanceof ComponentPlacerItem)) return;
        ComponentPlacerSelection selection = ComponentPlacerItem.getSelection(stack);
        if (selection == null || selection.getDimension() != minecraft.world.provider.getDimension()) return;
        ComponentPlacerSettings settings = ComponentPlacerItem.getSettings(stack);
        BlockPos first = selection.getFirst(), second = selection.getSecond() == null ? first : selection.getSecond();
        AxisAlignedBB box = new AxisAlignedBB(
                Math.min(first.getX(), second.getX()) + settings.getOffsetX(),
                Math.min(first.getY(), second.getY()) + settings.getOffsetY(),
                Math.min(first.getZ(), second.getZ()) + settings.getOffsetZ(),
                Math.max(first.getX(), second.getX()) + settings.getOffsetX() + 1,
                Math.max(first.getY(), second.getY()) + settings.getOffsetY() + 1,
                Math.max(first.getZ(), second.getZ()) + settings.getOffsetZ() + 1)
                .offset(-minecraft.getRenderManager().viewerPosX,
                        -minecraft.getRenderManager().viewerPosY, -minecraft.getRenderManager().viewerPosZ);
        GlStateManager.enableBlend(); GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D(); GlStateManager.depthMask(false); GlStateManager.glLineWidth(2.0F);
        RenderGlobal.drawSelectionBoundingBox(box, 0.15F, 0.9F, 0.95F, 1.0F);
        GlStateManager.depthMask(true); GlStateManager.enableTexture2D(); GlStateManager.disableBlend();
    }
}
