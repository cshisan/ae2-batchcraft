package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReturnModeTest {
    @Test
    void usesCurrentModeIds() {
        assertEquals(ReturnMode.UNBLOCKED, ReturnMode.fromId(0));
        assertEquals(ReturnMode.STRICT, ReturnMode.fromId(1));
        assertEquals(0, ReturnMode.UNBLOCKED.getId());
        assertEquals(1, ReturnMode.STRICT.getId());
        assertEquals("unblocked", ReturnMode.UNBLOCKED.getSerializedName());
        assertEquals("strict", ReturnMode.STRICT.getSerializedName());
    }

    @Test
    void unknownModeIdsDefaultToUnblocked() {
        assertEquals(ReturnMode.UNBLOCKED, ReturnMode.fromId(-1));
        assertEquals(ReturnMode.UNBLOCKED, ReturnMode.fromId(2));
    }
}
