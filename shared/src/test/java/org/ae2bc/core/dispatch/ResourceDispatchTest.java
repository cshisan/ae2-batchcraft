package org.ae2bc.core.dispatch;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

class ResourceDispatchTest {
    @Test
    void emptyAndSingleStepPlansPreserveOneShotAndAcceptanceLimits() {
        for (long supplied : new long[]{-1, 0, 1, 10, Long.MAX_VALUE}) {
            ResourceTransferPlan empty = ResourceTransferPlan.empty();
            Assertions.assertEquals(0, empty.execute(supplied));
            Assertions.assertThrows(IllegalStateException.class, () -> empty.execute(1));
            for (long accepted : new long[]{-1, 3, Long.MAX_VALUE}) {
                AtomicInteger visits = new AtomicInteger();
                ResourceTransferPlan single = ResourceTransferPlan.leaf(10, amount -> {
                    visits.incrementAndGet();
                    Assertions.assertTrue(amount > 0 && amount <= 10);
                    return accepted;
                });
                long expected = Math.max(0, Math.min(Math.min(supplied, 10), accepted));
                Assertions.assertEquals(expected, single.execute(supplied));
                Assertions.assertEquals(supplied <= 0 ? 0 : 1, visits.get());
                Assertions.assertThrows(IllegalStateException.class, () -> single.execute(supplied));
            }
        }
    }

    @Test
    void nestedBudgetsShareTheSameLimitAndReserveWorkForOtherPhases() {
        WorkBudget total = new WorkBudget(5);
        WorkBudget recovery = total.limitedTo(2);
        Assertions.assertTrue(recovery.tryUse());
        Assertions.assertTrue(recovery.tryUse());
        Assertions.assertFalse(recovery.tryUse());
        Assertions.assertEquals(3, total.remaining());
        WorkBudget extraction = total.limitedTo(10);
        for (int index = 0; index < 3; index++) {
            Assertions.assertTrue(extraction.tryUse());
        }
        Assertions.assertFalse(extraction.tryUse());
        Assertions.assertFalse(total.tryUse());
        Assertions.assertEquals(0, extraction.remaining());
    }

    @Test
    void equalDistributionRedistributesSmallCapacitiesWithoutLosingTheRemainder() {
        Assertions.assertArrayEquals(new long[]{1, 4, 4},
                ResourceDistribution.allocate(9, new long[]{1, 20, 20}, true));
        Assertions.assertArrayEquals(new long[]{4, 3, 3},
                ResourceDistribution.allocate(10, new long[]{20, 20, 20}, true));
        Assertions.assertArrayEquals(new long[]{1, 8, 0},
                ResourceDistribution.allocate(9, new long[]{1, 20, 20}, false));
    }

    @Test
    void allocationsConserveQuantityEvenWhenCapacityTotalsOverflowLong() {
        Random random = new Random(42);
        for (int trial = 0; trial < 1000; trial++) {
            long requested = trial % 2 == 0 ? Long.MAX_VALUE : random.nextInt(1000);
            long[] capacities = new long[1 + random.nextInt(20)];
            BigInteger totalCapacity = BigInteger.ZERO;
            for (int index = 0; index < capacities.length; index++) {
                capacities[index] = random.nextBoolean() ? Long.MAX_VALUE : random.nextInt(200) - 10;
                totalCapacity = totalCapacity.add(BigInteger.valueOf(Math.max(0, capacities[index])));
            }
            for (boolean evenly : new boolean[]{false, true}) {
                long[] allocated = ResourceDistribution.allocate(requested, capacities, evenly);
                BigInteger allocatedTotal = BigInteger.ZERO;
                for (int index = 0; index < capacities.length; index++) {
                    Assertions.assertTrue(allocated[index] >= 0);
                    Assertions.assertTrue(allocated[index] <= Math.max(0, capacities[index]));
                    allocatedTotal = allocatedTotal.add(BigInteger.valueOf(allocated[index]));
                }
                Assertions.assertEquals(totalCapacity.min(BigInteger.valueOf(requested)), allocatedTotal);
            }
        }
    }

    @Test
    void hugeRequestsDoNotRepeatedlyVisitAOneItemTarget() {
        AtomicInteger visits = new AtomicInteger();
        ResourceTransferPlan target = ResourceTransferPlan.leaf(1, supplied -> {
            visits.incrementAndGet();
            return supplied;
        });
        ResourceTransferPlan plan = ResourceTransferPlan.combine(
                Long.MAX_VALUE, Arrays.asList(target), true);
        Assertions.assertEquals(1, plan.amount());
        Assertions.assertEquals(0, visits.get());
        Assertions.assertEquals(1, plan.execute(Long.MAX_VALUE));
        Assertions.assertEquals(1, visits.get());
        Assertions.assertThrows(IllegalStateException.class, () -> plan.execute(1));
        Assertions.assertEquals(1, visits.get());
    }

    @Test
    void partialAcceptanceLeavesRemainderWithCallerInsteadOfRetryingUnplannedTargets() {
        AtomicLong second = new AtomicLong();
        ResourceTransferPlan plan = ResourceTransferPlan.combine(10, Arrays.asList(
                ResourceTransferPlan.leaf(8, supplied -> Math.min(5, supplied)),
                ResourceTransferPlan.leaf(2, second::addAndGet)), false);
        Assertions.assertEquals(10, plan.amount());
        Assertions.assertEquals(7, plan.execute(10));
        Assertions.assertEquals(2, second.get());
    }

    @Test
    void shortExtractionRedistributesOnlyTheResourcesActuallyObtained() {
        AtomicLong first = new AtomicLong();
        AtomicLong second = new AtomicLong();
        ResourceTransferPlan plan = ResourceTransferPlan.combine(20, Arrays.asList(
                ResourceTransferPlan.leaf(10, first::addAndGet),
                ResourceTransferPlan.leaf(10, second::addAndGet)), true);
        Assertions.assertEquals(5, plan.execute(5));
        Assertions.assertEquals(3, first.get());
        Assertions.assertEquals(2, second.get());
    }

    @Test
    void managerPortsRemainOneGroupWhenDistributingEqually() {
        AtomicLong output = new AtomicLong();
        AtomicLong portOne = new AtomicLong();
        AtomicLong portTwo = new AtomicLong();
        ResourceTransferPlan manager = ResourceTransferPlan.combine(20, Arrays.asList(
                ResourceTransferPlan.leaf(2, portOne::addAndGet),
                ResourceTransferPlan.leaf(20, portTwo::addAndGet)), false);
        ResourceTransferPlan plan = ResourceTransferPlan.combine(20, Arrays.asList(
                ResourceTransferPlan.leaf(20, output::addAndGet), manager), true);
        Assertions.assertEquals(20, plan.execute(20));
        Assertions.assertEquals(10, output.get());
        Assertions.assertEquals(2, portOne.get());
        Assertions.assertEquals(8, portTwo.get());
    }

    @Test
    void executionCannotReenterTheSamePlan() {
        ResourceTransferPlan[] holder = new ResourceTransferPlan[1];
        holder[0] = ResourceTransferPlan.leaf(1, supplied -> {
            Assertions.assertThrows(IllegalStateException.class, () -> holder[0].execute(supplied));
            return supplied;
        });
        Assertions.assertEquals(1, holder[0].execute(1));
    }
}
