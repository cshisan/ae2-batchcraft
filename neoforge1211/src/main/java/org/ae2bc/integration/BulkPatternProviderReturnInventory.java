package org.ae2bc.integration;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;

/* 仅供已完成来源扣账的主动提取批量回网，普通库存插入仍使用原缓存。 */
public interface BulkPatternProviderReturnInventory {
    void ae2bc$setBulkReturnHandler(Handler handler);

    long ae2bc$insertBulk(AEKey key, long amount, Actionable mode);

    @FunctionalInterface
    interface Handler {
        long insert(AEKey key, long amount, Actionable mode);
    }
}
