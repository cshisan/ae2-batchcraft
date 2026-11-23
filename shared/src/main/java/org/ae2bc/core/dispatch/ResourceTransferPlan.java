package org.ae2bc.core.dispatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.LongUnaryOperator;

/* 保存本次预检可交付的数量，执行时只访问已规划目标，不接管资源所有权。 */
public final class ResourceTransferPlan {
    private final List<Step> steps;
    private final long amount;
    private final boolean evenly;
    private boolean executed;

    private ResourceTransferPlan(List<Step> steps, boolean evenly) {
        this.steps = Collections.unmodifiableList(steps);
        long total = 0;
        for (Step step : steps) {
            total = Math.addExact(total, step.limit);
        }
        this.amount = total;
        this.evenly = evenly;
    }

    public static ResourceTransferPlan empty() {
        return new ResourceTransferPlan(Collections.emptyList(), false);
    }

    public static ResourceTransferPlan leaf(long capacity, LongUnaryOperator inserter) {
        if (capacity <= 0) {
            return ResourceTransferPlan.empty();
        }
        return new ResourceTransferPlan(Collections.singletonList(new Step(capacity, inserter)), false);
    }

    /* 组合目标或管理器子计划，保留原有分配层级。 */
    public static ResourceTransferPlan combine(
            long requested, List<ResourceTransferPlan> children, boolean evenly
    ) {
        long[] capacities = new long[children.size()];
        for (int index = 0; index < children.size(); index++) {
            capacities[index] = children.get(index).amount();
        }
        long[] quotas = ResourceDistribution.allocate(requested, capacities, evenly);
        List<Step> steps = new ArrayList<>();
        for (int index = 0; index < quotas.length; index++) {
            if (quotas[index] > 0) {
                ResourceTransferPlan child = children.get(index);
                steps.add(new Step(quotas[index], child::execute));
            }
        }
        return new ResourceTransferPlan(steps, evenly);
    }

    public long amount() {
        return this.amount;
    }

    /* 计划只执行一次；短提取重新分配已有额度，实际拒收不会触发无限重试。 */
    public long execute(long supplied) {
        if (this.executed) {
            throw new IllegalStateException("Transfer plan was already executed");
        }
        this.executed = true;
        long available = Math.max(0, Math.min(supplied, this.amount));
        if (available == 0) {
            return 0;
        }
        if (this.steps.size() == 1) {
            return ResourceTransferPlan.insert(this.steps.get(0), available);
        }
        if (available == this.amount) {
            long inserted = 0;
            for (Step step : this.steps) {
                inserted += ResourceTransferPlan.insert(step, step.limit);
            }
            return inserted;
        }
        long[] capacities = new long[this.steps.size()];
        for (int index = 0; index < capacities.length; index++) {
            capacities[index] = this.steps.get(index).limit;
        }
        long[] quotas = ResourceDistribution.allocate(available, capacities, this.evenly);
        long inserted = 0;
        for (int index = 0; index < quotas.length; index++) {
            if (quotas[index] > 0) {
                inserted += ResourceTransferPlan.insert(this.steps.get(index), quotas[index]);
            }
        }
        return inserted;
    }

    private static long insert(Step step, long amount) {
        return Math.max(0, Math.min(amount, step.inserter.applyAsLong(amount)));
    }

    private static final class Step {
        private final long limit;
        private final LongUnaryOperator inserter;

        private Step(long limit, LongUnaryOperator inserter) {
            this.limit = limit;
            this.inserter = inserter;
        }
    }
}
