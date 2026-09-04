package cn.ae2bc.part;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public final class EnergyDistributionHierarchySourceTest {
    @Test
    public void energyTunnelAllocatesToLogicalGroupsBeforeEndpoints() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/cn/ae2bc/part/PatternP2PTunnelEnergyPart.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("final List<EnergyGroup> groups = energyGroups();"));
        assertTrue(source.contains("distributionMode == EnergyDistributionMode.ROUND_ROBIN"));
        assertTrue(source.contains("short frequency = ((PatternP2PTunnelPart) endpoint).getFrequency();"));
        assertTrue(source.contains("UUID managerId = ((PatternP2PUnitPortPart) endpoint).getBoundManagerId();"));
        assertTrue(source.contains("PatternP2PUnitManagerPart manager = ((PatternP2PUnitPortPart) first).findManager();"));
        assertTrue(source.contains("manager.getEnergyDistributionMode()"));
        assertTrue(source.contains("if (!simulate) {"));
        assertTrue(source.contains("groupDistributionCursors.put(key, Integer.valueOf((start + 1) % endpoints.size()))"));
        assertFalse(source.contains("allocationPerOutput"));
    }
}
