package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PrioritizedEnergyDistributorTest {
    @Test
    void suppliesEveryReceiverInTheSameTickWhenEnergyIsAvailable() {
        int[] demand = {4, 3, 2};
        int[] received = new int[demand.length];

        int accepted = PrioritizedEnergyDistributor.distribute(9, demand.length,
                index -> demand[index], (index, amount) -> received[index] += amount);

        assertEquals(9, accepted);
        assertArrayEquals(demand, received);
    }

    @Test
    void earlierReceiverConsumesLimitedEnergyFirst() {
        int[] demand = {4, 3, 2};
        int[] received = new int[demand.length];

        PrioritizedEnergyDistributor.distribute(6, demand.length,
                index -> demand[index], (index, amount) -> received[index] += amount);

        assertArrayEquals(new int[]{4, 2, 0}, received);
    }

    @Test
    void doesNotBypassAnEarlierReceiverThatRejectsActualTransfer() {
        int[] demand = {4, 3};
        int[] received = new int[demand.length];

        int accepted = PrioritizedEnergyDistributor.distribute(7, demand.length,
                index -> demand[index], (index, amount) -> {
                    int actual = index == 0 ? 2 : amount;
                    received[index] += actual;
                    return actual;
                });

        assertEquals(2, accepted);
        assertArrayEquals(new int[]{2, 0}, received);
    }
}
