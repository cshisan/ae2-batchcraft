package cn.ae2bc.extension;

import cn.ae2bc.pattern.MaterialOutputConfigData;

public interface PatternEncodingLogicExtension {
    MaterialOutputConfigData ae2bc$getMaterialOutputConfig();

    void ae2bc$setMaterialOutputConfig(MaterialOutputConfigData config);

    long ae2bc$getPatternBatchCount();

    void ae2bc$setPatternBatchCount(long batchCount);
}
