package cn.ae2bc.mixin;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.items.PatternTermScreen;
import appeng.container.me.items.PatternTermContainer;
import cn.ae2bc.client.PatternEncodingTermScreenSupport;
import cn.ae2bc.extension.PatternEncodingTermMenuExtension;
import cn.ae2bc.pattern.MaterialOutputConfigData;
import cn.ae2bc.pattern.MaterialOutputForm;
import com.mojang.blaze3d.matrix.MatrixStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.inventory.container.Slot;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds processing-input material output hints and markers to AE2's old base screen. */
@Mixin(AEBaseScreen.class)
public abstract class PatternEncodingTermScreenMixin extends Screen {
    protected PatternEncodingTermScreenMixin() {
        super(StringTextComponent.EMPTY);
    }

    @Shadow(remap = false)
    protected abstract Slot getSlot(int mouseX, int mouseY);

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void ae2bc$openMaterialOutputConfig(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> callback) {
        if (!((Object) this instanceof PatternTermScreen)) return;
        PatternTermScreen screen = (PatternTermScreen) (Object) this;
        Slot hovered = getSlot((int) mouseX, (int) mouseY);
        if (PatternEncodingTermScreenSupport.handleMouseClicked(screen, hovered, button)) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "renderTooltips", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2bc$renderInputTooltip(MatrixStack matrices, int mouseX, int mouseY,
            CallbackInfo callback) {
        if (!((Object) this instanceof PatternTermScreen)) return;
        PatternTermScreen screen = (PatternTermScreen) (Object) this;
        PatternTermContainer container = (PatternTermContainer) screen.getMenu();
        Slot hovered = getSlot(mouseX, mouseY);
        if (PatternEncodingTermScreenSupport.getProcessingInputSlotIndex(container, hovered) < 0
                || hovered == null || !hovered.hasItem()) return;

        Minecraft minecraft = Minecraft.getInstance();
        ITooltipFlag flag = minecraft.options.advancedItemTooltips
                ? ITooltipFlag.TooltipFlags.ADVANCED : ITooltipFlag.TooltipFlags.NORMAL;
        List<ITextComponent> tooltip = new ArrayList<ITextComponent>(
                hovered.getItem().getTooltipLines(minecraft.player, flag));
        tooltip.add(new TranslationTextComponent("gui.ae2_batchcraft.material_output_config")
                .withStyle(TextFormatting.DARK_GRAY));
        screen.drawTooltip(matrices, mouseX, mouseY, tooltip);
        callback.cancel();
    }

    @Inject(method = "renderSlot", at = @At("TAIL"))
    private void ae2bc$renderConfigMarker(MatrixStack matrices, Slot slot, CallbackInfo callback) {
        if (!((Object) this instanceof PatternTermScreen)) return;
        PatternTermScreen screen = (PatternTermScreen) (Object) this;
        PatternTermContainer container = (PatternTermContainer) screen.getMenu();
        int inputSlot = PatternEncodingTermScreenSupport.getProcessingInputSlotIndex(container, slot);
        if (inputSlot < 0) return;
        MaterialOutputConfigData config = ((PatternEncodingTermMenuExtension) container)
                .ae2bc$getMaterialOutputConfig();
        if (config.getDirection(inputSlot) == null
                && config.getOutputForm(inputSlot) == MaterialOutputForm.NORMAL) return;

        matrices.pushPose();
        matrices.translate(0.0D, 0.0D, 200.0D);
        fill(matrices, slot.x + 15, slot.y, slot.x + 16, slot.y + 3, 0xFF2FCCB7);
        fill(matrices, slot.x + 13, slot.y, slot.x + 16, slot.y + 1, 0xFF65E8C9);
        matrices.popPose();
    }

}
