package org.ae2bc.part.general;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.storage.MEStorage;
import appeng.me.storage.ExternalStorageFacade;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.IdentityHashMap;
import java.util.Map;

public final class Pp2mpSendTarget {
    private final Pp2mpStorageTarget storageTarget;
    private final Pp2mpAdjacentTarget adjacentTarget;

    public Pp2mpSendTarget(Pp2mpAdjacentTarget adjacentTarget, Runnable changed) {
        this.storageTarget = new Pp2mpStorageTarget(adjacentTarget, changed, changed, false);
        this.adjacentTarget = adjacentTarget;
    }

    /* 使用本次操作已解析的目标，调用方依据实际插入量扣减持有的资源。 */
    public static long insert(
            Map<AEKeyType, MEStorage> storages, AEKey key, long amount, Actionable mode, IActionSource source
    ) {
        if (key == null || amount <= 0) {
            return 0;
        }
        MEStorage storage = storages.get(key.getType());
        return storage == null ? 0 : Math.clamp(storage.insert(key, amount, mode, source), 0, amount);
    }

    /* 发送已接收的材料，只扣减实际插入量；后续失败不会撤销已完成的记账。 */
    public static boolean drainPending(
            Map<AEKey, Long> pending, Map<AEKeyType, MEStorage> storages, IActionSource source, Runnable markForSave
    ) {
        boolean changed = false;
        try {
            Iterator<Map.Entry<AEKey, Long>> entries = pending.entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry<AEKey, Long> entry = entries.next();
                long inserted = Pp2mpSendTarget.insert(
                        storages, entry.getKey(), entry.getValue(), Actionable.MODULATE, source
                );
                if (inserted <= 0) {
                    continue;
                }
                changed = true;
                long remaining = entry.getValue() - inserted;
                if (remaining == 0) {
                    entries.remove();
                } else {
                    entry.setValue(remaining);
                }
            }
        } finally {
            if (changed) {
                markForSave.run();
            }
        }
        return changed;
    }

    public Map<AEKeyType, MEStorage> findStorages() {
        return this.storageTarget.findStorages();
    }

    /* 一次投递只解析实际材料类型，缺失类型也只查询一次。 */
    public Map<AEKeyType, MEStorage> findStorages(Iterable<AEKey> keys) {
        Map<AEKeyType, MEStorage> result = new IdentityHashMap<>(2);
        for (AEKey key : keys) {
            AEKeyType type = key.getType();
            if (!result.containsKey(type)) {
                result.put(type, this.findStorage(type));
            }
        }
        return result;
    }

    /* 存在性检查找到首个可用库存即返回，不构造全类型映射。 */
    public boolean hasStorage() {
        for (AEKeyType type : AEKeyTypes.getAll()) {
            if (this.findStorage(type) != null) {
                return true;
            }
        }
        return false;
    }

    public @Nullable MEStorage findStorage(AEKeyType type) {
        return this.storageTarget.findStorage(type);
    }

    public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
        if (key == null || amount <= 0) {
            return 0;
        }
        MEStorage storage = this.findStorage(key.getType());
        return storage == null ? 0 : Math.clamp(storage.insert(key, amount, mode, source), 0, amount);
    }

    /* 共享本次接单的容量计划及同类型库存，实际插入仍需结算差额。 */
    public boolean canInsertAll(Map<AEKey, Long> requestedInputs, IActionSource source, Pp2mpInsertPlan plan) {
        Map<AEKeyType, MEStorage> storages = requestedInputs.size() > 1 ? new IdentityHashMap<>(2) : null;
        for (Map.Entry<AEKey, Long> entry : requestedInputs.entrySet()) {
            long amount = entry.getValue();
            if (amount <= 0) {
                return false;
            }
            AEKeyType type = entry.getKey().getType();
            MEStorage storage = storages == null ? this.findStorage(type)
                    : storages.computeIfAbsent(type, this::findStorage);
            if (this.reserve(plan, entry.getKey(), amount, source, storage) < amount) {
                return false;
            }
        }
        return true;
    }

    /* 端口单次投递保留原生 AE 包装器的 int 数量上限。 */
    public long reserve(Pp2mpInsertPlan plan, AEKey key, long amount, IActionSource source) {
        MEStorage storage = this.findStorage(key.getType());
        if (storage == null || amount <= 0) {
            return 0;
        }
        // 流体及自定义存储可能按请求量限制整次接收，仍保留独立模拟。
        long requested = storage instanceof ExternalStorageFacade && key instanceof AEItemKey
                ? Math.min(Integer.MAX_VALUE, amount)
                : Math.clamp(storage.insert(key, amount, Actionable.SIMULATE, source), 0, amount);
        return this.reserve(plan, key, requested, source, storage);
    }

    /* 原生槽位共享模拟占用；自定义 AE 存储保留其自身插入语义。 */
    private long reserve(
            Pp2mpInsertPlan plan, AEKey key, long amount, IActionSource source, @Nullable MEStorage storage
    ) {
        if (storage == null || amount <= 0) {
            return 0;
        }
        Pp2mpAdjacentTarget.Target target = this.adjacentTarget.resolve();
        if (storage instanceof ExternalStorageFacade && target != null) {
            if (key instanceof AEItemKey itemKey) {
                IItemHandler handler = target.withLevel(level ->
                        level.getCapability(Capabilities.ItemHandler.BLOCK, target.pos(), target.side()));
                if (handler != null) {
                    return plan.reserve(handler, itemKey, amount);
                }
            } else if (key instanceof AEFluidKey fluidKey) {
                IFluidHandler handler = target.withLevel(level ->
                        level.getCapability(Capabilities.FluidHandler.BLOCK, target.pos(), target.side()));
                if (handler != null) {
                    return plan.reserve(handler, fluidKey, amount);
                }
            }
        }
        return plan.reserve(storage, key, amount, source);
    }

    public void clear() {
        this.storageTarget.clear();
    }
}
