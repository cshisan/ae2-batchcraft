package cn.ae2bc.pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialOutputEncodingContextTest {
    @Test
    void contextOnlyMatchesTheActiveEncodingLogic() {
        Object activeLogic = new Object();
        Object otherLogic = new Object();

        assertFalse(MaterialOutputEncodingContext.isActiveFor(activeLogic));
        try (var ignored = MaterialOutputEncodingContext.enter(activeLogic)) {
            assertTrue(MaterialOutputEncodingContext.isActiveFor(activeLogic));
            assertFalse(MaterialOutputEncodingContext.isActiveFor(otherLogic));
        }
        assertFalse(MaterialOutputEncodingContext.isActiveFor(activeLogic));
    }

    @Test
    void nestedContextRestoresTheOuterEncodingLogic() {
        Object outerLogic = new Object();
        Object innerLogic = new Object();

        try (var outer = MaterialOutputEncodingContext.enter(outerLogic)) {
            try (var inner = MaterialOutputEncodingContext.enter(innerLogic)) {
                assertFalse(MaterialOutputEncodingContext.isActiveFor(outerLogic));
                assertTrue(MaterialOutputEncodingContext.isActiveFor(innerLogic));
            }
            assertTrue(MaterialOutputEncodingContext.isActiveFor(outerLogic));
        }
        assertFalse(MaterialOutputEncodingContext.isActiveFor(outerLogic));
    }

    @Test
    void contextRejectsMissingLogic() {
        assertThrows(NullPointerException.class, () -> MaterialOutputEncodingContext.enter(null));
    }

    @Test
    void contextIsClearedWhenEncodingFails() {
        Object logic = new Object();

        assertThrows(IllegalStateException.class, () -> {
            try (var ignored = MaterialOutputEncodingContext.enter(logic)) {
                throw new IllegalStateException("encoding failed");
            }
        });
        assertFalse(MaterialOutputEncodingContext.isActiveFor(logic));
    }
}
