package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnergyDistributionBatchingSourceTest {
    @Test
    void globalModeAppliesSilentlyAndInvalidatesDemandOnce() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/cn/ae2bc/logic/PatternP2PEnergyGridService.java"));
        String method = methodBody(source, "public void setGlobalEnergyDistributionMode",
                "private void initializeOrApplyGlobalMode(PatternP2PTunnelPart");

        assertTrue(method.contains("manager.getLogic().applyEnergyDistributionMode(mode)"));
        assertFalse(method.contains("manager.getLogic().setEnergyDistributionMode(mode)"));
        assertFalse(method.contains("topologyChanged()"));
        assertEquals(1, occurrences(method, "demandChanged()"));
    }

    @Test
    void individualManagerModeChangeOnlyInvalidatesDemand() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/cn/ae2bc/logic/PatternP2PUnitManagerLogic.java"));
        String method = methodBody(source, "public void setEnergyDistributionMode",
                "boolean applyEnergyDistributionMode");

        assertTrue(method.contains("demandChanged()"));
        assertFalse(method.contains("topologyChanged()"));
    }

    private static String methodBody(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue(start >= 0 && end > start);
        return source.substring(start, end);
    }

    private static int occurrences(String source, String value) {
        int count = 0;
        for (int index = 0; (index = source.indexOf(value, index)) >= 0; index += value.length()) {
            count++;
        }
        return count;
    }
}
