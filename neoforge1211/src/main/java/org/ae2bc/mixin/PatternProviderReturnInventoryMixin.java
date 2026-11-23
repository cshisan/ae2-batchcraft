package org.ae2bc.mixin;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import org.ae2bc.integration.BulkPatternProviderReturnInventory;
import org.ae2bc.integration.ImmediatePatternProviderReturnInventory;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PatternProviderReturnInventory.class)
public abstract class PatternProviderReturnInventoryMixin implements ImmediatePatternProviderReturnInventory, BulkPatternProviderReturnInventory {
    @Shadow
    private boolean injectingIntoNetwork;

    @Unique
    private @Nullable Runnable ae2bc$immediateFlushHandler;

    @Unique
    private @Nullable BulkPatternProviderReturnInventory.Handler ae2bc$bulkReturnHandler;

    @Override
    public void ae2bc$setBulkReturnHandler(BulkPatternProviderReturnInventory.Handler handler) {
        this.ae2bc$bulkReturnHandler = handler;
    }

    /* 模拟和实际回网均禁止重新注入本供应器，防止存储总线形成递归返回。 */
    @Override
    public long ae2bc$insertBulk(AEKey key, long amount, Actionable mode) {
        if (this.injectingIntoNetwork || this.ae2bc$bulkReturnHandler == null || amount <= 0) {
            return 0;
        }
        this.injectingIntoNetwork = true;
        try {
            return Math.clamp(this.ae2bc$bulkReturnHandler.insert(key, amount, mode), 0, amount);
        } finally {
            this.injectingIntoNetwork = false;
        }
    }

    @Override
    public void ae2bc$setImmediateFlushHandler(Runnable handler) {
        this.ae2bc$immediateFlushHandler = handler;
    }

    @Override
    public void ae2bc$requestImmediateFlush() {
        PatternProviderReturnInventory inventory = (PatternProviderReturnInventory) (Object) this;
        if (inventory.isEmpty() || this.ae2bc$immediateFlushHandler == null) {
            return;
        }
        this.ae2bc$immediateFlushHandler.run();
    }
}
