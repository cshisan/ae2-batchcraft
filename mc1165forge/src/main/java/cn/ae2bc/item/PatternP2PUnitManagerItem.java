package cn.ae2bc.item;

import java.util.List;
import java.util.function.Function;

import javax.annotation.Nullable;

import appeng.api.parts.IPart;
import appeng.api.util.AEColor;
import appeng.items.parts.ColoredPartItem;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

/** Colored AE2 part item with the native tooltip behavior used by this version. */
public final class PatternP2PUnitManagerItem<T extends IPart> extends ColoredPartItem<T> {
    private final String[] tooltipKeys;

    public PatternP2PUnitManagerItem(Item.Properties properties, Function<ItemStack, T> factory,
            AEColor color, String... tooltipKeys) {
        super(properties, factory, color);
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
