package cn.ae2bc.client;

import cn.ae2bc.pattern.MaterialOutputConfigData;
import cn.ae2bc.pattern.MaterialOutputForm;
import cn.ae2bc.pattern.PatternEncodingState;
import java.util.List;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

/** Appends material output metadata to rv6 encoded-pattern tooltips. */
public final class EncodedPatternTooltipSupport {
    private EncodedPatternTooltipSupport() { }

    public static void append(ItemStack stack, World level, List<String> lines, ITooltipFlag flags) {
        MaterialOutputConfigData config = MaterialOutputConfigData.fromPacked(PatternEncodingState.read(stack));
        if (config.isEmpty() || stack == null || stack.getTagCompound() == null) return;
        NBTTagList inputs = stack.getTagCompound().getTagList("in", 10);
        boolean addedHeader = false;
        for (int slot = 0; slot < inputs.tagCount() && slot < 9; slot++) {
            NBTTagCompound inputTag = inputs.getCompoundTagAt(slot);
            ItemStack input = new ItemStack(inputTag);
            EnumFacing direction = config.getDirection(slot);
            MaterialOutputForm form = config.getOutputForm(slot);
            if (input.isEmpty() || direction == null && form == MaterialOutputForm.NORMAL) continue;
            if (!addedHeader) {
                lines.add(TextFormatting.GRAY + I18n.format("tooltip.ae2_batchcraft.material_output_config"));
                addedHeader = true;
            }
            String directionName = TextFormatting.AQUA + I18n.format(
                    "gui.ae2_batchcraft.direction." + (direction == null ? "auto" : direction.getName()));
            String formName = TextFormatting.YELLOW + I18n.format(
                    "gui.ae2_batchcraft.material_output_form." + form.getSerializedName());
            lines.add(I18n.format("tooltip.ae2_batchcraft.material_output_config.entry",
                    input.getDisplayName(), directionName, formName));
        }
    }
}
