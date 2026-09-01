package cn.ae2bc.item;

import java.util.List;
import java.util.function.Function;

import javax.annotation.Nullable;

import appeng.api.parts.IPart;
import appeng.items.parts.PartItem;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

/** AE2 8 part item with a dependency-free native inventory tooltip. */
public final class PatternP2PPartItem<T extends IPart> extends PartItem<T> {
    private final String[] tooltipKeys;

    public PatternP2PPartItem(Item.Properties properties, Function<ItemStack, T> factory,
            String... tooltipKeys) {
        super(properties, factory);
        this.tooltipKeys = tooltipKeys;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable World level,
            List<ITextComponent> tooltip, ITooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        for (String tooltipKey : tooltipKeys) {
            tooltip.add(new TranslationTextComponent(tooltipKey).withStyle(TextFormatting.GRAY));
        }
    }
}
