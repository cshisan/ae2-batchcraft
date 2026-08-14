package cn.ae2bc.core.frequency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class FrequencyLimitsTest {
    @Test void clampsToUnsignedShortRange() {
        assertEquals(0, FrequencyLimits.clamp(-1));
        assertEquals(1234, FrequencyLimits.clamp(1234));
        assertEquals(65535, FrequencyLimits.clamp(70000));
    }
}
