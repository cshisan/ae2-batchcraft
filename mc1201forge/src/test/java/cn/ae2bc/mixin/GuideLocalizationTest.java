package cn.ae2bc.mixin;

import cn.ae2bc.guide.GuideLocalization;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideLocalizationTest {
    @Test
    void recognizesChineseLanguageVariants() {
        assertTrue(GuideLocalization.isChinese("zh_cn"));
        assertTrue(GuideLocalization.isChinese("ZH-CN"));
        assertTrue(GuideLocalization.isChinese("zh"));
        assertFalse(GuideLocalization.isChinese("en_us"));
    }

    @Test
    void keepsChineseRegionWhenAResourceExistsAndNormalizesSeparators() {
        assertEquals("ae2guide/_zh_cn/index.md", GuideLocalization.localizedPath("ZH-CN"));
        assertEquals("ae2guide/_zh_tw/index.md", GuideLocalization.localizedPath("zh-TW"));
        assertEquals("ae2guide/_zh_cn/index.md", GuideLocalization.localizedPath(""));
        assertEquals("ae2guide/_zh_cn/index.md",
                GuideLocalization.localizedPath("zh_cn", "index"));
        assertEquals("ae2guide/_zh_cn/unit/extraction-port.md",
                GuideLocalization.localizedPath("ZH-CN", "unit/extraction-port.md"));
        assertEquals("ae2guide/_zh_cn/unit/extraction-port.md",
                GuideLocalization.localizedPath("zh_cn", "unit\\extraction-port.md"));
        assertEquals("ae2guide/_zh_cn/index.md",
                GuideLocalization.localizedPath("zh_cn", "../outside.md"));
        assertEquals("ae2guide/_zh_cn/index.md",
                GuideLocalization.localizedPath("zh_cn", "/unit/extraction-port.md"));
    }
}
