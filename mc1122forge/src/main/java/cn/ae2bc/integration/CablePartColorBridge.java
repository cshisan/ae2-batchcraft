package cn.ae2bc.integration;

import appeng.api.util.AEColor;
import appeng.items.parts.ItemPart;
import cn.ae2bc.item.PatternP2PUnitManagerItem;
import net.minecraft.item.ItemStack;

/** Resolves colors for AE2 cables and the custom unit-manager cable. */
public final class CablePartColorBridge {
    private CablePartColorBridge() {
    }

    public static AEColor resolve(ItemStack stack) {
        if (stack.getItem() instanceof PatternP2PUnitManagerItem) {
            return ((PatternP2PUnitManagerItem) stack.getItem()).getColor(stack);
        }
        ItemPart item = (ItemPart) stack.getItem();
        return AEColor.values()[item.variantOf(stack.getItemDamage())];
    }
}
