package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnergyDistributionModeTest {
    @Test
    void defaultsUnknownIdsToEven() {
        assertEquals(EnergyDistributionMode.EVEN, EnergyDistributionMode.fromId(-1));
        assertEquals(EnergyDistributionMode.EVEN, EnergyDistributionMode.fromId(99));
    }

    @Test
    void cyclesBetweenEvenAndRoundRobin() {
        assertEquals(EnergyDistributionMode.ROUND_ROBIN, EnergyDistributionMode.EVEN.next());
        assertEquals(EnergyDistributionMode.EVEN, EnergyDistributionMode.ROUND_ROBIN.next());
    }
}
