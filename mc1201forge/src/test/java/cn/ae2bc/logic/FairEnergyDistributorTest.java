package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FairEnergyDistributorTest {
    @Test
    void splitsEnergyEvenly() {
        int[] received = new int[3];

        int accepted = FairEnergyDistributor.distribute(10, received.length, 0,
                (index, amount) -> received[index] += amount);

        assertEquals(10, accepted);
        assertArrayEquals(new int[]{4, 3, 3}, received);
    }

    @Test
    void retriesRejectedSharesOnReceiversWithCapacity() {
        int[] received = new int[3];

        int accepted = FairEnergyDistributor.distribute(10, received.length, 0, (index, amount) -> {
            if (index != 0) {
                return 0;
            }
            received[index] += amount;
            return amount;
        });

        assertEquals(10, accepted);
        assertArrayEquals(new int[]{10, 0, 0}, received);
    }

    @Test
    void rotatesTheFirstReceiverForSmallOffers() {
        int[] received = new int[3];

        int accepted = FairEnergyDistributor.distribute(2, received.length, 1,
                (index, amount) -> received[index] += amount);

        assertEquals(2, accepted);
        assertArrayEquals(new int[]{0, 1, 1}, received);
    }

    @Test
    void reportsUnacceptedEnergy() {
        int accepted = FairEnergyDistributor.distribute(10, 2, 0, (index, amount) -> 0);

        assertEquals(0, accepted);
    }
}
