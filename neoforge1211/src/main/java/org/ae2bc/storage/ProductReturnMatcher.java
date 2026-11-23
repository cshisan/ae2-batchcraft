package org.ae2bc.storage;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.ae2bc.core.unit.ReturnMode;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class ProductReturnMatcher {
    @Nullable
    public static AEKey find(AEKey returned, Iterable<AEKey> declared, ReturnMode mode) {
        if (returned == null || declared == null || mode == null) {
            return null;
        }

        for (AEKey declaredKey : declared) {
            if (ProductReturnMatcher.matches(returned, declaredKey, mode)) {
                return declaredKey;
            }
        }
        return null;
    }

    @Nullable
    public static AEKey find(AEKey returned, Map<AEKey, ?> declared, ReturnMode mode) {
        if (returned == null || declared == null || mode == null) {
            return null;
        }
        // 内置键的 equals 已保证同类；扩展键仍保留 matches 的显式类型边界。
        if (mode == ReturnMode.STRICT_FULL && (returned instanceof AEItemKey || returned instanceof AEFluidKey)) {
            return declared.containsKey(returned) ? returned : null;
        }
        return ProductReturnMatcher.find(returned, declared.keySet(), mode);
    }

    /* 模糊匹配优先结算真正主产物，避免同注册 ID 的副产物抢占完成计数。 */
    @Nullable
    public static AEKey find(
            AEKey returned, Map<AEKey, ?> declared, ReturnMode mode, @Nullable AEKey primary
    ) {
        if (primary != null && declared.containsKey(primary) && ProductReturnMatcher.matches(returned, primary, mode)) {
            return primary;
        }
        return ProductReturnMatcher.find(returned, declared, mode);
    }

    public static boolean matches(AEKey returned, AEKey declared, ReturnMode mode) {
        if (returned == null || declared == null || mode == null || returned.getClass() != declared.getClass()) {
            return false;
        }

        return switch (mode) {
            case STRICT_FULL -> returned.equals(declared);
            case STRICT_IGNORE_NBT -> ProductReturnMatcher.ignoringNbt(returned, declared);
            case STRICT_CHECK_RID -> ProductReturnMatcher.matchesRegistryId(returned, declared);
            default -> true;
        };
    }

    private static boolean ignoringNbt(AEKey returned, AEKey declared) {
        if (returned instanceof AEItemKey returnedItem && declared instanceof AEItemKey declaredItem) {
            if (returnedItem.getItem() != declaredItem.getItem()) {
                return false;
            }
            ItemStack returnedStack = returnedItem.toStack();
            ItemStack declaredStack = declaredItem.toStack();
            returnedStack.remove(DataComponents.CUSTOM_DATA);
            declaredStack.remove(DataComponents.CUSTOM_DATA);
            return ItemStack.isSameItemSameComponents(returnedStack, declaredStack);
        }
        return returned.fuzzyEquals(declared, FuzzyMode.IGNORE_ALL);
    }

    private static boolean matchesRegistryId(AEKey returned, AEKey declared) {
        return returned.getPrimaryKey() == declared.getPrimaryKey();
    }
}
