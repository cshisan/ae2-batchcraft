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
                "public void synchronizeOutputGroupMode");

        assertFalse(method.contains("manager.getLogic().applyEnergyDistributionMode(mode)"));
        assertFalse(method.contains("manager.getLogic().setEnergyDistributionMode(mode)"));
        assertTrue(method.contains("energyTunnel.getHost().markForSave()"));
        assertFalse(method.contains("topologyChanged()"));
        assertEquals(1, occurrences(method, "demandChanged()"));
    }

    @Test
    void individualManagerModeChangeOnlyInvalidatesDemand() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/cn/ae2bc/logic/PatternP2PUnitManagerLogic.java"));
        String method = methodBody(source, "public void setEnergyDistributionMode",
                "public void setSyncMainConfiguration");

        assertTrue(method.contains("setLocalConfiguration"));
        assertFalse(method.contains("topologyChanged()"));
    }

    @Test
    void energyIsAllocatedToLogicalGroupsBeforeTheirEndpoints() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/cn/ae2bc/logic/PatternP2PEnergyGridService.java"));

        assertTrue(source.contains("allocateToGroups(allocatable);"));
        assertTrue(source.contains("totalDemand = saturatingAdd(totalDemand, group.demand);"));
        assertTrue(source.contains("globalEnergyDistributionMode == EnergyDistributionMode.ROUND_ROBIN"));
        assertTrue(source.contains("EnergyDistributionMode mode = sinks.get(0).mode();"));
        assertTrue(source.contains("return manager == null ? EnergyDistributionMode.EVEN"));
        assertTrue(source.contains(": manager.getLogic().getEnergyDistributionMode();"));
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
