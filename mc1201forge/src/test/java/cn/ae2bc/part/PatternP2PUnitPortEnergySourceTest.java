package cn.ae2bc.part;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternP2PUnitPortEnergySourceTest {
    @Test
    void externalEnergyIsNotGatedByAnActiveTask() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/cn/ae2bc/part/PatternP2PUnitPortPart.java"));
        int methodStart = source.indexOf("public int receiveExternalEnergy");
        int methodEnd = source.indexOf("public int isProvidingStrongPower", methodStart);
        String method = source.substring(methodStart, methodEnd);

        assertTrue(method.contains("manager == null"),
                "energy ports must still require a valid bound manager");
        assertFalse(method.contains("isTaskOperational()"),
                "energy ports must operate even when the unit has no active task");
    }
}
