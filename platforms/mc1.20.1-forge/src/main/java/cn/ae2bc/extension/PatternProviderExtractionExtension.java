package cn.ae2bc.extension;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.helpers.externalstorage.GenericStackInv;
import cn.ae2bc.logic.ProductExtractionSettings;
import cn.ae2bc.logic.ProductExtractionTask;
import cn.ae2bc.logic.ProductExtractionTickState;

public interface PatternProviderExtractionExtension extends ProductExtractionTask {
    IUpgradeInventory ae2bc$getProductExtractionUpgrades();

    GenericStackInv ae2bc$getProductExtractionMarkers();

    ProductExtractionSettings ae2bc$getProductExtractionSettings();

    void ae2bc$setProductExtractionInterval(int interval);

    void ae2bc$setProductExtractionAmount(int amount);

    void ae2bc$setProductExtractionWhitelist(boolean whitelist);

    boolean ae2bc$hasProductExtractionCard();

    boolean ae2bc$hasProductExtractionWork();

    ProductExtractionTickState ae2bc$tickProductExtraction();

    @Override
    default boolean hasProductExtractionWork() {
        return ae2bc$hasProductExtractionWork();
    }

    @Override
    default int getProductExtractionInterval() {
        return ae2bc$getProductExtractionSettings().interval();
    }

    @Override
    default ProductExtractionTickState tickProductExtraction() {
        return ae2bc$tickProductExtraction();
    }
}
