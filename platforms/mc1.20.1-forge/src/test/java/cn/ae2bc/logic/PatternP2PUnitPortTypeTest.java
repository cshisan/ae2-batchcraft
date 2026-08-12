package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PatternP2PUnitPortTypeTest {
    @Test
    void onlyEnergyPortsParticipateInExternalEnergyDistribution() {
        var accepted = Arrays.stream(PatternP2PUnitPortType.values())
                .filter(PatternP2PUnitPortType::acceptsExternalEnergy)
                .toList();

        assertEquals(java.util.List.of(PatternP2PUnitPortType.ENERGY), accepted);
    }

    @Test
    void extractionHasItsOwnPortType() {
        assertEquals("extract", PatternP2PUnitPortType.EXTRACT.getSerializedName());
    }
}
