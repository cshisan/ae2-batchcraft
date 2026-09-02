package cn.ae2bc.logic;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class TaskAllocationModeSourceTest {
    @Test void inputLogicUsesSharedSelectorAndPersistsMode() throws Exception {
        String source = Files.readString(Path.of("src/main/java/cn/ae2bc/logic/PatternP2PTunnelInputLogic.java"));
        assertTrue(source.contains("TaskEndpointSelector.select"));
        assertTrue(source.contains("TaskAllocationMode.fromId"));
        assertTrue(source.contains("getAvailableTaskEndpoints()"));
    }
    @Test void lowVersionSourcesContainNoBatchPlanner() throws Exception {
        String source = Files.walk(Path.of("src/main/java")).filter(p -> p.toString().endsWith(".java"))
                .map(p -> { try { return Files.readString(p); } catch (Exception e) { return ""; } })
                .reduce("", String::concat);
        assertFalse(source.contains("TaskAllocationPlanner"));
        assertFalse(source.contains("BatchDistributionPlanner"));
        assertFalse(source.contains("BatchDispatchContext"));
    }
}
