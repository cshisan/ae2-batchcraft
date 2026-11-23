package org.ae2bc.pattern;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.ae2bc.core.pattern.MaterialOutputMode;
import org.ae2bc.core.unit.ReturnMode;
import org.ae2bc.registry.component.ComponentRegistry;
import org.ae2bc.storage.ProductReturnMatcher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.ae2bc.core.dispatch.TaskProgress;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

class Pp2mpTaskOutputsTest {
    private static Pp2mpPatternDetails pattern(
            boolean fake, long batch, List<GenericStack> outputs, @Nullable GenericStack marker
    ) {
        ItemStack vanilla = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.STONE), 1)), outputs);
        MaterialOutputAttr config = fake
                ? MaterialOutputAttr.EMPTY.withMode(0, MaterialOutputMode.FAKE_CRAFTING_CONVERTER)
                : MaterialOutputAttr.EMPTY;
        ItemStack encoded = Pp2mpPatternItem.encodePattern(vanilla, config);
        encoded.set(ComponentRegistry.PATTERN_BATCH_COUNT, batch);
        if (marker != null) {
            encoded.set(ComponentRegistry.REAL_OUTPUT_MARKER, marker);
        }
        return new Pp2mpPatternDetails(AEItemKey.of(encoded), null);
    }

    @Test
    void markerControlsCompletionWithoutBecomingAnAe2Output() {
        AEKey ticket = AEItemKey.of(Items.PAPER);
        AEKey primary = AEItemKey.of(Items.DIAMOND);
        AEKey byproduct = AEItemKey.of(Items.GOLD_INGOT);
        Pp2mpPatternDetails pattern = Pp2mpTaskOutputsTest.pattern(true, 1,
                List.of(new GenericStack(ticket, 1), new GenericStack(byproduct, 8)),
                new GenericStack(primary, 6));
        Pp2mpTaskBatch.OutputPlan plan = Pp2mpTaskBatch.planOutputs(pattern, Set.of(ticket));
        Assertions.assertNotNull(plan);
        Assertions.assertEquals(ticket, pattern.getPrimaryOutput().what());
        Assertions.assertEquals(List.of(new GenericStack(ticket, 1), new GenericStack(byproduct, 8)),
                pattern.getOutputs());
        Assertions.assertEquals(primary, plan.primary());
        Assertions.assertEquals(Map.of(primary, 6L, byproduct, 8L), plan.pending());
        TaskProgress<AEKey> progress = new TaskProgress<>();
        progress.begin(plan.pending());
        progress.record(byproduct, 8);
        Assertions.assertTrue(progress.pending(plan.primary()));
        progress.begin(plan.pending());
        progress.record(primary, 6);
        Assertions.assertFalse(progress.pending(plan.primary()));
    }

    @Test
    void missingMarkerDoesNotFallBackToAnyOutputSlot() {
        AEKey ticket = AEItemKey.of(Items.PAPER);
        Pp2mpPatternDetails pattern = Pp2mpTaskOutputsTest.pattern(true, 1,
                List.of(new GenericStack(ticket, 1), new GenericStack(AEItemKey.of(Items.DIAMOND), 6),
                        new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 8)), null);
        Assertions.assertEquals(3, pattern.getOutputs().size());
        Assertions.assertFalse(Pp2mpTaskBatch.hasValidPrimaryOutput(pattern));
        Assertions.assertNull(Pp2mpTaskBatch.planOutputs(pattern, Set.of(ticket)));
    }

    @Test
    void emptySecondSlotIsValidWithAnIndependentMarker() {
        AEKey ticket = AEItemKey.of(Items.PAPER);
        AEKey primary = AEItemKey.of(Items.DIAMOND);
        Pp2mpPatternDetails pattern = Pp2mpTaskOutputsTest.pattern(true, 1,
                Arrays.asList(new GenericStack(ticket, 1), null,
                        new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 8)),
                new GenericStack(primary, 6));
        Assertions.assertTrue(Pp2mpTaskBatch.hasValidPrimaryOutput(pattern));
        Pp2mpTaskBatch.OutputPlan plan = Pp2mpTaskBatch.planOutputs(pattern, Set.of(ticket));
        Assertions.assertNotNull(plan);
        Assertions.assertEquals(primary, plan.primary());
    }

    @Test
    void fakeTicketCannotAlsoBeTheRealPrimary() {
        AEKey ticket = AEItemKey.of(Items.PAPER);
        Pp2mpPatternDetails pattern = Pp2mpTaskOutputsTest.pattern(true, 1,
                List.of(new GenericStack(ticket, 1)), new GenericStack(ticket, 6));
        Assertions.assertFalse(Pp2mpTaskBatch.hasValidPrimaryOutput(pattern));
        Assertions.assertNull(Pp2mpTaskBatch.planOutputs(pattern, Set.of(ticket)));
    }

    @Test
    void matchingNamedByproductCannotReplaceTheRequiredFakePrimary() {
        ItemStack expected = new ItemStack(Items.PAPER);
        expected.set(DataComponents.CUSTOM_NAME, Component.literal("fake-A"));
        ItemStack other = new ItemStack(Items.PAPER);
        other.set(DataComponents.CUSTOM_NAME, Component.literal("fake-A1"));
        AEItemKey expectedKey = AEItemKey.of(expected);
        AEItemKey otherKey = AEItemKey.of(other);
        Pp2mpPatternDetails pattern = Pp2mpTaskOutputsTest.pattern(true, 1,
                List.of(new GenericStack(expectedKey, 1), new GenericStack(otherKey, 1)),
                new GenericStack(AEItemKey.of(Items.DIAMOND), 1));
        Assertions.assertNull(Pp2mpTaskBatch.planOutputs(pattern, Set.of(otherKey)));
        Assertions.assertNotNull(Pp2mpTaskBatch.planOutputs(pattern, Set.of(expectedKey)));
    }

    @Test
    void completionUsesMarkerAmountWithoutAddingExplicitDuplicateByproducts() {
        AEKey ticket = AEItemKey.of(Items.PAPER);
        AEKey primary = AEItemKey.of(Items.DIAMOND);
        Pp2mpPatternDetails pattern = Pp2mpTaskOutputsTest.pattern(true, 2,
                List.of(new GenericStack(ticket, 2), new GenericStack(primary, 12),
                        new GenericStack(primary, 40)), new GenericStack(primary, 12));
        Pp2mpTaskBatch.OutputPlan plan = Pp2mpTaskBatch.planOutputs(pattern, Set.of(ticket));
        Assertions.assertNotNull(plan);
        Assertions.assertEquals(Map.of(primary, 12L), plan.pending());
        Assertions.assertEquals(52, pattern.getOutputs().get(1).amount());
    }

    @Test
    void fluidPrimaryUsesTheSameCompletionRule() {
        AEKey ticket = AEItemKey.of(Items.PAPER);
        AEKey primary = AEFluidKey.of(Fluids.WATER);
        Pp2mpPatternDetails pattern = Pp2mpTaskOutputsTest.pattern(true, 1,
                List.of(new GenericStack(ticket, 1)), new GenericStack(primary, 1000));
        Pp2mpTaskBatch.OutputPlan plan = Pp2mpTaskBatch.planOutputs(pattern, Set.of(ticket));
        Assertions.assertNotNull(plan);
        Assertions.assertEquals(primary, plan.primary());
        Assertions.assertEquals(Map.of(primary, 1000L), plan.pending());
    }

    @Test
    void ordinaryPatternsStillWaitForEveryOutput() {
        AEKey primary = AEItemKey.of(Items.DIAMOND);
        AEKey byproduct = AEItemKey.of(Items.GOLD_INGOT);
        Pp2mpPatternDetails pattern = Pp2mpTaskOutputsTest.pattern(false, 1,
                List.of(new GenericStack(primary, 6), new GenericStack(byproduct, 8)), null);
        Pp2mpTaskBatch.OutputPlan plan = Pp2mpTaskBatch.planOutputs(pattern, Set.of());
        Assertions.assertNotNull(plan);
        Assertions.assertNull(plan.primary());
        TaskProgress<AEKey> progress = new TaskProgress<>();
        progress.begin(plan.pending());
        progress.record(primary, 6);
        Assertions.assertTrue(progress.pending(plan.primary()));
    }

    /* 非批次交付保留完整产物数量，样板中的批次配置不再影响端点计数。 */
    @Test
    void unsplitDeliveryDoesNotDivideOutputsByConfiguredBatches() {
        AEKey product = AEItemKey.of(Items.DIAMOND);
        Pp2mpPatternDetails pattern = Pp2mpTaskOutputsTest.pattern(false, 20,
                List.of(new GenericStack(product, 100)), null);
        Pp2mpTaskBatch.OutputPlan plan = Pp2mpTaskBatch.planOutputs(pattern, Set.of());
        Assertions.assertNotNull(plan);
        Assertions.assertEquals(Map.of(product, 100L), plan.pending());
        TaskProgress<AEKey> progress = new TaskProgress<>();
        progress.begin(plan.pending());
        progress.record(product, 5);
        Assertions.assertTrue(progress.pending(plan.primary()));
        progress.record(product, 95);
        Assertions.assertFalse(progress.pending(plan.primary()));
    }

    @Test
    void fuzzyReturnsPreferPrimaryOverEarlierByproductWithSameRegistryId() {
        ItemStack primaryStack = new ItemStack(Items.DIAMOND);
        primaryStack.set(DataComponents.CUSTOM_NAME, Component.literal("primary"));
        ItemStack secondaryStack = new ItemStack(Items.DIAMOND);
        secondaryStack.set(DataComponents.CUSTOM_NAME, Component.literal("secondary"));
        AEKey primary = AEItemKey.of(primaryStack);
        AEKey secondary = AEItemKey.of(secondaryStack);
        Map<AEKey, Long> pending = new LinkedHashMap<>();
        pending.put(secondary, 8L);
        pending.put(primary, 6L);
        Assertions.assertEquals(primary, ProductReturnMatcher.find(
                AEItemKey.of(Items.DIAMOND), pending, ReturnMode.STRICT_CHECK_RID, primary));
        Assertions.assertNull(ProductReturnMatcher.find(
                AEItemKey.of(Items.DIAMOND), pending, ReturnMode.STRICT_FULL, primary));
    }
}
