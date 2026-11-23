package org.ae2bc.part.general;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import org.ae2bc.core.dispatch.ResourceTransferPlan;
import org.ae2bc.core.dispatch.WorkBudget;
import org.ae2bc.part.general.provider_filter.Pp2mpProviderFilterData;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/* 统一执行供应过滤、数量限制和目标插入。 */
public final class Pp2mpProviderTarget {
    private final Pp2mpSendTarget target;
    private final Supplier<Pp2mpProviderFilterData> filter;
    private final BooleanSupplier operational;
    private final IActionSource source;
    private final Runnable inserted;

    public Pp2mpProviderTarget(
            Pp2mpSendTarget target, Supplier<Pp2mpProviderFilterData> filter,
            BooleanSupplier operational, IActionSource source, Runnable inserted
    ) {
        this.target = target;
        this.filter = filter;
        this.operational = operational;
        this.source = source;
        this.inserted = inserted;
    }

    /* 每个目标只预检一次，并为计划内的一次实际插入预留执行机会。 */
    public ResourceTransferPlan plan(AEKey key, long amount, WorkBudget budget) {
        if (!budget.tryUse()) {
            return ResourceTransferPlan.empty();
        }
        long capacity = this.insert(key, amount, Actionable.SIMULATE);
        return ResourceTransferPlan.leaf(capacity, supplied -> this.insert(key, supplied, Actionable.MODULATE));
    }

    /* 实际执行重新检查目标和缺额，不把模拟容量当作库存预留。 */
    private long insert(AEKey key, long amount, Actionable mode) {
        if (key == null || amount <= 0 || !this.operational.getAsBoolean()) {
            return 0;
        }
        Pp2mpProviderFilterData rules = this.filter.get();
        if (!rules.allows(key)) {
            return 0;
        }
        MEStorage storage = this.target.findStorage(key.getType());
        if (storage == null) {
            return 0;
        }
        long current = 0;
        if (rules.targetAmount(key) > 0) {
            KeyCounter counter = new KeyCounter();
            storage.getAvailableStacks(counter);
            current = counter.get(key);
        }
        long requested = rules.amountToProvider(key, current, amount);
        long accepted = requested <= 0 ? 0
                : Math.clamp(storage.insert(key, requested, mode, this.source), 0, requested);
        if (mode == Actionable.MODULATE && accepted > 0) {
            this.inserted.run();
        }
        return accepted;
    }
}
