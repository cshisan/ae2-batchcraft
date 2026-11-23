package org.ae2bc.part.general;

import appeng.api.stacks.AEKeyType;
import appeng.api.storage.MEStorage;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public final class Pp2mpResourceIntakeTarget {
    private final Pp2mpAdjacentTarget adjacent;
    private final Pp2mpStorageTarget storageTarget;
    private final Runnable changed;
    private @Nullable Pp2mpAdjacentTarget.Target snapshot;
    private long generation;

    public Pp2mpResourceIntakeTarget(
            Pp2mpAdjacentTarget adjacent, Runnable contentsChanged, Runnable changed
    ) {
        this.adjacent = adjacent;
        this.changed = changed;
        this.storageTarget = new Pp2mpStorageTarget(adjacent, contentsChanged, this::onCapabilityChanged, true);
    }

    public Map<AEKeyType, MEStorage> findSources() {
        return this.storageTarget.findStorages();
    }

    public @Nullable MEStorage findSource(AEKeyType type) {
        return this.storageTarget.findStorage(type);
    }

    /* 地址变化和能力失效都会终止当前轮对旧来源的访问。 */
    public long generation() {
        Pp2mpAdjacentTarget.Target target = this.adjacent.resolve();
        if (this.snapshot != target) {
            this.snapshot = target;
            this.generation++;
        }
        return target == null ? Long.MIN_VALUE : this.generation;
    }

    /* 能力失效使当前提取循环放弃旧包装器，普通收发不改变目标版本。 */
    private void onCapabilityChanged() {
        this.generation++;
        this.changed.run();
    }

    public void clear() {
        this.storageTarget.clear();
        this.adjacent.clear();
        this.snapshot = null;
        this.generation++;
    }
}
