package cn.ae2bc.logic;

import cn.ae2bc.core.schedule.DeadlineQueue;

import java.util.List;

/** Keeps one live deadline per identity and discards replaced queue entries lazily. */
final class DeadlineScheduler<T> {
    private final DeadlineQueue<T> delegate = new DeadlineQueue<>();

    void schedule(T value, long deadline) {
        delegate.schedule(value, deadline);
    }

    void cancel(T value) {
        delegate.cancel(value);
    }

    boolean isScheduled(T value) {
        return delegate.isScheduled(value);
    }

    List<T> takeDue(long now) {
        return delegate.takeDue(now);
    }
}
