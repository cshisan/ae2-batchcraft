package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class IdentityGroupingTest {
    @Test
    void equalValuesWithDifferentIdentitiesRemainSeparate() {
        String first = new String("storage");
        String second = new String("storage");
        var source = new LinkedHashMap<String, String>();
        source.put("items", first);
        source.put("fluids", first);
        source.put("custom", second);

        var grouped = IdentityGrouping.invert(source);

        assertEquals(2, grouped.size());
        assertSame(first, grouped.keySet().stream().filter(value -> value == first).findFirst().orElseThrow());
        assertEquals(2, grouped.get(first).size());
        assertEquals(1, grouped.get(second).size());
    }
}
