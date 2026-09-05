package cn.ae2bc.item;

import appeng.items.parts.PartItem;
import cn.ae2bc.core.unit.UnitPortType;
import cn.ae2bc.part.PatternP2PUnitPortPart;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import java.util.List;
import javax.annotation.Nullable;

/** Unit-port item with annihilation-plane enchanting support for the break form. */
public final class PatternP2PUnitPortItem extends PartItem<PatternP2PUnitPortPart> {
    private final UnitPortType type;

    public PatternP2PUnitPortItem(Item.Properties properties, UnitPortType type) {
        super(properties, stack -> new PatternP2PUnitPortPart(stack, type));
        this.type = type;
    }

    @Override public boolean isEnchantable(ItemStack stack) { return type == UnitPortType.BREAK; }
    @Override public int getEnchantmentValue() { return type == UnitPortType.BREAK ? 10 : 0; }
    @Override public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return type == UnitPortType.BREAK && enchantment.category == EnchantmentType.DIGGER;
    }
    @Override public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return type == UnitPortType.BREAK;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable World level,
            List<ITextComponent> tooltip, ITooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(new TranslationTextComponent(
                "tooltip.ae2_batchcraft.pattern_p2p.unit_port." + type.getId())
                .withStyle(TextFormatting.GRAY));
        tooltip.add(new TranslationTextComponent("tooltip.ae2_batchcraft.pattern_p2p.binding")
                .withStyle(TextFormatting.GRAY));
    }
}
