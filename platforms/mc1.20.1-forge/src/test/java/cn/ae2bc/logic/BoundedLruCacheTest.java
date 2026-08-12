package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoundedLruCacheTest {
    @Test
    void rejectsNonPositiveCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new BoundedLruCache<>(0));
        assertThrows(IllegalArgumentException.class, () -> new BoundedLruCache<>(-1));
    }

    @Test
    void evictsTheLeastRecentlyUsedEntry() {
        var cache = new BoundedLruCache<String, String>(2);
        cache.computeIfAbsent("first", ignored -> "one");
        cache.computeIfAbsent("second", ignored -> "two");

        assertEquals("one", cache.computeIfAbsent("first", ignored -> {
            throw new AssertionError("Cached entry should not be recomputed");
        }));
        cache.computeIfAbsent("third", ignored -> "three");

        assertEquals("two-new", cache.computeIfAbsent("second", ignored -> "two-new"));
        assertEquals(2, cache.size());
    }

    @Test
    void patternMetadataCacheIsLimitedToSixtyFourEntries() {
        assertEquals(64, PatternMetadataCache.MAX_ENTRIES);
    }
}
