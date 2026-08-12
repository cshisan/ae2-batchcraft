package cn.ae2bc.core.schedule;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeadlineQueueTest {
    @Test
    void replacesAnIdentityWithoutReturningTheStaleDeadline() {
        Object task = new Object();
        DeadlineQueue<Object> queue = new DeadlineQueue<Object>();
        queue.schedule(task, 10);
        queue.schedule(task, 20);
        assertEquals(Collections.emptyList(), queue.takeDue(10));
        assertEquals(Collections.singletonList(task), queue.takeDue(20));
        assertFalse(queue.isScheduled(task));
    }

    @Test
    void cancelsOnlyTheRequestedIdentity() {
        Object first = new Object();
        Object second = new Object();
        DeadlineQueue<Object> queue = new DeadlineQueue<Object>();
        queue.schedule(first, 4);
        queue.schedule(second, 4);
        queue.cancel(first);
        assertTrue(queue.isScheduled(second));
        assertEquals(Collections.singletonList(second), queue.takeDue(4));
    }
}
