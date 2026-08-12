package cn.ae2bc.mixin;

import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import cn.ae2bc.client.MaterialOutputConfigScreen;
import cn.ae2bc.extension.PatternEncodingTermMenuExtension;
import cn.ae2bc.logic.DirectionLayout;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(PatternEncodingTermScreen.class)
public abstract class PatternEncodingTermScreenMixin {
    @Unique
    private DirectionLayout ae2bc$directionLayout = DirectionLayout.fromPlayerFacing(Direction.NORTH);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ae2bc$snapshotPlayerFacing(CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        ae2bc$directionLayout = DirectionLayout.fromPlayerFacing(player == null ? Direction.NORTH : player.getDirection());
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void ae2bc$openDirectionSelector(double mouseX, double mouseY, int button,
                                              CallbackInfoReturnable<Boolean> cir) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!Screen.hasControlDown() || !minecraft.options.keyPickItem.matchesMouse(button)) {
            return;
        }
        PatternEncodingTermScreen<?> screen = (PatternEncodingTermScreen<?>) (Object) this;
        PatternEncodingTermMenu menu = screen.getMenu();
        if (menu.getMode() != EncodingMode.PROCESSING) {
            return;
        }
        Slot hovered = ((AbstractContainerScreenInvoker) this).ae2bc$findSlot(mouseX, mouseY);
        int inputSlot = ae2bc$findInputSlot(menu, hovered);
        if (inputSlot < 0 || !hovered.hasItem()) {
            return;
        }
        ae2bc$showSelector(screen, inputSlot);
        cir.setReturnValue(true);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Unique
    private void ae2bc$showSelector(PatternEncodingTermScreen<?> screen, int inputSlot) {
        screen.switchToScreen(new MaterialOutputConfigScreen(screen, inputSlot, ae2bc$directionLayout));
    }

    @ModifyArg(
            method = "renderTooltip",
            at = @At(value = "INVOKE", target = "Lappeng/client/gui/me/items/PatternEncodingTermScreen;drawTooltip(Lnet/minecraft/client/gui/GuiGraphics;IILjava/util/List;)V"),
            index = 3)
    private List<Component> ae2bc$appendInputDirectionHint(List<Component> original) {
        PatternEncodingTermScreen<?> screen = (PatternEncodingTermScreen<?>) (Object) this;
        PatternEncodingTermMenu menu = screen.getMenu();
        int inputSlot = ae2bc$findInputSlot(menu, screen.getSlotUnderMouse());
        if (inputSlot < 0) {
            return original;
        }
        List<Component> tooltip = new ArrayList<>(original);
        tooltip.add(Component.translatable("gui.ae2_batchcraft.material_output_config")
                .withStyle(ChatFormatting.DARK_GRAY));
        return tooltip;
    }

    @Inject(method = "renderSlot", at = @At("TAIL"))
    private void ae2bc$renderDirectionMarker(GuiGraphics graphics, Slot slot, CallbackInfo ci) {
        PatternEncodingTermScreen<?> screen = (PatternEncodingTermScreen<?>) (Object) this;
        int inputSlot = ae2bc$findInputSlot(screen.getMenu(), slot);
        if (inputSlot < 0 || ((PatternEncodingTermMenuExtension) screen.getMenu())
                .ae2bc$getMaterialOutputConfig().getDirection(inputSlot) == null
                && ((PatternEncodingTermMenuExtension) screen.getMenu())
                .ae2bc$getMaterialOutputConfig().getOutputForm(inputSlot)
                == cn.ae2bc.pattern.MaterialOutputForm.NORMAL) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.fill(slot.x + 15, slot.y, slot.x + 16, slot.y + 3, 0xFF2FCCB7);
        graphics.fill(slot.x + 13, slot.y, slot.x + 16, slot.y + 1, 0xFF65E8C9);
        graphics.pose().popPose();
    }

    @Unique
    private static int ae2bc$findInputSlot(PatternEncodingTermMenu menu, Slot slot) {
        if (slot == null || menu.getMode() != EncodingMode.PROCESSING) {
            return -1;
        }
        Slot[] inputs = menu.getProcessingInputSlots();
        for (int i = 0; i < inputs.length; i++) {
            if (inputs[i] == slot) {
                return i;
            }
        }
        return -1;
    }
}
