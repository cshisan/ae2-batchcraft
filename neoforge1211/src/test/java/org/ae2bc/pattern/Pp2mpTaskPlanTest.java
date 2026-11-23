package org.ae2bc.pattern;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.*;
import appeng.crafting.pattern.AEProcessingPattern;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import org.ae2bc.core.pattern.MaterialOutputMode;
import org.ae2bc.registry.component.ComponentRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

class Pp2mpTaskPlanTest {
    private static IPatternDetails vanilla(long a, long b, long output) {
        return PatternTestSupport.processing(PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.STONE), a),
                        new GenericStack(AEItemKey.of(Items.DIRT), b)),
                List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), output))));
    }

    private static IPatternDetails pp2mp(long a, long b, long output, long batches) {
        ItemStack encoded = Pp2mpPatternItem.encodePattern(
                Pp2mpTaskPlanTest.vanilla(a, b, output).getDefinition().toStack(),
                MaterialOutputAttr.EMPTY.withMode(1, MaterialOutputMode.DROP));
        encoded.set(ComponentRegistry.PATTERN_BATCH_COUNT, batches);
        return new Pp2mpPatternDetails(AEItemKey.of(encoded), null);
    }

    private static KeyCounter[] inputs(long a, long b) {
        KeyCounter[] inputs = {new KeyCounter(), new KeyCounter()};
        inputs[0].add(AEItemKey.of(Items.STONE), a);
        inputs[1].add(AEItemKey.of(Items.DIRT), b);
        return inputs;
    }

    @Test
    void ecoStyleMergedVanillaPatternIsSplitIntoOriginalRecipes() {
        IPatternDetails base = Pp2mpTaskPlanTest.vanilla(2, 2, 1);
        IPatternDetails merged = new ScaledPattern(base, 64);
        KeyCounter[] received = Pp2mpTaskPlanTest.inputs(128, 128);
        Pp2mpTaskPlan plan = Pp2mpTaskPlan.resolve(merged, received, null);
        Assertions.assertEquals(64, plan.totalUnits());
        IPatternDetails single = Pp2mpTaskPlan.restore(plan.pattern(), null);
        Assertions.assertEquals(1, single.getPrimaryOutput().amount());
        Assertions.assertEquals(Pp2mpTaskPlan.amounts(Pp2mpTaskPlanTest.inputs(2, 2)),
                Pp2mpTaskPlan.amounts(Pp2mpTaskPlan.nextInputs(single, plan.inputs())));
        Assertions.assertEquals(128, received[0].get(AEItemKey.of(Items.STONE)));
    }

    @Test
    void pp2mpConfiguredBatchesMultiplyWithExternalCopiesExactlyOnce() {
        IPatternDetails original = Pp2mpTaskPlanTest.pp2mp(20, 20, 10, 10);
        Pp2mpTaskPlan plan = Pp2mpTaskPlan.resolve(
                new ScaledPattern(original, 50), Pp2mpTaskPlanTest.inputs(1000, 1000), null);
        Assertions.assertEquals(500, plan.totalUnits());
        IPatternDetails single = Pp2mpTaskPlan.restore(plan.pattern(), null);
        Assertions.assertEquals(1, Pp2mpTaskBatch.configuredBatchCount(single));
        Assertions.assertEquals(1, single.getPrimaryOutput().amount());
        Assertions.assertEquals(2, single.getInputs()[0].getMultiplier());
        Assertions.assertEquals(MaterialOutputMode.DROP, MaterialOutputModeResolver.resolve(single, 1));
    }

    /* 独立标记按原子批次归一，第三方合并倍数只增加批数，不重复放大标记。 */
    @Test
    void independentMarkerDividesOnceAndSurvivesDefinitionPersistence() {
        ItemStack encoded = Pp2mpPatternItem.encodePattern(
                Pp2mpTaskPlanTest.vanilla(20, 20, 10).getDefinition().toStack(),
                MaterialOutputAttr.EMPTY.withMode(1, MaterialOutputMode.FAKE_CRAFTING_CONVERTER));
        GenericStack marker = new GenericStack(AEFluidKey.of(Fluids.WATER), 10000);
        encoded.set(ComponentRegistry.REAL_OUTPUT_MARKER, marker);
        encoded.set(ComponentRegistry.PATTERN_BATCH_COUNT, 10L);
        IPatternDetails original = new Pp2mpPatternDetails(AEItemKey.of(encoded), null);
        Pp2mpTaskPlan plan = Pp2mpTaskPlan.resolve(
                new ScaledPattern(original, 50), Pp2mpTaskPlanTest.inputs(1000, 1000), null);
        Assertions.assertEquals(500, plan.totalUnits());
        IPatternDetails single = Pp2mpTaskPlan.restore(plan.pattern(), null);
        Assertions.assertEquals(new GenericStack(marker.what(), 1000),
                single.getDefinition().get(ComponentRegistry.REAL_OUTPUT_MARKER));
        Assertions.assertEquals(List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1)), single.getOutputs());
        Assertions.assertEquals(marker, original.getDefinition().get(ComponentRegistry.REAL_OUTPUT_MARKER));
        Assertions.assertEquals(plan.pattern(), Pp2mpTaskPlan.singleDefinition(single));
    }

    @Test
    void missingNonPositiveOrNonDivisibleMarkerCannotStartABatch() {
        for (Long amount : Arrays.asList(null, 0L, -1L, 3L)) {
            ItemStack encoded = Pp2mpPatternItem.encodePattern(
                    Pp2mpTaskPlanTest.vanilla(20, 20, 10).getDefinition().toStack(),
                    MaterialOutputAttr.EMPTY.withMode(1, MaterialOutputMode.FAKE_CRAFTING_CONVERTER));
            encoded.set(ComponentRegistry.PATTERN_BATCH_COUNT, 10L);
            if (amount != null) {
                encoded.set(ComponentRegistry.REAL_OUTPUT_MARKER,
                        new GenericStack(AEItemKey.of(Items.EMERALD), amount));
            }
            IPatternDetails pattern = new Pp2mpPatternDetails(AEItemKey.of(encoded), null);
            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> Pp2mpTaskPlan.resolve(pattern, Pp2mpTaskPlanTest.inputs(20, 20), null));
        }
    }

    /* 每批交付配置切分后的完整材料，不再拆成配方的最小整数比例。 */
    @Test
    void twentyBatchesDeliverFiveOfEachMaterialAndExpectFiveProducts() {
        for (long copies : new long[]{1, 3}) {
            IPatternDetails original = Pp2mpTaskPlanTest.pp2mp(100, 100, 100, 20);
            Pp2mpTaskPlan plan = Pp2mpTaskPlan.resolve(
                    new ScaledPattern(original, copies),
                    Pp2mpTaskPlanTest.inputs(100 * copies, 100 * copies), null);
            IPatternDetails single = Pp2mpTaskPlan.restore(plan.pattern(), null);
            Pp2mpTaskPlan.InputTemplate template = new Pp2mpTaskPlan.InputTemplate(single);
            Pp2mpTaskBatch.OutputPlan outputs = Pp2mpTaskBatch.planOutputs(single, Set.of());
            Assertions.assertNotNull(outputs);
            Assertions.assertEquals(Map.of(AEItemKey.of(Items.DIAMOND), 5L), outputs.pending());
            Assertions.assertEquals(20 * copies, plan.totalUnits());
            for (long batch = 0; batch < plan.totalUnits(); batch++) {
                Assertions.assertEquals(Map.of(AEItemKey.of(Items.STONE), 5L, AEItemKey.of(Items.DIRT), 5L),
                        Pp2mpTaskPlan.amounts(template.offer(plan.inputs())));
                template.deduct(plan.inputs());
            }
            Assertions.assertTrue(Pp2mpTaskPlan.amounts(plan.inputs()).isEmpty());
        }
    }

    @Test
    void unchangedPatternWithMergedCountersAlsoUsesActualInputAmount() {
        Pp2mpTaskPlan plan = Pp2mpTaskPlan.resolve(
                Pp2mpTaskPlanTest.vanilla(2, 2, 1), Pp2mpTaskPlanTest.inputs(20, 20), null);
        Assertions.assertEquals(10, plan.totalUnits());
    }

    @Test
    void duplicateSparseInputsAndEmptySlotsKeepTheirOriginalSlots() {
        AEKey stone = AEItemKey.of(Items.STONE);
        ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(
                Arrays.asList(new GenericStack(stone, 2), null, new GenericStack(stone, 4)),
                List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 2)));
        IPatternDetails base = PatternTestSupport.processing(encoded);
        KeyCounter[] received = new KeyCounter[]{new KeyCounter()};
        received[0].add(stone, 60);
        Pp2mpTaskPlan plan = Pp2mpTaskPlan.resolve(new ScaledPattern(base, 10), received, null);
        AEProcessingPattern single = Pp2mpTaskPlan.processing(Pp2mpTaskPlan.restore(plan.pattern(), null));
        List<Pp2mpPatternSlotMapper.SlotInput> slots = Pp2mpPatternSlotMapper.map(
                single, Pp2mpTaskPlan.nextInputs(single, plan.inputs()));
        Assertions.assertNotNull(slots);
        Assertions.assertEquals(List.of(
                new Pp2mpPatternSlotMapper.SlotInput(0, new GenericStack(stone, 2)),
                new Pp2mpPatternSlotMapper.SlotInput(2, new GenericStack(stone, 4))), slots);
    }

    @Test
    void fluidAndItemAmountsRemainInTheirOwnUnits() {
        AEKey water = AEFluidKey.of(Fluids.WATER);
        ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.STONE), 2), new GenericStack(water, 1000)),
                List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1)));
        KeyCounter[] received = new KeyCounter[]{new KeyCounter(), new KeyCounter()};
        received[0].add(AEItemKey.of(Items.STONE), 200);
        received[1].add(water, 100000);
        Pp2mpTaskPlan plan = Pp2mpTaskPlan.resolve(
                new ScaledPattern(PatternTestSupport.processing(encoded), 100), received, null);
        Assertions.assertEquals(100, plan.totalUnits());
        Assertions.assertEquals(Map.of(AEItemKey.of(Items.STONE), 2L, water, 1000L),
                Pp2mpTaskPlan.amounts(Pp2mpTaskPlan.nextInputs(
                        Pp2mpTaskPlan.restore(plan.pattern(), null), plan.inputs())));
    }

    @Test
    void tenMillionCopiesDoNotExpandIntoTenMillionTasks() {
        Pp2mpTaskPlan plan = Pp2mpTaskPlan.resolve(new ScaledPattern(
                        Pp2mpTaskPlanTest.vanilla(2, 2, 1), 10_000_000),
                Pp2mpTaskPlanTest.inputs(20_000_000, 20_000_000), null);
        Assertions.assertEquals(10_000_000, plan.totalUnits());
        Assertions.assertEquals(2, plan.inputs().length);
        Assertions.assertEquals(1, plan.inputs()[0].size());
    }

    @Test
    void rejectsMismatchedRatiosAndNonDivisibleConfiguredBatch() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Pp2mpTaskPlan.resolve(
                new ScaledPattern(Pp2mpTaskPlanTest.vanilla(2, 2, 1), 10),
                Pp2mpTaskPlanTest.inputs(20, 18), null));
        Assertions.assertThrows(IllegalArgumentException.class, () -> Pp2mpTaskPlan.resolve(
                Pp2mpTaskPlanTest.pp2mp(3, 4, 2, 2), Pp2mpTaskPlanTest.inputs(3, 4), null));
    }

    @Test
    void rejectsUnaccountedResourcesAndChangedWrapperEmissions() {
        KeyCounter[] inputs = Pp2mpTaskPlanTest.inputs(4, 4);
        inputs[0].add(AEItemKey.of(Items.PAPER), 1);
        Assertions.assertThrows(IllegalArgumentException.class, () -> Pp2mpTaskPlan.resolve(
                new ScaledPattern(Pp2mpTaskPlanTest.vanilla(2, 2, 1), 2), inputs, null));
        IPatternDetails incorrect = new ScaledPattern(Pp2mpTaskPlanTest.vanilla(2, 2, 1), 2) {
            @Override
            public void pushInputsToExternalInventory(KeyCounter[] inputs, PatternInputSink sink) {
                sink.pushInput(AEItemKey.of(Items.STONE), 1);
            }
        };
        Assertions.assertThrows(IllegalArgumentException.class, () -> Pp2mpTaskPlan.resolve(
                incorrect, Pp2mpTaskPlanTest.inputs(4, 4), null));
    }

    private static class ScaledPattern implements IPatternDetails {
        private final IPatternDetails original;
        private final long copies;

        private ScaledPattern(IPatternDetails original, long copies) {
            this.original = original;
            this.copies = copies;
        }

        @Override
        public AEItemKey getDefinition() {
            return this.original.getDefinition();
        }

        @Override
        public IInput[] getInputs() {
            IInput[] base = this.original.getInputs();
            IInput[] result = new IInput[base.length];
            for (int slot = 0; slot < base.length; slot++) {
                IInput input = base[slot];
                result[slot] = new IInput() {
                    public GenericStack[] getPossibleInputs() {
                        return input.getPossibleInputs();
                    }

                    public long getMultiplier() {
                        return Math.multiplyExact(input.getMultiplier(), ScaledPattern.this.copies);
                    }

                    public boolean isValid(AEKey key, Level level) {
                        return input.isValid(key, level);
                    }

                    public AEKey getRemainingKey(AEKey key) {
                        return input.getRemainingKey(key);
                    }
                };
            }
            return result;
        }

        @Override
        public List<GenericStack> getOutputs() {
            return this.original.getOutputs().stream()
                    .map(stack -> new GenericStack(stack.what(), Math.multiplyExact(stack.amount(), this.copies)))
                    .toList();
        }
    }
}
