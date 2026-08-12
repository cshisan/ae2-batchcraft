package cn.ae2bc.core.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TickCachedIndexTest {
    @Test
    void rebuildsOncePerTickAndAfterInvalidation() {
        TickCachedIndex<String, Integer> index = new TickCachedIndex<>();
        AtomicInteger builds = new AtomicInteger();

        assertEquals(1, index.find(10, "port", () -> {
            builds.incrementAndGet();
            return Collections.singletonMap("port", 1);
        }));
        assertNull(index.find(10, "missing", () -> {
            builds.incrementAndGet();
            return Collections.emptyMap();
        }));
        assertEquals(1, builds.get());
        assertEquals(1, index.values(10, Collections::emptyMap).size());

        index.find(11, "missing", () -> {
            builds.incrementAndGet();
            return Collections.emptyMap();
        });
        index.invalidate();
        index.find(11, "missing", () -> {
            builds.incrementAndGet();
            return Collections.emptyMap();
        });
        assertEquals(3, builds.get());
    }
}
