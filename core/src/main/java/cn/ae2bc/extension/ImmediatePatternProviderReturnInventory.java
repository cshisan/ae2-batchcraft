package cn.ae2bc.extension;

/** Internal bridge from a remote return inventory to its owning pattern provider. */
public interface ImmediatePatternProviderReturnInventory {
    void ae2bc$setImmediateFlushHandler(Runnable handler);

    void ae2bc$setReturnTickerWakeHandler(Runnable handler);

    void ae2bc$requestImmediateFlush(Runnable returnProgressListener);

    void ae2bc$registerReturnProgressListener(Runnable returnProgressListener);

    void ae2bc$unregisterReturnProgressListener(Runnable returnProgressListener);
}
