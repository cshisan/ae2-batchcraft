package org.ae2bc.core;

import org.ae2bc.common.Utils;
import org.ae2bc.core.unit.UnitIdentityKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SharedCoreUtilityTest {
    @Test
    void operationsForRoundsUpAndSaturates() {
        Assertions.assertEquals(0, Utils.operationsFor(0, 1));
        Assertions.assertEquals(0, Utils.operationsFor(10, 0));
        Assertions.assertEquals(1, Utils.operationsFor(1, 64));
        Assertions.assertEquals(2, Utils.operationsFor(65, 64));
        Assertions.assertEquals(Integer.MAX_VALUE, Utils.operationsFor(Long.MAX_VALUE, 1));
    }

    @Test
    void unitIdentityKeyPreservesUnsignedShortValues() {
        Assertions.assertEquals(
                0xFFFF0001,
                UnitIdentityKey.pack((short) -1, (short) 1)
        );
        Assertions.assertEquals(
                0x00010000,
                UnitIdentityKey.pack((short) 1, (short) 0)
        );
    }
}
