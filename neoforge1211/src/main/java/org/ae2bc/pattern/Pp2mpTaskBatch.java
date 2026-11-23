package org.ae2bc.pattern;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import org.ae2bc.common.Utils;
import org.ae2bc.core.pattern.PatternBatchCount;
import org.ae2bc.registry.component.ComponentRegistry;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 校验处理任务，并为一次或多次合成创建精确的输入批次。
 */
public final class Pp2mpTaskBatch {
    private Pp2mpTaskBatch() {
    }

    /* 真正产物仅从独立标记读取，不占用 AE2 的产物槽。 */
    private static @Nullable GenericStack realPrimaryOutput(IPatternDetails pattern) {
        return pattern instanceof Pp2mpPatternDetails
                ? pattern.getDefinition().get(ComponentRegistry.REAL_OUTPUT_MARKER) : null;
    }

    /* 接单前校验独立标记，防止接收材料后无法判断任务完成。 */
    public static boolean hasValidPrimaryOutput(IPatternDetails pattern) {
        if (!MaterialOutputModeResolver.hasFakeInputs(pattern)) {
            return true;
        }
        GenericStack primary = Pp2mpTaskBatch.realPrimaryOutput(pattern);
        return primary != null && primary.amount() > 0
                && !primary.what().equals(pattern.getPrimaryOutput().what());
    }

    /* 独立标记加入内部返回范围与计数，但不加入样板声明的产物。 */
    public static @Nullable OutputPlan planOutputs(IPatternDetails pattern, Set<AEKey> fakeOutputs) {
        if (!Pp2mpTaskBatch.hasValidPrimaryOutput(pattern)) {
            return null;
        }
        LinkedHashMap<AEKey, Long> pending = new LinkedHashMap<>();
        for (GenericStack output : pattern.getOutputs()) {
            if (output == null || output.amount() <= 0 || fakeOutputs.contains(output.what())) {
                continue;
            }
            pending.merge(output.what(), output.amount(), Utils::saturatingAdd);
        }
        AEKey primaryKey = null;
        if (MaterialOutputModeResolver.hasFakeInputs(pattern)) {
            GenericStack primary = Pp2mpTaskBatch.realPrimaryOutput(pattern);
            if (primary == null || !fakeOutputs.contains(pattern.getPrimaryOutput().what())
                    || fakeOutputs.contains(primary.what())) {
                return null;
            }
            primaryKey = primary.what();
            // 显式声明的同类副产物不累加到标记的完成数量；实际返回仍不限制数量。
            pending.putFirst(primaryKey, primary.amount());
        }
        return new OutputPlan(pending, primaryKey);
    }

    public static @Nullable KeyCounter[] scaleInputs(
            KeyCounter[] inputs,
            long units,
            long totalUnits,
            long startUnit
    ) {
        if (inputs == null || units <= 0 || totalUnits <= 0 || units > totalUnits) {
            return null;
        }
        if (startUnit < 0 || startUnit > totalUnits - units) {
            return null;
        }
        KeyCounter[] scaled = new KeyCounter[inputs.length];
        for (int slot = 0; slot < inputs.length; slot++) {
            KeyCounter source = inputs[slot];
            if (source == null) {
                continue;
            }
            KeyCounter destination = new KeyCounter();
            for (it.unimi.dsi.fastutil.objects.Object2LongMap.Entry<AEKey> entry : source) {
                long amount = scaleRange(entry.getLongValue(), startUnit, units, totalUnits);
                if (amount < 0) {
                    return null;
                }
                if (amount > 0) {
                    destination.add(entry.getKey(), amount);
                }
            }
            scaled[slot] = destination.isEmpty() ? null : destination;
        }
        return scaled;
    }

    public static long configuredBatchCount(IPatternDetails pattern) {
        if (!(pattern instanceof Pp2mpPatternDetails)) {
            return PatternBatchCount.DEFAULT;
        }
        Long value = pattern.getDefinition().get(ComponentRegistry.PATTERN_BATCH_COUNT);
        return value == null ? PatternBatchCount.DEFAULT : Math.max(PatternBatchCount.DEFAULT, value);
    }

    private static long scaleRange(long amount, long startUnit, long units, long totalUnits) {
        if (amount < 0) {
            return -1;
        }
        BigInteger total = BigInteger.valueOf(totalUnits);
        BigInteger amountValue = BigInteger.valueOf(amount);
        BigInteger start = amountValue.multiply(BigInteger.valueOf(startUnit)).divide(total);
        BigInteger end = amountValue.multiply(BigInteger.valueOf(startUnit + units)).divide(total);
        return end.subtract(start).longValueExact();
    }

    public record OutputPlan(Map<AEKey, Long> pending, @Nullable AEKey primary) {
    }
}
