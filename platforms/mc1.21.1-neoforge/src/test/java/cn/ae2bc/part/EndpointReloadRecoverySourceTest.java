package cn.ae2bc.part;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EndpointReloadRecoverySourceTest {
    @Test
    void patternEndpointsInvalidateAvailabilityWhenTheirGridStateReturns() throws Exception {
        String source = read("PatternP2PTunnelPart.java");

        assertTrue(source.contains("protected void onMainNodeStateChanged(IGridNodeListener.State reason)"));
        assertTrue(source.contains("outputLogic.alertRetry()"));
        assertTrue(source.contains("notifyInputAvailabilityChanged()"));
        assertTrue(source.contains("inputLogic.invalidateOutputs()"));
    }

    @Test
    void enabledEnergyInputWakesWhenItsGridStateReturns() throws Exception {
        String source = read("PatternP2PTunnelEnergyPart.java");

        assertTrue(source.contains("protected void onMainNodeStateChanged(IGridNodeListener.State reason)"));
        assertTrue(source.contains("if (pullEnabled)"));
        assertTrue(source.contains("grid.getTickManager().alertDevice(getMainNode().getNode())"));
    }

    @Test
    void unitEndpointsAlreadyDiscardReloadSensitiveCaches() throws Exception {
        String manager = read("PatternP2PUnitManagerPart.java");
        String port = read("PatternP2PUnitPortPart.java");

        assertTrue(manager.contains("protected void onMainNodeStateChanged(IGridNodeListener.State reason)"));
        assertTrue(manager.contains("synchronizeFromInput()"));
        assertTrue(manager.contains("notifyInputTopologyChanged()"));
        assertTrue(port.contains("protected void onMainNodeStateChanged(IGridNodeListener.State reason)"));
        assertTrue(port.contains("cachedManager = null"));
    }

    private static String read(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/cn/ae2bc/part", name));
    }
}
