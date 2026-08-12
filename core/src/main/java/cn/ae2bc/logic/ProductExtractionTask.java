package cn.ae2bc.logic;

/** A product extractor scheduled by its next due server tick. */
public interface ProductExtractionTask {
    boolean hasProductExtractionWork();

    int getProductExtractionInterval();

    ProductExtractionTickState tickProductExtraction();
}
