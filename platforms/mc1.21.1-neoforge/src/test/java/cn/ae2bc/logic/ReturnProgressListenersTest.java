package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReturnProgressListenersTest {
    @Test
    void duplicateRegistrationWakesTickerOnceAndProgressReleasesListener() {
        var listeners = new ReturnProgressListeners();
        var wakeCount = new AtomicInteger();
        Runnable producer = () -> { };

        listeners.register(producer, true, wakeCount::incrementAndGet);
        listeners.register(producer, true, wakeCount::incrementAndGet);

        assertEquals(1, wakeCount.get());
        assertTrue(listeners.takeAfterProgress(false).isEmpty());
        assertEquals(1, listeners.takeAfterProgress(true).size());
        assertTrue(listeners.takeAfterProgress(true).isEmpty());
    }

    @Test
    void emptyReturnInventoryDoesNotRegisterOrWake() {
        var listeners = new ReturnProgressListeners();
        var wakeCount = new AtomicInteger();

        listeners.register(() -> { }, false, wakeCount::incrementAndGet);

        assertEquals(0, wakeCount.get());
        assertTrue(listeners.takeAfterProgress(true).isEmpty());
    }
}
