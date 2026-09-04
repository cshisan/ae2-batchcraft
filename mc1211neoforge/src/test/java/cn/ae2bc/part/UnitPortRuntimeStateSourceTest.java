package cn.ae2bc.part;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitPortRuntimeStateSourceTest {
    @Test
    void taskLifecycleInvalidatesPortsWithoutResettingOnConfigurationChanges() throws Exception {
        String logic = Files.readString(Path.of(
                "src/main/java/cn/ae2bc/logic/PatternP2PUnitManagerLogic.java"));

        String apply = section(logic, "public void applyMainConfiguration", "public boolean tryAcceptPattern");
        assertTrue(apply.contains("wakePorts();"));
        assertFalse(apply.contains("invalidatePortRuntimeState();"));

        String reset = section(logic, "public void resetTaskState", "public PatternP2PUnitConfiguration");
        assertTrue(reset.contains("invalidatePortRuntimeState();"));

        String finish = section(logic, "private void finishTask()", "private void wakePorts()");
        assertTrue(finish.contains("invalidatePortRuntimeState();"));
    }

    @Test
    void reloadForcesRedstoneNeighborNotification() throws Exception {
        String port = Files.readString(Path.of(
                "src/main/java/cn/ae2bc/part/PatternP2PUnitPortPart.java"));

        assertTrue(port.contains("private boolean redstoneWorldStateDirty = true;"));
        assertTrue(port.contains("redstonePower != clamped || redstoneWorldStateDirty"));
        assertTrue(port.contains("public void invalidateTaskRuntimeState()"));
        assertTrue(port.contains("setRedstonePower(0);"));
    }

    @Test
    void managerAndOutputPortUseDirectLocalSlotOverrideSemantics() throws Exception {
        String managerLogic = Files.readString(Path.of(
                "src/main/java/cn/ae2bc/logic/PatternP2PUnitManagerLogic.java"));
        String managerPart = Files.readString(Path.of(
                "src/main/java/cn/ae2bc/part/PatternP2PUnitManagerPart.java"));
        String port = Files.readString(Path.of(
                "src/main/java/cn/ae2bc/part/PatternP2PUnitPortPart.java"));

        assertTrue(managerLogic.contains("ConfigurationSync.State<PatternP2PUnitConfiguration> configurationState"));
        assertFalse(managerLogic.contains("cachedMainConfiguration"));
        assertTrue(managerLogic.contains("configurationState.applyBroadcast(configuration, revision)"));
        assertTrue(managerPart.contains("port.applyManagerSingleSlot(mode);"));
        assertTrue(port.contains("return singleSlot;"));
        assertTrue(port.contains("public void applyManagerSingleSlot(OutputSlotSharingMode mode)"));
        assertTrue(port.contains("mode == OutputSlotSharingMode.FOLLOW_PORT"));
    }

    private static String section(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from + start.length());
        assertTrue(from >= 0, "Missing section start: " + start);
        assertTrue(to > from, "Missing section end: " + end);
        return source.substring(from, to);
    }
}
