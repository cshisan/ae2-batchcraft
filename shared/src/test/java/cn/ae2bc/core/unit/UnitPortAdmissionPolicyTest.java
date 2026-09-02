package cn.ae2bc.core.unit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitPortAdmissionPolicyTest {
    @Test
    void dropAndPlacePortsDoNotConsumeSharedTransferCapacity() {
        assertEquals(Long.MAX_VALUE, UnitPortAdmissionPolicy.initialCapacity(
                UnitPortType.DROP, TransferPortOutputMode.NORMAL));
        assertEquals(Long.MAX_VALUE, UnitPortAdmissionPolicy.initialCapacity(
                UnitPortType.DROP, TransferPortOutputMode.SINGLE_ITEM));
        assertEquals(Long.MAX_VALUE, UnitPortAdmissionPolicy.initialCapacity(
                UnitPortType.PLACE, TransferPortOutputMode.SINGLE_ITEM));
    }

    @Test
    void transferOnlyModesApplyOnlyToTransferPorts() {
        assertEquals(1, UnitPortAdmissionPolicy.initialCapacity(
                UnitPortType.TRANSFER, TransferPortOutputMode.SINGLE_ITEM));
        assertTrue(UnitPortAdmissionPolicy.usesTransferOutputMode(UnitPortType.TRANSFER));
        assertFalse(UnitPortAdmissionPolicy.usesTransferOutputMode(UnitPortType.DROP));
        assertFalse(UnitPortAdmissionPolicy.usesTransferOutputMode(UnitPortType.PLACE));
    }
}
