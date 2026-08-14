package cn.ae2bc.core.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InvalidatableValueTest {
    @Test
    void rebuildsOnlyAfterExplicitInvalidation() {
        InvalidatableValue<Integer> value = new InvalidatableValue<Integer>();
        AtomicInteger builds = new AtomicInteger();

        assertEquals(1, value.get(builds::incrementAndGet));
        assertEquals(1, value.get(builds::incrementAndGet));

        value.invalidate();

        assertEquals(2, value.get(builds::incrementAndGet));
        assertEquals(2, builds.get());
    }
}
