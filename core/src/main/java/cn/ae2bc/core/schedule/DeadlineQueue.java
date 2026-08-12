package cn.ae2bc.core.schedule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** Version-neutral identity scheduler for one live deadline per task. */
public final class DeadlineQueue<T> {
    private static final int COMPACTION_SLACK = 64;
    private final Map<T, Ticket<T>> scheduled = new IdentityHashMap<T, Ticket<T>>();
    private final PriorityQueue<Ticket<T>> queue = new PriorityQueue<Ticket<T>>(
            Comparator.<Ticket<T>>comparingLong(Ticket<T>::deadline)
                    .thenComparingLong(Ticket<T>::sequence));
    private long sequence;

    public void schedule(T value, long deadline) {
        Ticket<T> ticket = new Ticket<T>(value, deadline, sequence++);
        scheduled.put(value, ticket);
        queue.add(ticket);
        compactIfNeeded();
    }

    public void cancel(T value) {
        scheduled.remove(value);
        compactIfNeeded();
    }

    public boolean isScheduled(T value) {
        return scheduled.containsKey(value);
    }

    public List<T> takeDue(long now) {
        if (queue.isEmpty() || queue.peek().deadline() > now) {
            return Collections.emptyList();
        }
        List<T> due = new ArrayList<T>();
        while (!queue.isEmpty() && queue.peek().deadline() <= now) {
            Ticket<T> ticket = queue.remove();
            if (scheduled.remove(ticket.value(), ticket)) {
                due.add(ticket.value());
            }
        }
        return due;
    }

    private void compactIfNeeded() {
        if (queue.size() <= scheduled.size() * 4 + COMPACTION_SLACK) {
            return;
        }
        queue.clear();
        queue.addAll(scheduled.values());
    }

    private static final class Ticket<T> {
        private final T value;
        private final long deadline;
        private final long sequence;

        private Ticket(T value, long deadline, long sequence) {
            this.value = value;
            this.deadline = deadline;
            this.sequence = sequence;
        }

        private T value() { return value; }
        private long deadline() { return deadline; }
        private long sequence() { return sequence; }
    }
}
