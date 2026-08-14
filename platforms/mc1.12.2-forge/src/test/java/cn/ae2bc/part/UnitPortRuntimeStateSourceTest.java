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
    public void reloadForcesRedstoneNeighborNotification() throws Exception {
        String port = read("PatternP2PUnitPortPart.java");

        assertTrue(port.contains("private boolean redstoneWorldStateDirty = true;"));
        assertTrue(port.contains("redstonePower == next && !redstoneWorldStateDirty"));
        assertTrue(port.contains("public void invalidateTaskRuntimeState()"));
        assertTrue(port.contains("redstoneWorldStateDirty = true;"));
        assertTrue(port.contains("setRedstonePower(0);"));
    }

    @Test
    public void networkRecoveryWakesPortsEvenWhenConfigurationIsUnchanged() throws Exception {
        String manager = read("PatternP2PUnitManagerPart.java");
        String port = read("PatternP2PUnitPortPart.java");

        String managerGrid = section(manager, "@Override public void gridChanged()",
                "@MENetworkEventSubscribe");
        assertTrue(managerGrid.contains("wakeBoundPorts();"));

        String managerPower = section(manager, "public void onPowerStatusChanged",
                "@MENetworkEventSubscribe");
        assertTrue(managerPower.contains("wakeBoundPorts();"));

        String managerChannels = section(manager, "public void onChannelsChanged",
                "private void refreshModelState");
        assertTrue(managerChannels.contains("wakeBoundPorts();"));

        String portGrid = section(port, "public void gridChanged()", "@MENetworkEventSubscribe");
        assertTrue(portGrid.contains("cachedManager = null;"));
        assertTrue(portGrid.contains("redstoneWorldStateDirty = true;"));
        assertTrue(portGrid.contains("alertTicking();"));

        String portPower = section(port, "public void onPowerStatusChanged",
                "private void refreshModelState");
        assertTrue(portPower.contains("cachedManager = null;"));
        assertTrue(portPower.contains("redstoneWorldStateDirty = true;"));
        assertTrue(portPower.contains("alertTicking();"));
    }

    @Test
    public void taskAdmissionAlreadyRefreshesReturnConfiguration() throws Exception {
        String manager = read("PatternP2PUnitManagerPart.java");

        String admission = section(manager, "public boolean canAcceptTask()", "public boolean isTaskActive()");
        assertTrue(admission.contains("synchronizeFromInput();"));

        String apply = section(manager, "public void applyMainConfiguration", "public void resetTaskState");
        assertTrue(apply.contains("sameSettings(mainConfiguration, settings)"));
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
