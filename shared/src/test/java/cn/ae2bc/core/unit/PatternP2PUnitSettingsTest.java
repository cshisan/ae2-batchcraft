package cn.ae2bc.core.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.ae2bc.core.extraction.ProductExtractionLimits;
import cn.ae2bc.logic.RedstoneOutputMode;
import cn.ae2bc.logic.ReturnMode;
import org.junit.jupiter.api.Test;

class PatternP2PUnitSettingsTest {
    @Test
    void clampsAllNumericSettingsAndSuppliesEnumDefaults() {
        PatternP2PUnitSettings settings = new PatternP2PUnitSettings(
                null, false, 0, Integer.MAX_VALUE, null, 99, 40, 20);

        assertEquals(ReturnMode.UNBLOCKED, settings.getReturnMode());
        assertEquals(RedstoneOutputMode.SINGLE_TRIGGER, settings.getRedstoneMode());
        assertEquals(ProductExtractionLimits.MIN_INTERVAL, settings.getExtractionInterval());
        assertEquals(ProductExtractionLimits.MAX_AMOUNT, settings.getExtractionAmount());
        assertEquals(15, settings.getRedstoneStrength());
        assertEquals(20, settings.getPulseWidthTicks());
        assertEquals(20, settings.getPulsePeriodTicks());
    }

    @Test
    void defaultsEnableDirectBreakRecovery() {
        assertTrue(PatternP2PUnitSettings.DEFAULT.isBreakRecovery());
        assertEquals(2, PatternP2PUnitSettings.DEFAULT.getPulseWidthTicks());
        assertEquals(20, PatternP2PUnitSettings.DEFAULT.getPulsePeriodTicks());
    }
}
