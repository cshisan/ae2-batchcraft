package org.ae2bc.storage;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.MEStorage;
import org.ae2bc.Ae2bcMod;
import org.ae2bc.integration.BulkPatternProviderReturnInventory;
import org.ae2bc.integration.ImmediatePatternProviderReturnInventory;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

/**
 * 输出端和返回端口共用的产物返回目标。
 * 本类不保存产物，插入操作委托给连接的通用 AE 库存
 * 或匹配的外部存储策略。
 */
@SuppressWarnings("UnstableApiUsage")
public final class ReceiveInventory extends InsertOnlyStorage {
    private final Supplier<@Nullable GenericInternalInventory> genericTargetSupplier;
    private final Supplier<Map<AEKeyType, ExternalStorageStrategy>> strategySupplier;
    private final Function<AEKey, Boolean> allowed;
    private final BiConsumer<AEKey, Long> afterInsert;
    private final IActionSource actionSource;
    private final Runnable contentsChanged;
    private final Runnable beforeBatch;
    private final Runnable afterBatch;
    private int deferredFlushDepth;
    private int batchDepth;
    private @Nullable GenericInternalInventory batchTarget;

    public ReceiveInventory(
            Supplier<@Nullable GenericInternalInventory> genericTargetSupplier,
            Supplier<Map<AEKeyType, ExternalStorageStrategy>> strategySupplier,
            Function<AEKey, Boolean> allowed,
            BiConsumer<AEKey, Long> afterInsert,
            IActionSource actionSource,
            Runnable contentsChanged
    ) {
        this(genericTargetSupplier, strategySupplier, allowed, afterInsert, actionSource, contentsChanged,
                () -> { }, () -> { });
    }

    public ReceiveInventory(
            Supplier<@Nullable GenericInternalInventory> genericTargetSupplier,
            Supplier<Map<AEKeyType, ExternalStorageStrategy>> strategySupplier,
            Function<AEKey, Boolean> allowed, BiConsumer<AEKey, Long> afterInsert,
            IActionSource actionSource, Runnable contentsChanged, Runnable beforeBatch, Runnable afterBatch
    ) {
        this.genericTargetSupplier = genericTargetSupplier;
        this.strategySupplier = strategySupplier;
        this.allowed = allowed;
        this.afterInsert = afterInsert;
        this.actionSource = actionSource;
        this.contentsChanged = contentsChanged;
        this.beforeBatch = beforeBatch;
        this.afterBatch = afterBatch;
    }

    private @Nullable MEStorage findTarget(AEKey key) {
        Map<AEKeyType, ExternalStorageStrategy> strategies = this.strategySupplier.get();
        if (strategies == null) {
            return null;
        }

        ExternalStorageStrategy strategy = strategies.get(key.getType());
        return strategy == null ? null : strategy.createWrapper(false, this.contentsChanged);
    }

    private @Nullable GenericInternalInventory findGenericTarget() {
        GenericInternalInventory target = this.batchDepth > 0 ? this.batchTarget : this.genericTargetSupplier.get();
        return target == this ? null : target;
    }

    private long insertGenericTarget(GenericInternalInventory target, AEKey key, long amount, Actionable mode) {
        long inserted = 0;
        for (int slot = 0; slot < target.size() && inserted < amount; slot++) {
            if (!target.isAllowedIn(slot, key)) {
                continue;
            }
            inserted += target.insert(slot, key, amount - inserted, mode);
        }
        return Math.clamp(inserted, 0, amount);
    }

    private long insertInternal(AEKey key, long amount, Actionable mode, @Nullable IActionSource source) {
        return this.insertInternal(key, amount, mode, source, true);
    }

    /* 正常产物执行过滤和完成记账；取消任务的原材料仅退回原接收库存。 */
    private long insertInternal(AEKey key, long amount, Actionable mode, @Nullable IActionSource source, boolean trackOutput) {
        return this.insertInternal(key, amount, mode, source, trackOutput, false);
    }

    /* 主动提取已扣除来源实物；只在持有任务返回范围时允许绕过供应器缓存。 */
    public long insertExtracted(AEKey key, long amount, Actionable mode) {
        if (this.batchDepth <= 0) {
            throw new IllegalStateException("Bulk extraction requires an active return scope");
        }
        return this.insertInternal(key, amount, mode, null, true, true);
    }

    private long insertInternal(
            AEKey key, long amount, Actionable mode, @Nullable IActionSource source,
            boolean trackOutput, boolean bulkReturn
    ) {
        boolean allowed = key != null && amount > 0 && (!trackOutput || this.allowed.apply(key));
        if (Ae2bcMod.LOGGER.isDebugEnabled()) {
            Ae2bcMod.LOGGER.debug(
                    "[P2MP Return] request key={} amount={} mode={} allowed={}",
                    key,
                    amount,
                    mode,
                    allowed
            );
        }
        if (!allowed) {
            if (Ae2bcMod.LOGGER.isDebugEnabled()) {
                Ae2bcMod.LOGGER.debug("[P2MP Return] rejected before target lookup key={} amount={}", key, amount);
            }
            return 0;
        }

        IActionSource effectiveSource = source == null ? this.actionSource : source;
        GenericInternalInventory genericTarget = this.findGenericTarget();
        boolean supported = genericTarget != null && genericTarget.isSupportedType(key.getType());
        if (Ae2bcMod.LOGGER.isDebugEnabled()) {
            Ae2bcMod.LOGGER.debug(
                    "[P2MP Return] target generic={} supported={} keyType={}",
                    genericTarget == null ? "null" : genericTarget.getClass().getName(),
                    supported,
                    key.getType()
            );
        }
        long inserted;
        if (genericTarget != null && supported) {
            this.requestImmediateFlush(genericTarget, mode);
            inserted = bulkReturn && genericTarget.canInsert()
                    && genericTarget instanceof BulkPatternProviderReturnInventory bulk
                    ? Math.clamp(bulk.ae2bc$insertBulk(key, amount, mode), 0, amount) : 0;
            if (inserted < amount) {
                inserted += this.insertGenericTarget(genericTarget, key, amount - inserted, mode);
            }
        } else {
            MEStorage target = this.findTarget(key);
            if (target == null) {
                if (Ae2bcMod.LOGGER.isDebugEnabled()) {
                    Ae2bcMod.LOGGER.debug("[P2MP Return] no external target key={} keyType={}", key, key.getType());
                }
                return 0;
            }
            inserted = target.insert(key, amount, mode, effectiveSource);
        }
        inserted = Math.clamp(inserted, 0, amount);

        if (Ae2bcMod.LOGGER.isDebugEnabled()) {
            Ae2bcMod.LOGGER.debug(
                    "[P2MP Return] inserted key={} requested={} inserted={} mode={}",
                    key,
                    amount,
                    inserted,
                    mode
            );
        }

        if (mode == Actionable.MODULATE && inserted > 0) {
            if (trackOutput) {
                this.deferredFlushDepth++;
                try {
                    this.afterInsert.accept(key, inserted);
                } finally {
                    this.deferredFlushDepth--;
                }
            }
            this.requestImmediateFlush(genericTarget, mode);
        }

        return inserted;
    }

    private void requestImmediateFlush(
            @Nullable GenericInternalInventory target,
            Actionable mode
    ) {
        if (mode != Actionable.MODULATE || this.deferredFlushDepth > 0
                || !(target instanceof ImmediatePatternProviderReturnInventory immediate)) {
            return;
        }
        immediate.ae2bc$requestImmediateFlush();
    }

    @Override
    public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
        if (mode == Actionable.SIMULATE) {
            return this.insertInternal(key, amount, mode, source);
        }
        this.beginBatch();
        try {
            return this.insertInternal(key, amount, mode, source);
        } finally {
            this.endBatch();
        }
    }

    /* 一次返回操作共用目标，来源结算完成前禁止任务切换和立即刷新。 */
    @Override
    public void beginBatch() {
        if (this.batchDepth == 0) {
            this.beforeBatch.run();
            try {
                this.batchTarget = this.genericTargetSupplier.get();
            } catch (RuntimeException | Error failure) {
                try {
                    this.afterBatch.run();
                } catch (RuntimeException | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }
        this.batchDepth++;
        this.deferredFlushDepth++;
    }

    @Override
    public void endBatch() {
        if (this.batchDepth <= 0) {
            throw new IllegalStateException("Unbalanced inventory return scope");
        }
        if (this.batchDepth > 1) {
            this.batchDepth--;
            this.deferredFlushDepth--;
            return;
        }
        try {
            this.afterBatch.run();
        } finally {
            this.batchDepth = 0;
            this.deferredFlushDepth--;
            GenericInternalInventory target = this.batchTarget;
            this.batchTarget = null;
            this.requestImmediateFlush(target, Actionable.MODULATE);
        }
    }

    /* 仅供来源已扣账的提取边界调用；保留任务范围，只释放返回库存容量。 */
    public void flushSettled() {
        GenericInternalInventory target = this.findGenericTarget();
        if (target instanceof ImmediatePatternProviderReturnInventory immediate) {
            immediate.ae2bc$requestImmediateFlush();
        }
    }

    @Override
    public void endBatchSuppressed() {
        this.endBatch();
    }

    @Override
    public long getMaxAmount(AEKey key) {
        GenericInternalInventory genericTarget = this.findGenericTarget();
        if (genericTarget != null && genericTarget.isSupportedType(key.getType())) {
            return genericTarget.getMaxAmount(key);
        }
        return this.isSupportedType(key.getType()) && this.allowed.apply(key)
                ? Long.MAX_VALUE
                : 0;
    }

    @Override
    public long getCapacity(AEKeyType type) {
        GenericInternalInventory genericTarget = this.findGenericTarget();
        if (genericTarget != null && genericTarget.isSupportedType(type)) {
            return genericTarget.getCapacity(type);
        }
        return this.isSupportedType(type) ? Long.MAX_VALUE : 0;
    }

    @Override
    public boolean canInsert() {
        GenericInternalInventory genericTarget = this.findGenericTarget();
        if (genericTarget != null && genericTarget.canInsert()) {
            return true;
        }
        Map<AEKeyType, ExternalStorageStrategy> strategies = this.strategySupplier.get();
        return strategies != null && !strategies.isEmpty();
    }

    @Override
    public boolean isSupportedType(AEKeyType type) {
        GenericInternalInventory genericTarget = this.findGenericTarget();
        if (genericTarget != null && genericTarget.isSupportedType(type)) {
            return true;
        }
        Map<AEKeyType, ExternalStorageStrategy> strategies = this.strategySupplier.get();
        return strategies != null && strategies.containsKey(type);
    }

    @Override
    public boolean isAllowedIn(int slot, AEKey key) {
        return key != null
                && this.isSupportedType(key.getType())
                && this.allowed.apply(key);
    }

    /* 重试已提取的产物，按实际返回量扣减恢复队列。 */
    public boolean drainRecovery(Map<AEKey, Long> recovery) {
        return this.drainRecovery(recovery, () -> { });
    }

    public boolean drainRecovery(Map<AEKey, Long> recovery, Runnable markForSave) {
        return this.drainRecovery(recovery, markForSave, true);
    }

    private boolean drainRecovery(Map<AEKey, Long> recovery, Runnable markForSave, boolean trackOutput) {
        if (recovery.isEmpty()) {
            return false;
        }
        boolean changed = false;
        this.beginBatch();
        try {
            Iterator<Map.Entry<AEKey, Long>> entries = recovery.entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry<AEKey, Long> entry = entries.next();
                long inserted = this.insertInternal(
                        entry.getKey(), entry.getValue(), Actionable.MODULATE, null, trackOutput);
                if (inserted <= 0) {
                    continue;
                }
                changed = true;
                long remaining = entry.getValue() - inserted;
                if (remaining <= 0) {
                    entries.remove();
                } else {
                    entry.setValue(remaining);
                }
                markForSave.run();
            }
        } finally {
            this.endBatch();
        }
        return changed;
    }

    /* 退回取消任务的原料；扣账与保存必须先于供应器立即回传。 */
    public boolean drainUntracked(Map<AEKey, Long> recovery, Runnable markForSave) {
        return this.drainRecovery(recovery, markForSave, false);
    }

    /* 退回端点已持有的原材料，不能把原材料误计为本轮合成产物。 */
    public long insertUntracked(AEKey key, long amount) {
        return this.insertInternal(key, amount, Actionable.MODULATE, null, false);
    }

    /* 来源库存完成实物扣账和余量恢复后，才允许供应器立即回传网络。 */
    public void insertUntracked(AEKey key, long amount, LongConsumer settleInsertion) {
        long inserted;
        this.deferredFlushDepth++;
        try {
            inserted = this.insertInternal(key, amount, Actionable.MODULATE, null, false);
            settleInsertion.accept(inserted);
        } finally {
            this.deferredFlushDepth--;
        }
        this.requestImmediateFlush(this.findGenericTarget(), Actionable.MODULATE);
    }
}
