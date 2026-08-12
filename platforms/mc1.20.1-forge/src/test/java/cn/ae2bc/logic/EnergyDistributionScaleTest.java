package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class EnergyDistributionScaleTest {
    private static final int RECEIVER_COUNT = 4096;
    private static final int ITERATIONS = 100;

    @Test
    void evenDistributionRemainsLinearAndConservesEnergyAtScale() {
        assertTimeout(Duration.ofSeconds(2), () -> {
            for (int iteration = 0; iteration < ITERATIONS; iteration++) {
                int[] received = new int[RECEIVER_COUNT];
                int offered = RECEIVER_COUNT * 7 + 31;
                int accepted = FairEnergyDistributor.distribute(
                        offered, RECEIVER_COUNT, iteration, (index, amount) -> received[index] += amount);

                assertEquals(offered, accepted);
                assertEquals(offered, Arrays.stream(received).sum());
                int min = Arrays.stream(received).min().orElseThrow();
                int max = Arrays.stream(received).max().orElseThrow();
                assertEquals(1, max - min);
            }
        });
    }

    @Test
    void prioritizedDistributionRemainsLinearAndConservesEnergyAtScale() {
        assertTimeout(Duration.ofSeconds(2), () -> {
            int[] demand = new int[RECEIVER_COUNT];
            Arrays.fill(demand, 8);
            for (int iteration = 0; iteration < ITERATIONS; iteration++) {
                int[] received = new int[RECEIVER_COUNT];
                int offered = RECEIVER_COUNT * 8;
                int accepted = PrioritizedEnergyDistributor.distribute(
                        offered, RECEIVER_COUNT, index -> demand[index],
                        (index, amount) -> received[index] += amount);

                assertEquals(offered, accepted);
                assertEquals(offered, Arrays.stream(received).sum());
            }
        });
    }
}
