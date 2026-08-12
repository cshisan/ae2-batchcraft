package cn.ae2bc.part;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public final class UnitPortRuntimeStateSourceTest {
    @Test
    public void taskLifecycleInvalidatesPortsWithoutResettingOnConfigurationChanges() throws Exception {
        String manager = read("PatternP2PUnitManagerPart.java");

        String apply = section(manager, "public void applyMainConfiguration", "public void resetTaskState");
        assertTrue(apply.contains("wakeBoundPorts();"));
        assertFalse(apply.contains("invalidateBoundPortRuntimeState();"));

        String reset = section(manager, "public void resetTaskState", "public cn.ae2bc.logic.EnergyDistributionMode");
        assertTrue(reset.contains("invalidateBoundPortRuntimeState();"));

        String finish = section(manager, "private boolean finishTaskIfComplete", "private static boolean sameItem");
        assertTrue(finish.contains("invalidateBoundPortRuntimeState();"));
    }

    @Test
    public void reloadForcesRedstoneNeighborNotificationAndRestoresMainConfiguration() throws Exception {
        String manager = read("PatternP2PUnitManagerPart.java");
        String port = read("PatternP2PUnitPortPart.java");

        assertTrue(manager.contains("PatternP2PUnitMainConfiguration"));
        assertTrue(manager.contains("data.contains(MAIN_CONFIGURATION, 10)"));
        assertTrue(manager.contains("data.put(MAIN_CONFIGURATION, writeSettings("));
        assertTrue(port.contains("private boolean redstoneWorldStateDirty = true;"));
        assertTrue(port.contains("redstonePower == next && !redstoneWorldStateDirty"));
        assertTrue(port.contains("public void invalidateTaskRuntimeState()"));
        assertTrue(port.contains("setRedstonePower(0);"));
    }

    private static String read(String name) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get("src/main/java/cn/ae2bc/part", name));
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String section(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from + start.length());
        assertTrue("Missing section start: " + start, from >= 0);
        assertTrue("Missing section end: " + end, to > from);
        return source.substring(from, to);
    }
}
