package cn.ae2bc.client;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public final class UnitPortSingleSlotSyncSourceTest {
    @Test
    public void effectiveStateUsesGlobalModeAndOnlyFollowPortIsEditable() throws Exception {
        String port = read("../part/PatternP2PUnitPortPart.java");
        assertTrue(port.contains("case ALL: return true;"));
        assertTrue(port.contains("case DISABLED: return false;"));
        assertTrue(port.contains("case FOLLOW_PORT: return singleSlot;"));
        assertTrue(port.contains("manager.getOutputSlotSharingMode() == OutputSlotSharingMode.FOLLOW_PORT"));
        assertTrue(port.contains("public boolean isUsingMainConfiguration()"));
    }

    @Test
    public void serverPushesAuthoritativeStateToOpenPortScreens() throws Exception {
        String menu = read("../menu/UnitPortOutputConfigMenu.java");
        String network = read("../network/ModNetwork.java");
        String manager = read("../part/PatternP2PUnitManagerPart.java");
        assertTrue(menu.contains("public void refreshStateAndSync()"));
        assertTrue(menu.contains("private boolean stateSynchronized;"));
        assertTrue(menu.contains("refreshStateAndSync(boolean force)"));
        assertTrue(menu.contains("if (!force && stateSynchronized && !changed) return;"));
        assertTrue(menu.contains("manager.synchronizeFromInput();"));
        assertTrue(network.contains("refreshUnitPortStates(PatternP2PUnitManagerPart manager)"));
        assertTrue(network.contains("menu.refreshStateAndSync();"));
        assertTrue(network.contains("menu.refreshStateAndSync(true);"));
        assertTrue(manager.contains("ModNetwork.refreshUnitPortStates(this)"));
    }

    @Test
    public void managerAppliesSyncModeBeforeLocalSettings() throws Exception {
        String network = read("../network/ModNetwork.java");
        int sync = network.indexOf("part.setSyncMainConfiguration(message.syncMainConfiguration);");
        int settings = network.indexOf("part.setSettings(message.settings);");
        assertTrue(sync >= 0 && settings > sync);
    }

    @Test
    public void topologyLookupDoesNotHideOfflineBindings() throws Exception {
        String topology = read("../logic/PatternP2PTopologyGridService.java");
        assertTrue(topology.contains("return snapshot(ownNode.getGrid()).managers.get(id);"));
        assertTrue(topology.contains("return snapshot(ownNode.getGrid()).inputs.get(frequency);"));
    }

    @Test
    public void syncedManagersRefreshBeforeExposingEffectiveSlotMode() throws Exception {
        String manager = read("../part/PatternP2PUnitManagerPart.java");
        assertTrue(manager.contains("if (syncMainConfiguration && !synchronizingFromInput"));
        assertTrue(manager.contains("synchronizeFromInput();\n        }\n        return getEffectiveSettings()"));
        assertTrue(manager.contains("if (synchronizingFromInput) return;"));
    }

    private static String read(String relative) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get("src/main/java/cn/ae2bc/client", relative));
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
