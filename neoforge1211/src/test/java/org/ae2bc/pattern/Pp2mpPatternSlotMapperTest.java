package org.ae2bc.pattern;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import com.sun.management.ThreadMXBean;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

class Pp2mpPatternSlotMapperTest {
    private static final AEKey STONE = AEItemKey.of(Items.STONE);
    private static final AEKey DIRT = AEItemKey.of(Items.DIRT);

    @Test
    void equalAmountsKeepSparseSlotsDuplicateMaterialsComponentsAndFluidUnits() {
        ItemStack named = new ItemStack(Items.STONE);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("distinct material"));
        GenericStack first = new GenericStack(Pp2mpPatternSlotMapperTest.STONE, 2);
        GenericStack repeated = new GenericStack(Pp2mpPatternSlotMapperTest.STONE, 4);
        GenericStack component = new GenericStack(Objects.requireNonNull(AEItemKey.of(named)), 3);
        GenericStack water = new GenericStack(AEFluidKey.of(Fluids.WATER), 1000);
        AEProcessingPattern pattern = Pp2mpPatternSlotMapperTest.pattern(
                null, first, null, repeated, component, water, null);
        KeyCounter[] supplied = Pp2mpPatternSlotMapperTest.inputs(first, repeated, component, water);
        Pp2mpPatternSlotMapper.MappingResult result = Pp2mpPatternSlotMapper.mapDetailed(pattern, supplied);
        Assertions.assertEquals(Pp2mpPatternSlotMapper.Failure.NONE, result.failure());
        Assertions.assertEquals(List.of(
                new Pp2mpPatternSlotMapper.SlotInput(1, first),
                new Pp2mpPatternSlotMapper.SlotInput(3, repeated),
                new Pp2mpPatternSlotMapper.SlotInput(4, component),
                new Pp2mpPatternSlotMapper.SlotInput(5, water)), result.inputs());
        Assertions.assertEquals(6, supplied[0].get(Pp2mpPatternSlotMapperTest.STONE));
        Assertions.assertEquals(3, supplied[0].get(component.what()));
        Assertions.assertEquals(1000, supplied[0].get(water.what()));
        List<Pp2mpPatternSlotMapper.SlotInput> mapped = Objects.requireNonNull(result.inputs());
        Assertions.assertThrows(UnsupportedOperationException.class, mapped::clear);
    }

    @Test
    void scaledInputsKeepExactRatiosAndRejectFractionalSlots() {
        AEProcessingPattern pattern = Pp2mpPatternSlotMapperTest.pattern(
                new GenericStack(Pp2mpPatternSlotMapperTest.STONE, 2), null,
                new GenericStack(Pp2mpPatternSlotMapperTest.STONE, 4),
                new GenericStack(Pp2mpPatternSlotMapperTest.DIRT, 2));
        for (long multiplier : new long[]{1, 2, 6}) {
            KeyCounter[] supplied = Pp2mpPatternSlotMapperTest.inputs(
                    new GenericStack(Pp2mpPatternSlotMapperTest.STONE, 3 * multiplier),
                    new GenericStack(Pp2mpPatternSlotMapperTest.DIRT, multiplier));
            Assertions.assertEquals(List.of(
                    new Pp2mpPatternSlotMapper.SlotInput(0, new GenericStack(Pp2mpPatternSlotMapperTest.STONE, multiplier)),
                    new Pp2mpPatternSlotMapper.SlotInput(2, new GenericStack(Pp2mpPatternSlotMapperTest.STONE, 2 * multiplier)),
                    new Pp2mpPatternSlotMapper.SlotInput(3, new GenericStack(Pp2mpPatternSlotMapperTest.DIRT, multiplier))),
                    Pp2mpPatternSlotMapper.map(pattern, supplied));
        }
        Assertions.assertEquals(Pp2mpPatternSlotMapper.Failure.NON_INTEGRAL_SCALE,
                Pp2mpPatternSlotMapper.mapDetailed(pattern, Pp2mpPatternSlotMapperTest.inputs(
                        new GenericStack(Pp2mpPatternSlotMapperTest.STONE, 3),
                        new GenericStack(Pp2mpPatternSlotMapperTest.DIRT, 2))).failure());
        AEProcessingPattern indivisible = Pp2mpPatternSlotMapperTest.pattern(
                new GenericStack(Pp2mpPatternSlotMapperTest.STONE, 1), null,
                new GenericStack(Pp2mpPatternSlotMapperTest.STONE, 1));
        Pp2mpPatternSlotMapper.MappingResult rejected = Pp2mpPatternSlotMapper.mapDetailed(
                indivisible, Pp2mpPatternSlotMapperTest.inputs(new GenericStack(Pp2mpPatternSlotMapperTest.STONE, 1)));
        Assertions.assertEquals(Pp2mpPatternSlotMapper.Failure.NON_INTEGRAL_SCALE, rejected.failure());
        Assertions.assertEquals(0, rejected.slot());
    }

    @Test
    void largeEqualAndScaledAmountsDoNotOverflowIntermediateProducts() {
        AEProcessingPattern pattern = Pp2mpPatternSlotMapperTest.pattern(
                new GenericStack(Pp2mpPatternSlotMapperTest.STONE, Long.MAX_VALUE - 1));
        for (long amount : new long[]{Long.MAX_VALUE - 1, (Long.MAX_VALUE - 1) / 2, Long.MAX_VALUE}) {
            GenericStack supplied = new GenericStack(Pp2mpPatternSlotMapperTest.STONE, amount);
            Assertions.assertEquals(List.of(new Pp2mpPatternSlotMapper.SlotInput(0, supplied)),
                    Pp2mpPatternSlotMapper.map(pattern, Pp2mpPatternSlotMapperTest.inputs(supplied)));
        }
    }

    @Test
    void invalidOrDifferentResourcesStillFailBeforeMapping() {
        AEProcessingPattern pattern = Pp2mpPatternSlotMapperTest.pattern(
                new GenericStack(Pp2mpPatternSlotMapperTest.STONE, 2));
        Assertions.assertEquals(Pp2mpPatternSlotMapper.Failure.INVALID_ARGUMENT,
                Pp2mpPatternSlotMapper.mapDetailed(pattern, null).failure());
        Assertions.assertEquals(Pp2mpPatternSlotMapper.Failure.EMPTY_RUNTIME_INPUT,
                Pp2mpPatternSlotMapper.mapDetailed(pattern, new KeyCounter[0]).failure());
        ItemStack named = new ItemStack(Items.STONE);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("different components"));
        for (AEKey key : List.of(Pp2mpPatternSlotMapperTest.DIRT, Objects.requireNonNull(AEItemKey.of(named)))) {
            Assertions.assertEquals(Pp2mpPatternSlotMapper.Failure.UNMATCHED_RUNTIME_INPUT,
                    Pp2mpPatternSlotMapper.mapDetailed(pattern,
                            Pp2mpPatternSlotMapperTest.inputs(new GenericStack(key, 2))).failure());
        }
        Assertions.assertEquals(Pp2mpPatternSlotMapper.Failure.INVALID_ARGUMENT,
                Pp2mpPatternSlotMapper.mapDetailed(pattern, Pp2mpPatternSlotMapperTest.inputs(
                        new GenericStack(Pp2mpPatternSlotMapperTest.STONE, -1))).failure());
        KeyCounter[] overflowing = {
                Pp2mpPatternSlotMapperTest.inputs(new GenericStack(Pp2mpPatternSlotMapperTest.STONE, Long.MAX_VALUE))[0],
                Pp2mpPatternSlotMapperTest.inputs(new GenericStack(Pp2mpPatternSlotMapperTest.STONE, 1))[0]};
        Assertions.assertEquals(Pp2mpPatternSlotMapper.Failure.INVALID_ARGUMENT,
                Pp2mpPatternSlotMapper.mapDetailed(pattern, overflowing).failure());
        AEProcessingPattern malformed = Mockito.mock(AEProcessingPattern.class);
        Mockito.when(malformed.getSparseInputs()).thenReturn(List.of(
                new GenericStack(Pp2mpPatternSlotMapperTest.STONE, Long.MAX_VALUE),
                new GenericStack(Pp2mpPatternSlotMapperTest.STONE, 1)));
        Assertions.assertEquals(Pp2mpPatternSlotMapper.Failure.INVALID_ARGUMENT,
                Pp2mpPatternSlotMapper.mapDetailed(malformed,
                        Pp2mpPatternSlotMapperTest.inputs(new GenericStack(Pp2mpPatternSlotMapperTest.STONE, 2))).failure());
    }

    /* 相同九材料负载记录分配量，正确性不依赖耗时或分配阈值。 */
    @Test
    void equalAmountWorkloadReportsAllocationWithoutTimingThresholds() {
        List<GenericStack> materials = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            ItemStack item = new ItemStack(Items.PAPER);
            item.set(DataComponents.CUSTOM_NAME, Component.literal("mapped material " + index));
            materials.add(new GenericStack(Objects.requireNonNull(AEItemKey.of(item)), 32 + index));
        }
        GenericStack[] stacks = materials.toArray(GenericStack[]::new);
        AEProcessingPattern pattern = Pp2mpPatternSlotMapperTest.pattern(stacks);
        KeyCounter[] supplied = Pp2mpPatternSlotMapperTest.inputs(stacks);
        for (int round = 0; round < 1024; round++) {
            Assertions.assertEquals(9, Objects.requireNonNull(Pp2mpPatternSlotMapper.map(pattern, supplied)).size());
        }
        ThreadMXBean allocation = ManagementFactory.getThreadMXBean() instanceof ThreadMXBean bean
                && bean.isThreadAllocatedMemorySupported() && bean.isThreadAllocatedMemoryEnabled() ? bean : null;
        long thread = Thread.currentThread().threadId();
        long before = allocation == null ? -1 : allocation.getThreadAllocatedBytes(thread);
        int rounds = 4096;
        long total = 0;
        for (int round = 0; round < rounds; round++) {
            List<Pp2mpPatternSlotMapper.SlotInput> mapped = Objects.requireNonNull(
                    Pp2mpPatternSlotMapper.map(pattern, supplied));
            total += mapped.getLast().stack().amount();
        }
        long allocated = allocation == null ? -1 : allocation.getThreadAllocatedBytes(thread) - before;
        Assertions.assertEquals(40L * rounds, total);
        System.out.printf(Locale.ROOT, "HOTPATH slot_mapping materials=9 rounds=%d bytes_per_call=%.1f%n",
                rounds, allocated < 0 ? -1.0 : (double) allocated / rounds);
    }

    private static AEProcessingPattern pattern(GenericStack... inputs) {
        return PatternTestSupport.processing(PatternDetailsHelper.encodeProcessingPattern(
                Arrays.asList(inputs), List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1))));
    }

    private static KeyCounter[] inputs(GenericStack... materials) {
        KeyCounter counter = new KeyCounter();
        for (GenericStack material : materials) {
            counter.add(material.what(), material.amount());
        }
        return new KeyCounter[]{counter};
    }
}
