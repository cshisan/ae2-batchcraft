package org.ae2bc.part.general;

import appeng.api.AECapabilities;
import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.storage.MEStorage;
import appeng.parts.automation.StackWorldBehaviors;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

@SuppressWarnings("UnstableApiUsage")
public final class Pp2mpStorageTarget {
    private final Pp2mpAdjacentTarget adjacentTarget;
    private final Runnable contentsChanged;
    private final Runnable capabilityChanged;
    private final boolean extractableOnly;
    private @Nullable BlockCapabilityCache<MEStorage, Direction> storageCache;
    private @Nullable Map<AEKeyType, ExternalStorageStrategy> strategies;
    private long cachedGeneration = Long.MIN_VALUE;
    private long cacheVersion;

    public Pp2mpStorageTarget(
            Pp2mpAdjacentTarget adjacentTarget, Runnable contentsChanged,
            Runnable capabilityChanged, boolean extractableOnly
    ) {
        this.adjacentTarget = adjacentTarget;
        this.contentsChanged = contentsChanged;
        this.capabilityChanged = capabilityChanged;
        this.extractableOnly = extractableOnly;
    }

    /* 共享能力缓存与策略解析，每次操作重新获取包装器，不缓存库存数量。 */
    public Map<AEKeyType, MEStorage> findStorages() {
        Pp2mpAdjacentTarget.Target target = this.adjacentTarget.resolve();
        if (target == null) {
            this.clear();
            return Map.of();
        }
        this.refreshCaches(target);

        Map<AEKeyType, MEStorage> result = new IdentityHashMap<>();
        MEStorage direct = this.storageCache == null ? null : this.storageCache.getCapability();
        if (direct != null) {
            for (AEKeyType type : AEKeyTypes.getAll()) {
                result.put(type, direct);
            }
        } else if (this.strategies != null) {
            for (Map.Entry<AEKeyType, ExternalStorageStrategy> entry : this.strategies.entrySet()) {
                MEStorage wrapper = entry.getValue().createWrapper(this.extractableOnly, this.contentsChanged);
                if (wrapper != null) {
                    result.put(entry.getKey(), wrapper);
                }
            }
        }
        return result;
    }

    /* 只解析请求类型，避免单资源供应操作创建所有类型的包装器。 */
    public @Nullable MEStorage findStorage(AEKeyType type) {
        Pp2mpAdjacentTarget.Target target = this.adjacentTarget.resolve();
        if (target == null) {
            this.clear();
            return null;
        }
        this.refreshCaches(target);
        MEStorage direct = this.storageCache == null ? null : this.storageCache.getCapability();
        if (direct != null) {
            return direct;
        }
        ExternalStorageStrategy strategy = this.strategies == null ? null : this.strategies.get(type);
        return strategy == null ? null : strategy.createWrapper(this.extractableOnly, this.contentsChanged);
    }

    /* 相同目标复用能力监听器；失效通知和收发回调仅唤醒，不销毁缓存。 */
    private void refreshCaches(Pp2mpAdjacentTarget.Target target) {
        if (this.storageCache == null || this.cachedGeneration != target.generation()) {
            long version = ++this.cacheVersion;
            this.storageCache = target.withLevel(level -> BlockCapabilityCache.create(
                    AECapabilities.ME_STORAGE, level, target.pos(),
                    target.side(),
                    () -> this.cacheVersion == version && this.adjacentTarget.isCurrent(target),
                    this.capabilityChanged
            ));
            this.strategies = target.withLevel(level -> StackWorldBehaviors.createExternalStorageStrategies(
                    level, target.pos(), target.side()
            ));
            this.cachedGeneration = target.generation();
        }
    }

    public void clear() {
        this.cacheVersion++;
        this.storageCache = null;
        this.strategies = null;
        this.cachedGeneration = Long.MIN_VALUE;
    }
}
