package cn.ae2bc.mixin;

import appeng.items.misc.EncodedPatternItem;
import cn.ae2bc.pattern.MaterialOutputConfigCodec;
import cn.ae2bc.pattern.MaterialOutputConfigData;
import cn.ae2bc.pattern.MaterialOutputForm;
import cn.ae2bc.pattern.PatternEncodingState;
import java.util.List;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.Direction;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Final AE2 8 encoding boundary; custom output data cannot be lost after the vanilla write. */
@Mixin(EncodedPatternItem.class)
public final class EncodedPatternItemMixin {
    @Inject(method = "encodeProcessingPattern", at = @At("TAIL"), remap = false)
    private static void ae2bc$writeOutputConfig(ItemStack stack, ItemStack[] inputs, ItemStack[] outputs,
            CallbackInfo callback) {
        long[] packed = PatternEncodingState.consume();
        if (packed == null || packed.length == 0) {
            return;
        }
        if (stack.getTag() == null) {
            stack.setTag(new net.minecraft.nbt.CompoundNBT());
        }
        stack.getTag().putLongArray("MaterialOutputConfig", MaterialOutputConfigCodec.normalize(packed));
    }

    @Inject(method = "appendHoverText", at = @At("TAIL"))
    private void ae2bc$appendMaterialOutputConfig(ItemStack stack, World level,
            List<ITextComponent> lines, ITooltipFlag flags, CallbackInfo callback) {
        MaterialOutputConfigData config = MaterialOutputConfigData.fromPacked(PatternEncodingState.read(stack));
        if (config.isEmpty() || !stack.hasTag()) return;
        ListNBT inputs = stack.getTag().getList("in", 10);
        boolean addedHeader = false;
        for (int slot = 0; slot < inputs.size() && slot < 9; slot++) {
            CompoundNBT inputTag = inputs.getCompound(slot);
            ItemStack input = ItemStack.of(inputTag);
            Direction direction = config.getDirection(slot);
            MaterialOutputForm form = config.getOutputForm(slot);
            if (input.isEmpty() || direction == null && form == MaterialOutputForm.NORMAL) continue;
            if (!addedHeader) {
                lines.add(new TranslationTextComponent("tooltip.ae2_batchcraft.material_output_config")
                        .withStyle(TextFormatting.GRAY));
                addedHeader = true;
            }
            ITextComponent directionName = new TranslationTextComponent(
                    "gui.ae2_batchcraft.direction." + (direction == null ? "auto" : direction.getName()))
                    .withStyle(TextFormatting.AQUA);
            ITextComponent formName = new TranslationTextComponent(
                    "gui.ae2_batchcraft.material_output_form." + form.getSerializedName())
                    .withStyle(TextFormatting.YELLOW);
            lines.add(new TranslationTextComponent("tooltip.ae2_batchcraft.material_output_config.entry",
                    input.getHoverName(), directionName, formName));
        }
    }
}
