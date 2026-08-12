package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PatternP2PUnitIdentityColorsTest {
    @Test
    void mapsEachPatternP2PUnitIdToAStableNonZeroColorCode() {
        UUID first = UUID.fromString("6f0e3ba0-2aac-4665-b269-1bbf32636d2b");
        UUID second = UUID.fromString("f866bb68-5eec-4cf9-81a0-5907fd6f15c5");

        assertEquals(PatternP2PUnitIdentityColors.encode(first), PatternP2PUnitIdentityColors.encode(first));
        assertNotEquals(0, PatternP2PUnitIdentityColors.encode(first));
        assertNotEquals(PatternP2PUnitIdentityColors.encode(first), PatternP2PUnitIdentityColors.encode(second));
    }
}
