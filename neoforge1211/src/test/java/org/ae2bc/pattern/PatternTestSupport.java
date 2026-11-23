package org.ae2bc.pattern;

import appeng.api.stacks.AEItemKey;
import appeng.crafting.pattern.AEProcessingPattern;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Assertions;

public final class PatternTestSupport {
    private PatternTestSupport() {
    }

    /* 先验证测试样板实际生成了物品，再交给 AE2 解析。 */
    public static AEProcessingPattern processing(ItemStack encoded) {
        AEItemKey definition = AEItemKey.of(encoded);
        Assertions.assertNotNull(definition, "Processing pattern encoding returned an empty stack");
        return new AEProcessingPattern(definition);
    }
}
