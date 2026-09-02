package cn.ae2bc.logic;

import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.junit.Assert.*;

public class TaskAllocationModeSourceTest {
    @Test public void partUsesSharedSelectorAndPersistsMode() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/cn/ae2bc/part/PatternP2PTunnelPart.java")), "UTF-8");
        assertTrue(source.contains("TaskEndpointSelector.select"));
        assertTrue(source.contains("Ae2bcTaskAllocationMode"));
    }
    @Test public void noBatchPlannerIsPresent() throws Exception {
        String source = Files.walk(Paths.get("src/main/java")).filter(p -> p.toString().endsWith(".java"))
                .map(p -> { try { return new String(Files.readAllBytes(p), "UTF-8"); } catch (Exception e) { return ""; } })
                .reduce("", String::concat);
        assertFalse(source.contains("TaskAllocationPlanner"));
        assertFalse(source.contains("BatchDistributionPlanner"));
    }
}
