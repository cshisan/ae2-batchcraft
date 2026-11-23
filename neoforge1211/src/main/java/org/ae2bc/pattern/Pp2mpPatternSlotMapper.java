package org.ae2bc.pattern;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将运行时处理输入映射回稀疏的编码输入槽位。
 */
public final class Pp2mpPatternSlotMapper {
    private Pp2mpPatternSlotMapper() {
    }

    public static @Nullable List<SlotInput> map(AEProcessingPattern pattern, KeyCounter[] runtimeInputs) {
        MappingResult result = Pp2mpPatternSlotMapper.mapDetailed(pattern, runtimeInputs);
        return result.inputs();
    }

    /* 按资源总量恢复原始槽位，保留同一资源在多个槽位上的方向和输出方式。 */
    public static MappingResult mapDetailed(AEProcessingPattern pattern, KeyCounter[] runtimeInputs) {
        if (pattern == null || runtimeInputs == null) {
            return MappingResult.failure(Failure.INVALID_ARGUMENT, -1, 0, 0);
        }
        Map<AEKey, Long> runtime;
        Map<AEKey, Long> encodedTotals = new LinkedHashMap<>();
        try {
            runtime = Pp2mpTaskPlan.amounts(runtimeInputs);
            for (GenericStack encoded : pattern.getSparseInputs()) {
                if (encoded != null) {
                    encodedTotals.merge(encoded.what(), encoded.amount(), Math::addExact);
                }
            }
        } catch (IllegalArgumentException | ArithmeticException exception) {
            return MappingResult.failure(Failure.INVALID_ARGUMENT, -1, 0, 0);
        }
        if (runtime.isEmpty()) {
            return MappingResult.failure(Failure.EMPTY_RUNTIME_INPUT, -1, 0, 0);
        }
        if (!runtime.keySet().equals(encodedTotals.keySet())) {
            return MappingResult.failure(Failure.UNMATCHED_RUNTIME_INPUT, -1, 0, 0);
        }
        // 等量交付直接保留编码数量，缩放交付仍使用大整数校验。
        boolean sameAmounts = runtime.equals(encodedTotals);
        if (!sameAmounts) {
            AEKey first = encodedTotals.keySet().iterator().next();
            BigInteger baseAmount = BigInteger.valueOf(encodedTotals.get(first));
            BigInteger runtimeAmount = BigInteger.valueOf(runtime.get(first));
            for (AEKey key : encodedTotals.keySet()) {
                if (!BigInteger.valueOf(runtime.get(key)).multiply(baseAmount).equals(
                        BigInteger.valueOf(encodedTotals.get(key)).multiply(runtimeAmount))) {
                    return MappingResult.failure(Failure.NON_INTEGRAL_SCALE, -1, 0, 0);
                }
            }
        }
        List<GenericStack> sparse = pattern.getSparseInputs();
        List<SlotInput> result = new ArrayList<>();
        for (int slot = 0; slot < sparse.size(); slot++) {
            GenericStack encoded = sparse.get(slot);
            if (encoded == null) {
                continue;
            }
            long amount = sameAmounts ? encoded.amount() : Pp2mpPatternSlotMapper.scaleRequired(
                    encoded.amount(), runtime.get(encoded.what()), encodedTotals.get(encoded.what()));
            if (amount <= 0) {
                return MappingResult.failure(Failure.NON_INTEGRAL_SCALE, slot, encoded.amount(), amount);
            }
            result.add(new SlotInput(slot, sameAmounts ? encoded : new GenericStack(encoded.what(), amount)));
        }
        return MappingResult.success(result);
    }

    private static long scaleRequired(long encodedAmount, long runtimeTotal, long nominalTotal) {
        BigInteger numerator = BigInteger.valueOf(encodedAmount)
                .multiply(BigInteger.valueOf(runtimeTotal));
        BigInteger denominator = BigInteger.valueOf(nominalTotal);
        BigInteger[] division = numerator.divideAndRemainder(denominator);
        if (division[1].signum() != 0 || division[0].signum() <= 0
                || division[0].compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            return -1;
        }
        return division[0].longValue();
    }

    public enum Failure {
        NONE,
        INVALID_ARGUMENT,
        EMPTY_RUNTIME_INPUT,
        NON_INTEGRAL_SCALE,
        UNMATCHED_RUNTIME_INPUT
    }

    public record SlotInput(int slot, GenericStack stack) {
    }

    public record MappingResult(
            @Nullable List<SlotInput> inputs,
            Failure failure,
            int slot,
            long expected,
            long actual
    ) {
        private static MappingResult success(List<SlotInput> inputs) {
            return new MappingResult(List.copyOf(inputs), Failure.NONE, -1, 0, 0);
        }

        private static MappingResult failure(Failure failure, int slot, long expected, long actual) {
            return new MappingResult(null, failure, slot, expected, actual);
        }
    }

}
