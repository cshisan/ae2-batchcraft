package org.ae2bc.storage;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluids;
import org.ae2bc.core.unit.ReturnMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

class ProductReturnMatcherTest {
    @Test
    void strictLookupUsesEqualKeysAndLeavesDeclarationUntouched() {
        AEKey declared = AEItemKey.of(new ItemStack(Items.STONE));
        AEKey returned = AEItemKey.of(new ItemStack(Items.STONE));
        Map<AEKey, Long> products = new LinkedHashMap<>();
        products.put(declared, 2L);
        Assertions.assertEquals(declared, ProductReturnMatcher.find(returned, products, ReturnMode.STRICT_FULL));
        Assertions.assertNull(ProductReturnMatcher.find(AEItemKey.of(Items.DIRT), products, ReturnMode.STRICT_FULL));
        Assertions.assertEquals(Map.of(declared, 2L), products);
        Assertions.assertNull(ProductReturnMatcher.find(null, products, ReturnMode.STRICT_FULL));
        Assertions.assertNull(ProductReturnMatcher.find(returned, (Map<AEKey, ?>) null, ReturnMode.STRICT_FULL));
        Assertions.assertNull(ProductReturnMatcher.find(returned, products, null));
        AEKey water = AEFluidKey.of(Fluids.WATER);
        Assertions.assertEquals(water, ProductReturnMatcher.find(
                AEFluidKey.of(Fluids.WATER), Map.of(water, 1000L), ReturnMode.STRICT_FULL));
        Assertions.assertNull(ProductReturnMatcher.find(
                AEFluidKey.of(Fluids.LAVA), Map.of(water, 1000L), ReturnMode.STRICT_FULL));
    }

    @Test
    void fuzzyLookupStillPrefersPrimaryAndStrictLookupKeepsComponents() {
        AEKey ordinary = AEItemKey.of(Items.STONE);
        ItemStack named = new ItemStack(Items.STONE);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("primary"));
        AEKey primary = AEItemKey.of(named);
        Map<AEKey, Long> products = new LinkedHashMap<>();
        products.put(ordinary, 1L);
        products.put(primary, 2L);
        Assertions.assertSame(primary, ProductReturnMatcher.find(
                ordinary, products, ReturnMode.STRICT_CHECK_RID, primary));
        Assertions.assertEquals(ordinary, ProductReturnMatcher.find(
                ordinary, products, ReturnMode.STRICT_FULL, primary));
        Assertions.assertNull(ProductReturnMatcher.find(
                ordinary, Map.of(primary, 2L), ReturnMode.STRICT_FULL));
    }

    @Test
    void ignoringNbtKeepsItemAndOtherComponentsWithoutMutatingKeys() {
        ItemStack declaredStack = new ItemStack(Items.STONE);
        CompoundTag declaredNbt = new CompoundTag();
        declaredNbt.putInt("batch", 1);
        declaredStack.set(DataComponents.CUSTOM_DATA, CustomData.of(declaredNbt));
        declaredStack.set(DataComponents.CUSTOM_NAME, Component.literal("product"));
        declaredStack.set(DataComponents.REPAIR_COST, 3);
        ItemStack returnedStack = declaredStack.copy();
        CompoundTag returnedNbt = new CompoundTag();
        returnedNbt.putInt("batch", 2);
        returnedStack.set(DataComponents.CUSTOM_DATA, CustomData.of(returnedNbt));
        AEItemKey declared = Objects.requireNonNull(AEItemKey.of(declaredStack));
        AEItemKey returned = Objects.requireNonNull(AEItemKey.of(returnedStack));

        Assertions.assertTrue(ProductReturnMatcher.matches(returned, declared, ReturnMode.STRICT_IGNORE_NBT));
        Assertions.assertFalse(ProductReturnMatcher.matches(returned, declared, ReturnMode.STRICT_FULL));
        Assertions.assertEquals(CustomData.of(declaredNbt), declared.get(DataComponents.CUSTOM_DATA));
        Assertions.assertEquals(CustomData.of(returnedNbt), returned.get(DataComponents.CUSTOM_DATA));
        Assertions.assertFalse(ProductReturnMatcher.matches(
                AEItemKey.of(Items.DIRT), declared, ReturnMode.STRICT_IGNORE_NBT));

        returnedStack.remove(DataComponents.CUSTOM_DATA);
        Assertions.assertTrue(ProductReturnMatcher.matches(
                AEItemKey.of(returnedStack), declared, ReturnMode.STRICT_IGNORE_NBT));
        returnedStack.set(DataComponents.CUSTOM_NAME, Component.literal("other"));
        Assertions.assertFalse(ProductReturnMatcher.matches(
                AEItemKey.of(returnedStack), declared, ReturnMode.STRICT_IGNORE_NBT));
        returnedStack.set(DataComponents.CUSTOM_NAME, Component.literal("product"));
        returnedStack.set(DataComponents.REPAIR_COST, 4);
        Assertions.assertFalse(ProductReturnMatcher.matches(
                AEItemKey.of(returnedStack), declared, ReturnMode.STRICT_IGNORE_NBT));
    }

    @Test
    void ignoringNbtPreservesFluidAndKeyTypeBoundaries() {
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        Assertions.assertTrue(ProductReturnMatcher.matches(
                AEFluidKey.of(Fluids.WATER), water, ReturnMode.STRICT_IGNORE_NBT));
        Assertions.assertFalse(ProductReturnMatcher.matches(
                AEFluidKey.of(Fluids.LAVA), water, ReturnMode.STRICT_IGNORE_NBT));
        Assertions.assertFalse(ProductReturnMatcher.matches(
                AEItemKey.of(Items.WATER_BUCKET), water, ReturnMode.STRICT_IGNORE_NBT));
    }
}
