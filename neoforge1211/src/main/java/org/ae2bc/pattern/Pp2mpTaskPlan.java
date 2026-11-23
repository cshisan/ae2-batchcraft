package org.ae2bc.pattern;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.AEProcessingPattern;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.ae2bc.registry.component.ComponentRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 接单时还原单批样板，资源数量始终来自此次实际交付。
 */
public record Pp2mpTaskPlan(AEItemKey pattern, KeyCounter[] inputs, long totalUnits) {
    /** 单批材料模板只编译一次，扣账不依赖外部机器是否修改传入计数器。 */
    public static final class InputTemplate {
        private final List<GenericStack> slots;

        public InputTemplate(IPatternDetails pattern) {
            List<GenericStack> inputs = new ArrayList<>();
            for (IPatternDetails.IInput input : pattern.getInputs()) {
                inputs.add(new GenericStack(input.getPossibleInputs()[0].what(), input.getMultiplier()));
            }
            this.slots = List.copyOf(inputs);
        }

        private void validate(KeyCounter[] remaining) {
            if (remaining.length != this.slots.size()) {
                throw new IllegalArgumentException("Persisted input shape changed");
            }
            for (int slot = 0; slot < remaining.length; slot++) {
                GenericStack required = this.slots.get(slot);
                if (remaining[slot] == null || remaining[slot].get(required.what()) < required.amount()) {
                    throw new IllegalArgumentException("Insufficient persisted batch inputs");
                }
            }
        }

        public KeyCounter[] offer(KeyCounter[] remaining) {
            this.validate(remaining);
            KeyCounter[] offered = new KeyCounter[this.slots.size()];
            for (int slot = 0; slot < offered.length; slot++) {
                GenericStack required = this.slots.get(slot);
                offered[slot] = new KeyCounter();
                offered[slot].add(required.what(), required.amount());
            }
            return offered;
        }

        public void deduct(KeyCounter[] remaining) {
            this.validate(remaining);
            for (int slot = 0; slot < remaining.length; slot++) {
                GenericStack required = this.slots.get(slot);
                remaining[slot].remove(required.what(), required.amount());
                remaining[slot].removeZeros();
            }
        }
    }

    /* 只恢复已知物品的处理样板，不通过反射猜测第三方包装器的字段。 */
    public static IPatternDetails restore(AEItemKey definition, Level level) {
        if (definition.getItem() instanceof Pp2mpPatternItem) {
            return new Pp2mpPatternDetails(definition, level);
        }
        if (definition.getItem() == AEItems.PROCESSING_PATTERN.asItem()) {
            return new AEProcessingPattern(definition);
        }
        throw new IllegalArgumentException("Unsupported processing pattern definition");
    }

    public static AEProcessingPattern processing(IPatternDetails pattern) {
        if (pattern instanceof Pp2mpPatternDetails details
                && details.getDelegate() instanceof AEProcessingPattern processing) {
            return processing;
        }
        if (pattern != null && pattern.getClass() == AEProcessingPattern.class) {
            return (AEProcessingPattern) pattern;
        }
        throw new IllegalArgumentException("Unsupported processing pattern semantics");
    }

    /* 校验线性倍率后生成单批样板，拒绝非整除、语义改变和数量溢出。 */
    public static Pp2mpTaskPlan resolve(IPatternDetails execution, KeyCounter[] received, Level level) {
        IPatternDetails original = Pp2mpTaskPlan.restore(execution.getDefinition(), level);
        AEProcessingPattern processing = Pp2mpTaskPlan.processing(original);
        IPatternDetails.IInput[] baseInputs = original.getInputs();
        IPatternDetails.IInput[] executionInputs = execution.getInputs();
        if (!execution.supportsPushInputsToExternalInventory()
                || executionInputs.length != baseInputs.length || baseInputs.length == 0) {
            throw new IllegalArgumentException("Unsupported execution input shape");
        }
        long executionCopies = -1;
        for (int slot = 0; slot < baseInputs.length; slot++) {
            IPatternDetails.IInput base = baseInputs[slot];
            IPatternDetails.IInput scaled = executionInputs[slot];
            GenericStack[] originals = base.getPossibleInputs();
            GenericStack[] variants = scaled.getPossibleInputs();
            if (originals.length != variants.length) {
                throw new IllegalArgumentException("Execution substitutions changed");
            }
            for (int variant = 0; variant < originals.length; variant++) {
                GenericStack possible = originals[variant];
                GenericStack actual = variants[variant];
                long copies = Pp2mpTaskPlan.ratio(
                        Math.multiplyExact(actual.amount(), scaled.getMultiplier()),
                        Math.multiplyExact(possible.amount(), base.getMultiplier()));
                if (!possible.what().equals(actual.what())
                        || (executionCopies > 0 && executionCopies != copies)) {
                    throw new IllegalArgumentException("Non-linear execution inputs");
                }
                executionCopies = copies;
                if (!scaled.isValid(possible.what(), level) || scaled.getRemainingKey(possible.what()) != null) {
                    throw new IllegalArgumentException("Processing input remainder requires an adapter");
                }
            }
        }
        Map<AEKey, Long> expectedOutputs = Pp2mpTaskPlan.amounts(original.getOutputs());
        Map<AEKey, Long> actualOutputs = Pp2mpTaskPlan.amounts(execution.getOutputs());
        if (expectedOutputs.isEmpty() || !expectedOutputs.keySet().equals(actualOutputs.keySet())
                || !original.getPrimaryOutput().what().equals(execution.getPrimaryOutput().what())) {
            throw new IllegalArgumentException("Execution outputs changed");
        }
        for (Map.Entry<AEKey, Long> output : expectedOutputs.entrySet()) {
            if (Math.multiplyExact(output.getValue(), executionCopies) != actualOutputs.get(output.getKey())) {
                throw new IllegalArgumentException("Non-linear execution outputs");
            }
        }

        Map<AEKey, Long> held = Pp2mpTaskPlan.amounts(received);
        Map<AEKey, Long> encoded = Pp2mpTaskPlan.amounts(processing.getSparseInputs());
        if (!held.keySet().equals(encoded.keySet())) {
            throw new IllegalArgumentException("Received resources do not match the processing pattern");
        }
        long receivedCopies = -1;
        for (Map.Entry<AEKey, Long> input : encoded.entrySet()) {
            long copies = Pp2mpTaskPlan.ratio(held.get(input.getKey()), input.getValue());
            if (receivedCopies > 0 && receivedCopies != copies) {
                throw new IllegalArgumentException("Received input ratios differ");
            }
            receivedCopies = copies;
        }
        if (receivedCopies < executionCopies || receivedCopies % executionCopies != 0) {
            throw new IllegalArgumentException("Incomplete execution inputs");
        }
        // 验证第三方包装器的真实发料语义，不仅比较表面数量。
        if (execution.getClass() != original.getClass()) {
            KeyCounter[] probe = new KeyCounter[baseInputs.length];
            Map<AEKey, Long> offered = new LinkedHashMap<>();
            for (int slot = 0; slot < probe.length; slot++) {
                AEKey key = baseInputs[slot].getPossibleInputs()[0].what();
                long amount = Math.multiplyExact(baseInputs[slot].getMultiplier(), executionCopies);
                probe[slot] = new KeyCounter();
                probe[slot].add(key, amount);
                offered.merge(key, amount, Math::addExact);
            }
            Map<AEKey, Long> emitted = new LinkedHashMap<>();
            execution.pushInputsToExternalInventory(probe, (key, amount) -> {
                if (key == null || amount <= 0) {
                    throw new IllegalArgumentException("Invalid execution emission");
                }
                emitted.merge(key, amount, Math::addExact);
            });
            if (!offered.equals(emitted)) {
                throw new IllegalArgumentException("Execution changed the supplied resources");
            }
        }
        long batches = Pp2mpTaskBatch.configuredBatchCount(original);
        long units = Math.multiplyExact(receivedCopies, batches);
        AEItemKey definition = Pp2mpTaskPlan.singleDefinition(original);
        IPatternDetails normalized = Pp2mpTaskPlan.restore(definition, level);
        if (!Pp2mpTaskBatch.hasValidPrimaryOutput(normalized)) {
            throw new IllegalArgumentException("Invalid fake crafting primary output");
        }
        // 按单批样板的压缩输入顺序保存，每个计数器只持有真实交付的资源。
        IPatternDetails.IInput[] singleInputs = normalized.getInputs();
        KeyCounter[] pools = new KeyCounter[singleInputs.length];
        for (int slot = 0; slot < pools.length; slot++) {
            AEKey key = singleInputs[slot].getPossibleInputs()[0].what();
            pools[slot] = new KeyCounter();
            pools[slot].add(key, held.get(key));
        }
        return new Pp2mpTaskPlan(definition, pools, units);
    }

    public static AEItemKey singleDefinition(IPatternDetails original) {
        AEProcessingPattern processing = Pp2mpTaskPlan.processing(original);
        long batches = Pp2mpTaskBatch.configuredBatchCount(original);
        ItemStack single = original.getDefinition().toStack();
        AEProcessingPattern.encode(single,
                Pp2mpTaskPlan.divide(processing.getSparseInputs(), batches),
                Pp2mpTaskPlan.divide(processing.getSparseOutputs(), batches));
        if (single.getItem() instanceof Pp2mpPatternItem) {
            if (MaterialOutputModeResolver.hasFakeInputs(original)) {
                GenericStack marker = single.get(ComponentRegistry.REAL_OUTPUT_MARKER);
                if (marker == null) {
                    throw new IllegalArgumentException("Missing fake crafting real output marker");
                }
                single.set(ComponentRegistry.REAL_OUTPUT_MARKER,
                        new GenericStack(marker.what(), Pp2mpTaskPlan.ratio(marker.amount(), batches)));
            } else {
                single.remove(ComponentRegistry.REAL_OUTPUT_MARKER);
            }
            single.set(ComponentRegistry.PATTERN_BATCH_COUNT, 1L);
        }
        AEItemKey definition = AEItemKey.of(single);
        if (definition == null) {
            throw new IllegalArgumentException("Normalized processing pattern must not be empty");
        }
        return definition;
    }

    private static long ratio(long amount, long base) {
        if (base <= 0 || amount <= 0 || amount % base != 0) {
            throw new IllegalArgumentException("Non-integral processing multiplier");
        }
        return amount / base;
    }

    private static List<GenericStack> divide(List<GenericStack> stacks, long divisor) {
        List<GenericStack> divided = new ArrayList<>(stacks.size());
        for (GenericStack stack : stacks) {
            divided.add(stack == null ? null
                    : new GenericStack(stack.what(), Pp2mpTaskPlan.ratio(stack.amount(), divisor)));
        }
        return divided;
    }

    private static Map<AEKey, Long> amounts(List<GenericStack> stacks) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (GenericStack stack : stacks) {
            if (stack != null) {
                if (stack.amount() <= 0) {
                    throw new IllegalArgumentException("Non-positive pattern amount");
                }
                result.merge(stack.what(), stack.amount(), Math::addExact);
            }
        }
        return result;
    }

    public static Map<AEKey, Long> amounts(KeyCounter[] inputs) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (KeyCounter counter : inputs) {
            if (counter != null) {
                for (Object2LongMap.Entry<AEKey> entry : counter) {
                    if (entry.getLongValue() <= 0) {
                        throw new IllegalArgumentException("Non-positive received amount");
                    }
                    result.merge(entry.getKey(), entry.getLongValue(), Math::addExact);
                }
            }
        }
        return result;
    }

    /* 只读取剩余资源生成下一批，收到端点确认前不扣账。 */
    public static KeyCounter[] nextInputs(IPatternDetails single, KeyCounter[] remaining) {
        return new InputTemplate(single).offer(remaining);
    }
}
