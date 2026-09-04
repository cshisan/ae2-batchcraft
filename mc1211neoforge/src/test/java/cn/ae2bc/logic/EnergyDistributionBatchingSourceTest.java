package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnergyDistributionBatchingSourceTest {
    @Test
    void energyTunnelModeControlsFirstStageWithoutOverwritingEndpointModes() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/cn/ae2bc/logic/PatternP2PEnergyGridService.java"));
        String method = methodBody(source, "public void setGlobalEnergyDistributionMode",
                "public void synchronizeOutputGroupMode");

        assertTrue(method.contains("globalEnergyDistributionMode = mode"));
        assertFalse(method.contains("manager.getLogic()"));
        assertFalse(method.contains("output.getOutputLogic()"));
        assertEquals(1, occurrences(method, "demandChanged()"));
    }

    @Test
    void managerModeComesFromItsEffectiveUnitConfiguration() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/cn/ae2bc/logic/PatternP2PUnitManagerLogic.java"));
        String method = methodBody(source, "public EnergyDistributionMode getEnergyDistributionMode",
                "public void setEnergyDistributionMode");

        assertTrue(method.contains("getEffectiveConfiguration().energyDistributionMode()"));
        assertFalse(method.contains("globalEnergyDistributionMode"));
    }

    @Test
    void unitEnergyModePersistsInConfigurationWithLegacyManagerMigration() throws Exception {
        String configuration = Files.readString(Path.of(
                "src/main/java/cn/ae2bc/logic/PatternP2PUnitConfiguration.java"));
        String manager = Files.readString(Path.of(
                "src/main/java/cn/ae2bc/logic/PatternP2PUnitManagerLogic.java"));

        assertTrue(configuration.contains("data.putByte(\"EnergyDistributionMode\""));
        assertTrue(configuration.contains("data.contains(\"EnergyDistributionMode\")"));
        assertTrue(manager.contains("legacyEnergyDistributionMode"));
        assertTrue(manager.contains("withEnergyDistributionMode(legacyEnergyDistributionMode)"));
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
