package cn.ae2bc.core.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UnitPortTypeTest {
    @Test
    void idsRoundTrip() {
        for (UnitPortType type : UnitPortType.values()) {
            assertEquals(type, UnitPortType.fromId(type.getId()));
            assertEquals(type.getId(), type.getSerializedName());
        }
        assertThrows(IllegalArgumentException.class, () -> UnitPortType.fromId("missing"));
    }

    @Test
    void behaviorFlagsDescribePlatformIndependentRoles() {
        assertTrue(UnitPortType.TRANSFER.acceptsTaskInput());
        assertTrue(UnitPortType.RETURN.returnsTaskOutput());
        assertTrue(UnitPortType.EXTRACT.needsTaskTick());
        assertTrue(UnitPortType.ENERGY.acceptsExternalEnergy());
        assertFalse(UnitPortType.ENERGY.needsTaskTick());
        assertEquals(UnitPortType.TRANSFER, UnitPortType.forOutputFormId(0));
        assertEquals(UnitPortType.DROP, UnitPortType.forOutputFormId(1));
        assertEquals(UnitPortType.PLACE, UnitPortType.forOutputFormId(2));
    }
}
