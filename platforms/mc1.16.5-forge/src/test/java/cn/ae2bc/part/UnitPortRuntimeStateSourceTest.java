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
        String tunnel = read("PatternP2PTunnelPart.java");

        assertTrue(manager.contains("PatternP2PUnitMainConfiguration"));
        assertTrue(manager.contains("PatternP2PUnitMainConfigurationRevision"));
        assertTrue(manager.contains("data.contains(MAIN_CONFIGURATION, 10)"));
        assertTrue(manager.contains("data.put(MAIN_CONFIGURATION, writeSettings("));
        assertTrue(manager.contains("data.putLong(MAIN_CONFIGURATION_REVISION, mainConfigurationRevision)"));
        assertTrue(tunnel.contains("Ae2bcUnitConfigurationRevision"));
        assertTrue(tunnel.contains("refreshConfigurationConsumers();"));
        assertTrue(port.contains("private boolean redstoneWorldStateDirty = true;"));
        assertTrue(port.contains("redstonePower == next && !redstoneWorldStateDirty"));
        assertTrue(port.contains("public void invalidateTaskRuntimeState()"));
        assertTrue(port.contains("setRedstonePower(0);"));
    }

    @Test
    public void networkRecoveryWakesPortsEvenWhenConfigurationIsUnchanged() throws Exception {
        String manager = read("PatternP2PUnitManagerPart.java");
        String port = read("PatternP2PUnitPortPart.java");

        String managerAdd = section(manager, "@Override public void addToWorld()",
                "@Override public void removeFromWorld()");
        assertTrue(managerAdd.indexOf("synchronizeFromInput();")
                < managerAdd.indexOf("wakeBoundPorts();"));

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
    public void taskAdmissionRefreshesReturnConfigurationBeforeCommittingInputs() throws Exception {
        String manager = read("PatternP2PUnitManagerPart.java");

        String admission = section(manager, "public boolean canAcceptTask()", "public boolean isTaskActive()");
        assertTrue(admission.contains("synchronizeFromInput();"));

        String accept = section(manager, "public boolean acceptInputs", "private PatternP2PUnitPortPart findInputPort");
        int finalRefresh = accept.lastIndexOf("if (!canAcceptTask()) return false;");
        int forcedRefresh = accept.indexOf("synchronizeFromInput(true);", finalRefresh);
        int commit = accept.indexOf("pendingInputs.clear();");
        assertTrue("Configuration must be refreshed immediately before task state is committed",
                finalRefresh >= 0 && finalRefresh < commit);
        assertTrue("The final refresh must bypass a same-tick empty input cache",
                forcedRefresh > finalRefresh && forcedRefresh < commit);

        String apply = section(manager, "public void applyMainConfiguration", "public void resetTaskState");
        assertTrue(apply.contains("sameSettings(mainConfiguration, settings)"));
        assertTrue(apply.contains("mainConfigurationRevision == revision"));
    }

    @Test
    public void networkLifecycleInvalidatesInputCacheAndResynchronizesConfiguration() throws Exception {
        String manager = read("PatternP2PUnitManagerPart.java");
        String tunnel = read("PatternP2PTunnelPart.java");

        String tunnelAdd = section(tunnel, "public void addToWorld()", "public void removeFromWorld()");
        assertTrue(tunnelAdd.indexOf("super.addToWorld();")
                < tunnelAdd.indexOf("refreshConfigurationConsumers();"));
        assertTrue(tunnelAdd.contains("PatternP2PTopologyGridService.invalidate(getGridNode());"));

        String managerAdd = section(manager, "@Override public void addToWorld()",
                "@Override public void removeFromWorld()");
        assertTrue(managerAdd.indexOf("super.addToWorld();")
                < managerAdd.indexOf("synchronizeFromInput();"));
        assertTrue(managerAdd.contains("invalidateInputCache();"));

        String grid = section(manager, "@Override public void gridChanged()", "@MENetworkEventSubscribe");
        assertTrue(grid.contains("invalidateInputCache();"));
        assertTrue(grid.contains("synchronizeFromInput();"));

        String power = section(manager, "public void onPowerStatusChanged", "@MENetworkEventSubscribe");
        assertTrue(power.contains("invalidateInputCache();"));
        assertTrue(power.contains("synchronizeFromInput();"));

        String channels = section(manager, "public void onChannelsChanged", "private void refreshModelState");
        assertTrue(channels.contains("invalidateInputCache();"));
        assertTrue(channels.contains("synchronizeFromInput();"));
    }

    @Test
    public void configurationLookupIsStructuralButProductReturnRequiresAnActiveInput() throws Exception {
        String topology = readLogic("PatternP2PTopologyGridService.java");
        String manager = read("PatternP2PUnitManagerPart.java");

        String lookup = section(topology, "public static PatternP2PTunnelPart findInput",
                "public static int countByFrequency");
        assertTrue(lookup.contains("snapshot(ownNode.getGrid()).inputs.get(frequency)"));
        assertFalse("Configuration lookup must match 1.21.1 and not require a channel",
                lookup.contains("isActive()"));

        String returning = section(manager, "public ItemStack returnProduct", "private PatternP2PTunnelPart findInput");
        assertTrue(returning.contains("findOperationalInput();"));

        String operational = section(manager, "private PatternP2PTunnelPart findOperationalInput",
                "private void invalidateInputCache");
        assertTrue("Product transfer must still reject an offline input",
                operational.contains("input.getGridNode().isActive()"));
    }

    private static String read(String name) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get("src/main/java/cn/ae2bc/part", name));
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String readLogic(String name) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get("src/main/java/cn/ae2bc/logic", name));
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
