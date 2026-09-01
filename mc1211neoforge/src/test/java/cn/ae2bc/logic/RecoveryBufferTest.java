package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryBufferTest {
    @Test
    void mergesKeysAndRetainsAnUnacceptedRemainder() {
        var buffer = new RecoveryBuffer<String>();
        buffer.queue("stone", 4);
        buffer.queue("stone", 6);

        assertTrue(buffer.drain((ignored, amount) -> 3L));
        assertFalse(buffer.isEmpty());
        var remainder = new AtomicLong();
        assertTrue(buffer.drain((ignored, amount) -> {
            remainder.set(amount);
            return amount;
        }));

        assertEquals(7, remainder.get());
        assertTrue(buffer.isEmpty());
    }

    @Test
    void mergeSaturatesInsteadOfOverflowing() {
        var buffer = new RecoveryBuffer<String>();
        buffer.queue("stone", Long.MAX_VALUE);
        buffer.queue("stone", 1);
        var amount = new AtomicLong();

        buffer.drain((ignored, available) -> {
            amount.set(available);
            return available;
        });

        assertEquals(Long.MAX_VALUE, amount.get());
    }
}
