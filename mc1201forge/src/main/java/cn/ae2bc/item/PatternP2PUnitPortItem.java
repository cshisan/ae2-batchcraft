package cn.ae2bc.item;

import appeng.items.parts.PartItem;
import cn.ae2bc.core.unit.UnitPortType;
import cn.ae2bc.part.PatternP2PUnitPortPart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/** Part item whose break form supports the same enchantments as an annihilation plane. */
public final class PatternP2PUnitPortItem extends PartItem<PatternP2PUnitPortPart> {
    private final UnitPortType type;

    public PatternP2PUnitPortItem(Item.Properties properties, UnitPortType type) {
        super(properties, PatternP2PUnitPortPart.class,
                item -> new PatternP2PUnitPortPart(item, type));
        this.type = type;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return type == UnitPortType.BREAK;
    }

    @Override
    public int getEnchantmentValue() {
        return type == UnitPortType.BREAK ? 10 : 0;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return type == UnitPortType.BREAK && enchantment.category == EnchantmentCategory.DIGGER;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return type == UnitPortType.BREAK;
    }
}
