package org.ae2bc.part.resource_intake;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import org.ae2bc.storage.InsertOnlyStorage;

/**
 * 资源接收端对外暴露的即时转发库存。它不保存资源，插入操作直接交给当前输出目标。
 */
@SuppressWarnings("UnstableApiUsage")
public final class Pp2mpResourceIntakeStorage extends InsertOnlyStorage {
    private final Pp2mpResourceIntakeLogic logic;

    public Pp2mpResourceIntakeStorage(Pp2mpResourceIntakeLogic logic) {
        this.logic = logic;
    }

    /* 对外部能力执行即时模拟或转发，不持有普通接收缓存。 */
    @Override
    public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
        if (mode == Actionable.SIMULATE) {
            return this.logic.simulateIntake(key, amount);
        }
        return this.logic.insertIntake(key, amount);
    }

    @Override
    public long getMaxAmount(AEKey key) {
        return this.logic.simulateIntake(key, Long.MAX_VALUE) > 0 ? Long.MAX_VALUE : 0;
    }

    @Override
    public long getCapacity(AEKeyType type) {
        return Long.MAX_VALUE;
    }

    @Override
    public boolean canInsert() {
        return this.logic.hasResourceTargets();
    }

    @Override
    public boolean isSupportedType(AEKeyType type) {
        return this.logic.supportsResourceType(type);
    }

    @Override
    public boolean isAllowedIn(int slot, AEKey key) {
        return key != null && this.logic.simulateIntake(key, 1) > 0;
    }

}
