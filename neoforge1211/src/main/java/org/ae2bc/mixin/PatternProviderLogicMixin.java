package org.ae2bc.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import appeng.helpers.patternprovider.UnlockCraftingEvent;
import org.ae2bc.integration.BulkPatternProviderReturnInventory;
import org.ae2bc.integration.ImmediatePatternProviderReturnInventory;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(PatternProviderLogic.class)
public abstract class PatternProviderLogicMixin {
    @Shadow
    @Final
    private PatternProviderLogicHost host;

    @Shadow
    @Final
    private IManagedGridNode mainNode;

    @Shadow
    @Final
    private IActionSource actionSource;

    @Shadow
    @Final
    private PatternProviderReturnInventory returnInv;

    @Shadow
    private @Nullable UnlockCraftingEvent unlockEvent;

    @Shadow
    private @Nullable GenericStack unlockStack;

    @Unique
    private boolean ae2bc$flushingReturnInventory;

    @Inject(
            method = "<init>(Lappeng/api/networking/IManagedGridNode;"
                    + "Lappeng/helpers/patternprovider/PatternProviderLogicHost;I)V",
            at = @At("RETURN")
    )
    private void ae2bc$installImmediateFlushHandler(
            IManagedGridNode mainNode,
            PatternProviderLogicHost host,
            int patternInventorySize,
            CallbackInfo ci
    ) {
        ((ImmediatePatternProviderReturnInventory) this.returnInv)
                .ae2bc$setImmediateFlushHandler(this::ae2bc$flushReturnInventoryImmediately);
        ((BulkPatternProviderReturnInventory) this.returnInv)
                .ae2bc$setBulkReturnHandler(this::ae2bc$insertBulkReturn);
    }

    /* 使用供应器自身网络与操作来源，按实际回网量保留原版解锁回调。 */
    @Unique
    private long ae2bc$insertBulkReturn(AEKey key, long amount, Actionable mode) {
        IGrid grid = this.mainNode.getGrid();
        if (this.ae2bc$flushingReturnInventory || !this.mainNode.isActive() || grid == null) {
            return 0;
        }
        this.ae2bc$flushingReturnInventory = true;
        try {
            long inserted = Math.clamp(
                    grid.getStorageService().getInventory().insert(key, amount, mode, this.actionSource), 0, amount);
            if (mode == Actionable.MODULATE && inserted > 0) {
                UnlockCraftingEvent previousEvent = this.unlockEvent;
                GenericStack previousStack = this.unlockStack;
                ((PatternProviderLogicAccessor) this)
                        .ae2bc$onStackReturnedToNetwork(new GenericStack(key, inserted));
                if (previousEvent != this.unlockEvent || !Objects.equals(previousStack, this.unlockStack)) {
                    this.host.saveChanges();
                }
            }
            return inserted;
        } finally {
            this.ae2bc$flushingReturnInventory = false;
        }
    }

    @Unique
    private void ae2bc$flushReturnInventoryImmediately() {
        if (this.ae2bc$flushingReturnInventory || !this.mainNode.isActive()) {
            return;
        }
        @Nullable IGrid grid = this.mainNode.getGrid();
        if (grid == null) {
            return;
        }

        this.ae2bc$flushingReturnInventory = true;
        try {
            PatternProviderLogicAccessor accessor = (PatternProviderLogicAccessor) this;
            this.returnInv.injectIntoNetwork(
                    grid.getStorageService().getInventory(),
                    this.actionSource,
                    accessor::ae2bc$onStackReturnedToNetwork
            );
        } finally {
            this.ae2bc$flushingReturnInventory = false;
        }
    }
}
