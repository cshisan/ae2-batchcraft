package cn.ae2bc.mixin;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import cn.ae2bc.Ae2bcMod;
import cn.ae2bc.extension.ImmediatePatternProviderReturnInventory;
import cn.ae2bc.logic.ReturnProgressListeners;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

@Mixin(PatternProviderReturnInventory.class)
public abstract class PatternProviderReturnInventoryMixin implements ImmediatePatternProviderReturnInventory {
    @Unique private @Nullable Runnable ae2bc$immediateFlushHandler;
    @Unique private @Nullable Runnable ae2bc$returnTickerWakeHandler;
    @Unique private final ReturnProgressListeners ae2bc$returnProgressListeners = new ReturnProgressListeners();

    @Override
    public void ae2bc$setImmediateFlushHandler(Runnable handler) {
        ae2bc$immediateFlushHandler = handler;
    }

    @Override
    public void ae2bc$setReturnTickerWakeHandler(Runnable handler) {
        ae2bc$returnTickerWakeHandler = handler;
    }

    @Override
    public void ae2bc$requestImmediateFlush(Runnable returnProgressListener) {
        if (((PatternProviderReturnInventory) (Object) this).isEmpty()) {
            return;
        }
        if (ae2bc$immediateFlushHandler != null) {
            ae2bc$immediateFlushHandler.run();
        }
        ae2bc$registerReturnProgressListener(returnProgressListener);
    }

    @Override
    public void ae2bc$registerReturnProgressListener(Runnable returnProgressListener) {
        ae2bc$returnProgressListeners.register(returnProgressListener,
                !((PatternProviderReturnInventory) (Object) this).isEmpty(),
                ae2bc$returnTickerWakeHandler == null ? () -> { } : ae2bc$returnTickerWakeHandler);
    }

    @Override
    public void ae2bc$unregisterReturnProgressListener(Runnable returnProgressListener) {
        ae2bc$returnProgressListeners.unregister(returnProgressListener);
    }

    @Inject(method = "injectIntoNetwork", at = @At("RETURN"))
    private void ae2bc$notifyReturnProgress(MEStorage storage, IActionSource source,
                                             Consumer<GenericStack> insertionCallback,
                                             CallbackInfoReturnable<Boolean> cir) {
        // Only wake producers here. They resume extraction on their own ticker, after AE2 finishes this inventory pass.
        for (Runnable listener : ae2bc$returnProgressListeners.takeAfterProgress(cir.getReturnValueZ())) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                Ae2bcMod.LOGGER.error("Failed to wake a Pattern P2P return producer", e);
            }
        }
    }
}
