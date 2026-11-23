package org.ae2bc.part.unit.port.item;

import appeng.items.parts.PartItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.ae2bc.part.unit.port.part.Pp2mpUnitBreakPortPart;
import org.jspecify.annotations.NonNull;

public final class Pp2mpUnitBreakPortItem extends PartItem<Pp2mpUnitBreakPortPart> {
    public Pp2mpUnitBreakPortItem(Item.Properties properties) {
        super(properties, Pp2mpUnitBreakPortPart.class, Pp2mpUnitBreakPortPart::new);
    }

    @Override
    public boolean isEnchantable(@NonNull ItemStack stack) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getEnchantmentValue() {
        return 10;
    }

    @Override
    public boolean isBookEnchantable(@NonNull ItemStack stack, @NonNull ItemStack book) {
        return true;
    }
}
