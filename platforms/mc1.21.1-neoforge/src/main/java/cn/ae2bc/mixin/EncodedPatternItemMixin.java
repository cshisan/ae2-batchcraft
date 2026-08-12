package cn.ae2bc.mixin;

import appeng.crafting.pattern.AEProcessingPattern;
import appeng.crafting.pattern.EncodedPatternItem;
import cn.ae2bc.client.DirectionText;
import cn.ae2bc.pattern.InputDirectionData;
import cn.ae2bc.pattern.MaterialOutputConfigData;
import cn.ae2bc.pattern.MaterialOutputForm;
import cn.ae2bc.registry.ModContent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(EncodedPatternItem.class)
public abstract class EncodedPatternItemMixin {
    @Inject(method = "appendHoverText", at = @At("TAIL"))
    private void ae2bc$appendMaterialOutputConfig(ItemStack stack, Item.TooltipContext context,
                                                   List<Component> lines, TooltipFlag flags,
                                                   CallbackInfo ci) {
        MaterialOutputConfigData config = stack.get(ModContent.MATERIAL_OUTPUT_CONFIG.get());
        if (config == null || config.isEmpty() || Minecraft.getInstance().level == null) {
            return;
        }

        var item = (EncodedPatternItem<?>) (Object) this;
        if (!(item.decode(stack, Minecraft.getInstance().level) instanceof AEProcessingPattern pattern)) {
            return;
        }

        boolean addedHeader = false;
        var inputs = pattern.getSparseInputs();
        for (int slot = 0; slot < inputs.size() && InputDirectionData.isValidSlot(slot); slot++) {
            var input = inputs.get(slot);
            var direction = config.getDirection(slot);
            var outputForm = config.getOutputForm(slot);
            if (input == null || direction == null && outputForm == MaterialOutputForm.NORMAL) {
                continue;
            }
            if (!addedHeader) {
                lines.add(Component.translatable("tooltip.ae2_batchcraft.material_output_config")
                        .withStyle(ChatFormatting.GRAY));
                addedHeader = true;
            }
            Component directionName = DirectionText.absoluteName(direction).copy()
                    .withStyle(ChatFormatting.AQUA);
            Component outputFormName = Component.translatable(
                    "gui.ae2_batchcraft.material_output_form." + outputForm.getSerializedName())
                    .withStyle(ChatFormatting.YELLOW);
            lines.add(Component.translatable("tooltip.ae2_batchcraft.material_output_config.entry",
                    input.what().getDisplayName(), directionName, outputFormName));
        }
    }
}
