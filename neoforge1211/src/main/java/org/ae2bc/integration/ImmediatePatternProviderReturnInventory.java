package org.ae2bc.integration;

/**
 * 请求 AE2 样板供应器立即刷新返回库存的桥接接口。
 */
public interface ImmediatePatternProviderReturnInventory {
    void ae2bc$setImmediateFlushHandler(Runnable handler);

    void ae2bc$requestImmediateFlush();
}
