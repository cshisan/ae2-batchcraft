package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeadlineSchedulerTest {
    @Test
    void onlyReturnsJobsWhoseDeadlineHasArrived() {
        var scheduler = new DeadlineScheduler<Object>();
        var early = new Object();
        var late = new Object();

        scheduler.schedule(late, 20);
        scheduler.schedule(early, 5);

        assertTrue(scheduler.takeDue(4).isEmpty());
        assertEquals(java.util.List.of(early), scheduler.takeDue(5));
        assertEquals(java.util.List.of(late), scheduler.takeDue(20));
    }

    @Test
    void replacementAndCancellationInvalidateOldQueueEntries() {
        var scheduler = new DeadlineScheduler<Object>();
        var replaced = new Object();
        var cancelled = new Object();

        scheduler.schedule(replaced, 5);
        scheduler.schedule(replaced, 10);
        scheduler.schedule(cancelled, 5);
        scheduler.cancel(cancelled);

        assertTrue(scheduler.takeDue(5).isEmpty());
        assertTrue(scheduler.isScheduled(replaced));
        assertFalse(scheduler.isScheduled(cancelled));
        assertEquals(java.util.List.of(replaced), scheduler.takeDue(10));
    }

    @Test
    void wakeScheduledDuringProcessingRemainsPending() {
        var scheduler = new DeadlineScheduler<Object>();
        var job = new Object();

        scheduler.schedule(job, 1);
        assertEquals(java.util.List.of(job), scheduler.takeDue(1));
        scheduler.schedule(job, 1);

        assertTrue(scheduler.isScheduled(job));
        assertEquals(java.util.List.of(job), scheduler.takeDue(2));
    }
}
